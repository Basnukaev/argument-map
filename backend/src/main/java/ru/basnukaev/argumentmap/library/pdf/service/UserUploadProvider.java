package ru.basnukaev.argumentmap.library.pdf.service;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.LibraryFile;
import ru.basnukaev.argumentmap.library.domain.LibraryFileSourceType;
import ru.basnukaev.argumentmap.library.pdf.domain.PdfFileInfo;
import ru.basnukaev.argumentmap.library.pdf.domain.PdfLocation;
import ru.basnukaev.argumentmap.library.pdf.domain.PdfMetadata;
import ru.basnukaev.argumentmap.library.repository.LibraryFileRepository;

/**
 * Provider для книг загруженных пользователем через
 * {@code POST /api/v1/library/imports/file} (Этап 16.b, ADR-035). Книга
 * имеет уже зарегистрированный blob в {@code library_files} с
 * {@code sourceType=USER_UPLOAD}, ничего качать с upstream не надо -
 * provider просто резолвит {@code (bucket, storageKey)} из catalog.
 *
 * <p>{@link Order} = 50 - приоритет выше чем у
 * {@link PdfLinksSourceProvider} (order=100). Для user-uploaded книг
 * это естественный owner: они никогда не имеют {@code pdf_links} в
 * metadata, поэтому даже без явного приоритета {@code supports} вернёт
 * true только здесь. Order закладывается как defensive ordering на случай
 * будущих book'ов которые могут быть и uploaded и иметь fallback на
 * archive.org.
 *
 * <h2>Modelling: один blob = один файл</h2>
 *
 * <p>User upload в Этапе 16 - всегда single PDF (Spring multipart limit 50MB,
 * см. {@code spring.servlet.multipart.max-file-size}). Multi-volume загрузка
 * не реализована. Соответственно {@link #getMetadata} возвращает
 * {@code files = [singleEntry]}, {@link #locateFile} требует {@code fileIndex == 0}.
 *
 * <p>{@code PdfMetadata.root = null} - {@link PdfLinksSourceProvider} использует
 * root + filename для построения upstream URL при cache miss. У user-upload
 * cache miss невозможен (blob уже в catalog) - root не нужен.
 *
 * <p>{@code PdfFileInfo.pageCount} берётся из {@code book.metadata.pdf_page_count}
 * (записывается {@code FileImportService.buildBookMetadataJson}). Если поле
 * отсутствует или невалидно - возвращаем {@code null} (PDF.js на frontend
 * посчитает страницы сам при load).
 */
@Component
@Order(50)
public class UserUploadProvider implements PdfSourceProvider {

    private static final Logger log = LoggerFactory.getLogger(UserUploadProvider.class);
    private static final String CONTENT_TYPE = "application/pdf";

    private final LibraryFileRepository libraryFileRepository;
    private final ObjectMapper objectMapper;

    public UserUploadProvider(LibraryFileRepository libraryFileRepository,
                              ObjectMapper objectMapper) {
        this.libraryFileRepository = libraryFileRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(Book book) {
        if (book == null || book.id() == null) {
            return false;
        }
        return !libraryFileRepository
                .findActiveByBookIdAndSourceType(book.id(), LibraryFileSourceType.USER_UPLOAD)
                .isEmpty();
    }

    @Override
    public PdfMetadata getMetadata(Book book) {
        LibraryFile file = singleUploadedFile(book);
        String label = labelFromStorageKey(file.storageKey());
        Integer pageCount = readPdfPageCount(book);
        PdfFileInfo info = new PdfFileInfo(
                0, // index - всегда 0, user-upload single file
                fileNameFromStorageKey(file.storageKey()),
                label,
                false, // user-upload не имеет понятия cover
                file.sizeBytes(),
                pageCount
        );
        return new PdfMetadata(
                null, // root отсутствует - blob уже в нашем bucket'е
                false,
                file.sizeBytes(),
                List.of(info)
        );
    }

    @Override
    public PdfLocation locateFile(Book book, int fileIndex) {
        if (fileIndex != 0) {
            throw new PdfNotAvailableException(book.id(), fileIndex, 1);
        }
        LibraryFile file = singleUploadedFile(book);
        log.debug("user-upload pdf locate: book={} bucket={} key={}",
                book.id(), file.bucket(), file.storageKey());
        return new PdfLocation(file.bucket(), file.storageKey(), file.sizeBytes(), CONTENT_TYPE);
    }

    /**
     * Резолвит единственный user-uploaded blob книги. Если файлов
     * несколько (теоретически - re-upload до отдельной поддержки
     * multi-volume) - берём самый ранний (catalog отсортирован по
     * {@code downloaded_at}), это исходная загрузка.
     *
     * @throws PdfNotAvailableException если нет ни одного active blob'а
     *         с {@code sourceType=USER_UPLOAD}
     */
    private LibraryFile singleUploadedFile(Book book) {
        List<LibraryFile> files = libraryFileRepository
                .findActiveByBookIdAndSourceType(book.id(), LibraryFileSourceType.USER_UPLOAD);
        if (files.isEmpty()) {
            throw new PdfNotAvailableException(book.id());
        }
        return files.get(0);
    }

    /**
     * Storage key формата {@code {bookId}/{filename}} - filename без
     * uuid префикса. Используется как visible filename в metadata
     * response (например для frontend "скачать как ...").
     */
    private static String fileNameFromStorageKey(String storageKey) {
        if (storageKey == null) {
            return "upload.pdf";
        }
        int slash = storageKey.lastIndexOf('/');
        return slash >= 0 ? storageKey.substring(slash + 1) : storageKey;
    }

    /**
     * Label для UI - filename без расширения. Совпадает с поведением
     * {@link PdfLinksSourceProvider} при отсутствии явного label.
     */
    private static String labelFromStorageKey(String storageKey) {
        String filename = fileNameFromStorageKey(storageKey);
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private Integer readPdfPageCount(Book book) {
        String metadata = book.metadata();
        if (metadata == null || metadata.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(metadata);
            JsonNode field = root.get("pdf_page_count");
            return (field != null && field.canConvertToInt()) ? field.asInt() : null;
        } catch (JsonProcessingException e) {
            log.warn("книга {} содержит невалидный JSON в metadata: {}",
                    book.id(), e.getMessage());
            return null;
        }
    }

    /**
     * Утилитарный аксессор только для тестов - проверка что provider
     * правильно соединил book с blob'ом через repository. В production
     * code не используется.
     */
    Optional<LibraryFile> debugLookup(Book book) {
        return libraryFileRepository
                .findActiveByBookIdAndSourceType(book.id(), LibraryFileSourceType.USER_UPLOAD)
                .stream().findFirst();
    }
}
