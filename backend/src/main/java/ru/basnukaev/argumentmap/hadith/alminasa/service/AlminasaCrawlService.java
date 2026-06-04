package ru.basnukaev.argumentmap.hadith.alminasa.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import ru.basnukaev.argumentmap.hadith.alminasa.api.AlminasaEsClient;
import ru.basnukaev.argumentmap.hadith.alminasa.api.AlminasaProperties;
import ru.basnukaev.argumentmap.hadith.alminasa.api.dto.AlminasaHit;
import ru.basnukaev.argumentmap.hadith.alminasa.api.dto.AlminasaPage;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.AlminasaRows;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmHadithRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmNarratorRow;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmCrawlCheckpointDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmExplanationStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmHadithStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmNarratorStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmRulingStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.domain.AmCrawlCheckpoint;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.domain.AmCrawlCheckpoint.AmCrawlStatus;
import ru.basnukaev.argumentmap.hadith.alminasa.web.AlminasaCrawlConflictException;

/**
 * Resumable краулер alminasa → am_staging_* (ADR-060, спека §A).
 *
 * <p>«hadith-first»: один цикл по hadith-12 (search_after по
 * hadith_serial_id), зависимые (нарраторы/шархи/рулинги) добираются
 * батчевыми terms по id текущей страницы — только проверенные HAR'ом
 * формы запросов, без сортировочных требований к зависимым индексам.
 *
 * <p>Чекпоинт на границе КАЖДОЙ страницы; upsert'ы идемпотентны →
 * resume после PAUSED/FAILED/рестарта переигрывает максимум одну
 * страницу. Состояние pause — in-memory volatile (single-instance);
 * рестарт backend'а оставляет RUNNING-строку — её перехватывает
 * stale-timeout (паттерн ai.edit.processing-timeout-minutes).
 *
 * <p>БЕЗ @Transactional вокруг цикла: каждая страница коммитится сама,
 * прогресс не теряется при падении (идемпотентность вместо отката).
 *
 * <p>Сериализацию конкурентных писателей гарантирует НЕ
 * {@code synchronized claimStart()}, а executor {@code alminasaCrawlExecutor}
 * (core=max=1, queue=0, AbortPolicy): при stale-takeover, пока старый поток
 * ещё жив, второй submit отклоняется. Не поднимать queueCapacity.
 */
@Service
@ConditionalOnProperty(name = "alminasa.enabled", havingValue = "true", matchIfMissing = true)
public class AlminasaCrawlService {

    private static final Logger log = LoggerFactory.getLogger(AlminasaCrawlService.class);

    /** Ключ чекпоинта корпуса хадисов (generic-таблица — Планы 6+ добавят свои). */
    public static final String HADITH_INDEX_KEY = "hadith-12";

    private final AlminasaEsClient client;
    private final AmHadithStagingDao hadithDao;
    private final AmNarratorStagingDao narratorDao;
    private final AmExplanationStagingDao explanationDao;
    private final AmRulingStagingDao rulingDao;
    private final AmCrawlCheckpointDao checkpointDao;
    private final AlminasaProperties props;

    private volatile boolean pauseRequested;

    public AlminasaCrawlService(AlminasaEsClient client,
                                AmHadithStagingDao hadithDao,
                                AmNarratorStagingDao narratorDao,
                                AmExplanationStagingDao explanationDao,
                                AmRulingStagingDao rulingDao,
                                AmCrawlCheckpointDao checkpointDao,
                                AlminasaProperties props) {
        this.client = client;
        this.hadithDao = hadithDao;
        this.narratorDao = narratorDao;
        this.explanationDao = explanationDao;
        this.rulingDao = rulingDao;
        this.checkpointDao = checkpointDao;
        this.props = props;
    }

    /**
     * Claim RUNNING. Живой RUNNING → {@link AlminasaCrawlConflictException}
     * (409); stale RUNNING (updated_at старше stale-timeout — воркер умер)
     * перехватывается. IDLE/COMPLETED → старт с нуля (reset прогресса);
     * PAUSED/FAILED/stale → resume с last_sort_value.
     */
    public synchronized AmCrawlCheckpoint claimStart() {
        Optional<AmCrawlCheckpoint> existing = checkpointDao.find(HADITH_INDEX_KEY);
        if (existing.isPresent() && existing.get().status() == AmCrawlStatus.RUNNING) {
            OffsetDateTime staleBefore =
                    OffsetDateTime.now().minusMinutes(props.crawl().staleTimeoutMinutes());
            if (existing.get().updatedAt().isAfter(staleBefore)) {
                throw new AlminasaCrawlConflictException();
            }
            log.warn("alminasa crawl: stale RUNNING-claim (updated_at={}) — перехватываем",
                    existing.get().updatedAt());
        }
        boolean resetProgress = existing.isEmpty()
                || existing.get().status() == AmCrawlStatus.IDLE
                || existing.get().status() == AmCrawlStatus.COMPLETED;
        pauseRequested = false;
        return checkpointDao.upsertRunning(HADITH_INDEX_KEY, resetProgress);
    }

    /**
     * Асинхронная обёртка цикла. ВАЖНО: контроллер зовёт claimStart() и
     * crawlAsync() как ДВА вызова через Spring-прокси — self-invocation
     * обошёл бы @Async (регрессия Сессии 55 c @Retry).
     */
    @Async("alminasaCrawlExecutor")
    public void crawlAsync() {
        try {
            crawlLoop();
        } catch (Exception e) {
            log.error("alminasa crawl упал", e);
            checkpointDao.markFailed(HADITH_INDEX_KEY, abbreviate(e.toString()));
        }
    }

    /**
     * Синхронный цикл (package-visible для детерминированных IT). Чекпоинт
     * двигается на границе каждой страницы.
     */
    void crawlLoop() {
        // seed дедупликации нарраторов: что уже в staging — не перекачиваем
        Set<Long> stagedNarrators = new HashSet<>(narratorDao.findAllIds());
        while (true) {
            AmCrawlCheckpoint checkpoint = checkpointDao.find(HADITH_INDEX_KEY).orElseThrow();
            AlminasaPage page =
                    client.fetchHadithPage(checkpoint.lastSortValue(), props.crawl().pageSize());
            if (!Objects.equals(checkpoint.totalHits(), page.totalHits())) {
                checkpointDao.setTotalHits(HADITH_INDEX_KEY, page.totalHits());
            }
            if (page.hits().isEmpty()) {
                checkpointDao.markCompleted(HADITH_INDEX_KEY);
                log.info("alminasa crawl завершён: {} хадисов", checkpoint.fetchedCount());
                return;
            }

            List<AmHadithRow> rows = page.hits().stream().map(AlminasaRows::fromHadithHit).toList();
            hadithDao.upsertAll(rows);

            List<String> hadithIds = rows.stream().map(AmHadithRow::hadithId).toList();
            for (List<String> batch : partition(hadithIds, props.crawl().dependentBatchSize())) {
                rulingDao.upsertAll(client.fetchRulingsByHadithIds(batch).hits().stream()
                        .map(AlminasaRows::fromRulingHit).toList());
                explanationDao.upsertAll(client.fetchExplanationsByHadithIds(batch).hits().stream()
                        .map(AlminasaRows::fromExplanationHit).toList());
            }

            List<Long> newNarratorIds = collectNewNarratorIds(page.hits(), stagedNarrators);
            for (List<Long> batch : partition(newNarratorIds, props.crawl().dependentBatchSize())) {
                List<AmNarratorRow> narrators = client.fetchNarratorsByIds(batch).stream()
                        .map(AlminasaRows::fromNarratorHit).toList();
                narratorDao.upsertAll(narrators);
            }
            stagedNarrators.addAll(newNarratorIds);

            long lastSerial = rows.get(rows.size() - 1).hadithSerialId();
            long stagedCount = hadithDao.count();
            checkpointDao.advance(HADITH_INDEX_KEY, lastSerial, stagedCount);
            log.info("alminasa crawl: страница до serial={} (+{} хадисов, +{} рави)",
                    lastSerial, rows.size(), newNarratorIds.size());

            if (pauseRequested) {
                checkpointDao.markPaused(HADITH_INDEX_KEY);
                log.info("alminasa crawl: пауза на serial={}", lastSerial);
                return;
            }
            sleep(props.crawl().delayMs());
        }
    }

    /** Пауза на границе текущей страницы (no-op если краулер не идёт). */
    public void pause() {
        pauseRequested = true;
    }

    public Optional<AmCrawlCheckpoint> checkpoint() {
        return checkpointDao.find(HADITH_INDEX_KEY);
    }

    /** id рави из narrators[] страниц, которых ещё нет в staging. id — строки в источнике. */
    private static List<Long> collectNewNarratorIds(List<AlminasaHit> hits, Set<Long> staged) {
        Set<Long> ids = new LinkedHashSet<>();
        for (AlminasaHit hit : hits) {
            for (JsonNode narrator : hit.source().path("narrators")) {
                long id = narrator.path("id").asLong(0);
                if (id > 0 && !staged.contains(id)) {
                    ids.add(id);
                }
            }
        }
        return new ArrayList<>(ids);
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> parts = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            parts.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return parts;
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
}
