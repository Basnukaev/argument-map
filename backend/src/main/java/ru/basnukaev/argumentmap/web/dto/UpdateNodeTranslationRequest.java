package ru.basnukaev.argumentmap.web.dto;

import jakarta.validation.constraints.Size;

/**
 * PATCH /api/v1/nodes/translations/{id}. Все поля опциональные.
 *
 * <p>{@code translatorName} - null/blank = очистить (станет анонимным).
 * Пустая строка эквивалентна null. Меняется через update.
 *
 * <p>{@code body} - null = не менять. Пустая/blank string игнорируется
 * (сервис не позволяет очистить body перевода - удаляйте всю запись через
 * DELETE если перевод больше не нужен).
 *
 * <p>{@code isDefault} - см. отдельный endpoint
 * {@code POST /api/v1/nodes/translations/{id}/default} для atomic
 * default-switch (его нужно делать через специальный action, не через
 * PATCH с одним boolean - меняет state других переводов узла).
 */
public record UpdateNodeTranslationRequest(
        @Size(max = 200) String translatorName,
        @Size(max = 10000) String body
) {
}
