package ru.basnukaev.argumentmap.hadith.alminasa.web;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.exception.AdminOnlyException;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmAmbiguousStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmCommentaryStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmExplanationStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmHadithStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmNarratorStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmRulingStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.service.AlminasaCatalogService;
import ru.basnukaev.argumentmap.hadith.alminasa.service.AlminasaCrawlService;
import ru.basnukaev.argumentmap.hadith.alminasa.service.AlminasaDependentsBackfillService;
import ru.basnukaev.argumentmap.hadith.alminasa.service.AlminasaHadithMapper;
import ru.basnukaev.argumentmap.hadith.alminasa.service.AlminasaImportLauncher;
import ru.basnukaev.argumentmap.hadith.alminasa.web.dto.AlminasaBackfillStatusResponse;
import ru.basnukaev.argumentmap.hadith.alminasa.web.dto.AlminasaCatalogEntryResponse;
import ru.basnukaev.argumentmap.hadith.alminasa.web.dto.AlminasaCrawlStatusResponse;
import ru.basnukaev.argumentmap.hadith.alminasa.web.dto.AlminasaDryRunResponse;
import ru.basnukaev.argumentmap.hadith.alminasa.web.dto.AlminasaImportStatusResponse;
import ru.basnukaev.argumentmap.web.CurrentUser;

/**
 * Admin-endpoints краулинга alminasa (План 2, ADR-060). ADMIN-only
 * (паттерн SunnahAdminController). Полная админка с каталогом сборников
 * и dry-run превью — План 5.
 */
@RestController
@RequestMapping("/api/v1/admin/alminasa")
@ConditionalOnProperty(name = "alminasa.enabled", havingValue = "true", matchIfMissing = true)
public class AlminasaAdminController {

    private final AlminasaCrawlService crawlService;
    private final AmHadithStagingDao hadithDao;
    private final AmNarratorStagingDao narratorDao;
    private final AmExplanationStagingDao explanationDao;
    private final AmRulingStagingDao rulingDao;
    private final AmCommentaryStagingDao commentaryDao;
    private final AmAmbiguousStagingDao ambiguousDao;
    private final AlminasaCatalogService catalogService;
    private final AlminasaImportLauncher importLauncher;
    private final AlminasaDependentsBackfillService backfillService;
    private final AlminasaHadithMapper hadithMapper;

    public AlminasaAdminController(AlminasaCrawlService crawlService,
                                   AmHadithStagingDao hadithDao,
                                   AmNarratorStagingDao narratorDao,
                                   AmExplanationStagingDao explanationDao,
                                   AmRulingStagingDao rulingDao,
                                   AmCommentaryStagingDao commentaryDao,
                                   AmAmbiguousStagingDao ambiguousDao,
                                   AlminasaCatalogService catalogService,
                                   AlminasaImportLauncher importLauncher,
                                   AlminasaDependentsBackfillService backfillService,
                                   AlminasaHadithMapper hadithMapper) {
        this.crawlService = crawlService;
        this.hadithDao = hadithDao;
        this.narratorDao = narratorDao;
        this.explanationDao = explanationDao;
        this.rulingDao = rulingDao;
        this.commentaryDao = commentaryDao;
        this.ambiguousDao = ambiguousDao;
        this.catalogService = catalogService;
        this.importLauncher = importLauncher;
        this.backfillService = backfillService;
        this.hadithMapper = hadithMapper;
    }

    /**
     * Запуск/resume краулинга. 202 + текущий статус; 409 если уже идёт.
     * claimStart() и crawlAsync() — два вызова через Spring-прокси
     * (self-invocation обошёл бы @Async).
     */
    @PostMapping("/crawl/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AlminasaCrawlStatusResponse start(@CurrentUser UUID currentUserId) {
        requireAdmin(currentUserId);
        crawlService.claimStart();
        try {
            crawlService.crawlAsync();
        } catch (TaskRejectedException ex) {
            // stale-takeover при ещё живом старом воркере: queue=0 отклонил submit.
            // Чекпоинт НЕ трогаем — живой воркер продолжает advance/updated_at,
            // его claim снова станет не-stale на границе страницы. Честный ответ — 409.
            throw new AlminasaCrawlConflictException();
        }
        return statusResponse();
    }

    /** Пауза на границе текущей страницы (мягкая — чекпоинт сохраняется). */
    @PostMapping("/crawl/pause")
    public AlminasaCrawlStatusResponse pause(@CurrentUser UUID currentUserId) {
        requireAdmin(currentUserId);
        crawlService.pause();
        return statusResponse();
    }

    /** Чекпоинт + счётчики staging-таблиц (поллинг прогресса). */
    @GetMapping("/crawl/status")
    public AlminasaCrawlStatusResponse status(@CurrentUser UUID currentUserId) {
        requireAdmin(currentUserId);
        return statusResponse();
    }

    // ── каталог + импорт + dry-run (План 5) ──────────────────────────────────

    /** Каталог всех 12 сборников со staged/mapped прогрессом. ADMIN-only (403). */
    @GetMapping("/catalog")
    public List<AlminasaCatalogEntryResponse> catalog(@CurrentUser UUID currentUserId) {
        requireAdmin(currentUserId);
        return catalogService.catalog().stream()
                .map(AlminasaCatalogEntryResponse::from)
                .toList();
    }

    /** Снапшот состояния async-импорта (поллинг прогресса). ADMIN-only (403). */
    @GetMapping("/import/status")
    public AlminasaImportStatusResponse importStatus(@CurrentUser UUID currentUserId) {
        requireAdmin(currentUserId);
        return AlminasaImportStatusResponse.from(importLauncher.status());
    }

    /**
     * Запуск импорта рави. 202 + актуальный статус; 409
     * {@code alminasa-import-already-running} если импорт уже идёт.
     */
    @PostMapping("/import/narrators")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AlminasaImportStatusResponse importNarrators(@CurrentUser UUID currentUserId) {
        requireAdmin(currentUserId);
        importLauncher.launchNarrators();
        return AlminasaImportStatusResponse.from(importLauncher.status());
    }

    /**
     * Запуск импорта хадисов (опционально одного сборника по {@code bookId}).
     * 202 + актуальный статус; 409 если импорт уже идёт.
     */
    @PostMapping("/import/hadiths")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AlminasaImportStatusResponse importHadiths(@CurrentUser UUID currentUserId,
                                                      @RequestParam(required = false) Integer bookId) {
        requireAdmin(currentUserId);
        importLauncher.launchHadiths(bookId);
        return AlminasaImportStatusResponse.from(importLauncher.status());
    }

    /**
     * Dry-run превью маппинга одного хадиса ДО записи (read-only: маппинг +
     * rollback). 404 {@code alminasa-staging-not-found} если хадиса нет в
     * staging; 422 {@code alminasa-mapping-failed} при пустом/битом матне.
     */
    @GetMapping("/dry-run/{hadithId}")
    public AlminasaDryRunResponse dryRun(@CurrentUser UUID currentUserId,
                                         @PathVariable String hadithId) {
        requireAdmin(currentUserId);
        return AlminasaDryRunResponse.from(hadithMapper.dryRunHadith(hadithId));
    }

    // ── backfill علл/غريب (План 8) ────────────────────────────────────────────

    /**
     * Запуск backfill-краула зависимых данных (علل/иляль + غريب/гариб) поверх
     * staging-корпуса. 202 + статус; 409 {@code alminasa-backfill-already-running}
     * если backfill уже идёт. Может идти параллельно с crawl (разные индексы).
     */
    @PostMapping("/backfill/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AlminasaBackfillStatusResponse backfillStart(@CurrentUser UUID currentUserId) {
        requireAdmin(currentUserId);
        backfillService.start();
        return backfillStatusResponse();
    }

    /** Пауза backfill на границе текущей страницы (после pause рестарт с нуля). */
    @PostMapping("/backfill/pause")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AlminasaBackfillStatusResponse backfillPause(@CurrentUser UUID currentUserId) {
        requireAdmin(currentUserId);
        backfillService.pause();
        return backfillStatusResponse();
    }

    /** Снапшот прогресса backfill + счётчики staging علل/غريب (поллинг). */
    @GetMapping("/backfill/status")
    public AlminasaBackfillStatusResponse backfillStatus(@CurrentUser UUID currentUserId) {
        requireAdmin(currentUserId);
        return backfillStatusResponse();
    }

    private AlminasaBackfillStatusResponse backfillStatusResponse() {
        return AlminasaBackfillStatusResponse.of(
                backfillService.status(), commentaryDao.count(), ambiguousDao.count());
    }

    private AlminasaCrawlStatusResponse statusResponse() {
        return AlminasaCrawlStatusResponse.of(
                crawlService.checkpoint().orElse(null),
                hadithDao.count(),
                narratorDao.count(),
                explanationDao.count(),
                rulingDao.count());
    }

    private static void requireAdmin(UUID currentUserId) {
        if (!UserRole.ADMIN.equals(SecurityContextUtils.currentRoleOrAnonymous())) {
            throw new AdminOnlyException(currentUserId);
        }
    }
}
