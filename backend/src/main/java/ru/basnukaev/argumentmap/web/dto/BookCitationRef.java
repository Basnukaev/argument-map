package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

public record BookCitationRef(
        UUID id,
        String title,
        String language,
        Integer editionNumber,
        Integer publishedYearHijri,
        Integer publishedYearGregorian
) {
}
