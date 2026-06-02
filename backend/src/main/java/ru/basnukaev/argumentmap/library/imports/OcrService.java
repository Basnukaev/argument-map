package ru.basnukaev.argumentmap.library.imports;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import ru.basnukaev.argumentmap.exception.PageNotFoundException;
import ru.basnukaev.argumentmap.library.domain.OcrStatus;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.repository.PageRepository;
import ru.basnukaev.argumentmap.library.storage.ObjectStorageService;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 * OCR pipeline через Tess4j wrapper (Этап 17.b, ADR-041).
 *
 * <p>Async через {@code @Async("ocrTaskExecutor")} - REST endpoint
 * возвращает 202 Accepted сразу, тяжёлая работа идёт в bounded thread
 * pool (см. {@link OcrConfig}).
 *
 * <p>Цепочка:
 * <ol>
 *   <li>Load page + проверка наличия image pointer</li>
 *   <li>Перевод state: {@code PENDING|FAILED|DONE → PROCESSING},
 *       update {@code ocr_started_at=now}</li>
 *   <li>Download image из MinIO в temp file (Tess4j требует
 *       {@code java.io.File}, не InputStream)</li>
 *   <li>{@code Tesseract.setDatapath(tessdataPath)} +
 *       {@code setLanguage("ara+rus")} → {@code doOCR(file)} →
 *       String</li>
 *   <li>Update page.text_content + ocr_status=DONE + completed_at=now</li>
 *   <li>На exception: ocr_status=FAILED + log.error</li>
 *   <li>Temp file cleanup в finally</li>
 * </ol>
 *
 * <p>Tesseract native binding (libtesseract via JNA) загружается
 * лениво при первом {@code new Tesseract()}. Если system Tesseract не
 * установлен либо tessdata path неверный - первый вызов кинет
 * {@code TesseractException} или {@code UnsatisfiedLinkError}.
 * Перехватывается, page помечается FAILED. Backend стартует
 * нормально без native presence - OCR endpoint в этом случае всегда
 * возвращает FAILED после первой попытки.
 */
@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    /**
     * Список языков Tesseract в порядке приоритета. ara+rus покрывает
     * 95% контента платформы (арабские книги + русские переводы).
     * eng добавлен для technical comments / footnotes на английском
     * которые встречаются в современных academic editions.
     *
     * <p>Каждый язык требует соответствующий .traineddata файл
     * (ara.traineddata, rus.traineddata, eng.traineddata) в tessdata
     * каталоге. Без файла Tesseract бросает exception на init.
     */
    private static final String DEFAULT_LANGUAGES = "ara+rus+eng";

    private final PageRepository pageRepository;
    private final ObjectStorageService objectStorageService;
    private final String tessdataPath;

    public OcrService(PageRepository pageRepository,
                       ObjectStorageService objectStorageService,
                       @Qualifier("ocrTessdataPath") String tessdataPath) {
        this.pageRepository = pageRepository;
        this.objectStorageService = objectStorageService;
        this.tessdataPath = tessdataPath;
    }

    /**
     * Async версия - возвращает сразу, OCR гонится в ocrTaskExecutor.
     * REST endpoint возвращает 202 Accepted + текущий PROCESSING status.
     * Клиент опрашивает {@code GET /pages/{id}/ocr} для обновлений.
     *
     * <p>Spring @Async требует чтобы метод вызывался через прокси
     * (не из того же класса) - controller инжектит OcrService bean.
     */
    @Async("ocrTaskExecutor")
    public void recognizeAsync(UUID pageId) {
        try {
            recognize(pageId);
        } catch (Exception e) {
            // recognize сам уже помечает FAILED и логирует - тут только
            // catch-all чтобы async-thread не унёс stack trace в void
            log.error("OCR async задача упала для page {}: {}", pageId, e.getMessage());
        }
    }

    /**
     * Synchronous helper - выполняет OCR в caller thread. Используется
     * IT-тестами и internal callers которые управляют threading сами.
     *
     * <p>Все exception перехватываются → page помечается FAILED, метод
     * не пробрасывает дальше (caller-friendly для batch loops).
     *
     * @throws PageNotFoundException 404 если pageId не существует
     *                               (PRE-condition, до перевода в PROCESSING)
     */
    public void recognize(UUID pageId) {
        Page page = pageRepository.findById(pageId)
                .orElseThrow(() -> new PageNotFoundException(pageId));

        if (page.imageBucket() == null || page.imageStorageKey() == null) {
            log.warn("OCR пропущен для page {} - нет image pointer", pageId);
            pageRepository.updateOcrStatus(pageId, OcrStatus.FAILED,
                    Instant.now(), Instant.now());
            return;
        }

        // Атомарный claim PROCESSING: если другой concurrent вызов уже в
        // PROCESSING - не запускаем второй Tesseract recognize. Защита от
        // double-submit / re-trigger в полёте (check-then-act гонка, тот же
        // паттерн что у AiEditService).
        boolean claimed = pageRepository.tryClaimOcrProcessing(
                pageId, OcrStatus.PROCESSING, Instant.now());
        if (!claimed) {
            log.info("OCR пропущен для page {} - уже PROCESSING "
                    + "(concurrent trigger), второй recognize не запускаем", pageId);
            return;
        }

        Path tempFile = null;
        try {
            tempFile = downloadToTemp(page.imageBucket(), page.imageStorageKey());
            String recognized = doOcr(tempFile);
            String trimmed = recognized != null ? recognized.trim() : "";

            pageRepository.updateTextContentAndMarkDone(
                    pageId, trimmed, Instant.now());
            log.info("OCR success: page={} chars={}", pageId, trimmed.length());

        } catch (TesseractException | UnsatisfiedLinkError | IOException
                 | RuntimeException e) {
            log.error("OCR FAILED для page {}: {}", pageId, e.getMessage(), e);
            pageRepository.updateOcrStatus(pageId, OcrStatus.FAILED,
                    null, Instant.now());
        } finally {
            cleanupTemp(tempFile);
        }
    }

    /**
     * Скачать image из MinIO в temp file. Tess4j принимает только
     * {@code File} (или {@code BufferedImage}), не InputStream - JNA
     * native сторона требует path on disk.
     */
    private Path downloadToTemp(String bucket, String storageKey) throws IOException {
        Path temp = Files.createTempFile("ocr-page-", extractExt(storageKey));
        try (ResponseInputStream<GetObjectResponse> stream =
                     objectStorageService.get(bucket, storageKey)) {
            Files.copy(stream, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return temp;
    }

    /**
     * Запуск Tesseract на конкретный image file. {@code Tesseract} -
     * not thread-safe (instance-per-call), создаём для каждой страницы.
     * Это дороже чем reusable instance но безопаснее под concurrent OCR.
     *
     * <p>language={@link #DEFAULT_LANGUAGES} - hybrid ara+rus+eng. Tesseract
     * сам решает какой язык на пер-блок основе через LSTM identifier
     * (медленнее чем single language, но точнее на mixed content).
     */
    private String doOcr(Path imageFile) throws TesseractException {
        ITesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessdataPath);
        tesseract.setLanguage(DEFAULT_LANGUAGES);
        return tesseract.doOCR(imageFile.toFile());
    }

    private static String extractExt(String storageKey) {
        int lastDot = storageKey.lastIndexOf('.');
        return lastDot > 0 ? storageKey.substring(lastDot) : ".bin";
    }

    private static void cleanupTemp(Path tempFile) {
        if (tempFile != null) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
                // best-effort cleanup
            }
        }
    }
}
