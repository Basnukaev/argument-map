package ru.basnukaev.argumentmap.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.domain.NodeAuthority;
import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.domain.Stance;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class NodeAuthorityRepositoryIT {

    @Autowired
    private NodeAuthorityRepository nodeAuthorityRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID topicId;
    private UUID nodeId;
    private UUID authorityId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "u-" + userId, userId + "@e.com");
        topicId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO topics (id, title, created_by) VALUES (?, ?, ?)",
                topicId, "T", userId);
        nodeId = insertNode();
        authorityId = insertAuthority("Ибн Таймия");
    }

    @Test
    void save_insertsLink_withStance() {
        NodeAuthority link = new NodeAuthority(nodeId, authorityId, Stance.OPPOSES, Instant.now());

        nodeAuthorityRepository.save(link);

        var found = nodeAuthorityRepository.findByIds(nodeId, authorityId);
        assertThat(found).isPresent();
        assertThat(found.get().stance()).isEqualTo(Stance.OPPOSES);
    }

    @Test
    void findByNodeId_returnsAllAuthoritiesForNode() {
        UUID authority2 = insertAuthority("Ибн Хаджар");
        nodeAuthorityRepository.save(new NodeAuthority(nodeId, authorityId, Stance.HOLDS, Instant.now()));
        nodeAuthorityRepository.save(new NodeAuthority(nodeId, authority2, Stance.NEUTRAL, Instant.now()));

        List<NodeAuthority> links = nodeAuthorityRepository.findByNodeId(nodeId);

        assertThat(links).extracting(NodeAuthority::authorityId)
                .containsExactlyInAnyOrder(authorityId, authority2);
    }

    @Test
    void findByAuthorityId_returnsAllNodesForAuthority() {
        UUID node2 = insertNode();
        nodeAuthorityRepository.save(new NodeAuthority(nodeId, authorityId, Stance.HOLDS, Instant.now()));
        nodeAuthorityRepository.save(new NodeAuthority(node2, authorityId, Stance.OPPOSES, Instant.now()));

        List<NodeAuthority> links = nodeAuthorityRepository.findByAuthorityId(authorityId);

        assertThat(links).hasSize(2);
    }

    @Test
    void delete_removesLink() {
        nodeAuthorityRepository.save(new NodeAuthority(nodeId, authorityId, Stance.HOLDS, Instant.now()));

        boolean deleted = nodeAuthorityRepository.delete(nodeId, authorityId);

        assertThat(deleted).isTrue();
        assertThat(nodeAuthorityRepository.findByIds(nodeId, authorityId)).isEmpty();
    }

    @Test
    void authorityDeletion_cascadesLinks() {
        nodeAuthorityRepository.save(new NodeAuthority(nodeId, authorityId, Stance.HOLDS, Instant.now()));

        jdbcTemplate.update("DELETE FROM authorities WHERE id = ?", authorityId);

        assertThat(nodeAuthorityRepository.findByIds(nodeId, authorityId)).isEmpty();
    }

    private UUID insertNode() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO nodes (id, topic_id, node_type, content, status, "
                        + "created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, topicId, NodeType.CLAIM.name(), "c", NodeStatus.UNVERIFIED.name(), userId, odt(now), odt(now)
        );
        return id;
    }

    private UUID insertAuthority(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO authorities (id, name) VALUES (?, ?)",
                id, name
        );
        return id;
    }
}
