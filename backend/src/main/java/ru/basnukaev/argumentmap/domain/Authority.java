package ru.basnukaev.argumentmap.domain;

import java.time.Instant;
import java.util.UUID;

public record Authority(
        UUID id,
        String name,
        String bio,
        String era,
        String madhab,
        String metadata,
        Instant createdAt,
        String fullName,
        Integer deathYearHijri
) {
}
