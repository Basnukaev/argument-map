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

    /**
     * role: PROPHET / COMPANION / NARRATOR / COLLECTOR / VERSION.
     *
     * <p>VERSION-узел (юзер-фидбек: «в конце должна быть связь с версией
     * хадиса») — конечная вершина графа, представляющая сам хадис-версию
     * (сборник + номер + превью матна). У narrator/prophet-узлов
     * {@code version=null}; у version-узла {@code data=null}.
     */
    public record GraphNode(
            String id,
            String role,
            NarratorData data,
            VersionInfo version
    ) {
    }

    /**
     * Данные version-узла (конкретного хадиса-версии). Заполняется только у
     * узлов с {@code role="VERSION"}, {@code id=version-{hadithId}}.
     * Поля сборника null, если коллекция хадиса не найдена.
     */
    public record VersionInfo(
            UUID hadithId,
            String externalId,
            String collectionSlug,
            String collectionNameAr,
            String collectionNameRu,
            Integer printedNumber,
            String matnPreview
    ) {
    }

    /**
     * Данные narrator-узла. Значения полей — EFFECTIVE (с наложенными
     * курация-overrides ADR-065 §5: правка → новое значение, field-hide → null)
     * для ВСЕХ читателей. {@code overriddenFields} (Фаза 5.b) — admin-индикатор
     * «поле отредактировано»: имена переопределённых колонок, заполняется ТОЛЬКО
     * при {@code reveal=true} (ADMIN), иначе пустой список.
     */
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
            int tier,
            List<String> overriddenFields
    ) {
    }

    public record GraphEdge(
            String id,
            String source,
            String target,
            EdgeData data
    ) {
    }

    /**
     * {@code transmissionPhrase} — EFFECTIVE формула передачи звена (с
     * наложенным курация-override §5, Фаза 5.b) для ВСЕХ читателей.
     * {@code position} — позиция звена-приёмника в цепи (0 = сподвижник);
     * фронт адресует override по нему ({@code transmission_phrase@{position}}).
     * version-/merge-рёбра не несут звена → {@code position=null}.
     * {@code transmissionPhraseOverridden} — admin-индикатор «формула
     * отредактирована», заполняется ТОЛЬКО при {@code reveal=true} (ADMIN),
     * иначе {@code false} (курируемое значение видно всем, признак правки — лишь
     * ADMIN'у, зеркало {@code NarratorData.overriddenFields}).
     */
    public record EdgeData(
            String transmissionPhrase,
            String chainGrade,
            boolean onPrimaryChain,
            int sanadCount,
            Integer position,
            boolean transmissionPhraseOverridden
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
