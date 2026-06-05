package ru.basnukaev.argumentmap.hadith.web;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.HadithCrossref;
import ru.basnukaev.argumentmap.hadith.domain.HadithEdition;
import ru.basnukaev.argumentmap.hadith.domain.HadithExplanation;
import ru.basnukaev.argumentmap.hadith.domain.HadithRuling;
import ru.basnukaev.argumentmap.hadith.domain.Matn;
import ru.basnukaev.argumentmap.hadith.domain.Sanad;
import ru.basnukaev.argumentmap.hadith.domain.SanadNarrator;
import ru.basnukaev.argumentmap.hadith.alminasa.service.AlminasaCollections;
import ru.basnukaev.argumentmap.hadith.alminasa.service.AlminasaCollections.CollectionInfo;
import ru.basnukaev.argumentmap.hadith.repository.HadithCrossrefRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithEditionRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithExplanationRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithRulingRepository;
import ru.basnukaev.argumentmap.hadith.repository.MatnRepository;
import ru.basnukaev.argumentmap.hadith.repository.SanadRepository;
import ru.basnukaev.argumentmap.hadith.service.SanadGraphService;
import ru.basnukaev.argumentmap.hadith.web.dto.CrossrefDto;
import ru.basnukaev.argumentmap.hadith.web.dto.EditionDto;
import ru.basnukaev.argumentmap.hadith.web.dto.ExplanationDto;
import ru.basnukaev.argumentmap.hadith.web.dto.HadithDetailResponse;
import ru.basnukaev.argumentmap.hadith.web.dto.HadithResponse;
import ru.basnukaev.argumentmap.hadith.web.dto.RulingDto;
import ru.basnukaev.argumentmap.hadith.web.dto.SanadGraphResponse;
import ru.basnukaev.argumentmap.hadith.web.dto.SiblingMatnDto;
import ru.basnukaev.argumentmap.web.dto.PageRequest;
import ru.basnukaev.argumentmap.web.dto.PagedResponse;

/**
 * REST endpoints для самих хадисов. Vision 49d Section 2.6 Phase 1.f.
 *
 * <p>Phase 1.f: simple list + single GET. Phase 1.g: bundled detail
 * (hadith + sanads + narrators + matns в одном payload).
 */
@RestController
@RequestMapping("/api/v1/hadith/hadiths")
public class HadithController {

    private final HadithRepository hadithRepository;
    private final SanadRepository sanadRepository;
    private final MatnRepository matnRepository;
    private final HadithEditionRepository editionRepository;
    private final HadithRulingRepository rulingRepository;
    private final HadithExplanationRepository explanationRepository;
    private final HadithCrossrefRepository crossrefRepository;
    private final SanadGraphService sanadGraphService;
    private final ObjectMapper objectMapper;

    public HadithController(HadithRepository hadithRepository,
                            SanadRepository sanadRepository,
                            MatnRepository matnRepository,
                            HadithEditionRepository editionRepository,
                            HadithRulingRepository rulingRepository,
                            HadithExplanationRepository explanationRepository,
                            HadithCrossrefRepository crossrefRepository,
                            SanadGraphService sanadGraphService,
                            ObjectMapper objectMapper) {
        this.hadithRepository = hadithRepository;
        this.sanadRepository = sanadRepository;
        this.matnRepository = matnRepository;
        this.editionRepository = editionRepository;
        this.rulingRepository = rulingRepository;
        this.explanationRepository = explanationRepository;
        this.crossrefRepository = crossrefRepository;
        this.sanadGraphService = sanadGraphService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public PagedResponse<HadithResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID collectionId,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        PageRequest pr = PageRequest.from(page, size);
        List<Hadith> hadiths = hadithRepository
                .findPage(q, status, collectionId, sort, pr.size(), pr.offset());
        Map<UUID, String> previews = matnRepository.findPrimaryTextByHadithIds(
                hadiths.stream().map(Hadith::id).toList());
        List<HadithResponse> items = hadiths.stream()
                .map(h -> HadithResponse.from(h, previews.get(h.id())))
                .toList();
        long total = hadithRepository.countFiltered(q, status, collectionId);
        return PagedResponse.of(items, pr.page(), pr.size(), total);
    }

    @GetMapping("/{id}")
    public HadithResponse getOne(@PathVariable UUID id) {
        Hadith h = hadithRepository.findById(id)
                .orElseThrow(() -> new HadithNotFoundException(id));
        return toResponse(h);
    }

    /**
     * Phase 1.g bundled detail: hadith + sanads (each с narrators)
     * + matns в одном payload. Primary endpoint для UI sanad graph viz.
     * 1 GET вместо 3+ (N+1 avoidance).
     */
    @GetMapping("/{id}/detail")
    public HadithDetailResponse getDetail(@PathVariable UUID id) {
        Hadith h = hadithRepository.findById(id)
                .orElseThrow(() -> new HadithNotFoundException(id));

        List<Sanad> sanads = sanadRepository.findByHadithId(id);
        List<UUID> sanadIds = sanads.stream().map(Sanad::id).toList();
        // Bulk fetch narrators avoiding N+1
        List<SanadNarrator> allLinks = sanadRepository.findNarratorsBySanadIds(sanadIds);

        // Группируем narrator-links по sanadId ОДИН раз вместо линейного
        // скана allLinks на каждый sanad (был O(sanads × links)). Внутри
        // группы сортируем по position - порядок звеньев иснада значим
        // (position 0 = ближайший к Пророку ﷺ), а bulk-fetch не гарантирует
        // input-order по каждому sanad.
        Map<UUID, List<SanadNarrator>> linksBySanad = allLinks.stream()
                .collect(Collectors.groupingBy(SanadNarrator::sanadId));

        List<HadithDetailResponse.SanadDto> sanadDtos = sanads.stream()
                .map(s -> new HadithDetailResponse.SanadDto(
                        s.id(), s.chainGrade(), s.compiledById(),
                        s.compiledInBookId(), s.primaryChain(),
                        linksBySanad.getOrDefault(s.id(), List.of()).stream()
                                .sorted(Comparator.comparingInt(SanadNarrator::position))
                                .map(l -> new HadithDetailResponse.NarratorLinkDto(
                                        l.position(), l.narratorId(), l.transmissionPhrase()
                                ))
                                .toList()
                ))
                .toList();

        List<Matn> matns = matnRepository.findByHadithId(id);
        List<HadithDetailResponse.MatnDto> matnDtos = matns.stream()
                .map(m -> new HadithDetailResponse.MatnDto(
                        m.id(), m.textAr(), m.textRu(), m.textEn(),
                        m.collectionId(), m.printedNumber(), m.pageNo(),
                        m.volume(), m.isPrimary(), m.divergenceSummary()
                ))
                .toList();

        List<HadithDetailResponse.GradeDto> grades = parseGrades(h.metadata(), objectMapper);

        // alminasa-сателлиты: по одному запросу на тип (single-detail, N+1 нет).
        // Для legacy/seeded хадисов без сателлитов вернутся пустые списки.
        List<EditionDto> editions = editionRepository.findByHadithId(id).stream()
                .map(HadithController::toEditionDto)
                .toList();

        // In-method кэш резолва related_external_id → наш UUID. Rulings и
        // crossrefs ссылаются на параллельные передачи по alminasa external_id;
        // distinct-значений немного (~12), один lookup на уникальный id.
        Map<String, UUID> relatedIdCache = new HashMap<>();

        List<RulingDto> rulings = rulingRepository.findByHadithId(id).stream()
                .map(r -> toRulingDto(r, objectMapper, relatedIdCache))
                .toList();
        List<ExplanationDto> explanations = explanationRepository.findByHadithId(id).stream()
                .map(e -> toExplanationDto(e, objectMapper))
                .toList();
        List<CrossrefDto> crossrefs = crossrefRepository.findByHadithId(id).stream()
                .map(c -> toCrossrefDto(c, objectMapper, relatedIdCache))
                .toList();

        return new HadithDetailResponse(
                h.id(), h.collectionId(), h.primaryNumber(),
                h.normalizedMatn(), h.status(), h.sourceId(), h.createdAt(),
                h.externalId(), h.hadithType(), h.chapterAr(), h.subChapterAr(),
                h.fullTextAr(),
                sanadDtos, matnDtos, grades,
                editions, rulings, explanations, crossrefs
        );
    }

    /**
     * Резолв alminasa {@code related_external_id} → наш {@code hd_hadiths.id}
     * с in-method кэшем (один lookup на уникальный external_id за detail-запрос).
     * Не найден / null → null (FK-резолв опционален: сиблинг мог быть не
     * импортирован).
     */
    private UUID resolveRelatedHadithId(String relatedExternalId, Map<String, UUID> cache) {
        if (relatedExternalId == null) {
            return null;
        }
        return cache.computeIfAbsent(relatedExternalId, ext ->
                hadithRepository.findByExternalId("alminasa", ext)
                        .map(Hadith::id).orElse(null));
    }

    private static EditionDto toEditionDto(HadithEdition e) {
        return new EditionDto(e.editionName(), e.page(), e.volume());
    }

    /**
     * {@code source} ('embedded'|'index') и {@code relatedExternalId} —
     * из {@code hd_rulings.metadata} (jsonb). Defensive: любая ошибка
     * парсинга → оба поля null (вердикт остаётся валиден без provenance).
     *
     * <p>{@code relatedHadithId} — резолв {@code relatedExternalId} в наш FK
     * (in-method кэш). {@code relatedCollectionNameRu} — русский сборник
     * параллельной передачи по префиксу {@code relatedExternalId}.
     */
    private RulingDto toRulingDto(HadithRuling r, ObjectMapper objectMapper, Map<String, UUID> relatedIdCache) {
        String source = null;
        String relatedExternalId = null;
        String metadata = r.metadata();
        if (metadata != null && !metadata.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(metadata);
                source = nodeText(root, "source");
                relatedExternalId = nodeText(root, "relatedExternalId");
            } catch (Exception e) {
                // metadata — extensible jsonb, provenance не критичен для рендера
            }
        }
        UUID relatedHadithId = resolveRelatedHadithId(relatedExternalId, relatedIdCache);
        String relatedCollectionNameRu = AlminasaCollections.byExternalId(relatedExternalId)
                .map(CollectionInfo::nameRu).orElse(null);
        return new RulingDto(
                r.rulerName(), r.rulerDeathYear(), r.rulingText(),
                r.bookName(), r.page(), r.volume(), source, relatedExternalId,
                relatedHadithId, relatedCollectionNameRu);
    }

    /**
     * {@code reference} (СЛОВО-заголовок гариб-статьи) — из
     * {@code hd_explanations.metadata.reference} (jsonb; есть только у GHARIB).
     * Defensive: любая ошибка парсинга → null (карточка остаётся валидна без
     * заголовка-слова; образец {@link #toRulingDto}).
     */
    private static ExplanationDto toExplanationDto(HadithExplanation e, ObjectMapper objectMapper) {
        String reference = null;
        String metadata = e.metadata();
        if (metadata != null && !metadata.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(metadata);
                reference = nodeText(root, "reference");
            } catch (Exception ex) {
                // metadata — extensible jsonb, reference не критичен для рендера
            }
        }
        return new ExplanationDto(
                e.kind(), e.bookName(), e.author(), e.page(), e.volume(), e.text(), reference);
    }

    /**
     * {@code numbers} — распарсенный JSON-массив номеров сиблинга из
     * {@code hd_hadith_crossrefs.note} (битый/null → пустой список).
     * {@code collectionNameAr/Ru} — сборник сиблинга по префиксу
     * {@code relatedExternalId}. {@code relatedHadithId} — резолв в наш FK
     * (in-method кэш; resolve-проход ETL уже мог заполнить
     * {@code c.relatedHadithId()}, но при свежем сиблинге кэш-резолв надёжнее).
     */
    private CrossrefDto toCrossrefDto(HadithCrossref c, ObjectMapper objectMapper, Map<String, UUID> relatedIdCache) {
        List<String> numbers = parseNumbers(c.note(), objectMapper);
        Optional<CollectionInfo> info = AlminasaCollections.byExternalId(c.relatedExternalId());
        UUID relatedHadithId = c.relatedHadithId() != null
                ? c.relatedHadithId()
                : resolveRelatedHadithId(c.relatedExternalId(), relatedIdCache);
        return new CrossrefDto(
                c.relatedExternalId(), relatedHadithId, numbers,
                info.map(CollectionInfo::nameAr).orElse(null),
                info.map(CollectionInfo::nameRu).orElse(null));
    }

    /**
     * Парс JSON-массива номеров из {@code note} (формат маппера:
     * {@code numbersNote} пишет JSON-массив). Битый/null/не-массив → пустой
     * список (defensive: note исторически свободная строка).
     */
    private static List<String> parseNumbers(String note, ObjectMapper objectMapper) {
        if (note == null || note.isBlank()) {
            return List.of();
        }
        try {
            JsonNode arr = objectMapper.readTree(note);
            if (!arr.isArray()) {
                return List.of();
            }
            List<String> numbers = new ArrayList<>();
            for (JsonNode n : arr) {
                if (!n.isNull()) {
                    numbers.add(n.asText());
                }
            }
            return numbers;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Курируемые оценки учёных из {@code hd_hadiths.metadata.grades} (jsonb).
     * Defensive: любая ошибка парсинга → пустой список (grades не критичны).
     * Package-private static — unit-тестируется без Spring/БД.
     */
    static List<HadithDetailResponse.GradeDto> parseGrades(String metadata, ObjectMapper objectMapper) {
        if (metadata == null || metadata.isBlank()) {
            return List.of();
        }
        try {
            JsonNode arr = objectMapper.readTree(metadata).get("grades");
            if (arr == null || !arr.isArray()) {
                return List.of();
            }
            List<HadithDetailResponse.GradeDto> out = new ArrayList<>();
            for (JsonNode g : arr) {
                out.add(new HadithDetailResponse.GradeDto(
                        nodeText(g, "scholar"), nodeText(g, "grade"), nodeText(g, "note")));
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String nodeText(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    /**
     * Phase 3: граф иснада, преднастроенный под React Flow
     * (дедуплицированные узлы narrator'ов + синтетический корень Пророка ﷺ
     * + рёбра с формулами передачи). Питает компонент SanadGraph на фронте.
     */
    @GetMapping("/{id}/sanad-graph")
    public SanadGraphResponse getSanadGraph(@PathVariable UUID id) {
        // 404 если хадиса нет - граф несуществующего хадиса бессмыслен
        hadithRepository.findById(id)
                .orElseThrow(() -> new HadithNotFoundException(id));
        return sanadGraphService.buildGraph(id);
    }

    /**
     * Объединённый граф всех путей (طرق) хадиса: сам хадис + все его
     * резолвленные crossref-сиблинги, слитые в один граф (общие рави — один
     * узел, version-узел на каждую версию). Юзер: «все пути предания в одном
     * месте». 404 как у sanad-graph.
     */
    @GetMapping("/{id}/turuq-graph")
    public SanadGraphResponse getTuruqGraph(@PathVariable UUID id) {
        hadithRepository.findById(id)
                .orElseThrow(() -> new HadithNotFoundException(id));
        return sanadGraphService.buildTuruqGraph(id);
    }

    /**
     * Primary-матны импортированных параллельных передач (طرق) — секция
     * «Тексты параллельных передач» (С59). Lazy-эндпоинт: detail не раздуваем
     * (матны сиблингов нужны не каждому читателю). Порядок — по external_id
     * (стабилен между вызовами). 404 если хадиса нет.
     */
    @GetMapping("/{id}/sibling-matns")
    public List<SiblingMatnDto> getSiblingMatns(@PathVariable UUID id) {
        hadithRepository.findById(id)
                .orElseThrow(() -> new HadithNotFoundException(id));

        // резолвленные сиблинги, дедуп по id (один сиблинг может прийти
        // несколькими crossref-строками с разными note-номерами)
        List<UUID> siblingIds = crossrefRepository.findByHadithId(id).stream()
                .map(HadithCrossref::relatedHadithId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (siblingIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, String> matnByHadith = matnRepository.findPrimaryTextByHadithIds(siblingIds);
        return siblingIds.stream()
                .map(sid -> hadithRepository.findById(sid).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(h -> {
                    Optional<CollectionInfo> info =
                            AlminasaCollections.byExternalId(h.externalId());
                    return new SiblingMatnDto(
                            h.id(), h.externalId(),
                            info.map(CollectionInfo::nameAr).orElse(null),
                            info.map(CollectionInfo::nameRu).orElse(null),
                            h.primaryNumber(),
                            matnByHadith.get(h.id()));
                })
                // сиблинг без primary-матна бессмыслен в секции текстов
                .filter(dto -> dto.textAr() != null && !dto.textAr().isBlank())
                .sorted(java.util.Comparator.comparing(SiblingMatnDto::externalId))
                .toList();
    }

    private static HadithResponse toResponse(Hadith h) {
        return HadithResponse.from(h);
    }
}
