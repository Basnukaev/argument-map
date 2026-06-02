package ru.basnukaev.argumentmap.library.imports;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ru.basnukaev.argumentmap.exception.BookNotFoundException;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.PageRepository;
import ru.basnukaev.argumentmap.library.storage.ObjectStorageProperties;
import ru.basnukaev.argumentmap.library.storage.ObjectStorageService;

/**
 * Upload изображений-сканов страниц книги в MinIO bucket
 * {@code library-page-images} (Этап 17.a, ADR-041). Третий способ
 * добавить страницу в библиотеку - после shamela ETL (text-only) и
 * file import (PDF text extraction). Подходит для рукописей и редких
 * книг где text layer отсутствует.
 *
 * <p>Цепочка:
 * <ol>
 *   <li>Валидация MIME из {@link #ALLOWED_MIME_TYPES} + size (Spring
 *       multipart limit). EPUB/PDF не принимаются - для них есть
 *       {@code FileImportService}</li>
 *   <li>Поиск существующей {@code Page} по {@code (bookId, pageNumber)}.
 *       Если нет - создаётся новая с {@code text_content=""} (placeholder
 *       для CHECK constraint) + {@code image_url=null}</li>
 *   <li>S3 put в {@code library-page-images} bucket, ключ
 *       {@code {bookId}/page-{pageNumber}.{ext}}. Один файл на страницу;
 *       re-upload overwrites previous (S3 versioning сохранит историю)</li>
 *   <li>Обновление {@code Page} с image_bucket/key/uploaded_at (ADR-057:
 *       OCR pipeline удалён - субстрат для будущего AI-recognition)</li>
 * </ol>
 *
 * <p>Транзакционно: создание/обновление {@code Page} row + S3 put в
 * одной транзакции. Если S3 падает после Page-row commit (rare race) -
 * page без image_storage_key остаётся, повторный upload исправит.
 * Orphan blob в bucket'е (S3 put прошёл, БД не закомитилась) - ловится
 * {@code OrphanDetectionJanitor}.
 */
@Service
public class PageImageService {

    private static final Logger log = LoggerFactory.getLogger(PageImageService.class);

    /**
     * Whitelist content types для сканов. JPEG/PNG для типичных
     * scan workflows, TIFF для high-quality archival scans, WEBP
     * для optimized web delivery (некоторые ETL pipelines уже
     * конвертируют scan в WEBP для меньшего размера).
     */
    public static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            "image/webp",
            "image/tiff"
    );

    /**
     * Placeholder для {@code text_content} новой image-only страницы.
     * Пустая строка удовлетворяет CHECK constraint
     * {@code lib_pages_content_present} (text_content IS NOT NULL OR
     * image_url IS NOT NULL) без необходимости заполнения image_url -
     * который остаётся NULL, фронт читает image_bucket/storage_key.
     * Реальный text заполнится через будущий AI-recognition pipeline.
     */
    private static final String EMPTY_TEXT_PLACEHOLDER = "";

    private final PageRepository pageRepository;
    private final BookRepository bookRepository;
    private final ObjectStorageService objectStorageService;
    private final ObjectStorageProperties storageProperties;

    public PageImageService(PageRepository pageRepository,
                            BookRepository bookRepository,
                            ObjectStorageService objectStorageService,
                            ObjectStorageProperties storageProperties) {
        this.pageRepository = pageRepository;
        this.bookRepository = bookRepository;
        this.objectStorageService = objectStorageService;
        this.storageProperties = storageProperties;
    }

    /**
     * Загрузить image страницы. Если page для данной {@code (bookId,
     * pageNumber)} не существует - создаётся новая.
     *
     * @param bookId UUID существующей книги (валидируется через
     *               {@link BookNotFoundException})
     * @param pageNumber внутренний номер страницы (1-based, > 0)
     * @param file multipart file с изображением. Validation MIME и
     *             non-empty - в caller'е (controller)
     * @return обновлённая {@code Page} с image pointer
     * @throws BookNotFoundException 404 если bookId не существует
     * @throws PageImageException 422 при ошибке чтения/записи
     */
    @Transactional
    public Page uploadPageImage(UUID bookId, int pageNumber, MultipartFile file) {
        if (bookRepository.findById(bookId).isEmpty()) {
            throw new BookNotFoundException(bookId);
        }
        if (pageNumber <= 0) {
            throw new PageImageException(
                    "pageNumber должен быть > 0, получено " + pageNumber);
        }

        Page page = pageRepository.findByBookAndPageNumber(bookId, pageNumber)
                .orElseGet(() -> createPlaceholderPage(bookId, pageNumber));

        String bucket = storageProperties.buckets().pageImages();
        String storageKey = buildStorageKey(bookId, pageNumber, file);
        Instant uploadedAt = Instant.now();

        try (InputStream content = file.getInputStream()) {
            objectStorageService.put(bucket, storageKey, content,
                    file.getContentType());
        } catch (IOException e) {
            throw new PageImageException(
                    "не удалось прочитать uploaded image для bookId="
                            + bookId + " page=" + pageNumber, e);
        }

        boolean updated = pageRepository.updateImagePointer(
                page.id(), bucket, storageKey, uploadedAt);
        if (!updated) {
            // race: page была удалена между findByBookAndPageNumber и update.
            // Не должен происходить в @Transactional, но защищаемся.
            throw new PageImageException(
                    "page " + page.id() + " была удалена во время upload");
        }
        log.info("page image uploaded: bookId={} pageNumber={} bucket={} key={} size={}B",
                bookId, pageNumber, bucket, storageKey, file.getSize());

        return pageRepository.findById(page.id()).orElseThrow();
    }

    /**
     * Создать placeholder Page для image-only upload. {@code text_content=""}
     * пустая строка (не NULL) удовлетворяет CHECK constraint. Реальный
     * text появится через будущий AI-recognition pipeline (ADR-057).
     */
    private Page createPlaceholderPage(UUID bookId, int pageNumber) {
        Instant now = Instant.now();
        Page candidate = new Page(
                UUID.randomUUID(),
                bookId,
                null,                                // chapterId - unknown без context
                pageNumber,
                null,                                // printedPage
                null,                                // part
                null,                                // pdfPageNumber
                EMPTY_TEXT_PLACEHOLDER,              // text_content - заполнится после OCR
                null,                                // imageUrl - frontend читает через image_bucket
                null,                                // formattedContent
                now,
                now
        );
        pageRepository.save(candidate);
        return candidate;
    }

    /**
     * Storage key стабилен: {@code {bookId}/page-{pageNumber}.{ext}}.
     * Re-upload того же page → overwrite в bucket'е (S3 versioning
     * сохраняет историю, latest доступна для чтения).
     *
     * <p>Расширение берётся из content type, не из filename - filename
     * пользовательский и может быть произвольный (например {@code IMG_001.JPG}
     * вместо {@code .jpeg}). Mapping mime → ext эквивалентен whitelist'у в
     * {@link #ALLOWED_MIME_TYPES}.
     */
    private static String buildStorageKey(UUID bookId, int pageNumber,
                                           MultipartFile file) {
        String ext = mimeToExtension(file.getContentType());
        return bookId + "/page-" + pageNumber + "." + ext;
    }

    private static String mimeToExtension(String mime) {
        return switch (mime) {
            case MediaType.IMAGE_JPEG_VALUE -> "jpg";
            case MediaType.IMAGE_PNG_VALUE -> "png";
            case "image/webp" -> "webp";
            case "image/tiff" -> "tiff";
            default -> throw new PageImageException(
                    "не поддерживаемый MIME для image upload: " + mime);
        };
    }
}
