package ru.basnukaev.argumentmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.domain.AuditAction;
import ru.basnukaev.argumentmap.domain.AuditEntityType;
import ru.basnukaev.argumentmap.domain.AuditLog;
import ru.basnukaev.argumentmap.domain.Topic;
import ru.basnukaev.argumentmap.domain.TopicMember;
import ru.basnukaev.argumentmap.domain.TopicMemberRole;
import ru.basnukaev.argumentmap.domain.TopicVisibility;
import ru.basnukaev.argumentmap.exception.TopicAccessDeniedException;
import ru.basnukaev.argumentmap.exception.TopicNotFoundException;
import ru.basnukaev.argumentmap.exception.TopicWriteAccessDeniedException;
import ru.basnukaev.argumentmap.repository.AuditLogRepository;
import ru.basnukaev.argumentmap.repository.TopicMemberRepository;
import ru.basnukaev.argumentmap.repository.TopicRepository;

/**
 * IT для {@link TopicService#updateTopic(UUID, String, String, UUID, String)} -
 * partial update title/description темы (backlog tech debt #10).
 *
 * <p>Покрывает: happy path (full / partial), PATCH-семантика для null,
 * permission deny (PRIVATE non-owner, PUBLIC viewer), validation 404,
 * audit log запись с правильным FieldDiff
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class TopicServiceUpdateIT {

    @Autowired
    private TopicService topicService;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private TopicMemberRepository topicMemberRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ownerId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        ownerId = insertUser("owner");
        otherUserId = insertUser("other");
    }

    @Test
    void updateTopic_byOwner_updatesBothFields() {
        UUID topicId = createTopic("Старое название", "Старое описание", TopicVisibility.PRIVATE);

        Topic updated = topicService.updateTopic(
                topicId, "Новое название", "Новое описание", ownerId, UserRole.USER);

        assertThat(updated.title()).isEqualTo("Новое название");
        assertThat(updated.description()).isEqualTo("Новое описание");
    }

    @Test
    void updateTopic_titleOnly_keepsDescription() {
        UUID topicId = createTopic("Старое", "Описание сохраняется", TopicVisibility.PRIVATE);

        Topic updated = topicService.updateTopic(
                topicId, "Новое", null, ownerId, UserRole.USER);

        assertThat(updated.title()).isEqualTo("Новое");
        assertThat(updated.description()).isEqualTo("Описание сохраняется");
    }

    @Test
    void updateTopic_descriptionOnly_keepsTitle() {
        UUID topicId = createTopic("Название сохраняется", "Старое описание",
                TopicVisibility.PRIVATE);

        Topic updated = topicService.updateTopic(
                topicId, null, "Новое описание", ownerId, UserRole.USER);

        assertThat(updated.title()).isEqualTo("Название сохраняется");
        assertThat(updated.description()).isEqualTo("Новое описание");
    }

    @Test
    void updateTopic_emptyDescription_clearsToEmptyString() {
        UUID topicId = createTopic("T", "Было описание", TopicVisibility.PRIVATE);

        Topic updated = topicService.updateTopic(
                topicId, null, "", ownerId, UserRole.USER);

        // PATCH-семантика: "" = заменить на пустую строку (не null). Если
        // нужно явно вернуть NULL в БД - frontend пока такой опции не даёт
        assertThat(updated.description()).isEqualTo("");
    }

    @Test
    void updateTopic_noChanges_isNoOpAndNoAudit() {
        UUID topicId = createTopic("Same", "Same desc", TopicVisibility.PRIVATE);
        long auditBefore = auditLogRepository.countByEntity(AuditEntityType.TOPIC, topicId);

        Topic updated = topicService.updateTopic(
                topicId, "Same", "Same desc", ownerId, UserRole.USER);

        assertThat(updated.title()).isEqualTo("Same");
        long auditAfter = auditLogRepository.countByEntity(AuditEntityType.TOPIC, topicId);
        // no-op: ни одна запись audit не появилась
        assertThat(auditAfter).isEqualTo(auditBefore);
    }

    @Test
    void updateTopic_writesAuditWithFieldDiff() {
        UUID topicId = createTopic("Old title", "Old desc", TopicVisibility.PRIVATE);

        topicService.updateTopic(topicId, "New title", "New desc", ownerId, UserRole.USER);

        List<AuditLog> entries = auditLogRepository.findByEntityPage(
                AuditEntityType.TOPIC, topicId, 50, 0);
        AuditLog updateRow = entries.stream()
                .filter(e -> AuditAction.UPDATE.equals(e.action()))
                .findFirst()
                .orElseThrow();
        assertThat(updateRow.actorUserId()).isEqualTo(ownerId);
        // Диф содержит old и new для каждого поля - sanity check на JSON
        String changes = updateRow.changes();
        assertThat(changes).contains("title").contains("Old title").contains("New title");
        assertThat(changes).contains("description").contains("Old desc").contains("New desc");
    }

    @Test
    void updateTopic_auditOnlyForChangedFields() {
        UUID topicId = createTopic("Keep title", "Old desc", TopicVisibility.PRIVATE);

        topicService.updateTopic(topicId, null, "New desc", ownerId, UserRole.USER);

        List<AuditLog> entries = auditLogRepository.findByEntityPage(
                AuditEntityType.TOPIC, topicId, 50, 0);
        AuditLog updateRow = entries.stream()
                .filter(e -> AuditAction.UPDATE.equals(e.action()))
                .findFirst()
                .orElseThrow();
        // FieldDiff only по description - title не должен быть в changes
        assertThat(updateRow.changes()).contains("description").contains("New desc");
        // Title в changes отсутствует (no-change поле)
        assertThat(updateRow.changes()).doesNotContain("\"title\"");
    }

    @Test
    void updateTopic_byNonMember_onPrivate_throwsAccessDenied() {
        UUID topicId = createTopic("T", "D", TopicVisibility.PRIVATE);

        // не-owner non-member на PRIVATE - сначала read deny (топик невидим)
        assertThatThrownBy(() -> topicService.updateTopic(
                topicId, "X", null, otherUserId, UserRole.USER))
                .isInstanceOf(TopicAccessDeniedException.class);
    }

    @Test
    void updateTopic_byNonMember_onPublic_throwsWriteDenied() {
        UUID topicId = createTopic("T", "D", TopicVisibility.PUBLIC);

        // PUBLIC viewer без EDITOR membership - read ok, write deny
        assertThatThrownBy(() -> topicService.updateTopic(
                topicId, "X", null, otherUserId, UserRole.USER))
                .isInstanceOf(TopicWriteAccessDeniedException.class);
    }

    @Test
    void updateTopic_byEditorMember_onShared_succeeds() {
        UUID topicId = createTopic("T", "D", TopicVisibility.SHARED);
        addMember(topicId, otherUserId, TopicMemberRole.EDITOR);

        Topic updated = topicService.updateTopic(
                topicId, "By editor", null, otherUserId, UserRole.USER);

        assertThat(updated.title()).isEqualTo("By editor");
    }

    @Test
    void updateTopic_byPlainMember_onShared_throwsWriteDenied() {
        UUID topicId = createTopic("T", "D", TopicVisibility.SHARED);
        addMember(topicId, otherUserId, TopicMemberRole.MEMBER);

        // MEMBER (без EDITOR) - read ok, write deny (ADR-043 матрица)
        assertThatThrownBy(() -> topicService.updateTopic(
                topicId, "X", null, otherUserId, UserRole.USER))
                .isInstanceOf(TopicWriteAccessDeniedException.class);
    }

    @Test
    void updateTopic_byAdmin_bypassesPermissionCheck() {
        UUID topicId = createTopic("T", "D", TopicVisibility.PRIVATE);

        Topic updated = topicService.updateTopic(
                topicId, "By admin", null, otherUserId, UserRole.ADMIN);

        assertThat(updated.title()).isEqualTo("By admin");
    }

    @Test
    void updateTopic_whenTopicMissing_throwsTopicNotFound() {
        UUID missing = UUID.randomUUID();

        assertThatThrownBy(() -> topicService.updateTopic(
                missing, "X", null, ownerId, UserRole.USER))
                .isInstanceOf(TopicNotFoundException.class);
    }

    // ---- helpers ----

    private UUID insertUser(String suffix) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                id, "user-" + id + "-" + suffix, id + "-" + suffix + "@test.com"
        );
        return id;
    }

    private UUID createTopic(String title, String description, String visibility) {
        UUID topicId = UUID.randomUUID();
        topicRepository.save(new Topic(
                topicId, title, description, null, ownerId, Instant.now(),
                visibility,
                ru.basnukaev.argumentmap.domain.StatusAlgorithm.MVP
        ));
        return topicId;
    }

    private void addMember(UUID topicId, UUID userId, String role) {
        topicMemberRepository.save(new TopicMember(
                UUID.randomUUID(), topicId, userId, role, Instant.now(), ownerId
        ));
    }
}
