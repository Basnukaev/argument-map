package ru.basnukaev.argumentmap.web.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH /api/v1/nodes/{id}. Все поля опциональные - можно обновить
 * только содержимое (с записью revision), только координаты на канвасе
 * (без revision), originalLang, status либо комбинацию.
 *
 * <p>{@code originalLang} - 'ar' | 'ru' | 'en'. Пустая строка означает
 * очистить, null/отсутствие = не менять. Переводы управляются отдельными
 * endpoints (миграция 45 - multi-translation).
 *
 * <p>{@code status} - ручная установка статуса узла
 * (STANDING/DISPUTED/REFUTED/UNVERIFIED). null/отсутствие = не менять.
 * Валидация значения - в {@code NodeService.updateStatus}. Persistence
 * ручного статуса - см. там же (узел без влияющих рёбер сохраняет статус
 * при пересчёте MVP-алгоритмом).
 */
public record UpdateNodeRequest(
        @Size(min = 1, max = 10000) String content,
        Double posX,
        Double posY,
        @Pattern(regexp = "ar|ru|en|", message = "originalLang должен быть 'ar', 'ru', 'en' либо пустой строкой для очистки")
        String originalLang,
        String status
) {
}
