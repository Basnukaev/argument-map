package ru.basnukaev.argumentmap.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.domain.TopicVote;
import ru.basnukaev.argumentmap.domain.VoteStats;
import ru.basnukaev.argumentmap.exception.InvalidVoteException;
import ru.basnukaev.argumentmap.exception.TopicNotFoundException;
import ru.basnukaev.argumentmap.repository.TopicRepository;
import ru.basnukaev.argumentmap.repository.TopicVoteRepository;

/**
 * Голосование за темы (community-сигнал популярности, ADR-053).
 *
 * <p>Контракт permission: если user может read тему - может vote. Голос - это
 * reaction, не контентное изменение, поэтому write-access не требуется.
 * PRIVATE-темы автоматически защищены read-check'ом (non-owner не видит и не
 * может голосовать). ADMIN bypass автоматически через PermissionService.
 *
 * <p>Зеркалит удалённый NodeVoteService но на уровне тем - узлы это curated
 * expert data, голосование за них убрано.
 */
@Service
public class TopicVoteService {

    private final TopicVoteRepository topicVoteRepository;
    private final TopicRepository topicRepository;
    private final PermissionService permissionService;

    public TopicVoteService(TopicVoteRepository topicVoteRepository,
                            TopicRepository topicRepository,
                            PermissionService permissionService) {
        this.topicVoteRepository = topicVoteRepository;
        this.topicRepository = topicRepository;
        this.permissionService = permissionService;
    }

    /**
     * Записать (или обновить) голос user'а за тему. weight должен быть
     * -1 или +1. Идемпотентен: повторный vote с тем же weight - no-op (upsert).
     *
     * @throws TopicNotFoundException если темы нет
     * @throws InvalidVoteException   если weight не из {-1, +1}
     * @throws ru.basnukaev.argumentmap.exception.TopicAccessDeniedException
     *         если нет read-доступа к теме (403)
     */
    @Transactional
    public TopicVote vote(UUID topicId, UUID userId, int weight, String role) {
        if (weight != 1 && weight != -1) {
            throw new InvalidVoteException(
                    "Weight должен быть -1 или +1, получено: " + weight
            );
        }
        assertTopicExists(topicId);
        // read-permission check: голос требует только видимости темы.
        // ADMIN bypass работает автоматически
        permissionService.assertCanRead(topicId, userId, role);

        TopicVote vote = new TopicVote(
                UUID.randomUUID(), topicId, userId, weight, Instant.now()
        );
        return topicVoteRepository.save(vote);
    }

    /**
     * Удалить голос user'а за тему. Идемпотентен: если голоса не было -
     * возвращает false, но не бросает. Это для UI clear-vote операции.
     *
     * @throws TopicNotFoundException если темы нет
     * @throws ru.basnukaev.argumentmap.exception.TopicAccessDeniedException
     *         если нет read-доступа к теме (403)
     */
    @Transactional
    public boolean removeVote(UUID topicId, UUID userId, String role) {
        assertTopicExists(topicId);
        permissionService.assertCanRead(topicId, userId, role);
        return topicVoteRepository.deleteByTopicAndUser(topicId, userId);
    }

    /**
     * Агрегаты голосов темы с read-guard. Резолвит тему и проверяет доступ -
     * не отдаём статистику по приватным темам non-owner'у. Симметрично
     * vote()/removeVote().
     *
     * @throws TopicNotFoundException если темы нет
     * @throws ru.basnukaev.argumentmap.exception.TopicAccessDeniedException
     *         если нет read-доступа к теме (403)
     */
    @Transactional(readOnly = true)
    public VoteStats getStats(UUID topicId, UUID userId, String role) {
        assertTopicExists(topicId);
        permissionService.assertCanRead(topicId, userId, role);
        return topicVoteRepository.getStatsForTopic(topicId);
    }

    /**
     * Текущий vote user'а за тему: -1, +1 либо empty если не голосовал.
     * Не бросает permission deny - используется после того как доступ уже
     * проверен (GET endpoint вызывает getStats первым).
     */
    @Transactional(readOnly = true)
    public Optional<Integer> getUserVote(UUID topicId, UUID userId) {
        return topicVoteRepository.getUserVote(topicId, userId);
    }

    private void assertTopicExists(UUID topicId) {
        if (topicRepository.findById(topicId).isEmpty()) {
            throw new TopicNotFoundException(topicId);
        }
    }
}
