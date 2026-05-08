package ru.basnukaev.argumentmap.library.web.dto;

import java.util.UUID;

public record ImageRegionResponse(
        UUID id,
        double x,
        double y,
        double width,
        double height,
        String extractedText
) {
}
