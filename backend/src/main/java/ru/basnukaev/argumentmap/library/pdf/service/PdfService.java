package ru.basnukaev.argumentmap.library.pdf.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import ru.basnukaev.argumentmap.exception.BookNotFoundException;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.pdf.domain.PdfLocation;
import ru.basnukaev.argumentmap.library.pdf.domain.PdfMetadata;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.storage.ObjectStorageService;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 * Роутер PDF-провайдеров. Опрашивает все
 * {@link PdfSourceProvider} bean'ы в Spring-контексте (отсортированы
 * по {@code @Order}), выбирает первого подходящего для книги.
 *
 * <p>На MVP - один provider ({@link PdfLinksSourceProvider}).
 * Будущие: UserUploadProvider (order=50) для книг загруженных
 * пользователем в {@code library-user-uploads}, ArchiveOrgDirect для
 * книг с прямым archive.org ID без shamela-импорта.
 *
 * <p>После 25.b.6 - PDF идут напрямую через
 * {@link ObjectStorageService}. Никакого локального файлового кеша:
 * controller использует {@link #openRange} / {@link #openFull} для
 * streaming bytes из MinIO в HTTP response.
 */
@Service
public class PdfService {

    private final List<PdfSourceProvider> providers;
    private final BookRepository bookRepository;
    private final ObjectStorageService objectStorageService;

    public PdfService(List<PdfSourceProvider> providers,
                      BookRepository bookRepository,
                      ObjectStorageService objectStorageService) {
        this.providers = providers;
        this.bookRepository = bookRepository;
        this.objectStorageService = objectStorageService;
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
     * Резолвит location PDF файла в object storage. Lazy: при cache
     * miss provider скачает upstream + зарегистрирует в catalog/MinIO.
     */
    public PdfLocation locate(UUID bookId, int fileIndex) {
        Book book = loadBook(bookId);
        PdfSourceProvider provider = findProvider(book)
                .orElseThrow(() -> new PdfNotAvailableException(bookId));
        return provider.locateFile(book, fileIndex);
    }

    /**
     * Открывает full-file stream из object storage. Caller должен
     * закрыть stream (обычно через try-with-resources в controller).
     */
    public ResponseInputStream<GetObjectResponse> openFull(PdfLocation loc) {
        return objectStorageService.get(loc.bucket(), loc.storageKey());
    }

    /**
     * Открывает byte-range stream из object storage. Параметры
     * соответствуют HTTP Range header semantics: оба inclusive,
     * {@code endInclusive} может превышать реальный размер - S3 вернёт
     * {@code 206 Partial Content} с обрезанным диапазоном.
     */
    public ResponseInputStream<GetObjectResponse> openRange(
            PdfLocation loc, long startInclusive, long endInclusive) {
        return objectStorageService.getRange(
                loc.bucket(), loc.storageKey(), startInclusive, endInclusive);
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
}
