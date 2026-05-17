package ru.basnukaev.argumentmap.library.imports;

import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Конфигурация OCR pipeline (Этап 17.b, ADR-041).
 *
 * <p>{@code ocrTaskExecutor} - bounded thread pool для async Tesseract
 * вызовов. OCR это CPU-heavy work (~5-10 секунд на страницу A4 ara+rus),
 * поэтому pool намеренно небольшой - core 2 / max 4. На больше workers
 * нет смысла - Tesseract сам уже multi-threaded на single page, а CPU
 * starvation при N parallel Tesseract вреднее sequential queue.
 *
 * <p>Queue 100 - буферизуем большой batch (например admin грузит 500
 * страниц рукописи и тригерит OCR пачкой). При overflow -
 * {@code CallerRunsPolicy} - backpressure: вызывающий HTTP-thread
 * сделает OCR сам, замедлит acceptance дальнейших триггеров.
 *
 * <p>{@code @EnableAsync} включает {@code @Async} processing - Spring
 * проксирует методы аннотированные {@code @Async("ocrTaskExecutor")}
 * и отправляет в указанный executor вместо синхронного вызова.
 */
@Configuration
@EnableAsync
public class OcrConfig {

    private static final Logger log = LoggerFactory.getLogger(OcrConfig.class);

    /**
     * Tesseract recognizer - heavy CPU. Намеренно небольшой pool -
     * больше не значит лучше: Tesseract уже multi-threaded на один
     * рисовый запрос, N parallel вызовов конкурируют за CPU.
     */
    private static final int CORE_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 4;
    private static final int QUEUE_CAPACITY = 100;
    private static final int KEEP_ALIVE_SECONDS = 120;

    @Bean(name = "ocrTaskExecutor")
    public ThreadPoolTaskExecutor ocrTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
        executor.setThreadNamePrefix("ocr-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setAwaitTerminationSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        log.info("OCR task executor configured: core={}, max={}, queue={}, keepAlive={}s",
                CORE_POOL_SIZE, MAX_POOL_SIZE, QUEUE_CAPACITY, KEEP_ALIVE_SECONDS);
        return executor;
    }

    /**
     * Path к Tesseract training data (.traineddata файлы). System
     * Tesseract на Debian/WSL2 ставит файлы в
     * {@code /usr/share/tesseract-ocr/4.00/tessdata} (либо .../5/tessdata
     * для свежих версий). На macOS Homebrew - в
     * {@code /opt/homebrew/share/tessdata}. Дефолт оптимизирован под
     * Debian/WSL2 - типичная dev среда проекта.
     *
     * <p>Override через env {@code OCR_TESSDATA_PATH} либо
     * {@code application.yml} {@code ocr.tessdata.path}. См. ADR-041
     * + backend/CLAUDE.md OCR section.
     */
    @Bean(name = "ocrTessdataPath")
    public String ocrTessdataPath(
            @Value("${ocr.tessdata.path:/usr/share/tesseract-ocr/4.00/tessdata}")
            String path) {
        log.info("OCR tessdata path: {}", path);
        return path;
    }
}
