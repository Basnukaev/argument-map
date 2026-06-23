package ru.basnukaev.argumentmap.hadith.curation.web.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Тело {@code PUT /admin/curation/overrides} (ADR-065 §6.1): upsert правки
 * поля и/или скрытия. {@code value} обязателен на сервисном уровне только
 * если {@code !hidden && !isNull} (см. {@code curation-empty-override}).
 * {@code reason} обязателен для {@code hidden=true} (модерация).
 *
 * @param entityTable snake_case таблица (whitelist {@code OverrideEntity})
 * @param entityId    PK правимой записи
 * @param fieldName   колонка или {@code __record__} (скрыть запись)
 * @param value       новое значение (nullable)
 * @param isNull      явная правка поля в NULL (default false)
 * @param hidden      скрыть поле/запись (default false)
 * @param reason      обоснование (обязателен для hidden)
 */
public record CurationOverridePutRequest(
        @NotBlank String entityTable,
        @NotNull UUID entityId,
        @NotBlank String fieldName,
        String value,
        Boolean isNull,
        Boolean hidden,
        String reason
) {
    public boolean isNullFlag() {
        return Boolean.TRUE.equals(isNull);
    }

    public boolean hiddenFlag() {
        return Boolean.TRUE.equals(hidden);
    }
}
