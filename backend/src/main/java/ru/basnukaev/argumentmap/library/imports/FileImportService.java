package ru.basnukaev.argumentmap.library.imports;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookContentKind;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.BookVisibility;
import ru.basnukaev.argumentmap.library.domain.LibraryFile;
import ru.basnukaev.argumentmap.library.domain.LibraryFileSourceType;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.PageRepository;
import ru.basnukaev.argumentmap.library.service.BookService;
import ru.basnukaev.argumentmap.library.storage.ObjectStorageProperties;
import ru.basnukaev.argumentmap.library.storage.ObjectStorageService;

/**
 * Импорт пользовательского PDF в library как полноценную {@code Book}
 * с {@code Page[]} (Этап 16.a/16.d, ADR-035). Цепочка:
 *
 * <ol>
 *   <li>InputStream загружается в memory ({@code byte[]}) - PDF в нашем
 *       limit'е 50MB acceptable, две прохода нужны: PDFBox + S3 upload</li>
 *   <li>{@code Loader.loadPDF(byte[])} парсит PDF document</li>
 *   <li>{@code BookService.createBook} создаёт {@code Book} row с
 *       {@code bookType=BOOK}, title из metadata или filename,
 *       {@code metadata.user_uploaded=true}</li>
 *   <li>Для каждой phys-страницы PDF создаётся {@code Page} с
 *       {@code pageNumber=i+1} (1-based), {@code pdfPageNumber=i+1}
 *       (та же phys page), {@code textContent} = извлечённый text</li>
 *   <li>Сам PDF blob через {@link ObjectStorageService#putAndRegister}
 *       сохраняется в {@code library-user-uploads} bucket, ключ
 *       {@code {bookId}/{filename}}, запись в {@code library_files}
 *       с {@code sourceType=USER_UPLOAD}</li>
 * </ol>
 *
 * <p>Транзакционно - вся операция или всё или ничего. Если PDF
 * парсится но S3 put падает - вся транзакция откатывается (book/pages
 * deleted, blob может попасть orphan в bucket - детектируется
 * {@code OrphanDetectionJanitor}).
 *
 * <p>EPUB не реализован сейчас (см. ADR-035) - метод {@code importEpub}
 * добавится отдельным этапом когда появится UX-кейс. Сейчас 100%
 * пользовательских материалов - PDF (shamela / archive.org).
 */
@Service
public class FileImportService {

    private static final Logger log = LoggerFactory.getLogger(FileImportService.class);

    /**
     * PDF без extracted text (например полностью scanned-images PDF -
     * чисто bitmap страницы) возвращает empty string. Сохраняем в
     * {@code text_content} пустую строку - CHECK constraint
     * {@code lib_pages_content_present} требует {@code text_content
     * IS NOT NULL OR image_url IS NOT NULL}, пустая строка не NULL.
     * Текст таких страниц заполнится через будущий AI-recognition pipeline.
     */
    private static final String EMPTY_PAGE_PLACEHOLDER = "";

    private final BookService bookService;
    private final BookRepository bookRepository;
    private final PageRepository pageRepository;
    private final ObjectStorageService objectStorageService;
    private final ObjectStorageProperties storageProperties;

    public FileImportService(BookService bookService,
                             BookRepository bookRepository,
                             PageRepository pageRepository,
                             ObjectStorageService objectStorageService,
                             ObjectStorageProperties storageProperties) {
        this.bookService = bookService;
        this.bookRepository = bookRepository;
        this.pageRepository = pageRepository;
        this.objectStorageService = objectStorageService;
        this.storageProperties = storageProperties;
    }

    /**
     * Импортирует PDF как новую книгу в library. Создаёт Book, Page[]
     * (по одной на phys-страницу PDF), сохраняет PDF blob в
     * {@code library-user-uploads} bucket.
     *
     * @param pdfBytes полный contents PDF в byte array. Caller
     *                 ответственен за enforcement size limit (50MB)
     *                 до вызова - на уровне Spring multipart config
     * @param filename оригинальное имя файла (без path) - используется
     *                 для storage key и fallback title
     * @param metadata опциональные поля от пользователя (title override,
     *                 authorityId, language, description)
     * @param currentUserId UUID юзера-загрузчика (из {@code X-User-Id} header)
     * @return созданная Book
     * @throws FileImportException при corrupted PDF, encrypted PDF без
     *                             пароля, или 0-страничном PDF
     */
    @Transactional
    public ImportResult importPdf(byte[] pdfBytes, String filename,
                                  ImportMetadata metadata, UUID currentUserId) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new FileImportException("PDF файл пустой");
        }
        ImportMetadata effectiveMeta = metadata != null ? metadata : ImportMetadata.empty();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.isEncrypted()) {
                // PDFBox поддерживает decrypt с password, но для MVP
                // user-upload запрещаем encrypted - реализуем по запросу
                throw new FileImportException(
                        "PDF зашифрован - decrypt не поддерживается на текущем этапе");
            }
            int numPages = document.getNumberOfPages();
            if (numPages == 0) {
                throw new FileImportException("PDF не содержит страниц");
            }

            String title = resolveTitle(document, effectiveMeta, filename);
            String language = effectiveMeta.language() != null
                    && !effectiveMeta.language().isBlank()
                    ? effectiveMeta.language() : "ar";
            String metadataJson = buildBookMetadataJson(filename, numPages);

            Book book;
            // ADR-043 Amendment (Этап 22.c): user-uploads через PDF
            // import получают visibility=PRIVATE по умолчанию. Не PUBLIC -
            // user может загружать свои конспекты/черновики, которые
            // не должны быть видны другим без явного sharing
            if (effectiveMeta.hasAcademicData()) {
                // 16.g: пользователь заполнил academic-метаданные в upload
                // форме - используем overload который сделает
                // findOrCreate в lib_muhaqqiqs/publishers/places
                book = bookService.createBook(
                        BookType.BOOK, title, effectiveMeta.authorityId(),
                        language, effectiveMeta.description(),
                        metadataJson, currentUserId,
                        effectiveMeta.muhaqqiqName(),
                        effectiveMeta.publisherName(),
                        effectiveMeta.publicationPlaceName(),
                        effectiveMeta.editionNumber(),
                        effectiveMeta.publishedYearHijri(),
                        effectiveMeta.publishedYearGregorian(),
                        BookVisibility.PRIVATE
                );
            } else {
                // Старый путь (shamela-совместимый): academic FK = null,
                // но visibility=PRIVATE для user-uploads
                book = bookService.createBook(
                        BookType.BOOK, title, effectiveMeta.authorityId(),
                        language, effectiveMeta.description(),
                        metadataJson, currentUserId,
                        null, null, null, null, null, null,
                        BookVisibility.PRIVATE
                );
            }
            log.info("PDF импорт: создана книга id={} title='{}' страниц={} academic={}",
                    book.id(), title, numPages, effectiveMeta.hasAcademicData());

            // page-by-page extraction. PDFTextStripper'у задаём диапазон
            // [i+1, i+1] для одной страницы (API 1-based)
            PDFTextStripper stripper = new PDFTextStripper();
            boolean anyNonBlankText = false;
            for (int i = 0; i < numPages; i++) {
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);
                String pageText;
                try {
                    pageText = stripper.getText(document);
                } catch (IOException e) {
                    throw new FileImportException(
                            "не удалось извлечь текст со страницы " + (i + 1), e);
                }
                anyNonBlankText |= pageText != null && !pageText.isBlank();
                Instant now = Instant.now();
                Page page = new Page(
                        UUID.randomUUID(),
                        book.id(),
                        null,                             // chapterId - PDF без chapter outline
                        i + 1,                            // pageNumber internal
                        null,                             // printedPage - неизвестен для user-upload
                        null,                             // part - single-volume
                        i + 1,                            // pdfPageNumber = phys
                        pageText != null ? pageText : EMPTY_PAGE_PLACEHOLDER,
                        null,                             // imageUrl - text-mode
                        null,                             // formattedContent - не редактировалось (ADR-039)
                        now, now
                );
                pageRepository.save(page);
            }

            // S3 put после save pages - порядок защищает от orphan blob'а
            // в случае page-extraction failure: если PDFTextStripper
            // упадёт на конкретной странице или CHECK constraint
            // lib_pages.* отклонит row - бросаем исключение ДО put в
            // bucket, blob не появляется в storage. При S3 put failure
            // (network etc) после успешного save pages вся транзакция
            // откатится через @Transactional - pages удалятся из БД.
            // Edge case: commit БД упал после успешного S3 put → orphan
            // blob остаётся в bucket'е, ловится OrphanDetectionJanitor
            // (см. Этап 25.b).
            String bucket = storageProperties.buckets().userUploads();
            String storageKey = book.id() + "/" + sanitizeFilename(filename);
            LibraryFile registered = objectStorageService.putAndRegister(
                    bucket, storageKey,
                    new ByteArrayInputStream(pdfBytes),
                    "application/pdf",
                    book.id(), null, LibraryFileSourceType.USER_UPLOAD,
                    null, null);

            // content_kind после записи страниц + регистрации файла:
            // hasFile всегда true (USER_UPLOAD library_files); hasText =
            // хотя бы одна страница с НЕпустым текстом (scanned-image PDF
            // даёт пустые плейсхолдеры → FILE_ONLY).
            bookRepository.updateContentKind(book.id(),
                    BookContentKind.of(anyNonBlankText, true));

            log.info("PDF импорт завершён: book={} pages={} bucket={} key={} sha256={}",
                    book.id(), numPages, bucket, storageKey, registered.contentHash());

            return new ImportResult(book, numPages, registered);
        } catch (IOException e) {
            throw new FileImportException("не удалось разобрать PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Title resolution приоритет: user override > PDF metadata > filename
     * без расширения. Гарантирует non-blank результат - CHECK constraint
     * {@code lib_books.title NOT NULL}.
     */
    private static String resolveTitle(PDDocument document, ImportMetadata meta, String filename) {
        if (meta.title() != null && !meta.title().isBlank()) {
            return meta.title().trim();
        }
        PDDocumentInformation info = document.getDocumentInformation();
        if (info != null) {
            String pdfTitle = info.getTitle();
            if (pdfTitle != null && !pdfTitle.isBlank()) {
                return pdfTitle.trim();
            }
        }
        return stripExtension(filename != null ? filename : "untitled.pdf");
    }

    /**
     * JSON metadata для lib_books.metadata - помечаем источник, размер,
     * чтобы при дальнейшем reindex / debug было видно. Используем
     * простую string concatenation - JSON минимальный, выносить
     * Jackson сюда overhead.
     */
    private static String buildBookMetadataJson(String filename, int numPages) {
        String safeFilename = filename != null ? filename.replace("\"", "\\\"") : "unknown";
        return "{\"user_uploaded\":true,"
                + "\"original_filename\":\"" + safeFilename + "\","
                + "\"pdf_page_count\":" + numPages + "}";
    }

    /**
     * Storage key safe filename - режем path separators (защита от
     * наивного path traversal в bucket'е) и пробелы (S3 принимает но
     * URL-encoded неудобно для debug). Расширение .pdf оставляем.
     */
    private static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload.pdf";
        }
        String base = filename;
        int lastSlash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            base = base.substring(lastSlash + 1);
        }
        return base.replaceAll("\\s+", "_");
    }

    private static String stripExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(0, lastDot) : filename;
    }

    /**
     * Результат успешного import'а - книга + число созданных страниц +
     * метаданные blob'а в catalog. Используется controller'ом для
     * формирования response.
     */
    public record ImportResult(Book book, int pageCount, LibraryFile file) {
    }
}
