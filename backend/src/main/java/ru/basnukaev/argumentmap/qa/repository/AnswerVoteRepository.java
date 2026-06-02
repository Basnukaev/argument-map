package ru.basnukaev.argumentmap.qa.repository;

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

import ru.basnukaev.argumentmap.domain.VoteStats;
import ru.basnukaev.argumentmap.qa.domain.AnswerVote;

/**
 * JDBC-репозиторий для голосов за отдельные ответы Q&amp;A (community-сигнал
 * качества ответа).
 *
 * <p>Голос - одно из {-1, +1}. Один user может проголосовать за один ответ
 * только один раз - повторный vote upsert'ится (UPDATE существующей строки с
 * новым weight и обновлённым voted_at). Зеркалит
 * {@link QuestionVoteRepository} но на уровне ответов.
 */
@Repository
public class AnswerVoteRepository {

    private static final String COLUMNS = "id, answer_id, user_id, weight, voted_at";

    private static final RowMapper<AnswerVote> ROW_MAPPER = (rs, rn) -> new AnswerVote(
            rs.getObject("id", UUID.class),
            rs.getObject("answer_id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getInt("weight"),
            instant(rs, "voted_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public AnswerVoteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Upsert голоса. Если строки нет - INSERT, если есть - UPDATE weight и
     * voted_at. Возвращает финальное состояние из БД.
     */
    public AnswerVote save(AnswerVote vote) {
        jdbcTemplate.update(
                "INSERT INTO answer_votes (" + COLUMNS + ") "
                        + "VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT (answer_id, user_id) DO UPDATE "
                        + "SET weight = EXCLUDED.weight, voted_at = EXCLUDED.voted_at",
                vote.id(),
                vote.answerId(),
                vote.userId(),
                vote.weight(),
                odt(vote.votedAt())
        );
        // после upsert id может отличаться (если был existing row с другим id);
        // вернём актуальную запись из БД
        return findByAnswerAndUser(vote.answerId(), vote.userId()).orElse(vote);
    }

    public Optional<AnswerVote> findByAnswerAndUser(UUID answerId, UUID userId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM answer_votes WHERE answer_id = ? AND user_id = ?",
                ROW_MAPPER, answerId, userId
        ).stream().findFirst();
    }

    public boolean deleteByAnswerAndUser(UUID answerId, UUID userId) {
        return jdbcTemplate.update(
                "DELETE FROM answer_votes WHERE answer_id = ? AND user_id = ?",
                answerId, userId
        ) > 0;
    }

    /**
     * Статистика голосов одного ответа. Если голосов нет - {@link VoteStats#EMPTY}.
     */
    public VoteStats getStatsForAnswer(UUID answerId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT "
                            + "COALESCE(SUM(CASE WHEN weight = 1 THEN 1 ELSE 0 END), 0) AS up, "
                            + "COALESCE(SUM(CASE WHEN weight = -1 THEN 1 ELSE 0 END), 0) AS down "
                            + "FROM answer_votes WHERE answer_id = ?",
                    (rs, rn) -> VoteStats.of(rs.getInt("up"), rs.getInt("down")),
                    answerId
            );
        } catch (EmptyResultDataAccessException e) {
            return VoteStats.EMPTY;
        }
    }

    /**
     * Bulk-запрос статистики для списка ответов. Возвращает map answerId →
     * VoteStats. Ответы без голосов в map отсутствуют - caller должен
     * трактовать это как {@link VoteStats#EMPTY}. Пустой список - пустая map.
     */
    public Map<UUID, VoteStats> getStatsForAnswers(Collection<UUID> answerIds) {
        if (answerIds == null || answerIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = answerIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT answer_id, "
                + "SUM(CASE WHEN weight = 1 THEN 1 ELSE 0 END) AS up, "
                + "SUM(CASE WHEN weight = -1 THEN 1 ELSE 0 END) AS down "
                + "FROM answer_votes WHERE answer_id IN (" + placeholders + ") "
                + "GROUP BY answer_id";
        Map<UUID, VoteStats> result = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            UUID answerId = rs.getObject("answer_id", UUID.class);
            int up = rs.getInt("up");
            int down = rs.getInt("down");
            result.put(answerId, VoteStats.of(up, down));
        }, answerIds.toArray());
        return result;
    }

    /**
     * Текущий голос user'а за ответ: -1, +1 либо empty если не голосовал.
     */
    public Optional<Integer> getUserVote(UUID answerId, UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return findByAnswerAndUser(answerId, userId).map(AnswerVote::weight);
    }

    /**
     * Голоса конкретного user'а для списка ответов. answerId → weight
     * (-1 либо +1). Ответы где user не голосовал - в map отсутствуют.
     * Используется чтобы вернуть AnswerResponse.userVote в bulk-операциях
     * (list path).
     */
    public Map<UUID, Integer> getUserVotesForAnswers(Collection<UUID> answerIds, UUID userId) {
        if (answerIds == null || answerIds.isEmpty() || userId == null) {
            return Map.of();
        }
        String placeholders = answerIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT answer_id, weight FROM answer_votes "
                + "WHERE user_id = ? AND answer_id IN (" + placeholders + ")";
        Object[] args = new Object[answerIds.size() + 1];
        args[0] = userId;
        int i = 1;
        for (UUID id : answerIds) {
            args[i++] = id;
        }
        Map<UUID, Integer> result = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            result.put(rs.getObject("answer_id", UUID.class), rs.getInt("weight"));
        }, args);
        return result;
    }
}
