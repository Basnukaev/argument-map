package ru.basnukaev.argumentmap.library.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.web.dto.AuthorityCitationRef;
import ru.basnukaev.argumentmap.web.dto.MuhaqqiqRef;
import ru.basnukaev.argumentmap.web.dto.PublicationPlaceRef;
import ru.basnukaev.argumentmap.web.dto.PublisherRef;

public record BookDetailResponse(
        UUID id,
        BookType bookType,
        String title,
        UUID authorityId,
        String language,
        String description,
        JsonNode metadata,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        List<ChapterResponse> chapters,
        UUID muhaqqiqId,
        UUID publisherId,
        UUID publicationPlaceId,
        Integer editionNumber,
        Integer publishedYearHijri,
        Integer publishedYearGregorian,
        AuthorityCitationRef authority,
        MuhaqqiqRef muhaqqiq,
        PublisherRef publisher,
        PublicationPlaceRef publicationPlace,
        String visibility,
        // Thesis (академическая рисала) поля - nullable, заполнены только
        // для shamela-диссертаций (миграция 58). degree=ماجستير/دكتوراه,
        // supervisor=إشراف, institution=جامعة/كلية.
        String thesisDegree,
        String thesisSupervisor,
        String thesisInstitution
) {
}
