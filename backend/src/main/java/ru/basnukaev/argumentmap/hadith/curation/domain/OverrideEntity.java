package ru.basnukaev.argumentmap.hadith.curation.domain;

import java.util.Optional;

/**
 * Whitelist таблиц hadith-домена, правки которых хранятся в
 * {@code hd_field_overrides} (ADR-065). Mirror CHECK-constraint
 * {@code hd_field_overrides_table_check} — порядок и состав совпадают.
 *
 * <p>{@link #tableName()} — snake_case имя таблицы, как лежит в
 * {@code entity_table} и приходит во входе REST. Реальный Java enum (а не
 * constant-class как {@code HadithStatus}): {@code entity_table} — закрытый
 * набор, зеркалируемый CHECK'ом, и enum даёт компилятору exhaustiveness для
 * apply-диспетчеризации (Фаза 2).
 */
public enum OverrideEntity {
    HD_HADITHS("hd_hadiths"),
    HD_NARRATORS("hd_narrators"),
    HD_SANADS("hd_sanads"),
    HD_SANAD_NARRATORS("hd_sanad_narrators"),
    HD_RULINGS("hd_rulings"),
    HD_EXPLANATIONS("hd_explanations"),
    HD_NARRATOR_COMMENTARIES("hd_narrator_commentaries"),
    HD_MATNS("hd_matns");

    private final String tableName;

    OverrideEntity(String tableName) {
        this.tableName = tableName;
    }

    public String tableName() {
        return tableName;
    }

    /**
     * Резолв из значения {@code entity_table} (REST-вход или БД-строка).
     * {@code empty} — таблица не в whitelist (PATCH с ней → 400).
     */
    public static Optional<OverrideEntity> fromTableName(String tableName) {
        for (OverrideEntity e : values()) {
            if (e.tableName.equals(tableName)) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }
}
