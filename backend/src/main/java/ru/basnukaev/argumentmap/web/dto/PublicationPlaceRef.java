package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

public record PublicationPlaceRef(
        UUID id,
        String name
) {
}
