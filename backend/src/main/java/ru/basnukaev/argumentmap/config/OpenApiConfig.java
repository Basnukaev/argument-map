package ru.basnukaev.argumentmap.config;

import java.util.Arrays;
import java.util.List;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import ru.basnukaev.argumentmap.web.CurrentUser;
import ru.basnukaev.argumentmap.web.CurrentUserArgumentResolver;

/**
 * Кастомизация OpenAPI-спецификации для springdoc-openapi.
 *
 * <p>Springdoc не знает про наш {@link CurrentUser} HandlerMethodArgumentResolver
 * и автогенерирует параметр {@code userId} как {@code query} для каждого
 * мутирующего эндпоинта. Это вводит в заблуждение фронтенд (после
 * regen-api в OpenAPI-схеме появляется query.userId, хотя реально бэк
 * читает заголовок X-User-Id).
 *
 * <p>Решение: {@link OperationCustomizer} удаляет автоматический query.userId
 * параметр и добавляет на его место header X-User-Id с правильной
 * UUID-схемой. Применяется ко всем операциям где есть параметр с аннотацией
 * {@link CurrentUser}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OperationCustomizer currentUserHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            boolean hasCurrentUser = Arrays.stream(handlerMethod.getMethodParameters())
                    .anyMatch(p -> p.hasParameterAnnotation(CurrentUser.class));
            if (!hasCurrentUser) {
                return operation;
            }

            // удаляем автоматический query.userId, который springdoc вывел из имени параметра
            List<Parameter> params = operation.getParameters();
            if (params != null) {
                params.removeIf(p -> "userId".equals(p.getName()) && "query".equals(p.getIn()));
            }

            // добавляем header X-User-Id - реальный источник, который читает резолвер.
            // required=true потому что без заголовка резолвер бросает MissingUserHeaderException → 400
            Parameter userHeader = new Parameter()
                    .name(CurrentUserArgumentResolver.HEADER)
                    .in("header")
                    .required(true)
                    .description("UUID текущего пользователя (ADR-006, временно до Spring Security в Этапе 6)")
                    .schema(new StringSchema().format("uuid"));
            operation.addParametersItem(userHeader);
            return operation;
        };
    }
}
