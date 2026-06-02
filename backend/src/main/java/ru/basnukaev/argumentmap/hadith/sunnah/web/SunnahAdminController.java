package ru.basnukaev.argumentmap.hadith.sunnah.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.exception.AdminOnlyException;
import ru.basnukaev.argumentmap.hadith.isnad.ExtractedIsnad;
import ru.basnukaev.argumentmap.hadith.isnad.IsnadExtractionService;
import ru.basnukaev.argumentmap.hadith.service.SanadGraphService;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.SunnahDataSource;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahCollectionRow;
import ru.basnukaev.argumentmap.hadith.sunnah.service.SunnahImportService;
import ru.basnukaev.argumentmap.hadith.sunnah.service.SunnahMappingResult;
import ru.basnukaev.argumentmap.hadith.sunnah.web.dto.IsnadExtractionRequest;
import ru.basnukaev.argumentmap.hadith.sunnah.web.dto.IsnadExtractionResponse;
import ru.basnukaev.argumentmap.hadith.sunnah.web.dto.SunnahCollectionPreview;
import ru.basnukaev.argumentmap.hadith.sunnah.web.dto.SunnahHadithBrowseItem;
import ru.basnukaev.argumentmap.hadith.sunnah.web.dto.SunnahHadithPreview;
import ru.basnukaev.argumentmap.hadith.sunnah.web.dto.SunnahImportResponse;
import ru.basnukaev.argumentmap.web.CurrentUser;
import ru.basnukaev.argumentmap.web.dto.PageRequest;
import ru.basnukaev.argumentmap.web.dto.PagedResponse;

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
    private final IsnadExtractionService isnadExtractionService;
    private final SanadGraphService sanadGraphService;

    public SunnahAdminController(SunnahImportService importService,
                                 ObjectProvider<SunnahDataSource> sourceProvider,
                                 IsnadExtractionService isnadExtractionService,
                                 SanadGraphService sanadGraphService) {
        this.importService = importService;
        this.sourceProvider = sourceProvider;
        this.isnadExtractionService = isnadExtractionService;
        this.sanadGraphService = sanadGraphService;
    }

    /** Каталог сборников, доступных в источнике (превью до импорта). */
    @GetMapping("/collections")
    public List<SunnahCollectionPreview> listCollections(@CurrentUser UUID currentUserId) {
        requireAdmin(currentUserId);
        SunnahDataSource src = source();
        Map<String, Integer> hadithCounts = src.readHadithCounts();
        return src.readCollections().stream()
                .map(c -> new SunnahCollectionPreview(
                        c.name(), c.titleEn(), c.titleAr(),
                        c.totalHadith(),
                        hadithCounts.getOrDefault(c.name(), 0),
                        c.hasBooks(), c.hasChapters()))
                .toList();
    }

    /**
     * Список хадисов сборника, доступных В ИСТОЧНИКЕ (до импорта), с флагом
     * {@code alreadyImported}. Фазовый импорт (ADR-052): пролистать корпус
     * прежде чем коммитить. Пагинация в памяти источника.
     */
    @GetMapping("/collections/{collection}/hadiths")
    public PagedResponse<SunnahHadithBrowseItem> browseHadiths(
            @PathVariable String collection,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @CurrentUser UUID currentUserId) {
        requireAdmin(currentUserId);
        PageRequest pr = PageRequest.from(page, size);
        SunnahDataSource src = source();
        List<SunnahHadithBrowseItem> items =
                importService.browseHadiths(src, collection, pr.size(), pr.offset());
        long total = importService.countHadiths(src, collection);
        return PagedResponse.of(items, pr.page(), pr.size(), total);
    }

    /**
     * DRY-RUN превью маппинга одного хадиса в наш формат hd_* — БЕЗ записи в БД
     * (ключевая фича фазового импорта, ADR-052). См.
     * {@link SunnahImportService#previewSingle}.
     */
    @GetMapping("/preview/{collection}/{number}")
    public SunnahHadithPreview preview(@PathVariable String collection,
                                       @PathVariable String number,
                                       @CurrentUser UUID currentUserId) {
        requireAdmin(currentUserId);
        return importService.previewSingle(source(), collection, number);
    }

    /** Импорт одного сборника: источник → staging → hd_*. Идемпотентно. */
    @PostMapping("/import/{collection}")
    public SunnahImportResponse importCollection(@PathVariable String collection,
                                                 @CurrentUser UUID currentUserId) {
        requireAdmin(currentUserId);
        SunnahMappingResult result = importService.importCollection(source(), collection);
        return SunnahImportResponse.from(result);
    }

    /**
     * Импорт ровно ОДНОГО хадиса по номеру (фазовый/верифицируемый путь,
     * ADR-052). Идемпотентно по (collection, primaryNumber). 404 если хадиса
     * нет в источнике.
     */
    @PostMapping("/import/{collection}/{number}")
    public SunnahImportResponse importSingle(@PathVariable String collection,
                                             @PathVariable String number,
                                             @CurrentUser UUID currentUserId) {
        requireAdmin(currentUserId);
        SunnahMappingResult result = importService.importSingle(source(), collection, number);
        return SunnahImportResponse.from(result);
    }

    /**
     * Извлечь иснад из матна хадиса через LLM и построить превью-граф
     * (ADR-059). Превью — НИЧЕГО не пишется в БД. Latency 5-15с (вызов
     * LLM). Если LLM не настроен — отдаём {@code llmEnabled:false} БЕЗ
     * обращения к источнику дампа (короткое замыкание).
     *
     * <p>Матн берётся сервером из источника (preview-путь), а не из
     * клиентского тела — не доверяем клиентскому тексту.
     */
    @PostMapping("/extract-isnad")
    public IsnadExtractionResponse extractIsnad(@Valid @RequestBody IsnadExtractionRequest request,
                                                @CurrentUser UUID currentUserId) {
        requireAdmin(currentUserId);
        if (!isnadExtractionService.isLlmEnabled()) {
            return IsnadExtractionResponse.llmDisabled();
        }

        SunnahDataSource src = source();
        String collection = request.collection();
        String number = String.valueOf(request.number());
        SunnahHadithPreview preview = importService.previewSingle(src, collection, number);
        String matn = preview.matnAr();
        if (matn == null || matn.isBlank()) {
            return IsnadExtractionResponse.notFound();
        }

        Optional<ExtractedIsnad> extracted = isnadExtractionService.extract(matn);
        if (extracted.isEmpty() || !extracted.get().isnadFound()) {
            return IsnadExtractionResponse.notFound();
        }

        ExtractedIsnad isnad = extracted.get();
        String collectionAr = collectionTitleAr(src, collection);
        var graph = sanadGraphService.buildGraphFromExtracted(isnad, collectionAr, null);
        return IsnadExtractionResponse.found(graph, isnad.cleanedMatn());
    }

    /** Арабское название сборника из источника (для COLLECTOR-узла), nullable. */
    private String collectionTitleAr(SunnahDataSource src, String collectionName) {
        return src.readCollections().stream()
                .filter(c -> collectionName.equals(c.name()))
                .map(SunnahCollectionRow::titleAr)
                .findFirst()
                .orElse(null);
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
