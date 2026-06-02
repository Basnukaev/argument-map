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
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import ru.basnukaev.argumentmap.library.pdf.domain.PdfMetadata;
import ru.basnukaev.argumentmap.library.pdf.domain.PdfStreamingResult;
import ru.basnukaev.argumentmap.library.pdf.domain.RangeSpec;
import ru.basnukaev.argumentmap.library.pdf.service.PdfService;
import ru.basnukaev.argumentmap.library.pdf.service.RangeNotSatisfiableException;
import ru.basnukaev.argumentmap.library.web.dto.PdfFileInfoResponse;
import ru.basnukaev.argumentmap.library.web.dto.PdfInfoResponse;

/**
 * REST endpoints для streaming PDF-файлов книг. Source-agnostic
 * (ADR-021, ADR-024, ADR-023 amendment): backend сам выбирает
 * provider'а, провайдер сам решает откуда читать (MinIO bucket или
 * upstream archive.org через Range forwarding).
 *
 * <p>25.d.5 - lazy streaming через {@code PdfService.openStream}:
 * <ul>
 *   <li>cache hit (MinIO) → провайдер открывает {@code GetObjectRequest.range()}
 *       стрим, bytes идут S3 → backend → клиент</li>
 *   <li>cache miss + Range → провайдер открывает HTTP Range к archive.org,
 *       bytes идут upstream → backend → клиент (без буферизации в памяти)</li>
 *   <li>cache miss + full → провайдер синхронно скачивает + кеширует +
 *       стримит из MinIO (legacy path для admin)</li>
 * </ul>
 *
 * <p>Chunk-size limit = 1MB. Если клиент запрашивает больше - обрезаем.
 * Балансирует между:
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
        RangeSpec rangeSpec = parseRangeHeader(rangeHeader);
        PdfStreamingResult result = pdfService.openStream(bookId, fileIndex, rangeSpec);

        if (!result.isPartial()) {
            log.debug("pdf full stream: book={} fileIndex={} total={}",
                    bookId, fileIndex, result.totalSize());
            StreamingResponseBody body = output -> {
                try (PdfStreamingResult res = result) {
                    res.stream().transferTo(output);
                }
            };
            BodyBuilder okBuilder = ResponseEntity.ok()
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentType(MediaType.APPLICATION_PDF);
            // -1 = upstream не дал Content-Length (некоторые CDN). Не
            // выставляем заголовок вовсе - стримим без фиксированной длины,
            // а не эмитим Content-Length: -1.
            if (result.contentLength() >= 0) {
                okBuilder.contentLength(result.contentLength());
            }
            return okBuilder.body(body);
        }

        log.debug("pdf range stream: book={} fileIndex={} bytes={}-{}/{}",
                bookId, fileIndex, result.startInclusive(), result.endInclusive(), result.totalSize());
        StreamingResponseBody body = output -> {
            try (PdfStreamingResult res = result) {
                res.stream().transferTo(output);
            }
        };
        BodyBuilder partialBuilder = ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE,
                        "bytes " + result.startInclusive() + "-" + result.endInclusive()
                                + "/" + result.totalSize())
                .contentType(MediaType.APPLICATION_PDF);
        if (result.contentLength() >= 0) {
            partialBuilder.contentLength(result.contentLength());
        }
        return partialBuilder.body(body);
    }

    /**
     * Парсит HTTP Range header в {@link RangeSpec}. Поддерживает только
     * первый диапазон если их несколько (multi-range PDF.js не использует).
     * Применяет {@link #DEFAULT_CHUNK_SIZE} cap на end чтобы не отдать
     * клиенту 100MB одним response - PDF.js пере-запросит следующий
     * chunk после получения первого.
     *
     * <p>Suffix-ranges {@code bytes=-N} (RFC 7233 "последние N байт")
     * через {@link HttpRange.SuffixByteRange} - явно отвергаются 416.
     * Причина: parser не имеет на руках totalSize чтобы перевести в
     * абсолютный диапазон, а наивный {@code getRangeStart(Long.MAX_VALUE)}
     * дал бы {@code MAX-N}, после прибавления chunk size получили бы
     * long overflow → degenerate diapason → провайдер всё равно бросил
     * бы 416, но кодовый путь был бы случайно-correct и confusing.
     * Suffix-ranges PDF.js не использует, для curl/wget явный 416 с
     * объяснением лучше скрытого overflow. Если в будущем понадобится
     * поддержка - надо расширить {@link RangeSpec} suffix-режимом и
     * резолвить в provider'е где известен totalSize.
     *
     * @return {@code null} если header отсутствует (full request)
     * @throws RangeNotSatisfiableException для suffix-range request'ов
     */
    private static RangeSpec parseRangeHeader(String rangeHeader) {
        if (rangeHeader == null || rangeHeader.isBlank()) {
            return null;
        }
        List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
        if (ranges.isEmpty()) {
            return null;
        }
        HttpRange first = ranges.get(0);
        // HttpRange.SuffixByteRange (RFC 7233 "bytes=-N") - package-private,
        // нельзя instanceof. Detection через class simple name: либо
        // SuffixByteRange либо ByteRange. Альтернатива - проверка
        // getRangeStart на overflow-симптом (start > totalSize/2), но
        // string-compare надёжнее и явнее намерения
        if ("SuffixByteRange".equals(first.getClass().getSimpleName())) {
            throw RangeNotSatisfiableException.unsupportedSuffix();
        }
        // HttpRange.getRangeStart/getRangeEnd требуют length - используем
        // Long.MAX_VALUE как unbounded и потом cap'аем по chunk size в provider
        long start = first.getRangeStart(Long.MAX_VALUE);
        long requestedEnd = first.getRangeEnd(Long.MAX_VALUE);
        long cappedEnd = Math.min(start + DEFAULT_CHUNK_SIZE - 1, requestedEnd);
        return new RangeSpec(start, cappedEnd);
    }
}
