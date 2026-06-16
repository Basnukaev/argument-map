package ru.basnukaev.argumentmap.hadith.alminasa.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.hadith.alminasa.api.dto.AlminasaHit;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.AlminasaRows;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmNarratorCommentaryRow;
import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.NarratorCommentary;
import ru.basnukaev.argumentmap.hadith.repository.NarratorCommentaryRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRepository;

/**
 * Round-trip IT narrator-commentary (миграция 76, ADR-061): staging upsert
 * идемпотентен по doc_id + findByNarratorId; доменный save → findByNarratorId
 * → deleteByNarratorId с jsonb-массивом comments. Фикстура
 * {@code s59/narrator-commentary-12.json} (рави 4396, ابن حجر) — round-trip.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AmNarratorCommentaryStagingDaoIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired private AmNarratorCommentaryStagingDao stagingDao;
    @Autowired private NarratorCommentaryRepository commentaryRepository;
    @Autowired private NarratorRepository narratorRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    /** Postgres timestamptz хранит микросекунды — наносы Instant.now() не переживают round-trip. */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private Narrator savedNarrator(String externalId) {
        UUID id = UUID.randomUUID();
        return narratorRepository.save(new Narrator(
                id, null, "أبو هريرة", "ابو هريرة", null, null,
                null, null, null, null, null,
                "SAHABI", null, 0, "{}", now(),
                "alminasa", externalId, null, null, null, null));
    }

    /**
     * Единственный хит _msearch-фикстуры. ВАЖНО: эта фикстура обёрнута в
     * {@code response.responses[0].hits.hits[0]} (НЕ top-level {@code responses}
     * как hadith-commentary-12). _source.id отсутствует (includes ограничивал).
     */
    private static AlminasaHit fixtureHit() throws IOException {
        try (InputStream in = AmNarratorCommentaryStagingDaoIT.class
                .getResourceAsStream("/alminasa/s59/narrator-commentary-12.json")) {
            JsonNode hit = MAPPER.readTree(in)
                    .path("response").path("responses").get(0).path("hits").path("hits").get(0);
            return new AlminasaHit(hit.path("_id").asText(), hit.path("_source"), hit.path("sort"));
        }
    }

    @Test
    void staging_upsert_идемпотентен_по_doc_id_и_findByNarratorId() {
        stagingDao.upsertAll(List.of(
                new AmNarratorCommentaryRow("jKK-hpEBXUur4f6nEYAA", 4396, "ابن حجر",
                        "تقريب التهذيب", "{\"commenter\":\"ابن حجر\",\"book\":\"تقريب التهذيب\"}"),
                new AmNarratorCommentaryRow("OTHER-DOC-ID", 4396, "الذهبي",
                        "ميزان الاعتدال", "{\"commenter\":\"الذهبي\"}")));
        // повтор того же doc_id с другим commenter — строка одна, поле новое
        stagingDao.upsertAll(List.of(
                new AmNarratorCommentaryRow("jKK-hpEBXUur4f6nEYAA", 4396, "ابن حجر العسقلاني",
                        "تقريب التهذيب", "{\"commenter\":\"ابن حجر العسقلاني\"}")));

        assertThat(stagingDao.count()).isEqualTo(2);
        List<AmNarratorCommentaryRow> rows = stagingDao.findByNarratorId(4396);
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(AmNarratorCommentaryRow::commenter)
                .containsExactlyInAnyOrder("ابن حجر العسقلاني", "الذهبي");
        // другой рави — пусто
        assertThat(stagingDao.findByNarratorId(9999)).isEmpty();
    }

    @Test
    void staging_фикстура_через_парсер_с_инжектированным_id() throws IOException {
        // _source фикстуры снят без id (includes ограничивал); инжектим id рави
        // в _source — так выглядит live-док (terms:{id} несёт _source.id).
        AlminasaHit raw = fixtureHit();
        com.fasterxml.jackson.databind.node.ObjectNode src =
                ((com.fasterxml.jackson.databind.node.ObjectNode) raw.source()).put("id", "4396");
        AmNarratorCommentaryRow row = AlminasaRows.fromNarratorCommentaryHit(
                new AlminasaHit(raw.id(), src, raw.sort()));

        assertThat(row.docId()).isEqualTo("jKK-hpEBXUur4f6nEYAA");
        assertThat(row.narratorId()).isEqualTo(4396);
        assertThat(row.commenter()).isEqualTo("ابن حجر");
        assertThat(row.book()).isEqualTo("تقريب التهذيب");

        stagingDao.upsertAll(List.of(row));
        assertThat(stagingDao.findByNarratorId(4396)).singleElement()
                .satisfies(r -> assertThat(r.docId()).isEqualTo("jKK-hpEBXUur4f6nEYAA"));
        // raw — валидный jsonb: comments-массив доступен
        assertThat(jdbcTemplate.queryForObject(
                "SELECT raw->'comments'->>0 FROM am_staging_narrator_commentary "
                        + "WHERE doc_id = 'jKK-hpEBXUur4f6nEYAA'", String.class))
                .isEqualTo("الصحابي الجليل ، حافظ الصحابة");
    }

    @Test
    void domain_save_findByNarratorId_delete_round_trip() {
        Narrator narrator = savedNarrator("4396-rt");
        UUID id = UUID.randomUUID();
        commentaryRepository.save(new NarratorCommentary(
                id, narrator.id(), "ابن حجر", 852, "تقريب التهذيب",
                "ابن حجر العسقلاني", 1218, 1,
                List.of("الصحابي الجليل ، حافظ الصحابة"), "{\"source\":\"alminasa\"}", now()));

        List<NarratorCommentary> found = commentaryRepository.findByNarratorId(narrator.id());
        assertThat(found).singleElement().satisfies(c -> {
            assertThat(c.commenter()).isEqualTo("ابن حجر");
            assertThat(c.commenterDeathYear()).isEqualTo(852);
            assertThat(c.bookName()).isEqualTo("تقريب التهذيب");
            assertThat(c.page()).isEqualTo(1218);
            assertThat(c.volume()).isEqualTo(1);
            // comments — jsonb-массив строк, не конкатенация
            assertThat(c.comments()).containsExactly("الصحابي الجليل ، حافظ الصحابة");
        });

        commentaryRepository.deleteByNarratorId(narrator.id());
        assertThat(commentaryRepository.findByNarratorId(narrator.id())).isEmpty();
    }

    @Test
    void domain_сортировка_по_году_смерти_критика_nulls_last() {
        Narrator narrator = savedNarrator("4396-sort");
        // три цитаты: год 852, год null, год 748 → ожидаемый порядок 748, 852, null
        commentaryRepository.save(new NarratorCommentary(
                UUID.randomUUID(), narrator.id(), "ابن حجر", 852, "تقريب", null, null, null,
                List.of("ثقة"), null, now()));
        commentaryRepository.save(new NarratorCommentary(
                UUID.randomUUID(), narrator.id(), "مجهول", null, "كتاب", null, null, null,
                List.of("..."), null, now()));
        commentaryRepository.save(new NarratorCommentary(
                UUID.randomUUID(), narrator.id(), "الذهبي", 748, "ميزان", null, null, null,
                List.of("حافظ"), null, now()));

        List<NarratorCommentary> found = commentaryRepository.findByNarratorId(narrator.id());
        assertThat(found).extracting(NarratorCommentary::commenter)
                .containsExactly("الذهبي", "ابن حجر", "مجهول");
    }
}
