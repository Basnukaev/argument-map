package ru.basnukaev.argumentmap.library.shamela.etl.dto;

/**
 * Страница из {@code {bookId}.sqlite}. Составной PK
 * {@code (book_id, id)} - {@code id} уникален только в пределах
 * конкретной книги (shamela-соглашение). {@code content} - сырой
 * HTML с inline-разметкой shamela, не парсим, храним как есть.
 */
public record ShamelaPageRow(
        long bookId,
        int id,
        String content,
        String part,
        String printedPage,
        String number,
        String services
) {
}
