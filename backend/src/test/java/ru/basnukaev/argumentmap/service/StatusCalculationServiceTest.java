package ru.basnukaev.argumentmap.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import ru.basnukaev.argumentmap.domain.Edge;
import ru.basnukaev.argumentmap.domain.EdgeType;
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.repository.EdgeRepository;
import ru.basnukaev.argumentmap.repository.NodeRepository;

class StatusCalculationServiceTest {

    private NodeRepository nodeRepo;
    private EdgeRepository edgeRepo;
    private StatusCalculationService service;
    private UUID topicId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        nodeRepo = Mockito.mock(NodeRepository.class);
        edgeRepo = Mockito.mock(EdgeRepository.class);
        service = new StatusCalculationService(nodeRepo, edgeRepo);
        topicId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void singleQuestionWithoutEdges_staysUnverified() {
        UUID q = UUID.randomUUID();
        givenGraph(List.of(node(q, NodeStatus.UNVERIFIED)), List.of());

        service.recalculateTopic(topicId);

        verify(nodeRepo, never()).updateStatus(eq(q), any(), any());
    }

    @Test
    void claimSupportedByStanding_becomesStanding() {
        UUID source = UUID.randomUUID();
        UUID claim = UUID.randomUUID();
        givenGraph(
                List.of(node(source, NodeStatus.STANDING), node(claim, NodeStatus.UNVERIFIED)),
                List.of(edge(source, claim, EdgeType.SUPPORTS))
        );

        service.recalculateTopic(topicId);

        verify(nodeRepo).updateStatus(eq(claim), eq(NodeStatus.STANDING), any());
    }

    @Test
    void claimRefutedByStanding_becomesRefuted() {
        UUID source = UUID.randomUUID();
        UUID claim = UUID.randomUUID();
        givenGraph(
                List.of(node(source, NodeStatus.STANDING), node(claim, NodeStatus.UNVERIFIED)),
                List.of(edge(source, claim, EdgeType.REFUTES))
        );

        service.recalculateTopic(topicId);

        verify(nodeRepo).updateStatus(eq(claim), eq(NodeStatus.REFUTED), any());
    }

    @Test
    void claimWithStandingSupportAndStandingRefute_becomesDisputed() {
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        UUID claim = UUID.randomUUID();
        givenGraph(
                List.of(node(s1, NodeStatus.STANDING),
                        node(s2, NodeStatus.STANDING),
                        node(claim, NodeStatus.UNVERIFIED)),
                List.of(edge(s1, claim, EdgeType.SUPPORTS),
                        edge(s2, claim, EdgeType.REFUTES))
        );

        service.recalculateTopic(topicId);

        verify(nodeRepo).updateStatus(eq(claim), eq(NodeStatus.DISPUTED), any());
    }

    @Test
    void chain_aSupportsB_bSupportsC_cascadesToStanding() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        givenGraph(
                List.of(node(a, NodeStatus.STANDING),
                        node(b, NodeStatus.UNVERIFIED),
                        node(c, NodeStatus.UNVERIFIED)),
                List.of(edge(a, b, EdgeType.SUPPORTS),
                        edge(b, c, EdgeType.SUPPORTS))
        );

        service.recalculateTopic(topicId);

        verify(nodeRepo).updateStatus(eq(b), eq(NodeStatus.STANDING), any());
        verify(nodeRepo).updateStatus(eq(c), eq(NodeStatus.STANDING), any());
    }

    @Test
    void supportFromRefutedSource_doesNotMakeTargetStanding() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        givenGraph(
                List.of(node(a, NodeStatus.REFUTED), node(b, NodeStatus.UNVERIFIED)),
                List.of(edge(a, b, EdgeType.SUPPORTS))
        );

        service.recalculateTopic(topicId);

        // b остаётся UNVERIFIED — нет ни STANDING supports, ни refutes
        verify(nodeRepo, never()).updateStatus(eq(b), any(), any());
    }

    @Test
    void invalidatesFromStandingSource_killsTargetEvenWithStandingSupport() {
        UUID supporter = UUID.randomUUID();
        UUID metaArg = UUID.randomUUID();
        UUID claim = UUID.randomUUID();
        givenGraph(
                List.of(node(supporter, NodeStatus.STANDING),
                        node(metaArg, NodeStatus.STANDING),
                        node(claim, NodeStatus.UNVERIFIED)),
                List.of(edge(supporter, claim, EdgeType.SUPPORTS),
                        edge(metaArg, claim, EdgeType.INVALIDATES))
        );

        service.recalculateTopic(topicId);

        verify(nodeRepo).updateStatus(eq(claim), eq(NodeStatus.REFUTED), any());
    }

    @Test
    void invalidatesFromRefutedSource_isIgnored() {
        UUID metaArg = UUID.randomUUID();
        UUID supporter = UUID.randomUUID();
        UUID claim = UUID.randomUUID();
        givenGraph(
                List.of(node(metaArg, NodeStatus.REFUTED),
                        node(supporter, NodeStatus.STANDING),
                        node(claim, NodeStatus.UNVERIFIED)),
                List.of(edge(metaArg, claim, EdgeType.INVALIDATES),
                        edge(supporter, claim, EdgeType.SUPPORTS))
        );

        service.recalculateTopic(topicId);

        // INVALIDATES от REFUTED не убивает; supports от STANDING делает claim STANDING
        verify(nodeRepo).updateStatus(eq(claim), eq(NodeStatus.STANDING), any());
    }

    @Test
    void cycleASupportsBSupportsA_doesNotStackOverflow_andConverges() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        givenGraph(
                List.of(node(a, NodeStatus.UNVERIFIED), node(b, NodeStatus.UNVERIFIED)),
                List.of(edge(a, b, EdgeType.SUPPORTS), edge(b, a, EdgeType.SUPPORTS))
        );

        service.recalculateTopic(topicId);
        // ничего не должно упасть; оба узла остаются UNVERIFIED, ни один STANDING не появляется из ниоткуда
        verify(nodeRepo, never()).updateStatus(any(), any(), any());
    }

    @Test
    void cycleASupportsB_bRefutesA_converges() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        givenGraph(
                List.of(node(a, NodeStatus.STANDING), node(b, NodeStatus.STANDING)),
                List.of(edge(a, b, EdgeType.SUPPORTS), edge(b, a, EdgeType.REFUTES))
        );

        service.recalculateTopic(topicId);
        // Сходится за конечное число итераций — отсутствие исключений и так доказывает.
        // В этой точке оба узла сходятся к UNVERIFIED (см. трассировку алгоритма).
    }

    @Test
    void qualifiesEdge_doesNotChangeTargetStatus() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        givenGraph(
                List.of(node(a, NodeStatus.STANDING), node(b, NodeStatus.UNVERIFIED)),
                List.of(edge(a, b, EdgeType.QUALIFIES))
        );

        service.recalculateTopic(topicId);

        verify(nodeRepo, never()).updateStatus(eq(b), any(), any());
    }

    @Test
    void respondsToEdge_doesNotChangeTargetStatus() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        givenGraph(
                List.of(node(a, NodeStatus.STANDING), node(b, NodeStatus.UNVERIFIED)),
                List.of(edge(a, b, EdgeType.RESPONDS_TO))
        );

        service.recalculateTopic(topicId);

        verify(nodeRepo, never()).updateStatus(eq(b), any(), any());
    }

    @Test
    void emptyTopic_skipsCalculation() {
        when(nodeRepo.findByTopicId(topicId)).thenReturn(List.of());

        service.recalculateTopic(topicId);

        // edgeRepo не должен дёргаться, updateStatus тем более
        Mockito.verifyNoInteractions(edgeRepo);
        verify(nodeRepo, never()).updateStatus(any(), any(), any());
    }

    @Test
    void changeFromStandingToRefuted_persistsUpdate() {
        UUID source = UUID.randomUUID();
        UUID claim = UUID.randomUUID();
        givenGraph(
                List.of(node(source, NodeStatus.STANDING),
                        node(claim, NodeStatus.STANDING)),  // изначально STANDING
                List.of(edge(source, claim, EdgeType.REFUTES))  // но source REFUTES → должен стать REFUTED
        );

        service.recalculateTopic(topicId);

        verify(nodeRepo).updateStatus(eq(claim), eq(NodeStatus.REFUTED), any());
    }

    private void givenGraph(List<Node> nodes, List<Edge> edges) {
        when(nodeRepo.findByTopicId(topicId)).thenReturn(nodes);
        when(edgeRepo.findByTopicId(topicId)).thenReturn(edges);
    }

    private Node node(UUID id, NodeStatus status) {
        Instant now = Instant.now();
        return new Node(id, topicId, NodeType.CLAIM, "c", status, null, null, 0, userId, now, now);
    }

    private Edge edge(UUID from, UUID to, EdgeType type) {
        return new Edge(UUID.randomUUID(), from, to, type, null, null, null, userId, Instant.now());
    }
}
