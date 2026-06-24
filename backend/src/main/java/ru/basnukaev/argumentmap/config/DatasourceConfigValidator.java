package ru.basnukaev.argumentmap.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import ru.basnukaev.argumentmap.auth.web.security.SecurityHeadersCustomizer;

/**
 * Fail-fast валидатор datasource-кредов для prod (P0-3).
 *
 * <p>В default/prod датасорс читается из env {@code SPRING_DATASOURCE_URL /
 * _USERNAME / _PASSWORD} без небезопасного fallback (см. {@code
 * application.yml}). Это уже даёт fail-fast если env-переменная не выставлена
 * (Spring не может разрешить placeholder без default). Этот валидатор закрывает
 * второй сценарий: prod-profile активен, но URL всё-таки указывает на dev/local
 * БД (например деплой случайно поднялся под default profile=local, либо
 * SPRING_DATASOURCE_URL ошибочно выставлен в localhost). Тогда был бы тихий
 * коннект к dev-БД с риском потери/порчи данных - падаем на старте.
 *
 * <p>Зеркалит паттерн {@code ActuatorSecurityConfig} / {@code JwtService}:
 * проверка привязана к prod profile (явная safety boundary), в dev/test/local
 * ветка не активна.
 */
@Configuration
public class DatasourceConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(DatasourceConfigValidator.class);

    /**
     * Маркеры dev/local датасорса в URL. Если prod-profile активен, но URL
     * содержит любой из них - почти наверняка случайный коннект к dev-БД.
     */
    private static final String[] DEV_URL_MARKERS = {"localhost", "127.0.0.1", "/argumentmap"};

    public DatasourceConfigValidator(Environment environment,
                                     @Value("${spring.datasource.url:}") String url,
                                     @Value("${spring.datasource.username:}") String username) {
        // Та же детекция prod, что в ActuatorSecurityConfig - literal "prod"
        // в active profiles.
        if (!SecurityHeadersCustomizer.isProdProfile(environment)) {
            return;
        }
        String lowerUrl = url == null ? "" : url.toLowerCase();
        for (String marker : DEV_URL_MARKERS) {
            if (lowerUrl.contains(marker)) {
                throw new IllegalStateException(
                        "spring.datasource.url в prod profile указывает на dev/local БД ('" + marker
                                + "' в URL). Установить SPRING_DATASOURCE_URL / SPRING_DATASOURCE_USERNAME / "
                                + "SPRING_DATASOURCE_PASSWORD на прод-базу. Защита от деплоя на dev-БД (P0-3).");
            }
        }
        if ("argmap".equals(username)) {
            throw new IllegalStateException(
                    "spring.datasource.username в prod profile = 'argmap' (dev-пользователь). "
                            + "Установить SPRING_DATASOURCE_USERNAME на прод-креды (P0-3).");
        }
        log.info("DatasourceConfigValidator: prod datasource OK (URL не указывает на dev/local БД)");
    }
}
