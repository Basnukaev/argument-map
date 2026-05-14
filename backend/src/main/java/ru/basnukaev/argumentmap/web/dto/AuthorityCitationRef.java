package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

public record AuthorityCitationRef(
        UUID id,
        String name,
        String fullName,
        Integer deathYearHijri
) {
}
