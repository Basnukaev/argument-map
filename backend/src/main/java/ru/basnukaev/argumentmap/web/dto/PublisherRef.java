package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

public record PublisherRef(
        UUID id,
        String name
) {
}
