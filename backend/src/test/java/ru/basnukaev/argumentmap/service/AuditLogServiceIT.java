package ru.basnukaev.argumentmap.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
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
import ru.basnukaev.argumentmap.domain.AuditAction;
import ru.basnukaev.argumentmap.domain.AuditEntityType;
import ru.basnukaev.argumentmap.domain.AuditLog;

/**
 * IT для {@link AuditLogService} (Этап 22.d, ADR-043 Amendment 3).
 * Проверяет: JSON-сериализация {@code changes} в jsonb, фильтрация
 * по entity / parent / actor, sort created_at DESC.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AuditLogServiceIT {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID actorId;

    @BeforeEach
    void setUp() {
        actorId = insertUser("actor");
    }

    private UUID insertUser(String suffix) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                id, "user-" + id + "-" + suffix, id + "-" + suffix + "@test.com"
        );
        return id;
    }

    @Test
    void logCreate_persistsRow_withSnapshot() {
        UUID topicId = UUID.randomUUID();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", "Тестовая тема");
        snapshot.put("visibility", "PUBLIC");

        AuditLog saved = auditLogService.logCreate(AuditEntityType.TOPIC, topicId,
                null, null, actorId, snapshot);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.action()).isEqualTo(AuditAction.CREATE);
        assertThat(saved.changes()).contains("created").contains("Тестовая тема");

        List<AuditLog> found = auditLogService.findByEntityPage(
                AuditEntityType.TOPIC, topicId, 10, 0);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).entityId()).isEqualTo(topicId);
        assertThat(found.get(0).actorUserId()).isEqualTo(actorId);
    }

    @Test
    void logUpdate_capturesFieldChanges() {
        UUID nodeId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        Map<String, AuditLogService.FieldDiff> diff = new LinkedHashMap<>();
        diff.put("content", new AuditLogService.FieldDiff("старый", "новый"));

        auditLogService.logUpdate(AuditEntityType.NODE, nodeId,
                AuditEntityType.TOPIC, topicId, actorId, diff);

        List<AuditLog> found = auditLogService.findByEntityPage(
                AuditEntityType.NODE, nodeId, 10, 0);
        assertThat(found).hasSize(1);
        AuditLog row = found.get(0);
        assertThat(row.action()).isEqualTo(AuditAction.UPDATE);
        assertThat(row.parentEntityType()).isEqualTo(AuditEntityType.TOPIC);
        assertThat(row.parentEntityId()).isEqualTo(topicId);
        assertThat(row.changes()).contains("content").contains("старый").contains("новый");
    }

    @Test
    void logDelete_capturesBeforeSnapshot() {
        UUID edgeId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("edgeType", "SUPPORTS");

        auditLogService.logDelete(AuditEntityType.EDGE, edgeId,
                AuditEntityType.TOPIC, topicId, actorId, snapshot);

        List<AuditLog> found = auditLogService.findByEntityPage(
                AuditEntityType.EDGE, edgeId, 10, 0);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).action()).isEqualTo(AuditAction.DELETE);
        assertThat(found.get(0).changes()).contains("deleted").contains("SUPPORTS");
    }

    @Test
    void findByEntity_returnsLogsInChronologicalOrderDesc() throws InterruptedException {
        UUID entityId = UUID.randomUUID();
        auditLogService.logCreate(AuditEntityType.TOPIC, entityId, null, null,
                actorId, Map.of("v", 1));
        // подождать чтобы created_at был гарантированно позже у 2-го row
        Thread.sleep(20);
        auditLogService.logUpdate(AuditEntityType.TOPIC, entityId, null, null,
                actorId, Map.of("title",
                        new AuditLogService.FieldDiff("old", "new")));

        List<AuditLog> found = auditLogService.findByEntityPage(
                AuditEntityType.TOPIC, entityId, 10, 0);
        assertThat(found).hasSize(2);
        // DESC - сначала UPDATE, потом CREATE
        assertThat(found.get(0).action()).isEqualTo(AuditAction.UPDATE);
        assertThat(found.get(1).action()).isEqualTo(AuditAction.CREATE);
    }

    @Test
    void findByParent_returnsChildAndSelfLogs() {
        UUID topicId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        UUID edgeId = UUID.randomUUID();
        UUID otherTopicId = UUID.randomUUID();

        // тема (сам родитель)
        auditLogService.logCreate(AuditEntityType.TOPIC, topicId, null, null,
                actorId, Map.of("title", "T"));
        // child node + edge у нашей темы
        auditLogService.logCreate(AuditEntityType.NODE, nodeId,
                AuditEntityType.TOPIC, topicId, actorId, Map.of("content", "C"));
        auditLogService.logCreate(AuditEntityType.EDGE, edgeId,
                AuditEntityType.TOPIC, topicId, actorId, Map.of("rationale", "R"));
        // child node ДРУГОЙ темы - не должен попасть
        auditLogService.logCreate(AuditEntityType.NODE, UUID.randomUUID(),
                AuditEntityType.TOPIC, otherTopicId, actorId, Map.of("content", "X"));

        List<AuditLog> found = auditLogService.findByParentOrSelfPage(
                AuditEntityType.TOPIC, topicId, 50, 0);
        assertThat(found).hasSize(3);
        assertThat(found).extracting(AuditLog::entityType)
                .containsExactlyInAnyOrder(
                        AuditEntityType.TOPIC,
                        AuditEntityType.NODE,
                        AuditEntityType.EDGE
                );
    }

    @Test
    void findByActor_returnsOnlyThatUsersActions() {
        UUID anotherActor = insertUser("other-actor");
        auditLogService.logCreate(AuditEntityType.TOPIC, UUID.randomUUID(),
                null, null, actorId, Map.of("a", 1));
        auditLogService.logCreate(AuditEntityType.TOPIC, UUID.randomUUID(),
                null, null, actorId, Map.of("b", 2));
        auditLogService.logCreate(AuditEntityType.TOPIC, UUID.randomUUID(),
                null, null, anotherActor, Map.of("c", 3));

        List<AuditLog> mine = auditLogService.findByActorPage(actorId, 50, 0);
        assertThat(mine).hasSize(2);
        assertThat(mine).extracting(AuditLog::actorUserId)
                .containsOnly(actorId);

        assertThat(auditLogService.countByActor(actorId)).isEqualTo(2L);
        assertThat(auditLogService.countByActor(anotherActor)).isEqualTo(1L);
    }

    @Test
    void logVisibilityChange_capturesOldAndNew() {
        UUID topicId = UUID.randomUUID();
        auditLogService.logVisibilityChange(AuditEntityType.TOPIC, topicId,
                actorId, "PRIVATE", "PUBLIC");

        List<AuditLog> found = auditLogService.findByEntityPage(
                AuditEntityType.TOPIC, topicId, 10, 0);
        assertThat(found).hasSize(1);
        AuditLog row = found.get(0);
        assertThat(row.action()).isEqualTo(AuditAction.VISIBILITY_CHANGE);
        assertThat(row.changes()).contains("PRIVATE").contains("PUBLIC");
    }

    @Test
    void logMemberAdd_capturesUserAndRole() {
        UUID memberId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID memberUserId = UUID.randomUUID();

        auditLogService.logMemberAdd(AuditEntityType.TOPIC_MEMBER, memberId,
                AuditEntityType.TOPIC, topicId, actorId, memberUserId, "EDITOR");

        List<AuditLog> found = auditLogService.findByEntityPage(
                AuditEntityType.TOPIC_MEMBER, memberId, 10, 0);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).action()).isEqualTo(AuditAction.MEMBER_ADD);
        assertThat(found.get(0).changes()).contains("EDITOR").contains(memberUserId.toString());
    }
}
