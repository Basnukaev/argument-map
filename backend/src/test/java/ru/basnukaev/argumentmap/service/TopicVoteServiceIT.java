package ru.basnukaev.argumentmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.domain.TopicVote;
import ru.basnukaev.argumentmap.domain.VoteStats;
import ru.basnukaev.argumentmap.exception.InvalidVoteException;
import ru.basnukaev.argumentmap.exception.TopicAccessDeniedException;
import ru.basnukaev.argumentmap.exception.TopicNotFoundException;
import ru.basnukaev.argumentmap.repository.TopicVoteRepository;

/**
 * IT для {@link TopicVoteService} - голосование за темы (ADR-053).
 * Зеркалит удалённый NodeVoteServiceIT но на уровне тем.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class TopicVoteServiceIT {

    @Autowired
    private TopicVoteService topicVoteService;

    @Autowired
    private TopicVoteRepository topicVoteRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ownerId;
    private UUID otherUserId;
    private UUID topicId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                ownerId, "owner-" + ownerId, ownerId + "@example.com"
        );
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                otherUserId, "other-" + otherUserId, otherUserId + "@example.com"
        );
        topicId = UUID.randomUUID();
        // PUBLIC visibility - другие user'ы могут vote (read access)
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by, visibility) VALUES (?, ?, ?, 'PUBLIC')",
                topicId, "T-vote", ownerId
        );
    }

    @Test
    void vote_firstTime_creates() {
        TopicVote vote = topicVoteService.vote(topicId, ownerId, 1, UserRole.USER);

        assertThat(vote.weight()).isEqualTo(1);
        assertThat(vote.topicId()).isEqualTo(topicId);
        assertThat(vote.userId()).isEqualTo(ownerId);
        assertThat(vote.votedAt()).isNotNull();

        VoteStats stats = topicVoteRepository.getStatsForTopic(topicId);
        assertThat(stats.upvotes()).isEqualTo(1);
        assertThat(stats.downvotes()).isZero();
        assertThat(stats.score()).isEqualTo(1);
    }

    @Test
    void vote_existing_updates() {
        topicVoteService.vote(topicId, ownerId, 1, UserRole.USER);
        // тот же user меняет голос на downvote - upsert
        TopicVote updated = topicVoteService.vote(topicId, ownerId, -1, UserRole.USER);

        assertThat(updated.weight()).isEqualTo(-1);

        VoteStats stats = topicVoteRepository.getStatsForTopic(topicId);
        assertThat(stats.upvotes()).isZero();
        assertThat(stats.downvotes()).isEqualTo(1);
        assertThat(stats.score()).isEqualTo(-1);
    }

    @Test
    void vote_invalidWeight_throws() {
        assertThatThrownBy(() -> topicVoteService.vote(topicId, ownerId, 0, UserRole.USER))
                .isInstanceOf(InvalidVoteException.class);
        assertThatThrownBy(() -> topicVoteService.vote(topicId, ownerId, 2, UserRole.USER))
                .isInstanceOf(InvalidVoteException.class);
        assertThatThrownBy(() -> topicVoteService.vote(topicId, ownerId, -2, UserRole.USER))
                .isInstanceOf(InvalidVoteException.class);
    }

    @Test
    void vote_topicNotFound_throws() {
        assertThatThrownBy(() -> topicVoteService.vote(UUID.randomUUID(), ownerId, 1, UserRole.USER))
                .isInstanceOf(TopicNotFoundException.class);
    }

    @Test
    void vote_privateTopicNonOwner_throws403() {
        UUID privTopicId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by, visibility) VALUES (?, ?, ?, 'PRIVATE')",
                privTopicId, "T-priv", ownerId
        );

        assertThatThrownBy(() -> topicVoteService.vote(privTopicId, otherUserId, 1, UserRole.USER))
                .isInstanceOf(TopicAccessDeniedException.class);
    }

    @Test
    void removeVote_existing_deletes() {
        topicVoteService.vote(topicId, ownerId, 1, UserRole.USER);

        boolean removed = topicVoteService.removeVote(topicId, ownerId, UserRole.USER);

        assertThat(removed).isTrue();
        assertThat(topicVoteRepository.findByTopicAndUser(topicId, ownerId)).isEmpty();
    }

    @Test
    void removeVote_notVoted_idempotent() {
        boolean removed = topicVoteService.removeVote(topicId, ownerId, UserRole.USER);

        assertThat(removed).isFalse();
    }

    @Test
    void getStats_aggregatesMultipleUsers() {
        topicVoteService.vote(topicId, ownerId, 1, UserRole.USER);
        topicVoteService.vote(topicId, otherUserId, -1, UserRole.USER);

        VoteStats stats = topicVoteService.getStats(topicId, ownerId, UserRole.USER);

        assertThat(stats.upvotes()).isEqualTo(1);
        assertThat(stats.downvotes()).isEqualTo(1);
        assertThat(stats.score()).isZero();
    }

    @Test
    void getStats_privateTopicNonOwner_throws403() {
        UUID privTopicId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by, visibility) VALUES (?, ?, ?, 'PRIVATE')",
                privTopicId, "T-priv", ownerId
        );

        assertThatThrownBy(() -> topicVoteService.getStats(privTopicId, otherUserId, UserRole.USER))
                .isInstanceOf(TopicAccessDeniedException.class);
    }

    @Test
    void getStats_adminBypassesPrivate() {
        UUID privTopicId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by, visibility) VALUES (?, ?, ?, 'PRIVATE')",
                privTopicId, "T-priv", ownerId
        );
        // ADMIN видит агрегаты приватной чужой темы (bypass)
        VoteStats stats = topicVoteService.getStats(privTopicId, otherUserId, UserRole.ADMIN);
        assertThat(stats).isEqualTo(VoteStats.EMPTY);
    }

    @Test
    void getUserVote_returnsCurrent() {
        topicVoteService.vote(topicId, ownerId, -1, UserRole.USER);

        assertThat(topicVoteService.getUserVote(topicId, ownerId)).contains(-1);
        assertThat(topicVoteService.getUserVote(topicId, otherUserId)).isEmpty();
    }

    @Test
    void getStatsForTopics_bulk_groupsByTopic() {
        UUID topic2 = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by, visibility) VALUES (?, ?, ?, 'PUBLIC')",
                topic2, "T2", ownerId
        );
        topicVoteService.vote(topicId, ownerId, 1, UserRole.USER);
        topicVoteService.vote(topicId, otherUserId, 1, UserRole.USER);
        topicVoteService.vote(topic2, ownerId, -1, UserRole.USER);

        Map<UUID, VoteStats> stats = topicVoteRepository.getStatsForTopics(java.util.List.of(topicId, topic2));

        assertThat(stats.get(topicId).score()).isEqualTo(2);
        assertThat(stats.get(topic2).score()).isEqualTo(-1);
    }
}
