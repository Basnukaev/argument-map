package ru.basnukaev.argumentmap.web.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record AuthorityResponse(
        UUID id,
        String name,
        String bio,
        String era,
        String madhab,
        JsonNode metadata,
        Instant createdAt,
        String fullName,
        Integer deathYearHijri,
        String type
) {
}
