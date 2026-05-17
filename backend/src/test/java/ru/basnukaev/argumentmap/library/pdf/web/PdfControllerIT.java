package ru.basnukaev.argumentmap.library.pdf.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
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
import ru.basnukaev.argumentmap.library.pdf.domain.PdfMetadata;
import ru.basnukaev.argumentmap.library.pdf.domain.PdfStreamingResult;
import ru.basnukaev.argumentmap.library.pdf.domain.RangeSpec;
import ru.basnukaev.argumentmap.library.pdf.service.PdfNotAvailableException;
import ru.basnukaev.argumentmap.library.pdf.service.PdfService;
import ru.basnukaev.argumentmap.library.pdf.service.RangeNotSatisfiableException;

/**
 * IT для {@link PdfController}. Mock'аем {@link PdfService} - не
 * тестируем сами provider'ы (это unit-уровень), focus на wiring
 * controller'а: Range header parsing, content-type, status codes,
 * 404 для книг без PDF, 416 Range Not Satisfiable.
 *
 * <p>После 25.d.5 - controller использует {@code PdfService.openStream}
 * (lazy через provider). Mock возвращает {@link PdfStreamingResult} с
 * {@link ByteArrayInputStream} вокруг known bytes - проверяем wiring
 * status codes, headers, content.
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

    @BeforeEach
    void setUp() {
        bookId = UUID.randomUUID();
        sampleBytes = new byte[10_000];
        for (int i = 0; i < sampleBytes.length; i++) {
            sampleBytes[i] = (byte) (i % 256);
        }
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
        Mockito.when(pdfService.openStream(eq(bookId), eq(0), isNull()))
                .thenReturn(fullResult(sampleBytes));

        mockMvc.perform(get("/api/v1/library/books/{id}/pdf", bookId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "10000"))
                .andExpect(content().contentTypeCompatibleWith("application/pdf"));
    }

    @Test
    void streamPdf_withRange_returnsPartialContent() throws Exception {
        Mockito.when(pdfService.openStream(eq(bookId), eq(0), any(RangeSpec.class)))
                .thenAnswer(inv -> {
                    RangeSpec rs = inv.getArgument(2);
                    long start = rs.startInclusive();
                    long end = rs.endInclusive();
                    byte[] slice = new byte[(int) (end - start + 1)];
                    System.arraycopy(sampleBytes, (int) start, slice, 0, slice.length);
                    return partialResult(slice, start, end, sampleBytes.length);
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
        Mockito.when(pdfService.openStream(eq(bookId), eq(3), isNull()))
                .thenReturn(fullResult(sampleBytes));

        mockMvc.perform(get("/api/v1/library/books/{id}/pdf", bookId)
                        .param("fileIndex", "3"))
                .andExpect(status().isOk());

        Mockito.verify(pdfService).openStream(eq(bookId), eq(3), isNull());
    }

    @Test
    void streamPdf_invalidFileIndex_returns404() throws Exception {
        Mockito.when(pdfService.openStream(eq(bookId), eq(99), isNull()))
                .thenThrow(new PdfNotAvailableException(bookId, 99, 5));

        mockMvc.perform(get("/api/v1/library/books/{id}/pdf", bookId)
                        .param("fileIndex", "99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("pdf-not-available")));
    }

    @Test
    void streamPdf_bookWithoutPdf_returns404() throws Exception {
        Mockito.when(pdfService.openStream(eq(bookId), eq(0), isNull()))
                .thenThrow(new PdfNotAvailableException(bookId));

        mockMvc.perform(get("/api/v1/library/books/{id}/pdf", bookId))
                .andExpect(status().isNotFound());
    }

    @Test
    void streamPdf_rangeOutsideFile_returns416() throws Exception {
        Mockito.when(pdfService.openStream(eq(bookId), eq(0), any(RangeSpec.class)))
                .thenThrow(new RangeNotSatisfiableException(50_000, 10_000));

        mockMvc.perform(get("/api/v1/library/books/{id}/pdf", bookId)
                        .header(HttpHeaders.RANGE, "bytes=50000-60000"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(jsonPath("$.type").value(containsString("range-not-satisfiable")))
                .andExpect(jsonPath("$.start").value(50_000))
                .andExpect(jsonPath("$.totalSize").value(10_000));
    }

    private static PdfStreamingResult fullResult(byte[] content) {
        return new PdfStreamingResult(
                new ByteArrayInputStream(content),
                content.length,
                0L,
                content.length - 1,
                content.length,
                false);
    }

    private static PdfStreamingResult partialResult(byte[] slice, long start, long end, long total) {
        return new PdfStreamingResult(
                new ByteArrayInputStream(slice),
                slice.length,
                start,
                end,
                total,
                true);
    }
}
