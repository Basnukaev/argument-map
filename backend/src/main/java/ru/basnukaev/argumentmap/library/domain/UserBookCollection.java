package ru.basnukaev.argumentmap.library.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Запись о книге в personal collection пользователя. Vision 49d
 * Section 2.2.
 *
 * <p>collection_name - "Избранное" по умолчанию или произвольное
 * имя ("Курс по тафсиру", "Прочитано", и т.п.). UNIQUE по
 * (user_id, book_id, collection_name) - книга может быть в нескольких
 * коллекциях того же user'а, но не дублироваться в одной.
 *
 * @param id уникальный surrogate UUID PK
 * @param userId владелец коллекции
 * @param bookId книга в коллекции
 * @param collectionName имя коллекции (default "Избранное")
 * @param addedAt timestamp добавления
 */
public record UserBookCollection(
        UUID id,
        UUID userId,
        UUID bookId,
        String collectionName,
        Instant addedAt
) {
    public static final String DEFAULT_COLLECTION = "Избранное";
}
