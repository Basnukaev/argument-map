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

import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.Sanad;
import ru.basnukaev.argumentmap.hadith.domain.SanadNarrator;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRepository;
import ru.basnukaev.argumentmap.hadith.repository.SanadRepository;
import ru.basnukaev.argumentmap.hadith.web.dto.SanadGraphResponse;
import ru.basnukaev.argumentmap.hadith.web.dto.SanadGraphResponse.EdgeData;
import ru.basnukaev.argumentmap.hadith.web.dto.SanadGraphResponse.GraphEdge;
import ru.basnukaev.argumentmap.hadith.web.dto.SanadGraphResponse.GraphNode;
import ru.basnukaev.argumentmap.hadith.web.dto.SanadGraphResponse.NarratorData;
import ru.basnukaev.argumentmap.hadith.web.dto.SanadGraphResponse.SanadSummary;

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
 * </ul>
 *
 * <p>Узел Пророка ﷺ синтетический - не строка в БД, а корень графа,
 * соединённый со сподвижником (position 0). Подпись ребра - сокращённая
 * формула передачи (тахаммуль): سمعت / حدثنا / أخبرنا / عن.
 */
@Service
public class SanadGraphService {

    private static final Logger log = LoggerFactory.getLogger(SanadGraphService.class);

    static final String PROPHET_NODE_ID = "prophet";

    private final SanadRepository sanadRepository;
    private final NarratorRepository narratorRepository;
    private final ObjectMapper objectMapper;

    public SanadGraphService(SanadRepository sanadRepository,
                             NarratorRepository narratorRepository,
                             ObjectMapper objectMapper) {
        this.sanadRepository = sanadRepository;
        this.narratorRepository = narratorRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public SanadGraphResponse buildGraph(UUID hadithId) {
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

        List<GraphNode> nodes = new ArrayList<>();
        if (hasProphetRoot) {
            nodes.add(prophetNode());
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
            nodes.add(new GraphNode(
                    "narrator-" + nid, role,
                    narratorData(n, tier, collectionByCollector.get(nid))
            ));
        }

        List<GraphEdge> edges = buildEdges(sanads, links, hasProphetRoot);

        List<SanadSummary> sanadSummaries = sanads.stream()
                .map(s -> new SanadSummary(
                        s.id(),
                        metaText(s.metadata(), "collectionRu"),
                        metaText(s.metadata(), "collectionAr"),
                        s.chainGrade(),
                        s.primaryChain(),
                        s.compiledById() == null ? null : "narrator-" + s.compiledById()
                ))
                .toList();

        return new SanadGraphResponse(hadithId, nodes, edges, sanadSummaries);
    }

    /**
     * Собирает превью-граф иснада из извлечённой LLM цепочки (ADR-059) —
     * <b>без обращения к БД</b>. Эфемерный граф для admin-превью импорта
     * sunnah.com: пока биографии передатчиков пусты (rijal-источник —
     * future), узлы несут только арабское имя.
     *
     * <p>Маппинг в {@link SanadGraphResponse} (reuse конвенций
     * {@link #buildGraph}):
     * <ul>
     *   <li><b>Разворот:</b> {@code isnad.narrators()} идёт top→companion
     *       (как в матне), а граф строится снизу вверх: position 0 =
     *       сподвижник (Prophet-side). Реверсим список.</li>
     *   <li><b>Синтетические id</b> {@code "x-0"}, {@code "x-1"}, … —
     *       никаких БД-uuid. {@code narratorId = null}.</li>
     *   <li>Узел Пророка ﷺ ({@link #prophetNode()}) → ребро к position 0;
     *       подпись ребра = transmission сподвижника
     *       («عن النبي» / «سمعت»).</li>
     *   <li><b>Роли:</b> position 0 → COMPANION, остальные передатчики →
     *       NARRATOR (составитель — НЕ звено цепи).</li>
     *   <li><b>Узел COLLECTOR</b> (id {@code "collector"}) добавляется
     *       внизу для отображения сборника — связан от верхнего
     *       передатчика, tier = max+1. Добавляем т.к. существующий граф
     *       показывает составителя.</li>
     *   <li>{@code hadithId = null} (не персистентный хадис), {@code
     *       sanads} — пустой список (превью без сводки цепей).</li>
     * </ul>
     *
     * @param isnad        извлечённая цепочка (предполагается
     *                     {@code isnadFound=true} с непустыми narrators)
     * @param collectionAr арабское имя сборника для COLLECTOR-узла
     *                     (nullable)
     * @param collectionRu русское имя сборника для COLLECTOR-узла
     *                     (nullable)
     */
    public SanadGraphResponse buildGraphFromExtracted(
            ru.basnukaev.argumentmap.hadith.isnad.ExtractedIsnad isnad,
            String collectionAr, String collectionRu) {
        // top→companion → companion→top: position 0 = сподвижник
        List<ru.basnukaev.argumentmap.hadith.isnad.ExtractedNarrator> chain =
                new ArrayList<>(isnad.narrators());
        java.util.Collections.reverse(chain);

        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();
        nodes.add(prophetNode());

        int n = chain.size();
        for (int i = 0; i < n; i++) {
            ru.basnukaev.argumentmap.hadith.isnad.ExtractedNarrator narrator = chain.get(i);
            String nodeId = "x-" + i;
            String role = (i == 0) ? "COMPANION" : "NARRATOR";
            int tier = i + 1;
            nodes.add(new GraphNode(nodeId, role, sparseNarratorData(narrator.name(), tier, null)));

            String source = (i == 0) ? PROPHET_NODE_ID : "x-" + (i - 1);
            edges.add(new GraphEdge(
                    "edge-" + i, source, nodeId,
                    new EdgeData(narrator.transmission(), null, true, 1)));
        }

        // Синтетический узел сборника снизу цепи (как в БД-графе показан
        // составитель). Связан от верхнего передатчика (последний в chain).
        if (n > 0) {
            int collectorTier = n + 1;
            nodes.add(new GraphNode(
                    "collector", "COLLECTOR",
                    collectorData(collectionAr, collectionRu, collectorTier)));
            edges.add(new GraphEdge(
                    "edge-collector", "x-" + (n - 1), "collector",
                    new EdgeData(null, null, true, 1)));
        }

        return new SanadGraphResponse(null, nodes, edges, List.of());
    }

    /** NarratorData превью-узла: только арабское имя + tier, остальное пусто. */
    private NarratorData sparseNarratorData(String nameAr, int tier, String collection) {
        return new NarratorData(
                null, nameAr, null, null, null, null, null, null,
                null, null, null, null, null, null, collection, tier);
    }

    /** NarratorData для синтетического COLLECTOR-узла превью-графа. */
    private NarratorData collectorData(String collectionAr, String collectionRu, int tier) {
        return new NarratorData(
                null, collectionAr, null, collectionRu, null, null, null, null,
                null, null, null, null, null, null, collectionAr, tier);
    }

    private List<GraphEdge> buildEdges(List<Sanad> sanads,
                                       List<SanadNarrator> links,
                                       boolean hasProphetRoot) {
        Map<UUID, List<SanadNarrator>> linksBySanad = links.stream()
                .collect(Collectors.groupingBy(SanadNarrator::sanadId));

        // LinkedHashMap сохраняет порядок вставки - primary chain (первая в
        // sanads) задаёт видимую подпись общих рёбер.
        LinkedHashMap<String, EdgeAccumulator> acc = new LinkedHashMap<>();
        for (Sanad s : sanads) {
            List<SanadNarrator> chain = new ArrayList<>(
                    linksBySanad.getOrDefault(s.id(), List.of()));
            chain.sort(Comparator.comparingInt(SanadNarrator::position));
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
                String key = source + "->" + target;
                EdgeAccumulator existing = acc.get(key);
                if (existing == null) {
                    acc.put(key, new EdgeAccumulator(
                            source, target, cur.transmissionPhrase(),
                            s.chainGrade(), s.primaryChain()));
                } else {
                    existing.sanadCount++;
                    existing.onPrimaryChain = existing.onPrimaryChain || s.primaryChain();
                }
            }
        }

        List<GraphEdge> edges = new ArrayList<>(acc.size());
        int idx = 0;
        for (EdgeAccumulator e : acc.values()) {
            edges.add(new GraphEdge(
                    "edge-" + (idx++),
                    e.source, e.target,
                    new EdgeData(e.transmissionPhrase, e.chainGrade, e.onPrimaryChain, e.sanadCount)
            ));
        }
        return edges;
    }

    private GraphNode prophetNode() {
        return new GraphNode(
                PROPHET_NODE_ID, "PROPHET",
                new NarratorData(
                        null, "النَّبِيُّ مُحَمَّدٌ ﷺ", "Prophet Muhammad", "Пророк Мухаммад ﷺ",
                        null, null, null, null, null, null, null,
                        null, null, null, null, 0
                )
        );
    }

    private NarratorData narratorData(Narrator n, int tier, String collection) {
        return new NarratorData(
                n.id(), n.nameAr(), n.nameArNormalized(), metaText(n.metadata(), "nameRu"),
                n.kunya(), n.laqab(), n.yearBirthHijri(), n.yearDeathHijri(),
                n.birthplace(), n.primaryResidence(), n.deathPlace(),
                n.reliabilityGrade(), n.reliabilityComment(), metaText(n.metadata(), "generation"),
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

    /** Мутабельный аккумулятор ребра на время дедупликации. */
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
}
