package ru.basnukaev.argumentmap.library.domain;

import java.time.Instant;
import java.util.UUID;

public record PublicationPlace(
        UUID id,
        String name,
        Instant createdAt
) {
}
