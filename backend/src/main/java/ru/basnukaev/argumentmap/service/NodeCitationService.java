package ru.basnukaev.argumentmap.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.domain.NodeSource;
import ru.basnukaev.argumentmap.domain.PdfBbox;
import ru.basnukaev.argumentmap.domain.Source;
import ru.basnukaev.argumentmap.domain.SourceType;
import ru.basnukaev.argumentmap.exception.BookNotFoundException;
import ru.basnukaev.argumentmap.exception.ImageRegionNotFoundException;
import ru.basnukaev.argumentmap.exception.InvalidCitationException;
import ru.basnukaev.argumentmap.exception.NodeNotFoundException;
import ru.basnukaev.argumentmap.exception.PageNotFoundException;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.LibraryFile;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.pdf.service.PdfNotAvailableException;
import ru.basnukaev.argumentmap.library.pdf.service.PdfService;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.LibraryFileRepository;
import ru.basnukaev.argumentmap.library.repository.PageRepository;
import ru.basnukaev.argumentmap.repository.NodeRepository;
import ru.basnukaev.argumentmap.repository.NodeSourceRepository;
import ru.basnukaev.argumentmap.repository.SourceRepository;
import ru.basnukaev.argumentmap.web.dto.CitationRequest;
import ru.basnukaev.argumentmap.web.dto.NodeSourceResponse;
import ru.basnukaev.argumentmap.web.mapper.DtoMappers;

/**
 * Сервис создания positional citation (Этап 18.f, ADR-026 + ADR-027).
 * Принимает {@link CitationRequest} в одном из трёх режимов (TEXT/PDF/REGION),
 * выполняет валидацию, ensure-or-create Source per book, insert в
 * node_sources с positional полями и snapshot location, возвращает
 * расширенный {@link NodeSourceResponse} с computed location через JOIN.
 */
@Service
public class NodeCitationService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final NodeRepository nodeRepository;
    private final BookRepository bookRepository;
    private final PageRepository pageRepository;
    private final SourceRepository sourceRepository;
    private final NodeSourceRepository nodeSourceRepository;
    private final LibraryFileRepository libraryFileRepository;
    private final PdfService pdfService;
    private final PermissionService permissionService;
    private final JdbcTemplate jdbcTemplate;

    public NodeCitationService(NodeRepository nodeRepository,
                               BookRepository bookRepository,
                               PageRepository pageRepository,
                               SourceRepository sourceRepository,
                               NodeSourceRepository nodeSourceRepository,
                               LibraryFileRepository libraryFileRepository,
                               PdfService pdfService,
                               PermissionService permissionService,
                               JdbcTemplate jdbcTemplate) {
        this.nodeRepository = nodeRepository;
        this.bookRepository = bookRepository;
        this.pageRepository = pageRepository;
        this.sourceRepository = sourceRepository;
        this.nodeSourceRepository = nodeSourceRepository;
        this.libraryFileRepository = libraryFileRepository;
        this.pdfService = pdfService;
        this.permissionService = permissionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Создание structured citation с write-guard (ADR-043). Sibling
     * freeform-пути NodeSourceService.attachSource - тот же контентный
     * mutating-эффект (вставка в node_sources), поэтому требует тех же
     * write-прав на тему узла. Без этого guard на /sources обходился бы
     * через /citations (sibling-path bypass). Резолвит topicId узла и
     * ассертит assertCanWrite.
     *
     * @throws NodeNotFoundException если узла нет (404)
     * @throws ru.basnukaev.argumentmap.exception.TopicWriteAccessDeniedException
     *         если нет write-доступа к теме узла (403)
     */
    @Transactional
    public NodeSourceResponse createCitation(UUID nodeId, CitationRequest req,
                                             UUID actorUserId, String actorRole) {
        ru.basnukaev.argumentmap.domain.Node node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
        permissionService.assertCanWrite(node.topicId(), actorUserId, actorRole);
        return createCitation(nodeId, req);
    }

    /**
     * Legacy overload без permission-check. Internal callers + IT.
     * REST endpoint должен звать
     * {@link #createCitation(UUID, CitationRequest, UUID, String)}.
     */
    @Transactional
    public NodeSourceResponse createCitation(UUID nodeId, CitationRequest req) {
        if (nodeRepository.findById(nodeId).isEmpty()) {
            throw new NodeNotFoundException(nodeId);
        }
        if (req.bookId() == null) {
            throw new InvalidCitationException("bookId обязателен");
        }
        Book book = bookRepository.findById(req.bookId())
                .orElseThrow(() -> new BookNotFoundException(req.bookId()));

        boolean isText = req.pageId() != null;
        boolean isPdf = req.pdfFileId() != null;
        boolean isPdfLink = req.pdfFileIndex() != null;
        boolean isRegion = req.imageRegionId() != null;
        int activeModes = (isText ? 1 : 0) + (isPdf ? 1 : 0) + (isPdfLink ? 1 : 0) + (isRegion ? 1 : 0);
        if (activeModes != 1) {
            throw new InvalidCitationException(
                    "Ровно один из (pageId / pdfFileId / pdfFileIndex / imageRegionId) должен быть указан, получено: "
                            + activeModes);
        }

        Page page = null;
        LibraryFile pdfFile = null;

        if (isText) {
            if (req.rangeStart() == null || req.rangeEnd() == null) {
                throw new InvalidCitationException("rangeStart и rangeEnd обязательны для TEXT mode");
            }
            if (req.rangeStart() < 0 || req.rangeEnd() <= req.rangeStart()) {
                throw new InvalidCitationException(
                        "Невалидный range: rangeStart=" + req.rangeStart()
                                + " rangeEnd=" + req.rangeEnd());
            }
            page = pageRepository.findById(req.pageId())
                    .orElseThrow(() -> new PageNotFoundException(req.pageId()));
            if (!page.bookId().equals(req.bookId())) {
                throw new InvalidCitationException(
                        "pageId=" + req.pageId() + " не принадлежит bookId=" + req.bookId());
            }
        }
        if (isPdf) {
            if (req.pdfPageNumber() == null || req.pdfPageNumber() < 1) {
                throw new InvalidCitationException("pdfPageNumber >= 1 обязателен для PDF mode");
            }
            if (req.pdfBbox() == null) {
                throw new InvalidCitationException("pdfBbox обязателен для PDF mode");
            }
            pdfFile = libraryFileRepository.findById(req.pdfFileId())
                    .filter(f -> f.deletedAt() == null)
                    .orElseThrow(() -> new PdfNotAvailableException(req.pdfFileId()));
        }
        if (isPdfLink) {
            // PDF_LINK (ADR-067): FILE_ONLY archive.org-скан без library_files.
            // Адресуется 0-based ordinal'ом в pdf_links.files[]. Валидируем
            // bounds против PdfService.getMetadata (читает только
            // lib_books.metadata, без blob-download).
            if (req.pdfFileIndex() < 0) {
                throw new InvalidCitationException("pdfFileIndex >= 0 обязателен для PDF_LINK mode");
            }
            if (req.pdfPageNumber() == null || req.pdfPageNumber() < 1) {
                throw new InvalidCitationException("pdfPageNumber >= 1 обязателен для PDF_LINK mode");
            }
            if (req.pdfBbox() == null) {
                throw new InvalidCitationException("pdfBbox обязателен для PDF_LINK mode");
            }
            int fileCount = pdfService.getMetadata(req.bookId()).files().size();
            if (req.pdfFileIndex() >= fileCount) {
                throw new InvalidCitationException(
                        "pdfFileIndex=" + req.pdfFileIndex() + " вне диапазона: книга bookId="
                                + req.bookId() + " имеет " + fileCount + " PDF-файлов");
            }
        }
        if (isRegion) {
            // FK ON DELETE RESTRICT на lib_image_regions гарантирует валидность
            // при insert. Здесь explicit check для понятного error message
            // на не-существующий регион
            Integer count = jdbcCount("SELECT COUNT(*) FROM lib_image_regions WHERE id = ?",
                    req.imageRegionId());
            if (count == null || count == 0) {
                throw new ImageRegionNotFoundException(req.imageRegionId());
            }
        }

        Source source = sourceRepository.findByBookId(req.bookId()).orElseGet(() -> {
            Source created = new Source(
                    UUID.randomUUID(),
                    SourceType.BOOK,
                    book.title(),
                    null,
                    null,
                    book.authorityId(),
                    req.bookId(),
                    null,
                    Instant.now()
            );
            return sourceRepository.upsertByBookId(created);
        });

        String snapshotLocation = buildLocationSnapshot(book, page, pdfFile, req);

        Instant now = Instant.now();
        NodeSource ns;
        if (isText) {
            ns = NodeSource.textMode(
                    nodeId, source.id(), req.quote(), req.context(), snapshotLocation,
                    req.pageId(), req.rangeStart(), req.rangeEnd(), now);
        } else if (isPdf) {
            ns = NodeSource.pdfMode(
                    nodeId, source.id(), req.quote(), req.context(), snapshotLocation,
                    req.pdfFileId(), req.pdfPageNumber(), pdfBboxToJson(req.pdfBbox()), now);
        } else if (isPdfLink) {
            ns = NodeSource.pdfLinkMode(
                    nodeId, source.id(), req.quote(), req.context(), snapshotLocation,
                    req.pdfFileIndex(), req.pdfPageNumber(), pdfBboxToJson(req.pdfBbox()), now);
        } else {
            ns = NodeSource.regionMode(
                    nodeId, source.id(), req.quote(), req.context(), snapshotLocation,
                    req.imageRegionId(), now);
        }
        nodeSourceRepository.save(ns);

        return nodeSourceRepository.findByIdWithLocation(ns.id())
                .map(DtoMappers::toResponse)
                .orElseThrow(() -> new IllegalStateException(
                        "node_sources row только что создан но findByIdWithLocation empty"));
    }

    private String buildLocationSnapshot(Book book, Page page, LibraryFile pdfFile, CitationRequest req) {
        // Display location: только book.title + Т.X стр.Y. Range (char offsets)
        // используется только для технического highlight через query param и
        // НЕ показывается в location string - не соответствует традиции
        // исламской academic citation, где сноска = библиография без позиций
        // символов (полная academic metadata в Этапе 20, ADR-028).
        String title = book.title() != null ? book.title() : "?";
        if (page != null) {
            String part = page.part() != null ? page.part() : "?";
            String printedOrInternal = page.printedPage() != null
                    ? page.printedPage()
                    : String.valueOf(page.pageNumber());
            return title + ", Т." + part + " стр." + printedOrInternal;
        }
        if (pdfFile != null) {
            return title + ", PDF стр." + req.pdfPageNumber();
        }
        if (req.pdfFileIndex() != null) {
            // PDF_LINK (ADR-067): денормализуем 1-based номер тома + label
            // в snapshot, чтобы дрейф порядка pdf_links.files[] при реимпорте
            // был человеко-обнаружим (ordinal стабилен by-design, но
            // snapshot - страховка).
            return title + ", PDF т." + (req.pdfFileIndex() + 1)
                    + pdfVolumeLabelSuffix(req.bookId(), req.pdfFileIndex())
                    + ", стр." + req.pdfPageNumber();
        }
        return title + ", скан";
    }

    /**
     * Суффикс с label тома из pdf_links.files[] для денормализации в
     * snapshot (ADR-067). Best-effort: при любой ошибке metadata -
     * пустая строка (snapshot не критичен для целостности).
     */
    private String pdfVolumeLabelSuffix(UUID bookId, int fileIndex) {
        try {
            var files = pdfService.getMetadata(bookId).files();
            if (fileIndex >= 0 && fileIndex < files.size()) {
                String label = files.get(fileIndex).label();
                if (label != null && !label.isBlank()) {
                    return " (" + label + ")";
                }
            }
        } catch (RuntimeException ignored) {
            // snapshot best-effort - не блокируем citation из-за metadata
        }
        return "";
    }

    private String pdfBboxToJson(PdfBbox bbox) {
        try {
            return JSON.writeValueAsString(bbox);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать PdfBbox: " + bbox, e);
        }
    }

    private Integer jdbcCount(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }
}
