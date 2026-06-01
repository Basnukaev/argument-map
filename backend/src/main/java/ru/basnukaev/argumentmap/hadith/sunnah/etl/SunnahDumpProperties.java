package ru.basnukaev.argumentmap.hadith.sunnah.etl;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация подключения к MySQL-дампу sunnah.com (ADR-052, Phase 5 шаг 2.d).
 * Блок {@code sunnah.dump:} в {@code application.yml} / env.
 *
 * <p>По умолчанию {@code enabled=false} — дамп не сконфигурирован, reader-bean
 * не создаётся (admin-endpoint отвечает 503). Включается заданием
 * {@code SUNNAH_DUMP_ENABLED=true} + {@code SUNNAH_DUMP_URL/USERNAME/PASSWORD}
 * (отдельный MySQL с загруженным {@code db/00-samplegitdb.sql}).
 *
 * @param enabled включает создание {@link SunnahDumpReader}-бина
 * @param url JDBC URL MySQL-дампа ({@code jdbc:mysql://host:port/db})
 * @param username пользователь MySQL
 * @param password пароль MySQL
 */
@ConfigurationProperties(prefix = "sunnah.dump")
public record SunnahDumpProperties(
        boolean enabled,
        String url,
        String username,
        String password
) {
}
