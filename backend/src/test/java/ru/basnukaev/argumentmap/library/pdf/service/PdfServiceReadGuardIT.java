package ru.basnukaev.argumentmap.library.pdf.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.auth.domain.AuthenticatedUser;
import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.exception.BookAccessDeniedException;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.BookVisibility;
import ru.basnukaev.argumentmap.library.repository.BookRepository;

/**
 * Read-guard IT для {@link PdfService} (ADR-043 Amendment). До фикса
 * GET /pdf и /pdf/info стримили блоб любой книги по bookId без проверки
 * доступа (IDOR) - можно было скачать PRIVATE книгу перебором UUID.
 *
 * <p>Permission-check сидит в {@code loadBook}, который вызывается ДО
 * любого обращения к provider/storage, поэтому тест бьёт по
 * {@code getMetadata} (тоже идёт через loadBook) - 403 кидается раньше
 * чем понадобится MinIO. PdfService читает principal из SecurityContext
 * (не @CurrentUser param), поэтому выставляем контекст вручную.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PdfServiceReadGuardIT {

    @Autowired private PdfService pdfService;
    @Autowired private BookRepository bookRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID ownerId;
    private UUID otherUserId;
    private UUID privateBookId;

    @BeforeEach
    void setUp() {
        ownerId = insertUser();
        otherUserId = insertUser();
        Instant now = Instant.now();
        Book book = bookRepository.save(new Book(
                UUID.randomUUID(), BookType.MANUSCRIPT, "PRIVATE Книга",
                null, "ar", null, null, ownerId, now, now,
                null, null, null, null, null, null,
                BookVisibility.PRIVATE));
        privateBookId = book.id();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getMetadata_privateBook_nonOwner_throws403() {
        authenticateAs(otherUserId, UserRole.USER);

        assertThatThrownBy(() -> pdfService.getMetadata(privateBookId))
                .isInstanceOf(BookAccessDeniedException.class);
    }

    private void authenticateAs(UUID userId, String role) {
        AuthenticatedUser principal = new AuthenticatedUser(
                userId, "u-" + userId, userId + "@e.com", role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        java.util.List.of()));
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                id, "u-" + id, id + "@example.com");
        return id;
    }
}
