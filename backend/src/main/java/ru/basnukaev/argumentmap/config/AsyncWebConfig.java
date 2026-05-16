package ru.basnukaev.argumentmap.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Конфиг async/streaming Spring MVC (ADR-024, Этап 25.b operational
 * hardening).
 *
 * <p>По умолчанию Spring использует {@code SimpleAsyncTaskExecutor} для
 * {@code StreamingResponseBody} - **создаёт новый Thread на каждый
 * запрос**, никаких bounds. Под нагрузкой (10+ одновременных PDF
 * downloads крупных книг) - JVM thread exhaustion: {@code OutOfMemoryError:
 * unable to create new native thread} либо deadlock OS.
 *
 * <p>Решение - bounded {@link ThreadPoolTaskExecutor} с фиксированным
 * pool + bounded queue + предсказуемой rejection policy:
 * <ul>
 *   <li>core 10 / max 50 - умеренный pool, scaling под бурсты</li>
 *   <li>queue 100 - буферизуем кратковременные spikes</li>
 *   <li>{@code CallerRunsPolicy} - back-pressure: при переполнении
 *       request обрабатывается в HTTP-thread'е tomcat (медленнее ответ
 *       пользователю, но НЕ 500/timeout, лучше для UX)</li>
 *   <li>keepAlive 60s - idle threads умирают, освобождают память</li>
 * </ul>
 *
 * <p>Метрики через {@code /actuator/metrics/executor.*} автоматически
 * (Micrometer auto-instruments {@link ThreadPoolTaskExecutor} beans).
 *
 * <p>Async timeout 5 минут - крупный PDF ~100MB через slow connection
 * не должен прерваться раньше. Frontend react-pdf делает Range requests
 * по 1MB, каждый укладывается в 5 минут с большим запасом.
 */
@Configuration
public class AsyncWebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncWebConfig.class);

    private static final int CORE_POOL_SIZE = 10;
    private static final int MAX_POOL_SIZE = 50;
    private static final int QUEUE_CAPACITY = 100;
    private static final int KEEP_ALIVE_SECONDS = 60;
    private static final long ASYNC_TIMEOUT_MS = 5L * 60 * 1000;

    @Bean(name = "mvcAsyncTaskExecutor")
    public ThreadPoolTaskExecutor mvcAsyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
        executor.setThreadNamePrefix("mvc-async-");
        // CallerRunsPolicy - back-pressure: при переполнении queue
        // запрос выполнится в caller thread (HTTP-thread tomcat). Это
        // временно замедлит acceptance новых запросов tomcat - estable
        // throttling вместо abrupt 500.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setAwaitTerminationSeconds(30);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        log.info("MVC async executor configured: core={}, max={}, queue={}, keepAlive={}s",
                CORE_POOL_SIZE, MAX_POOL_SIZE, QUEUE_CAPACITY, KEEP_ALIVE_SECONDS);
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        AsyncTaskExecutor executor = mvcAsyncTaskExecutor();
        configurer.setTaskExecutor(executor);
        configurer.setDefaultTimeout(ASYNC_TIMEOUT_MS);
    }
}
