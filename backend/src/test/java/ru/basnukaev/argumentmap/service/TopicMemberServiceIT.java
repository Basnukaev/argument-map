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
import ru.basnukaev.argumentmap.domain.StatusAlgorithm;
import ru.basnukaev.argumentmap.domain.Topic;
import ru.basnukaev.argumentmap.domain.TopicMember;
import ru.basnukaev.argumentmap.domain.TopicMemberRole;
import ru.basnukaev.argumentmap.domain.TopicVisibility;
import ru.basnukaev.argumentmap.exception.TopicAccessDeniedException;
import ru.basnukaev.argumentmap.exception.TopicMemberNotFoundException;
import ru.basnukaev.argumentmap.exception.TopicNotFoundException;
import ru.basnukaev.argumentmap.exception.TopicWriteAccessDeniedException;
import ru.basnukaev.argumentmap.repository.TopicMemberRepository;
import ru.basnukaev.argumentmap.repository.TopicRepository;

/**
 * IT для {@link TopicMemberService} - покрывает privilege escalation guards.
 * Доступ к add/update/remove только у owner (или ADMIN). MEMBER может
 * удалить только себя (self-leave). EDITOR ≠ owner - не может управлять
 * членами. До audit было покрытие только через TopicMemberControllerIT;
 * здесь явные service-level edge cases.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class TopicMemberServiceIT {

    @Autowired
    private TopicMemberService topicMemberService;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private TopicMemberRepository topicMemberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ownerId;
    private UUID memberId;
    private UUID strangerId;
    private UUID topicId;

    @BeforeEach
    void setUp() {
        ownerId = insertUser("owner");
        memberId = insertUser("member");
        strangerId = insertUser("stranger");
        topicId = insertTopic(ownerId, TopicVisibility.SHARED);
    }

    private UUID insertUser(String suffix) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                id, "u-" + id + "-" + suffix, id + "-" + suffix + "@test.com"
        );
        return id;
    }

    private UUID insertTopic(UUID owner, String visibility) {
        UUID id = UUID.randomUUID();
        topicRepository.save(new Topic(
                id, "T", null, null, owner, Instant.now(), visibility,
                StatusAlgorithm.MVP
        ));
        return id;
    }

    @Test
    void addMember_ownerSuccess() {
        TopicMember m = topicMemberService.addMember(
                topicId, memberId, TopicMemberRole.MEMBER, ownerId, UserRole.USER
        );
        assertThat(m.id()).isNotNull();
        assertThat(m.userId()).isEqualTo(memberId);
        assertThat(m.role()).isEqualTo(TopicMemberRole.MEMBER);
    }

    @Test
    void addMember_invalidRole_throwsIllegalArgument() {
        assertThatThrownBy(() -> topicMemberService.addMember(
                topicId, memberId, "WRONG_ROLE", ownerId, UserRole.USER
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addMember_topicMissing_throwsNotFoundBeforeAccessCheck() {
        // Проверяем порядок - 404 (not found) приоритет над 403 (access denied),
        // не leak'аем существование темы через access denied
        UUID missingTopic = UUID.randomUUID();
        assertThatThrownBy(() -> topicMemberService.addMember(
                missingTopic, memberId, TopicMemberRole.MEMBER, strangerId, UserRole.USER
        )).isInstanceOf(TopicNotFoundException.class);
    }

    @Test
    void addMember_nonOwner_throwsWriteAccessDenied() {
        // Privilege escalation guard - не-owner не может добавлять других.
        // PermissionService.assertIsOwner бросает TopicWriteAccessDeniedException
        // (а не TopicAccessDeniedException) - добавление member классифицируется
        // как write operation, а не как denial читать вообще
        assertThatThrownBy(() -> topicMemberService.addMember(
                topicId, memberId, TopicMemberRole.MEMBER, strangerId, UserRole.USER
        )).isInstanceOf(TopicWriteAccessDeniedException.class);
    }

    @Test
    void addMember_ownerAsMember_rejected() {
        // Бессмысленно - owner уже имеет full access. Service ловит явно
        assertThatThrownBy(() -> topicMemberService.addMember(
                topicId, ownerId, TopicMemberRole.MEMBER, ownerId, UserRole.USER
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addMember_duplicate_throwsIllegalArgument() {
        topicMemberService.addMember(topicId, memberId, TopicMemberRole.MEMBER, ownerId, UserRole.USER);
        // DuplicateKeyException → IllegalArgumentException (UNIQUE constraint)
        assertThatThrownBy(() -> topicMemberService.addMember(
                topicId, memberId, TopicMemberRole.EDITOR, ownerId, UserRole.USER
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addMember_adminBypassesOwnerCheck() {
        // ADMIN bypass - не-owner с ADMIN role'ом может добавлять
        TopicMember m = topicMemberService.addMember(
                topicId, memberId, TopicMemberRole.EDITOR, strangerId, UserRole.ADMIN
        );
        assertThat(m.userId()).isEqualTo(memberId);
    }

    @Test
    void removeMember_selfLeave_allowed() {
        TopicMember added = topicMemberService.addMember(
                topicId, memberId, TopicMemberRole.MEMBER, ownerId, UserRole.USER
        );
        // Member может удалить сам себя
        topicMemberService.removeMember(topicId, added.id(), memberId, UserRole.USER);
        assertThat(topicMemberRepository.findById(added.id())).isEmpty();
    }

    @Test
    void removeMember_strangerCannotRemoveOthers() {
        TopicMember added = topicMemberService.addMember(
                topicId, memberId, TopicMemberRole.MEMBER, ownerId, UserRole.USER
        );
        assertThatThrownBy(() -> topicMemberService.removeMember(
                topicId, added.id(), strangerId, UserRole.USER
        )).isInstanceOf(TopicWriteAccessDeniedException.class);
    }

    @Test
    void removeMember_missingId_throwsNotFound() {
        assertThatThrownBy(() -> topicMemberService.removeMember(
                topicId, UUID.randomUUID(), ownerId, UserRole.USER
        )).isInstanceOf(TopicMemberNotFoundException.class);
    }

    @Test
    void removeMember_wrongTopicForMember_throwsNotFound() {
        // Member принадлежит topicId, пытаемся удалить через другой topic - 404
        UUID otherTopicId = insertTopic(ownerId, TopicVisibility.PRIVATE);
        TopicMember added = topicMemberService.addMember(
                topicId, memberId, TopicMemberRole.MEMBER, ownerId, UserRole.USER
        );
        assertThatThrownBy(() -> topicMemberService.removeMember(
                otherTopicId, added.id(), ownerId, UserRole.USER
        )).isInstanceOf(TopicMemberNotFoundException.class);
    }

    @Test
    void updateMemberRole_ownerChangesMemberToEditor() {
        TopicMember added = topicMemberService.addMember(
                topicId, memberId, TopicMemberRole.MEMBER, ownerId, UserRole.USER
        );
        TopicMember updated = topicMemberService.updateMemberRole(
                topicId, added.id(), TopicMemberRole.EDITOR, ownerId, UserRole.USER
        );
        assertThat(updated.role()).isEqualTo(TopicMemberRole.EDITOR);
    }

    @Test
    void updateMemberRole_memberCannotPromoteSelf() {
        // Privilege escalation guard - member не может сам себя promotnut.
        // assertIsOwner бросает TopicWriteAccessDeniedException для не-owner
        // (см. addMember_nonOwner_throwsWriteAccessDenied)
        TopicMember added = topicMemberService.addMember(
                topicId, memberId, TopicMemberRole.MEMBER, ownerId, UserRole.USER
        );
        assertThatThrownBy(() -> topicMemberService.updateMemberRole(
                topicId, added.id(), TopicMemberRole.EDITOR, memberId, UserRole.USER
        )).isInstanceOf(TopicWriteAccessDeniedException.class);
    }

    @Test
    void updateMemberRole_invalidRole_throwsIllegalArgument() {
        TopicMember added = topicMemberService.addMember(
                topicId, memberId, TopicMemberRole.MEMBER, ownerId, UserRole.USER
        );
        assertThatThrownBy(() -> topicMemberService.updateMemberRole(
                topicId, added.id(), "INVALID", ownerId, UserRole.USER
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listMembers_strangerCannotRead() {
        topicMemberService.addMember(topicId, memberId, TopicMemberRole.MEMBER, ownerId, UserRole.USER);
        // SHARED тема - stranger не member и не owner → 403
        assertThatThrownBy(() -> topicMemberService.listMembers(topicId, strangerId, UserRole.USER))
                .isInstanceOf(TopicAccessDeniedException.class);
    }

    @Test
    void listMembers_memberCanRead() {
        topicMemberService.addMember(topicId, memberId, TopicMemberRole.MEMBER, ownerId, UserRole.USER);
        List<TopicMember> members = topicMemberService.listMembers(topicId, memberId, UserRole.USER);
        assertThat(members).hasSize(1);
        assertThat(members.get(0).userId()).isEqualTo(memberId);
    }
}
