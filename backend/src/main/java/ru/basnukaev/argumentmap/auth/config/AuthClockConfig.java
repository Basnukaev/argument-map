package ru.basnukaev.argumentmap.auth.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring bean для {@link Clock} - инжектится в auth компоненты
 * (например {@link ru.basnukaev.argumentmap.auth.web.security.RateLimitFilter})
 * для тестирования time-based поведения без {@code Thread.sleep}.
 *
 * <p>Default {@code Clock.systemUTC()} в prod. Тесты могут override
 * через @{@code TestConfiguration} - на это {@code @ConditionalOnMissingBean}.
 */
@Configuration
public class AuthClockConfig {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
