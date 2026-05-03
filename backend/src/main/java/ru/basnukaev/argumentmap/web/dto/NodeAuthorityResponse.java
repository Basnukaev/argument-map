package ru.basnukaev.argumentmap.web.dto;

import java.time.Instant;
import java.util.UUID;

import ru.basnukaev.argumentmap.domain.Stance;

public record NodeAuthorityResponse(
        UUID nodeId,
        UUID authorityId,
        Stance stance,
        Instant createdAt
) {
}
