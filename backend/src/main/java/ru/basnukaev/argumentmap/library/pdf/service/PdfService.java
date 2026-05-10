package ru.basnukaev.argumentmap.library.pdf.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import ru.basnukaev.argumentmap.exception.BookNotFoundException;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.pdf.domain.PdfMetadata;
import ru.basnukaev.argumentmap.library.repository.BookRepository;

/**
 * Роутер PDF-провайдеров. Опрашивает все
 * {@link PdfSourceProvider} bean'ы в Spring-контексте (отсортированы
 * по {@code @Order}), выбирает первого подходящего для книги.
 *
 * <p>На MVP - один provider ({@link PdfLinksSourceProvider}).
 * Будущие: MinioCacheProvider (order=10) перехватывает cached
 * requests раньше всех, ArchiveOrgDirectProvider для книг с
 * прямым archive.org ID без shamela-импорта.
 *
 * <p>{@code tempDir} - каталог для скачанных PDF на MVP без MinIO.
 * Будет заменён MinIO-кешом в 25.b.
 */
@Service
public class PdfService {

    private static final Logger log = LoggerFactory.getLogger(PdfService.class);

    private final List<PdfSourceProvider> providers;
    private final BookRepository bookRepository;
    private final Path tempDir;

    public PdfService(List<PdfSourceProvider> providers,
                      BookRepository bookRepository,
                      @Value("${library.pdf.temp-dir:${java.io.tmpdir}/argmap-pdf}") String tempDirPath) {
        this.providers = providers;
        this.bookRepository = bookRepository;
        this.tempDir = Path.of(tempDirPath);
        ensureDir(this.tempDir);
        log.info("PdfService init: tempDir={} providers={}", this.tempDir,
                providers.stream().map(p -> p.getClass().getSimpleName()).toList());
    }

    /**
     * Метадата PDF для книги - где брать, список файлов, размер.
     * Не качает сам PDF.
     *
     * @throws BookNotFoundException если книга не существует
     * @throws PdfNotAvailableException если книга не имеет PDF source
     */
    public PdfMetadata getMetadata(UUID bookId) {
        Book book = loadBook(bookId);
        return findProvider(book)
                .map(p -> p.getMetadata(book))
                .orElseThrow(() -> new PdfNotAvailableException(bookId));
    }

    /**
     * Lazy download конкретного файла. Возвращает path к локально
     * сохранённому PDF. После 25.b - path будет к MinIO-cached версии
     * (download только при cache miss).
     */
    public Path getOrDownload(UUID bookId, int fileIndex) {
        Book book = loadBook(bookId);
        PdfSourceProvider provider = findProvider(book)
                .orElseThrow(() -> new PdfNotAvailableException(bookId));
        Path bookDir = tempDir.resolve(bookId.toString());
        return provider.downloadFile(book, fileIndex, bookDir);
    }

    private Book loadBook(UUID bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
    }

    private Optional<PdfSourceProvider> findProvider(Book book) {
        return providers.stream()
                .filter(p -> p.supports(book))
                .findFirst();
    }

    private static void ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("не удалось создать temp каталог для PDF: " + dir, e);
        }
    }
}
