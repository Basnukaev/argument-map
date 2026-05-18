package ru.basnukaev.argumentmap.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.domain.NodeTranslation;

/**
 * JDBC-репозиторий для {@link NodeTranslation} (миграция 45).
 *
 * <p>Сортировка - default-перевод первым, затем по created_at ASC (фронт
 * получает stable порядок: «по умолчанию», далее в порядке добавления).
 *
 * <p>{@link #findByNodeIds(Collection)} - bulk-load для GET
 * /topics/{id}/graph: один SQL на весь граф, не N+1.
 */
@Repository
public class NodeTranslationRepository {

    private static final String COLUMNS =
            "id, node_id, translator_name, language, body, is_default, created_at, created_by";

    private static final RowMapper<NodeTranslation> ROW_MAPPER = (rs, rn) -> new NodeTranslation(
            rs.getObject("id", UUID.class),
            rs.getObject("node_id", UUID.class),
            rs.getString("translator_name"),
            rs.getString("language"),
            rs.getString("body"),
            rs.getBoolean("is_default"),
            instant(rs, "created_at"),
            rs.getObject("created_by", UUID.class)
    );

    private final JdbcTemplate jdbcTemplate;

    public NodeTranslationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public NodeTranslation save(NodeTranslation translation) {
        jdbcTemplate.update(
                "INSERT INTO node_translations (" + COLUMNS + ") "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                translation.id(),
                translation.nodeId(),
                translation.translatorName(),
                translation.language(),
                translation.body(),
                translation.isDefault(),
                odt(translation.createdAt()),
                translation.createdBy()
        );
        return translation;
    }

    public Optional<NodeTranslation> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM node_translations WHERE id = ?",
                ROW_MAPPER, id
        ).stream().findFirst();
    }

    /**
     * Все переводы узла. Сортировка: is_default DESC (default первым),
     * затем created_at ASC. Использовать в API responses и в GET endpoint.
     */
    public List<NodeTranslation> findByNodeId(UUID nodeId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM node_translations WHERE node_id = ? "
                        + "ORDER BY is_default DESC, created_at ASC",
                ROW_MAPPER, nodeId
        );
    }

    public Optional<NodeTranslation> findDefaultByNodeId(UUID nodeId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM node_translations "
                        + "WHERE node_id = ? AND is_default = true",
                ROW_MAPPER, nodeId
        ).stream().findFirst();
    }

    /**
     * Bulk-load переводов для всех узлов сразу. Один SQL на весь граф -
     * нет N+1 на GET /topics/{id}/graph.
     *
     * <p>Возвращает Map: nodeId → List переводов в default-первый порядке.
     * Узлы без переводов в map не присутствуют (caller использует getOrDefault).
     */
    public Map<UUID, List<NodeTranslation>> findByNodeIds(Collection<UUID> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = String.join(",", Collections.nCopies(nodeIds.size(), "?"));
        String sql = "SELECT " + COLUMNS + " FROM node_translations "
                + "WHERE node_id IN (" + placeholders + ") "
                + "ORDER BY node_id, is_default DESC, created_at ASC";
        List<NodeTranslation> all = jdbcTemplate.query(sql, ROW_MAPPER, nodeIds.toArray());
        Map<UUID, List<NodeTranslation>> grouped = new LinkedHashMap<>();
        for (NodeTranslation t : all) {
            grouped.computeIfAbsent(t.nodeId(), k -> new ArrayList<>()).add(t);
        }
        return grouped;
    }

    public boolean update(UUID id, String translatorName, String body) {
        return jdbcTemplate.update(
                "UPDATE node_translations SET translator_name = ?, body = ? WHERE id = ?",
                translatorName, body, id
        ) > 0;
    }

    /**
     * Atomic switch default-перевода внутри одного узла. Сначала снимаем
     * флаг со всех других переводов того же узла, затем ставим на нужный.
     * Один transaction (@Transactional на caller) - либо оба апдейта,
     * либо ни одного.
     *
     * <p>Возвращает true если translationId существует и принадлежит nodeId,
     * иначе false (caller реагирует 404 либо игнорирует).
     */
    public boolean setDefault(UUID translationId, UUID nodeId) {
        int unset = jdbcTemplate.update(
                "UPDATE node_translations SET is_default = false "
                        + "WHERE node_id = ? AND id != ?",
                nodeId, translationId
        );
        int set = jdbcTemplate.update(
                "UPDATE node_translations SET is_default = true "
                        + "WHERE id = ? AND node_id = ?",
                translationId, nodeId
        );
        // unset может быть 0 (если переводов больше нет) - не индикатор ошибки.
        // set должен быть 1 - если 0, значит translation отсутствует
        return set > 0;
    }

    public boolean deleteById(UUID id) {
        return jdbcTemplate.update("DELETE FROM node_translations WHERE id = ?", id) > 0;
    }

    public boolean existsForNodeTranslatorLanguage(UUID nodeId, String translatorName, String language) {
        // partial unique index покрывает оба случая (NULL и not-NULL) - проверяем
        // SQL'ем напрямую с IS NULL handling
        Long count;
        if (translatorName == null) {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM node_translations "
                            + "WHERE node_id = ? AND translator_name IS NULL AND language = ?",
                    Long.class, nodeId, language
            );
        } else {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM node_translations "
                            + "WHERE node_id = ? AND translator_name = ? AND language = ?",
                    Long.class, nodeId, translatorName, language
            );
        }
        return count != null && count > 0;
    }

    /**
     * Возвращает первый по created_at ASC перевод узла. Используется для
     * promote-после-delete-default flow: если удалили default, новый default
     * выбираем как самый старый из оставшихся (детерминированный порядок).
     */
    public Optional<NodeTranslation> findOldestByNodeId(UUID nodeId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM node_translations "
                        + "WHERE node_id = ? ORDER BY created_at ASC LIMIT 1",
                ROW_MAPPER, nodeId
        ).stream().findFirst();
    }
}
