package ru.basnukaev.argumentmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.exception.BookAccessDeniedException;
import ru.basnukaev.argumentmap.exception.BookWriteAccessDeniedException;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookMember;
import ru.basnukaev.argumentmap.library.domain.BookMemberRole;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.BookVisibility;
import ru.basnukaev.argumentmap.library.repository.BookMemberRepository;
import ru.basnukaev.argumentmap.library.repository.BookRepository;

/**
 * IT для PermissionService.canReadBook/canWriteBook (ADR-043 Amendment,
 * Этап 22.c). Mirror of {@link PermissionServiceIT} - vis matrix для
 * library books.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PermissionServiceBookIT {

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookMemberRepository bookMemberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ownerId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        ownerId = insertUser("owner");
        otherUserId = insertUser("other");
    }

    private UUID insertUser(String suffix) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                id, "user-" + id + "-" + suffix, id + "-" + suffix + "@test.com"
        );
        return id;
    }

    private UUID insertBook(UUID createdBy, String visibility) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Book b = new Book(id, BookType.BOOK, "T", null, "ar",
                null, null, createdBy, now, now,
                null, null, null, null, null, null, visibility);
        bookRepository.save(b);
        return id;
    }

    private void addMember(UUID bookId, UUID userId, String role) {
        BookMember m = new BookMember(
                UUID.randomUUID(), bookId, userId, role,
                Instant.now(), ownerId
        );
        bookMemberRepository.save(m);
    }

    // ---- canReadBook ----

    @Test
    void canReadBook_PRIVATE_ownerCanRead() {
        UUID bookId = insertBook(ownerId, BookVisibility.PRIVATE);
        assertThat(permissionService.canReadBook(bookId, ownerId, UserRole.USER)).isTrue();
    }

    @Test
    void canReadBook_PRIVATE_nonOwnerCannotRead() {
        UUID bookId = insertBook(ownerId, BookVisibility.PRIVATE);
        assertThat(permissionService.canReadBook(bookId, otherUserId, UserRole.USER)).isFalse();
    }

    @Test
    void canReadBook_SHARED_memberCanRead() {
        UUID bookId = insertBook(ownerId, BookVisibility.SHARED);
        addMember(bookId, otherUserId, BookMemberRole.MEMBER);
        assertThat(permissionService.canReadBook(bookId, otherUserId, UserRole.USER)).isTrue();
    }

    @Test
    void canReadBook_SHARED_nonMemberCannotRead() {
        UUID bookId = insertBook(ownerId, BookVisibility.SHARED);
        assertThat(permissionService.canReadBook(bookId, otherUserId, UserRole.USER)).isFalse();
    }

    @Test
    void canReadBook_PUBLIC_anyAuthenticatedCanRead() {
        UUID bookId = insertBook(ownerId, BookVisibility.PUBLIC);
        assertThat(permissionService.canReadBook(bookId, otherUserId, UserRole.USER)).isTrue();
    }

    @Test
    void canReadBook_ADMIN_bypassAllChecks() {
        UUID bookId = insertBook(ownerId, BookVisibility.PRIVATE);
        assertThat(permissionService.canReadBook(bookId, otherUserId, UserRole.ADMIN)).isTrue();
    }

    // ---- canWriteBook ----

    @Test
    void canWriteBook_SHARED_EDITORcanWrite_MEMBERcannot() {
        UUID bookId = insertBook(ownerId, BookVisibility.SHARED);
        UUID editorUserId = insertUser("editor");
        UUID memberUserId = insertUser("member");
        addMember(bookId, editorUserId, BookMemberRole.EDITOR);
        addMember(bookId, memberUserId, BookMemberRole.MEMBER);

        assertThat(permissionService.canWriteBook(bookId, editorUserId, UserRole.USER)).isTrue();
        assertThat(permissionService.canWriteBook(bookId, memberUserId, UserRole.USER)).isFalse();
    }

    @Test
    void canWriteBook_PUBLIC_nonOwnerCannotWrite_unlessEDITOR() {
        UUID bookId = insertBook(ownerId, BookVisibility.PUBLIC);
        UUID editorUserId = insertUser("editor-pub");
        addMember(bookId, editorUserId, BookMemberRole.EDITOR);

        // обычный (не EDITOR) - read да, write нет
        assertThat(permissionService.canReadBook(bookId, otherUserId, UserRole.USER)).isTrue();
        assertThat(permissionService.canWriteBook(bookId, otherUserId, UserRole.USER)).isFalse();

        // EDITOR может write
        assertThat(permissionService.canWriteBook(bookId, editorUserId, UserRole.USER)).isTrue();
    }

    // ---- asserts ----

    @Test
    void assertCanReadBook_PRIVATE_nonOwner_throws403() {
        UUID bookId = insertBook(ownerId, BookVisibility.PRIVATE);
        assertThatThrownBy(() -> permissionService.assertCanReadBook(bookId, otherUserId, UserRole.USER))
                .isInstanceOf(BookAccessDeniedException.class);
    }

    @Test
    void assertCanWriteBook_SHARED_MEMBER_throwsWriteDenied() {
        UUID bookId = insertBook(ownerId, BookVisibility.SHARED);
        addMember(bookId, otherUserId, BookMemberRole.MEMBER);
        assertThatThrownBy(() -> permissionService.assertCanWriteBook(bookId, otherUserId, UserRole.USER))
                .isInstanceOf(BookWriteAccessDeniedException.class);
    }
}
