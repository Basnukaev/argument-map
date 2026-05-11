package ru.basnukaev.argumentmap.library.shamela.service;

/**
 * Результат {@link ShamelaBookImportService#importBook(long)}: сколько
 * страниц и заголовков было записано/обновлено в {@code lib_shamela_page}/
 * {@code lib_shamela_title} для конкретной книги. {@code majorRelease} -
 * та версия, по которой реально качали архив (взято из staging-записи
 * {@code lib_shamela_book}).
 */
public record BookImportResult(
        long bookId,
        int majorRelease,
        int pagesCount,
        int titlesCount
) {
}
