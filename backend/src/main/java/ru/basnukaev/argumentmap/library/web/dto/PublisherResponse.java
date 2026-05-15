package ru.basnukaev.argumentmap.library.web.dto;

import java.util.UUID;

/**
 * DTO издателя для autocomplete в BookEditModal (Этап 20.d).
 */
public record PublisherResponse(
        UUID id,
        String name
) {
}
