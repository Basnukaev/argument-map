package ru.basnukaev.argumentmap.hadith.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.exception.AdminOnlyException;
import ru.basnukaev.argumentmap.hadith.curation.domain.FieldOverride;
import ru.basnukaev.argumentmap.hadith.curation.domain.OverrideEntity;
import ru.basnukaev.argumentmap.hadith.curation.repository.OverrideRepository;
import ru.basnukaev.argumentmap.hadith.curation.web.CurationException;
import ru.basnukaev.argumentmap.hadith.web.dto.TransmissionPhraseResponse;
import ru.basnukaev.argumentmap.service.AuditLogService;

/**
 * Ручная ADMIN-правка формулы передачи (риваят-глагол حدثنا/عن) звена иснада
 * (курация Фаза 5.b, ADR-065 amendment). Правка живёт в overlay-таблице
 * {@code hd_field_overrides}, НЕ в колонке {@code hd_sanad_narrators}, потому
 * переживает delete-recreate реимпорта alminasa.
 *
 * <p><b>Ключ override (СТАБИЛЬНЫЙ):</b> {@code entity_table='hd_sanad_narrators'},
 * {@code entity_id=hadith_id}, {@code field_name='transmission_phrase@'+position}.
 * Прямой ключ по {@code sanad_id} был бы стёрт (sanad_id = новый UUID на
 * реимпорте после {@code deleteByHadithId}); {@code hadith_id} стабилен, а
 * {@code position} детерминирован. alminasa = 1 sanad на хадис, потому позиция
 * однозначно адресует звено (YAGNI vs {@code @{position}:{narratorExternalId}}).
 *
 * <p>Пишем напрямую через {@link OverrideRepository} (не через generic
 * {@code CurationOverrideService}), т.к. синтетический ключ {@code entity_id=
 * hadith_id} не проходит generic-проверку существования строки
 * {@code hd_sanad_narrators} по id. Существование звена валидируем своим JOIN'ом
 * ({@code hd_sanad_narrators} ⋈ {@code hd_sanads} по {@code sanad_id}). Аудит
 * дублируем вручную тем же {@link AuditLogService} (ADR-043), в той же транзакции.
 *
 * <p>EDIT-only: скрытие формулы НЕ поддержано (пустой риваят-глагол путает).
 */
@Service
public class SanadTransmissionPhraseService {

    private static final Logger log = LoggerFactory.getLogger(SanadTransmissionPhraseService.class);

    private static final String AUDIT_ENTITY = "HD_FIELD_OVERRIDE";

    private final OverrideRepository overrideRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    public SanadTransmissionPhraseService(OverrideRepository overrideRepository,
                                          JdbcTemplate jdbcTemplate,
                                          AuditLogService auditLogService) {
        this.overrideRepository = overrideRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogService = auditLogService;
    }

    /**
     * Правка формулы передачи звена {@code (hadithId, position)}. Цепочка:
     * ADMIN-guard (403) → проверка существования звена (404) → upsert override
     * + audit (одна транзакция).
     *
     * @param hadithId стабильный ключ (PK хадиса)
     * @param position позиция звена (0 = сподвижник)
     * @param phrase   новая формула передачи (триммится; пустая → 400 на @Valid)
     * @param userId   текущий пользователь (для 403-detail + edited_by)
     * @param role     роль текущего пользователя (UserRole.*)
     * @return сохранённое EFFECTIVE-значение
     * @throws AdminOnlyException 403 — не ADMIN
     * @throws CurationException  404 — звена {@code (hadithId, position)} нет
     */
    @Transactional
    public TransmissionPhraseResponse editTransmissionPhrase(UUID hadithId, int position,
                                                             String phrase, UUID userId, String role) {
        if (!UserRole.ADMIN.equals(role)) {
            throw new AdminOnlyException(userId);
        }

        String trimmed = phrase == null ? "" : phrase.trim();
        if (trimmed.isBlank()) {
            // @NotNull/@Size ловят на @Valid, но защищаемся от строки из пробелов.
            throw CurationException.emptyOverride();
        }

        assertLinkExists(hadithId, position);

        String fieldName = FieldOverride.transmissionPhraseField(position);
        upsertPhraseOverride(hadithId, fieldName, trimmed, userId);

        log.info("Правка формулы передачи звена хадиса {} позиция {} → overlay {} ({} симв.)",
                hadithId, position, fieldName, trimmed.length());

        return new TransmissionPhraseResponse(hadithId, position, trimmed);
    }

    /**
     * Существование звена {@code (hadithId, position)}: JOIN
     * {@code hd_sanad_narrators} ⋈ {@code hd_sanads} по {@code sanad_id}.
     * Нет звена → 404 (мис-keyed правка не создаёт мёртвый override).
     */
    private void assertLinkExists(UUID hadithId, int position) {
        Integer found = jdbcTemplate.query(
                "SELECT 1 FROM hd_sanad_narrators sn "
                        + "JOIN hd_sanads s ON s.id = sn.sanad_id "
                        + "WHERE s.hadith_id = ? AND sn.position = ? LIMIT 1",
                (rs, rn) -> 1, hadithId, position).stream().findFirst().orElse(null);
        if (found == null) {
            throw CurationException.entityNotFound(
                    OverrideEntity.HD_SANAD_NARRATORS.tableName(), hadithId);
        }
    }

    /**
     * Upsert override формулы + audit_log в той же транзакции (ADR-043
     * consistency). Override всегда на {@code hd_sanad_narrators}.
     */
    private void upsertPhraseOverride(UUID hadithId, String fieldName, String phrase, UUID userId) {
        FieldOverride existing = overrideRepository
                .findOne(OverrideEntity.HD_SANAD_NARRATORS, hadithId, fieldName).orElse(null);
        FieldOverride toSave = new FieldOverride(
                existing != null ? existing.id() : UUID.randomUUID(),
                OverrideEntity.HD_SANAD_NARRATORS.tableName(), hadithId, fieldName,
                phrase, false, false, userId, Instant.now(), null);
        FieldOverride saved = overrideRepository.upsert(toSave);

        if (existing == null) {
            auditLogService.logCreate(AUDIT_ENTITY, saved.id(),
                    OverrideEntity.HD_SANAD_NARRATORS.tableName(), hadithId, userId,
                    Map.of("value", phrase, "field", fieldName));
        } else {
            Map<String, AuditLogService.FieldDiff> diff = new LinkedHashMap<>();
            diff.put("value", new AuditLogService.FieldDiff(existing.overrideValue(), phrase));
            auditLogService.logUpdate(AUDIT_ENTITY, saved.id(),
                    OverrideEntity.HD_SANAD_NARRATORS.tableName(), hadithId, userId, diff);
        }
    }
}
