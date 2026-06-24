package ru.basnukaev.argumentmap.hadith.web;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.exception.AdminOnlyException;
import ru.basnukaev.argumentmap.hadith.service.HadithHealthService;
import ru.basnukaev.argumentmap.hadith.web.dto.HadithDataHealthResponse;
import ru.basnukaev.argumentmap.web.CurrentUser;

/**
 * Admin-endpoint «здоровья» данных хадис-корпуса (P1-2, PROD-READINESS-AUDIT
 * §4/§7): счётчики недозаполненных записей для курации. ADMIN-only (паттерн
 * AlminasaAdminController): нет principal → 401 (security-фильтр), не ADMIN →
 * 403 ({@code forbidden-admin-only}).
 */
@RestController
@RequestMapping("/api/v1/admin/hadith")
public class HadithHealthController {

    private final HadithHealthService healthService;

    public HadithHealthController(HadithHealthService healthService) {
        this.healthService = healthService;
    }

    /** Снапшот счётчиков «битых»/недозаполненных записей. ADMIN-only. */
    @GetMapping("/health")
    public HadithDataHealthResponse health(@CurrentUser UUID currentUserId) {
        requireAdmin(currentUserId);
        return healthService.health();
    }

    private static void requireAdmin(UUID currentUserId) {
        if (!UserRole.ADMIN.equals(SecurityContextUtils.currentRoleOrAnonymous())) {
            throw new AdminOnlyException(currentUserId);
        }
    }
}
