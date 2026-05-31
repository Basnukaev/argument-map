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
import ru.basnukaev.argumentmap.hadith.domain.Matn;
import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.NarratorReliability;
import ru.basnukaev.argumentmap.hadith.domain.Sanad;
import ru.basnukaev.argumentmap.hadith.domain.SanadNarrator;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.MatnRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRepository;
import ru.basnukaev.argumentmap.hadith.repository.SanadRepository;

/**
 * IT для HadithController. Vision 49d Section 2.6 Phase 1.f/g.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class HadithControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private HadithRepository hadithRepository;
    @Autowired private NarratorRepository narratorRepository;
    @Autowired private SanadRepository sanadRepository;
    @Autowired private MatnRepository matnRepository;

    private UUID hadithId;
    private UUID narratorId;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();

        Narrator narrator = new Narrator(
                UUID.randomUUID(), null, "أبو هريرة", "abu hurayrah",
                null, null, null, 59, null, null, null,
                NarratorReliability.THIQA, null, 0, null, now
        );
        narratorRepository.save(narrator);
        narratorId = narrator.id();

        Hadith hadith = new Hadith(
                UUID.randomUUID(), null, 1,
                "إنما الأعمال بالنيات",
                HadithStatus.CANONICAL, null, null, now
        );
        hadithRepository.save(hadith);
        hadithId = hadith.id();

        Sanad sanad = new Sanad(
                UUID.randomUUID(), hadithId,
                "SAHIH", null, null, true, null, now
        );
        sanadRepository.save(sanad);
        sanadRepository.saveNarratorLink(
                new SanadNarrator(sanad.id(), 0, narratorId, "سمعت")
        );

        Matn matn = new Matn(
                UUID.randomUUID(), hadithId,
                "إنما الأعمال بالنيات", "innama al-amal",
                "Дела по намерениям", null,
                null, 1, null, null, true, null, null, now
        );
        matnRepository.save(matn);
    }

    @Test
    void GET_list_returnsHadiths() throws Exception {
        mockMvc.perform(get("/api/v1/hadith/hadiths"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void GET_one_returnsHadith() throws Exception {
        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}", hadithId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(hadithId.toString()))
                .andExpect(jsonPath("$.status").value("CANONICAL"));
    }

    @Test
    void GET_nonExistent_returns404() throws Exception {
        UUID ghost = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}", ghost))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.containsString("hadith-not-found")));
    }

    @Test
    void GET_detail_returnsBundledResponse() throws Exception {
        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}/detail", hadithId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(hadithId.toString()))
                .andExpect(jsonPath("$.sanads").isArray())
                .andExpect(jsonPath("$.sanads.length()").value(1))
                .andExpect(jsonPath("$.sanads[0].chainGrade").value("SAHIH"))
                .andExpect(jsonPath("$.sanads[0].narrators.length()").value(1))
                .andExpect(jsonPath("$.sanads[0].narrators[0].narratorId").value(narratorId.toString()))
                .andExpect(jsonPath("$.sanads[0].narrators[0].position").value(0))
                .andExpect(jsonPath("$.matns").isArray())
                .andExpect(jsonPath("$.matns.length()").value(1))
                .andExpect(jsonPath("$.matns[0].isPrimary").value(true));
    }

    @Test
    void GET_filterByStatus() throws Exception {
        mockMvc.perform(get("/api/v1/hadith/hadiths").param("status", "CANONICAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("CANONICAL"));
    }

    @Test
    void GET_sanadGraph_returnsProphetRootedGraph() throws Exception {
        // setUp создаёт 1 sanad с 1 narrator (position 0) - граф = Пророк ﷺ
        // (синтетический корень) + 1 узел-сподвижник, соединённые 1 ребром.
        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}/sanad-graph", hadithId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hadithId").value(hadithId.toString()))
                .andExpect(jsonPath("$.nodes.length()").value(2))
                .andExpect(jsonPath("$.edges.length()").value(1))
                .andExpect(jsonPath("$.edges[0].source").value("prophet"))
                .andExpect(jsonPath("$.edges[0].target").value("narrator-" + narratorId))
                .andExpect(jsonPath("$.sanads.length()").value(1));
    }

    @Test
    void GET_sanadGraph_nonExistent_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}/sanad-graph", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.containsString("hadith-not-found")));
    }
}
