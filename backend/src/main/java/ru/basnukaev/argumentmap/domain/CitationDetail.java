package ru.basnukaev.argumentmap.domain;

import java.util.UUID;

/**
 * Structured citation для academic display (ADR-028). 27 raw полей из 9 LEFT JOIN
 * в {@link ru.basnukaev.argumentmap.repository.NodeSourceRepository#findByNodeIdWithLocation}.
 * Любое из ID-полей может быть null - frontend проверяет каждое и пропускает
 * соответствующий блок при рендере.
 */
public record CitationDetail(
        UUID authorityId,
        String authorityName,
        String authorFullName,
        Integer authorDeathYearHijri,

        UUID bookId,
        String bookTitle,
        String bookLanguage,

        UUID muhaqqiqId,
        String muhaqqiqName,
        String muhaqqiqFullName,

        UUID publisherId,
        String publisherName,
        UUID publicationPlaceId,
        String publicationPlaceName,
        Integer editionNumber,
        Integer publishedYearHijri,
        Integer publishedYearGregorian,

        UUID pageId,
        String part,
        String printedPage,
        Integer pageNumber,
        Integer rangeStart,
        Integer rangeEnd,

        UUID pdfFileId,
        Integer pdfPageNumber,
        String pdfBbox,

        UUID imageRegionId,
        String regionPrintedPage,
        Integer regionPageNumber
) {
}
