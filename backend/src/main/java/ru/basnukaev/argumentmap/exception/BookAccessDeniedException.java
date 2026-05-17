package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

/**
 * Бросается когда user не имеет права читать книгу (ADR-043 Amendment).
 *
 * <p>Маппится в {@code 403 Forbidden} с Problem Details
 * {@code type: forbidden-book-access}. bookId и userId включаются
 * в properties для debugging.
 */
public class BookAccessDeniedException extends RuntimeException {

    private final UUID bookId;
    private final UUID userId;

    public BookAccessDeniedException(UUID bookId, UUID userId) {
        super("Пользователь " + userId + " не имеет доступа на чтение к книге " + bookId);
        this.bookId = bookId;
        this.userId = userId;
    }

    public UUID getBookId() {
        return bookId;
    }

    public UUID getUserId() {
        return userId;
    }
}
