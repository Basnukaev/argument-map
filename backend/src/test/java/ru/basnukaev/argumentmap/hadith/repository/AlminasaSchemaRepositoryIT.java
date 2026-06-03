package ru.basnukaev.argumentmap.hadith.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.hadith.domain.Collection;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.HadithCrossref;
import ru.basnukaev.argumentmap.hadith.domain.HadithEdition;
import ru.basnukaev.argumentmap.hadith.domain.HadithExplanation;
import ru.basnukaev.argumentmap.hadith.domain.HadithRuling;
import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.NarratorRelation;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AlminasaSchemaRepositoryIT {

    @Autowired CollectionRepository collectionRepository;
    @Autowired HadithRepository hadithRepository;
    @Autowired NarratorRepository narratorRepository;
    @Autowired HadithEditionRepository editionRepository;
    @Autowired HadithRulingRepository rulingRepository;
    @Autowired HadithExplanationRepository explanationRepository;
    @Autowired HadithCrossrefRepository crossrefRepository;
    @Autowired NarratorRelationRepository relationRepository;

    @Test
    void hadithRoundTripsAlminasaColumns() {
        UUID collectionId = UUID.randomUUID();
        collectionRepository.save(new Collection(
                collectionId, "bukhari-it-" + collectionId, "صحيح البخاري",
                "Sahih al-Bukhari", null, null, 7031, "{}", Instant.now()));

        UUID hadithId = UUID.randomUUID();
        hadithRepository.save(new Hadith(
                hadithId, collectionId, 1, "انما الاعمال بالنيات", "CANONICAL",
                null, "{}", Instant.now(),
                "alminasa", "146-1", "مرفوع", "باب بدء الوحي",
                "باب كيف كان بدء الوحي", "<a class=rawy id=4698>الحميدي</a>"));

        Hadith found = hadithRepository.findByExternalId("alminasa", "146-1").orElseThrow();
        assertThat(found.id()).isEqualTo(hadithId);
        assertThat(found.hadithType()).isEqualTo("مرفوع");
        assertThat(found.chapterAr()).isEqualTo("باب بدء الوحي");
        assertThat(found.subChapterAr()).isEqualTo("باب كيف كان بدء الوحي");
        assertThat(found.fullTextAr()).contains("rawy id=4698");
    }

    @Test
    void narratorRoundTripsAlminasaColumns() {
        UUID narratorId = UUID.randomUUID();
        narratorRepository.save(new Narrator(
                narratorId, null, "علقمة بن وقاص العتواري", "علقمه بن وقاص العتواري",
                "أبو يحيى", null, null, null, "المدينة", "المدينة", "المدينة",
                "THIQA", null, 0, "{}", Instant.now(),
                "alminasa", "5719", "الثانية", "ثقة ثبت",
                "ولد على عهده عهد النبي", "في خلافة عبد الملك بن مروان"));

        Narrator found = narratorRepository.findByExternalId("alminasa", "5719").orElseThrow();
        assertThat(found.id()).isEqualTo(narratorId);
        assertThat(found.tabaqa()).isEqualTo("الثانية");
        assertThat(found.gradeText()).isEqualTo("ثقة ثبت");
        assertThat(found.bornOnText()).contains("النبي");
        assertThat(found.diedOnText()).contains("عبد الملك");
    }

    @Test
    void childTablesInsertAndRead() {
        UUID collectionId = UUID.randomUUID();
        collectionRepository.save(new Collection(
                collectionId, "child-it-" + collectionId, "ص", null, null, null, null,
                "{}", Instant.now()));
        UUID hadithId = UUID.randomUUID();
        hadithRepository.save(new Hadith(
                hadithId, collectionId, 1, "n", "VARIANT", null, "{}", Instant.now()));
        UUID narratorId = UUID.randomUUID();
        narratorRepository.save(new Narrator(
                narratorId, null, "x", "x", null, null, null, null, null, null, null,
                "UNKNOWN", null, 0, "{}", Instant.now()));

        editionRepository.save(new HadithEdition(
                UUID.randomUUID(), hadithId, "دار طوق النجاة", 6, 1));
        rulingRepository.save(new HadithRuling(
                UUID.randomUUID(), hadithId, "البخاري", 256, "أورده في صحيحه",
                "صحيح البخاري", 6, 1, "{}", Instant.now()));
        explanationRepository.save(new HadithExplanation(
                UUID.randomUUID(), hadithId, "SHARH", "فتح الباري", "ابن حجر", 852,
                15, 1, "نص الشرح", "{}", Instant.now()));
        crossrefRepository.save(new HadithCrossref(
                UUID.randomUUID(), hadithId, "146-2356", null, "raw", null, Instant.now()));
        relationRepository.save(new NarratorRelation(
                UUID.randomUUID(), narratorId, null, "الزهري", "STUDENT", 24, Instant.now()));

        assertThat(editionRepository.findByHadithId(hadithId)).singleElement()
                .satisfies(ed -> {
                    assertThat(ed.editionName()).isEqualTo("دار طوق النجاة");
                    assertThat(ed.page()).isEqualTo(6);
                    assertThat(ed.volume()).isEqualTo(1);
                });
        assertThat(rulingRepository.findByHadithId(hadithId)).singleElement()
                .satisfies(r -> assertThat(r.rulerDeathYear()).isEqualTo(256));
        assertThat(explanationRepository.findByHadithId(hadithId)).singleElement()
                .satisfies(e -> assertThat(e.kind()).isEqualTo("SHARH"));
        assertThat(crossrefRepository.findByHadithId(hadithId)).singleElement()
                .satisfies(c -> assertThat(c.relatedExternalId()).isEqualTo("146-2356"));
        assertThat(relationRepository.findByNarratorId(narratorId)).singleElement()
                .satisfies(rel -> assertThat(rel.cnt()).isEqualTo(24));
    }
}
