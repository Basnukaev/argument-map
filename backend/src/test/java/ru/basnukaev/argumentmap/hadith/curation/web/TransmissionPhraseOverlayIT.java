package ru.basnukaev.argumentmap.hadith.curation.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
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
import ru.basnukaev.argumentmap.hadith.domain.HadithStatus;
import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.NarratorReliability;
import ru.basnukaev.argumentmap.hadith.domain.Sanad;
import ru.basnukaev.argumentmap.hadith.domain.SanadNarrator;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRepository;
import ru.basnukaev.argumentmap.hadith.repository.SanadRepository;

/**
 * Headline-proof Фазы 5.b (ADR-065 amendment): формула передачи звена иснада
 * ({@code transmission_phrase}), отредактированная через
 * {@code PATCH /sanad-narrators/transmission-phrase}, живёт в overlay под
 * СТАБИЛЬНЫМ ключом {@code (entity_id=hadith_id, field_name='transmission_phrase@'
 * +position)} и потому переживает delete-recreate реимпорта alminasa (новый
 * sanad_id, импортная формула вернулась).
 *
 * <p>Сценарий: PATCH формулы звена позиции k → проверяем (a) записан overlay-row
 * (не колонка), (b) колонка {@code hd_sanad_narrators.transmission_phrase} не
 * тронута, (c) GET detail звено position k показывает курируемую формулу, (d) GET
 * sanad-graph ребро показывает её → симулируем реимпорт (удаляем sanad, создаём
 * новый с другим id и теми же звеньями/позициями) → GET detail/graph СНОВА
 * показывают курируемую формулу (overlay не зависел от sanad_id). ЭТО — proof.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class TransmissionPhraseOverlayIT {

    private static final String DETAIL = "/api/v1/hadith/hadiths/{id}/detail";
    private static final String GRAPH = "/api/v1/hadith/hadiths/{id}/sanad-graph";
    private static final String PHRASE = "/api/v1/hadith/sanad-narrators/transmission-phrase";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private HadithRepository hadithRepository;
    @Autowired private SanadRepository sanadRepository;
    @Autowired private NarratorRepository narratorRepository;

    private UUID hadithId;
    private UUID sanadId;
    private UUID companionId;
    private UUID collectorId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        hadithId = UUID.randomUUID();
        hadithRepository.save(new Hadith(hadithId, null, 1, "متن",
                HadithStatus.CANONICAL, null, null, Instant.now()));

        companionId = saveNarrator("صحابي", NarratorReliability.SAHABI);
        collectorId = saveNarrator("البخاري", NarratorReliability.THIQA);

        sanadId = recreateSanad("سمعت", "حدثنا");

        adminId = insertAdmin();
    }

    @Test
    void transmissionPhrase_editedViaPatch_survivesReimport() throws Exception {
        // 1. Правка формулы передачи звена позиции 1 (приёмник = коллектор)
        patchPhrase(1, "أخبرنا");

        // 2a. Записано в OVERLAY под синтетическим стабильным ключом hadith_id
        assertThat(overrideValue("transmission_phrase@1")).isEqualTo("أخبرنا");
        // 2b. КОЛОНКА hd_sanad_narrators не тронута — импортная формула на месте
        String columnPhrase = jdbcTemplate.queryForObject(
                "SELECT transmission_phrase FROM hd_sanad_narrators "
                        + "WHERE sanad_id = ? AND position = 1", String.class, sanadId);
        assertThat(columnPhrase).isEqualTo("حدثنا");

        // 3. GET detail — звено position 1 показывает курируемую формулу
        mockMvc.perform(get(DETAIL, hadithId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sanads[0].narrators[?(@.position==1)].transmissionPhrase")
                        .value(org.hamcrest.Matchers.hasItem("أخبرنا")))
                // звено position 0 не тронуто
                .andExpect(jsonPath("$.sanads[0].narrators[?(@.position==0)].transmissionPhrase")
                        .value(org.hamcrest.Matchers.hasItem("سمعت")));

        // 4. GET sanad-graph — ребро (коллектор) показывает курируемую формулу
        mockMvc.perform(get(GRAPH, hadithId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edges[?(@.target=='narrator-" + collectorId + "')].data.transmissionPhrase")
                        .value(org.hamcrest.Matchers.hasItem("أخبرنا")));

        // 5. СИМУЛЯЦИЯ РЕИМПОРТА: delete-recreate sanad'а (новый id, ИМПОРТНАЯ
        // формула حدثنا вернулась в колонку, те же звенья/позиции)
        sanadRepository.deleteByHadithId(hadithId);
        UUID newSanadId = recreateSanad("سمعت", "حدثنا");
        assertThat(newSanadId).isNotEqualTo(sanadId);

        // 6. ГОЛОВНОЕ ДОКАЗАТЕЛЬСТВО: курируемая формула ВСЁ ЕЩЁ показывается —
        // overlay ключевался hadith_id+position, не пересоздаваемым sanad_id
        mockMvc.perform(get(DETAIL, hadithId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sanads[0].narrators[?(@.position==1)].transmissionPhrase")
                        .value(org.hamcrest.Matchers.hasItem("أخبرنا")));
        mockMvc.perform(get(GRAPH, hadithId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edges[?(@.target=='narrator-" + collectorId + "')].data.transmissionPhrase")
                        .value(org.hamcrest.Matchers.hasItem("أخبرنا")));
    }

    @Test
    void transmissionPhrase_overriddenIndicator_onlyForAdmin() throws Exception {
        patchPhrase(1, "أخبرنا");

        // ADMIN reveal → ребро несёт transmissionPhraseOverridden=true
        mockMvc.perform(get(GRAPH, hadithId).header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edges[?(@.target=='narrator-" + collectorId
                        + "')].data.transmissionPhraseOverridden")
                        .value(org.hamcrest.Matchers.hasItem(true)));

        // гость → индикатор false (значение EFFECTIVE для всех, признак — лишь ADMIN)
        mockMvc.perform(get(GRAPH, hadithId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edges[?(@.target=='narrator-" + collectorId
                        + "')].data.transmissionPhraseOverridden")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                // но EFFECTIVE-формула гостю видна
                .andExpect(jsonPath("$.edges[?(@.target=='narrator-" + collectorId
                        + "')].data.transmissionPhrase")
                        .value(org.hamcrest.Matchers.hasItem("أخبرنا")));
    }

    // ── write-validation ────────────────────────────────────────────────────────

    @Test
    void patch_nonAdmin_returns403() throws Exception {
        UUID userId = insertUser();
        mockMvc.perform(patch(PHRASE).header("X-User-Id", userId.toString())
                        .contentType("application/json")
                        .content(body(1, "أخبرنا")))
                .andExpect(status().isForbidden());
    }

    @Test
    void patch_unknownLink_returns404() throws Exception {
        // position 99 не существует в цепи → 404 curation-entity-not-found
        mockMvc.perform(patch(PHRASE).header("X-User-Id", adminId.toString())
                        .contentType("application/json")
                        .content(body(99, "أخبرنا")))
                .andExpect(status().isNotFound());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private UUID saveNarrator(String name, String grade) {
        UUID id = UUID.randomUUID();
        narratorRepository.save(new Narrator(id, null, name, name, null, null, null, null,
                null, null, null, grade, null, 0, "{}", Instant.now(),
                null, null, null, null, null, null));
        return id;
    }

    /** Цепь сподвижник(0)→коллектор(1) с заданными импортными формулами. */
    private UUID recreateSanad(String phrase0, String phrase1) {
        UUID id = UUID.randomUUID();
        sanadRepository.save(new Sanad(id, hadithId, "SAHIH", collectorId, null, true, "{}", Instant.now()));
        sanadRepository.saveNarratorLink(new SanadNarrator(id, 0, companionId, phrase0));
        sanadRepository.saveNarratorLink(new SanadNarrator(id, 1, collectorId, phrase1));
        return id;
    }

    private void patchPhrase(int position, String phrase) throws Exception {
        mockMvc.perform(patch(PHRASE).header("X-User-Id", adminId.toString())
                        .contentType("application/json")
                        .content(body(position, phrase)))
                .andExpect(status().isOk());
    }

    private String body(int position, String phrase) {
        return "{\"hadithId\":\"" + hadithId + "\",\"position\":" + position
                + ",\"phrase\":\"" + phrase + "\"}";
    }

    private String overrideValue(String field) {
        List<String> rows = jdbcTemplate.query(
                "SELECT override_value FROM hd_field_overrides "
                        + "WHERE entity_table = 'hd_sanad_narrators' AND entity_id = ? AND field_name = ?",
                (rs, rn) -> rs.getString("override_value"), hadithId, field);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private UUID insertAdmin() {
        return insertUserWithRole(UserRole.ADMIN);
    }

    private UUID insertUser() {
        return insertUserWithRole(UserRole.USER);
    }

    private UUID insertUserWithRole(String role) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, username, email, role) VALUES (?, ?, ?, ?)",
                id, "u-" + id, id + "@t.com", role);
        return id;
    }
}
