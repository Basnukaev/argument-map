package ru.basnukaev.argumentmap.library.web.dto;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.basnukaev.argumentmap.library.domain.BookType;

/**
 * Тело запроса POST /api/v1/library/books. С Этапа 20.e расширен 6
 * опциональными academic-полями (для AddSourceModal manual book entry
 * с findOrCreate по справочникам). Все academic-поля nullable - старый
 * вызов без них продолжает работать (createBook_quranWithoutAuthor IT).
 *
 * <p>Семантика academic строк: {@code null} или blank trimmed -> FK
 * остаётся null. Non-blank trimmed -> {@code findOrCreate(name)} в
 * соответствующем справочнике.
 */
public record CreateBookRequest(
        @NotNull BookType bookType,
        @NotBlank @Size(max = 500) String title,
        UUID authorityId,
        @NotBlank @Size(max = 32) String language,
        @Size(max = 5000) String description,
        JsonNode metadata,
        String muhaqqiqName,
        String publisherName,
        String publicationPlaceName,
        @Min(1) @Max(99) Integer editionNumber,
        @Min(1) @Max(9999) Integer publishedYearHijri,
        @Min(1) @Max(9999) Integer publishedYearGregorian
) {
}
