package ru.basnukaev.argumentmap.library.shamela.web;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.shamela.api.ShamelaApiException;
import ru.basnukaev.argumentmap.library.shamela.service.BookImportResult;
import ru.basnukaev.argumentmap.library.shamela.service.MappedBookResult;
import ru.basnukaev.argumentmap.library.shamela.service.MasterSyncResult;
import ru.basnukaev.argumentmap.library.shamela.service.ShamelaImportService;
import ru.basnukaev.argumentmap.library.shamela.service.ShamelaNotFoundException;
import ru.basnukaev.argumentmap.library.shamela.service.ShamelaToLibraryMapper;

/**
 * MockMvc IT для {@code /api/v1/admin/shamela/*}. Сервисный слой замокан
 * через {@code @MockitoBean} - этот тест проверяет только тонкий
 * controller-слой: HTTP-маппинг, validation, exception → ProblemDetail.
 *
 * <p>Полный pipeline-тест с реальным postgres - в {@code ShamelaImportServiceIT}
 * и {@code ShamelaToLibraryMapperIT}, дублировать тут смысла нет.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ShamelaAdminControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShamelaImportService importService;

    @MockitoBean
    private ShamelaToLibraryMapper mapper;

    // ---------------- sync-master ----------------

    @Test
    void syncMaster_returns_200_with_body_on_success() throws Exception {
        when(importService.syncMaster()).thenReturn(
                MasterSyncResult.synced(0, 1261, 50, 25_000, 8500));

        mockMvc.perform(post("/api/v1/admin/shamela/sync-master"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.previousVersion").value(0))
                .andExpect(jsonPath("$.currentVersion").value(1261))
                .andExpect(jsonPath("$.categoriesCount").value(50))
                .andExpect(jsonPath("$.authorsCount").value(25_000))
                .andExpect(jsonPath("$.booksCount").value(8500));
    }

    @Test
    void syncMaster_returns_200_with_unchanged_when_version_same() throws Exception {
        when(importService.syncMaster()).thenReturn(MasterSyncResult.unchanged(1261));

        mockMvc.perform(post("/api/v1/admin/shamela/sync-master"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(false))
                .andExpect(jsonPath("$.currentVersion").value(1261))
                .andExpect(jsonPath("$.booksCount").value(0));
    }

    @Test
    void syncMaster_returns_502_on_shamela_api_error() throws Exception {
        when(importService.syncMaster()).thenThrow(
                new ShamelaApiException("HTTP 503 от dev.shamela.ws"));

        mockMvc.perform(post("/api/v1/admin/shamela/sync-master"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("shamela API недоступна"))
                .andExpect(jsonPath("$.detail").value("HTTP 503 от dev.shamela.ws"));
    }

    // ---------------- import-book ----------------

    @Test
    void importBook_returns_200_with_body_on_success() throws Exception {
        when(importService.importBook(41557L))
                .thenReturn(new BookImportResult(41557L, 4, 320, 18));

        mockMvc.perform(post("/api/v1/admin/shamela/import-book/41557"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId").value(41557))
                .andExpect(jsonPath("$.majorRelease").value(4))
                .andExpect(jsonPath("$.pagesCount").value(320))
                .andExpect(jsonPath("$.titlesCount").value(18));

        verify(importService).importBook(41557L);
    }

    @Test
    void importBook_returns_404_when_book_missing_in_staging() throws Exception {
        when(importService.importBook(99999L)).thenThrow(
                new ShamelaNotFoundException("книга id=99999 не найдена"));

        mockMvc.perform(post("/api/v1/admin/shamela/import-book/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Запись shamela не найдена"))
                .andExpect(jsonPath("$.detail").value("книга id=99999 не найдена"));
    }

    @Test
    void importBook_returns_400_on_negative_id() throws Exception {
        mockMvc.perform(post("/api/v1/admin/shamela/import-book/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Некорректный аргумент"));

        verify(importService, never()).importBook(anyLong());
    }

    @Test
    void importBook_returns_400_on_zero_id() throws Exception {
        mockMvc.perform(post("/api/v1/admin/shamela/import-book/0"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(importService);
    }

    // ---------------- map-book ----------------

    @Test
    void mapBook_returns_200_with_body_on_success() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID bookUuid = UUID.randomUUID();
        UUID authorityUuid = UUID.randomUUID();
        when(mapper.mapBook(eq(41557L), eq(userId)))
                .thenReturn(MappedBookResult.freshlyCreated(
                        bookUuid, 41557L, authorityUuid, 18, 320));

        mockMvc.perform(post("/api/v1/admin/shamela/map-book/41557")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId").value(bookUuid.toString()))
                .andExpect(jsonPath("$.shamelaBookId").value(41557))
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.authorityId").value(authorityUuid.toString()))
                .andExpect(jsonPath("$.chaptersCount").value(18))
                .andExpect(jsonPath("$.pagesCount").value(320));
    }

    @Test
    void mapBook_returns_already_mapped_with_zero_counts() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID bookUuid = UUID.randomUUID();
        UUID authorityUuid = UUID.randomUUID();
        when(mapper.mapBook(eq(41557L), eq(userId)))
                .thenReturn(MappedBookResult.alreadyMapped(bookUuid, 41557L, authorityUuid));

        mockMvc.perform(post("/api/v1/admin/shamela/map-book/41557")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.chaptersCount").value(0))
                .andExpect(jsonPath("$.pagesCount").value(0));
    }

    @Test
    void mapBook_returns_400_when_x_user_id_header_missing() throws Exception {
        mockMvc.perform(post("/api/v1/admin/shamela/map-book/41557"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value(
                        "Отсутствует или невалидный заголовок X-User-Id"));

        verifyNoInteractions(mapper);
    }

    @Test
    void mapBook_returns_404_when_book_missing() throws Exception {
        UUID userId = UUID.randomUUID();
        when(mapper.mapBook(eq(99999L), eq(userId)))
                .thenThrow(new ShamelaNotFoundException(
                        "shamela book id=99999 не найдена в lib_shamela_book"));

        mockMvc.perform(post("/api/v1/admin/shamela/map-book/99999")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Запись shamela не найдена"));
    }

    @Test
    void mapBook_returns_400_on_negative_id() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/admin/shamela/map-book/-5")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(mapper);
    }
}
