package ru.basnukaev.argumentmap.hadith.alminasa.service;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Single-thread executor маппинга alminasa staging→hd_* (план 5, решение 2).
 * Один импорт за раз: core=max=1, queue=0; второй submit отбивается
 * AbortPolicy — {@link AlminasaImportLauncher} маппит {@code TaskRejectedException}
 * в откат RUNNING→IDLE + 409. Сериализует ВСЕ виды импорта (narrators при
 * работающем hadiths → 409, осознанно).
 *
 * <p><b>БЕЗ</b> {@code @ConditionalOnProperty(alminasa.enabled)} (в отличие от
 * {@link AlminasaCrawlConfig}): импорт работает чисто по локальному staging,
 * alminasa-API не нужен. Если переиспользовать crawl-бин — при
 * {@code alminasa.enabled=false} импорт-страница сломалась бы.
 * {@code @EnableAsync} уже включён в {@code AiEditConfig}.
 */
@Configuration
public class AlminasaImportConfig {

    @Bean("alminasaImportExecutor")
    public ThreadPoolTaskExecutor alminasaImportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("alminasa-import-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
