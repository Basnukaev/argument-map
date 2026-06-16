package ru.basnukaev.argumentmap.hadith.alminasa.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import ru.basnukaev.argumentmap.hadith.alminasa.api.AlminasaEsClient;
import ru.basnukaev.argumentmap.hadith.alminasa.api.AlminasaProperties;
import ru.basnukaev.argumentmap.hadith.alminasa.api.dto.AlminasaHit;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.AlminasaRows;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmNarratorRow;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmCrawlCheckpointDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmNarratorCommentaryStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmNarratorStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.web.AlminasaBackfillConflictException;

/**
 * Backfill-краул джарх/таʿдиль-цитат о рави (narrator-commentary-12, ADR-061)
 * поверх уже снятого корпуса рави. Зеркало {@link
 * AlminasaDependentsBackfillService} со сдвигом ключа джойна хадис→рави.
 *
 * <p><b>Не пере-обход narrators-12</b>: цикл идёт keyset'ом по PK
 * {@code narrator_id} {@code am_staging_narrator} В ПАМЯТИ ({@link
 * AmNarratorStagingDao#findPage}). One-shot за admin-эндпоинтом: чекпоинт
 * {@code index_name='narrator-commentary-backfill'} хранит только КОАРС-прогресс
 * для поллинга статуса. <b>Crash/pause → рестарт с нуля</b> (upsert по doc_id
 * идемпотентен).
 *
 * <p>Бьёт по ВСЕМ staged-рави (решение 7): {@code terms:{id}} возвращает пусто
 * для рави без цитат — robust, отдельный {@code hasCommentary}-фильтр не нужен
 * для MVP. На странице: external_id'ы → {@link
 * AlminasaEsClient#fetchNarratorCommentaries} → upsert narrator-commentary.
 *
 * <p><b>Контракт переходов (паттерн dependents-backfill'а):</b> RUNNING ставится
 * СИНХРОННО в {@link #start()} ДО submit (CAS IDLE→RUNNING; занято → 409
 * {@link AlminasaBackfillConflictException}). {@code TaskRejectedException} →
 * откат IDLE + 409. async-тело гарантирует уход из RUNNING (try/catch/finally).
 * <b>Свой executor</b> ({@code alminasaBackfillExecutor} — общий с dependents-
 * backfill: оба one-shot за admin-кнопкой, одновременный запуск двух не нужен;
 * второй submit отобьётся AbortPolicy → 409).
 */
@Service
@ConditionalOnProperty(name = "alminasa.enabled", havingValue = "true", matchIfMissing = true)
public class AlminasaNarratorCommentaryBackfillService {

    private static final Logger log =
            LoggerFactory.getLogger(AlminasaNarratorCommentaryBackfillService.class);

    /** Ключ чекпоинта backfill (коарс-прогресс для статуса; отдельный от hadith-12). */
    public static final String BACKFILL_INDEX_KEY = "narrator-commentary-backfill";

    /** Статус прогона. */
    public enum Status { IDLE, RUNNING }

    private final AlminasaEsClient client;
    private final AmNarratorStagingDao narratorDao;
    private final AmNarratorCommentaryStagingDao commentaryDao;
    private final AmCrawlCheckpointDao checkpointDao;
    private final ThreadPoolTaskExecutor executor;
    private final AlminasaProperties props;

    private final AtomicReference<State> state = new AtomicReference<>(State.idle());
    private volatile boolean pauseRequested;

    public AlminasaNarratorCommentaryBackfillService(
            AlminasaEsClient client,
            AmNarratorStagingDao narratorDao,
            AmNarratorCommentaryStagingDao commentaryDao,
            AmCrawlCheckpointDao checkpointDao,
            @Qualifier("alminasaBackfillExecutor") ThreadPoolTaskExecutor executor,
            AlminasaProperties props) {
        this.client = client;
        this.narratorDao = narratorDao;
        this.commentaryDao = commentaryDao;
        this.checkpointDao = checkpointDao;
        this.executor = executor;
        this.props = props;
    }

    /**
     * Синхронный CAS IDLE→RUNNING ДО submit. Занято → 409. Прямой submit на
     * executor: AbortPolicy при живом воркере → {@code TaskRejectedException} →
     * откат RUNNING→IDLE + 409.
     */
    public State start() {
        claimRunning();
        try {
            executor.execute(this::runBackfill);
        } catch (TaskRejectedException ex) {
            state.set(State.idle());
            // чекпоинт уже переведён в RUNNING claimRunning'ом — откатываем,
            // иначе статус-поллинг показывал бы фантомный RUNNING
            checkpointDao.markFailed(BACKFILL_INDEX_KEY, "executor отклонил задачу (живой воркер)");
            throw new AlminasaBackfillConflictException();
        }
        return state.get();
    }

    private void claimRunning() {
        State previous = state.get();
        if (previous.status() == Status.RUNNING
                || !state.compareAndSet(previous, State.running())) {
            throw new AlminasaBackfillConflictException();
        }
        pauseRequested = false;
        checkpointDao.upsertRunning(BACKFILL_INDEX_KEY, true);
    }

    /** Пауза на границе текущей страницы (no-op если backfill не идёт). */
    public void pause() {
        pauseRequested = true;
    }

    /**
     * Тело прогона на executor-потоке. Гарантирует уход из RUNNING: нормальное
     * завершение → IDLE; RuntimeException → IDLE + lastError.
     */
    private void runBackfill() {
        try {
            backfillLoop();
            state.set(state.get().toIdle(null));
        } catch (RuntimeException e) {
            log.error("alminasa narrator-commentary backfill упал", e);
            checkpointDao.markFailed(BACKFILL_INDEX_KEY, abbreviate(e.toString()));
            state.set(state.get().toIdle(e.toString()));
        }
    }

    /**
     * Синхронный цикл (package-visible для детерминированных IT). Keyset по PK
     * {@code narrator_id} в памяти; чекпоинт двигается коарсово на границе
     * каждой страницы (только для статуса).
     */
    void backfillLoop() {
        Long afterId = null;
        int processedPages = 0;
        int processedNarrators = 0;
        while (true) {
            List<AmNarratorRow> page = narratorDao.findPage(afterId, props.crawl().pageSize());
            if (page.isEmpty()) {
                checkpointDao.markCompleted(BACKFILL_INDEX_KEY);
                log.info("alminasa narrator-commentary backfill завершён: {} страниц, {} рави",
                        processedPages, processedNarrators);
                return;
            }

            // narrator_id'ы страницы (= external_id) → narrator-commentary-доки → upsert
            List<Integer> narratorIds = page.stream()
                    .map(r -> Math.toIntExact(r.narratorId())).toList();
            List<AlminasaHit> commentaries = client.fetchNarratorCommentaries(narratorIds);
            commentaryDao.upsertAll(commentaries.stream()
                    .map(AlminasaRows::fromNarratorCommentaryHit).toList());

            AmNarratorRow last = page.get(page.size() - 1);
            afterId = last.narratorId();
            processedPages++;
            processedNarrators += page.size();

            // коарс-advance чекпоинта (только статус): курсор последней строки + processed
            checkpointDao.advance(BACKFILL_INDEX_KEY, last.narratorId(),
                    String.valueOf(last.narratorId()), processedNarrators);
            state.set(state.get().withProgress(processedPages, processedNarrators));
            log.info("alminasa narrator-commentary backfill: страница до id={} (+{} цитат)",
                    last.narratorId(), commentaries.size());

            if (pauseRequested) {
                checkpointDao.markPaused(BACKFILL_INDEX_KEY);
                log.info("alminasa narrator-commentary backfill: пауза на странице {} "
                        + "(после pause рестарт с нуля)", processedPages);
                return;
            }
            sleep(props.crawl().delayMs());
        }
    }

    /** Снапшот статуса (поллинг прогресса). */
    public State status() {
        return state.get();
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String abbreviate(String message) {
        return message != null && message.length() > 500 ? message.substring(0, 500) : message;
    }

    /** In-memory снапшот прогона backfill (для поллинга статуса). */
    public record State(
            Status status,
            OffsetDateTime startedAt,
            int processedPages,
            int processedNarrators,
            String lastError) {

        static State idle() {
            return new State(Status.IDLE, null, 0, 0, null);
        }

        static State running() {
            return new State(Status.RUNNING, OffsetDateTime.now(), 0, 0, null);
        }

        State withProgress(int pages, int narrators) {
            return new State(status, startedAt, pages, narrators, lastError);
        }

        State toIdle(String error) {
            return new State(Status.IDLE, startedAt, processedPages, processedNarrators, error);
        }
    }
}
