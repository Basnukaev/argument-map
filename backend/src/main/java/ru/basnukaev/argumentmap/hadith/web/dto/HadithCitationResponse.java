package ru.basnukaev.argumentmap.hadith.web.dto;

import java.util.UUID;

import ru.basnukaev.argumentmap.domain.NodeSource;

/**
 * Результат прикрепления хадиса к узлу (под-проект #2). Хадис-опора —
 * это node_source с HADITH-источником; {@code hadithId} даёт фронту прямую
 * ссылку на хадис.
 */
public record HadithCitationResponse(
        UUID nodeSourceId,
        UUID nodeId,
        UUID hadithId,
        UUID sourceId
) {
    public static HadithCitationResponse of(NodeSource ns, UUID hadithId) {
        return new HadithCitationResponse(ns.id(), ns.nodeId(), hadithId, ns.sourceId());
    }
}
