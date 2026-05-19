package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

/**
 * Симметричный {@link DeletedTopicAuditAccessDeniedException} для книг.
 * Бросается когда non-ADMIN пытается прочитать audit удалённой книги
 * через {@code GET /api/v1/audit/books/{id}}.
 *
 * <p>Маппится в {@code 403 Forbidden} с Problem Details
 * {@code type: forbidden-deleted-book-audit}.
 */
public class DeletedBookAuditAccessDeniedException extends RuntimeException {

    private final UUID bookId;
    private final UUID userId;

    public DeletedBookAuditAccessDeniedException(UUID bookId, UUID userId) {
        super("Пользователь " + userId + " не имеет прав на audit удалённой книги " + bookId
                + " (только ADMIN)");
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
