package ru.basnukaev.argumentmap.web.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import ru.basnukaev.argumentmap.domain.Reliability;
import ru.basnukaev.argumentmap.domain.SourceType;

public record SourceResponse(
        UUID id,
        SourceType sourceType,
        String title,
        String citation,
        Reliability reliability,
        UUID authorityId,
        JsonNode metadata,
        Instant createdAt
) {
}
