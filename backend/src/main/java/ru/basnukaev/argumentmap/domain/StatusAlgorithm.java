package ru.basnukaev.argumentmap.domain;

/**
 * Алгоритм пересчёта статусов узлов в теме (ADR-044). String-литералы
 * соответствуют CHECK constraint в БД (миграция 41), поэтому не enum -
 * тот же подход что у {@link TopicVisibility}.
 *
 * <ul>
 *   <li>{@link #MVP} - дефолтный fixpoint-алгоритм в
 *       {@link ru.basnukaev.argumentmap.service.StatusCalculationService}
 *       (учитывает SUPPORTS/REFUTES/INVALIDATES, см. ADR-007)
 *   <li>{@link #DUNG_GROUNDED} - grounded semantics Dung's framework,
 *       только attack-рёбра (REFUTES/INVALIDATES), реализация в
 *       {@link ru.basnukaev.argumentmap.service.DungFrameworkService}
 * </ul>
 */
public final class StatusAlgorithm {

    public static final String MVP = "MVP";
    public static final String DUNG_GROUNDED = "DUNG_GROUNDED";

    private StatusAlgorithm() {
    }

    public static boolean isValid(String value) {
        return MVP.equals(value) || DUNG_GROUNDED.equals(value);
    }
}
