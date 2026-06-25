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

    /**
     * Префикс синтетического {@code field_name} формулы передачи звена иснада
     * (Фаза 5.b, ADR-065 amendment). Хранится на
     * {@code entity_table='hd_sanad_narrators'} с {@code entity_id=hadith_id}
     * (СТАБИЛЬНЫЙ ключ) и {@code field_name='transmission_phrase@'+position}.
     *
     * <p>Композитный PK {@code hd_sanad_narrators (sanad_id, position)} НЕ имеет
     * суррогатного UUID, а {@code sanad_id} пересоздаётся на реимпорте
     * ({@code UUID.randomUUID()} после {@code deleteByHadithId}) → прямой ключ
     * по {@code sanad_id} стёрся бы. {@code hadith_id} стабилен (upsert по
     * {@code (source, external_id)}), {@code position} детерминирован
     * (реверс-индекс цепи, 0 = сподвижник). alminasa = ровно 1 sanad на хадис,
     * потому {@code @{position}} однозначно адресует звено (YAGNI vs
     * {@code @{position}:{narratorExternalId}}). Накладывается на ЧТЕНИИ
     * ({@code OverrideApplyService.applyTransmissionPhrase}). Не реальная колонка.
     */
    public static final String TRANSMISSION_PHRASE_PREFIX = "transmission_phrase@";

    /** Синтетический {@code field_name} формулы передачи звена на позиции {@code position}. */
    public static String transmissionPhraseField(int position) {
        return TRANSMISSION_PHRASE_PREFIX + position;
    }

    /** {@code true} если {@code field} — синтетический ключ формулы передачи звена. */
    public static boolean isTransmissionPhraseField(String field) {
        return field != null && field.startsWith(TRANSMISSION_PHRASE_PREFIX);
    }

    /**
     * Позиция звена из синтетического {@code field_name} формулы передачи, или
     * {@code null} если не {@code transmission_phrase@N} с целочисленным суффиксом.
     * Защитный парсинг — мис-keyed правка не роняет read (как {@code applyInt}).
     */
    public static Integer transmissionPhrasePosition(String field) {
        if (!isTransmissionPhraseField(field)) {
            return null;
        }
        try {
            return Integer.valueOf(field.substring(TRANSMISSION_PHRASE_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** {@code true} если правка переопределяет значение поля (или ставит NULL). */
    public boolean hasValueOverride() {
        return overrideValue != null || isNullOverride;
    }

    /** {@code true} если это скрытие всей записи: {@code __record__} + hidden. */
    public boolean isRecordHide() {
        return hidden && RECORD_FIELD.equals(fieldName);
    }
}
