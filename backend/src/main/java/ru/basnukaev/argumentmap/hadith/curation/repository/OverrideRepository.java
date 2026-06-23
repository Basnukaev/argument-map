package ru.basnukaev.argumentmap.hadith.curation.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.curation.domain.FieldOverride;
import ru.basnukaev.argumentmap.hadith.curation.domain.OverrideEntity;

/**
 * Доступ к {@code hd_field_overrides} (ADR-065). Тонкий JDBC-репозиторий:
 * upsert по UNIQUE-ключу {@code (entity_table, entity_id, field_name)},
 * батч-{@link #findByEntity} (один {@code IN}-запрос на тип сущности —
 * apply-слой Фазы 2 читает все overrides набора записей без N+1), и
 * точечные find/delete для generic write-API (Фаза 3).
 */
@Repository
public class OverrideRepository {

    private static final String COLUMNS =
            "id, entity_table, entity_id, field_name, override_value, "
                    + "is_null_override, hidden, edited_by, edited_at, reason";

    private static final RowMapper<FieldOverride> ROW_MAPPER = (rs, rn) -> new FieldOverride(
            rs.getObject("id", UUID.class),
            rs.getString("entity_table"),
            rs.getObject("entity_id", UUID.class),
            rs.getString("field_name"),
            rs.getString("override_value"),
            rs.getBoolean("is_null_override"),
            rs.getBoolean("hidden"),
            rs.getObject("edited_by", UUID.class),
            instant(rs, "edited_at"),
            rs.getString("reason")
    );

    private final JdbcTemplate jdbcTemplate;

    public OverrideRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Upsert override по UNIQUE-ключу: повторный PATCH того же поля обновляет
     * ту же строку (idempotent, §6.1). Возвращает персистентную строку
     * (читает обратно — на конфликте {@code id} остаётся прежним, не из
     * {@code o.id()}; аутентичный id нужен для audit_log).
     */
    public FieldOverride upsert(FieldOverride o) {
        jdbcTemplate.update(
                "INSERT INTO hd_field_overrides (" + COLUMNS + ") "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (entity_table, entity_id, field_name) DO UPDATE SET "
                        + "override_value = EXCLUDED.override_value, "
                        + "is_null_override = EXCLUDED.is_null_override, "
                        + "hidden = EXCLUDED.hidden, "
                        + "edited_by = EXCLUDED.edited_by, "
                        + "edited_at = EXCLUDED.edited_at, "
                        + "reason = EXCLUDED.reason",
                o.id(), o.entityTable(), o.entityId(), o.fieldName(), o.overrideValue(),
                o.isNullOverride(), o.hidden(), o.editedBy(), odt(o.editedAt()), o.reason());
        return findOne(OverrideEntity.fromTableName(o.entityTable()).orElseThrow(),
                o.entityId(), o.fieldName()).orElseThrow();
    }

    /**
     * Все overrides набора записей одной таблицы — один {@code IN}-запрос
     * (apply-слой, без N+1). Пустой вход → пустой список (NO_OP).
     */
    public List<FieldOverride> findByEntity(OverrideEntity table, Collection<UUID> entityIds) {
        if (entityIds.isEmpty()) {
            return List.of();
        }
        String placeholders = entityIds.stream().map(x -> "?").collect(Collectors.joining(","));
        Object[] args = new Object[entityIds.size() + 1];
        args[0] = table.tableName();
        int i = 1;
        for (UUID id : entityIds) {
            args[i++] = id;
        }
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_field_overrides "
                        + "WHERE entity_table = ? AND entity_id IN (" + placeholders + ")",
                ROW_MAPPER, args);
    }

    /** Overrides одной записи (для admin-вида «что переопределено/скрыто»). */
    public List<FieldOverride> findByEntityId(OverrideEntity table, UUID entityId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_field_overrides "
                        + "WHERE entity_table = ? AND entity_id = ? ORDER BY field_name",
                ROW_MAPPER, table.tableName(), entityId);
    }

    public Optional<FieldOverride> findOne(OverrideEntity table, UUID entityId, String fieldName) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_field_overrides "
                        + "WHERE entity_table = ? AND entity_id = ? AND field_name = ?",
                ROW_MAPPER, table.tableName(), entityId, fieldName).stream().findFirst();
    }

    /** Откат правки к импортному значению. Возвращает число удалённых строк (0 → 404). */
    public int delete(OverrideEntity table, UUID entityId, String fieldName) {
        return jdbcTemplate.update(
                "DELETE FROM hd_field_overrides "
                        + "WHERE entity_table = ? AND entity_id = ? AND field_name = ?",
                table.tableName(), entityId, fieldName);
    }
}
