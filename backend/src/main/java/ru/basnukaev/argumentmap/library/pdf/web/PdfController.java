package ru.basnukaev.argumentmap.library.pdf.web;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.library.pdf.domain.PdfMetadata;
import ru.basnukaev.argumentmap.library.pdf.service.PdfService;
import ru.basnukaev.argumentmap.library.web.dto.PdfFileInfoResponse;
import ru.basnukaev.argumentmap.library.web.dto.PdfInfoResponse;

/**
 * REST endpoints для streaming PDF-файлов книг. Source-agnostic
 * (ADR-021): backend сам выбирает provider'а (shamela через
 * archive.org CDN, прямой archive.org, MinIO upload).
 *
 * <p>Производительность - Range header support через
 * {@link ResourceRegion}. PDF.js (frontend react-pdf) запрашивает
 * chunks по 64KB-1MB через Range, не качает весь файл (~50MB) сразу.
 *
 * <p>Chunk-size = 1MB (1024*1024). Если клиент запрашивает больше -
 * обрезаем до chunk-size. Это балансирует между:
 * <ul>
 *   <li>слишком мелкие chunks: много HTTP-запросов, latency накапливается</li>
 *   <li>слишком крупные chunks: память сервера, медленный TTFB</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/library/books/{bookId}/pdf")
public class PdfController {

    private static final Logger log = LoggerFactory.getLogger(PdfController.class);

    /** 1MB - оптимальный chunk для PDF.js range requests. */
    private static final long DEFAULT_CHUNK_SIZE = 1024L * 1024;

    private final PdfService pdfService;

    public PdfController(PdfService pdfService) {
        this.pdfService = pdfService;
    }

    @GetMapping("/info")
    public PdfInfoResponse getPdfInfo(@PathVariable UUID bookId) {
        PdfMetadata meta = pdfService.getMetadata(bookId);
        List<PdfFileInfoResponse> files = meta.files().stream()
                .map(f -> new PdfFileInfoResponse(f.index(), f.label(), f.isCover(),
                        f.sizeBytes(), f.pageCount()))
                .toList();
        return new PdfInfoResponse(meta.hasCover(), meta.totalSizeBytes(), files);
    }

    @GetMapping
    public ResponseEntity<ResourceRegion> streamPdf(
            @PathVariable UUID bookId,
            @RequestParam(defaultValue = "0") int fileIndex,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader
    ) throws IOException {
        Path file = pdfService.getOrDownload(bookId, fileIndex);
        FileSystemResource resource = new FileSystemResource(file);
        long length = resource.contentLength();

        List<HttpRange> ranges = rangeHeader == null
                ? List.of()
                : HttpRange.parseRanges(rangeHeader);

        if (ranges.isEmpty()) {
            // Полный download без Range. На MVP - не chunk'им, отдаём весь
            // файл одним response. PDF.js при первом запросе посмотрит на
            // Accept-Ranges: bytes и далее перейдёт на range-режим
            return ResponseEntity.ok()
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(length)
                    .body(new ResourceRegion(resource, 0, length));
        }

        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(length);
        long end = range.getRangeEnd(length);
        long rangeLength = Math.min(end - start + 1, DEFAULT_CHUNK_SIZE);
        log.debug("pdf range: book={} fileIndex={} start={} length={} total={}",
                bookId, fileIndex, start, rangeLength, length);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentType(MediaType.APPLICATION_PDF)
                .body(new ResourceRegion(resource, start, rangeLength));
    }
}
