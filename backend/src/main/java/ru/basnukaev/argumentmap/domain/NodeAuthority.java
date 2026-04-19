package ru.basnukaev.argumentmap.domain;

import java.time.Instant;
import java.util.UUID;

public record NodeAuthority(
        UUID nodeId,
        UUID authorityId,
        Stance stance,
        Instant createdAt
) {
}
