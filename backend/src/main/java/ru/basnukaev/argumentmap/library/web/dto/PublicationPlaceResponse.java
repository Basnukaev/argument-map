package ru.basnukaev.argumentmap.library.web.dto;

import java.util.UUID;

/**
 * DTO места издания для autocomplete в BookEditModal (Этап 20.d).
 */
public record PublicationPlaceResponse(
        UUID id,
        String name
) {
}
