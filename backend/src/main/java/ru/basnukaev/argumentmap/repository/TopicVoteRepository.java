package ru.basnukaev.argumentmap.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.domain.TopicVote;
import ru.basnukaev.argumentmap.domain.VoteStats;

/**
 * JDBC-репозиторий для голосов за темы (community-сигнал популярности).
 *
 * <p>Голос - одно из {-1, +1}. Один user может проголосовать за одну тему
 * только один раз - повторный vote upsert'ится (UPDATE существующей строки
 * с новым weight и обновлённым voted_at). Зеркалит удалённый NodeVoteRepository
 * но на уровне тем (ADR-053).
 */
@Repository
public class TopicVoteRepository {

    private static final String COLUMNS = "id, topic_id, user_id, weight, voted_at";

    private static final RowMapper<TopicVote> ROW_MAPPER = (rs, rn) -> new TopicVote(
            rs.getObject("id", UUID.class),
            rs.getObject("topic_id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getInt("weight"),
            instant(rs, "voted_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public TopicVoteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Upsert голоса. Если строки нет - INSERT, если есть - UPDATE weight и
     * voted_at. Возвращает финальное состояние из БД.
     */
    public TopicVote save(TopicVote vote) {
        jdbcTemplate.update(
                "INSERT INTO topic_votes (" + COLUMNS + ") "
                        + "VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT (topic_id, user_id) DO UPDATE "
                        + "SET weight = EXCLUDED.weight, voted_at = EXCLUDED.voted_at",
                vote.id(),
                vote.topicId(),
                vote.userId(),
                vote.weight(),
                odt(vote.votedAt())
        );
        // после upsert id может отличаться (если был existing row с другим id);
        // вернём актуальную запись из БД
        return findByTopicAndUser(vote.topicId(), vote.userId()).orElse(vote);
    }

    public Optional<TopicVote> findByTopicAndUser(UUID topicId, UUID userId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM topic_votes WHERE topic_id = ? AND user_id = ?",
                ROW_MAPPER, topicId, userId
        ).stream().findFirst();
    }

    public boolean deleteByTopicAndUser(UUID topicId, UUID userId) {
        return jdbcTemplate.update(
                "DELETE FROM topic_votes WHERE topic_id = ? AND user_id = ?",
                topicId, userId
        ) > 0;
    }

    /**
     * Статистика голосов одной темы. Если голосов нет - {@link VoteStats#EMPTY}.
     */
    public VoteStats getStatsForTopic(UUID topicId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT "
                            + "COALESCE(SUM(CASE WHEN weight = 1 THEN 1 ELSE 0 END), 0) AS up, "
                            + "COALESCE(SUM(CASE WHEN weight = -1 THEN 1 ELSE 0 END), 0) AS down "
                            + "FROM topic_votes WHERE topic_id = ?",
                    (rs, rn) -> VoteStats.of(rs.getInt("up"), rs.getInt("down")),
                    topicId
            );
        } catch (EmptyResultDataAccessException e) {
            return VoteStats.EMPTY;
        }
    }

    /**
     * Bulk-запрос статистики для списка тем. Возвращает map topicId →
     * VoteStats. Темы без голосов в map отсутствуют - caller должен трактовать
     * это как {@link VoteStats#EMPTY}. Пустой список - пустая map.
     */
    public Map<UUID, VoteStats> getStatsForTopics(Collection<UUID> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = topicIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT topic_id, "
                + "SUM(CASE WHEN weight = 1 THEN 1 ELSE 0 END) AS up, "
                + "SUM(CASE WHEN weight = -1 THEN 1 ELSE 0 END) AS down "
                + "FROM topic_votes WHERE topic_id IN (" + placeholders + ") "
                + "GROUP BY topic_id";
        Map<UUID, VoteStats> result = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            UUID topicId = rs.getObject("topic_id", UUID.class);
            int up = rs.getInt("up");
            int down = rs.getInt("down");
            result.put(topicId, VoteStats.of(up, down));
        }, topicIds.toArray());
        return result;
    }

    /**
     * Текущий голос user'а за тему: -1, +1 либо empty если не голосовал.
     */
    public Optional<Integer> getUserVote(UUID topicId, UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return findByTopicAndUser(topicId, userId).map(TopicVote::weight);
    }

    /**
     * Голоса конкретного user'а для списка тем. topicId → weight (-1 либо +1).
     * Темы где user не голосовал - в map отсутствуют. Используется чтобы
     * вернуть TopicResponse.userVote в bulk-операциях (list path).
     */
    public Map<UUID, Integer> getUserVotesForTopics(Collection<UUID> topicIds, UUID userId) {
        if (topicIds == null || topicIds.isEmpty() || userId == null) {
            return Map.of();
        }
        String placeholders = topicIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT topic_id, weight FROM topic_votes "
                + "WHERE user_id = ? AND topic_id IN (" + placeholders + ")";
        Object[] args = new Object[topicIds.size() + 1];
        args[0] = userId;
        int i = 1;
        for (UUID id : topicIds) {
            args[i++] = id;
        }
        Map<UUID, Integer> result = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            result.put(rs.getObject("topic_id", UUID.class), rs.getInt("weight"));
        }, args);
        return result;
    }
}
