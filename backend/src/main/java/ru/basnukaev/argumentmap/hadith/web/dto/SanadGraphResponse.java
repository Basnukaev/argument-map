package ru.basnukaev.argumentmap.hadith.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * Граф иснада, преднастроенный под React Flow. Hadith Explorer Phase 3.
 *
 * <p>Узлы дедуплицированы: один narrator = один узел, даже если он
 * встречается в нескольких цепях (sanad'ах). Это ключевое свойство для
 * визуализации структуры "гариб у истока — машхур в ветвях": общий
 * верхний участок цепи рисуется одной нитью, которая расходится
 * (fan-out) у общего звена (common link / мадар).
 *
 * <p>Синтетический узел {@code prophet} (Пророк ﷺ) добавляется сверху и
 * соединяется со сподвижником (position 0). Это не строка в БД, а
 * визуальный корень графа.
 */
public record SanadGraphResponse(
        UUID hadithId,
        List<GraphNode> nodes,
        List<GraphEdge> edges,
        List<SanadSummary> sanads
) {

    /** role: PROPHET / COMPANION / NARRATOR / COLLECTOR */
    public record GraphNode(
            String id,
            String role,
            NarratorData data
    ) {
    }

    public record NarratorData(
            UUID narratorId,
            String nameAr,
            String nameLatin,
            String nameRu,
            String kunya,
            String laqab,
            Integer yearBirthHijri,
            Integer yearDeathHijri,
            String birthplace,
            String primaryResidence,
            String deathPlace,
            String reliabilityGrade,
            String reliabilityComment,
            String generation,
            String tabaqa,
            String gradeText,
            String externalId,
            String collection,
            int tier
    ) {
    }

    public record GraphEdge(
            String id,
            String source,
            String target,
            EdgeData data
    ) {
    }

    public record EdgeData(
            String transmissionPhrase,
            String chainGrade,
            boolean onPrimaryChain,
            int sanadCount
    ) {
    }

    public record SanadSummary(
            UUID id,
            String collectionRu,
            String collectionAr,
            String chainGrade,
            boolean primaryChain,
            String collectorNodeId
    ) {
    }
}
