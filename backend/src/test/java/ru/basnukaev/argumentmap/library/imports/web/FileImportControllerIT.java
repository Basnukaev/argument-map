package ru.basnukaev.argumentmap.library.imports.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.storage.ObjectStorageProperties;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.VersioningConfiguration;

/**
 * Web layer IT для {@link FileImportController} через MockMvc -
 * проверяет multipart parsing, валидацию contentType, обработку
 * ошибок через {@code GlobalExceptionHandler} (Этап 16.b).
 *
 * <p>Не {@code @Transactional} - {@link FileImportService} делает
 * реальный S3 put который не откатывается с тестовой транзакцией.
 * Чистим bucket в {@code @BeforeEach}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Testcontainers
class FileImportControllerIT {

    @Container
    static final MinIOContainer MINIO =
            new MinIOContainer("minio/minio:RELEASE.2025-07-23T15-54-02Z-cpuv1")
                    .withUserName("minioadmin")
                    .withPassword("minioadmin");

    @DynamicPropertySource
    static void minioProperties(DynamicPropertyRegistry r) {
        r.add("storage.endpoint", MINIO::getS3URL);
        r.add("storage.access-key", () -> "minioadmin");
        r.add("storage.secret-key", () -> "minioadmin");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private ObjectStorageProperties properties;

    private UUID userId;
    private String uploadsBucket;

    @BeforeEach
    void setUp() {
        uploadsBucket = properties.buckets().userUploads();
        ensureBucket(uploadsBucket, true);
        clearBucket(uploadsBucket);

        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "user-" + userId, userId + "@example.com");
    }

    @Test
    void POST_validPdf_returns201WithBody() throws Exception {
        byte[] pdfBytes = buildPdf(List.of("page 1 text", "page 2 text"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "uploaded.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);

        mockMvc.perform(multipart("/api/v1/library/imports/file")
                        .file(file)
                        .param("title", "Моя книга")
                        .param("language", "ar")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        Matchers.startsWith("/api/v1/library/books/")))
                .andExpect(jsonPath("$.bookId").isNotEmpty())
                .andExpect(jsonPath("$.fileId").isNotEmpty())
                .andExpect(jsonPath("$.pageCount").value(2))
                .andExpect(jsonPath("$.contentHash").isNotEmpty())
                .andExpect(jsonPath("$.sizeBytes").value(pdfBytes.length))
                .andExpect(jsonPath("$.bucket").value(uploadsBucket));
    }

    @Test
    void POST_wrongMimeType_returns415() throws Exception {
        MockMultipartFile bogus = new MockMultipartFile(
                "file", "x.txt", MediaType.TEXT_PLAIN_VALUE,
                "not a pdf".getBytes());

        mockMvc.perform(multipart("/api/v1/library/imports/file")
                        .file(bogus)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.type")
                        .value("https://argumentmap.example/errors/unsupported-media-type"))
                .andExpect(jsonPath("$.title").value("Неподдерживаемый тип файла"));
    }

    @Test
    void POST_emptyFile_returns422() throws Exception {
        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[0]);

        mockMvc.perform(multipart("/api/v1/library/imports/file")
                        .file(empty)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type")
                        .value("https://argumentmap.example/errors/file-import-error"));
    }

    @Test
    void POST_corruptedPdf_returns422() throws Exception {
        MockMultipartFile bogus = new MockMultipartFile(
                "file", "bogus.pdf", MediaType.APPLICATION_PDF_VALUE,
                "not really a PDF".getBytes());

        mockMvc.perform(multipart("/api/v1/library/imports/file")
                        .file(bogus)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail")
                        .value(Matchers.containsString("PDF")));
    }

    @Test
    void POST_missingUserHeader_returns400() throws Exception {
        // ADR-040 (dev/test profile): permitAll → @CurrentUser требует
        // principal → MissingUserHeaderException 400. В prod profile
        // 401 раньше от Spring Security
        byte[] pdfBytes = buildPdf(List.of("page"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);

        mockMvc.perform(multipart("/api/v1/library/imports/file").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type")
                        .value("https://argumentmap.example/errors/missing-user-header"));
    }

    @Test
    void POST_withAcademicMultipart_returns201AndBookHasAcademicFK() throws Exception {
        byte[] pdfBytes = buildPdf(List.of("p"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "academic.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);

        MvcResult result = mockMvc.perform(multipart("/api/v1/library/imports/file")
                        .file(file)
                        .param("title", "С academic")
                        .param("language", "ar")
                        .param("muhaqqiqName", "Шуайб аль-Арнаут")
                        .param("publisherName", "Дар Тайба")
                        .param("publicationPlaceName", "Бейрут")
                        .param("editionNumber", "3")
                        .param("publishedYearHijri", "1435")
                        .param("publishedYearGregorian", "2014")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookId").isNotEmpty())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String bookId = body.get("bookId").asText();

        // verify через JOIN: книга связана с правильным muhaqqiq/publisher/place
        // и integer-поля сохранены
        Integer muhaqqiqMatch = jdbcTemplate.queryForObject(
                "SELECT 1 FROM lib_books b JOIN lib_muhaqqiqs m ON b.muhaqqiq_id = m.id "
                        + "WHERE b.id = ?::uuid AND m.name = ?",
                Integer.class, bookId, "Шуайб аль-Арнаут");
        org.assertj.core.api.Assertions.assertThat(muhaqqiqMatch).isEqualTo(1);

        Integer publisherMatch = jdbcTemplate.queryForObject(
                "SELECT 1 FROM lib_books b JOIN lib_publishers p ON b.publisher_id = p.id "
                        + "WHERE b.id = ?::uuid AND p.name = ?",
                Integer.class, bookId, "Дар Тайба");
        org.assertj.core.api.Assertions.assertThat(publisherMatch).isEqualTo(1);

        Integer placeMatch = jdbcTemplate.queryForObject(
                "SELECT 1 FROM lib_books b JOIN lib_publication_places pp "
                        + "ON b.publication_place_id = pp.id "
                        + "WHERE b.id = ?::uuid AND pp.name = ?",
                Integer.class, bookId, "Бейрут");
        org.assertj.core.api.Assertions.assertThat(placeMatch).isEqualTo(1);

        Integer edition = jdbcTemplate.queryForObject(
                "SELECT edition_number FROM lib_books WHERE id = ?::uuid",
                Integer.class, bookId);
        org.assertj.core.api.Assertions.assertThat(edition).isEqualTo(3);

        Integer yearHijri = jdbcTemplate.queryForObject(
                "SELECT published_year_hijri FROM lib_books WHERE id = ?::uuid",
                Integer.class, bookId);
        org.assertj.core.api.Assertions.assertThat(yearHijri).isEqualTo(1435);

        Integer yearGregorian = jdbcTemplate.queryForObject(
                "SELECT published_year_gregorian FROM lib_books WHERE id = ?::uuid",
                Integer.class, bookId);
        org.assertj.core.api.Assertions.assertThat(yearGregorian).isEqualTo(2014);
    }

    @Test
    void POST_withInvalidEditionRange_returns422() throws Exception {
        byte[] pdfBytes = buildPdf(List.of("p"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "bad.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);

        mockMvc.perform(multipart("/api/v1/library/imports/file")
                        .file(file)
                        .param("editionNumber", "150")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type")
                        .value("https://argumentmap.example/errors/file-import-error"))
                .andExpect(jsonPath("$.detail")
                        .value(Matchers.containsString("editionNumber")));
    }

    @Test
    void POST_withInvalidYearRange_returns422() throws Exception {
        byte[] pdfBytes = buildPdf(List.of("p"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "bad-year.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);

        mockMvc.perform(multipart("/api/v1/library/imports/file")
                        .file(file)
                        .param("publishedYearHijri", "99999")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type")
                        .value("https://argumentmap.example/errors/file-import-error"))
                .andExpect(jsonPath("$.detail")
                        .value(Matchers.containsString("publishedYearHijri")));
    }

    @Test
    void POST_upload_thenGET_pdfInfo_returnsValidResponseWithUploadedBucket() throws Exception {
        // E2E проверка closes critical code review issue (Этап 16.h):
        // PDF загруженный через upload должен быть читаем через
        // /pdf/info endpoint - чтобы reader на frontend открыл книгу
        byte[] pdfBytes = buildPdf(List.of("page 1 text", "page 2 text", "page 3 text"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "manual.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/library/imports/file")
                        .file(file)
                        .param("title", "Полное руководство")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode uploadBody = objectMapper.readTree(
                uploadResult.getResponse().getContentAsString());
        String bookId = uploadBody.get("bookId").asText();

        // Главная проверка: GET /pdf/info - до 16.h возвращал 404
        // pdf-not-available потому что PdfLinksSourceProvider не находил
        // pdf_links в metadata user-uploaded книги
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/library/books/" + bookId + "/pdf/info")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files").isArray())
                .andExpect(jsonPath("$.files.length()").value(1))
                .andExpect(jsonPath("$.files[0].index").value(0))
                .andExpect(jsonPath("$.files[0].label").value("manual"))
                .andExpect(jsonPath("$.files[0].isCover").value(false))
                .andExpect(jsonPath("$.files[0].sizeBytes").value(pdfBytes.length))
                .andExpect(jsonPath("$.files[0].pageCount").value(3))
                .andExpect(jsonPath("$.totalSizeBytes").value(pdfBytes.length));

        // Также проверим что streaming endpoint работает (полный download
        // без Range) - frontend сначала запросит без Range, посмотрит
        // Accept-Ranges, потом перейдёт на range-mode
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/library/books/" + bookId + "/pdf?fileIndex=0")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Content-Type",
                        Matchers.startsWith(MediaType.APPLICATION_PDF_VALUE)));
    }

    @Test
    void POST_withInvalidLanguage_returns422() throws Exception {
        byte[] pdfBytes = buildPdf(List.of("p"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "bad-lang.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);

        mockMvc.perform(multipart("/api/v1/library/imports/file")
                        .file(file)
                        .param("language", "zzzz")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type")
                        .value("https://argumentmap.example/errors/file-import-error"))
                .andExpect(jsonPath("$.detail")
                        .value(Matchers.containsString("language")));
    }

    @Test
    void POST_minimumFields_filenameAsTitleAndArDefault() throws Exception {
        byte[] pdfBytes = buildPdf(List.of("one page"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "raw-name.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);

        mockMvc.perform(multipart("/api/v1/library/imports/file")
                        .file(file)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pageCount").value(1))
                .andExpect(jsonPath("$.storageKey")
                        .value(Matchers.endsWith("/raw-name.pdf")));
    }

    private static byte[] buildPdf(List<String> pageTexts) {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String text : pageTexts) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(50, 700);
                    stream.showText(text);
                    stream.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("test fixture build failed", e);
        }
    }

    private void ensureBucket(String bucket, boolean withVersioning) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
        if (withVersioning) {
            s3Client.putBucketVersioning(PutBucketVersioningRequest.builder()
                    .bucket(bucket)
                    .versioningConfiguration(VersioningConfiguration.builder()
                            .status(BucketVersioningStatus.ENABLED)
                            .build())
                    .build());
        }
    }

    private void clearBucket(String bucket) {
        ListObjectVersionsResponse versions = s3Client.listObjectVersions(
                r -> r.bucket(bucket));
        versions.versions().forEach(v -> s3Client.deleteObject(
                r -> r.bucket(bucket).key(v.key()).versionId(v.versionId())));
        versions.deleteMarkers().forEach(m -> s3Client.deleteObject(
                r -> r.bucket(bucket).key(m.key()).versionId(m.versionId())));
    }
}
