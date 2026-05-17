package ru.basnukaev.argumentmap.library.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotNull;

/**
 * Запрос на сохранение ProseMirror JSON для страницы (Этап 17.0,
 * ADR-039). Тело принимает {@link JsonNode} напрямую - фронт уже
 * сериализовал editor state через Tiptap {@code editor.getJSON()}.
 *
 * <p>Backend не валидирует ProseMirror schema (типы node'ов, content
 * model) - принимает любой синтаксически валидный JSON, валидация
 * на уровне Tiptap-extensions на фронте. Это идиоматично для
 * jsonb-колонок (см. ADR-039 «Resolution» секцию).
 */
public record UpdateFormattedContentRequest(
        @NotNull JsonNode formattedContent
) {
}
