package ru.basnukaev.argumentmap.hadith.alminasa.web;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.exception.AdminOnlyException;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmExplanationStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmHadithStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmNarratorStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmRulingStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.service.AlminasaCrawlService;
import ru.basnukaev.argumentmap.hadith.alminasa.web.dto.AlminasaCrawlStatusResponse;
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

    public AlminasaAdminController(AlminasaCrawlService crawlService,
                                   AmHadithStagingDao hadithDao,
                                   AmNarratorStagingDao narratorDao,
                                   AmExplanationStagingDao explanationDao,
                                   AmRulingStagingDao rulingDao) {
        this.crawlService = crawlService;
        this.hadithDao = hadithDao;
        this.narratorDao = narratorDao;
        this.explanationDao = explanationDao;
        this.rulingDao = rulingDao;
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
