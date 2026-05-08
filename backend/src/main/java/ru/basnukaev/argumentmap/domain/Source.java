package ru.basnukaev.argumentmap.domain;

import java.time.Instant;
import java.util.UUID;

public record Source(
        UUID id,
        SourceType sourceType,
        String title,
        String citation,
        Reliability reliability,
        UUID authorityId,
        String metadata,
        Instant createdAt
) {
}
