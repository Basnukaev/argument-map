package ru.basnukaev.argumentmap.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.domain.AuditLog;

/**
 * Доступ к {@code audit_log} (Этап 22.d, ADR-043 Amendment 3).
 *
 * <p>JSON-колонки {@code changes}/{@code metadata} пишутся через
 * {@code ?::jsonb} cast - тот же подход что в
 * {@link ru.basnukaev.argumentmap.library.repository.BookRepository}.
 *
 * <p>Сортировка по умолчанию {@code created_at DESC} (новые сверху) для
 * UI audit history.
 */
@Repository
public class AuditLogRepository {

    private static final String COLUMNS =
            "id, entity_type, entity_id, parent_entity_type, parent_entity_id, "
                    + "action, actor_user_id, changes, metadata, created_at";

    private static final RowMapper<AuditLog> ROW_MAPPER = (rs, rn) -> new AuditLog(
            rs.getObject("id", UUID.class),
            rs.getString("entity_type"),
            rs.getObject("entity_id", UUID.class),
            rs.getString("parent_entity_type"),
            rs.getObject("parent_entity_id", UUID.class),
            rs.getString("action"),
            rs.getObject("actor_user_id", UUID.class),
            rs.getString("changes"),
            rs.getString("metadata"),
            instant(rs, "created_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public AuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AuditLog save(AuditLog log) {
        jdbcTemplate.update(
                "INSERT INTO audit_log (" + COLUMNS + ") "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)",
                log.id(),
                log.entityType(),
                log.entityId(),
                log.parentEntityType(),
                log.parentEntityId(),
                log.action(),
                log.actorUserId(),
                log.changes(),
                log.metadata(),
                odt(log.createdAt())
        );
        return log;
    }

    public List<AuditLog> findByEntityPage(String entityType, UUID entityId,
                                           int limit, int offset) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM audit_log "
                        + "WHERE entity_type = ? AND entity_id = ? "
                        + "ORDER BY created_at DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, entityType, entityId, limit, offset
        );
    }

    public long countByEntity(String entityType, UUID entityId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log "
                        + "WHERE entity_type = ? AND entity_id = ?",
                Long.class, entityType, entityId
        );
        return count == null ? 0L : count;
    }

    /**
     * Возвращает audit log для всех child entities заданного родителя
     * (например все nodes/edges темы) + сам родитель если его entity_type
     * совпадает с parentType. Используется для GET /audit/topics/{id} -
     * хочется одним запросом увидеть все события связанные с темой.
     *
     * <p>Включает rows где
     * {@code parent_entity_id=? AND parent_entity_type=?} ИЛИ
     * {@code entity_id=? AND entity_type=?} (сам parent).
     */
    public List<AuditLog> findByParentOrSelfPage(String parentType, UUID parentId,
                                                 int limit, int offset) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM audit_log "
                        + "WHERE (parent_entity_type = ? AND parent_entity_id = ?) "
                        + "   OR (entity_type = ? AND entity_id = ?) "
                        + "ORDER BY created_at DESC LIMIT ? OFFSET ?",
                ROW_MAPPER,
                parentType, parentId,
                parentType, parentId,
                limit, offset
        );
    }

    public long countByParentOrSelf(String parentType, UUID parentId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log "
                        + "WHERE (parent_entity_type = ? AND parent_entity_id = ?) "
                        + "   OR (entity_type = ? AND entity_id = ?)",
                Long.class,
                parentType, parentId,
                parentType, parentId
        );
        return count == null ? 0L : count;
    }

    public List<AuditLog> findByActorPage(UUID actorId, int limit, int offset) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM audit_log "
                        + "WHERE actor_user_id = ? "
                        + "ORDER BY created_at DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, actorId, limit, offset
        );
    }

    public long countByActor(UUID actorId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE actor_user_id = ?",
                Long.class, actorId
        );
        return count == null ? 0L : count;
    }

    /**
     * Admin-view: фильтры по entityType / actor / date-range. Все
     * опциональные. Используется в {@code GET /api/v1/audit/admin}.
     */
    public List<AuditLog> findFilteredPage(String entityType, UUID actorId,
                                           Instant dateFrom, Instant dateTo,
                                           int limit, int offset) {
        StringBuilder sql = new StringBuilder(
                "SELECT " + COLUMNS + " FROM audit_log WHERE 1=1"
        );
        List<Object> args = new ArrayList<>();
        appendAdminFilters(sql, args, entityType, actorId, dateFrom, dateTo);
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    public long countFiltered(String entityType, UUID actorId,
                              Instant dateFrom, Instant dateTo) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM audit_log WHERE 1=1"
        );
        List<Object> args = new ArrayList<>();
        appendAdminFilters(sql, args, entityType, actorId, dateFrom, dateTo);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    /**
     * Удаляет записи audit_log с {@code created_at < cutoff}. Используется
     * {@code AuditLogRetentionJanitor} для periodic cleanup (compliance
     * retention policy). Возвращает количество удалённых строк.
     */
    public int deleteOlderThan(Instant cutoff) {
        return jdbcTemplate.update(
                "DELETE FROM audit_log WHERE created_at < ?",
                Timestamp.from(cutoff)
        );
    }

    /**
     * Helper - один источник истины для WHERE-фильтров admin endpoint.
     * Гарантирует что count не разойдётся с list.
     */
    private static void appendAdminFilters(StringBuilder sql, List<Object> args,
                                           String entityType, UUID actorId,
                                           Instant dateFrom, Instant dateTo) {
        if (entityType != null) {
            sql.append(" AND entity_type = ?");
            args.add(entityType);
        }
        if (actorId != null) {
            sql.append(" AND actor_user_id = ?");
            args.add(actorId);
        }
        if (dateFrom != null) {
            sql.append(" AND created_at >= ?");
            args.add(Timestamp.from(dateFrom));
        }
        if (dateTo != null) {
            sql.append(" AND created_at <= ?");
            args.add(Timestamp.from(dateTo));
        }
    }
}
