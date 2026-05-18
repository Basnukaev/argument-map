package ru.basnukaev.argumentmap.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;

@Repository
public class NodeRepository {

    // original_lang остался после миграции 45 (translation / translation_lang
    // вынесены в child-таблицу node_translations - см. NodeTranslationRepository)
    private static final String COLUMNS =
            "id, topic_id, node_type, content, status, pos_x, pos_y, z_index, created_by, "
                    + "created_at, updated_at, original_lang";

    private static final RowMapper<Node> ROW_MAPPER = (rs, rn) -> new Node(
            rs.getObject("id", UUID.class),
            rs.getObject("topic_id", UUID.class),
            NodeType.valueOf(rs.getString("node_type")),
            rs.getString("content"),
            NodeStatus.valueOf(rs.getString("status")),
            (Double) rs.getObject("pos_x"),
            (Double) rs.getObject("pos_y"),
            rs.getInt("z_index"),
            rs.getObject("created_by", UUID.class),
            instant(rs, "created_at"),
            instant(rs, "updated_at"),
            rs.getString("original_lang")
    );

    private final JdbcTemplate jdbcTemplate;

    public NodeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Node save(Node node) {
        jdbcTemplate.update(
                "INSERT INTO nodes (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                node.id(),
                node.topicId(),
                node.nodeType().name(),
                node.content(),
                node.status().name(),
                node.posX(),
                node.posY(),
                node.zIndex(),
                node.createdBy(),
                odt(node.createdAt()),
                odt(node.updatedAt()),
                node.originalLang()
        );
        return node;
    }

    public Optional<Node> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM nodes WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    public List<Node> findByTopicId(UUID topicId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM nodes WHERE topic_id = ? ORDER BY created_at",
                ROW_MAPPER,
                topicId
        );
    }

    public void update(Node node) {
        jdbcTemplate.update(
                "UPDATE nodes SET content = ?, status = ?, updated_at = ?, original_lang = ? "
                        + "WHERE id = ?",
                node.content(),
                node.status().name(),
                odt(node.updatedAt()),
                node.originalLang(),
                node.id()
        );
    }

    public void updateStatus(UUID nodeId, NodeStatus status, Instant updatedAt) {
        jdbcTemplate.update(
                "UPDATE nodes SET status = ?, updated_at = ? WHERE id = ?",
                status.name(),
                odt(updatedAt),
                nodeId
        );
    }

    /**
     * Обновление координат узла на канвасе. Не пишет revision (позиция -
     * не часть содержимого), не меняет updatedAt. Возвращает true если
     * запись существовала и была обновлена.
     */
    public boolean updatePosition(UUID nodeId, Double posX, Double posY) {
        return jdbcTemplate.update(
                "UPDATE nodes SET pos_x = ?, pos_y = ? WHERE id = ?",
                posX,
                posY,
                nodeId
        ) > 0;
    }

    /**
     * Обновление stacking order (z_index) узла на канвасе. По аналогии с
     * updatePosition - не пишет revision, не меняет updatedAt: z-order это
     * UI affordance, не доменное изменение содержимого. Возвращает true
     * если запись существовала и была обновлена.
     */
    public boolean updateZIndex(UUID nodeId, int zIndex) {
        return jdbcTemplate.update(
                "UPDATE nodes SET z_index = ? WHERE id = ?",
                zIndex,
                nodeId
        ) > 0;
    }

    /**
     * Возвращает максимальный z_index среди узлов темы. Если тема пуста -
     * 0 (default из DDL). Используется для «На передний план»: новый
     * z_index = findMaxZIndex(topicId) + 1.
     */
    public int findMaxZIndex(UUID topicId) {
        Integer max = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(z_index), 0) FROM nodes WHERE topic_id = ?",
                Integer.class,
                topicId
        );
        return max == null ? 0 : max;
    }

    /**
     * Возвращает минимальный z_index среди узлов темы. Если тема пуста -
     * 0. Используется для «На задний план»: новый z_index = findMinZIndex
     * (topicId) - 1.
     */
    public int findMinZIndex(UUID topicId) {
        Integer min = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MIN(z_index), 0) FROM nodes WHERE topic_id = ?",
                Integer.class,
                topicId
        );
        return min == null ? 0 : min;
    }

    public boolean deleteById(UUID id) {
        return jdbcTemplate.update("DELETE FROM nodes WHERE id = ?", id) > 0;
    }
}
