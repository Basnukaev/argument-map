package ru.basnukaev.argumentmap.library.pdf.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.pdf.domain.PdfFileInfo;
import ru.basnukaev.argumentmap.library.pdf.domain.PdfMetadata;
import ru.basnukaev.argumentmap.library.pdf.service.PdfNotAvailableException;
import ru.basnukaev.argumentmap.library.pdf.service.PdfService;

/**
 * IT для {@link PdfController}. Mock'аем {@link PdfService} - не
 * тестируем сами provider'ы (это unit-уровень), focus на wiring
 * controller'а: Range header parsing, content-type, status codes,
 * 404 для книг без PDF, 416/400 для невалидных запросов.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PdfControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PdfService pdfService;

    @TempDir
    Path tempDir;

    private UUID bookId;
    private Path samplePdf;

    @BeforeEach
    void setUp() throws Exception {
        bookId = UUID.randomUUID();
        samplePdf = tempDir.resolve("sample.pdf");
        // 10KB sample - imitates PDF binary. Real content неважен, тестируем
        // streaming/Range, не PDF parsing
        byte[] content = new byte[10_000];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i % 256);
        }
        Files.write(samplePdf, content);
    }

    @Test
    void getPdfInfo_existingBook_returnsMetadata() throws Exception {
        Mockito.when(pdfService.getMetadata(bookId)).thenReturn(new PdfMetadata(
                "https://archive.org/download/test/",
                true,
                135_000_000L,
                List.of(
                        new PdfFileInfo(0, "01.pdf", "Том 1", null, null),
                        new PdfFileInfo(1, "02p.pdf", "المقدمة", null, null)
                )
        ));

        mockMvc.perform(get("/api/v1/library/books/{id}/pdf/info", bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCover").value(true))
                .andExpect(jsonPath("$.totalSizeBytes").value(135_000_000L))
                .andExpect(jsonPath("$.files.length()").value(2))
                .andExpect(jsonPath("$.files[0].index").value(0))
                .andExpect(jsonPath("$.files[0].label").value("Том 1"))
                .andExpect(jsonPath("$.files[1].label").value("المقدمة"))
                // filename НЕ возвращается клиенту (защита от обхода нашего endpoint)
                .andExpect(jsonPath("$.files[0].filename").doesNotExist());
    }

    @Test
    void getPdfInfo_bookWithoutPdf_returns404() throws Exception {
        Mockito.when(pdfService.getMetadata(bookId))
                .thenThrow(new PdfNotAvailableException(bookId));

        mockMvc.perform(get("/api/v1/library/books/{id}/pdf/info", bookId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("pdf-not-available")));
    }

    @Test
    void streamPdf_withoutRange_returnsFullFile() throws Exception {
        Mockito.when(pdfService.getOrDownload(bookId, 0)).thenReturn(samplePdf);

        mockMvc.perform(get("/api/v1/library/books/{id}/pdf", bookId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "10000"))
                .andExpect(content().contentTypeCompatibleWith("application/pdf"));
    }

    @Test
    void streamPdf_withRange_returnsPartialContent() throws Exception {
        Mockito.when(pdfService.getOrDownload(bookId, 0)).thenReturn(samplePdf);

        mockMvc.perform(get("/api/v1/library/books/{id}/pdf", bookId)
                        .header(HttpHeaders.RANGE, "bytes=100-199"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(content().contentTypeCompatibleWith("application/pdf"));
    }

    @Test
    void streamPdf_withFileIndex_passesToService() throws Exception {
        Mockito.when(pdfService.getOrDownload(bookId, 3)).thenReturn(samplePdf);

        mockMvc.perform(get("/api/v1/library/books/{id}/pdf", bookId)
                        .param("fileIndex", "3"))
                .andExpect(status().isOk());

        Mockito.verify(pdfService).getOrDownload(bookId, 3);
    }

    @Test
    void streamPdf_invalidFileIndex_returns404() throws Exception {
        Mockito.when(pdfService.getOrDownload(bookId, 99))
                .thenThrow(new PdfNotAvailableException(bookId, 99, 5));

        mockMvc.perform(get("/api/v1/library/books/{id}/pdf", bookId)
                        .param("fileIndex", "99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("pdf-not-available")));
    }

    @Test
    void streamPdf_bookWithoutPdf_returns404() throws Exception {
        Mockito.when(pdfService.getOrDownload(bookId, 0))
                .thenThrow(new PdfNotAvailableException(bookId));

        mockMvc.perform(get("/api/v1/library/books/{id}/pdf", bookId))
                .andExpect(status().isNotFound());
    }
}
