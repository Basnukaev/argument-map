package ru.basnukaev.argumentmap.library.imports;

import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Конфигурация AI editing pass (ADR-042, Этап 17.e).
 *
 * <p>{@code aiEditTaskExecutor} - bounded thread pool для async вызовов
 * Anthropic API. LLM запрос - это I/O-bound work (~5-15с латентность,
 * 95% времени ждём response), поэтому pool может быть чуть шире OCR
 * (core 2, max 4) но всё равно небольшой - API rate limits и cost
 * controls. На больше workers нет смысла - rate limit Anthropic API
 * для большинства tier'ов ~50 req/min, при N=4 параллельно мы упрёмся
 * быстро.
 *
 * <p>Queue 50 - меньше OCR (100) потому что AI edit задачи каждая
 * дороже (cost + latency), не хотим накопить большой backlog. При
 * overflow - {@code CallerRunsPolicy} - HTTP-thread выполнит сам,
 * сделает backpressure ощутимым для admin'а который наполняет очередь.
 *
 * <p>{@code @EnableAsync} включается через {@link OcrConfig} - один
 * раз достаточно для всего приложения.
 */
@Configuration
public class AiEditConfig {

    private static final Logger log = LoggerFactory.getLogger(AiEditConfig.class);

    private static final int CORE_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 4;
    private static final int QUEUE_CAPACITY = 50;
    private static final int KEEP_ALIVE_SECONDS = 120;

    @Bean(name = "aiEditTaskExecutor")
    public ThreadPoolTaskExecutor aiEditTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
        executor.setThreadNamePrefix("ai-edit-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setAwaitTerminationSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        log.info("AI edit task executor configured: core={}, max={}, queue={}, keepAlive={}s",
                CORE_POOL_SIZE, MAX_POOL_SIZE, QUEUE_CAPACITY, KEEP_ALIVE_SECONDS);
        return executor;
    }
}
