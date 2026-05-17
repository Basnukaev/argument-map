package ru.basnukaev.argumentmap.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.domain.NodeVote;
import ru.basnukaev.argumentmap.domain.VoteStats;

/**
 * JDBC-репозиторий для голосов за узлы.
 *
 * <p>Голос - одно из {-1, +1}. Один user может проголосовать за один node
 * только один раз - повторный vote upsert'ится (UPDATE существующей строки
 * с новым weight и обновлённым voted_at).
 */
@Repository
public class NodeVoteRepository {

    private static final String COLUMNS = "id, node_id, user_id, weight, voted_at";

    private static final RowMapper<NodeVote> ROW_MAPPER = (rs, rn) -> new NodeVote(
            rs.getObject("id", UUID.class),
            rs.getObject("node_id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getInt("weight"),
            instant(rs, "voted_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public NodeVoteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Upsert голоса. Если строки нет - INSERT, если есть - UPDATE weight и
     * voted_at (но id и voted_at остаются от существующей записи если weight
     * не изменился; здесь обновляем всегда). Возвращает финальное состояние.
     */
    public NodeVote save(NodeVote vote) {
        jdbcTemplate.update(
                "INSERT INTO node_votes (" + COLUMNS + ") "
                        + "VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT (node_id, user_id) DO UPDATE "
                        + "SET weight = EXCLUDED.weight, voted_at = EXCLUDED.voted_at",
                vote.id(),
                vote.nodeId(),
                vote.userId(),
                vote.weight(),
                odt(vote.votedAt())
        );
        // после upsert id может отличаться (если был existing row с другим id);
        // вернём актуальную запись из БД
        return findByNodeAndUser(vote.nodeId(), vote.userId()).orElse(vote);
    }

    public Optional<NodeVote> findByNodeAndUser(UUID nodeId, UUID userId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM node_votes WHERE node_id = ? AND user_id = ?",
                ROW_MAPPER, nodeId, userId
        ).stream().findFirst();
    }

    public boolean deleteByNodeAndUser(UUID nodeId, UUID userId) {
        return jdbcTemplate.update(
                "DELETE FROM node_votes WHERE node_id = ? AND user_id = ?",
                nodeId, userId
        ) > 0;
    }

    public long countByNodeId(UUID nodeId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM node_votes WHERE node_id = ?",
                Long.class, nodeId
        );
        return count == null ? 0L : count;
    }

    /**
     * Статистика голосов одного узла. Если голосов нет - {@link VoteStats#EMPTY}.
     */
    public VoteStats getStatsForNode(UUID nodeId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT "
                            + "COALESCE(SUM(CASE WHEN weight = 1 THEN 1 ELSE 0 END), 0) AS up, "
                            + "COALESCE(SUM(CASE WHEN weight = -1 THEN 1 ELSE 0 END), 0) AS down "
                            + "FROM node_votes WHERE node_id = ?",
                    (rs, rn) -> VoteStats.of(rs.getInt("up"), rs.getInt("down")),
                    nodeId
            );
        } catch (EmptyResultDataAccessException e) {
            return VoteStats.EMPTY;
        }
    }

    /**
     * Bulk-запрос статистики для списка узлов. Возвращает map nodeId →
     * VoteStats. Узлы без голосов в map отсутствуют - caller должен трактовать
     * это как {@link VoteStats#EMPTY}. Пустой список - пустая map.
     */
    public Map<UUID, VoteStats> getStatsForNodes(Collection<UUID> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = nodeIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT node_id, "
                + "SUM(CASE WHEN weight = 1 THEN 1 ELSE 0 END) AS up, "
                + "SUM(CASE WHEN weight = -1 THEN 1 ELSE 0 END) AS down "
                + "FROM node_votes WHERE node_id IN (" + placeholders + ") "
                + "GROUP BY node_id";
        Map<UUID, VoteStats> result = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            UUID nodeId = rs.getObject("node_id", UUID.class);
            int up = rs.getInt("up");
            int down = rs.getInt("down");
            result.put(nodeId, VoteStats.of(up, down));
        }, nodeIds.toArray());
        return result;
    }

    /**
     * Голоса конкретного user'а для списка узлов. nodeId → weight (-1 либо +1).
     * Узлы где user не голосовал - в map отсутствуют. Используется чтобы
     * вернуть NodeResponse.userVote в bulk-операциях.
     */
    public Map<UUID, Integer> getUserVotesForNodes(Collection<UUID> nodeIds, UUID userId) {
        if (nodeIds == null || nodeIds.isEmpty() || userId == null) {
            return Map.of();
        }
        String placeholders = nodeIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT node_id, weight FROM node_votes "
                + "WHERE user_id = ? AND node_id IN (" + placeholders + ")";
        Object[] args = new Object[nodeIds.size() + 1];
        args[0] = userId;
        int i = 1;
        for (UUID id : nodeIds) {
            args[i++] = id;
        }
        Map<UUID, Integer> result = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            result.put(rs.getObject("node_id", UUID.class), rs.getInt("weight"));
        }, args);
        return result;
    }

    /**
     * Все голоса по одному узлу - для transparency (списка voter'ов).
     * Используется GET-эндпоинтом аггрегатной статистики.
     */
    public List<NodeVote> findByNodeId(UUID nodeId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM node_votes WHERE node_id = ? ORDER BY voted_at",
                ROW_MAPPER, nodeId
        );
    }
}
