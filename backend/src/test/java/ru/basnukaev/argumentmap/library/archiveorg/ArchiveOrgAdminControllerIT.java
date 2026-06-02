package ru.basnukaev.argumentmap.library.archiveorg;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.archiveorg.ArchiveOrgPreview.CoverOption;
import ru.basnukaev.argumentmap.library.archiveorg.ArchiveOrgPreview.ProvenanceField;
import ru.basnukaev.argumentmap.library.archiveorg.ArchiveOrgPreview.VolumeGroup;

/**
 * MockMvc IT для {@code /api/v1/admin/archive-org/*} (ADR-056). Сервис
 * замокан - проверяем тонкий controller-слой: ADMIN-guard, контракты
 * preview/import, маппинг ошибок (invalid URL → 400, item not found →
 * 404, archive.org down → 502). Полный pipeline - в
 * {@code ArchiveOrgImportServiceIT}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ArchiveOrgAdminControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ArchiveOrgImportService importService;

    private UUID adminId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email, role) VALUES (?, ?, ?, 'ADMIN') ON CONFLICT (id) DO NOTHING",
                adminId, "admin-" + adminId, adminId + "@test.local");
        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email, role) VALUES (?, ?, ?, 'USER') ON CONFLICT (id) DO NOTHING",
                userId, "user-" + userId, userId + "@test.local");
    }

    private static ArchiveOrgPreview samplePreview() {
        return new ArchiveOrgPreview(
                "fmhji",
                ProvenanceField.of("الفقه المنهجي"),
                ProvenanceField.missing(),
                ProvenanceField.missing(),
                ProvenanceField.missing(),
                ProvenanceField.missing(),
                ProvenanceField.missing(),
                ProvenanceField.missing(),
                ProvenanceField.missing(),
                ProvenanceField.missing(),
                ProvenanceField.of("ar"),
                "описание", // plain-text (HTML снят)
                List.of(new VolumeGroup("volume", 1,
                        "fmhji1.pdf", "Книга", 100L,
                        "https://archive.org/download/fmhji/fmhji1.pdf")),
                List.of(new CoverOption("thumbnail", "https://archive.org/services/img/fmhji")),
                true);
    }

    // ---------------- preview ----------------

    @Test
    void preview_admin_returns200WithProvenanceAndFiles() throws Exception {
        when(importService.preview(anyString())).thenReturn(samplePreview());

        mockMvc.perform(get("/api/v1/admin/archive-org/preview")
                        .param("url", "https://archive.org/details/fmhji")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archiveOrgId").value("fmhji"))
                .andExpect(jsonPath("$.title.source").value("archive_org"))
                .andExpect(jsonPath("$.author.source").value("missing"))
                .andExpect(jsonPath("$.files[0].role").value("volume"))
                .andExpect(jsonPath("$.coverOptions[0].kind").value("thumbnail"))
                .andExpect(jsonPath("$.hasPdf").value(true));
    }

    @Test
    void preview_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/archive-org/preview")
                        .param("url", "https://archive.org/details/fmhji")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.containsString("forbidden-admin-only")));
        verifyNoInteractions(importService);
    }

    @Test
    void preview_invalidUrl_returns400() throws Exception {
        when(importService.preview(anyString()))
                .thenThrow(new InvalidArchiveOrgUrlException("не archive.org-URL"));

        mockMvc.perform(get("/api/v1/admin/archive-org/preview")
                        .param("url", "https://example.com/foo")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.containsString("invalid-archive-org-url")));
    }

    @Test
    void preview_itemNotFound_returns404() throws Exception {
        when(importService.preview(anyString()))
                .thenThrow(new ArchiveOrgItemNotFoundException("nope"));

        mockMvc.perform(get("/api/v1/admin/archive-org/preview")
                        .param("url", "nope")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void preview_archiveOrgDown_returns502() throws Exception {
        when(importService.preview(anyString()))
                .thenThrow(new ArchiveOrgException("circuit breaker open"));

        mockMvc.perform(get("/api/v1/admin/archive-org/preview")
                        .param("url", "fmhji")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isBadGateway());
    }

    // ---------------- import ----------------

    @Test
    void import_admin_returns200WithResult() throws Exception {
        UUID bookId = UUID.randomUUID();
        when(importService.importBook(any()))
                .thenReturn(new ArchiveOrgImportResponse(bookId, "fmhji", 3, true, 0, false));

        mockMvc.perform(post("/api/v1/admin/archive-org/import")
                        .header("X-User-Id", adminId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://archive.org/details/fmhji\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId").value(bookId.toString()))
                .andExpect(jsonPath("$.volumesRegistered").value(3))
                .andExpect(jsonPath("$.coverSet").value(true))
                .andExpect(jsonPath("$.alreadyExisted").value(false));
    }

    @Test
    void import_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/archive-org/import")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://archive.org/details/fmhji\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(importService);
    }

    @Test
    void import_blankUrl_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/archive-org/import")
                        .header("X-User-Id", adminId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(importService);
    }
}
