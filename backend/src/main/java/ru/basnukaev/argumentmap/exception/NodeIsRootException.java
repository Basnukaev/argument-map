package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

/**
 * Бросается при попытке удалить корневой узел темы. Корневой узел -
 * это `topics.root_node_id` для одноимённой темы; его удаление разрушает
 * целостность графа (orphan edges, status calculation сломан).
 * Чтобы удалить корневой вопрос - надо удалить тему целиком.
 */
public class NodeIsRootException extends RuntimeException {

    private final UUID nodeId;
    private final UUID topicId;

    public NodeIsRootException(UUID nodeId, UUID topicId) {
        super("Узел " + nodeId + " является корневым для темы " + topicId
                + " - удалите тему целиком");
        this.nodeId = nodeId;
        this.topicId = topicId;
    }

    public UUID getNodeId() {
        return nodeId;
    }

    public UUID getTopicId() {
        return topicId;
    }
}
