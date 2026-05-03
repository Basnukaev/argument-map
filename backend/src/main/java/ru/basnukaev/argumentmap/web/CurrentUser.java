package ru.basnukaev.argumentmap.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Маркирует параметр контроллера, в который Spring инжектит UUID
 * пользователя, прочитанный из заголовка X-User-Id (см. ADR-006).
 * Резолвер — {@link CurrentUserArgumentResolver}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
