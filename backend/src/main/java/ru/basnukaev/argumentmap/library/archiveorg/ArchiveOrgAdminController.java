package ru.basnukaev.argumentmap.library.archiveorg;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.exception.AdminOnlyException;

/**
 * Admin REST endpoints импорта книг из archive.org (ADR-056).
 *
 * <p><b>ADMIN-only</b> (mirror {@code ShamelaAdminController}/{@code
 * SunnahAdminController}): оба endpoint проверяют role через
 * {@link #requireAdmin()}. Non-ADMIN → 403 {@code forbidden-admin-only}.
 *
 * <p>Маппинг ошибок (GlobalExceptionHandler):
 * <ul>
 *   <li>{@link InvalidArchiveOrgUrlException} → 400;</li>
 *   <li>{@link ArchiveOrgItemNotFoundException} → 404;</li>
 *   <li>{@link ArchiveOrgException} → 502 (archive.org недоступен /
 *       circuit breaker open).</li>
 * </ul>
 *
 * <p>{@code import} синхронный. Текст из archive.org не извлекается -
 * книги FILE_ONLY (ADR-056 amendment b). {@code preview} при настроенном
 * LLM может занять 5-15с (AI-извлечение метаданных, ADR-058).
 */
@RestController
@RequestMapping("/api/v1/admin/archive-org")
public class ArchiveOrgAdminController {

    private final ArchiveOrgImportService importService;

    public ArchiveOrgAdminController(ArchiveOrgImportService importService) {
        this.importService = importService;
    }

    /** Превью «как ляжет в наш формат» - без записи в БД. */
    @GetMapping("/preview")
    public ArchiveOrgPreview preview(@RequestParam("url") String url) {
        requireAdmin();
        return importService.preview(url);
    }

    /** Импорт книги в lib_books (+ pdf_links только original, cover_url). */
    @PostMapping("/import")
    public ArchiveOrgImportResponse importBook(@Valid @RequestBody ArchiveOrgImportRequest request) {
        requireAdmin();
        return importService.importBook(request);
    }

    /** Гвард ADMIN-only (mirror ShamelaAdminController#requireAdmin). */
    private static void requireAdmin() {
        if (!UserRole.ADMIN.equals(SecurityContextUtils.currentRoleOrAnonymous())) {
            throw new AdminOnlyException(SecurityContextUtils.currentUserIdOrNull());
        }
    }
}
