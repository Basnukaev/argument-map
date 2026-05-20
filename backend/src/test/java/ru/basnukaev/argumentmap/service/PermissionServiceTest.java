package ru.basnukaev.argumentmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.domain.Topic;
import ru.basnukaev.argumentmap.domain.TopicMember;
import ru.basnukaev.argumentmap.domain.TopicMemberRole;
import ru.basnukaev.argumentmap.domain.TopicVisibility;
import ru.basnukaev.argumentmap.exception.InsufficientRoleException;
import ru.basnukaev.argumentmap.exception.TopicAccessDeniedException;
import ru.basnukaev.argumentmap.exception.TopicNotFoundException;
import ru.basnukaev.argumentmap.exception.TopicWriteAccessDeniedException;
import ru.basnukaev.argumentmap.repository.TopicMemberRepository;
import ru.basnukaev.argumentmap.repository.TopicRepository;

/**
 * Unit-тесты PermissionService с моками - не поднимаем ApplicationContext
 * (этим занимается PermissionServiceIT). Покрытие visibility-матрицы
 * ADR-043.
 */
@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private TopicMemberRepository topicMemberRepository;

    @InjectMocks
    private PermissionService permissionService;

    private UUID ownerId;
    private UUID otherUserId;
    private UUID topicId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        topicId = UUID.randomUUID();
    }

    private Topic topic(String visibility) {
        return new Topic(topicId, "T", null, null, ownerId, Instant.now(), visibility,
                ru.basnukaev.argumentmap.domain.StatusAlgorithm.MVP);
    }

    private TopicMember member(String role) {
        return new TopicMember(UUID.randomUUID(), topicId, otherUserId, role,
                Instant.now(), ownerId);
    }

    // ---- canReadTopic ----

    @Test
    void canReadTopic_PRIVATE_ownerCanRead() {
        when(topicRepository.findById(topicId)).thenReturn(Optional.of(topic(TopicVisibility.PRIVATE)));
        assertThat(permissionService.canReadTopic(topicId, ownerId, UserRole.USER)).isTrue();
    }

    @Test
    void canReadTopic_PRIVATE_nonOwnerCannotRead() {
        when(topicRepository.findById(topicId)).thenReturn(Optional.of(topic(TopicVisibility.PRIVATE)));
        assertThat(permissionService.canReadTopic(topicId, otherUserId, UserRole.USER)).isFalse();
    }

    @Test
    void canReadTopic_SHARED_memberCanRead() {
        when(topicRepository.findById(topicId)).thenReturn(Optional.of(topic(TopicVisibility.SHARED)));
        when(topicMemberRepository.existsByTopicAndUser(topicId, otherUserId)).thenReturn(true);
        assertThat(permissionService.canReadTopic(topicId, otherUserId, UserRole.USER)).isTrue();
    }

    @Test
    void canReadTopic_SHARED_nonMemberCannotRead() {
        when(topicRepository.findById(topicId)).thenReturn(Optional.of(topic(TopicVisibility.SHARED)));
        when(topicMemberRepository.existsByTopicAndUser(topicId, otherUserId)).thenReturn(false);
        assertThat(permissionService.canReadTopic(topicId, otherUserId, UserRole.USER)).isFalse();
    }

    @Test
    void canReadTopic_SHARED_ownerCanRead() {
        when(topicRepository.findById(topicId)).thenReturn(Optional.of(topic(TopicVisibility.SHARED)));
        // не должен дергать topicMemberRepository - owner exits раньше
        assertThat(permissionService.canReadTopic(topicId, ownerId, UserRole.USER)).isTrue();
    }

    @Test
    void canReadTopic_PUBLIC_anyAuthenticatedCanRead() {
        when(topicRepository.findById(topicId)).thenReturn(Optional.of(topic(TopicVisibility.PUBLIC)));
        assertThat(permissionService.canReadTopic(topicId, otherUserId, UserRole.USER)).isTrue();
    }

    @Test
    void canReadTopic_ADMIN_bypassAllChecks_evenWithoutTopic() {
        // ADMIN не должен даже дергать БД - bypass на role check
        assertThat(permissionService.canReadTopic(topicId, otherUserId, UserRole.ADMIN)).isTrue();
    }

    @Test
    void canReadTopic_topicNotFound_throws() {
        when(topicRepository.findById(topicId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> permissionService.canReadTopic(topicId, ownerId, UserRole.USER))
                .isInstanceOf(TopicNotFoundException.class);
    }

    // ---- canWriteTopic ----

    @Test
    void canWriteTopic_PRIVATE_ownerCanWrite() {
        when(topicRepository.findById(topicId)).thenReturn(Optional.of(topic(TopicVisibility.PRIVATE)));
        assertThat(permissionService.canWriteTopic(topicId, ownerId, UserRole.USER)).isTrue();
    }

    @Test
    void canWriteTopic_PRIVATE_nonOwnerCannotWrite() {
        when(topicRepository.findById(topicId)).thenReturn(Optional.of(topic(TopicVisibility.PRIVATE)));
        assertThat(permissionService.canWriteTopic(topicId, otherUserId, UserRole.USER)).isFalse();
    }

    @Test
    void canWriteTopic_SHARED_EDITORcanWrite() {
        when(topicRepository.findById(topicId)).thenReturn(Optional.of(topic(TopicVisibility.SHARED)));
        when(topicMemberRepository.findByTopicAndUser(topicId, otherUserId))
                .thenReturn(Optional.of(member(TopicMemberRole.EDITOR)));
        assertThat(permissionService.canWriteTopic(topicId, otherUserId, UserRole.USER)).isTrue();
    }

    @Test
    void canWriteTopic_SHARED_MEMBERcannotWrite() {
        when(topicRepository.findById(topicId)).thenReturn(Optional.of(topic(TopicVisibility.SHARED)));
        when(topicMemberRepository.findByTopicAndUser(topicId, otherUserId))
                .thenReturn(Optional.of(member(TopicMemberRole.MEMBER)));
        assertThat(permissionService.canWriteTopic(topicId, otherUserId, UserRole.USER)).isFalse();
    }

    @Test
    void canWriteTopic_PUBLIC_nonMemberCannotWrite() {
        // PUBLIC даёт read всем, но write только owner + EDITOR member
        when(topicRepository.findById(topicId)).thenReturn(Optional.of(topic(TopicVisibility.PUBLIC)));
        when(topicMemberRepository.findByTopicAndUser(topicId, otherUserId)).thenReturn(Optional.empty());
        assertThat(permissionService.canWriteTopic(topicId, otherUserId, UserRole.USER)).isFalse();
    }

    @Test
    void canWriteTopic_ADMIN_bypass() {
        assertThat(permissionService.canWriteTopic(topicId, otherUserId, UserRole.ADMIN)).isTrue();
    }

    // ---- assert variants ----

    @Test
    void assertCanRead_PRIVATE_nonOwner_throwsTopicAccessDenied() {
        when(topicRepository.findById(topicId)).thenReturn(Optional.of(topic(TopicVisibility.PRIVATE)));
        assertThatThrownBy(() -> permissionService.assertCanRead(topicId, otherUserId, UserRole.USER))
                .isInstanceOf(TopicAccessDeniedException.class);
    }

    @Test
    void assertCanWrite_PRIVATE_nonOwner_throwsTopicAccessDenied() {
        // у нас нет read - значит сначала access deny (404-like behaviour)
        when(topicRepository.findById(topicId)).thenReturn(Optional.of(topic(TopicVisibility.PRIVATE)));
        assertThatThrownBy(() -> permissionService.assertCanWrite(topicId, otherUserId, UserRole.USER))
                .isInstanceOf(TopicAccessDeniedException.class);
    }

    @Test
    void assertCanWrite_SHARED_MEMBER_throwsTopicWriteAccessDenied() {
        // read есть (MEMBER), write нет → write deny (а не read deny)
        when(topicRepository.findById(topicId)).thenReturn(Optional.of(topic(TopicVisibility.SHARED)));
        when(topicMemberRepository.existsByTopicAndUser(topicId, otherUserId)).thenReturn(true);
        when(topicMemberRepository.findByTopicAndUser(topicId, otherUserId))
                .thenReturn(Optional.of(member(TopicMemberRole.MEMBER)));
        assertThatThrownBy(() -> permissionService.assertCanWrite(topicId, otherUserId, UserRole.USER))
                .isInstanceOf(TopicWriteAccessDeniedException.class);
    }

    @Test
    void assertIsOwner_nonOwner_throwsTopicWriteAccessDenied() {
        when(topicRepository.findById(topicId)).thenReturn(Optional.of(topic(TopicVisibility.SHARED)));
        assertThatThrownBy(() -> permissionService.assertIsOwner(topicId, otherUserId, UserRole.USER))
                .isInstanceOf(TopicWriteAccessDeniedException.class);
    }

    @Test
    void assertIsOwner_owner_doesNotThrow() {
        when(topicRepository.findById(topicId)).thenReturn(Optional.of(topic(TopicVisibility.PRIVATE)));
        permissionService.assertIsOwner(topicId, ownerId, UserRole.USER);
    }

    @Test
    void assertIsOwner_ADMIN_bypass() {
        permissionService.assertIsOwner(topicId, otherUserId, UserRole.ADMIN);
    }

    // ─── assertHasRoleAtLeast (Vision 49d Section 2.4) ──────────────

    @Test
    void assertHasRoleAtLeast_admin_canDoAllRoles() {
        permissionService.assertHasRoleAtLeast(ownerId, UserRole.ADMIN, UserRole.USER);
        permissionService.assertHasRoleAtLeast(ownerId, UserRole.ADMIN, UserRole.STUDENT);
        permissionService.assertHasRoleAtLeast(ownerId, UserRole.ADMIN, UserRole.SCHOLAR);
        permissionService.assertHasRoleAtLeast(ownerId, UserRole.ADMIN, UserRole.ADMIN);
    }

    @Test
    void assertHasRoleAtLeast_user_cannotDoStudentActions() {
        assertThatThrownBy(() -> permissionService.assertHasRoleAtLeast(
                ownerId, UserRole.USER, UserRole.STUDENT))
                .isInstanceOf(InsufficientRoleException.class)
                .hasMessageContaining("USER")
                .hasMessageContaining("STUDENT");
    }

    @Test
    void assertHasRoleAtLeast_student_cannotDoScholarActions() {
        assertThatThrownBy(() -> permissionService.assertHasRoleAtLeast(
                ownerId, UserRole.STUDENT, UserRole.SCHOLAR))
                .isInstanceOf(InsufficientRoleException.class)
                .hasMessageContaining("STUDENT")
                .hasMessageContaining("SCHOLAR");
    }

    @Test
    void assertHasRoleAtLeast_scholar_canDoStudentActions() {
        permissionService.assertHasRoleAtLeast(ownerId, UserRole.SCHOLAR, UserRole.STUDENT);
        permissionService.assertHasRoleAtLeast(ownerId, UserRole.SCHOLAR, UserRole.USER);
    }

    @Test
    void assertHasRoleAtLeast_nullActual_throws() {
        assertThatThrownBy(() -> permissionService.assertHasRoleAtLeast(
                ownerId, null, UserRole.USER))
                .isInstanceOf(InsufficientRoleException.class);
    }
}
