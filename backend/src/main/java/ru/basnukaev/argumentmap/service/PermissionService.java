package ru.basnukaev.argumentmap.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.domain.Topic;
import ru.basnukaev.argumentmap.domain.TopicMember;
import ru.basnukaev.argumentmap.domain.TopicMemberRole;
import ru.basnukaev.argumentmap.domain.TopicVisibility;
import ru.basnukaev.argumentmap.exception.TopicAccessDeniedException;
import ru.basnukaev.argumentmap.exception.TopicNotFoundException;
import ru.basnukaev.argumentmap.exception.TopicWriteAccessDeniedException;
import ru.basnukaev.argumentmap.repository.TopicMemberRepository;
import ru.basnukaev.argumentmap.repository.TopicRepository;

/**
 * Permission checks для тем (ADR-043). Vis матрица:
 * <ul>
 *   <li>PRIVATE: только owner может read/write
 *   <li>SHARED: owner + EDITOR могут write, owner + EDITOR + MEMBER могут read
 *   <li>PUBLIC: все аутентифицированные могут read, owner + EDITOR могут write
 * </ul>
 *
 * <p>ADMIN роль (ADR-040) bypass всех проверок.
 *
 * <p>Делается в Service-слое (не в Controller через @PreAuthorize) для
 * переиспользования в future GraphQL/CLI/scheduled jobs.
 */
@Service
public class PermissionService {

    private final TopicRepository topicRepository;
    private final TopicMemberRepository topicMemberRepository;

    public PermissionService(TopicRepository topicRepository,
                             TopicMemberRepository topicMemberRepository) {
        this.topicRepository = topicRepository;
        this.topicMemberRepository = topicMemberRepository;
    }

    @Transactional(readOnly = true)
    public boolean canReadTopic(UUID topicId, UUID userId, String role) {
        if (UserRole.ADMIN.equals(role)) {
            return true;
        }
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new TopicNotFoundException(topicId));
        return canReadTopic(topic, userId);
    }

    /**
     * Перегрузка для случаев когда у вызывающего уже есть Topic - избегаем
     * лишнего SELECT (используется в TopicService.getTopic после findById).
     */
    @Transactional(readOnly = true)
    public boolean canReadTopic(Topic topic, UUID userId) {
        if (topic.createdBy().equals(userId)) {
            return true;
        }
        if (TopicVisibility.PUBLIC.equals(topic.visibility())) {
            return true;
        }
        if (TopicVisibility.SHARED.equals(topic.visibility())) {
            return topicMemberRepository.existsByTopicAndUser(topic.id(), userId);
        }
        // PRIVATE без owner-match
        return false;
    }

    @Transactional(readOnly = true)
    public boolean canWriteTopic(UUID topicId, UUID userId, String role) {
        if (UserRole.ADMIN.equals(role)) {
            return true;
        }
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new TopicNotFoundException(topicId));
        return canWriteTopic(topic, userId);
    }

    @Transactional(readOnly = true)
    public boolean canWriteTopic(Topic topic, UUID userId) {
        if (topic.createdBy().equals(userId)) {
            return true;
        }
        // PRIVATE non-owner - запрещено
        if (TopicVisibility.PRIVATE.equals(topic.visibility())) {
            return false;
        }
        // SHARED / PUBLIC - write только если EDITOR
        return topicMemberRepository.findByTopicAndUser(topic.id(), userId)
                .map(TopicMember::role)
                .map(TopicMemberRole.EDITOR::equals)
                .orElse(false);
    }

    /**
     * Только owner темы может удалять её, менять visibility и управлять
     * членами. EDITOR это не может (даже на SHARED).
     */
    @Transactional(readOnly = true)
    public boolean isOwner(UUID topicId, UUID userId, String role) {
        if (UserRole.ADMIN.equals(role)) {
            return true;
        }
        return topicRepository.findById(topicId)
                .map(t -> t.createdBy().equals(userId))
                .orElse(false);
    }

    // ---- assert-варианты (бросают исключение) ----

    @Transactional(readOnly = true)
    public void assertCanRead(UUID topicId, UUID userId, String role) {
        if (!canReadTopic(topicId, userId, role)) {
            throw new TopicAccessDeniedException(topicId, userId);
        }
    }

    @Transactional(readOnly = true)
    public void assertCanWrite(UUID topicId, UUID userId, String role) {
        // Если читать нельзя - это access deny на уровне read (404-like
        // behaviour: не leak'аем существование private темы). Если читать
        // можно но писать нельзя - это write deny.
        if (!canReadTopic(topicId, userId, role)) {
            throw new TopicAccessDeniedException(topicId, userId);
        }
        if (!canWriteTopic(topicId, userId, role)) {
            throw new TopicWriteAccessDeniedException(topicId, userId);
        }
    }

    @Transactional(readOnly = true)
    public void assertIsOwner(UUID topicId, UUID userId, String role) {
        if (!isOwner(topicId, userId, role)) {
            throw new TopicWriteAccessDeniedException(topicId, userId);
        }
    }
}
