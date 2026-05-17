package ru.basnukaev.argumentmap.web;

import java.util.UUID;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import ru.basnukaev.argumentmap.auth.domain.AuthenticatedUser;
import ru.basnukaev.argumentmap.exception.MissingUserHeaderException;

/**
 * Извлекает UUID текущего пользователя из {@code SecurityContext} и
 * инжектит в параметры, помеченные {@link CurrentUser}. ADR-040 -
 * principal приходит либо из JWT (через JwtAuthenticationFilter), либо
 * из X-User-Id в dev/test (через XUserIdAuthenticationFilter).
 *
 * <p>API аннотации {@link CurrentUser} не меняется - старые controllers
 * продолжают работать. Только источник UUID переехал с заголовка на
 * principal. Исключение {@link MissingUserHeaderException} оставляем
 * для совместимости с GlobalExceptionHandler (тот же тип ошибки 400).
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    /**
     * Имя legacy header'а - сохраняется только для совместимости
     * OpenApiConfig (UI документации). Реальный источник UUID -
     * SecurityContext.
     */
    public static final String HEADER = "X-User-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && parameter.getParameterType().equals(UUID.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new MissingUserHeaderException(
                    "Запрос не аутентифицирован - принципал отсутствует в SecurityContext"
            );
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof AuthenticatedUser user) {
            return user.id();
        }
        throw new MissingUserHeaderException(
                "SecurityContext содержит неизвестный тип principal: " + principal.getClass()
        );
    }
}
