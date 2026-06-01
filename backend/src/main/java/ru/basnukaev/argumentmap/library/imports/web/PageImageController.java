package ru.basnukaev.argumentmap.library.imports.web;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.imports.PageImageException;
import ru.basnukaev.argumentmap.library.imports.PageImageService;
import ru.basnukaev.argumentmap.library.service.BookService;
import ru.basnukaev.argumentmap.library.service.PageDetail;
import ru.basnukaev.argumentmap.library.web.dto.PageResponse;
import ru.basnukaev.argumentmap.library.web.mapper.LibraryDtoMappers;
import ru.basnukaev.argumentmap.web.CurrentUser;

/**
 * REST endpoint для загрузки image-сканов страниц книги (Этап 17.a,
 * ADR-041). Третий способ внести страницы в библиотеку (после shamela
 * ETL и file import PDF). Одна страница - один файл.
 *
 * <p>Multipart/form-data:
 * <ul>
 *   <li>{@code file} (required) - image binary
 *       ({@code image/jpeg|png|webp|tiff})</li>
 *   <li>{@code pageNumber} (required, query param) - внутренний номер
 *       страницы 1-based. Если page с таким номером уже существует -
 *       overwrite image (S3 versioning сохранит историю)</li>
 * </ul>
 *
 * <p>Возвращает {@link PageResponse} с обновлёнными полями
 * {@code imageBucket}/{@code imageStorageKey}/{@code ocrStatus=PENDING}.
 * Размер до 20MB enforce'ится Spring multipart parser'ом - превышение
 * даёт {@code MaxUploadSizeExceededException} → 413 Payload Too Large
 * (handler в {@code GlobalExceptionHandler}).
 *
 * <p>OCR не запускается автоматически - см. отдельный
 * {@code POST /api/v1/library/pages/{pageId}/ocr} endpoint (Этап 17.b).
 * Это позволяет batch-uploader сначала залить все страницы и потом
 * триггерить OCR пачкой.
 */
@RestController
@RequestMapping("/api/v1/library/books")
public class PageImageController {

    private static final Logger log = LoggerFactory.getLogger(PageImageController.class);

    private final PageImageService pageImageService;
    private final BookService bookService;

    public PageImageController(PageImageService pageImageService,
                                BookService bookService) {
        this.pageImageService = pageImageService;
        this.bookService = bookService;
    }

    @PostMapping(path = "/{bookId}/pages",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PageResponse> uploadPageImage(
            @PathVariable UUID bookId,
            @RequestParam("pageNumber") int pageNumber,
            @RequestParam("file") MultipartFile file,
            @CurrentUser UUID currentUserId) {

        validateFile(file);

        // ADR-043 Amendment: write-guard - upload перезаписывает image
        // страницы + сбрасывает ocr_status, поэтому требует write-доступ
        // к книге. Раньше шло без проверки (любой мог затереть чужую
        // PRIVATE книгу).
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        bookService.assertCanWriteBook(bookId, currentUserId, role);

        log.info("page image upload: bookId={} pageNumber={} size={}B contentType={} by user={}",
                bookId, pageNumber, file.getSize(), file.getContentType(), currentUserId);

        Page page = pageImageService.uploadPageImage(bookId, pageNumber, file);
        PageDetail detail = bookService.getPage(page.id());

        return ResponseEntity.ok(LibraryDtoMappers.toResponse(detail));
    }

    private static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new PageImageException("загруженный файл пустой");
        }
        String contentType = file.getContentType();
        if (contentType == null
                || !PageImageService.ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new UnsupportedMediaTypeException(
                    "тип файла " + contentType + " не поддерживается для page image, "
                            + "ожидаются: " + PageImageService.ALLOWED_MIME_TYPES);
        }
    }
}
