package ru.basnukaev.argumentmap.hadith.alminasa.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmCrawlCheckpointDao;
import ru.basnukaev.argumentmap.hadith.alminasa.service.AlminasaCrawlService;

/**
 * IT admin-endpoints краулера alminasa: ADMIN-only, 202 на start,
 * 409 на двойной старт, форма status-ответа. base-url указывает на
 * закрытый порт — async-краулер быстро падает в FAILED, на контракт
 * endpoint'ов это не влияет (детерминированный happy-path краулера —
 * в AlminasaCrawlServiceIT).
 *
 * <p>БЕЗ @Transactional: start запускает async-поток с собственными
 * транзакциями чекпоинта — изоляция через ручную чистку в setUp.
 */
@SpringBootTest(properties = {
        "alminasa.base-url=http://127.0.0.1:1",
        "alminasa.crawl.delay-ms=0"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AlminasaAdminControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AmCrawlCheckpointDao checkpointDao;

    private UUID adminId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM am_crawl_checkpoint");
        jdbcTemplate.update("DELETE FROM users WHERE username LIKE 'alminasa-%'");
        adminId = insertUser("alminasa-admin", UserRole.ADMIN);
        userId = insertUser("alminasa-user", UserRole.USER);
    }

    @Test
    void start_не_админом_403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/alminasa/crawl/start")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type", containsString("forbidden-admin-only")));
    }

    @Test
    void status_без_чекпоинта_отдаёт_IDLE_и_нулевые_счётчики() throws Exception {
        mockMvc.perform(get("/api/v1/admin/alminasa/crawl/status")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IDLE"))
                .andExpect(jsonPath("$.stagedHadiths").value(0))
                .andExpect(jsonPath("$.stagedNarrators").value(0))
                .andExpect(jsonPath("$.stagedExplanations").value(0))
                .andExpect(jsonPath("$.stagedRulings").value(0));
    }

    @Test
    void start_отдаёт_202_со_статусом() throws Exception {
        mockMvc.perform(post("/api/v1/admin/alminasa/crawl/start")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void двойной_start_409() throws Exception {
        checkpointDao.upsertRunning(AlminasaCrawlService.HADITH_INDEX_KEY, true);

        mockMvc.perform(post("/api/v1/admin/alminasa/crawl/start")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type", containsString("alminasa-crawl-already-running")));
    }

    @Test
    void pause_отдаёт_200_со_статусом() throws Exception {
        mockMvc.perform(post("/api/v1/admin/alminasa/crawl/pause")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }

    private UUID insertUser(String suffix, String role) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email, role) VALUES (?, ?, ?, ?)",
                id, suffix + "-" + id, id + "-" + suffix + "@test.com", role);
        return id;
    }
}
