package ru.basnukaev.argumentmap.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * Ответ на {@code POST /api/v1/topics/import}. Содержит id новой темы и
 * список warnings про missing books / authorities / pages где
 * find-or-skip привёл к потере части citation context.
 *
 * <p>Warnings - human-readable строки на русском. Не структурированные -
 * нужны для UI toast/log, а не для programmatic processing
 */
public record TopicImportResponse(
        UUID topicId,
        int importedNodes,
        int importedEdges,
        int importedNodeSources,
        int importedSources,
        int importedAuthorities,
        List<String> warnings
) {
}
