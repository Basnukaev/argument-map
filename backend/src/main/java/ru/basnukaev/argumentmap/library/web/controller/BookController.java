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
import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.BookVisibility;
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
import ru.basnukaev.argumentmap.library.web.dto.UpdateBookVisibilityRequest;
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
        // ADR-043 Amendment: REST POST оставляет visibility=PUBLIC по
        // умолчанию (open library default), пользователь может позже
        // сменить через PATCH /visibility. Поведение существующих
        // shamela ETL preservenо.
        Book created = bookService.createBook(
                request.bookType(), request.title(), request.authorityId(),
                request.language(), request.description(),
                LibraryDtoMappers.jsonToString(request.metadata()),
                currentUserId,
                request.muhaqqiqName(), request.publisherName(),
                request.publicationPlaceName(),
                request.editionNumber(),
                request.publishedYearHijri(),
                request.publishedYearGregorian(),
                BookVisibility.PUBLIC
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
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "sort", required = false) String sort,
            @CurrentUser UUID currentUserId) {
        // ADR-043 Amendment: visibility filter применяется на repository
        // уровне через listVisibleBooksPage. PRIVATE owned + SHARED member
        // + PUBLIC видны user'у. ADMIN видит всё.
        // Vision 49d Section 2.1: sort whitelist (recent/popular/alphabetical).
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        PageRequest pr = PageRequest.from(page, size);
        List<Book> items = bookService.listVisibleBooksPage(currentUserId, role,
                query, type, authorityId, publisherId,
                pr.size(), pr.offset(), sort);
        long total = bookService.countVisibleBooks(currentUserId, role,
                query, type, authorityId, publisherId);
        List<BookSummaryResponse> mapped = items.stream()
                .map(LibraryDtoMappers::toSummary)
                .toList();
        return PagedResponse.of(mapped, pr.page(), pr.size(), total);
    }

    /** Vision 49d Phase 2 - POST view increment endpoint */
    @PostMapping("/books/{bookId}/views")
    public ResponseEntity<Void> incrementView(@PathVariable UUID bookId) {
        bookService.incrementViewCount(bookId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/books/{bookId}")
    public BookDetailResponse getOne(@PathVariable UUID bookId,
                                     @CurrentUser UUID currentUserId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        BookDetail detail = bookService.getBookWithChapters(bookId, currentUserId, role);
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
                                     @Valid @RequestBody UpdateBookRequest request,
                                     @CurrentUser UUID currentUserId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        bookService.updateAcademicMetadata(
                bookId,
                request.muhaqqiqName(),
                request.publisherName(),
                request.publicationPlaceName(),
                request.editionNumber(),
                request.publishedYearHijri(),
                request.publishedYearGregorian(),
                currentUserId, role
        );
        BookDetail detail = bookService.getBookWithChapters(bookId, currentUserId, role);
        return LibraryDtoMappers.toDetailResponse(detail);
    }

    /**
     * Меняет visibility книги (ADR-043 Amendment). Только owner (или ADMIN).
     */
    @PatchMapping("/books/{bookId}/visibility")
    public BookResponse updateVisibility(@PathVariable UUID bookId,
                                         @Valid @RequestBody UpdateBookVisibilityRequest request,
                                         @CurrentUser UUID currentUserId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        Book updated = bookService.updateVisibility(bookId, request.visibility(),
                currentUserId, role);
        return LibraryDtoMappers.toResponse(updated);
    }

    @DeleteMapping("/books/{bookId}")
    public ResponseEntity<Void> delete(@PathVariable UUID bookId,
                                       @CurrentUser UUID currentUserId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        bookService.deleteBook(bookId, currentUserId, role);
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
            @Valid @RequestBody UpdateFormattedContentRequest request,
            @CurrentUser UUID userId) {
        // ADR-043 Amendment: write-guard на parent book страницы
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        PageDetail detail = bookService.updateFormattedContent(
                pageId,
                request.formattedContent().toString(),
                userId, role
        );
        return LibraryDtoMappers.toResponse(detail);
    }
}
