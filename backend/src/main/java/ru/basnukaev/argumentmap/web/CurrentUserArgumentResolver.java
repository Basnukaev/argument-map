package ru.basnukaev.argumentmap.web;

import java.util.UUID;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import ru.basnukaev.argumentmap.exception.MissingUserHeaderException;

/**
 * Достаёт UUID пользователя из X-User-Id (ADR-006) и инжектит в параметры,
 * помеченные {@link CurrentUser}. Существование пользователя в БД не
 * проверяем здесь — FK-нарушение поймает запись в репозитории и переведётся
 * в 422 через GlobalExceptionHandler.
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

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
        String raw = webRequest.getHeader(HEADER);
        if (raw == null || raw.isBlank()) {
            throw new MissingUserHeaderException("Заголовок X-User-Id обязателен");
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new MissingUserHeaderException(
                    "Заголовок X-User-Id должен быть UUID, получено: " + raw
            );
        }
    }
}
