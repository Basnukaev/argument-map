package ru.basnukaev.argumentmap.library.imports.web;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.exception.PageNotFoundException;
import ru.basnukaev.argumentmap.library.domain.OcrStatus;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.imports.OcrService;
import ru.basnukaev.argumentmap.library.repository.PageRepository;
import ru.basnukaev.argumentmap.library.web.dto.OcrJobResponse;
import ru.basnukaev.argumentmap.web.CurrentUser;

/**
 * REST endpoints для OCR pipeline (Этап 17.b, ADR-041).
 *
 * <ul>
 *   <li>{@code POST /api/v1/library/pages/{pageId}/ocr} - триггерит async
 *       OCR через {@link OcrService#recognizeAsync}. Returns 202 Accepted
 *       + текущий статус ({@code PROCESSING} если успели обновить state
 *       до возврата response, либо {@code PENDING} если поставлено в queue)</li>
 *   <li>{@code GET /api/v1/library/pages/{pageId}/ocr} - текущий статус
 *       OCR задачи. Polling endpoint - фронт опрашивает каждые 2-3 сек
 *       пока {@code status=PROCESSING}, переходит в {@code DONE}/
 *       {@code FAILED} → стопает polling</li>
 * </ul>
 *
 * <p>403 не возвращается - любой authenticated user может триггерить OCR
 * по любой странице которая у него видна (visibility - этап 22). На MVP
 * - открытая платформа.
 *
 * <p>409 не возвращается даже если статус PROCESSING - re-trigger
 * допустим (например после рестарта backend'а page может застрять в
 * PROCESSING navсегда; форсированный re-trigger выводит из этого).
 * Дубликатные tasks не страшны: {@code OcrService.recognize} idempotent
 * на uровне state machine.
 */
@RestController
@RequestMapping("/api/v1/library/pages")
public class OcrController {

    private static final Logger log = LoggerFactory.getLogger(OcrController.class);

    private final OcrService ocrService;
    private final PageRepository pageRepository;

    public OcrController(OcrService ocrService,
                          PageRepository pageRepository) {
        this.ocrService = ocrService;
        this.pageRepository = pageRepository;
    }

    @PostMapping("/{pageId}/ocr")
    public ResponseEntity<OcrJobResponse> triggerOcr(
            @PathVariable UUID pageId,
            @CurrentUser UUID currentUserId) {

        Page page = pageRepository.findById(pageId)
                .orElseThrow(() -> new PageNotFoundException(pageId));

        log.info("OCR trigger: page={} by user={} currentStatus={}",
                pageId, currentUserId, page.ocrStatus());

        ocrService.recognizeAsync(pageId);

        // Возвращаем status каким он был на момент запроса - реальный
        // PROCESSING может быть выставлен async через миллисекунду.
        // Polling endpoint покажет актуальное значение.
        String status = page.ocrStatus() != null
                ? page.ocrStatus() : OcrStatus.PENDING;
        OcrJobResponse body = new OcrJobResponse(
                pageId, status,
                page.ocrStartedAt(), page.ocrCompletedAt(),
                page.imageStorageKey() != null
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @GetMapping("/{pageId}/ocr")
    public OcrJobResponse getOcrStatus(@PathVariable UUID pageId) {
        Page page = pageRepository.findById(pageId)
                .orElseThrow(() -> new PageNotFoundException(pageId));
        return new OcrJobResponse(
                pageId,
                page.ocrStatus(),
                page.ocrStartedAt(),
                page.ocrCompletedAt(),
                page.imageStorageKey() != null
        );
    }
}
