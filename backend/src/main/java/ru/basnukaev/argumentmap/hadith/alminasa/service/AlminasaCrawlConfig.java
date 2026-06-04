package ru.basnukaev.argumentmap.hadith.alminasa.service;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Single-thread executor краулера alminasa (паттерн AiEditConfig).
 * Один краулер за раз: core=max=1, queue=0; второй submit отбивается
 * AbortPolicy (но до него не доходит — claimStart() уже отдал 409).
 * @EnableAsync уже включён в AiEditConfig.
 */
@Configuration
@ConditionalOnProperty(name = "alminasa.enabled", havingValue = "true", matchIfMissing = true)
public class AlminasaCrawlConfig {

    @Bean("alminasaCrawlExecutor")
    public ThreadPoolTaskExecutor alminasaCrawlExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("alminasa-crawl-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
