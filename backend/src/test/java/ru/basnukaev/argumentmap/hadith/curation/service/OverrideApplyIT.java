package ru.basnukaev.argumentmap.hadith.curation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.hadith.curation.domain.FieldOverride;
import ru.basnukaev.argumentmap.hadith.curation.domain.OverrideEntity;
import ru.basnukaev.argumentmap.hadith.curation.repository.OverrideRepository;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRepository;

/**
 * IT apply-слоя (Фаза 2): override виден в display-методах
 * ({@code findById}), но импортный путь ({@code findByExternalId},
 * {@code findByNameArNormalized}) отдаёт RAW — правка переживёт реимпорт.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OverrideApplyIT {

    @Autowired HadithRepository hadithRepository;
    @Autowired NarratorRepository narratorRepository;
    @Autowired OverrideRepository overrideRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                id, "u-" + id, id + "@e.com");
        return id;
    }

    @Test
    void hadithAuthenticityOverride_effectiveInFindById_rawInImportLookup() {
        UUID user = insertUser();
        UUID hid = UUID.randomUUID();
        hadithRepository.save(new Hadith(
                hid, null, 5, "نص", "VARIANT", null, "{}", Instant.now(),
                "alminasa", "ext-curation-99", "مرفوع", "باب", null, "<a>fulltext</a>", null));

        overrideRepository.upsert(new FieldOverride(UUID.randomUUID(),
                OverrideEntity.HD_HADITHS.tableName(), hid, "authenticity",
                "SAHIH", false, false, user, Instant.now(), "фикс импорта"));

        // display-путь — EFFECTIVE
        Hadith effective = hadithRepository.findById(hid).orElseThrow();
        assertThat(effective.authenticity()).isEqualTo("SAHIH");
        assertThat(effective.fullTextAr()).isEqualTo("<a>fulltext</a>");   // первоисточник цел

        // import idempotency lookup — RAW (иначе правка затёрлась бы в импорт)
        Hadith raw = hadithRepository.findByExternalId("alminasa", "ext-curation-99").orElseThrow();
        assertThat(raw.authenticity()).isNull();
    }

    @Test
    void narratorReliabilityOverride_effectiveInFindById_rawInDedupLookup() {
        UUID user = insertUser();
        UUID nid = UUID.randomUUID();
        String uniqueName = "راوي-اختبار-" + nid;
        narratorRepository.save(new Narrator(
                nid, null, uniqueName, uniqueName, null, null, null, null,
                null, null, null, "UNKNOWN", null, 0, "{}", Instant.now()));

        overrideRepository.upsert(new FieldOverride(UUID.randomUUID(),
                OverrideEntity.HD_NARRATORS.tableName(), nid, "reliability_grade",
                "THIQA", false, false, user, Instant.now(), "у Ибн Хаджара ثقة"));

        assertThat(narratorRepository.findById(nid).orElseThrow().reliabilityGrade())
                .isEqualTo("THIQA");
        // dedup-путь импорта — RAW
        assertThat(narratorRepository.findByNameArNormalized(uniqueName).orElseThrow().reliabilityGrade())
                .isEqualTo("UNKNOWN");
    }

    @Test
    void noOverride_returnsBaseRow() {
        UUID hid = UUID.randomUUID();
        hadithRepository.save(new Hadith(
                hid, null, 6, "نص2", "CANONICAL", null, "{}", Instant.now()));
        assertThat(hadithRepository.findById(hid).orElseThrow().status()).isEqualTo("CANONICAL");
    }
}
