package ru.basnukaev.argumentmap.library.domain;

import java.time.Instant;
import java.util.UUID;

public record Muhaqqiq(
        UUID id,
        String name,
        String fullName,
        Instant createdAt
) {
}
