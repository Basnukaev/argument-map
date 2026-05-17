package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

/**
 * Бросается когда user может читать книгу, но не имеет права писать
 * (ADR-043 Amendment). Типично - SHARED книга с ролью MEMBER, либо
 * PUBLIC книга для non-owner.
 *
 * <p>Маппится в {@code 403 Forbidden} с Problem Details
 * {@code type: forbidden-book-write}.
 */
public class BookWriteAccessDeniedException extends RuntimeException {

    private final UUID bookId;
    private final UUID userId;

    public BookWriteAccessDeniedException(UUID bookId, UUID userId) {
        super("Пользователь " + userId + " не имеет прав на запись в книгу " + bookId);
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
