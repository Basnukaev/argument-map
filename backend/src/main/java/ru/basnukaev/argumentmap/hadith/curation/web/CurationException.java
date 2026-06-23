package ru.basnukaev.argumentmap.hadith.curation.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;

/**
 * Доменные ошибки курации (ADR-065 §6.3) — единый параметризованный тип
 * вместо 6 классов: все мапятся в RFC 7807 ProblemDetail одинаково
 * (status + type-slug + title), различаясь лишь данными. Маппинг —
 * {@code GlobalExceptionHandler.handleCuration}.
 */
public class CurationException extends RuntimeException {

    private final transient HttpStatus status;
    private final String typeSlug;
    private final String title;

    private CurationException(HttpStatus status, String typeSlug, String title, String detail) {
        super(detail);
        this.status = status;
        this.typeSlug = typeSlug;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String typeSlug() {
        return typeSlug;
    }

    public String title() {
        return title;
    }

    public static CurationException invalidEntityTable(String table) {
        return new CurationException(HttpStatus.BAD_REQUEST, "curation-invalid-entity-table",
                "Недопустимая таблица курации", "Таблица не в whitelist курации: " + table);
    }

    public static CurationException fieldNotEditable(String field) {
        return new CurationException(HttpStatus.BAD_REQUEST, "curation-field-not-editable",
                "Поле не редактируемо/не скрываемо",
                "Поле вне whitelist курации (или первоисточник): " + field);
    }

    public static CurationException invalidEnumValue(String field, String value) {
        return new CurationException(HttpStatus.BAD_REQUEST, "curation-invalid-enum-value",
                "Недопустимое значение поля",
                "Значение '" + value + "' вне whitelist enum поля " + field);
    }

    public static CurationException reasonRequired() {
        return new CurationException(HttpStatus.BAD_REQUEST, "curation-reason-required",
                "Требуется причина", "Для скрытия (hidden=true) обязателен reason (модерация)");
    }

    public static CurationException emptyOverride() {
        return new CurationException(HttpStatus.BAD_REQUEST, "curation-empty-override",
                "Пустая правка", "Нужно хоть что-то: value, isNull или hidden");
    }

    public static CurationException entityNotFound(String table, UUID id) {
        return new CurationException(HttpStatus.NOT_FOUND, "curation-entity-not-found",
                "Запись не найдена", "Нет записи " + id + " в таблице " + table);
    }

    public static CurationException overrideNotFound() {
        return new CurationException(HttpStatus.NOT_FOUND, "curation-override-not-found",
                "Правка не найдена", "Override для (таблица, запись, поле) не существует");
    }
}
