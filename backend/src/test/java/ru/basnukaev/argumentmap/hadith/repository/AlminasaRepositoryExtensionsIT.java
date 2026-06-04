package ru.basnukaev.argumentmap.hadith.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.hadith.domain.Collection;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.HadithCrossref;
import ru.basnukaev.argumentmap.hadith.domain.Matn;
import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.NarratorRelation;

/**
 * IT новых методов hd_*-репозиториев добавленных для маппера alminasa (план 3, Task 1):
 * update round-trip, deleteByHadithId (Matn), resolveRelatedHadithIds, findUnresolved /
 * updateRelatedNarratorId, findExternalNormalizedNameIds.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AlminasaRepositoryExtensionsIT {

    @Autowired CollectionRepository collectionRepository;
    @Autowired HadithRepository hadithRepository;
    @Autowired NarratorRepository narratorRepository;
    @Autowired MatnRepository matnRepository;
    @Autowired HadithCrossrefRepository crossrefRepository;
    @Autowired NarratorRelationRepository relationRepository;

    // ── вспомогательные фабрики ───────────────────────────────────────────────

    /** Postgres timestamptz хранит микросекунды — наносы Instant.now() не переживают round-trip. */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private Collection savedCollection(String slug) {
        UUID id = UUID.randomUUID();
        // slug varchar(50): короткий суффикс для уникальности вместо полного UUID
        return collectionRepository.save(new Collection(
                id, slug + "-" + id.toString().substring(0, 8),
                "اسم", null, null, null, null, "{}", now()));
    }

    private Hadith savedHadith(UUID collectionId, String externalId) {
        UUID id = UUID.randomUUID();
        return hadithRepository.save(new Hadith(
                id, collectionId, null, "نص", "CANONICAL", null, "{}",
                now(), "alminasa", externalId, "مرفوع", "باب", null, "نص كامل"));
    }

    private Narrator savedNarrator(String externalId) {
        UUID id = UUID.randomUUID();
        return narratorRepository.save(new Narrator(
                id, null, "اسم الراوي", "اسم الراوي", null, null,
                null, null, null, null, null,
                "UNKNOWN", null, 0, "{}", now(),
                "alminasa", externalId, "الثانية", "ثقة", "ولادة", "وفاة"));
    }

    // ── HadithRepository.update ───────────────────────────────────────────────

    @Test
    void hadith_update_round_trip() {
        Collection col = savedCollection("bukhari-upd");
        Hadith original = savedHadith(col.id(), "146-upd-1");

        // обновляем поля
        Hadith updated = new Hadith(
                original.id(), col.id(), 42, "نص معدّل", "VARIANT",
                null, "{\"v\":2}", original.createdAt(),
                "alminasa", "146-upd-1", "موقوف", "باب معدّل", "فصل", "نص كامل معدّل");
        hadithRepository.update(updated);

        Hadith found = hadithRepository.findById(original.id()).orElseThrow();
        assertThat(found.primaryNumber()).isEqualTo(42);
        assertThat(found.normalizedMatn()).isEqualTo("نص معدّل");
        assertThat(found.status()).isEqualTo("VARIANT");
        assertThat(found.hadithType()).isEqualTo("موقوف");
        assertThat(found.chapterAr()).isEqualTo("باب معدّل");
        assertThat(found.subChapterAr()).isEqualTo("فصل");
        assertThat(found.fullTextAr()).isEqualTo("نص كامل معدّل");
        // id и created_at не изменились
        assertThat(found.id()).isEqualTo(original.id());
        assertThat(found.createdAt()).isEqualTo(original.createdAt());
    }

    // ── NarratorRepository.update ─────────────────────────────────────────────

    @Test
    void narrator_update_round_trip() {
        Narrator original = savedNarrator("5719-upd");

        Narrator updated = new Narrator(
                original.id(), null, "اسم جديد", "اسم جديد",
                "أبو يحيى", "الليثي", 50, 94,
                "المدينة", "المدينة", "المدينة",
                "THIQA", "حافظ", 10, "{\"meta\":1}", original.createdAt(),
                "alminasa", "5719-upd", "الثالثة", "ثقة ثبت",
                "ولد سنة 50", "توفي سنة 94");
        narratorRepository.update(updated);

        Narrator found = narratorRepository.findById(original.id()).orElseThrow();
        assertThat(found.nameAr()).isEqualTo("اسم جديد");
        assertThat(found.kunya()).isEqualTo("أبو يحيى");
        assertThat(found.yearBirthHijri()).isEqualTo(50);
        assertThat(found.yearDeathHijri()).isEqualTo(94);
        assertThat(found.reliabilityGrade()).isEqualTo("THIQA");
        assertThat(found.tabaqa()).isEqualTo("الثالثة");
        assertThat(found.gradeText()).isEqualTo("ثقة ثبت");
        assertThat(found.bornOnText()).isEqualTo("ولد سنة 50");
        assertThat(found.diedOnText()).isEqualTo("توفي سنة 94");
        // id и created_at не изменились
        assertThat(found.id()).isEqualTo(original.id());
        assertThat(found.createdAt()).isEqualTo(original.createdAt());
    }

    // ── MatnRepository.deleteByHadithId ──────────────────────────────────────

    @Test
    void matn_deleteByHadithId_удаляет_все_матны_хадиса() {
        Collection col = savedCollection("bukhari-matn");
        Hadith h = savedHadith(col.id(), "146-matn-1");

        matnRepository.save(new Matn(
                UUID.randomUUID(), h.id(), "نص المتن الأول", "نص المتن الأول",
                null, null, col.id(), 1, 6, 1, true, null, "{}", Instant.now()));
        matnRepository.save(new Matn(
                UUID.randomUUID(), h.id(), "نص المتن الثاني", "نص المتن الثاني",
                null, null, col.id(), 2, 7, 1, false, null, "{}", Instant.now()));

        assertThat(matnRepository.findByHadithId(h.id())).hasSize(2);

        matnRepository.deleteByHadithId(h.id());

        assertThat(matnRepository.findByHadithId(h.id())).isEmpty();
    }

    @Test
    void matn_deleteByHadithId_идемпотентен_при_пустой_таблице() {
        Collection col = savedCollection("bukhari-matn-empty");
        Hadith h = savedHadith(col.id(), "146-matn-empty");

        // хадис без матнов — не должно бросить исключение
        matnRepository.deleteByHadithId(h.id());
        assertThat(matnRepository.findByHadithId(h.id())).isEmpty();
    }

    // ── HadithCrossrefRepository.resolveRelatedHadithIds ─────────────────────

    @Test
    void crossref_resolve_позитив_заполняет_fk() {
        Collection col = savedCollection("bukhari-cr");
        Hadith h146 = savedHadith(col.id(), "146-cr-1");
        Hadith h158 = savedHadith(col.id(), "158-cr-1");

        // crossref h146 → external_id "158-cr-1", related_hadith_id пока NULL
        crossrefRepository.save(new HadithCrossref(
                UUID.randomUUID(), h146.id(), "158-cr-1", null, "TARIQ", null, Instant.now()));

        int affected = crossrefRepository.resolveRelatedHadithIds();

        assertThat(affected).isEqualTo(1);
        HadithCrossref resolved = crossrefRepository.findByHadithId(h146.id()).get(0);
        assertThat(resolved.relatedHadithId()).isEqualTo(h158.id());
    }

    @Test
    void crossref_resolve_не_трогает_уже_заполненный_fk() {
        Collection col = savedCollection("bukhari-cr2");
        Hadith h = savedHadith(col.id(), "146-cr-2");
        Hadith other = savedHadith(col.id(), "158-cr-2");

        // FK уже заполнен
        crossrefRepository.save(new HadithCrossref(
                UUID.randomUUID(), h.id(), "158-cr-2", other.id(), "TARIQ", null, Instant.now()));

        int affected = crossrefRepository.resolveRelatedHadithIds();

        // ноль затронутых — уже заполнен
        assertThat(affected).isEqualTo(0);
    }

    @Test
    void crossref_resolve_не_трогает_другой_external_source() {
        Collection col = savedCollection("bukhari-cr3");
        Hadith h = savedHadith(col.id(), "146-cr-3");

        // хадис без alminasa external_source — не должен резолвиться
        UUID otherId = UUID.randomUUID();
        hadithRepository.save(new Hadith(
                otherId, col.id(), null, "n", "VARIANT", null, "{}", Instant.now(),
                "sunnah", "158-cr-3-sunnah", null, null, null, null));

        crossrefRepository.save(new HadithCrossref(
                UUID.randomUUID(), h.id(), "158-cr-3-sunnah", null, "TARIQ", null, Instant.now()));

        int affected = crossrefRepository.resolveRelatedHadithIds();

        assertThat(affected).isEqualTo(0);
        assertThat(crossrefRepository.findByHadithId(h.id()).get(0).relatedHadithId()).isNull();
    }

    // ── NarratorRelationRepository.findUnresolved / updateRelatedNarratorId ──

    @Test
    void narratorRelation_findUnresolved_и_updateRelatedNarratorId() {
        Narrator narrator = savedNarrator("5000-rel");
        Narrator related = savedNarrator("5001-rel");

        NarratorRelation rel = relationRepository.save(new NarratorRelation(
                UUID.randomUUID(), narrator.id(), null, "الزهري", "STUDENT", 24, Instant.now()));

        // до резолва — видна в unresolved
        List<NarratorRelation> unresolved = relationRepository.findUnresolved(10, 0);
        assertThat(unresolved).extracting(NarratorRelation::id).contains(rel.id());

        // резолвим
        relationRepository.updateRelatedNarratorId(rel.id(), related.id());

        // после резолва — не видна в unresolved
        List<NarratorRelation> afterResolve = relationRepository.findUnresolved(10, 0);
        assertThat(afterResolve).extracting(NarratorRelation::id).doesNotContain(rel.id());

        // FK заполнен
        NarratorRelation loaded = relationRepository.findByNarratorId(narrator.id()).get(0);
        assertThat(loaded.relatedNarratorId()).isEqualTo(related.id());
    }

    @Test
    void narratorRelation_findUnresolved_пагинация_offset() {
        Narrator narrator = savedNarrator("5010-rel");

        // три несрезолвленные связи
        for (int i = 0; i < 3; i++) {
            relationRepository.save(new NarratorRelation(
                    UUID.randomUUID(), narrator.id(), null,
                    "اسم " + i, "SCHOLAR", i, Instant.now()));
        }

        List<NarratorRelation> page1 = relationRepository.findUnresolved(2, 0);
        List<NarratorRelation> page2 = relationRepository.findUnresolved(2, 2);

        assertThat(page1).hasSize(2);
        assertThat(page2).hasSize(1);
        // нет пересечений
        assertThat(page1).extracting(NarratorRelation::id)
                .doesNotContainAnyElementsOf(
                        page2.stream().map(NarratorRelation::id).toList());
    }

    // ── NarratorRepository.findExternalNormalizedNameIds ─────────────────────

    @Test
    void narrator_findExternalNormalizedNameIds_группирует_омонимы() {
        // два рави с одинаковым нормализованным именем (омонимы)
        UUID id1 = UUID.randomUUID();
        narratorRepository.save(new Narrator(
                id1, null, "الزهري", "الزهري", null, null, null, null,
                null, null, null, "UNKNOWN", null, 0, "{}", Instant.now(),
                "alminasa", "9001", null, null, null, null));
        UUID id2 = UUID.randomUUID();
        narratorRepository.save(new Narrator(
                id2, null, "الزهري", "الزهري", null, null, null, null,
                null, null, null, "UNKNOWN", null, 0, "{}", Instant.now(),
                "alminasa", "9002", null, null, null, null));
        // рави из другого источника — не должен попасть
        UUID id3 = UUID.randomUUID();
        narratorRepository.save(new Narrator(
                id3, null, "الزهري", "الزهري", null, null, null, null,
                null, null, null, "UNKNOWN", null, 0, "{}", Instant.now(),
                "sunnah", "9003", null, null, null, null));

        Map<String, List<UUID>> map = narratorRepository.findExternalNormalizedNameIds();

        assertThat(map).containsKey("الزهري");
        // оба alminasa-рави в списке, sunnah — нет
        assertThat(map.get("الزهري")).containsExactlyInAnyOrder(id1, id2);
        assertThat(map.get("الزهري")).doesNotContain(id3);
    }

    @Test
    void narrator_findExternalNormalizedNameIds_единственный_кандидат() {
        UUID id = UUID.randomUUID();
        narratorRepository.save(new Narrator(
                id, null, "مالك بن أنس", "مالك بن انس", null, null, null, 179,
                null, null, null, "UNKNOWN", null, 0, "{}", Instant.now(),
                "alminasa", "19-001", null, null, null, null));

        Map<String, List<UUID>> map = narratorRepository.findExternalNormalizedNameIds();

        assertThat(map).containsKey("مالك بن انس");
        assertThat(map.get("مالك بن انس")).containsExactly(id);
    }
}
