package ru.basnukaev.argumentmap.hadith.web;

import java.util.UUID;

/**
 * Бросается когда сборник хадисов не найден по book_id (обратный lookup моста
 * под-проекта #3: книга → сборник). Маппится в 404 collection-not-found через
 * GlobalExceptionHandler.
 */
public class CollectionNotFoundException extends RuntimeException {

    private final UUID bookId;

    public CollectionNotFoundException(UUID bookId) {
        super("Сборник хадисов для книги не найден: " + bookId);
        this.bookId = bookId;
    }

    public UUID getBookId() {
        return bookId;
    }
}
