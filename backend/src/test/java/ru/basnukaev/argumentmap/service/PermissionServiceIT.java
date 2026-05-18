package ru.basnukaev.argumentmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.time.Instant;
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
import ru.basnukaev.argumentmap.domain.Topic;
import ru.basnukaev.argumentmap.domain.TopicMember;
import ru.basnukaev.argumentmap.domain.TopicMemberRole;
import ru.basnukaev.argumentmap.domain.TopicVisibility;
import ru.basnukaev.argumentmap.exception.TopicAccessDeniedException;
import ru.basnukaev.argumentmap.exception.TopicWriteAccessDeniedException;
import ru.basnukaev.argumentmap.repository.TopicMemberRepository;
import ru.basnukaev.argumentmap.repository.TopicRepository;

/**
 * IT для PermissionService через Testcontainers - проверяет vis matrix
 * ADR-043 на реальной БД (UNION-запрос findVisibleToUser, JOIN с
 * topic_members, ADMIN bypass).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PermissionServiceIT {

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private TopicMemberRepository topicMemberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ownerId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        ownerId = insertUser("owner");
        otherUserId = insertUser("other");
    }

    private UUID insertUser(String suffix) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                id, "user-" + id + "-" + suffix, id + "-" + suffix + "@test.com"
        );
        return id;
    }

    private UUID insertTopic(UUID createdBy, String visibility) {
        UUID id = UUID.randomUUID();
        Topic t = new Topic(id, "T", null, null, createdBy, Instant.now(), visibility,
                ru.basnukaev.argumentmap.domain.StatusAlgorithm.MVP);
        topicRepository.save(t);
        return id;
    }

    private void addMember(UUID topicId, UUID userId, String role) {
        TopicMember m = new TopicMember(
                UUID.randomUUID(), topicId, userId, role,
                Instant.now(), ownerId
        );
        topicMemberRepository.save(m);
    }

    // ---- canReadTopic ----

    @Test
    void canReadTopic_PRIVATE_ownerCanRead() {
        UUID topicId = insertTopic(ownerId, TopicVisibility.PRIVATE);
        assertThat(permissionService.canReadTopic(topicId, ownerId, UserRole.USER)).isTrue();
    }

    @Test
    void canReadTopic_PRIVATE_nonOwnerCannotRead() {
        UUID topicId = insertTopic(ownerId, TopicVisibility.PRIVATE);
        assertThat(permissionService.canReadTopic(topicId, otherUserId, UserRole.USER)).isFalse();
    }

    @Test
    void canReadTopic_SHARED_memberCanRead() {
        UUID topicId = insertTopic(ownerId, TopicVisibility.SHARED);
        addMember(topicId, otherUserId, TopicMemberRole.MEMBER);
        assertThat(permissionService.canReadTopic(topicId, otherUserId, UserRole.USER)).isTrue();
    }

    @Test
    void canReadTopic_SHARED_nonMemberCannotRead() {
        UUID topicId = insertTopic(ownerId, TopicVisibility.SHARED);
        // не добавляем otherUserId как member
        assertThat(permissionService.canReadTopic(topicId, otherUserId, UserRole.USER)).isFalse();
    }

    @Test
    void canReadTopic_PUBLIC_anyAuthenticatedCanRead() {
        UUID topicId = insertTopic(ownerId, TopicVisibility.PUBLIC);
        assertThat(permissionService.canReadTopic(topicId, otherUserId, UserRole.USER)).isTrue();
    }

    @Test
    void canReadTopic_ADMIN_bypassAllChecks() {
        UUID topicId = insertTopic(ownerId, TopicVisibility.PRIVATE);
        assertThat(permissionService.canReadTopic(topicId, otherUserId, UserRole.ADMIN)).isTrue();
    }

    // ---- canWriteTopic ----

    @Test
    void canWriteTopic_SHARED_EDITORcanWrite_MEMBERcannot() {
        UUID topicId = insertTopic(ownerId, TopicVisibility.SHARED);
        UUID editorUserId = insertUser("editor");
        UUID memberUserId = insertUser("member");
        addMember(topicId, editorUserId, TopicMemberRole.EDITOR);
        addMember(topicId, memberUserId, TopicMemberRole.MEMBER);

        assertThat(permissionService.canWriteTopic(topicId, editorUserId, UserRole.USER)).isTrue();
        assertThat(permissionService.canWriteTopic(topicId, memberUserId, UserRole.USER)).isFalse();
    }

    @Test
    void canWriteTopic_PUBLIC_nonOwnerCannotWrite_unlessEDITOR() {
        UUID topicId = insertTopic(ownerId, TopicVisibility.PUBLIC);
        UUID editorUserId = insertUser("editor-pub");
        addMember(topicId, editorUserId, TopicMemberRole.EDITOR);

        // обычный (не EDITOR) - read да, write нет
        assertThat(permissionService.canReadTopic(topicId, otherUserId, UserRole.USER)).isTrue();
        assertThat(permissionService.canWriteTopic(topicId, otherUserId, UserRole.USER)).isFalse();

        // EDITOR может write
        assertThat(permissionService.canWriteTopic(topicId, editorUserId, UserRole.USER)).isTrue();
    }

    // ---- asserts ----

    @Test
    void assertCanRead_PRIVATE_nonOwner_throws403() {
        UUID topicId = insertTopic(ownerId, TopicVisibility.PRIVATE);
        assertThatThrownBy(() -> permissionService.assertCanRead(topicId, otherUserId, UserRole.USER))
                .isInstanceOf(TopicAccessDeniedException.class);
    }

    @Test
    void assertCanWrite_SHARED_MEMBER_throwsWriteDenied() {
        UUID topicId = insertTopic(ownerId, TopicVisibility.SHARED);
        addMember(topicId, otherUserId, TopicMemberRole.MEMBER);
        assertThatThrownBy(() -> permissionService.assertCanWrite(topicId, otherUserId, UserRole.USER))
                .isInstanceOf(TopicWriteAccessDeniedException.class);
    }

    // ---- findVisibleToUserWithCounts (UNION в repository) ----

    @Test
    void findVisibleToUserWithCounts_returnsOnlyVisibleTopics() {
        // owner создаёт 3 темы разного visibility, otherUser - 1 свою PRIVATE
        UUID privateTopic = insertTopic(ownerId, TopicVisibility.PRIVATE);
        UUID sharedTopic = insertTopic(ownerId, TopicVisibility.SHARED);
        UUID publicTopic = insertTopic(ownerId, TopicVisibility.PUBLIC);
        UUID otherPrivateTopic = insertTopic(otherUserId, TopicVisibility.PRIVATE);
        // otherUser добавлен как member в SHARED
        addMember(sharedTopic, otherUserId, TopicMemberRole.MEMBER);

        // owner видит свои 3 темы (PRIVATE / SHARED / PUBLIC), но не чужую PRIVATE
        var ownerVisible = topicRepository.findVisibleToUserWithCounts(ownerId);
        assertThat(ownerVisible).extracting(twc -> twc.topic().id())
                .contains(privateTopic, sharedTopic, publicTopic)
                .doesNotContain(otherPrivateTopic);

        // otherUser видит свою PRIVATE + SHARED (как member) + PUBLIC, но не чужую PRIVATE
        var otherVisible = topicRepository.findVisibleToUserWithCounts(otherUserId);
        assertThat(otherVisible).extracting(twc -> twc.topic().id())
                .contains(otherPrivateTopic, sharedTopic, publicTopic)
                .doesNotContain(privateTopic);
    }

    // suppress unused warning for odt import
    @SuppressWarnings("unused")
    private void unusedImportSilencer() {
        odt(Instant.now());
    }
}
