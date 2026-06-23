package ru.basnukaev.argumentmap.hadith.curation.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.HadithRuling;
import ru.basnukaev.argumentmap.hadith.domain.HadithStatus;
import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.NarratorCommentary;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithRulingRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorCommentaryRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRepository;

import java.util.List;

/**
 * IT record-level hide (Фаза 4, §4.2/§4.3): скрытая запись вырезана из detail
 * для читателя, но приходит ADMIN'у с {@code hiddenByAdmin}+причиной (reveal).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CurationHideIT {

    private static final String OVERRIDES = "/api/v1/admin/curation/overrides";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private HadithRepository hadithRepository;
    @Autowired private HadithRulingRepository rulingRepository;
    @Autowired private NarratorRepository narratorRepository;
    @Autowired private NarratorCommentaryRepository commentaryRepository;

    private UUID adminId;

    @BeforeEach
    void setUp() {
        adminId = insertUser("admin", UserRole.ADMIN);
    }

    private UUID insertUser(String suffix, String role) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, username, email, role) VALUES (?, ?, ?, ?)",
                id, suffix + "-" + id, id + "-" + suffix + "@t.com", role);
        return id;
    }

    private void hideRecord(String table, UUID entityId) throws Exception {
        mockMvc.perform(put(OVERRIDES).header("X-User-Id", adminId.toString())
                        .contentType("application/json")
                        .content("{\"entityTable\":\"" + table + "\",\"entityId\":\"" + entityId
                                + "\",\"fieldName\":\"__record__\",\"hidden\":true,"
                                + "\"reason\":\"модерация\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void hiddenRuling_cutForGuest_revealedForAdmin() throws Exception {
        UUID hid = UUID.randomUUID();
        hadithRepository.save(new Hadith(hid, null, 1, "n", HadithStatus.CANONICAL, null, null, Instant.now()));
        UUID hidden = UUID.randomUUID();
        UUID visible = UUID.randomUUID();
        rulingRepository.save(new HadithRuling(hidden, hid, "одиозный", 300, "صحيح-скрытый",
                "كتاب", 1, 1, "{}", Instant.now()));
        rulingRepository.save(new HadithRuling(visible, hid, "البخاري", 256, "صحيح-видимый",
                "كتاب", 1, 1, "{}", Instant.now()));

        hideRecord("hd_rulings", hidden);

        // гость — скрытый вердикт вырезан, виден только один
        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}/detail", hid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rulings.length()").value(1))
                .andExpect(jsonPath("$.rulings[*].rulingText", contains("صحيح-видимый")));

        // ADMIN — оба, скрытый помечен hiddenByAdmin + причина
        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}/detail", hid)
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rulings.length()").value(2))
                .andExpect(jsonPath("$.rulings[?(@.hiddenByAdmin==true)].rulingText",
                        hasItem("صحيح-скрытый")))
                .andExpect(jsonPath("$.rulings[?(@.hiddenByAdmin==true)].hideReason",
                        hasItem("модерация")));
    }

    @Test
    void hiddenCommentary_cutForGuest_revealedForAdmin() throws Exception {
        UUID nid = UUID.randomUUID();
        narratorRepository.save(new Narrator(nid, null, "راو", "راو", null, null, null, null,
                null, null, null, "UNKNOWN", null, 0, "{}", Instant.now()));
        UUID hiddenC = UUID.randomUUID();
        commentaryRepository.save(new NarratorCommentary(hiddenC, nid, "заблудший", 250,
                "كتاب", "автор", 1, 1, List.of("ضعيف-скрытый"), "{}", Instant.now()));
        commentaryRepository.save(new NarratorCommentary(UUID.randomUUID(), nid, "ابن حجر", 852,
                "تقريب", "автор", 1, 1, List.of("ثقة-видимый"), "{}", Instant.now()));

        hideRecord("hd_narrator_commentaries", hiddenC);

        mockMvc.perform(get("/api/v1/hadith/narrators/{id}", nid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commentaries.length()").value(1))
                .andExpect(jsonPath("$.commentaries[0].commenter").value("ابن حجر"));

        mockMvc.perform(get("/api/v1/hadith/narrators/{id}", nid)
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commentaries.length()").value(2))
                .andExpect(jsonPath("$.commentaries[?(@.hiddenByAdmin==true)].commenter",
                        hasItem("заблудший")));
    }

    @Test
    void recordHideOnNonHideableEntity_returns400() throws Exception {
        // hd_hadiths не поддерживает record-hide (§5) → 400
        UUID hid = UUID.randomUUID();
        hadithRepository.save(new Hadith(hid, null, 1, "n", HadithStatus.CANONICAL, null, null, Instant.now()));
        mockMvc.perform(put(OVERRIDES).header("X-User-Id", adminId.toString())
                        .contentType("application/json")
                        .content("{\"entityTable\":\"hd_hadiths\",\"entityId\":\"" + hid
                                + "\",\"fieldName\":\"__record__\",\"hidden\":true,\"reason\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type",
                        org.hamcrest.Matchers.containsString("curation-field-not-editable")));
    }
}
