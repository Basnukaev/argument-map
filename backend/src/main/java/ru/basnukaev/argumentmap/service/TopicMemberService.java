package ru.basnukaev.argumentmap.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.domain.TopicMember;
import ru.basnukaev.argumentmap.domain.TopicMemberRole;
import ru.basnukaev.argumentmap.exception.TopicAccessDeniedException;
import ru.basnukaev.argumentmap.exception.TopicMemberNotFoundException;
import ru.basnukaev.argumentmap.exception.TopicNotFoundException;
import ru.basnukaev.argumentmap.exception.TopicWriteAccessDeniedException;
import ru.basnukaev.argumentmap.repository.TopicMemberRepository;
import ru.basnukaev.argumentmap.repository.TopicRepository;

/**
 * Управление членами SHARED-тем (ADR-043).
 *
 * <p>Membership API доступен только для owner темы (или ADMIN). EDITOR
 * не может добавлять/удалять других членов - это privilege escalation
 * (EDITOR сделал бы себя owner-equivalent). MEMBER не может управлять
 * никем кроме самого себя (delete self).
 */
@Service
public class TopicMemberService {

    private final TopicMemberRepository topicMemberRepository;
    private final TopicRepository topicRepository;
    private final PermissionService permissionService;

    public TopicMemberService(TopicMemberRepository topicMemberRepository,
                              TopicRepository topicRepository,
                              PermissionService permissionService) {
        this.topicMemberRepository = topicMemberRepository;
        this.topicRepository = topicRepository;
        this.permissionService = permissionService;
    }

    /**
     * Добавляет user в тему как MEMBER либо EDITOR. Только owner может
     * добавлять. UNIQUE constraint в БД ловит дубли - бросаем
     * IllegalArgumentException, в Problem Details маппится через
     * data-integrity или отдельный handler.
     */
    @Transactional
    public TopicMember addMember(UUID topicId, UUID newMemberUserId, String role,
                                 UUID actorUserId, String actorRole) {
        if (!TopicMemberRole.isValid(role)) {
            throw new IllegalArgumentException(
                    "Невалидная роль: " + role + " (ожидается MEMBER/EDITOR)"
            );
        }
        // Проверка существования темы перед permission check - чтобы 404 а не 403
        topicRepository.findById(topicId)
                .orElseThrow(() -> new TopicNotFoundException(topicId));
        permissionService.assertIsOwner(topicId, actorUserId, actorRole);

        // owner не может быть добавлен как member - бессмысленно (он уже owner)
        topicRepository.findById(topicId).ifPresent(t -> {
            if (t.createdBy().equals(newMemberUserId)) {
                throw new IllegalArgumentException(
                        "Owner темы не может быть добавлен как member"
                );
            }
        });

        TopicMember member = new TopicMember(
                UUID.randomUUID(), topicId, newMemberUserId,
                role, Instant.now(), actorUserId
        );
        try {
            return topicMemberRepository.save(member);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException(
                    "Пользователь " + newMemberUserId + " уже является членом темы " + topicId
            );
        }
    }

    @Transactional(readOnly = true)
    public List<TopicMember> listMembers(UUID topicId, UUID actorUserId, String actorRole) {
        topicRepository.findById(topicId)
                .orElseThrow(() -> new TopicNotFoundException(topicId));
        // Видеть список членов могут все кто видит тему - не leak'аем
        // private member list. owner и members видят, posторонние - 403
        permissionService.assertCanRead(topicId, actorUserId, actorRole);
        return topicMemberRepository.findByTopicId(topicId);
    }

    /**
     * Удаляет член темы. Owner всегда может удалять, member может
     * удалить только себя (self-leave). EDITOR ≠ owner поэтому
     * EDITOR не может удалить другого EDITOR'а.
     */
    @Transactional
    public void removeMember(UUID topicId, UUID memberId,
                             UUID actorUserId, String actorRole) {
        TopicMember member = topicMemberRepository.findById(memberId)
                .orElseThrow(() -> new TopicMemberNotFoundException(memberId));
        if (!member.topicId().equals(topicId)) {
            // membership относится к другой теме - 404 (не leak'аем структуру)
            throw new TopicMemberNotFoundException(memberId);
        }

        // Owner / ADMIN - всегда можно
        // member.userId == actorUserId - self-leave допустим
        boolean isSelfLeave = member.userId().equals(actorUserId);
        boolean isOwnerOrAdmin = permissionService.isOwner(topicId, actorUserId, actorRole);
        if (!isSelfLeave && !isOwnerOrAdmin) {
            throw new TopicWriteAccessDeniedException(topicId, actorUserId);
        }

        topicMemberRepository.delete(memberId);
    }

    /**
     * Меняет роль члена темы - только owner (или ADMIN). Member не может
     * сам себя promotnut'ь до EDITOR.
     */
    @Transactional
    public TopicMember updateMemberRole(UUID topicId, UUID memberId, String newRole,
                                        UUID actorUserId, String actorRole) {
        if (!TopicMemberRole.isValid(newRole)) {
            throw new IllegalArgumentException(
                    "Невалидная роль: " + newRole + " (ожидается MEMBER/EDITOR)"
            );
        }
        TopicMember existing = topicMemberRepository.findById(memberId)
                .orElseThrow(() -> new TopicMemberNotFoundException(memberId));
        if (!existing.topicId().equals(topicId)) {
            throw new TopicMemberNotFoundException(memberId);
        }
        permissionService.assertIsOwner(topicId, actorUserId, actorRole);

        topicMemberRepository.updateRole(memberId, newRole);
        return topicMemberRepository.findById(memberId).orElseThrow();
    }
}
