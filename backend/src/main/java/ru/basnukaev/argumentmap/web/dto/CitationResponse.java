package ru.basnukaev.argumentmap.web.dto;

/**
 * Structured citation для academic display (ADR-028). 8 nullable nested refs -
 * frontend проверяет каждое и пропускает блок если ref = null.
 */
public record CitationResponse(
        AuthorityCitationRef authority,
        BookCitationRef book,
        MuhaqqiqRef muhaqqiq,
        PublisherRef publisher,
        PublicationPlaceRef publicationPlace,
        LocationRef location,
        PdfRef pdf,
        RegionRef region
) {
}
