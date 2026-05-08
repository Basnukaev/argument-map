package ru.basnukaev.argumentmap.library.domain;

import java.time.Instant;
import java.util.UUID;

public record ImageRegion(
        UUID id,
        UUID pageId,
        double x,
        double y,
        double width,
        double height,
        String extractedText,
        Instant createdAt
) {
}
