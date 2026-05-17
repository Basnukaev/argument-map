package ru.basnukaev.argumentmap.library.web.mapper;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.domain.Authority;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.ImageRegion;
import ru.basnukaev.argumentmap.library.domain.Muhaqqiq;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.domain.PublicationPlace;
import ru.basnukaev.argumentmap.library.domain.Publisher;
import ru.basnukaev.argumentmap.library.service.BookDetail;
import ru.basnukaev.argumentmap.library.service.ChapterNode;
import ru.basnukaev.argumentmap.library.service.PageDetail;
import ru.basnukaev.argumentmap.library.web.dto.BookDetailResponse;
import ru.basnukaev.argumentmap.library.web.dto.BookResponse;
import ru.basnukaev.argumentmap.library.web.dto.BookSummaryResponse;
import ru.basnukaev.argumentmap.library.web.dto.ChapterResponse;
import ru.basnukaev.argumentmap.library.web.dto.ImageRegionResponse;
import ru.basnukaev.argumentmap.library.web.dto.PageResponse;
import ru.basnukaev.argumentmap.library.web.dto.PageSummaryResponse;
import ru.basnukaev.argumentmap.web.dto.AuthorityCitationRef;
import ru.basnukaev.argumentmap.web.dto.MuhaqqiqRef;
import ru.basnukaev.argumentmap.web.dto.PublicationPlaceRef;
import ru.basnukaev.argumentmap.web.dto.PublisherRef;

public final class LibraryDtoMappers {

    private static final ObjectMapper JSON = new ObjectMapper();

    private LibraryDtoMappers() {
    }

    public static BookResponse toResponse(Book book) {
        return new BookResponse(
                book.id(), book.bookType(), book.title(),
                book.authorityId(), book.language(), book.description(),
                jsonFromString(book.metadata()),
                book.createdBy(), book.createdAt(), book.updatedAt()
        );
    }

    public static BookSummaryResponse toSummary(Book book) {
        return new BookSummaryResponse(
                book.id(), book.bookType(), book.title(),
                book.authorityId(), book.language(), book.createdAt()
        );
    }

    public static BookDetailResponse toDetailResponse(BookDetail detail) {
        Book book = detail.book();
        List<ChapterResponse> chapters = detail.rootChapters().stream()
                .map(LibraryDtoMappers::toResponse)
                .toList();
        return new BookDetailResponse(
                book.id(), book.bookType(), book.title(),
                book.authorityId(), book.language(), book.description(),
                jsonFromString(book.metadata()),
                book.createdBy(), book.createdAt(), book.updatedAt(),
                chapters,
                book.muhaqqiqId(),
                book.publisherId(),
                book.publicationPlaceId(),
                book.editionNumber(),
                book.publishedYearHijri(),
                book.publishedYearGregorian(),
                toAuthorityRef(detail.authority()),
                toMuhaqqiqRef(detail.muhaqqiq()),
                toPublisherRef(detail.publisher()),
                toPublicationPlaceRef(detail.publicationPlace())
        );
    }

    private static AuthorityCitationRef toAuthorityRef(Authority a) {
        if (a == null) return null;
        return new AuthorityCitationRef(a.id(), a.name(), a.fullName(), a.deathYearHijri());
    }

    private static MuhaqqiqRef toMuhaqqiqRef(Muhaqqiq m) {
        if (m == null) return null;
        return new MuhaqqiqRef(m.id(), m.name(), m.fullName());
    }

    private static PublisherRef toPublisherRef(Publisher p) {
        if (p == null) return null;
        return new PublisherRef(p.id(), p.name());
    }

    private static PublicationPlaceRef toPublicationPlaceRef(PublicationPlace p) {
        if (p == null) return null;
        return new PublicationPlaceRef(p.id(), p.name());
    }

    public static ChapterResponse toResponse(ChapterNode node) {
        List<ChapterResponse> children = node.children().stream()
                .map(LibraryDtoMappers::toResponse)
                .toList();
        return new ChapterResponse(
                node.chapter().id(), node.chapter().title(),
                node.chapter().orderIndex(), node.chapter().parentChapterId(),
                node.chapter().startPageNumber(),
                children
        );
    }

    public static PageSummaryResponse toSummary(Page page) {
        return new PageSummaryResponse(
                page.id(), page.pageNumber(),
                page.printedPage(), page.part(),
                page.chapterId(),
                page.textContent() != null,
                page.imageUrl() != null
        );
    }

    public static PageResponse toResponse(PageDetail detail) {
        Page page = detail.page();
        List<ImageRegionResponse> regions = detail.regions().stream()
                .map(LibraryDtoMappers::toResponse)
                .toList();
        return new PageResponse(
                page.id(), page.bookId(), page.chapterId(),
                page.pageNumber(),
                page.printedPage(), page.part(), page.pdfPageNumber(),
                page.textContent(), page.imageUrl(),
                jsonFromString(page.formattedContent()),
                regions, page.createdAt(), page.updatedAt()
        );
    }

    public static ImageRegionResponse toResponse(ImageRegion region) {
        return new ImageRegionResponse(
                region.id(), region.x(), region.y(),
                region.width(), region.height(), region.extractedText()
        );
    }

    public static String jsonToString(JsonNode node) {
        return node == null ? null : node.toString();
    }

    static JsonNode jsonFromString(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return JSON.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "БД содержит невалидный JSON в jsonb-колонке: " + raw, e
            );
        }
    }
}
