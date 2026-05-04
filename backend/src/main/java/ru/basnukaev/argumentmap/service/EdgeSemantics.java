package ru.basnukaev.argumentmap.service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import ru.basnukaev.argumentmap.domain.EdgeType;
import ru.basnukaev.argumentmap.domain.NodeType;

/**
 * Матрица допустимых пар (fromType, edgeType, toType). Источник истины - ADR-010.
 * Изменение здесь должно одновременно отражаться в ADR-010, во фронте
 * (src/utils/edgeRules.ts) и в тестах с обеих сторон.
 */
public final class EdgeSemantics {

    private static final Map<NodeType, Map<NodeType, Set<EdgeType>>> ALLOWED;

    static {
        Map<NodeType, Map<NodeType, Set<EdgeType>>> m = new EnumMap<>(NodeType.class);

        Map<NodeType, Set<EdgeType>> fromQuestion = new EnumMap<>(NodeType.class);
        fromQuestion.put(NodeType.QUESTION, EnumSet.of(EdgeType.QUALIFIES));
        fromQuestion.put(NodeType.CLAIM, EnumSet.of(EdgeType.QUALIFIES));
        fromQuestion.put(NodeType.ARGUMENT, EnumSet.of(EdgeType.QUALIFIES));
        m.put(NodeType.QUESTION, fromQuestion);

        Map<NodeType, Set<EdgeType>> fromClaim = new EnumMap<>(NodeType.class);
        fromClaim.put(NodeType.QUESTION, EnumSet.of(EdgeType.RESPONDS_TO));
        fromClaim.put(NodeType.CLAIM, EnumSet.of(EdgeType.SUPPORTS, EdgeType.REFUTES, EdgeType.QUALIFIES));
        m.put(NodeType.CLAIM, fromClaim);

        Map<NodeType, Set<EdgeType>> fromArgument = new EnumMap<>(NodeType.class);
        fromArgument.put(NodeType.CLAIM, EnumSet.of(EdgeType.SUPPORTS, EdgeType.REFUTES));
        fromArgument.put(NodeType.ARGUMENT, EnumSet.of(EdgeType.INVALIDATES));
        m.put(NodeType.ARGUMENT, fromArgument);

        Map<NodeType, Set<EdgeType>> fromEvidence = new EnumMap<>(NodeType.class);
        fromEvidence.put(NodeType.CLAIM, EnumSet.of(EdgeType.SUPPORTS, EdgeType.REFUTES));
        fromEvidence.put(NodeType.ARGUMENT, EnumSet.of(EdgeType.SUPPORTS, EdgeType.REFUTES, EdgeType.INVALIDATES));
        m.put(NodeType.EVIDENCE, fromEvidence);

        ALLOWED = m;
    }

    private EdgeSemantics() {
    }

    public static boolean isAllowed(NodeType fromType, EdgeType edgeType, NodeType toType) {
        Map<NodeType, Set<EdgeType>> byTo = ALLOWED.get(fromType);
        if (byTo == null) {
            return false;
        }
        Set<EdgeType> allowed = byTo.get(toType);
        return allowed != null && allowed.contains(edgeType);
    }

    public static Set<EdgeType> getAllowed(NodeType fromType, NodeType toType) {
        Map<NodeType, Set<EdgeType>> byTo = ALLOWED.get(fromType);
        if (byTo == null) {
            return Set.of();
        }
        Set<EdgeType> allowed = byTo.get(toType);
        return allowed != null ? Set.copyOf(allowed) : Set.of();
    }
}
