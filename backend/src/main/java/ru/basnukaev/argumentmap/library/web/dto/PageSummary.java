package ru.basnukaev.argumentmap.library.web.dto;

import java.util.UUID;

public record PageSummary(
        UUID id,
        int pageNumber,
        UUID chapterId,
        boolean hasText,
        boolean hasImage
) {
}
