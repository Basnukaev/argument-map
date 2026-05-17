package ru.basnukaev.argumentmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.domain.NodeVote;
import ru.basnukaev.argumentmap.domain.VoteStats;
import ru.basnukaev.argumentmap.exception.InvalidVoteException;
import ru.basnukaev.argumentmap.exception.NodeNotFoundException;
import ru.basnukaev.argumentmap.repository.NodeVoteRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class NodeVoteServiceIT {

    @Autowired
    private NodeVoteService nodeVoteService;

    @Autowired
    private NodeVoteRepository nodeVoteRepository;

    @Autowired
    private NodeService nodeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ownerId;
    private UUID otherUserId;
    private UUID topicId;
    private UUID nodeId;

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
        nodeId = nodeService.createNode(topicId, NodeType.ARGUMENT, "Аргумент", ownerId).id();
    }

    @Test
    void vote_firstTime_creates() {
        NodeVote vote = nodeVoteService.vote(nodeId, ownerId, 1);

        assertThat(vote.weight()).isEqualTo(1);
        assertThat(vote.nodeId()).isEqualTo(nodeId);
        assertThat(vote.userId()).isEqualTo(ownerId);
        assertThat(vote.votedAt()).isNotNull();

        VoteStats stats = nodeVoteRepository.getStatsForNode(nodeId);
        assertThat(stats.upvotes()).isEqualTo(1);
        assertThat(stats.downvotes()).isZero();
        assertThat(stats.score()).isEqualTo(1);
    }

    @Test
    void vote_existing_updates() {
        nodeVoteService.vote(nodeId, ownerId, 1);
        // тот же user меняет голос на downvote - upsert
        NodeVote updated = nodeVoteService.vote(nodeId, ownerId, -1);

        assertThat(updated.weight()).isEqualTo(-1);

        VoteStats stats = nodeVoteRepository.getStatsForNode(nodeId);
        assertThat(stats.upvotes()).isZero();
        assertThat(stats.downvotes()).isEqualTo(1);
        assertThat(stats.score()).isEqualTo(-1);
    }

    @Test
    void vote_invalidWeight_throws() {
        assertThatThrownBy(() -> nodeVoteService.vote(nodeId, ownerId, 0))
                .isInstanceOf(InvalidVoteException.class);
        assertThatThrownBy(() -> nodeVoteService.vote(nodeId, ownerId, 2))
                .isInstanceOf(InvalidVoteException.class);
        assertThatThrownBy(() -> nodeVoteService.vote(nodeId, ownerId, -2))
                .isInstanceOf(InvalidVoteException.class);
    }

    @Test
    void vote_nodeNotFound_throws() {
        assertThatThrownBy(() -> nodeVoteService.vote(UUID.randomUUID(), ownerId, 1))
                .isInstanceOf(NodeNotFoundException.class);
    }

    @Test
    void removeVote_existing_deletes() {
        nodeVoteService.vote(nodeId, ownerId, 1);

        boolean removed = nodeVoteService.removeVote(nodeId, ownerId);

        assertThat(removed).isTrue();
        assertThat(nodeVoteRepository.findByNodeAndUser(nodeId, ownerId)).isEmpty();
    }

    @Test
    void removeVote_notVoted_idempotent() {
        boolean removed = nodeVoteService.removeVote(nodeId, ownerId);

        assertThat(removed).isFalse();
    }

    @Test
    void getStatsForNode_aggregatesMultipleUsers() {
        nodeVoteService.vote(nodeId, ownerId, 1);
        nodeVoteService.vote(nodeId, otherUserId, -1);

        VoteStats stats = nodeVoteService.getStatsForNode(nodeId);

        assertThat(stats.upvotes()).isEqualTo(1);
        assertThat(stats.downvotes()).isEqualTo(1);
        assertThat(stats.score()).isZero();
    }

    @Test
    void getUserVote_returnsCurrent() {
        nodeVoteService.vote(nodeId, ownerId, -1);

        assertThat(nodeVoteService.getUserVote(nodeId, ownerId)).contains(-1);
        assertThat(nodeVoteService.getUserVote(nodeId, otherUserId)).isEmpty();
    }
}
