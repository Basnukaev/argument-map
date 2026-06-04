package ru.basnukaev.argumentmap.hadith.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.HadithStatus;
import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.NarratorRelation;
import ru.basnukaev.argumentmap.hadith.domain.NarratorReliability;
import ru.basnukaev.argumentmap.hadith.domain.Sanad;
import ru.basnukaev.argumentmap.hadith.domain.SanadNarrator;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRelationRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRepository;
import ru.basnukaev.argumentmap.hadith.repository.SanadRepository;

/**
 * IT для NarratorController. Vision 49d Section 2.6 Phase 1.b smoke.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class NarratorControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private NarratorRepository narratorRepository;
    @Autowired private HadithRepository hadithRepository;
    @Autowired private SanadRepository sanadRepository;
    @Autowired private NarratorRelationRepository relationRepository;

    private UUID id1;
    private UUID id2;

    @BeforeEach
    void setUp() {
        // n1 — полный (alminasa) конструктор: deathPlace/tabaqa/gradeText/born/died.
        Narrator n1 = new Narrator(
                UUID.randomUUID(), null, "أبو هريرة", "abu hurayrah",
                "أبو هريرة", null, null, 59,
                "اليمن", "المدينة المنورة", "المدينة",
                NarratorReliability.THIQA, "Самый плодовитый передатчик",
                0, null, Instant.now(),
                "alminasa", "rawy-1", "الطبقة الأولى", "ثقة حافظ",
                "ولد قبل الهجرة", "توفي سنة 59 هـ"
        );
        narratorRepository.save(n1);
        id1 = n1.id();

        Narrator n2 = new Narrator(
                UUID.randomUUID(), null, "مالك بن أنس", "malik ibn anas",
                "أبو عبد الله", null, 93, 179,
                "المدينة", "المدينة", "المدينة المنورة",
                NarratorReliability.THIQA, "Имам دار الهجرة",
                0, null, Instant.now()
        );
        narratorRepository.save(n2);
        id2 = n2.id();
    }

    @Test
    void GET_list_returnsAllNarrators() throws Exception {
        mockMvc.perform(get("/api/v1/hadith/narrators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void GET_one_returnsNarrator() throws Exception {
        mockMvc.perform(get("/api/v1/hadith/narrators/{id}", id1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id1.toString()))
                .andExpect(jsonPath("$.reliabilityGrade").value("THIQA"));
    }

    @Test
    void GET_one_returnsAlminasaEnrichmentAndRelations() throws Exception {
        // Сеть передатчиков: один STUDENT (резолвлен в id2) + один SCHOLAR (нерезолвлен).
        relationRepository.save(new NarratorRelation(
                UUID.randomUUID(), id1, id2, "مالك بن أنس", "STUDENT", 42, Instant.now()));
        relationRepository.save(new NarratorRelation(
                UUID.randomUUID(), id1, null, "ابن شهاب الزهري", "SCHOLAR", 17, Instant.now()));

        mockMvc.perform(get("/api/v1/hadith/narrators/{id}", id1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tabaqa").value("الطبقة الأولى"))
                .andExpect(jsonPath("$.gradeText").value("ثقة حافظ"))
                .andExpect(jsonPath("$.bornOnText").value("ولد قبل الهجرة"))
                .andExpect(jsonPath("$.diedOnText").value("توفي سنة 59 هـ"))
                .andExpect(jsonPath("$.deathPlace").value("المدينة المنورة"))
                .andExpect(jsonPath("$.relations").isArray())
                .andExpect(jsonPath("$.relations.length()").value(2))
                .andExpect(jsonPath("$.relations[?(@.role == 'STUDENT')].relatedNarratorId")
                        .value(org.hamcrest.Matchers.hasItem(id2.toString())))
                .andExpect(jsonPath("$.relations[?(@.role == 'STUDENT')].cnt")
                        .value(org.hamcrest.Matchers.hasItem(42)))
                .andExpect(jsonPath("$.relations[?(@.role == 'SCHOLAR')].relatedNarratorId")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.relations[?(@.role == 'SCHOLAR')].relatedName")
                        .value(org.hamcrest.Matchers.hasItem("ابن شهاب الزهري")));
    }

    @Test
    void GET_list_doesNotBuildRelations() throws Exception {
        // list-путь: relations = null (без N+1); enrichment-поля проходят.
        mockMvc.perform(get("/api/v1/hadith/narrators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].relations")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())));
    }

    @Test
    void GET_nonExistent_returns404() throws Exception {
        UUID ghost = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/hadith/narrators/{id}", ghost))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.containsString("narrator-not-found")));
    }

    @Test
    void GET_transmitted_returnsHadithsByNarrator() throws Exception {
        Instant now = Instant.now();
        Hadith h = new Hadith(UUID.randomUUID(), null, 1, "إنما الأعمال بالنيات",
                HadithStatus.CANONICAL, null, null, now);
        hadithRepository.save(h);
        Sanad s = new Sanad(UUID.randomUUID(), h.id(), "SAHIH", null, null, true, null, now);
        sanadRepository.save(s);
        sanadRepository.saveNarratorLink(new SanadNarrator(s.id(), 0, id1, "سمعت"));

        // id1 передавал этот хадис
        mockMvc.perform(get("/api/v1/hadith/narrators/{id}/transmitted", id1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(h.id().toString()));

        // id2 не встречается ни в одном sanad'е
        mockMvc.perform(get("/api/v1/hadith/narrators/{id}/transmitted", id2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void GET_transmitted_nonExistentNarrator_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/hadith/narrators/{id}/transmitted", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.containsString("narrator-not-found")));
    }
}
