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
import ru.basnukaev.argumentmap.exception.InvalidVoteException;
import ru.basnukaev.argumentmap.exception.QuestionNotFoundException;
import ru.basnukaev.argumentmap.qa.domain.QuestionVote;
import ru.basnukaev.argumentmap.qa.repository.QuestionVoteRepository;

/**
 * IT для {@link QuestionVoteService} - голосование за вопросы Q&amp;A.
 * Зеркалит {@code TopicVoteServiceIT} но без visibility/permission - questions
 * это open discussion (голосовать может любой authenticated user).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class QuestionVoteServiceIT {

    @Autowired
    private QuestionVoteService questionVoteService;

    @Autowired
    private QuestionVoteRepository questionVoteRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID authorId;
    private UUID otherUserId;
    private UUID questionId;

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
    }

    @Test
    void vote_firstTime_creates() {
        QuestionVote vote = questionVoteService.vote(questionId, authorId, 1);

        assertThat(vote.weight()).isEqualTo(1);
        assertThat(vote.questionId()).isEqualTo(questionId);
        assertThat(vote.userId()).isEqualTo(authorId);
        assertThat(vote.votedAt()).isNotNull();

        VoteStats stats = questionVoteRepository.getStatsForQuestion(questionId);
        assertThat(stats.upvotes()).isEqualTo(1);
        assertThat(stats.downvotes()).isZero();
        assertThat(stats.score()).isEqualTo(1);
    }

    @Test
    void vote_existing_updates() {
        questionVoteService.vote(questionId, authorId, 1);
        // тот же user меняет голос на downvote - upsert
        QuestionVote updated = questionVoteService.vote(questionId, authorId, -1);

        assertThat(updated.weight()).isEqualTo(-1);

        VoteStats stats = questionVoteRepository.getStatsForQuestion(questionId);
        assertThat(stats.upvotes()).isZero();
        assertThat(stats.downvotes()).isEqualTo(1);
        assertThat(stats.score()).isEqualTo(-1);
    }

    @Test
    void vote_byNonAuthor_allowed() {
        // open discussion - любой authenticated user может голосовать, не только автор
        QuestionVote vote = questionVoteService.vote(questionId, otherUserId, 1);

        assertThat(vote.weight()).isEqualTo(1);
        assertThat(questionVoteService.getStats(questionId).score()).isEqualTo(1);
    }

    @Test
    void vote_invalidWeight_throws() {
        assertThatThrownBy(() -> questionVoteService.vote(questionId, authorId, 0))
                .isInstanceOf(InvalidVoteException.class);
        assertThatThrownBy(() -> questionVoteService.vote(questionId, authorId, 2))
                .isInstanceOf(InvalidVoteException.class);
        assertThatThrownBy(() -> questionVoteService.vote(questionId, authorId, -2))
                .isInstanceOf(InvalidVoteException.class);
    }

    @Test
    void vote_questionNotFound_throws() {
        assertThatThrownBy(() -> questionVoteService.vote(UUID.randomUUID(), authorId, 1))
                .isInstanceOf(QuestionNotFoundException.class);
    }

    @Test
    void removeVote_existing_deletes() {
        questionVoteService.vote(questionId, authorId, 1);

        boolean removed = questionVoteService.removeVote(questionId, authorId);

        assertThat(removed).isTrue();
        assertThat(questionVoteRepository.findByQuestionAndUser(questionId, authorId)).isEmpty();
    }

    @Test
    void removeVote_notVoted_idempotent() {
        boolean removed = questionVoteService.removeVote(questionId, authorId);

        assertThat(removed).isFalse();
    }

    @Test
    void removeVote_questionNotFound_throws() {
        assertThatThrownBy(() -> questionVoteService.removeVote(UUID.randomUUID(), authorId))
                .isInstanceOf(QuestionNotFoundException.class);
    }

    @Test
    void getStats_aggregatesMultipleUsers() {
        questionVoteService.vote(questionId, authorId, 1);
        questionVoteService.vote(questionId, otherUserId, -1);

        VoteStats stats = questionVoteService.getStats(questionId);

        assertThat(stats.upvotes()).isEqualTo(1);
        assertThat(stats.downvotes()).isEqualTo(1);
        assertThat(stats.score()).isZero();
    }

    @Test
    void getStats_questionNotFound_throws() {
        assertThatThrownBy(() -> questionVoteService.getStats(UUID.randomUUID()))
                .isInstanceOf(QuestionNotFoundException.class);
    }

    @Test
    void getUserVote_returnsCurrent() {
        questionVoteService.vote(questionId, authorId, -1);

        assertThat(questionVoteService.getUserVote(questionId, authorId)).contains(-1);
        assertThat(questionVoteService.getUserVote(questionId, otherUserId)).isEmpty();
    }

    @Test
    void getStatsForQuestions_bulk_groupsByQuestion() {
        UUID question2 = createQuestion("Q2", authorId);
        questionVoteService.vote(questionId, authorId, 1);
        questionVoteService.vote(questionId, otherUserId, 1);
        questionVoteService.vote(question2, authorId, -1);

        Map<UUID, VoteStats> stats =
                questionVoteRepository.getStatsForQuestions(List.of(questionId, question2));

        assertThat(stats.get(questionId).score()).isEqualTo(2);
        assertThat(stats.get(question2).score()).isEqualTo(-1);
    }

    private UUID createQuestion(String title, UUID askedBy) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO questions (id, title, status, asked_by) VALUES (?, ?, 'OPEN', ?)",
                id, title, askedBy
        );
        return id;
    }
}
