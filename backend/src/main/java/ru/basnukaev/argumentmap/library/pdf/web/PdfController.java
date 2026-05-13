package ru.basnukaev.argumentmap.library.pdf.web;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import ru.basnukaev.argumentmap.library.pdf.domain.PdfLocation;
import ru.basnukaev.argumentmap.library.pdf.domain.PdfMetadata;
import ru.basnukaev.argumentmap.library.pdf.service.PdfService;
import ru.basnukaev.argumentmap.library.web.dto.PdfFileInfoResponse;
import ru.basnukaev.argumentmap.library.web.dto.PdfInfoResponse;

/**
 * REST endpoints для streaming PDF-файлов книг. Source-agnostic
 * (ADR-021, ADR-024): backend сам выбирает provider'а, читает PDF
 * напрямую из object storage (MinIO/S3) и проксирует Range chunks
 * клиенту.
 *
 * <p>После 25.b.6 - lazy streaming через {@link StreamingResponseBody}:
 * bytes идут MinIO → backend → frontend без полной загрузки в память.
 * PDF.js (frontend react-pdf) запрашивает chunks по 64KB-1MB через
 * Range header.
 *
 * <p>Chunk-size limit = 1MB (1024*1024). Если клиент запрашивает
 * больше - обрезаем. Балансирует между:
 * <ul>
 *   <li>слишком мелкие chunks: много HTTP-запросов, latency накапливается</li>
 *   <li>слишком крупные chunks: память сервера, медленный TTFB</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/library/books/{bookId}/pdf")
public class PdfController {

    private static final Logger log = LoggerFactory.getLogger(PdfController.class);
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
    public ResponseEntity<StreamingResponseBody> streamPdf(
            @PathVariable UUID bookId,
            @RequestParam(defaultValue = "0") int fileIndex,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader
    ) {
        PdfLocation loc = pdfService.locate(bookId, fileIndex);
        long length = loc.sizeBytes();

        List<HttpRange> ranges = rangeHeader == null
                ? List.of()
                : HttpRange.parseRanges(rangeHeader);

        if (ranges.isEmpty()) {
            // Полный download без Range. PDF.js при первом запросе посмотрит
            // на Accept-Ranges: bytes и далее перейдёт на range-режим
            StreamingResponseBody body = output -> {
                try (var stream = pdfService.openFull(loc)) {
                    stream.transferTo(output);
                }
            };
            return ResponseEntity.ok()
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(length)
                    .body(body);
        }

        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(length);
        long requestedEnd = range.getRangeEnd(length);
        long actualEnd = Math.min(start + DEFAULT_CHUNK_SIZE - 1, requestedEnd);
        long rangeLength = actualEnd - start + 1;

        log.debug("pdf range: book={} fileIndex={} start={} length={} total={}",
                bookId, fileIndex, start, rangeLength, length);

        StreamingResponseBody body = output -> {
            try (var stream = pdfService.openRange(loc, start, actualEnd)) {
                stream.transferTo(output);
            }
        };

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE,
                        "bytes " + start + "-" + actualEnd + "/" + length)
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(rangeLength)
                .body(body);
    }
}
