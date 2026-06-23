package ru.basnukaev.argumentmap.hadith.curation.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.basnukaev.argumentmap.hadith.curation.domain.FieldOverride;

/**
 * Загруженные overrides набора записей одной таблицы, сгруппированные по
 * {@code entity_id → (field_name → FieldOverride)}. Типобезопасные
 * каст-помощники (§3.4) накладывают override на базовое значение поля.
 *
 * <p>Пустой набор — {@link #EMPTY} (NO_OP, без аллокаций): подавляющее
 * большинство записей overrides не имеют, и apply над ними — возврат базовой
 * записи как есть.
 */
public final class OverrideSet {

    private static final Logger log = LoggerFactory.getLogger(OverrideSet.class);

    public static final OverrideSet EMPTY = new OverrideSet(Map.of());

    private final Map<UUID, Map<String, FieldOverride>> byEntity;

    private OverrideSet(Map<UUID, Map<String, FieldOverride>> byEntity) {
        this.byEntity = byEntity;
    }

    /** Сгруппировать плоский список строк override в набор по entity_id. */
    public static OverrideSet group(Collection<FieldOverride> rows) {
        if (rows.isEmpty()) {
            return EMPTY;
        }
        Map<UUID, Map<String, FieldOverride>> m = new HashMap<>();
        for (FieldOverride o : rows) {
            m.computeIfAbsent(o.entityId(), k -> new HashMap<>()).put(o.fieldName(), o);
        }
        return new OverrideSet(m);
    }

    public boolean isEmpty() {
        return byEntity.isEmpty();
    }

    /** Есть ли у записи хоть один override (быстрый guard в apply). */
    public boolean hasEntity(UUID id) {
        return byEntity.containsKey(id);
    }

    /** Record-hide override записи ({@code __record__} + hidden), если есть. */
    public Optional<FieldOverride> recordHide(UUID id) {
        Map<String, FieldOverride> rec = byEntity.get(id);
        if (rec == null) {
            return Optional.empty();
        }
        FieldOverride o = rec.get(FieldOverride.RECORD_FIELD);
        return o != null && o.hidden() ? Optional.of(o) : Optional.empty();
    }

    /** Скрыта ли запись целиком ({@code __record__} + hidden) — Фаза 4. */
    public boolean isRecordHidden(UUID id) {
        return recordHide(id).isPresent();
    }

    /** Имена переопределённых (не record-hide) полей записи — для admin-индикатора. */
    public Set<String> overriddenFields(UUID id) {
        Map<String, FieldOverride> rec = byEntity.get(id);
        if (rec == null) {
            return Set.of();
        }
        return rec.values().stream()
                .filter(o -> !FieldOverride.RECORD_FIELD.equals(o.fieldName()))
                .map(FieldOverride::fieldName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private FieldOverride get(UUID id, String field) {
        Map<String, FieldOverride> rec = byEntity.get(id);
        return rec == null ? null : rec.get(field);
    }

    /** String/enum-поле: override-значение | null (hide/null-override) | base. */
    public String applyStr(UUID id, String field, String base) {
        FieldOverride o = get(id, field);
        if (o == null) {
            return base;
        }
        if (o.hidden()) {
            return null;                       // поле-уровневое скрытие (Фаза 4)
        }
        if (o.isNullOverride()) {
            return null;
        }
        return o.overrideValue() != null ? o.overrideValue() : base;
    }

    /** Числовое поле: parse | base (битый int → WARN + base, не роняем read). */
    public Integer applyInt(UUID id, String field, Integer base) {
        FieldOverride o = get(id, field);
        if (o == null) {
            return base;
        }
        if (o.hidden()) {
            return null;
        }
        if (o.isNullOverride()) {
            return null;
        }
        if (o.overrideValue() == null) {
            return base;
        }
        try {
            return Integer.valueOf(o.overrideValue().trim());
        } catch (NumberFormatException e) {
            log.warn("курация: битый int-override {}.{} = '{}' — оставлен base",
                    o.entityTable(), field, o.overrideValue());
            return base;
        }
    }

    /**
     * Boolean-поле: {@code "true"/"false"} | base. Параллель
     * {@link #applyInt}/{@link #applyStr}; первый вызов — Фаза 5
     * ({@code hd_sanads.primary_chain}). Тогда же ужесточить парсинг
     * (сейчас не-"true" → false молча, как {@code Boolean.valueOf}).
     */
    public Boolean applyBool(UUID id, String field, Boolean base) {
        FieldOverride o = get(id, field);
        if (o == null) {
            return base;
        }
        if (o.hidden()) {
            return null;
        }
        if (o.isNullOverride()) {
            return null;
        }
        return o.overrideValue() == null ? base : Boolean.valueOf(o.overrideValue().trim());
    }
}
