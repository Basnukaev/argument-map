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
    private final JdbcTemplate jdbcTemplate;

    public NodeCitationService(NodeRepository nodeRepository,
                               BookRepository bookRepository,
                               PageRepository pageRepository,
                               SourceRepository sourceRepository,
                               NodeSourceRepository nodeSourceRepository,
                               LibraryFileRepository libraryFileRepository,
                               JdbcTemplate jdbcTemplate) {
        this.nodeRepository = nodeRepository;
        this.bookRepository = bookRepository;
        this.pageRepository = pageRepository;
        this.sourceRepository = sourceRepository;
        this.nodeSourceRepository = nodeSourceRepository;
        this.libraryFileRepository = libraryFileRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

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
        boolean isRegion = req.imageRegionId() != null;
        int activeModes = (isText ? 1 : 0) + (isPdf ? 1 : 0) + (isRegion ? 1 : 0);
        if (activeModes != 1) {
            throw new InvalidCitationException(
                    "Ровно один из (pageId / pdfFileId / imageRegionId) должен быть указан, получено: "
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
        } else {
            ns = NodeSource.regionMode(
                    nodeId, source.id(), req.quote(), req.context(), snapshotLocation,
                    req.imageRegionId(), now);
        }
        nodeSourceRepository.save(ns);

        return nodeSourceRepository.findByPkWithLocation(nodeId, source.id())
                .map(DtoMappers::toResponse)
                .orElseThrow(() -> new IllegalStateException(
                        "node_sources row только что создан но findByPkWithLocation empty"));
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
        return title + ", скан";
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
