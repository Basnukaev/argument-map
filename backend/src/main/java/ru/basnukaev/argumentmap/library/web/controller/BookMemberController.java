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
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.library.domain.BookMember;
import ru.basnukaev.argumentmap.library.service.BookMemberService;
import ru.basnukaev.argumentmap.library.web.dto.AddBookMemberRequest;
import ru.basnukaev.argumentmap.library.web.dto.BookMemberResponse;
import ru.basnukaev.argumentmap.library.web.dto.UpdateBookMemberRequest;
import ru.basnukaev.argumentmap.library.web.mapper.LibraryDtoMappers;
import ru.basnukaev.argumentmap.web.CurrentUser;

/**
 * Управление членами SHARED-книг (ADR-043 Amendment, Этап 22.c). Endpoint'ы
 * зеркалят {@link ru.basnukaev.argumentmap.web.controller.TopicMemberController}:
 * <ul>
 *   <li>POST /api/v1/library/books/{id}/members - добавить (owner)
 *   <li>GET /api/v1/library/books/{id}/members - список (read access к книге)
 *   <li>PATCH /api/v1/library/books/{id}/members/{memberId} - сменить роль (owner)
 *   <li>DELETE /api/v1/library/books/{id}/members/{memberId} - удалить
 *       (owner или self-leave)
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/library/books/{bookId}/members")
public class BookMemberController {

    private final BookMemberService bookMemberService;

    public BookMemberController(BookMemberService bookMemberService) {
        this.bookMemberService = bookMemberService;
    }

    @PostMapping
    public ResponseEntity<BookMemberResponse> add(@PathVariable UUID bookId,
                                                  @Valid @RequestBody AddBookMemberRequest request,
                                                  @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        BookMember added = bookMemberService.addMember(
                bookId, request.userId(), request.role(), userId, role
        );
        return ResponseEntity
                .created(URI.create("/api/v1/library/books/" + bookId + "/members/" + added.id()))
                .body(LibraryDtoMappers.toResponse(added));
    }

    @GetMapping
    public List<BookMemberResponse> list(@PathVariable UUID bookId) {
        // Guest view (roadmap 49.G): GET под permitAll, userId из
        // SecurityContext (null если аноним), не @CurrentUser. listMembers
        // делает assertCanReadBook - аноним видит членов только PUBLIC книги;
        // PRIVATE/SHARED → 403.
        UUID userId = SecurityContextUtils.currentUserIdOrNull();
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        return bookMemberService.listMembers(bookId, userId, role).stream()
                .map(LibraryDtoMappers::toResponse).toList();
    }

    @PatchMapping("/{memberId}")
    public BookMemberResponse update(@PathVariable UUID bookId,
                                     @PathVariable UUID memberId,
                                     @Valid @RequestBody UpdateBookMemberRequest request,
                                     @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        BookMember updated = bookMemberService.updateMemberRole(
                bookId, memberId, request.role(), userId, role
        );
        return LibraryDtoMappers.toResponse(updated);
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> delete(@PathVariable UUID bookId,
                                       @PathVariable UUID memberId,
                                       @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        bookMemberService.removeMember(bookId, memberId, userId, role);
        return ResponseEntity.noContent().build();
    }
}
