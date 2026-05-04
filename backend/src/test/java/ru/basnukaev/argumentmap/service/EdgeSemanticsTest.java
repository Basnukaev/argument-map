package ru.basnukaev.argumentmap.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import ru.basnukaev.argumentmap.domain.EdgeType;
import ru.basnukaev.argumentmap.domain.NodeType;

class EdgeSemanticsTest {

    /** Зеркало матрицы из ADR-010. Если матрица в коде разойдётся со спецификацией - тест упадёт. */
    private static final Map<NodeType, Map<NodeType, Set<EdgeType>>> SPEC = buildSpec();

    private static Map<NodeType, Map<NodeType, Set<EdgeType>>> buildSpec() {
        Map<NodeType, Map<NodeType, Set<EdgeType>>> m = new EnumMap<>(NodeType.class);
        for (NodeType from : NodeType.values()) {
            m.put(from, new EnumMap<>(NodeType.class));
            for (NodeType to : NodeType.values()) {
                m.get(from).put(to, EnumSet.noneOf(EdgeType.class));
            }
        }

        m.get(NodeType.QUESTION).put(NodeType.QUESTION, EnumSet.of(EdgeType.QUALIFIES));
        m.get(NodeType.QUESTION).put(NodeType.CLAIM, EnumSet.of(EdgeType.QUALIFIES));
        m.get(NodeType.QUESTION).put(NodeType.ARGUMENT, EnumSet.of(EdgeType.QUALIFIES));

        m.get(NodeType.CLAIM).put(NodeType.QUESTION, EnumSet.of(EdgeType.RESPONDS_TO));
        m.get(NodeType.CLAIM).put(NodeType.CLAIM,
                EnumSet.of(EdgeType.SUPPORTS, EdgeType.REFUTES, EdgeType.QUALIFIES));

        m.get(NodeType.ARGUMENT).put(NodeType.CLAIM, EnumSet.of(EdgeType.SUPPORTS, EdgeType.REFUTES));
        m.get(NodeType.ARGUMENT).put(NodeType.ARGUMENT, EnumSet.of(EdgeType.INVALIDATES));

        m.get(NodeType.EVIDENCE).put(NodeType.CLAIM, EnumSet.of(EdgeType.SUPPORTS, EdgeType.REFUTES));
        m.get(NodeType.EVIDENCE).put(NodeType.ARGUMENT,
                EnumSet.of(EdgeType.SUPPORTS, EdgeType.REFUTES, EdgeType.INVALIDATES));

        return m;
    }

    @TestFactory
    Stream<DynamicTest> isAllowed_matchesSpec_forEveryCombination() {
        return Stream.of(NodeType.values()).flatMap(from ->
                Stream.of(NodeType.values()).flatMap(to ->
                        Stream.of(EdgeType.values()).map(edge -> {
                            boolean expected = SPEC.get(from).get(to).contains(edge);
                            String name = "%s --%s-> %s expects %s".formatted(
                                    from, edge, to, expected);
                            return DynamicTest.dynamicTest(name, () ->
                                    assertThat(EdgeSemantics.isAllowed(from, edge, to)).isEqualTo(expected));
                        })));
    }

    @TestFactory
    Stream<DynamicTest> getAllowed_matchesSpec_forEveryPair() {
        return Stream.of(NodeType.values()).flatMap(from ->
                Stream.of(NodeType.values()).map(to -> {
                    Set<EdgeType> expected = SPEC.get(from).get(to);
                    String name = "%s -> %s expects %s".formatted(from, to, expected);
                    return DynamicTest.dynamicTest(name, () ->
                            assertThat(EdgeSemantics.getAllowed(from, to)).isEqualTo(expected));
                }));
    }
}
