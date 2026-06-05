package ru.basnukaev.argumentmap.hadith.alminasa.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmHadithRow;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmAmbiguousStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmCommentaryStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmCrawlCheckpointDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmHadithStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.web.AlminasaBackfillConflictException;

/**
 * Backfill-краул зависимых данных alminasa (علل/иляль через hadith-commentary-12
 * + غريب/гариб через ambiguous-12) поверх уже снятого корпуса хадисов (План 8,
 * решение 1, ADR-060).
 *
 * <p><b>Не пере-обход hadith-12</b> (фикс C1 — Option A, БЕЗ resumability):
 * цикл идёт keyset'ом {@code (book_id, hadith_serial_id)} по
 * {@code am_staging_hadith} ЧИСТО В ПАМЯТИ ({@link AmHadithStagingDao#findPage}).
 * У {@code am_crawl_checkpoint} нет колонок под пару (int, long), а backfill —
 * one-shot ~30-40 мин по статичному корпусу за admin-эндпоинтом: resumability
 * низкоценна. Чекпоинт {@code index_name='backfill-s59'} хранит только
 * КОАРС-прогресс для поллинга статуса. <b>Crash → рестарт с нуля</b> (upsert
 * идемпотентен); <b>pause</b> — флагом на границе страницы, после pause рестарт
 * ТОЖЕ с нуля (без resume с середины — in-memory курсор теряется).
 *
 * <p>На странице: hadith_id'ы → {@link AlminasaEsClient#fetchCommentaries} →
 * upsert commentary; flatten {@code raw.ambiguous[].explanation_ids} (Jackson) →
 * {@link AlminasaEsClient#fetchAmbiguous} → upsert ambiguous. Батчинг — внутри
 * fetch-методов (per-index, реш. 1 M1).
 *
 * <p><b>Контракт переходов (паттерн launcher'а С58):</b> RUNNING ставится
 * СИНХРОННО в {@link #start()} ДО submit (CAS IDLE→RUNNING; занято → 409
 * {@link AlminasaBackfillConflictException}). {@code TaskRejectedException} на
 * submit (queue=0 + живой воркер) → откат в IDLE + 409. async-тело гарантирует
 * уход из RUNNING (try/catch/finally), иначе один transient-фейл навечно
 * залочил бы 409. <b>Свой executor</b> ({@code alminasaBackfillExecutor}) —
 * backfill и crawl МОГУТ идти параллельно (разные index_name).
 */
@Service
@ConditionalOnProperty(name = "alminasa.enabled", havingValue = "true", matchIfMissing = true)
public class AlminasaDependentsBackfillService {

    private static final Logger log = LoggerFactory.getLogger(AlminasaDependentsBackfillService.class);

    /** Ключ чекпоинта backfill (коарс-прогресс для статуса; отдельный от hadith-12). */
    public static final String BACKFILL_INDEX_KEY = "backfill-s59";

    /** Статус прогона. */
    public enum Status { IDLE, RUNNING }

    private final AlminasaEsClient client;
    private final AmHadithStagingDao hadithDao;
    private final AmCommentaryStagingDao commentaryDao;
    private final AmAmbiguousStagingDao ambiguousDao;
    private final AmCrawlCheckpointDao checkpointDao;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor executor;
    private final AlminasaProperties props;

    private final AtomicReference<State> state = new AtomicReference<>(State.idle());
    private volatile boolean pauseRequested;

    public AlminasaDependentsBackfillService(
            AlminasaEsClient client,
            AmHadithStagingDao hadithDao,
            AmCommentaryStagingDao commentaryDao,
            AmAmbiguousStagingDao ambiguousDao,
            AmCrawlCheckpointDao checkpointDao,
            ObjectMapper objectMapper,
            @Qualifier("alminasaBackfillExecutor") ThreadPoolTaskExecutor executor,
            AlminasaProperties props) {
        this.client = client;
        this.hadithDao = hadithDao;
        this.commentaryDao = commentaryDao;
        this.ambiguousDao = ambiguousDao;
        this.checkpointDao = checkpointDao;
        this.objectMapper = objectMapper;
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
     * завершение → IDLE; RuntimeException → IDLE + lastError (иначе transient-фейл
     * навечно залочил бы 409).
     */
    private void runBackfill() {
        try {
            backfillLoop();
            state.set(state.get().toIdle(null));
        } catch (RuntimeException e) {
            log.error("alminasa backfill упал", e);
            checkpointDao.markFailed(BACKFILL_INDEX_KEY, abbreviate(e.toString()));
            state.set(state.get().toIdle(e.toString()));
        }
    }

    /**
     * Синхронный цикл (package-visible для детерминированных IT). Keyset по
     * {@code (book_id, hadith_serial_id)} в памяти; чекпоинт двигается коарсово
     * на границе каждой страницы (только для статуса).
     */
    void backfillLoop() {
        Integer afterBookId = null;
        Long afterSerial = null;
        int processedPages = 0;
        int processedHadiths = 0;
        while (true) {
            List<AmHadithRow> page = hadithDao.findPage(afterBookId, afterSerial, props.crawl().pageSize());
            if (page.isEmpty()) {
                checkpointDao.markCompleted(BACKFILL_INDEX_KEY);
                log.info("alminasa backfill завершён: {} страниц, {} хадисов",
                        processedPages, processedHadiths);
                return;
            }

            // علл (иляль): hadith_id'ы страницы → commentary-доки → upsert
            List<String> hadithIds = page.stream().map(AmHadithRow::hadithId).toList();
            List<AlminasaHit> commentaries = client.fetchCommentaries(hadithIds);
            commentaryDao.upsertAll(commentaries.stream()
                    .map(AlminasaRows::fromCommentaryHit).toList());

            // غريب (гариб): flatten raw.ambiguous[].explanation_ids → ambiguous-доки → upsert
            List<Integer> ambiguousIds = collectAmbiguousIds(page);
            List<AlminasaHit> ambiguous = client.fetchAmbiguous(ambiguousIds);
            ambiguousDao.upsertAll(ambiguous.stream()
                    .map(AlminasaRows::fromAmbiguousHit).toList());

            AmHadithRow last = page.get(page.size() - 1);
            afterBookId = last.bookId();
            afterSerial = last.hadithSerialId();
            processedPages++;
            processedHadiths += page.size();

            // коарс-advance чекпоинта (только статус): курсор последней строки + processed
            checkpointDao.advance(BACKFILL_INDEX_KEY, last.hadithSerialId(),
                    last.hadithId(), processedHadiths);
            state.set(state.get().withProgress(processedPages, processedHadiths));
            log.info("alminasa backfill: страница до ({}, {}) (+{} комментариев, +{} статей)",
                    last.bookId(), last.hadithSerialId(), commentaries.size(), ambiguous.size());

            if (pauseRequested) {
                checkpointDao.markPaused(BACKFILL_INDEX_KEY);
                log.info("alminasa backfill: пауза на странице {} (после pause рестарт с нуля)",
                        processedPages);
                return;
            }
            sleep(props.crawl().delayMs());
        }
    }

    /** id словарных статей из {@code raw.ambiguous[].explanation_ids} страницы (дедуп, порядок). */
    private List<Integer> collectAmbiguousIds(List<AmHadithRow> page) {
        Set<Integer> ids = new LinkedHashSet<>();
        for (AmHadithRow row : page) {
            JsonNode raw = parse(row.rawJson(), row.hadithId());
            JsonNode ambiguous = raw.path("ambiguous");
            if (!ambiguous.isArray()) {
                continue;
            }
            for (JsonNode entry : ambiguous) {
                for (JsonNode id : entry.path("explanation_ids")) {
                    if (id.canConvertToInt()) {
                        ids.add(id.asInt());
                    }
                }
            }
        }
        return new ArrayList<>(ids);
    }

    /** Снапшот статуса (поллинг прогресса). */
    public State status() {
        return state.get();
    }

    private JsonNode parse(String rawJson, String hadithId) {
        try {
            return objectMapper.readTree(rawJson);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "alminasa backfill: битый raw JSON хадиса " + hadithId, e);
        }
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
            int processedHadiths,
            String lastError) {

        static State idle() {
            return new State(Status.IDLE, null, 0, 0, null);
        }

        static State running() {
            return new State(Status.RUNNING, OffsetDateTime.now(), 0, 0, null);
        }

        State withProgress(int pages, int hadiths) {
            return new State(status, startedAt, pages, hadiths, lastError);
        }

        State toIdle(String error) {
            return new State(Status.IDLE, startedAt, processedPages, processedHadiths, error);
        }
    }
}
