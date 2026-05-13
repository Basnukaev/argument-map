package ru.basnukaev.argumentmap.library.pdf.service;

import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.pdf.domain.PdfLocation;
import ru.basnukaev.argumentmap.library.pdf.domain.PdfMetadata;

/**
 * Provider PDF-файлов для книги. Source-agnostic архитектура (см.
 * spec {@code docs/superpowers/specs/2026-05-11-pdf-viewer-source-agnostic.md},
 * 25.b refactor): реализации обслуживают разные источники (shamela
 * через archive.org CDN, прямой archive.org, user-upload, IIIF и т.д.).
 *
 * <p>{@link PdfService} опрашивает все реализации и выбирает первого
 * подходящего provider'а для книги.
 *
 * <p>После 25.b.6 - все PDF идут через object storage (MinIO/S3).
 * Provider отвечает за: cache check в catalog → если нет, download
 * upstream + register. Локальный файловый кеш отсутствует.
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
     * Не качаем сам PDF - это lazy через {@link #locateFile}.
     */
    PdfMetadata getMetadata(Book book);

    /**
     * Резолвит PDF файл в object storage. Если уже есть в catalog -
     * возвращает существующую location. Если нет - качает upstream +
     * регистрирует в {@code library_files} + {@code ObjectStorage} -
     * после чего возвращает location.
     *
     * <p>Idempotent при concurrent calls - повторный resolve того же
     * файла возвращает ту же location.
     */
    PdfLocation locateFile(Book book, int fileIndex);
}
