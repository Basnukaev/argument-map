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
import ru.basnukaev.argumentmap.qa.domain.QuestionVote;

/**
 * JDBC-репозиторий для голосов за вопросы Q&amp;A (community-сигнал
 * популярности).
 *
 * <p>Голос - одно из {-1, +1}. Один user может проголосовать за один вопрос
 * только один раз - повторный vote upsert'ится (UPDATE существующей строки с
 * новым weight и обновлённым voted_at). Зеркалит
 * {@link ru.basnukaev.argumentmap.repository.TopicVoteRepository} но на уровне
 * вопросов.
 */
@Repository
public class QuestionVoteRepository {

    private static final String COLUMNS = "id, question_id, user_id, weight, voted_at";

    private static final RowMapper<QuestionVote> ROW_MAPPER = (rs, rn) -> new QuestionVote(
            rs.getObject("id", UUID.class),
            rs.getObject("question_id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getInt("weight"),
            instant(rs, "voted_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public QuestionVoteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Upsert голоса. Если строки нет - INSERT, если есть - UPDATE weight и
     * voted_at. Возвращает финальное состояние из БД.
     */
    public QuestionVote save(QuestionVote vote) {
        jdbcTemplate.update(
                "INSERT INTO question_votes (" + COLUMNS + ") "
                        + "VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT (question_id, user_id) DO UPDATE "
                        + "SET weight = EXCLUDED.weight, voted_at = EXCLUDED.voted_at",
                vote.id(),
                vote.questionId(),
                vote.userId(),
                vote.weight(),
                odt(vote.votedAt())
        );
        // после upsert id может отличаться (если был existing row с другим id);
        // вернём актуальную запись из БД
        return findByQuestionAndUser(vote.questionId(), vote.userId()).orElse(vote);
    }

    public Optional<QuestionVote> findByQuestionAndUser(UUID questionId, UUID userId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM question_votes WHERE question_id = ? AND user_id = ?",
                ROW_MAPPER, questionId, userId
        ).stream().findFirst();
    }

    public boolean deleteByQuestionAndUser(UUID questionId, UUID userId) {
        return jdbcTemplate.update(
                "DELETE FROM question_votes WHERE question_id = ? AND user_id = ?",
                questionId, userId
        ) > 0;
    }

    /**
     * Статистика голосов одного вопроса. Если голосов нет - {@link VoteStats#EMPTY}.
     */
    public VoteStats getStatsForQuestion(UUID questionId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT "
                            + "COALESCE(SUM(CASE WHEN weight = 1 THEN 1 ELSE 0 END), 0) AS up, "
                            + "COALESCE(SUM(CASE WHEN weight = -1 THEN 1 ELSE 0 END), 0) AS down "
                            + "FROM question_votes WHERE question_id = ?",
                    (rs, rn) -> VoteStats.of(rs.getInt("up"), rs.getInt("down")),
                    questionId
            );
        } catch (EmptyResultDataAccessException e) {
            return VoteStats.EMPTY;
        }
    }

    /**
     * Bulk-запрос статистики для списка вопросов. Возвращает map questionId →
     * VoteStats. Вопросы без голосов в map отсутствуют - caller должен
     * трактовать это как {@link VoteStats#EMPTY}. Пустой список - пустая map.
     */
    public Map<UUID, VoteStats> getStatsForQuestions(Collection<UUID> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = questionIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT question_id, "
                + "SUM(CASE WHEN weight = 1 THEN 1 ELSE 0 END) AS up, "
                + "SUM(CASE WHEN weight = -1 THEN 1 ELSE 0 END) AS down "
                + "FROM question_votes WHERE question_id IN (" + placeholders + ") "
                + "GROUP BY question_id";
        Map<UUID, VoteStats> result = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            UUID questionId = rs.getObject("question_id", UUID.class);
            int up = rs.getInt("up");
            int down = rs.getInt("down");
            result.put(questionId, VoteStats.of(up, down));
        }, questionIds.toArray());
        return result;
    }

    /**
     * Текущий голос user'а за вопрос: -1, +1 либо empty если не голосовал.
     */
    public Optional<Integer> getUserVote(UUID questionId, UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return findByQuestionAndUser(questionId, userId).map(QuestionVote::weight);
    }

    /**
     * Голоса конкретного user'а для списка вопросов. questionId → weight
     * (-1 либо +1). Вопросы где user не голосовал - в map отсутствуют.
     * Используется чтобы вернуть QuestionResponse.userVote в bulk-операциях
     * (list path).
     */
    public Map<UUID, Integer> getUserVotesForQuestions(Collection<UUID> questionIds, UUID userId) {
        if (questionIds == null || questionIds.isEmpty() || userId == null) {
            return Map.of();
        }
        String placeholders = questionIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT question_id, weight FROM question_votes "
                + "WHERE user_id = ? AND question_id IN (" + placeholders + ")";
        Object[] args = new Object[questionIds.size() + 1];
        args[0] = userId;
        int i = 1;
        for (UUID id : questionIds) {
            args[i++] = id;
        }
        Map<UUID, Integer> result = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            result.put(rs.getObject("question_id", UUID.class), rs.getInt("weight"));
        }, args);
        return result;
    }
}
