package ru.basnukaev.argumentmap.library.shamela.etl.dto;

import java.util.List;

/**
 * Композитный результат чтения {@code {bookId}.sqlite} - страницы и
 * заголовки книги в одном проходе. Используется
 * {@code ShamelaBookReader#read(Path, long)} чтобы не открывать
 * Connection дважды для одной и той же книги.
 *
 * <p>Списки могут быть пустыми (например для книг ещё не загруженных
 * в shamela), но никогда не {@code null}.
 */
public record ShamelaBookContent(
        List<ShamelaPageRow> pages,
        List<ShamelaTitleRow> titles
) {
}
