package ru.basnukaev.argumentmap.library.pdf.service;

import java.nio.file.Path;

import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.pdf.domain.PdfMetadata;

/**
 * Provider PDF-файлов для книги. Source-agnostic архитектура (см.
 * spec {@code docs/superpowers/specs/2026-05-11-pdf-viewer-source-agnostic.md}):
 * реализации обслуживают разные источники (shamela через archive.org
 * CDN, прямой archive.org, user-upload через MinIO, IIIF и т.д.).
 *
 * <p>{@link PdfService} опрашивает все реализации и выбирает первого
 * подходящего provider'а для книги.
 */
public interface PdfSourceProvider {

    /**
     * Может ли provider обслужить эту книгу. Например
     * {@link PdfLinksSourceProvider} возвращает true только если
     * {@code book.metadata.pdf_links} существует и имеет файлы.
     */
    boolean supports(Book book);

    /**
     * Метаданные PDF: где брать root, список файлов, размер.
     * Не качаем сам PDF - это lazy через {@link #downloadFile}.
     */
    PdfMetadata getMetadata(Book book);

    /**
     * Lazy-скачивание конкретного PDF файла в локальный каталог.
     * На MVP - простой full-download, потом возвращаем path к
     * скачанному файлу. В будущей версии (после MinIO в 25.b) -
     * проверяем кеш и возвращаем cached path.
     *
     * @param book      книга
     * @param fileIndex индекс файла из {@link PdfMetadata#files()}
     *                  (0-based)
     * @param targetDir каталог для размещения файла
     * @return path к скачанному PDF-файлу
     */
    Path downloadFile(Book book, int fileIndex, Path targetDir);
}
