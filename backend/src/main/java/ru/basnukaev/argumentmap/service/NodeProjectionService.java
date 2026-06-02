package ru.basnukaev.argumentmap.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.domain.NodeTranslation;
import ru.basnukaev.argumentmap.repository.NodeSourceRepository;
import ru.basnukaev.argumentmap.repository.NodeTranslationRepository;
import ru.basnukaev.argumentmap.web.dto.InlineCitationRef;

/**
 * Projection-сервис для обогащения узлов данными которые рендерятся в
 * NodeResponse: inline citations, переводы.
 *
 * <p>Введён в backend architecture audit 2026-05-18 - устраняет layering
 * violation: ранее NodeController и TopicController.getGraph дёргали
 * NodeSourceRepository / NodeTranslationRepository напрямую, что нарушало
 * правило «controller → service → repository». Enrichment fragment
 * дублировался; теперь один источник истины.
 *
 * <p>Голосование за узлы удалено (ADR-053) - голоса теперь на
 * уровне тем. Projection обогащает только citations + translations.
 *
 * <p>Single-node вариант через 2 single-row SQL. Batch вариант через
 * 2 bulk SQL (не N+1) - используется для всего графа сразу.
 */
@Service
public class NodeProjectionService {

    private final NodeSourceRepository nodeSourceRepository;
    private final NodeTranslationRepository nodeTranslationRepository;

    public NodeProjectionService(NodeSourceRepository nodeSourceRepository,
                                 NodeTranslationRepository nodeTranslationRepository) {
        this.nodeSourceRepository = nodeSourceRepository;
        this.nodeTranslationRepository = nodeTranslationRepository;
    }

    /**
     * Single-node projection: 2 SQL запроса (citations + translations).
     * Используется в PATCH-then-enrich сценариях
     * (NodeController.update/bringToFront/sendToBack), где после
     * NodeService.* нужен полный NodeResponse.
     */
    @Transactional(readOnly = true)
    public NodeProjection single(UUID nodeId) {
        List<InlineCitationRef> citations = nodeSourceRepository.findInlineCitationsForNode(nodeId);
        List<NodeTranslation> translations = nodeTranslationRepository.findByNodeId(nodeId);
        return new NodeProjection(citations, translations);
    }

    /**
     * Batch projection для всего графа: 2 bulk SQL вместо 2*N. Возвращает
     * map'ы по nodeId. Используется в TopicController.getGraph.
     */
    @Transactional(readOnly = true)
    public NodeProjectionBatch batch(List<UUID> nodeIds) {
        Map<UUID, List<InlineCitationRef>> citations =
                nodeSourceRepository.findInlineCitationsForNodes(nodeIds);
        Map<UUID, List<NodeTranslation>> translations =
                nodeTranslationRepository.findByNodeIds(nodeIds);
        return new NodeProjectionBatch(citations, translations);
    }

    /**
     * Single-node projection (для одного узла). Поля могут быть пустыми
     * списками (нет citations / translations).
     */
    public record NodeProjection(
            List<InlineCitationRef> citations,
            List<NodeTranslation> translations
    ) {
    }

    /**
     * Batch projection (для всего графа). Обе map'ы - key=nodeId.
     */
    public record NodeProjectionBatch(
            Map<UUID, List<InlineCitationRef>> citations,
            Map<UUID, List<NodeTranslation>> translations
    ) {
    }
}
