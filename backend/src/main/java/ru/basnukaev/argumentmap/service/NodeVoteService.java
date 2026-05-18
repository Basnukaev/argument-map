package ru.basnukaev.argumentmap.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.NodeVote;
import ru.basnukaev.argumentmap.domain.VoteStats;
import ru.basnukaev.argumentmap.exception.InvalidVoteException;
import ru.basnukaev.argumentmap.exception.NodeNotFoundException;
import ru.basnukaev.argumentmap.repository.NodeRepository;
import ru.basnukaev.argumentmap.repository.NodeVoteRepository;

/**
 * Голосование за вес аргумента (узла).
 *
 * <p>Контракт permission: если user может read тему - может vote (ADR-043
 * downstream). Голос - это reaction, не контентное изменение, поэтому
 * write-access не требуется. PRIVATE-темы автоматически защищены
 * read-check'ом (non-owner не видит и не может голосовать).
 *
 * <p>Голоса не влияют на StatusCalculation - это сигнал силы, ортогональный
 * Dung-style logical status.
 */
@Service
public class NodeVoteService {

    private final NodeVoteRepository nodeVoteRepository;
    private final NodeRepository nodeRepository;
    private final PermissionService permissionService;

    public NodeVoteService(NodeVoteRepository nodeVoteRepository,
                           NodeRepository nodeRepository,
                           PermissionService permissionService) {
        this.nodeVoteRepository = nodeVoteRepository;
        this.nodeRepository = nodeRepository;
        this.permissionService = permissionService;
    }

    /**
     * Записать (или обновить) голос user'а за node. weight должен быть
     * -1 или +1. Идемпотентен: повторный vote с тем же weight - no-op.
     *
     * @throws NodeNotFoundException если узла нет
     * @throws InvalidVoteException если weight не из {-1, +1}
     */
    @Transactional
    public NodeVote vote(UUID nodeId, UUID userId, int weight) {
        if (weight != 1 && weight != -1) {
            throw new InvalidVoteException(
                    "Weight должен быть -1 или +1, получено: " + weight
            );
        }
        Node node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
        // read-permission check через topic'овский canRead - голос требует
        // только видимости узла. ADMIN bypass работает автоматически
        permissionService.assertCanRead(node.topicId(), userId,
                SecurityContextUtils.currentRoleOrAnonymous());

        NodeVote vote = new NodeVote(
                UUID.randomUUID(), nodeId, userId, weight, Instant.now()
        );
        return nodeVoteRepository.save(vote);
    }

    /**
     * Удалить голос user'а за node. Идемпотентен: если голоса не было -
     * возвращает false, но не бросает. Это для UI clear-vote операции.
     */
    @Transactional
    public boolean removeVote(UUID nodeId, UUID userId) {
        Node node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
        permissionService.assertCanRead(node.topicId(), userId,
                SecurityContextUtils.currentRoleOrAnonymous());
        return nodeVoteRepository.deleteByNodeAndUser(nodeId, userId);
    }

    @Transactional(readOnly = true)
    public VoteStats getStatsForNode(UUID nodeId) {
        if (nodeRepository.findById(nodeId).isEmpty()) {
            throw new NodeNotFoundException(nodeId);
        }
        return nodeVoteRepository.getStatsForNode(nodeId);
    }

    /**
     * Текущий vote user'а за узел: -1, +1 либо empty если не голосовал.
     * Не бросает permission deny - используется в bulk-load NodeResponse,
     * где permission уже проверен на уровне graph fetch.
     */
    @Transactional(readOnly = true)
    public Optional<Integer> getUserVote(UUID nodeId, UUID userId) {
        return nodeVoteRepository.findByNodeAndUser(nodeId, userId)
                .map(NodeVote::weight);
    }
}
