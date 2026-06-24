package ru.basnukaev.argumentmap.hadith.curation.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Строка {@code hd_field_overrides} (ADR-065): одна правка и/или скрытие
 * одного поля одной записи hadith-домена. Накладывается на ЧТЕНИИ
 * ({@code OverrideApplyService}); импорт alminasa её не трогает — потому
 * правка переживает delete-recreate реимпорта.
 *
 * <p>Тонкое зеркало строки БД (как {@code Matn}/{@code Hadith}):
 * {@code entityTable} хранится текстом (whitelist — {@link OverrideEntity});
 * {@code fieldName} — snake_case колонка ИЛИ синтетический
 * {@link #RECORD_FIELD} (скрытие всей записи, §4.2). {@code overrideValue}
 * хранится текстом и кастуется в тип поля на apply (§3.4).
 * {@code isNullOverride} отличает «правку в NULL» от «нет значения, только
 * hidden». {@code reason} обязателен для {@code hidden=true} на сервисном
 * уровне (модерация — кто/почему скрыл).
 */
public record FieldOverride(
        UUID id,
        String entityTable,
        UUID entityId,
        String fieldName,
        String overrideValue,
        boolean isNullOverride,
        boolean hidden,
        UUID editedBy,
        Instant editedAt,
        String reason
) {
    /** Синтетический {@code field_name} для скрытия всей записи целиком (§4.2). */
    public static final String RECORD_FIELD = "__record__";

    /**
     * Синтетические {@code field_name} перевода primary-матна (Фаза 6, §10
     * вопрос 2). Хранятся на {@code entity_table='hd_matns'} с
     * {@code entity_id=hadith_id} (СТАБИЛЬНЫЙ ключ — переживает delete-recreate
     * реимпорта, в отличие от {@code matn.id}). Накладываются на ЧТЕНИИ на
     * primary-матн хадиса в {@code OverrideApplyService}. Не реальные колонки.
     */
    public static final String PRIMARY_TEXT_RU = "primary_text_ru";
    public static final String PRIMARY_TEXT_EN = "primary_text_en";

    /** {@code true} если правка переопределяет значение поля (или ставит NULL). */
    public boolean hasValueOverride() {
        return overrideValue != null || isNullOverride;
    }

    /** {@code true} если это скрытие всей записи: {@code __record__} + hidden. */
    public boolean isRecordHide() {
        return hidden && RECORD_FIELD.equals(fieldName);
    }
}
