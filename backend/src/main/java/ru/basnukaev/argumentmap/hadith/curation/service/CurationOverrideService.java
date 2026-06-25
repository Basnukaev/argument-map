package ru.basnukaev.argumentmap.hadith.curation.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.exception.AdminOnlyException;
import ru.basnukaev.argumentmap.hadith.curation.domain.CurationWhitelist;
import ru.basnukaev.argumentmap.hadith.curation.domain.FieldOverride;
import ru.basnukaev.argumentmap.hadith.curation.domain.OverrideEntity;
import ru.basnukaev.argumentmap.hadith.curation.repository.OverrideRepository;
import ru.basnukaev.argumentmap.hadith.curation.web.CurationException;
import ru.basnukaev.argumentmap.hadith.curation.web.dto.CurationOverridePutRequest;
import ru.basnukaev.argumentmap.hadith.curation.web.dto.CurationOverrideResponse;
import ru.basnukaev.argumentmap.service.AuditLogService;

/**
 * Write-сервис курации (ADR-065 §6/§7): upsert/delete/list overrides.
 * ADMIN-only (FB-5 — admin-курация/модерация). Валидирует whitelist
 * (§5: первоисточник не правится), enum-значения, обязательность reason
 * для hide. Аудит двойной: {@code edited_by/at/reason} в самой строке +
 * {@link AuditLogService} (ADR-043) в ТОЙ ЖЕ транзакции (consistency).
 */
@Service
public class CurationOverrideService {

    private static final String AUDIT_ENTITY = "HD_FIELD_OVERRIDE";

    private final OverrideRepository overrideRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    public CurationOverrideService(OverrideRepository overrideRepository,
                                   JdbcTemplate jdbcTemplate,
                                   AuditLogService auditLogService) {
        this.overrideRepository = overrideRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogService = auditLogService;
    }

    /** Upsert правки/скрытия. Idempotent по UNIQUE-ключу. */
    @Transactional
    public CurationOverrideResponse upsert(CurationOverridePutRequest req, UUID userId, String role) {
        requireAdmin(role, userId);
        OverrideEntity entity = resolve(req.entityTable());

        boolean hidden = req.hiddenFlag();
        boolean isNull = req.isNullFlag();
        boolean hasValue = req.value() != null;

        if (!hidden && !isNull && !hasValue) {
            throw CurationException.emptyOverride();
        }
        if (hidden && isBlank(req.reason())) {
            throw CurationException.reasonRequired();
        }
        validateField(entity, req.fieldName(), hidden, isNull, hasValue, req.value());
        assertEntityExists(entity, req.entityId());

        FieldOverride existing = overrideRepository
                .findOne(entity, req.entityId(), req.fieldName()).orElse(null);
        FieldOverride toSave = new FieldOverride(
                existing != null ? existing.id() : UUID.randomUUID(),
                entity.tableName(), req.entityId(), req.fieldName(),
                isNull ? null : req.value(), isNull, hidden,
                userId, Instant.now(), trimToNull(req.reason()));
        FieldOverride saved = overrideRepository.upsert(toSave);

        CurationOverrideResponse resp = CurationOverrideResponse.from(saved);
        if (existing == null) {
            auditLogService.logCreate(AUDIT_ENTITY, saved.id(), entity.tableName(),
                    req.entityId(), userId, resp);
        } else {
            Map<String, AuditLogService.FieldDiff> diff = new LinkedHashMap<>();
            putIfChanged(diff, "value", existing.overrideValue(), saved.overrideValue());
            // isNull отдельно от value: переход hide→null-override оставляет
            // overrideValue=null в обоих состояниях, но это разные правки
            putIfChanged(diff, "isNull", existing.isNullOverride(), saved.isNullOverride());
            putIfChanged(diff, "hidden", existing.hidden(), saved.hidden());
            putIfChanged(diff, "reason", existing.reason(), saved.reason());
            auditLogService.logUpdate(AUDIT_ENTITY, saved.id(), entity.tableName(),
                    req.entityId(), userId, diff);
        }
        return resp;
    }

    /** Откат правки к импортному значению. */
    @Transactional
    public void delete(String entityTable, UUID entityId, String fieldName, UUID userId, String role) {
        requireAdmin(role, userId);
        OverrideEntity entity = resolve(entityTable);
        FieldOverride existing = overrideRepository.findOne(entity, entityId, fieldName)
                .orElseThrow(CurationException::overrideNotFound);
        overrideRepository.delete(entity, entityId, fieldName);
        auditLogService.logDelete(AUDIT_ENTITY, existing.id(), entity.tableName(),
                entityId, userId, CurationOverrideResponse.from(existing));
    }

    /** Список правок записи — admin-вид «что переопределено/скрыто». */
    @Transactional(readOnly = true)
    public List<CurationOverrideResponse> list(String entityTable, UUID entityId, UUID userId, String role) {
        requireAdmin(role, userId);
        OverrideEntity entity = resolve(entityTable);
        return overrideRepository.findByEntityId(entity, entityId).stream()
                .map(CurationOverrideResponse::from)
                .toList();
    }

    // ── validation ──────────────────────────────────────────────────────────

    private void validateField(OverrideEntity entity, String field, boolean hidden,
                               boolean isNull, boolean hasValue, String value) {
        // синтетические ключи перевода primary-матна (Фаза 6) пишутся ТОЛЬКО через
        // C9 editTranslation по СТАБИЛЬНОМУ hadith_id-ключу. Generic-эндпоинт
        // ключует override по entity_id из тела (= matn.id), создав мёртвую строку,
        // которую applyWithPrimaryTranslation игнорирует (резолвит по hadith_id) →
        // 400, чтобы не плодить молча-битые overrides.
        if (FieldOverride.PRIMARY_TEXT_RU.equals(field) || FieldOverride.PRIMARY_TEXT_EN.equals(field)) {
            throw CurationException.fieldNotEditable(field);
        }
        // синтетические ключи формул передачи звена (Фаза 5.b) пишутся ТОЛЬКО через
        // SanadTransmissionPhraseService по СТАБИЛЬНОМУ (hadith_id, position)-ключу.
        // Generic-эндпоинт ключевал бы по entity_id из тела (= sanad_id, нестабилен
        // на реимпорте) → мёртвая строка, игнорируемая apply (резолв по hadith_id) →
        // 400, чтобы не плодить молча-битые overrides.
        if (FieldOverride.isTransmissionPhraseField(field)) {
            throw CurationException.fieldNotEditable(field);
        }
        if (hidden && !CurationWhitelist.isHideAllowed(entity, field)) {
            throw CurationException.fieldNotEditable(field);
        }
        if (hasValue || isNull) {
            // правка значения: __record__ не несёт значения, поле должно быть editable
            if (FieldOverride.RECORD_FIELD.equals(field) || !CurationWhitelist.isEditable(entity, field)) {
                throw CurationException.fieldNotEditable(field);
            }
            if (hasValue) {
                Set<String> opts = CurationWhitelist.enumOptions(entity, field);
                if (!opts.isEmpty() && !opts.contains(value)) {
                    throw CurationException.invalidEnumValue(field, value);
                }
            }
        }
    }

    /**
     * Существование целевой записи. {@code entity.tableName()} интерполируется
     * в SQL, но это БЕЗОПАСНО: значение всегда — один из 8 hardcoded enum-
     * литералов (raw-строка запроса прошла whitelist {@link #resolve}), а не
     * пользовательский ввод. Все сущности, доступные generic-эндпоинту, имеют
     * суррогатный {@code id}-PK; {@code hd_sanad_narrators} (композитный PK без
     * суррогата) правится ТОЛЬКО через выделенный
     * {@code SanadTransmissionPhraseService} по стабильному {@code (hadith_id,
     * position)}-ключу — в whitelist generic-полей его не осталось (Фаза 5.b),
     * потому сюда он не доходит.
     */
    private void assertEntityExists(OverrideEntity entity, UUID id) {
        Integer found = jdbcTemplate.query(
                "SELECT 1 FROM " + entity.tableName() + " WHERE id = ? LIMIT 1",
                (rs, rn) -> 1, id).stream().findFirst().orElse(null);
        if (found == null) {
            throw CurationException.entityNotFound(entity.tableName(), id);
        }
    }

    private OverrideEntity resolve(String entityTable) {
        return OverrideEntity.fromTableName(entityTable)
                .orElseThrow(() -> CurationException.invalidEntityTable(entityTable));
    }

    private void requireAdmin(String role, UUID userId) {
        if (!UserRole.ADMIN.equals(role)) {
            throw new AdminOnlyException(userId);
        }
    }

    private static void putIfChanged(Map<String, AuditLogService.FieldDiff> diff,
                                     String field, Object oldV, Object newV) {
        if (!Objects.equals(oldV, newV)) {
            diff.put(field, new AuditLogService.FieldDiff(oldV, newV));
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trimToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
