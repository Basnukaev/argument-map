package ru.basnukaev.argumentmap.hadith.curation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.hadith.curation.domain.FieldOverride;
import ru.basnukaev.argumentmap.hadith.curation.domain.OverrideEntity;

/**
 * IT слоя курации Фазы 1: миграция 78 применяется, CHECK/UNIQUE работают,
 * upsert идемпотентен, батч-{@code findByEntity} скоупится по таблице.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OverrideRepositoryIT {

    @Autowired OverrideRepository repository;
    @Autowired JdbcTemplate jdbcTemplate;

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                id, "u-" + id, id + "@e.com");
        return id;
    }

    private FieldOverride valueOverride(UUID entityId, String field, String value, UUID user) {
        return new FieldOverride(UUID.randomUUID(), OverrideEntity.HD_HADITHS.tableName(),
                entityId, field, value, false, false, user, Instant.now(), "фикс импорта");
    }

    @Test
    void upsertAndFindRoundTrip() {
        UUID user = insertUser();
        UUID hadithId = UUID.randomUUID();

        FieldOverride saved = repository.upsert(valueOverride(hadithId, "authenticity", "SAHIH", user));
        assertThat(saved.id()).isNotNull();

        FieldOverride found = repository
                .findOne(OverrideEntity.HD_HADITHS, hadithId, "authenticity").orElseThrow();
        assertThat(found.overrideValue()).isEqualTo("SAHIH");
        assertThat(found.editedBy()).isEqualTo(user);
        assertThat(found.hidden()).isFalse();
        assertThat(found.reason()).isEqualTo("фикс импорта");
    }

    @Test
    void upsertIsIdempotentByUniqueKey() {
        UUID user = insertUser();
        UUID hadithId = UUID.randomUUID();

        FieldOverride first = repository.upsert(valueOverride(hadithId, "authenticity", "DAIF", user));
        FieldOverride second = repository.upsert(valueOverride(hadithId, "authenticity", "SAHIH", user));

        // та же строка (id сохранился), значение обновилось — не дубль
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(repository.findByEntityId(OverrideEntity.HD_HADITHS, hadithId))
                .singleElement()
                .satisfies(o -> assertThat(o.overrideValue()).isEqualTo("SAHIH"));
    }

    @Test
    void findByEntityBatchScopedToTable() {
        UUID user = insertUser();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        repository.upsert(valueOverride(a, "authenticity", "SAHIH", user));
        repository.upsert(valueOverride(b, "status", "VARIANT", user));
        // та же entity_id a, но другая таблица — не должна попасть в выборку hd_hadiths
        repository.upsert(new FieldOverride(UUID.randomUUID(),
                OverrideEntity.HD_NARRATORS.tableName(), a, "reliability_grade",
                "THIQA", false, false, user, Instant.now(), null));

        List<FieldOverride> batch = repository.findByEntity(OverrideEntity.HD_HADITHS, List.of(a, b));
        assertThat(batch).hasSize(2)
                .allSatisfy(o -> assertThat(o.entityTable())
                        .isEqualTo(OverrideEntity.HD_HADITHS.tableName()));

        assertThat(repository.findByEntity(OverrideEntity.HD_HADITHS, List.of())).isEmpty();
    }

    @Test
    void deleteReturnsAffectedRows() {
        UUID user = insertUser();
        UUID hadithId = UUID.randomUUID();
        repository.upsert(valueOverride(hadithId, "authenticity", "SAHIH", user));

        assertThat(repository.delete(OverrideEntity.HD_HADITHS, hadithId, "authenticity")).isEqualTo(1);
        assertThat(repository.delete(OverrideEntity.HD_HADITHS, hadithId, "authenticity")).isZero();
        assertThat(repository.findOne(OverrideEntity.HD_HADITHS, hadithId, "authenticity")).isEmpty();
    }

    @Test
    void recordHideOverridePersistsWithoutValue() {
        UUID user = insertUser();
        UUID rulingId = UUID.randomUUID();
        // только hidden=true, без override_value — payload-check проходит
        repository.upsert(new FieldOverride(UUID.randomUUID(),
                OverrideEntity.HD_RULINGS.tableName(), rulingId, FieldOverride.RECORD_FIELD,
                null, false, true, user, Instant.now(), "модерация: одиозный вердикт"));

        FieldOverride found = repository
                .findOne(OverrideEntity.HD_RULINGS, rulingId, FieldOverride.RECORD_FIELD).orElseThrow();
        assertThat(found.isRecordHide()).isTrue();
        assertThat(found.overrideValue()).isNull();
        assertThat(found.reason()).isEqualTo("модерация: одиозный вердикт");
    }

    @Test
    void tableCheckRejectsUnknownEntityTable() {
        UUID user = insertUser();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO hd_field_overrides "
                        + "(id, entity_table, entity_id, field_name, override_value, edited_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "lib_books", UUID.randomUUID(), "title", "x", user))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void payloadCheckRejectsEmptyOverride() {
        UUID user = insertUser();
        // ни value, ни is_null_override, ни hidden → CHECK падает
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO hd_field_overrides "
                        + "(id, entity_table, entity_id, field_name, override_value, "
                        + "is_null_override, hidden, edited_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), OverrideEntity.HD_HADITHS.tableName(), UUID.randomUUID(),
                "authenticity", null, false, false, user))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueConstraintBlocksRawDuplicate() {
        UUID user = insertUser();
        UUID hadithId = UUID.randomUUID();
        repository.upsert(valueOverride(hadithId, "authenticity", "SAHIH", user));
        // прямой INSERT того же ключа (мимо ON CONFLICT) — UNIQUE бьёт
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO hd_field_overrides "
                        + "(id, entity_table, entity_id, field_name, override_value, edited_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), OverrideEntity.HD_HADITHS.tableName(), hadithId,
                "authenticity", "HASAN", user))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
