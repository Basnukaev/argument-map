package ru.basnukaev.argumentmap.library.pdf.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import ru.basnukaev.argumentmap.library.pdf.domain.PdfLocation;
import ru.basnukaev.argumentmap.library.pdf.domain.PdfMetadata;
import ru.basnukaev.argumentmap.library.pdf.service.PdfNotAvailableException;
import ru.basnukaev.argumentmap.library.pdf.service.PdfService;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 * IT для {@link PdfController}. Mock'аем {@link PdfService} - не
 * тестируем сами provider'ы (это unit-уровень), focus на wiring
 * controller'а: Range header parsing, content-type, status codes,
 * 404 для книг без PDF.
 *
 * <p>После 25.b.6 PDF идёт через {@code PdfService.openRange/openFull}
 * (stream из MinIO), не через {@code FileSystemResource}. Mock возвращает
 * {@link ResponseInputStream} обёрнутый над {@link ByteArrayInputStream}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PdfControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PdfService pdfService;

    private UUID bookId;
    private byte[] sampleBytes;
    private PdfLocation sampleLocation;

    @BeforeEach
    void setUp() {
        bookId = UUID.randomUUID();
        sampleBytes = new byte[10_000];
        for (int i = 0; i < sampleBytes.length; i++) {
            sampleBytes[i] = (byte) (i % 256);
        }
        sampleLocation = new PdfLocation(
                "library-imported-books",
                bookId + "/01.pdf",
                sampleBytes.length,
                "application/pdf");
    }

    @Test
    void getPdfInfo_existingBook_returnsMetadata() throws Exception {
        Mockito.when(pdfService.getMetadata(bookId)).thenReturn(new PdfMetadata(
                "https://archive.org/download/test/",
                true,
                135_000_000L,
                List.of(
                        new PdfFileInfo(0, "00.pdf", "Обложка", true, null, null),
                        new PdfFileInfo(1, "01.pdf", "Том 1", false, null, null),
                        new PdfFileInfo(2, "02p.pdf", "المقدمة", false, null, null)
                )
        ));

        mockMvc.perform(get("/api/v1/library/books/{id}/pdf/info", bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCover").value(true))
                .andExpect(jsonPath("$.totalSizeBytes").value(135_000_000L))
                .andExpect(jsonPath("$.files.length()").value(3))
                .andExpect(jsonPath("$.files[0].index").value(0))
                .andExpect(jsonPath("$.files[0].label").value("Обложка"))
                .andExpect(jsonPath("$.files[0].isCover").value(true))
                .andExpect(jsonPath("$.files[1].label").value("Том 1"))
                .andExpect(jsonPath("$.files[1].isCover").value(false))
                .andExpect(jsonPath("$.files[2].label").value("المقدمة"))
                .andExpect(jsonPath("$.files[2].isCover").value(false))
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
        Mockito.when(pdfService.locate(bookId, 0)).thenReturn(sampleLocation);
        Mockito.when(pdfService.openFull(sampleLocation))
                .thenAnswer(inv -> mockS3Stream(sampleBytes));

        mockMvc.perform(get("/api/v1/library/books/{id}/pdf", bookId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "10000"))
                .andExpect(content().contentTypeCompatibleWith("application/pdf"));
    }

    @Test
    void streamPdf_withRange_returnsPartialContent() throws Exception {
        Mockito.when(pdfService.locate(bookId, 0)).thenReturn(sampleLocation);
        Mockito.when(pdfService.openRange(eq(sampleLocation),
                        Mockito.anyLong(), Mockito.anyLong()))
                .thenAnswer(inv -> {
                    long start = inv.getArgument(1);
                    long end = inv.getArgument(2);
                    byte[] slice = new byte[(int) (end - start + 1)];
                    System.arraycopy(sampleBytes, (int) start, slice, 0, slice.length);
                    return mockS3Stream(slice);
                });

        mockMvc.perform(get("/api/v1/library/books/{id}/pdf", bookId)
                        .header(HttpHeaders.RANGE, "bytes=100-199"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE,
                        "bytes 100-199/10000"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "100"))
                .andExpect(content().contentTypeCompatibleWith("application/pdf"));
    }

    @Test
    void streamPdf_withFileIndex_passesToService() throws Exception {
        Mockito.when(pdfService.locate(bookId, 3)).thenReturn(sampleLocation);
        Mockito.when(pdfService.openFull(sampleLocation))
                .thenAnswer(inv -> mockS3Stream(sampleBytes));

        mockMvc.perform(get("/api/v1/library/books/{id}/pdf", bookId)
                        .param("fileIndex", "3"))
                .andExpect(status().isOk());

        Mockito.verify(pdfService).locate(bookId, 3);
    }

    @Test
    void streamPdf_invalidFileIndex_returns404() throws Exception {
        Mockito.when(pdfService.locate(bookId, 99))
                .thenThrow(new PdfNotAvailableException(bookId, 99, 5));

        mockMvc.perform(get("/api/v1/library/books/{id}/pdf", bookId)
                        .param("fileIndex", "99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("pdf-not-available")));
    }

    @Test
    void streamPdf_bookWithoutPdf_returns404() throws Exception {
        Mockito.when(pdfService.locate(bookId, 0))
                .thenThrow(new PdfNotAvailableException(bookId));

        mockMvc.perform(get("/api/v1/library/books/{id}/pdf", bookId))
                .andExpect(status().isNotFound());
    }

    /**
     * Создаёт {@code ResponseInputStream<GetObjectResponse>} обёртку
     * вокруг известных bytes. Используется для mock'а
     * {@code PdfService.openFull/openRange}.
     */
    private static ResponseInputStream<GetObjectResponse> mockS3Stream(byte[] content) {
        InputStream raw = new ByteArrayInputStream(content);
        GetObjectResponse resp = GetObjectResponse.builder()
                .contentLength((long) content.length)
                .contentType("application/pdf")
                .build();
        return new ResponseInputStream<>(resp, software.amazon.awssdk.http.AbortableInputStream.create(raw));
    }
}
