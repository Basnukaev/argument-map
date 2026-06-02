package ru.basnukaev.argumentmap.library.archiveorg;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.basnukaev.argumentmap.library.archiveorg.ArchiveOrgPreview.CoverOption;
import ru.basnukaev.argumentmap.library.archiveorg.ArchiveOrgPreview.ProvenanceField;
import ru.basnukaev.argumentmap.library.archiveorg.ArchiveOrgPreview.VolumeGroup;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookContentKind;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.BookVisibility;
import ru.basnukaev.argumentmap.library.imports.BookMetadataExtractionService;
import ru.basnukaev.argumentmap.library.imports.ExtractedBookMetadata;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.service.BookService;

/**
 * Импорт книги из archive.org (ADR-056, amendment b). Две операции:
 * <ul>
 *   <li>{@link #preview(String)} - распарсить metadata + сгруппировать
 *       PDF, без записи в БД. Метаданные обогащаются LLM
 *       ({@link BookMetadataExtractionService}, ADR-058) как primary,
 *       regex-парсер описания - fallback;</li>
 *   <li>{@link #importBook(ArchiveOrgImportRequest)} - создать
 *       {@code lib_books} (тип BOOK, PUBLIC, владелец - системный
 *       пользователь) с {@code metadata.pdf_links} (только original
 *       Image-Container PDF), {@code metadata.archive_org_id}, cover_url,
 *       академ. полями (findOrCreate). PDF ленивые (только URL в
 *       pdf_links). content_kind всегда FILE_ONLY.</li>
 * </ul>
 *
 * <p><b>FILE_ONLY (ADR-056 amendment b):</b> текст из archive.org мы НЕ
 * извлекаем. Их OCR-PDF ({@code *_text.pdf}) портят арабский (источник
 * «абракадабры»), а наш собственный OCR удалён (ADR-057). archive.org-книги
 * читаются как сканы → {@code content_kind=FILE_ONLY}, {@code lib_pages}
 * не создаются.
 *
 * <p><b>created_by:</b> archive.org-импорт - admin tooling без
 * пользовательского контента, владелец - фиксированный системный
 * пользователь {@code 00000000-...-0002} (тот же что у hd_collections
 * bridge, миграция 65). visibility=PUBLIC (open library, как shamela).
 *
 * <p><b>Идемпотентность:</b> lookup по {@code metadata->>'archive_org_id'};
 * повторный импорт того же identifier возвращает существующую книгу.
 */
@Service
public class ArchiveOrgImportService {

    private static final Logger log = LoggerFactory.getLogger(ArchiveOrgImportService.class);

    /** Системный пользователь-владелец admin-импортированных книг (миграция 65). */
    static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    /** Разделитель авторов для single-string поля превью (арабская запятая). */
    private static final String AUTHOR_JOINER = " ، ";

    private final ArchiveOrgClient client;
    private final ArchiveOrgMetadataMapper mapper;
    private final BookMetadataExtractionService metadataExtractionService;
    private final BookService bookService;
    private final BookRepository bookRepository;
    private final ObjectMapper objectMapper;

    public ArchiveOrgImportService(ArchiveOrgClient client,
                                   ArchiveOrgMetadataMapper mapper,
                                   BookMetadataExtractionService metadataExtractionService,
                                   BookService bookService,
                                   BookRepository bookRepository,
                                   ObjectMapper objectMapper) {
        this.client = client;
        this.mapper = mapper;
        this.metadataExtractionService = metadataExtractionService;
        this.bookService = bookService;
        this.bookRepository = bookRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Превью без записи в БД. Поверх regex-baseline (мгновенный) при
     * включённом LLM прогоняем AI-извлечение метаданных и предпочитаем его
     * значения. ВНИМАНИЕ: при настроенном ключе превью может занять 5-15с
     * (вызов LLM); default (ключ "disabled") - мгновенный regex.
     */
    public ArchiveOrgPreview preview(String url) {
        String id = client.extractIdentifier(url);
        ArchiveOrgMetadata raw = client.fetchMetadata(id);
        ArchiveOrgPreview base = mapper.toPreview(id, raw);
        return enrichWithAi(base);
    }

    /**
     * Накладывает AI-извлечённые метаданные (ADR-058) поверх regex-baseline.
     * Для каждого gap-поля предпочитаем AI-значение (если непустое), иначе
     * оставляем regex-значение. Provenance не меняется: и AI, и regex берут
     * данные из того же archive.org-описания, поэтому заполненное поле
     * остаётся {@code archive_org}; только реально отсутствующие - missing.
     * Graceful: если LLM disabled/упал - {@code extract} вернёт empty и
     * превью останется regex-only.
     */
    private ArchiveOrgPreview enrichWithAi(ArchiveOrgPreview base) {
        Optional<ExtractedBookMetadata> aiOpt =
                metadataExtractionService.extract(base.rawDescription());
        if (aiOpt.isEmpty()) {
            return base;
        }
        ExtractedBookMetadata ai = aiOpt.get();
        return new ArchiveOrgPreview(
                base.archiveOrgId(),
                prefer(ai.titleAr(), base.title()),
                prefer(joinAuthors(ai.authors()), base.author()),
                prefer(ai.publisher(), base.publisher()),
                prefer(ai.place(), base.place()),
                base.muhaqqiq(), // мухаккык AI отдельно не извлекает
                prefer(editionValue(ai), base.edition()),
                prefer(toStr(ai.yearHijri()), base.yearHijri()),
                prefer(toStr(ai.yearGregorian()), base.yearGregorian()),
                prefer(toStr(ai.volumes()), base.volumes()),
                base.language(), // язык - чистое metadata-поле, AI не нужен
                base.rawDescription(),
                base.files(),
                base.coverOptions(),
                base.hasPdf());
    }

    /**
     * AI-значение в приоритете: если непустое - {@code archive_org}, иначе
     * fallback на regex-поле (оно само уже archive_org либо missing).
     */
    private static ProvenanceField prefer(String aiValue, ProvenanceField regexField) {
        if (aiValue != null && !aiValue.isBlank()) {
            return ProvenanceField.of(aiValue);
        }
        return regexField;
    }

    /** Номер издания цифрой, иначе текст издания (LLM мог вернуть строку). */
    private static String editionValue(ExtractedBookMetadata ai) {
        if (ai.editionNumber() != null) {
            return String.valueOf(ai.editionNumber());
        }
        return ai.editionText();
    }

    /** Список авторов → одна строка через арабскую запятую (форма поля превью). */
    private static String joinAuthors(List<String> authors) {
        if (authors == null || authors.isEmpty()) {
            return null;
        }
        return String.join(AUTHOR_JOINER, authors);
    }

    private static String toStr(Integer value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Импорт книги. Идемпотентно по archive_org_id. Возвращает результат
     * с числом томов и флагом обложки. content_kind всегда FILE_ONLY,
     * lib_pages не создаются.
     */
    @Transactional
    public ArchiveOrgImportResponse importBook(ArchiveOrgImportRequest request) {
        String id = client.extractIdentifier(request.url());

        Optional<Book> existing = bookRepository.findByArchiveOrgId(id);
        if (existing.isPresent()) {
            Book book = existing.get();
            log.info("archive.org import idempotent hit: identifier={} bookId={}", id, book.id());
            return new ArchiveOrgImportResponse(
                    book.id(), id, 0, book.coverUrl() != null, 0, true);
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

        // content_kind всегда FILE_ONLY: pdf_links непуст (hasPdf проверен),
        // текст не извлекаем (ADR-056 amendment b). of(false, true) = FILE_ONLY.
        bookRepository.updateContentKind(book.id(), BookContentKind.of(false, true));

        log.info("archive.org import: bookId={} identifier={} volumes={} coverSet={}",
                book.id(), id, volumes.size(), coverSet);
        return new ArchiveOrgImportResponse(
                book.id(), id, volumes.size(), coverSet, 0, false);
    }

    // ---------------- cover ----------------

    private String resolveCoverUrl(ArchiveOrgImportRequest request, String id, VolumeGroup cover) {
        String kind = request.coverKind() != null ? request.coverKind() : CoverOption.KIND_THUMBNAIL;
        return switch (kind) {
            case CoverOption.KIND_UPLOAD -> request.coverUrl(); // явный URL загруженной обложки
            case CoverOption.KIND_COVER_PDF_PAGE ->
                // cover-PDF как обложка: ссылка на скан-PDF обложки (фронт рендерит первую страницу)
                    cover != null
                            ? cover.downloadUrl()
                            : client.baseUrl() + "/services/img/" + id;
            default -> client.baseUrl() + "/services/img/" + id; // thumbnail
        };
    }

    // ---------------- pdf_links (object-form) ----------------

    /**
     * Строит {@code metadata} jsonb: {@code pdf_links} (object-form files[]
     * с volumeNo, только original PDF) + {@code archive_org_id}. Обложка
     * (если есть) идёт первым элементом с {@code cover:1} на уровне
     * pdf_links - совместимо с {@code PdfLinksSourceProvider}
     * ({@code cover:1} → files[0] исключается из чтения). OCR-варианты НЕ
     * пишутся (ADR-056 amendment b).
     */
    private String buildMetadataJson(String id, String base,
                                     VolumeGroup cover, List<VolumeGroup> volumes) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("archive_org_id", id);

        ObjectNode pdfLinks = root.putObject("pdf_links");
        pdfLinks.put("root", base + "/download/" + id + "/");

        ArrayNode files = pdfLinks.putArray("files");
        if (cover != null) {
            // обложка первым элементом + cover:1 - конвенция PdfLinksSourceProvider
            pdfLinks.put("cover", 1);
            appendFile(files, cover);
        }
        for (VolumeGroup v : volumes) {
            appendFile(files, v);
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new ArchiveOrgException("не удалось сериализовать metadata для " + id, e);
        }
    }

    /**
     * Один файл pdf_links. {@code variant} всегда {@code original} (OCR
     * отброшены) - оставлен для forward-compat с PdfLinksSourceProvider.
     * {@code volumeNo} использует более поздняя фаза (per-volume навигация).
     */
    private static void appendFile(ArrayNode files, VolumeGroup v) {
        ObjectNode node = files.addObject();
        node.put("name", v.name());
        node.put("label", v.label());
        node.put("variant", "original");
        node.put("volumeNo", v.volumeNo());
        if (v.sizeBytes() != null) {
            node.put("size", v.sizeBytes());
        }
    }

    // ---------------- helpers ----------------

    private static String valueOf(ProvenanceField f) {
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
