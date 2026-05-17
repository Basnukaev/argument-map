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
import ru.basnukaev.argumentmap.library.web.dto.UpdateFormattedContentRequest;
import ru.basnukaev.argumentmap.library.web.mapper.LibraryDtoMappers;
import ru.basnukaev.argumentmap.web.CurrentUser;
import ru.basnukaev.argumentmap.web.dto.PageRequest;
import ru.basnukaev.argumentmap.web.dto.PagedResponse;

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
                currentUserId,
                request.muhaqqiqName(), request.publisherName(),
                request.publicationPlaceName(),
                request.editionNumber(),
                request.publishedYearHijri(),
                request.publishedYearGregorian()
        );
        return ResponseEntity.created(URI.create("/api/v1/library/books/" + created.id()))
                .body(LibraryDtoMappers.toResponse(created));
    }

    /**
     * Пагинированный список книг (Этап pagination).
     *
     * <p>Фильтры (опциональные):
     * <ul>
     *   <li>{@code q} - подстрока в title (case-insensitive)</li>
     *   <li>{@code type} - {@link BookType} (BOOK/JURISPRUDENCE/HADITH/...)</li>
     *   <li>{@code authorityId} - автор книги (UUID)</li>
     *   <li>{@code publisherId} - издатель (UUID, академический справочник)</li>
     * </ul>
     */
    @GetMapping("/books")
    public PagedResponse<BookSummaryResponse> list(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "type", required = false) BookType type,
            @RequestParam(name = "authorityId", required = false) UUID authorityId,
            @RequestParam(name = "publisherId", required = false) UUID publisherId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        PageRequest pr = PageRequest.from(page, size);
        List<Book> items = bookService.listBooksPage(query, type, authorityId, publisherId,
                pr.size(), pr.offset());
        long total = bookService.countBooks(query, type, authorityId, publisherId);
        List<BookSummaryResponse> mapped = items.stream()
                .map(LibraryDtoMappers::toSummary)
                .toList();
        return PagedResponse.of(mapped, pr.page(), pr.size(), total);
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

    /**
     * Сохранение ProseMirror JSON для страницы (Этап 17.0, ADR-039).
     * Tiptap admin editor вызывает этот endpoint после save. Backend
     * принимает любой синтаксически валидный JSON - schema validation
     * (типы node'ов, content model) делается на фронте через Tiptap-
     * extensions, не на backend (см. ADR-039).
     *
     * <p>Возвращает {@link PageResponse} с обновлённым
     * {@code formattedContent} для consistency - фронт может сразу
     * перерендерить страницу без дополнительного GET.
     */
    @PatchMapping("/pages/{pageId}/formatted-content")
    public PageResponse updateFormattedContent(
            @PathVariable UUID pageId,
            @Valid @RequestBody UpdateFormattedContentRequest request) {
        PageDetail detail = bookService.updateFormattedContent(
                pageId,
                request.formattedContent().toString()
        );
        return LibraryDtoMappers.toResponse(detail);
    }
}
