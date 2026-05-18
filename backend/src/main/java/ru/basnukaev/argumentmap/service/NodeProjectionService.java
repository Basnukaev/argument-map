package ru.basnukaev.argumentmap.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.domain.NodeTranslation;
import ru.basnukaev.argumentmap.domain.VoteStats;
import ru.basnukaev.argumentmap.repository.NodeSourceRepository;
import ru.basnukaev.argumentmap.repository.NodeTranslationRepository;
import ru.basnukaev.argumentmap.repository.NodeVoteRepository;
import ru.basnukaev.argumentmap.web.dto.InlineCitationRef;

/**
 * Projection-сервис для обогащения узлов данными которые рендерятся в
 * NodeResponse: голос-статистика, личный голос пользователя, inline
 * citations, переводы.
 *
 * <p>Введён в backend architecture audit 2026-05-18 - устраняет layering
 * violation: ранее NodeController и TopicController.getGraph дёргали
 * NodeVoteRepository / NodeSourceRepository / NodeTranslationRepository
 * напрямую (4 раза в NodeController и 1 раз в TopicController), что
 * нарушало правило «controller → service → repository». 4-строчный
 * enrichment fragment дублировался; теперь один источник истины.
 *
 * <p>Single-node вариант через 4 single-row SQL. Batch вариант через
 * 4 bulk SQL (не N+1) - используется для всего графа сразу.
 */
@Service
public class NodeProjectionService {

    private final NodeVoteRepository nodeVoteRepository;
    private final NodeSourceRepository nodeSourceRepository;
    private final NodeTranslationRepository nodeTranslationRepository;

    public NodeProjectionService(NodeVoteRepository nodeVoteRepository,
                                 NodeSourceRepository nodeSourceRepository,
                                 NodeTranslationRepository nodeTranslationRepository) {
        this.nodeVoteRepository = nodeVoteRepository;
        this.nodeSourceRepository = nodeSourceRepository;
        this.nodeTranslationRepository = nodeTranslationRepository;
    }

    /**
     * Single-node projection: 4 SQL запроса (stats + userVote + citations +
     * translations). Используется в PATCH-then-enrich сценариях
     * (NodeController.update/bringToFront/sendToBack), где после
     * NodeService.* нужен полный NodeResponse.
     *
     * <p>{@code userId} null - userVote вернётся null (anonymous case
     * не используется сейчас, но safe).
     */
    @Transactional(readOnly = true)
    public NodeProjection single(UUID nodeId, UUID userId) {
        VoteStats stats = nodeVoteRepository.getStatsForNode(nodeId);
        Integer userVote = userId == null
                ? null
                : nodeVoteRepository.findByNodeAndUser(nodeId, userId)
                        .map(v -> v.weight()).orElse(null);
        List<InlineCitationRef> citations = nodeSourceRepository.findInlineCitationsForNode(nodeId);
        List<NodeTranslation> translations = nodeTranslationRepository.findByNodeId(nodeId);
        return new NodeProjection(stats, userVote, citations, translations);
    }

    /**
     * Batch projection для всего графа: 4 bulk SQL вместо 4*N. Возвращает
     * map'ы по nodeId. Используется в TopicController.getGraph.
     */
    @Transactional(readOnly = true)
    public NodeProjectionBatch batch(List<UUID> nodeIds, UUID userId) {
        Map<UUID, VoteStats> stats = nodeVoteRepository.getStatsForNodes(nodeIds);
        Map<UUID, Integer> userVotes = nodeVoteRepository.getUserVotesForNodes(nodeIds, userId);
        Map<UUID, List<InlineCitationRef>> citations =
                nodeSourceRepository.findInlineCitationsForNodes(nodeIds);
        Map<UUID, List<NodeTranslation>> translations =
                nodeTranslationRepository.findByNodeIds(nodeIds);
        return new NodeProjectionBatch(stats, userVotes, citations, translations);
    }

    /**
     * Single-node projection (для одного узла). Поля могут быть null
     * (userVote если пользователь не голосовал) либо пустыми списками.
     */
    public record NodeProjection(
            VoteStats stats,
            Integer userVote,
            List<InlineCitationRef> citations,
            List<NodeTranslation> translations
    ) {
    }

    /**
     * Batch projection (для всего графа). Все четыре map'а - key=nodeId.
     */
    public record NodeProjectionBatch(
            Map<UUID, VoteStats> stats,
            Map<UUID, Integer> userVotes,
            Map<UUID, List<InlineCitationRef>> citations,
            Map<UUID, List<NodeTranslation>> translations
    ) {
    }
}
