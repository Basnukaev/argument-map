package ru.basnukaev.argumentmap.library.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.exception.BookMemberNotFoundException;
import ru.basnukaev.argumentmap.exception.BookNotFoundException;
import ru.basnukaev.argumentmap.exception.BookWriteAccessDeniedException;
import ru.basnukaev.argumentmap.library.domain.BookMember;
import ru.basnukaev.argumentmap.library.domain.BookMemberRole;
import ru.basnukaev.argumentmap.library.repository.BookMemberRepository;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.service.PermissionService;

/**
 * Управление членами SHARED-книг (ADR-043 Amendment, Этап 22.c).
 *
 * <p>Membership API доступен только для owner книги (или ADMIN). EDITOR
 * не может добавлять/удалять других членов - это privilege escalation
 * (EDITOR сделал бы себя owner-equivalent). MEMBER не может управлять
 * никем кроме самого себя (delete self).
 *
 * <p>Аналог {@link ru.basnukaev.argumentmap.service.TopicMemberService}.
 */
@Service
public class BookMemberService {

    private final BookMemberRepository bookMemberRepository;
    private final BookRepository bookRepository;
    private final PermissionService permissionService;

    public BookMemberService(BookMemberRepository bookMemberRepository,
                             BookRepository bookRepository,
                             PermissionService permissionService) {
        this.bookMemberRepository = bookMemberRepository;
        this.bookRepository = bookRepository;
        this.permissionService = permissionService;
    }

    @Transactional
    public BookMember addMember(UUID bookId, UUID newMemberUserId, String role,
                                UUID actorUserId, String actorRole) {
        if (!BookMemberRole.isValid(role)) {
            throw new IllegalArgumentException(
                    "Невалидная роль: " + role + " (ожидается MEMBER/EDITOR)"
            );
        }
        bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
        permissionService.assertIsBookOwner(bookId, actorUserId, actorRole);

        bookRepository.findById(bookId).ifPresent(b -> {
            if (b.createdBy() != null && b.createdBy().equals(newMemberUserId)) {
                throw new IllegalArgumentException(
                        "Owner книги не может быть добавлен как member"
                );
            }
        });

        BookMember member = new BookMember(
                UUID.randomUUID(), bookId, newMemberUserId,
                role, Instant.now(), actorUserId
        );
        try {
            return bookMemberRepository.save(member);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException(
                    "Пользователь " + newMemberUserId + " уже является членом книги " + bookId
            );
        }
    }

    @Transactional(readOnly = true)
    public List<BookMember> listMembers(UUID bookId, UUID actorUserId, String actorRole) {
        bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
        permissionService.assertCanReadBook(bookId, actorUserId, actorRole);
        return bookMemberRepository.findByBookId(bookId);
    }

    /**
     * Удаляет члена книги. Owner всегда может удалять, member может
     * удалить только себя (self-leave). EDITOR ≠ owner поэтому
     * EDITOR не может удалить другого EDITOR'а.
     */
    @Transactional
    public void removeMember(UUID bookId, UUID memberId,
                             UUID actorUserId, String actorRole) {
        BookMember member = bookMemberRepository.findById(memberId)
                .orElseThrow(() -> new BookMemberNotFoundException(memberId));
        if (!member.bookId().equals(bookId)) {
            throw new BookMemberNotFoundException(memberId);
        }

        boolean isSelfLeave = member.userId().equals(actorUserId);
        boolean isOwnerOrAdmin = permissionService.isBookOwner(bookId, actorUserId, actorRole);
        if (!isSelfLeave && !isOwnerOrAdmin) {
            throw new BookWriteAccessDeniedException(bookId, actorUserId);
        }

        bookMemberRepository.delete(memberId);
    }

    @Transactional
    public BookMember updateMemberRole(UUID bookId, UUID memberId, String newRole,
                                       UUID actorUserId, String actorRole) {
        if (!BookMemberRole.isValid(newRole)) {
            throw new IllegalArgumentException(
                    "Невалидная роль: " + newRole + " (ожидается MEMBER/EDITOR)"
            );
        }
        BookMember existing = bookMemberRepository.findById(memberId)
                .orElseThrow(() -> new BookMemberNotFoundException(memberId));
        if (!existing.bookId().equals(bookId)) {
            throw new BookMemberNotFoundException(memberId);
        }
        permissionService.assertIsBookOwner(bookId, actorUserId, actorRole);

        bookMemberRepository.updateRole(memberId, newRole);
        return bookMemberRepository.findById(memberId).orElseThrow();
    }
}
