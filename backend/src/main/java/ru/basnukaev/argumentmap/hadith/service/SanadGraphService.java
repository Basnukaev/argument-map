package ru.basnukaev.argumentmap.hadith.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.hadith.domain.Collection;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.Sanad;
import ru.basnukaev.argumentmap.hadith.domain.SanadNarrator;
import ru.basnukaev.argumentmap.hadith.repository.CollectionRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithCrossrefRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.MatnRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRepository;
import ru.basnukaev.argumentmap.hadith.repository.SanadRepository;
import ru.basnukaev.argumentmap.hadith.web.dto.SanadGraphResponse;
import ru.basnukaev.argumentmap.hadith.web.dto.SanadGraphResponse.EdgeData;
import ru.basnukaev.argumentmap.hadith.web.dto.SanadGraphResponse.GraphEdge;
import ru.basnukaev.argumentmap.hadith.web.dto.SanadGraphResponse.GraphNode;
import ru.basnukaev.argumentmap.hadith.web.dto.SanadGraphResponse.NarratorData;
import ru.basnukaev.argumentmap.hadith.web.dto.SanadGraphResponse.SanadSummary;
import ru.basnukaev.argumentmap.hadith.web.dto.SanadGraphResponse.VersionInfo;

/**
 * Сборка графа иснада под React Flow. Hadith Explorer Phase 3.
 *
 * <p>Ключевые решения:
 * <ul>
 *   <li><b>Дедупликация узлов</b> - один narrator = один узел, даже если
 *       он встречается в нескольких sanad'ах. Общая верхняя часть цепи
 *       (Умар → Алькама → Мухаммад → Яхья) рисуется одной нитью,
 *       расходящейся у общего звена (мадар) Яхьи ибн Саида.</li>
 *   <li><b>Дедупликация рёбер</b> по ключу {@code source->target}. Если
 *       пара передатчиков встречается в нескольких цепях, ребро одно;
 *       видимая подпись берётся из первой обработанной цепи (primary
 *       chain идёт первой), а число цепей через ребро накапливается.</li>
 *   <li><b>Роль узла</b> выводится из позиции и {@code compiledById}:
 *       PROPHET (синтетический корень), COMPANION (position 0),
 *       COLLECTOR (составитель сборника), иначе NARRATOR.</li>
 *   <li><b>Version-узел</b> (юзер-фидбек) - конечная вершина каждого
 *       хадиса-версии (сборник + номер + превью матна). Рёбра идут от
 *       коллекторного конца каждой цепи (рави с макс. position) в
 *       version-узел.</li>
 * </ul>
 *
 * <p>Узел Пророка ﷺ синтетический - не строка в БД, а корень графа,
 * соединённый со сподвижником (position 0). Подпись ребра - сокращённая
 * формула передачи (тахаммуль): سمعت / حدثنا / أخبرنا / عن.
 *
 * <p>{@link #buildGraph} строит граф одного хадиса (его цепи + version-узел);
 * {@link #buildTuruqGraph} - надстройка: объединяет граф самого хадиса со
 * всеми его резолвленными crossref-путями (طرق) в один слитый граф. Оба
 * используют общий внутренний аккумулятор {@link GraphAccumulator}.
 */
@Service
public class SanadGraphService {

    private static final Logger log = LoggerFactory.getLogger(SanadGraphService.class);

    static final String PROPHET_NODE_ID = "prophet";

    /** Длина превью матна в version-узле. */
    private static final int VERSION_MATN_PREVIEW_LEN = 120;

    private final SanadRepository sanadRepository;
    private final NarratorRepository narratorRepository;
    private final HadithRepository hadithRepository;
    private final MatnRepository matnRepository;
    private final CollectionRepository collectionRepository;
    private final HadithCrossrefRepository crossrefRepository;
    private final ObjectMapper objectMapper;

    public SanadGraphService(SanadRepository sanadRepository,
                             NarratorRepository narratorRepository,
                             HadithRepository hadithRepository,
                             MatnRepository matnRepository,
                             CollectionRepository collectionRepository,
                             HadithCrossrefRepository crossrefRepository,
                             ObjectMapper objectMapper) {
        this.sanadRepository = sanadRepository;
        this.narratorRepository = narratorRepository;
        this.hadithRepository = hadithRepository;
        this.matnRepository = matnRepository;
        this.collectionRepository = collectionRepository;
        this.crossrefRepository = crossrefRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Граф иснада одного хадиса: дедуплицированные узлы рави + синтетический
     * корень Пророка ﷺ + version-узел самого хадиса. Главный хадиса —
     * {@code hadithId}, поэтому рёбра его primary-цепи помечаются
     * {@code onPrimaryChain}.
     */
    @Transactional(readOnly = true)
    public SanadGraphResponse buildGraph(UUID hadithId) {
        Hadith main = hadithRepository.findById(hadithId).orElse(null);
        GraphAccumulator acc = new GraphAccumulator(hadithId);
        if (main != null) {
            addHadith(acc, main);
        }
        return acc.toResponse(hadithId);
    }

    /**
     * Объединённый граф всех путей (طرق): сам хадис + все резолвленные
     * crossref-сиблинги ({@code related_hadith_id IS NOT NULL}, distinct).
     * Рави шарятся между версиями (те же UUID) → один узел; у каждой версии
     * свой version-узел и свои рёбра. {@code onPrimaryChain} — только для
     * рёбер primary-цепи ГЛАВНОГО хадиса. {@code hadithId} ответа = главный.
     */
    @Transactional(readOnly = true)
    public SanadGraphResponse buildTuruqGraph(UUID hadithId) {
        GraphAccumulator acc = new GraphAccumulator(hadithId);

        Hadith main = hadithRepository.findById(hadithId).orElse(null);
        if (main != null) {
            addHadith(acc, main);
        }

        // Резолвленные сиблинги (distinct related_hadith_id), исключая сам хадис.
        List<UUID> siblingIds = crossrefRepository.findByHadithId(hadithId).stream()
                .map(c -> c.relatedHadithId())
                .filter(Objects::nonNull)
                .filter(rid -> !rid.equals(hadithId))
                .distinct()
                .toList();
        for (UUID siblingId : siblingIds) {
            hadithRepository.findById(siblingId).ifPresent(sib -> addHadith(acc, sib));
        }

        return acc.toResponse(hadithId);
    }

    /**
     * Вливает один хадис (его цепи + рави + version-узел) в общий
     * аккумулятор. Рави/Пророк дедуплицируются между хадисами; для каждой
     * цепи добавляется ребро от её коллекторного конца в version-узел.
     */
    private void addHadith(GraphAccumulator acc, Hadith hadith) {
        UUID hadithId = hadith.id();
        boolean isMain = hadithId.equals(acc.mainHadithId);

        List<Sanad> sanads = sanadRepository.findByHadithId(hadithId);
        List<UUID> sanadIds = sanads.stream().map(Sanad::id).toList();
        List<SanadNarrator> links = sanadRepository.findNarratorsBySanadIds(sanadIds);

        List<UUID> narratorIds = links.stream()
                .map(SanadNarrator::narratorId).distinct().toList();
        Map<UUID, Narrator> narratorById = narratorRepository.findByIds(narratorIds).stream()
                .collect(Collectors.toMap(Narrator::id, n -> n));

        Set<UUID> companionIds = links.stream()
                .filter(l -> l.position() == 0)
                .map(SanadNarrator::narratorId)
                .collect(Collectors.toSet());
        Set<UUID> collectorIds = sanads.stream()
                .map(Sanad::compiledById).filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Позиция узла (для tier'а). Один narrator может встречаться на одной
        // и той же позиции в разных цепях - берём минимальную на всякий случай.
        Map<UUID, Integer> positionByNarrator = new HashMap<>();
        for (SanadNarrator l : links) {
            positionByNarrator.merge(l.narratorId(), l.position(), Math::min);
        }

        // Сборник для каждого collector-узла (из metadata sanad'а).
        Map<UUID, String> collectionByCollector = new HashMap<>();
        for (Sanad s : sanads) {
            if (s.compiledById() != null) {
                String collection = metaText(s.metadata(), "collectionRu");
                if (collection != null) {
                    collectionByCollector.putIfAbsent(s.compiledById(), collection);
                }
            }
        }

        boolean hasProphetRoot = !companionIds.isEmpty();
        if (hasProphetRoot) {
            acc.ensureProphet();
        }

        for (UUID nid : narratorIds) {
            Narrator n = narratorById.get(nid);
            if (n == null) {
                continue;
            }
            // Приоритет COLLECTOR над COMPANION намеренный: роль COLLECTOR несёт
            // ярлык сборника (collection), важный для UI. Пересечение (сподвижник,
            // который одновременно compiledById) - вырожденный случай: в канонических
            // иснадах составитель всегда внизу цепи, не на position 0.
            String role = collectorIds.contains(nid) ? "COLLECTOR"
                    : companionIds.contains(nid) ? "COMPANION"
                    : "NARRATOR";
            int tier = positionByNarrator.getOrDefault(nid, 0) + 1;
            acc.addNarratorNode(nid, role, narratorData(n, tier, collectionByCollector.get(nid)));
        }

        // Рёбра внутри цепей (prophet→companion→…→collector) с дедупом.
        accumulateChainEdges(acc, sanads, links, hasProphetRoot, isMain);

        // Version-узел самого хадиса + рёбра от коллекторного конца каждой цепи.
        String versionNodeId = addVersionNode(acc, hadith);
        accumulateVersionEdges(acc, sanads, links, versionNodeId, isMain);

        // Сводки цепей.
        for (Sanad s : sanads) {
            acc.sanadSummaries.add(new SanadSummary(
                    s.id(),
                    metaText(s.metadata(), "collectionRu"),
                    metaText(s.metadata(), "collectionAr"),
                    s.chainGrade(),
                    s.primaryChain(),
                    s.compiledById() == null ? null : "narrator-" + s.compiledById()
            ));
        }
    }

    /**
     * Рёбра передачи внутри цепей хадиса: prophet→companion (position 0) и
     * звено→звено далее. Дедуп по {@code source->target}; {@code onPrimaryChain}
     * накапливается только если цепь primary И хадис — главный.
     */
    private void accumulateChainEdges(GraphAccumulator acc, List<Sanad> sanads,
                                      List<SanadNarrator> links,
                                      boolean hasProphetRoot, boolean isMain) {
        Map<UUID, List<SanadNarrator>> linksBySanad = links.stream()
                .collect(Collectors.groupingBy(SanadNarrator::sanadId));

        for (Sanad s : sanads) {
            List<SanadNarrator> chain = new ArrayList<>(
                    linksBySanad.getOrDefault(s.id(), List.of()));
            chain.sort(Comparator.comparingInt(SanadNarrator::position));
            boolean primary = isMain && s.primaryChain();
            for (int i = 0; i < chain.size(); i++) {
                SanadNarrator cur = chain.get(i);
                String source;
                if (i == 0) {
                    if (!hasProphetRoot) {
                        continue;
                    }
                    source = PROPHET_NODE_ID;
                } else {
                    source = "narrator-" + chain.get(i - 1).narratorId();
                }
                String target = "narrator-" + cur.narratorId();
                acc.addEdge(source, target, cur.transmissionPhrase(), s.chainGrade(), primary);
            }
        }
    }

    /**
     * Рёбра «коллекторный конец цепи → version-узел». Коллекторный конец —
     * рави с МАКСИМАЛЬНОЙ position в цепи (самый низ иснада). transmissionPhrase
     * = null. {@code onPrimaryChain} — primary-цепь главного хадиса.
     */
    private void accumulateVersionEdges(GraphAccumulator acc, List<Sanad> sanads,
                                        List<SanadNarrator> links,
                                        String versionNodeId, boolean isMain) {
        Map<UUID, List<SanadNarrator>> linksBySanad = links.stream()
                .collect(Collectors.groupingBy(SanadNarrator::sanadId));

        for (Sanad s : sanads) {
            List<SanadNarrator> chain = linksBySanad.getOrDefault(s.id(), List.of());
            if (chain.isEmpty()) {
                continue;
            }
            SanadNarrator collectorEnd = chain.stream()
                    .max(Comparator.comparingInt(SanadNarrator::position))
                    .orElseThrow();
            boolean primary = isMain && s.primaryChain();
            acc.addVersionEdge("narrator-" + collectorEnd.narratorId(), versionNodeId, primary);
        }
    }

    /**
     * Создаёт version-узел хадиса (сборник + номер + превью матна) и
     * возвращает его id. Коллекция nullable (null → slug/имена null, узел
     * всё равно создаётся). tier = max tier среди уже добавленных + 1.
     */
    private String addVersionNode(GraphAccumulator acc, Hadith hadith) {
        UUID hadithId = hadith.id();
        String nodeId = "version-" + hadithId;

        Collection collection = hadith.collectionId() == null ? null
                : collectionRepository.findById(hadith.collectionId()).orElse(null);

        String matnPreview = preview(matnRepository
                .findPrimaryTextByHadithIds(List.of(hadithId)).get(hadithId));

        VersionInfo info = new VersionInfo(
                hadithId,
                hadith.externalId(),
                collection == null ? null : collection.slug(),
                collection == null ? null : collection.nameAr(),
                collection == null ? null : collection.nameRu(),
                hadith.primaryNumber(),
                matnPreview);

        acc.addVersionNode(nodeId, info);
        return nodeId;
    }

    private static String preview(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= VERSION_MATN_PREVIEW_LEN
                ? text : text.substring(0, VERSION_MATN_PREVIEW_LEN);
    }

    private static GraphNode prophetNode() {
        return new GraphNode(
                PROPHET_NODE_ID, "PROPHET",
                new NarratorData(
                        null, "النَّبِيُّ مُحَمَّدٌ ﷺ", "Prophet Muhammad", "Пророк Мухаммад ﷺ",
                        null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, 0
                ),
                null
        );
    }

    private NarratorData narratorData(Narrator n, int tier, String collection) {
        return new NarratorData(
                n.id(), n.nameAr(), n.nameArNormalized(), metaText(n.metadata(), "nameRu"),
                n.kunya(), n.laqab(), n.yearBirthHijri(), n.yearDeathHijri(),
                n.birthplace(), n.primaryResidence(), n.deathPlace(),
                n.reliabilityGrade(), n.reliabilityComment(), metaText(n.metadata(), "generation"),
                n.tabaqa(), n.gradeText(), n.externalId(),
                collection, tier
        );
    }

    /**
     * Defensive извлечение строкового поля из JSONB metadata. Любая ошибка
     * парсинга → null (metadata - extensible, не критичен для графа).
     */
    private String metaText(String metadata, String field) {
        if (metadata == null || metadata.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(metadata);
            JsonNode value = root.get(field);
            return value == null || value.isNull() ? null : value.asText();
        } catch (Exception e) {
            log.debug("Не удалось распарсить metadata для поля {}: {}", field, e.getMessage());
            return null;
        }
    }

    /**
     * Аккумулятор слияния графа из одного или нескольких хадисов. Узлы рави
     * дедуплицируются по {@code narrator-{id}}, version-узлы — по
     * {@code version-{hadithId}}, рёбра передачи — по {@code source->target}.
     * {@code mainHadithId} задаёт, чьи primary-цепи помечаются
     * {@code onPrimaryChain}.
     */
    private static final class GraphAccumulator {

        private final UUID mainHadithId;

        // LinkedHashMap фиксирует порядок: prophet (если есть) первый, далее
        // рави в порядке добавления, version-узлы в хвосте.
        private final LinkedHashMap<String, GraphNode> nodes = new LinkedHashMap<>();
        private final LinkedHashMap<String, EdgeAccumulator> edges = new LinkedHashMap<>();
        private final List<VersionEdge> versionEdges = new ArrayList<>();
        private final List<SanadSummary> sanadSummaries = new ArrayList<>();

        GraphAccumulator(UUID mainHadithId) {
            this.mainHadithId = mainHadithId;
        }

        void ensureProphet() {
            nodes.putIfAbsent(PROPHET_NODE_ID, prophetNode());
        }

        void addNarratorNode(UUID narratorId, String role, NarratorData data) {
            // Дедуп: тот же рави между версиями — один узел. Первая добавленная
            // роль/данные побеждают (primary-хадис обрабатывается первым).
            nodes.putIfAbsent("narrator-" + narratorId, new GraphNode(
                    "narrator-" + narratorId, role, data, null));
        }

        void addVersionNode(String nodeId, VersionInfo info) {
            nodes.putIfAbsent(nodeId, new GraphNode(nodeId, "VERSION", null, info));
        }

        void addEdge(String source, String target, String transmissionPhrase,
                     String chainGrade, boolean onPrimaryChain) {
            String key = source + "->" + target;
            EdgeAccumulator existing = edges.get(key);
            if (existing == null) {
                edges.put(key, new EdgeAccumulator(
                        source, target, transmissionPhrase, chainGrade, onPrimaryChain));
            } else {
                existing.sanadCount++;
                existing.onPrimaryChain = existing.onPrimaryChain || onPrimaryChain;
            }
        }

        void addVersionEdge(String source, String target, boolean onPrimaryChain) {
            String key = source + "->" + target;
            for (VersionEdge ve : versionEdges) {
                if (ve.source.equals(source) && ve.target.equals(target)) {
                    ve.sanadCount++;
                    ve.onPrimaryChain = ve.onPrimaryChain || onPrimaryChain;
                    return;
                }
            }
            versionEdges.add(new VersionEdge(source, target, onPrimaryChain));
        }

        SanadGraphResponse toResponse(UUID hadithId) {
            // VERSION-узлы добавлены в хвост (после рави) — фронт кладёт их под
            // коллекторным концом цепи (макс. tier + 1) по role="VERSION".
            List<GraphNode> nodeList = new ArrayList<>(nodes.values());

            List<GraphEdge> edgeList = new ArrayList<>(edges.size() + versionEdges.size());
            int idx = 0;
            for (EdgeAccumulator e : edges.values()) {
                edgeList.add(new GraphEdge(
                        "edge-" + (idx++),
                        e.source, e.target,
                        new EdgeData(e.transmissionPhrase, e.chainGrade,
                                e.onPrimaryChain, e.sanadCount)
                ));
            }
            int vIdx = 0;
            for (VersionEdge ve : versionEdges) {
                edgeList.add(new GraphEdge(
                        "edge-version-" + (vIdx++),
                        ve.source, ve.target,
                        new EdgeData(null, null, ve.onPrimaryChain, ve.sanadCount)
                ));
            }

            return new SanadGraphResponse(hadithId, nodeList, edgeList, sanadSummaries);
        }
    }

    /** Мутабельный аккумулятор ребра передачи на время дедупликации. */
    private static final class EdgeAccumulator {
        final String source;
        final String target;
        final String transmissionPhrase;
        final String chainGrade;
        boolean onPrimaryChain;
        int sanadCount = 1;

        EdgeAccumulator(String source, String target, String transmissionPhrase,
                        String chainGrade, boolean onPrimaryChain) {
            this.source = source;
            this.target = target;
            this.transmissionPhrase = transmissionPhrase;
            this.chainGrade = chainGrade;
            this.onPrimaryChain = onPrimaryChain;
        }
    }

    /** Мутабельный аккумулятор ребра «коллектор → version-узел». */
    private static final class VersionEdge {
        final String source;
        final String target;
        boolean onPrimaryChain;
        int sanadCount = 1;

        VersionEdge(String source, String target, boolean onPrimaryChain) {
            this.source = source;
            this.target = target;
            this.onPrimaryChain = onPrimaryChain;
        }
    }
}
