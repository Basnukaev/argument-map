package ru.basnukaev.argumentmap.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import ru.basnukaev.argumentmap.domain.Edge;
import ru.basnukaev.argumentmap.domain.EdgeType;
import ru.basnukaev.argumentmap.domain.Node;

/**
 * Реализация Dung's abstract argumentation framework (ADR-044) для
 * расчёта статусов узлов через grounded labelling.
 *
 * <p>Argumentation Framework по Dung'у - пара (A, R): A - аргументы
 * (наши nodes), R - attack relation (наши edges типа REFUTES /
 * INVALIDATES). Grounded extension - минимальный complete extension
 * (skeptical reasoning, ровно одно решение для любого графа)
 *
 * <p>Labels:
 * <ul>
 *   <li>{@link #IN} - argument accepted (defender всех своих attackers)
 *   <li>{@link #OUT} - argument rejected (есть IN-attacker)
 *   <li>{@link #UNDEC} - undecided (cycle attacks без defender)
 * </ul>
 *
 * <p>Алгоритм - iterative labelling до сходимости:
 * <ol>
 *   <li>Nodes без attackers → IN
 *   <li>Nodes у которых все attackers OUT → IN
 *   <li>Nodes с attacker IN → OUT
 *   <li>Повторять 2-3 до convergence
 *   <li>Remaining → UNDEC
 * </ol>
 *
 * <p>SUPPORTS / QUALIFIES / RESPONDS_TO edges намеренно игнорируются -
 * Dung's framework attack-based по дизайну (см. ADR-044). Если нужны
 * support-эффекты - использовать MVP алгоритм
 */
@Service
public class DungFrameworkService {

    public static final String IN = "IN";
    public static final String OUT = "OUT";
    public static final String UNDEC = "UNDEC";

    /**
     * Возвращает grounded labelling для argumentation framework (nodes,
     * attack-edges). Каждому nodeId сопоставляется один из labels
     * {@link #IN} / {@link #OUT} / {@link #UNDEC}. Order вычислений
     * детерминирован - один и тот же граф даёт один и тот же labelling
     */
    public Map<UUID, String> computeGroundedLabelling(List<Node> nodes, List<Edge> edges) {
        if (nodes == null || nodes.isEmpty()) {
            return Map.of();
        }

        // Attack adjacency: nodeId -> Set<attackerNodeId>. Учитываем только
        // attack-edges (REFUTES + INVALIDATES). Edge от отсутствующего в
        // nodes узла игнорируется (defensive против orphan edges, не должно
        // случаться по invariants)
        Set<UUID> nodeIds = new HashSet<>(nodes.size());
        for (Node n : nodes) {
            nodeIds.add(n.id());
        }
        Map<UUID, Set<UUID>> attackersOf = new HashMap<>(nodes.size());
        for (Node n : nodes) {
            attackersOf.put(n.id(), new HashSet<>());
        }
        if (edges != null) {
            for (Edge e : edges) {
                if (!isAttack(e.edgeType())) {
                    continue;
                }
                if (!nodeIds.contains(e.fromNodeId()) || !nodeIds.contains(e.toNodeId())) {
                    continue;
                }
                attackersOf.get(e.toNodeId()).add(e.fromNodeId());
            }
        }

        Map<UUID, String> labels = new HashMap<>(nodes.size());

        // Iterative labelling до convergence. Bound = nodes.size() итераций
        // (каждый node может изменить label максимум один раз от undef → IN/OUT;
        // UNDEC присваивается только после exit из loop'а). Защита от
        // бесконечного цикла на случай implementation bug
        int maxIterations = nodes.size() + 1;
        boolean changed = true;
        int iter = 0;
        while (changed && iter < maxIterations) {
            changed = false;
            for (Node n : nodes) {
                UUID nodeId = n.id();
                if (labels.containsKey(nodeId)) {
                    continue;
                }
                Set<UUID> attackers = attackersOf.get(nodeId);
                if (attackers.isEmpty()) {
                    // нет attackers - IN unconditionally
                    labels.put(nodeId, IN);
                    changed = true;
                    continue;
                }
                // Если хоть один attacker IN - node OUT (defended attacker'ом)
                boolean hasInAttacker = false;
                boolean allOutAttackers = true;
                for (UUID atk : attackers) {
                    String atkLabel = labels.get(atk);
                    if (IN.equals(atkLabel)) {
                        hasInAttacker = true;
                        break;
                    }
                    if (!OUT.equals(atkLabel)) {
                        allOutAttackers = false;
                    }
                }
                if (hasInAttacker) {
                    labels.put(nodeId, OUT);
                    changed = true;
                } else if (allOutAttackers) {
                    // Все attackers OUT (отбиты) - node defended, label IN
                    labels.put(nodeId, IN);
                    changed = true;
                }
                // иначе - оставляем undef до следующей итерации (есть
                // attacker UNDEC или unlabeled)
            }
            iter++;
        }

        // Все ноды без label после convergence - UNDEC (attack-cycle без
        // defender'а)
        for (Node n : nodes) {
            labels.putIfAbsent(n.id(), UNDEC);
        }

        return labels;
    }

    private static boolean isAttack(EdgeType type) {
        return type == EdgeType.REFUTES || type == EdgeType.INVALIDATES;
    }
}
