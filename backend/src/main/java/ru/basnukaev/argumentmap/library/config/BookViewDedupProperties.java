package ru.basnukaev.argumentmap.library.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Конфиг дедупликации инкремента просмотров книги (анти-инфляция).
 * Соответствует блоку {@code book.view-count:} в {@code application.yml}.
 *
 * <p>Если один и тот же (clientIp + bookId) уже был засчитан в пределах
 * {@code dedupWindow} — повторный POST /views является тихим no-op
 * (view_count не инкрементируется, ответ 204 как обычно).
 *
 * @param dedupWindow размер sliding window. Default {@code PT30M} — 30 минут;
 *                    повторный просмотр той же книги с того же IP засчитывается
 *                    не чаще раза в полчаса. Должен быть положительным
 */
@ConfigurationProperties(prefix = "book.view-count")
public record BookViewDedupProperties(
        @DefaultValue("PT30M") Duration dedupWindow
) {

    public BookViewDedupProperties {
        if (dedupWindow == null || dedupWindow.isNegative() || dedupWindow.isZero()) {
            throw new IllegalArgumentException(
                    "book.view-count.dedup-window=" + dedupWindow
                            + " должен быть положительным (например PT30M)"
            );
        }
    }
}
