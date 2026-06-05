package ru.basnukaev.argumentmap.hadith.alminasa.service;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Single-thread executor backfill-краула зависимых данных alminasa (علл/غريب,
 * План 8, решение 1). Один backfill за раз: core=max=1, queue=0; второй submit
 * отбивается AbortPolicy → {@link AlminasaDependentsBackfillService} маппит
 * {@code TaskRejectedException} в откат RUNNING→IDLE + 409.
 *
 * <p><b>ОТДЕЛЬНЫЙ</b> бин от {@code alminasaCrawlExecutor}: реюз сериализовал бы
 * backfill за живым краулом — а они МОГУТ идти параллельно (разные index_name
 * чекпоинта). {@code @ConditionalOnProperty(alminasa.enabled)}: backfill зовёт
 * ES-клиент, без alminasa-API смысла нет (как у crawl-executor).
 * {@code @EnableAsync} уже включён в {@code AiEditConfig}.
 */
@Configuration
@ConditionalOnProperty(name = "alminasa.enabled", havingValue = "true", matchIfMissing = true)
public class AlminasaBackfillConfig {

    @Bean("alminasaBackfillExecutor")
    public ThreadPoolTaskExecutor alminasaBackfillExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("alminasa-backfill-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
