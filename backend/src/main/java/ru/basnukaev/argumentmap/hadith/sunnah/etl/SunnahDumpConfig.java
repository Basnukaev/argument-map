package ru.basnukaev.argumentmap.hadith.sunnah.etl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Конфигурация чтения дампа sunnah.com (ADR-052). Создаёт
 * {@link SunnahDumpReader} только при {@code sunnah.dump.enabled=true} —
 * отдельный MySQL-{@code DataSource}, изолированный от основного Postgres
 * (НЕ {@code @Primary}, используется только reader'ом через свой JdbcTemplate).
 *
 * <p>{@link DriverManagerDataSource} (без пула) — импорт редкий, admin-
 * триггерный; connection-per-операция приемлем. Для высокочастотного доступа
 * заменить на пул.
 */
@Configuration
@EnableConfigurationProperties(SunnahDumpProperties.class)
public class SunnahDumpConfig {

    private static final Logger log = LoggerFactory.getLogger(SunnahDumpConfig.class);

    @Bean
    @ConditionalOnProperty(prefix = "sunnah.dump", name = "enabled", havingValue = "true")
    public SunnahDumpReader sunnahDumpReader(SunnahDumpProperties props, ObjectMapper objectMapper) {
        if (props.url() == null || props.url().isBlank()) {
            throw new IllegalStateException(
                    "sunnah.dump.enabled=true, но sunnah.dump.url не задан");
        }
        DriverManagerDataSource ds = new DriverManagerDataSource(
                props.url(), props.username(), props.password());
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        log.info("sunnah dump reader активен: {}", props.url());
        return new SunnahDumpReader(ds, objectMapper);
    }
}
