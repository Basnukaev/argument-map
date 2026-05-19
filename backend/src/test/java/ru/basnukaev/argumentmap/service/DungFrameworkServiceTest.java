package ru.basnukaev.argumentmap.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ru.basnukaev.argumentmap.domain.Edge;
import ru.basnukaev.argumentmap.domain.EdgeType;
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;

/**
 * Unit-тесты grounded labelling - без БД, на records напрямую. См.
 * ADR-044
 */
class DungFrameworkServiceTest {

    private DungFrameworkService service;
    private UUID topicId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new DungFrameworkService();
        topicId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void empty_returnsEmptyLabelling() {
        Map<UUID, String> labels = service.computeGroundedLabelling(List.of(), List.of());

        assertThat(labels).isEmpty();
    }

    @Test
    void singleNoAttack_returnsIN() {
        Node a = node();

        Map<UUID, String> labels = service.computeGroundedLabelling(List.of(a), List.of());

        assertThat(labels).containsEntry(a.id(), DungFrameworkService.IN);
    }

    @Test
    void multipleIndependent_allReturnIN() {
        Node a = node();
        Node b = node();
        Node c = node();

        Map<UUID, String> labels = service.computeGroundedLabelling(
                List.of(a, b, c), List.of()
        );

        assertThat(labels).hasSize(3);
        assertThat(labels.values()).allMatch(DungFrameworkService.IN::equals);
    }

    @Test
    void attackedByIN_returnsOUT() {
        // a attacks b - a без attackers (IN), b отбит (OUT)
        Node a = node();
        Node b = node();
        Edge attack = edge(a.id(), b.id(), EdgeType.REFUTES);

        Map<UUID, String> labels = service.computeGroundedLabelling(
                List.of(a, b), List.of(attack)
        );

        assertThat(labels).containsEntry(a.id(), DungFrameworkService.IN);
        assertThat(labels).containsEntry(b.id(), DungFrameworkService.OUT);
    }

    @Test
    void invalidatesEdge_alsoTreatedAsAttack() {
        // INVALIDATES (как REFUTES) - attack по Dung'у
        Node a = node();
        Node b = node();
        Edge inv = edge(a.id(), b.id(), EdgeType.INVALIDATES);

        Map<UUID, String> labels = service.computeGroundedLabelling(
                List.of(a, b), List.of(inv)
        );

        assertThat(labels).containsEntry(a.id(), DungFrameworkService.IN);
        assertThat(labels).containsEntry(b.id(), DungFrameworkService.OUT);
    }

    @Test
    void attackedByOUT_returnsIN_defenderCase() {
        // c attacks b, a attacks c. c защищён a (a IN → c OUT → b IN)
        Node a = node();
        Node c = node();
        Node b = node();
        Edge aAttacksC = edge(a.id(), c.id(), EdgeType.REFUTES);
        Edge cAttacksB = edge(c.id(), b.id(), EdgeType.REFUTES);

        Map<UUID, String> labels = service.computeGroundedLabelling(
                List.of(a, b, c), List.of(aAttacksC, cAttacksB)
        );

        assertThat(labels).containsEntry(a.id(), DungFrameworkService.IN);
        assertThat(labels).containsEntry(c.id(), DungFrameworkService.OUT);
        assertThat(labels).containsEntry(b.id(), DungFrameworkService.IN);
    }

    @Test
    void mutualAttackCycle_returnsUNDEC() {
        // a attacks b, b attacks a - классический Dung-cycle. Оба UNDEC
        Node a = node();
        Node b = node();
        Edge aAttacksB = edge(a.id(), b.id(), EdgeType.REFUTES);
        Edge bAttacksA = edge(b.id(), a.id(), EdgeType.REFUTES);

        Map<UUID, String> labels = service.computeGroundedLabelling(
                List.of(a, b), List.of(aAttacksB, bAttacksA)
        );

        assertThat(labels).containsEntry(a.id(), DungFrameworkService.UNDEC);
        assertThat(labels).containsEntry(b.id(), DungFrameworkService.UNDEC);
    }

    @Test
    void supportEdgesAreIgnored_inGroundedLabelling() {
        // a SUPPORTS b - в Dung'е игнорируется. Оба IN (нет attacks)
        Node a = node();
        Node b = node();
        Edge support = edge(a.id(), b.id(), EdgeType.SUPPORTS);

        Map<UUID, String> labels = service.computeGroundedLabelling(
                List.of(a, b), List.of(support)
        );

        assertThat(labels).containsEntry(a.id(), DungFrameworkService.IN);
        assertThat(labels).containsEntry(b.id(), DungFrameworkService.IN);
    }

    @Test
    void qualifiesAndRespondsTo_alsoIgnored() {
        Node a = node();
        Node b = node();
        Node c = node();
        Edge q = edge(a.id(), b.id(), EdgeType.QUALIFIES);
        Edge r = edge(b.id(), c.id(), EdgeType.RESPONDS_TO);

        Map<UUID, String> labels = service.computeGroundedLabelling(
                List.of(a, b, c), List.of(q, r)
        );

        // ни одного attack, все IN
        assertThat(labels.values()).allMatch(DungFrameworkService.IN::equals);
    }

    @Test
    void complexChain_correctLabelling() {
        // Цепочка: a → b → c → d (где → = REFUTES)
        // a IN (no attackers), b OUT (a IN), c IN (b OUT), d OUT (c IN)
        Node a = node();
        Node b = node();
        Node c = node();
        Node d = node();
        Edge ab = edge(a.id(), b.id(), EdgeType.REFUTES);
        Edge bc = edge(b.id(), c.id(), EdgeType.REFUTES);
        Edge cd = edge(c.id(), d.id(), EdgeType.REFUTES);

        Map<UUID, String> labels = service.computeGroundedLabelling(
                List.of(a, b, c, d), List.of(ab, bc, cd)
        );

        assertThat(labels).containsEntry(a.id(), DungFrameworkService.IN);
        assertThat(labels).containsEntry(b.id(), DungFrameworkService.OUT);
        assertThat(labels).containsEntry(c.id(), DungFrameworkService.IN);
        assertThat(labels).containsEntry(d.id(), DungFrameworkService.OUT);
    }

    @Test
    void doubleAttacker_oneInOneOut_targetIsOUT() {
        // b и c attacks d. b IN, c OUT (а атакует с). d должен быть OUT
        // потому что хоть один attacker IN
        Node a = node();
        Node b = node();
        Node c = node();
        Node d = node();
        Edge aAttacksC = edge(a.id(), c.id(), EdgeType.REFUTES);
        Edge bAttacksD = edge(b.id(), d.id(), EdgeType.REFUTES);
        Edge cAttacksD = edge(c.id(), d.id(), EdgeType.REFUTES);

        Map<UUID, String> labels = service.computeGroundedLabelling(
                List.of(a, b, c, d), List.of(aAttacksC, bAttacksD, cAttacksD)
        );

        assertThat(labels).containsEntry(a.id(), DungFrameworkService.IN);
        assertThat(labels).containsEntry(b.id(), DungFrameworkService.IN);
        assertThat(labels).containsEntry(c.id(), DungFrameworkService.OUT);
        assertThat(labels).containsEntry(d.id(), DungFrameworkService.OUT);
    }

    @Test
    void threeCycle_allUNDEC() {
        // a → b → c → a - тройной cycle, все UNDEC
        Node a = node();
        Node b = node();
        Node c = node();
        Edge ab = edge(a.id(), b.id(), EdgeType.REFUTES);
        Edge bc = edge(b.id(), c.id(), EdgeType.REFUTES);
        Edge ca = edge(c.id(), a.id(), EdgeType.REFUTES);

        Map<UUID, String> labels = service.computeGroundedLabelling(
                List.of(a, b, c), List.of(ab, bc, ca)
        );

        assertThat(labels.values()).allMatch(DungFrameworkService.UNDEC::equals);
    }

    @Test
    void selfAttack_isUNDEC() {
        // a attacks a - self-attacker. По grounded semantics UNDEC
        // (не IN т.к. себя же атакует, не OUT т.к. нет другого IN-attacker'а)
        Node a = node();
        Edge selfAttack = edge(a.id(), a.id(), EdgeType.REFUTES);

        Map<UUID, String> labels = service.computeGroundedLabelling(
                List.of(a), List.of(selfAttack)
        );

        assertThat(labels).containsEntry(a.id(), DungFrameworkService.UNDEC);
    }

    @Test
    void orphanEdge_referenceToMissingNode_isIgnored() {
        // edge от UUID не в nodes - defensively ignored
        Node a = node();
        UUID ghost = UUID.randomUUID();
        Edge orphan = edge(ghost, a.id(), EdgeType.REFUTES);

        Map<UUID, String> labels = service.computeGroundedLabelling(
                List.of(a), List.of(orphan)
        );

        // a остаётся IN т.к. ghost-attacker не считается
        assertThat(labels).containsEntry(a.id(), DungFrameworkService.IN);
    }

    @Test
    void nullEdges_treatedAsEmpty() {
        Node a = node();

        Map<UUID, String> labels = service.computeGroundedLabelling(List.of(a), null);

        assertThat(labels).containsEntry(a.id(), DungFrameworkService.IN);
    }

    private Node node() {
        Instant now = Instant.now();
        return new Node(UUID.randomUUID(), topicId, NodeType.CLAIM, "c",
                NodeStatus.UNVERIFIED, null, null, 0, userId, now, now,
                null);
    }

    private Edge edge(UUID from, UUID to, EdgeType type) {
        return new Edge(UUID.randomUUID(), from, to, type, null, null, null,
                userId, Instant.now(), 0);
    }
}
