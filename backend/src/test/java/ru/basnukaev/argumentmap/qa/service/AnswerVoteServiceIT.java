package ru.basnukaev.argumentmap.qa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.domain.VoteStats;
import ru.basnukaev.argumentmap.exception.AnswerNotFoundException;
import ru.basnukaev.argumentmap.exception.InvalidVoteException;
import ru.basnukaev.argumentmap.qa.domain.AnswerVote;
import ru.basnukaev.argumentmap.qa.repository.AnswerVoteRepository;

/**
 * IT для {@link AnswerVoteService} - голосование за отдельные ответы Q&amp;A.
 * Зеркалит {@code QuestionVoteServiceIT} но на уровне ответов - answers это
 * open discussion (голосовать может любой authenticated user).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AnswerVoteServiceIT {

    @Autowired
    private AnswerVoteService answerVoteService;

    @Autowired
    private AnswerVoteRepository answerVoteRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID authorId;
    private UUID otherUserId;
    private UUID questionId;
    private UUID answerId;

    @BeforeEach
    void setUp() {
        authorId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                authorId, "author-" + authorId, authorId + "@example.com"
        );
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                otherUserId, "other-" + otherUserId, otherUserId + "@example.com"
        );
        questionId = createQuestion("Q-vote", authorId);
        answerId = createAnswer(questionId, "A-vote", authorId);
    }

    @Test
    void vote_firstTime_creates() {
        AnswerVote vote = answerVoteService.vote(answerId, authorId, 1);

        assertThat(vote.weight()).isEqualTo(1);
        assertThat(vote.answerId()).isEqualTo(answerId);
        assertThat(vote.userId()).isEqualTo(authorId);
        assertThat(vote.votedAt()).isNotNull();

        VoteStats stats = answerVoteRepository.getStatsForAnswer(answerId);
        assertThat(stats.upvotes()).isEqualTo(1);
        assertThat(stats.downvotes()).isZero();
        assertThat(stats.score()).isEqualTo(1);
    }

    @Test
    void vote_existing_updates() {
        answerVoteService.vote(answerId, authorId, 1);
        // тот же user меняет голос на downvote - upsert
        AnswerVote updated = answerVoteService.vote(answerId, authorId, -1);

        assertThat(updated.weight()).isEqualTo(-1);

        VoteStats stats = answerVoteRepository.getStatsForAnswer(answerId);
        assertThat(stats.upvotes()).isZero();
        assertThat(stats.downvotes()).isEqualTo(1);
        assertThat(stats.score()).isEqualTo(-1);
    }

    @Test
    void vote_byNonAuthor_allowed() {
        // open discussion - любой authenticated user может голосовать, не только автор
        AnswerVote vote = answerVoteService.vote(answerId, otherUserId, 1);

        assertThat(vote.weight()).isEqualTo(1);
        assertThat(answerVoteService.getStats(answerId).score()).isEqualTo(1);
    }

    @Test
    void vote_invalidWeight_throws() {
        assertThatThrownBy(() -> answerVoteService.vote(answerId, authorId, 0))
                .isInstanceOf(InvalidVoteException.class);
        assertThatThrownBy(() -> answerVoteService.vote(answerId, authorId, 2))
                .isInstanceOf(InvalidVoteException.class);
        assertThatThrownBy(() -> answerVoteService.vote(answerId, authorId, -2))
                .isInstanceOf(InvalidVoteException.class);
    }

    @Test
    void vote_answerNotFound_throws() {
        assertThatThrownBy(() -> answerVoteService.vote(UUID.randomUUID(), authorId, 1))
                .isInstanceOf(AnswerNotFoundException.class);
    }

    @Test
    void removeVote_existing_deletes() {
        answerVoteService.vote(answerId, authorId, 1);

        boolean removed = answerVoteService.removeVote(answerId, authorId);

        assertThat(removed).isTrue();
        assertThat(answerVoteRepository.findByAnswerAndUser(answerId, authorId)).isEmpty();
    }

    @Test
    void removeVote_notVoted_idempotent() {
        boolean removed = answerVoteService.removeVote(answerId, authorId);

        assertThat(removed).isFalse();
    }

    @Test
    void removeVote_answerNotFound_throws() {
        assertThatThrownBy(() -> answerVoteService.removeVote(UUID.randomUUID(), authorId))
                .isInstanceOf(AnswerNotFoundException.class);
    }

    @Test
    void getStats_aggregatesMultipleUsers() {
        answerVoteService.vote(answerId, authorId, 1);
        answerVoteService.vote(answerId, otherUserId, -1);

        VoteStats stats = answerVoteService.getStats(answerId);

        assertThat(stats.upvotes()).isEqualTo(1);
        assertThat(stats.downvotes()).isEqualTo(1);
        assertThat(stats.score()).isZero();
    }

    @Test
    void getStats_answerNotFound_throws() {
        assertThatThrownBy(() -> answerVoteService.getStats(UUID.randomUUID()))
                .isInstanceOf(AnswerNotFoundException.class);
    }

    @Test
    void getUserVote_returnsCurrent() {
        answerVoteService.vote(answerId, authorId, -1);

        assertThat(answerVoteService.getUserVote(answerId, authorId)).contains(-1);
        assertThat(answerVoteService.getUserVote(answerId, otherUserId)).isEmpty();
    }

    @Test
    void getStatsForAnswers_bulk_groupsByAnswer() {
        UUID answer2 = createAnswer(questionId, "A2", authorId);
        answerVoteService.vote(answerId, authorId, 1);
        answerVoteService.vote(answerId, otherUserId, 1);
        answerVoteService.vote(answer2, authorId, -1);

        Map<UUID, VoteStats> stats =
                answerVoteRepository.getStatsForAnswers(List.of(answerId, answer2));

        assertThat(stats.get(answerId).score()).isEqualTo(2);
        assertThat(stats.get(answer2).score()).isEqualTo(-1);
    }

    @Test
    void getUserVotesForAnswers_bulk_keyedByAnswer() {
        UUID answer2 = createAnswer(questionId, "A2", authorId);
        answerVoteService.vote(answerId, authorId, 1);
        answerVoteService.vote(answer2, authorId, -1);

        Map<UUID, Integer> votes =
                answerVoteRepository.getUserVotesForAnswers(List.of(answerId, answer2), authorId);

        assertThat(votes.get(answerId)).isEqualTo(1);
        assertThat(votes.get(answer2)).isEqualTo(-1);
        assertThat(votes).hasSize(2);
    }

    private UUID createQuestion(String title, UUID askedBy) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO questions (id, title, status, asked_by) VALUES (?, ?, 'OPEN', ?)",
                id, title, askedBy
        );
        return id;
    }

    private UUID createAnswer(UUID qId, String body, UUID authorIdParam) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO answers (id, question_id, body, author_id) VALUES (?, ?, ?, ?)",
                id, qId, body, authorIdParam
        );
        return id;
    }
}
