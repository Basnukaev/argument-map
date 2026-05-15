package ru.basnukaev.argumentmap.library.web.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.service.BookDetail;
import ru.basnukaev.argumentmap.library.service.BookService;
import ru.basnukaev.argumentmap.library.service.PageDetail;
import ru.basnukaev.argumentmap.library.web.dto.BookDetailResponse;
import ru.basnukaev.argumentmap.library.web.dto.BookResponse;
import ru.basnukaev.argumentmap.library.web.dto.BookSummaryResponse;
import ru.basnukaev.argumentmap.library.web.dto.CreateBookRequest;
import ru.basnukaev.argumentmap.library.web.dto.PageResponse;
import ru.basnukaev.argumentmap.library.web.dto.PageSummaryResponse;
import ru.basnukaev.argumentmap.library.web.dto.UpdateBookRequest;
import ru.basnukaev.argumentmap.library.web.mapper.LibraryDtoMappers;
import ru.basnukaev.argumentmap.web.CurrentUser;

@RestController
@RequestMapping("/api/v1/library")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @PostMapping("/books")
    public ResponseEntity<BookResponse> create(
            @Valid @RequestBody CreateBookRequest request,
            @CurrentUser UUID currentUserId) {
        Book created = bookService.createBook(
                request.bookType(), request.title(), request.authorityId(),
                request.language(), request.description(),
                LibraryDtoMappers.jsonToString(request.metadata()),
                currentUserId
        );
        return ResponseEntity.created(URI.create("/api/v1/library/books/" + created.id()))
                .body(LibraryDtoMappers.toResponse(created));
    }

    @GetMapping("/books")
    public List<BookSummaryResponse> list(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "type", required = false) BookType type) {
        return bookService.listBooks(query, type).stream()
                .map(LibraryDtoMappers::toSummary)
                .toList();
    }

    @GetMapping("/books/{bookId}")
    public BookDetailResponse getOne(@PathVariable UUID bookId) {
        BookDetail detail = bookService.getBookWithChapters(bookId);
        return LibraryDtoMappers.toDetailResponse(detail);
    }

    /**
     * Partial update academic metadata через {@link UpdateBookRequest}
     * (Этап 20.d, BookEditModal). Title/authority/description/metadata не
     * меняются через этот endpoint - только мухаккик, издатель, место
     * издания, номер издания, годы по хиджре и григориану.
     */
    @PatchMapping("/books/{bookId}")
    public BookDetailResponse update(@PathVariable UUID bookId,
                                     @Valid @RequestBody UpdateBookRequest request) {
        bookService.updateAcademicMetadata(
                bookId,
                request.muhaqqiqName(),
                request.publisherName(),
                request.publicationPlaceName(),
                request.editionNumber(),
                request.publishedYearHijri(),
                request.publishedYearGregorian()
        );
        BookDetail detail = bookService.getBookWithChapters(bookId);
        return LibraryDtoMappers.toDetailResponse(detail);
    }

    @DeleteMapping("/books/{bookId}")
    public ResponseEntity<Void> delete(@PathVariable UUID bookId) {
        bookService.deleteBook(bookId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/books/{bookId}/pages")
    public List<PageSummaryResponse> listPages(
            @PathVariable UUID bookId,
            @RequestParam(name = "from", required = false) Integer fromPage,
            @RequestParam(name = "to", required = false) Integer toPage) {
        return bookService.listPages(bookId, fromPage, toPage).stream()
                .map(LibraryDtoMappers::toSummary)
                .toList();
    }

    @GetMapping("/pages/{pageId}")
    public PageResponse getPage(@PathVariable UUID pageId) {
        PageDetail detail = bookService.getPage(pageId);
        return LibraryDtoMappers.toResponse(detail);
    }
}
