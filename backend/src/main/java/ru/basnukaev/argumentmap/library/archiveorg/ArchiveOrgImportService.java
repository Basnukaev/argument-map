package ru.basnukaev.argumentmap.library.archiveorg;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.basnukaev.argumentmap.library.archiveorg.ArchiveOrgPreview.CoverOption;
import ru.basnukaev.argumentmap.library.archiveorg.ArchiveOrgPreview.PdfFileRef;
import ru.basnukaev.argumentmap.library.archiveorg.ArchiveOrgPreview.VolumeGroup;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.BookVisibility;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.pdf.service.PdfFetcher;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.PageRepository;
import ru.basnukaev.argumentmap.library.service.BookService;

/**
 * Импорт книги из archive.org (ADR-056). Две операции:
 * <ul>
 *   <li>{@link #preview(String)} - распарсить metadata + сгруппировать
 *       PDF, без записи в БД;</li>
 *   <li>{@link #importBook(ArchiveOrgImportRequest)} - создать
 *       {@code lib_books} (тип BOOK, PUBLIC, владелец - системный
 *       пользователь) с {@code metadata.pdf_links} (object-form
 *       original+OCR на том), {@code metadata.archive_org_id}, cover_url,
 *       академ. полями (findOrCreate). PDF ленивые (только URL в
 *       pdf_links). Опционально синхронно извлекает текст из OCR-PDF.</li>
 * </ul>
 *
 * <p><b>created_by:</b> archive.org-импорт - admin tooling без
 * пользовательского контента, владелец - фиксированный системный
 * пользователь {@code 00000000-...-0002} (тот же что у hd_collections
 * bridge, миграция 65). visibility=PUBLIC (open library, как shamela).
 *
 * <p><b>Идемпотентность:</b> lookup по {@code metadata->>'archive_org_id'};
 * повторный импорт того же identifier возвращает существующую книгу.
 *
 * <p><b>Извлечение текста (MVP синхронно):</b> при {@code extractText=true}
 * качаем OCR-PDF тома → PDFBox → {@code lib_pages}. {@code testModePages=N}
 * ограничивает N страниц на том (отладка). Полное фоновое извлечение
 * всех томов (+ Tesseract для скан-only) - итерация.
 */
@Service
public class ArchiveOrgImportService {

    private static final Logger log = LoggerFactory.getLogger(ArchiveOrgImportService.class);

    /** Системный пользователь-владелец admin-импортированных книг (миграция 65). */
    static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final ArchiveOrgClient client;
    private final ArchiveOrgMetadataMapper mapper;
    private final BookService bookService;
    private final BookRepository bookRepository;
    private final PageRepository pageRepository;
    private final PdfFetcher pdfFetcher;
    private final ObjectMapper objectMapper;

    public ArchiveOrgImportService(ArchiveOrgClient client,
                                   ArchiveOrgMetadataMapper mapper,
                                   BookService bookService,
                                   BookRepository bookRepository,
                                   PageRepository pageRepository,
                                   PdfFetcher pdfFetcher,
                                   ObjectMapper objectMapper) {
        this.client = client;
        this.mapper = mapper;
        this.bookService = bookService;
        this.bookRepository = bookRepository;
        this.pageRepository = pageRepository;
        this.pdfFetcher = pdfFetcher;
        this.objectMapper = objectMapper;
    }

    /** Превью без записи в БД. */
    public ArchiveOrgPreview preview(String url) {
        String id = client.extractIdentifier(url);
        ArchiveOrgMetadata raw = client.fetchMetadata(id);
        return mapper.toPreview(id, raw);
    }

    /**
     * Импорт книги. Идемпотентно по archive_org_id. Возвращает результат
     * с числом томов, флагом обложки и числом извлечённых страниц.
     */
    @Transactional
    public ArchiveOrgImportResponse importBook(ArchiveOrgImportRequest request) {
        String id = client.extractIdentifier(request.url());

        Optional<Book> existing = bookRepository.findByArchiveOrgId(id);
        if (existing.isPresent()) {
            Book book = existing.get();
            log.info("archive.org import idempotent hit: identifier={} bookId={}", id, book.id());
            return new ArchiveOrgImportResponse(
                    book.id(), id, 0, book.metadata() != null && book.metadata().contains("\"cover\""),
                    0, true);
        }

        ArchiveOrgMetadata raw = client.fetchMetadata(id);
        ArchiveOrgPreview preview = mapper.toPreview(id, raw);
        if (!preview.hasPdf()) {
            throw new ArchiveOrgException(
                    "у archive.org item '" + id + "' нет ни одного PDF - импорт невозможен");
        }

        List<VolumeGroup> volumes = preview.files().stream()
                .filter(g -> VolumeGroup.ROLE_VOLUME.equals(g.role()))
                .toList();
        VolumeGroup cover = preview.files().stream()
                .filter(g -> VolumeGroup.ROLE_COVER.equals(g.role()))
                .findFirst()
                .orElse(null);

        String coverUrl = resolveCoverUrl(request, id, cover);
        String metadataJson = buildMetadataJson(id, client.baseUrl(), cover, volumes);

        String title = firstNonBlank(request.title(), valueOf(preview.title()));
        if (title == null || title.isBlank()) {
            title = id; // CHECK constraint lib_books.title NOT NULL
        }
        String language = firstNonBlank(request.language(), valueOf(preview.language()), "ar");
        String description = firstNonBlank(request.description(), preview.rawDescription());

        Book book = bookService.createBook(
                BookType.BOOK,
                title,
                null,                       // authority - findOrCreate автора отдельная итерация
                language,
                description,
                metadataJson,
                SYSTEM_USER_ID,
                request.muhaqqiqName(),
                request.publisherName(),
                request.placeName(),
                request.editionNumber(),
                request.yearHijri(),
                request.yearGregorian(),
                BookVisibility.PUBLIC
        );

        boolean coverSet = false;
        if (coverUrl != null && !coverUrl.isBlank()) {
            bookRepository.updateCoverUrl(book.id(), coverUrl);
            coverSet = true;
        }

        int pagesExtracted = 0;
        if (request.extractText()) {
            pagesExtracted = extractText(book.id(), volumes, request.testModePages());
        }

        log.info("archive.org import: bookId={} identifier={} volumes={} coverSet={} pages={}",
                book.id(), id, volumes.size(), coverSet, pagesExtracted);
        return new ArchiveOrgImportResponse(
                book.id(), id, volumes.size(), coverSet, pagesExtracted, false);
    }

    // ---------------- cover ----------------

    private String resolveCoverUrl(ArchiveOrgImportRequest request, String id, VolumeGroup cover) {
        String kind = request.coverKind() != null ? request.coverKind() : CoverOption.KIND_THUMBNAIL;
        return switch (kind) {
            case CoverOption.KIND_UPLOAD -> request.coverUrl(); // явный URL загруженной обложки
            case CoverOption.KIND_COVER_PDF_PAGE ->
                // cover-PDF как обложка: ссылка на скан-PDF обложки (фронт рендерит первую страницу)
                    cover != null && cover.original() != null
                            ? cover.original().downloadUrl()
                            : client.baseUrl() + "/services/img/" + id;
            default -> client.baseUrl() + "/services/img/" + id; // thumbnail
        };
    }

    // ---------------- pdf_links (object-form) ----------------

    /**
     * Строит {@code metadata} jsonb: {@code pdf_links} (object-form
     * files[] с variant original/ocr + volumeNo) + {@code archive_org_id}.
     * Обложка (если есть) идёт первым элементом с {@code cover:1} на
     * уровне pdf_links - совместимо с {@code PdfLinksSourceProvider}
     * ({@code cover:1} → files[0] исключается из чтения).
     */
    private String buildMetadataJson(String id, String base,
                                     VolumeGroup cover, List<VolumeGroup> volumes) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("archive_org_id", id);

        ObjectNode pdfLinks = root.putObject("pdf_links");
        pdfLinks.put("root", base + "/download/" + id + "/");

        ArrayNode files = pdfLinks.putArray("files");
        boolean hasCover = cover != null && cover.original() != null;
        if (hasCover) {
            // обложка первым элементом + cover:1 - конвенция PdfLinksSourceProvider
            pdfLinks.put("cover", 1);
            appendFile(files, cover.original(), "original", 0, "Обложка");
            if (cover.ocr() != null) {
                appendFile(files, cover.ocr(), "ocr", 0, "Обложка (OCR)");
            }
        }
        for (VolumeGroup v : volumes) {
            String label = volumes.size() > 1 ? "Том " + v.volumeNo() : "Книга";
            if (v.original() != null) {
                appendFile(files, v.original(), "original", v.volumeNo(), label);
            }
            if (v.ocr() != null) {
                appendFile(files, v.ocr(), "ocr", v.volumeNo(), label + " (OCR)");
            }
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new ArchiveOrgException("не удалось сериализовать metadata для " + id, e);
        }
    }

    private static void appendFile(ArrayNode files, PdfFileRef ref,
                                   String variant, int volumeNo, String label) {
        ObjectNode node = files.addObject();
        node.put("name", ref.name());
        node.put("label", label);
        node.put("variant", variant);
        node.put("volumeNo", volumeNo);
        if (ref.size() != null) {
            node.put("size", ref.size());
        }
    }

    // ---------------- text extraction (MVP синхронно) ----------------

    /**
     * Извлекает текст из OCR-PDF каждого тома в {@code lib_pages}. Если у
     * тома нет OCR-ветви - берём original (скан-PDF может иметь текстовый
     * слой). Если {@code testModePages>0} - максимум N страниц на том.
     * Зеркалит page-creation из {@code FileImportService}.
     *
     * @return суммарное число созданных страниц
     */
    private int extractText(UUID bookId, List<VolumeGroup> volumes, Integer testModePages) {
        int limit = (testModePages != null && testModePages > 0) ? testModePages : Integer.MAX_VALUE;
        int pageCounter = 0;
        for (VolumeGroup v : volumes) {
            PdfFileRef src = v.ocr() != null ? v.ocr() : v.original();
            if (src == null) {
                continue;
            }
            pageCounter = extractVolume(bookId, v.volumeNo(), src, limit, pageCounter);
        }
        return pageCounter;
    }

    private int extractVolume(UUID bookId, int volumeNo, PdfFileRef src,
                              int limit, int pageCounterStart) {
        Path temp = null;
        int pageCounter = pageCounterStart;
        try {
            temp = Files.createTempFile("archiveorg-extract-", ".pdf");
            pdfFetcher.fetch(URI.create(src.downloadUrl()), temp);
            try (PDDocument doc = Loader.loadPDF(temp.toFile())) {
                int numPages = Math.min(doc.getNumberOfPages(), limit);
                PDFTextStripper stripper = new PDFTextStripper();
                for (int i = 0; i < numPages; i++) {
                    stripper.setStartPage(i + 1);
                    stripper.setEndPage(i + 1);
                    String text = stripper.getText(doc);
                    pageCounter++;
                    Instant now = Instant.now();
                    Page page = new Page(
                            UUID.randomUUID(),
                            bookId,
                            null,                       // chapterId - без outline
                            pageCounter,                // pageNumber - сквозной по томам
                            null,                       // printedPage
                            "Том " + volumeNo,          // part - том
                            i + 1,                      // pdfPageNumber - phys в томе
                            text != null ? text : "",   // CHECK: text_content NOT NULL
                            null,                       // imageUrl
                            null,                       // formattedContent
                            now, now
                    );
                    pageRepository.save(page);
                }
            }
            return pageCounter;
        } catch (IOException e) {
            throw new ArchiveOrgException(
                    "не удалось извлечь текст из тома " + volumeNo + " (" + src.downloadUrl() + ")", e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            }
        }
    }

    // ---------------- helpers ----------------

    private static String valueOf(ArchiveOrgPreview.ProvenanceField f) {
        return f != null ? f.value() : null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
