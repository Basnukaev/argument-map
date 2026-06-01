package ru.basnukaev.argumentmap.hadith.sunnah.web;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.exception.AdminOnlyException;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.SunnahDataSource;
import ru.basnukaev.argumentmap.hadith.sunnah.service.SunnahImportService;
import ru.basnukaev.argumentmap.hadith.sunnah.service.SunnahMappingResult;
import ru.basnukaev.argumentmap.hadith.sunnah.web.dto.SunnahCollectionPreview;
import ru.basnukaev.argumentmap.hadith.sunnah.web.dto.SunnahImportResponse;
import ru.basnukaev.argumentmap.web.CurrentUser;

/**
 * Admin REST endpoints для импорта sunnah.com (Phase 5 ETL шаг 2.d, ADR-052).
 *
 * <p><b>ADMIN-only</b> (как audit admin endpoint). <b>Bulk-policy gate:</b>
 * импорт строго по одному сборнику явным вызовом; {@code GET /collections} —
 * превью каталога до импорта.
 *
 * <p>Источник — {@link SunnahDataSource} через {@link ObjectProvider}: бин
 * создаётся только при {@code sunnah.dump.enabled=true}. Если источник не
 * сконфигурирован — {@link SunnahDumpNotConfiguredException} → 503.
 */
@RestController
@RequestMapping("/api/v1/admin/sunnah")
public class SunnahAdminController {

    private final SunnahImportService importService;
    private final ObjectProvider<SunnahDataSource> sourceProvider;

    public SunnahAdminController(SunnahImportService importService,
                                 ObjectProvider<SunnahDataSource> sourceProvider) {
        this.importService = importService;
        this.sourceProvider = sourceProvider;
    }

    /** Каталог сборников, доступных в источнике (превью до импорта). */
    @GetMapping("/collections")
    public List<SunnahCollectionPreview> listCollections(@CurrentUser UUID currentUserId) {
        requireAdmin(currentUserId);
        return source().readCollections().stream()
                .map(c -> new SunnahCollectionPreview(
                        c.name(), c.titleEn(), c.titleAr(),
                        c.totalHadith(), c.hasBooks(), c.hasChapters()))
                .toList();
    }

    /** Импорт одного сборника: источник → staging → hd_*. Идемпотентно. */
    @PostMapping("/import/{collection}")
    public SunnahImportResponse importCollection(@PathVariable String collection,
                                                 @CurrentUser UUID currentUserId) {
        requireAdmin(currentUserId);
        SunnahMappingResult result = importService.importCollection(source(), collection);
        return SunnahImportResponse.from(result);
    }

    private SunnahDataSource source() {
        SunnahDataSource source = sourceProvider.getIfAvailable();
        if (source == null) {
            throw new SunnahDumpNotConfiguredException();
        }
        return source;
    }

    private static void requireAdmin(UUID currentUserId) {
        if (!UserRole.ADMIN.equals(SecurityContextUtils.currentRoleOrAnonymous())) {
            throw new AdminOnlyException(currentUserId);
        }
    }
}
