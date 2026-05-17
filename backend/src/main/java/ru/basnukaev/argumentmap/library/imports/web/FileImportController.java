package ru.basnukaev.argumentmap.library.imports.web;

import java.io.IOException;
import java.net.URI;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ru.basnukaev.argumentmap.library.imports.FileImportException;
import ru.basnukaev.argumentmap.library.imports.FileImportService;
import ru.basnukaev.argumentmap.library.imports.FileImportService.ImportResult;
import ru.basnukaev.argumentmap.library.imports.ImportMetadata;
import ru.basnukaev.argumentmap.web.CurrentUser;

/**
 * REST endpoint для пользовательского upload'а PDF в library (Этап 16.b).
 * Multipart/form-data:
 * <ul>
 *   <li>{@code file} (required) - PDF binary, до 50MB (limit в
 *       {@code spring.servlet.multipart.max-file-size})</li>
 *   <li>{@code title} (optional) - override автоматически извлечённого
 *       из PDF metadata</li>
 *   <li>{@code authorityId} (optional) - UUID существующего автора</li>
 *   <li>{@code language} (optional) - ISO 639-1, default {@code "ar"}</li>
 *   <li>{@code description} (optional) - заметки</li>
 *   <li>Academic-поля (Этап 16.g, optional) - {@code muhaqqiqName},
 *       {@code publisherName}, {@code publicationPlaceName},
 *       {@code editionNumber} (1..99), {@code publishedYearHijri} (1..9999),
 *       {@code publishedYearGregorian} (1..9999). Mirror диапазонов из
 *       {@code CreateBookRequest}/{@code UpdateBookRequest}. Если хотя бы
 *       одно заполнено - {@code FileImportService} вызывает 13-args
 *       перегрузку {@code BookService.createBook} с findOrCreate FK</li>
 * </ul>
 *
 * <p>Размер enforce'ится Spring multipart parser'ом до того как
 * controller body будет распарсен - превышение даёт
 * {@code MaxUploadSizeExceededException} → 413 Payload Too Large
 * (handler в {@code GlobalExceptionHandler}).
 *
 * <p>MIME type валидируется через whitelist {@link #ALLOWED_MIME_TYPES} -
 * только {@code application/pdf}. EPUB добавится когда появится
 * implementation (см. ADR-035).
 *
 * <p>Validation academic Integer-полей сделана руками в {@link
 * #validateAcademicRanges} - {@code @RequestParam} с Bean Validation
 * требует {@code @Validated} на классе + handler для
 * {@code HandlerMethodValidationException}, что в проекте нигде не
 * настроено. Ручная валидация бросает {@link FileImportException} →
 * 422 file-import-error с понятным сообщением.
 */
@RestController
@RequestMapping("/api/v1/library/imports")
public class FileImportController {

    private static final Logger log = LoggerFactory.getLogger(FileImportController.class);

    /**
     * Whitelist разрешённых content types. Сейчас только PDF. EPUB
     * (application/epub+zip) добавится когда будет реализован EPUB-import.
     */
    static final Set<String> ALLOWED_MIME_TYPES = Set.of(MediaType.APPLICATION_PDF_VALUE);

    // Mirror диапазонов из CreateBookRequest/UpdateBookRequest DTO (Этап 20.e).
    // Если меняются там - надо менять здесь
    private static final int EDITION_MIN = 1;
    private static final int EDITION_MAX = 99;
    private static final int YEAR_MIN = 1;
    private static final int YEAR_MAX = 9999;

    private final FileImportService fileImportService;

    public FileImportController(FileImportService fileImportService) {
        this.fileImportService = fileImportService;
    }

    @PostMapping(path = "/file",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FileImportResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "authorityId", required = false) UUID authorityId,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "muhaqqiqName", required = false) String muhaqqiqName,
            @RequestParam(value = "publisherName", required = false) String publisherName,
            @RequestParam(value = "publicationPlaceName", required = false) String publicationPlaceName,
            @RequestParam(value = "editionNumber", required = false) Integer editionNumber,
            @RequestParam(value = "publishedYearHijri", required = false) Integer publishedYearHijri,
            @RequestParam(value = "publishedYearGregorian", required = false) Integer publishedYearGregorian,
            @CurrentUser UUID currentUserId) {

        validateFile(file);
        validateAcademicRanges(editionNumber, publishedYearHijri, publishedYearGregorian);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new FileImportException("не удалось прочитать uploaded файл", e);
        }

        ImportMetadata metadata = new ImportMetadata(
                title, authorityId, language, description,
                muhaqqiqName, publisherName, publicationPlaceName,
                editionNumber, publishedYearHijri, publishedYearGregorian
        );
        log.info("file import: filename={} size={}B academic={} by user={}",
                file.getOriginalFilename(), bytes.length,
                metadata.hasAcademicData(), currentUserId);

        ImportResult result = fileImportService.importPdf(
                bytes, file.getOriginalFilename(), metadata, currentUserId);

        FileImportResponse body = new FileImportResponse(
                result.book().id(),
                result.file().fileId(),
                result.pageCount(),
                result.file().contentHash(),
                result.file().sizeBytes(),
                result.file().bucket(),
                result.file().storageKey()
        );

        return ResponseEntity
                .created(URI.create("/api/v1/library/books/" + result.book().id()))
                .body(body);
    }

    /**
     * Pre-validation до чтения bytes - быстрые отказы. Сам PDFBox parsing -
     * в сервисе.
     *
     * @throws FileImportException 422 если файл пустой
     * @throws UnsupportedMediaTypeException 415 если content type не PDF
     */
    private static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileImportException("загруженный файл пустой");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new UnsupportedMediaTypeException(
                    "тип файла " + contentType + " не поддерживается, ожидаются: "
                            + ALLOWED_MIME_TYPES);
        }
    }

    /**
     * Валидация academic Integer-полей (16.g) - mirror диапазонов из
     * {@code CreateBookRequest} / {@code UpdateBookRequest} DTO.
     *
     * @throws FileImportException 422 если значение вне допустимого
     *                             диапазона
     */
    private static void validateAcademicRanges(Integer editionNumber,
                                                Integer publishedYearHijri,
                                                Integer publishedYearGregorian) {
        if (editionNumber != null && (editionNumber < EDITION_MIN || editionNumber > EDITION_MAX)) {
            throw new FileImportException(
                    "editionNumber должен быть в диапазоне " + EDITION_MIN
                            + ".." + EDITION_MAX + ", получено " + editionNumber);
        }
        if (publishedYearHijri != null
                && (publishedYearHijri < YEAR_MIN || publishedYearHijri > YEAR_MAX)) {
            throw new FileImportException(
                    "publishedYearHijri должен быть в диапазоне " + YEAR_MIN
                            + ".." + YEAR_MAX + ", получено " + publishedYearHijri);
        }
        if (publishedYearGregorian != null
                && (publishedYearGregorian < YEAR_MIN || publishedYearGregorian > YEAR_MAX)) {
            throw new FileImportException(
                    "publishedYearGregorian должен быть в диапазоне " + YEAR_MIN
                            + ".." + YEAR_MAX + ", получено " + publishedYearGregorian);
        }
    }
}
