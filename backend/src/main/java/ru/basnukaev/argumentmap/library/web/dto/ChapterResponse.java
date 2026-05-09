package ru.basnukaev.argumentmap.library.web.dto;

import java.util.List;
import java.util.UUID;

public record ChapterResponse(
        UUID id,
        String title,
        int orderIndex,
        UUID parentChapterId,
        Integer startPageNumber,
        List<ChapterResponse> children
) {
}
