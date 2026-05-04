package ru.basnukaev.argumentmap.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.domain.Revision;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RevisionRepositoryIT {

    @Autowired
    private RevisionRepository revisionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID topicId;
    private UUID nodeId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "u-" + userId, userId + "@e.com");
        topicId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO topics (id, title, created_by) VALUES (?, ?, ?)",
                topicId, "T", userId);
        nodeId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO nodes (id, topic_id, node_type, content, status, "
                        + "created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                nodeId, topicId, NodeType.CLAIM.name(), "initial", NodeStatus.UNVERIFIED.name(), userId, odt(now), odt(now)
        );
    }

    @Test
    void save_insertsRevision_findByIdReturnsSame() {
        Instant when = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Revision revision = new Revision(
                UUID.randomUUID(), nodeId,
                "старый текст", "новый текст",
                userId, when
        );

        revisionRepository.save(revision);

        var found = revisionRepository.findById(revision.id());
        assertThat(found).isPresent();
        Revision reloaded = found.get();
        assertThat(reloaded.nodeId()).isEqualTo(nodeId);
        assertThat(reloaded.contentBefore()).isEqualTo("старый текст");
        assertThat(reloaded.contentAfter()).isEqualTo("новый текст");
        assertThat(reloaded.changedBy()).isEqualTo(userId);
        assertThat(reloaded.changedAt()).isEqualTo(when);
    }

    @Test
    void save_withNullContentBefore_worksFine() {
        Revision firstRevision = new Revision(
                UUID.randomUUID(), nodeId, null, "первая версия", userId, Instant.now()
        );

        revisionRepository.save(firstRevision);

        Revision reloaded = revisionRepository.findById(firstRevision.id()).orElseThrow();
        assertThat(reloaded.contentBefore()).isNull();
        assertThat(reloaded.contentAfter()).isEqualTo("первая версия");
    }

    @Test
    void findByNodeId_returnsHistoryInChronologicalOrder() {
        Instant base = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Revision r1 = new Revision(UUID.randomUUID(), nodeId, null, "v1", userId, base.minusSeconds(120));
        Revision r2 = new Revision(UUID.randomUUID(), nodeId, "v1", "v2", userId, base.minusSeconds(60));
        Revision r3 = new Revision(UUID.randomUUID(), nodeId, "v2", "v3", userId, base);
        revisionRepository.save(r3);
        revisionRepository.save(r1);
        revisionRepository.save(r2);

        List<Revision> history = revisionRepository.findByNodeId(nodeId);

        assertThat(history).extracting(Revision::id).containsExactly(r1.id(), r2.id(), r3.id());
    }

    @Test
    void nodeDeletion_cascadesRevisions() {
        Revision r = new Revision(UUID.randomUUID(), nodeId, null, "content", userId, Instant.now());
        revisionRepository.save(r);

        jdbcTemplate.update("DELETE FROM nodes WHERE id = ?", nodeId);

        assertThat(revisionRepository.findById(r.id())).isEmpty();
    }
}
