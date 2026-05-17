package ru.basnukaev.argumentmap.library.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.domain.LibraryFile;
import ru.basnukaev.argumentmap.library.domain.LibraryFileSourceType;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.imports.FileImportService.ImportResult;
import ru.basnukaev.argumentmap.library.repository.LibraryFileRepository;
import ru.basnukaev.argumentmap.library.repository.MuhaqqiqRepository;
import ru.basnukaev.argumentmap.library.repository.PageRepository;
import ru.basnukaev.argumentmap.library.repository.PublicationPlaceRepository;
import ru.basnukaev.argumentmap.library.repository.PublisherRepository;
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
 * Integration test для {@link FileImportService} - проверяет full
 * round-trip user-uploaded PDF -> Book + Page[] + library_files entry
 * в S3 bucket'е. PDF фикстуры генерируются programmatically через
 * PDFBox в {@code @BeforeEach} - не коммитим binary'и в репу.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Testcontainers
class FileImportServiceIT {

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
    private FileImportService service;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private LibraryFileRepository libraryFileRepository;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private MuhaqqiqRepository muhaqqiqRepository;

    @Autowired
    private PublisherRepository publisherRepository;

    @Autowired
    private PublicationPlaceRepository publicationPlaceRepository;

    @Autowired
    private ObjectStorageProperties properties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void importPdf_3pages_createsBookAnd3Pages() {
        byte[] pdfBytes = buildPdf(List.of("First page text", "Second page text", "Third page text"), null);

        ImportResult result = service.importPdf(pdfBytes, "test-book.pdf",
                new ImportMetadata("Тестовая книга", null, "ar", null), userId);

        assertThat(result.book()).isNotNull();
        assertThat(result.book().title()).isEqualTo("Тестовая книга");
        assertThat(result.book().language()).isEqualTo("ar");
        assertThat(result.book().createdBy()).isEqualTo(userId);
        assertThat(result.pageCount()).isEqualTo(3);

        List<Page> pages = pageRepository.findByBookIdRange(result.book().id(), 1, Integer.MAX_VALUE);
        assertThat(pages).hasSize(3);
        assertThat(pages.get(0).pageNumber()).isEqualTo(1);
        assertThat(pages.get(0).pdfPageNumber()).isEqualTo(1);
        assertThat(pages.get(0).textContent()).contains("First page text");
        assertThat(pages.get(1).pageNumber()).isEqualTo(2);
        assertThat(pages.get(1).textContent()).contains("Second page text");
        assertThat(pages.get(2).pageNumber()).isEqualTo(3);
        assertThat(pages.get(2).textContent()).contains("Third page text");
    }

    @Test
    void importPdf_titleFromMetadataIfNotOverridden() {
        byte[] pdfBytes = buildPdf(List.of("page 1"), "Built-in PDF Title");

        ImportResult result = service.importPdf(pdfBytes, "filename-title.pdf",
                ImportMetadata.empty(), userId);

        // user title null -> PDF metadata title used
        assertThat(result.book().title()).isEqualTo("Built-in PDF Title");
    }

    @Test
    void importPdf_titleFromFilenameIfNoMetadata() {
        byte[] pdfBytes = buildPdf(List.of("page 1"), null);

        ImportResult result = service.importPdf(pdfBytes, "my-book-name.pdf",
                ImportMetadata.empty(), userId);

        assertThat(result.book().title()).isEqualTo("my-book-name");
    }

    @Test
    void importPdf_userTitleOverridesPdfMetadata() {
        byte[] pdfBytes = buildPdf(List.of("page 1"), "PDF Author Title");
        ImportMetadata withOverride = new ImportMetadata(
                "User Override", null, "ru", null);

        ImportResult result = service.importPdf(pdfBytes, "ignored.pdf", withOverride, userId);

        assertThat(result.book().title()).isEqualTo("User Override");
        assertThat(result.book().language()).isEqualTo("ru");
    }

    @Test
    void importPdf_savesToMinIoAndRecordsLibraryFile() {
        byte[] pdfBytes = buildPdf(List.of("hello", "world"), null);

        ImportResult result = service.importPdf(pdfBytes, "uploaded.pdf",
                ImportMetadata.empty(), userId);

        LibraryFile file = result.file();
        assertThat(file.bucket()).isEqualTo(uploadsBucket);
        assertThat(file.storageKey()).isEqualTo(result.book().id() + "/uploaded.pdf");
        assertThat(file.sourceType()).isEqualTo(LibraryFileSourceType.USER_UPLOAD);
        assertThat(file.sourceUrl()).isNull();
        assertThat(file.bookId()).isEqualTo(result.book().id());
        assertThat(file.contentHash()).hasSize(64);
        assertThat(file.sizeBytes()).isEqualTo(pdfBytes.length);

        // verify catalog row
        LibraryFile reloaded = libraryFileRepository.findById(file.fileId()).orElseThrow();
        assertThat(reloaded.contentHash()).isEqualTo(file.contentHash());

        // verify object actually in bucket
        ListObjectVersionsResponse versions = s3Client.listObjectVersions(
                r -> r.bucket(uploadsBucket).prefix(file.storageKey()));
        assertThat(versions.versions()).anyMatch(v -> v.key().equals(file.storageKey()));
    }

    @Test
    void importPdf_filenameWithSpacesAndPath_sanitizedToStorageKey() {
        byte[] pdfBytes = buildPdf(List.of("p"), null);

        ImportResult result = service.importPdf(pdfBytes,
                "/uploads/My Book Title.pdf", ImportMetadata.empty(), userId);

        // spaces replaced + path stripped
        assertThat(result.file().storageKey()).isEqualTo(result.book().id() + "/My_Book_Title.pdf");
    }

    @Test
    void importPdf_emptyBytes_throwsFileImportException() {
        assertThatThrownBy(() -> service.importPdf(new byte[0], "empty.pdf",
                ImportMetadata.empty(), userId))
                .isInstanceOf(FileImportException.class)
                .hasMessageContaining("пустой");
    }

    @Test
    void importPdf_corruptedBytes_throwsFileImportException() {
        byte[] bogus = "this is not a pdf".getBytes();

        assertThatThrownBy(() -> service.importPdf(bogus, "fake.pdf",
                ImportMetadata.empty(), userId))
                .isInstanceOf(FileImportException.class)
                .hasMessageContaining("разобрать PDF");
    }

    @Test
    void importPdf_defaultLanguageIsArabic() {
        byte[] pdfBytes = buildPdf(List.of("p"), null);

        ImportResult result = service.importPdf(pdfBytes, "x.pdf",
                ImportMetadata.empty(), userId);

        assertThat(result.book().language()).isEqualTo("ar");
    }

    @Test
    void importPdf_metadataJsonContainsUserUploadedMarker() {
        byte[] pdfBytes = buildPdf(List.of("a", "b"), null);

        ImportResult result = service.importPdf(pdfBytes, "marker.pdf",
                ImportMetadata.empty(), userId);

        // raw metadata в lib_books.metadata jsonb - проверяем через
        // BookRepository -> метаданные сериализуются как plain JSON string
        assertThat(result.book().metadata())
                .contains("\"user_uploaded\":true")
                .contains("\"original_filename\":\"marker.pdf\"")
                .contains("\"pdf_page_count\":2");
    }

    @Test
    void importPdf_withAcademicData_callsCreateBook13Args() {
        byte[] pdfBytes = buildPdf(List.of("p"), null);
        ImportMetadata meta = new ImportMetadata(
                "Книга с тахкиком", null, "ar", null,
                "Шуайб аль-Арнаут", "Дар Тайба", "Бейрут",
                3, 1435, 2014
        );

        ImportResult result = service.importPdf(pdfBytes, "academic.pdf", meta, userId);

        // все 3 FK заполнены - findOrCreate в справочниках сработал
        assertThat(result.book().muhaqqiqId()).isNotNull();
        assertThat(result.book().publisherId()).isNotNull();
        assertThat(result.book().publicationPlaceId()).isNotNull();
        assertThat(muhaqqiqRepository.findById(result.book().muhaqqiqId()).orElseThrow().name())
                .isEqualTo("Шуайб аль-Арнаут");
        assertThat(publisherRepository.findById(result.book().publisherId()).orElseThrow().name())
                .isEqualTo("Дар Тайба");
        assertThat(publicationPlaceRepository.findById(result.book().publicationPlaceId())
                .orElseThrow().name())
                .isEqualTo("Бейрут");
        assertThat(result.book().editionNumber()).isEqualTo(3);
        assertThat(result.book().publishedYearHijri()).isEqualTo(1435);
        assertThat(result.book().publishedYearGregorian()).isEqualTo(2014);
    }

    @Test
    void importPdf_withPartialAcademicData_resolvesFKsForFilledFieldsOnly() {
        byte[] pdfBytes = buildPdf(List.of("p"), null);
        // только publisher + год хиджры; muhaqqiq/place/edition/year_gregorian пустые
        ImportMetadata meta = new ImportMetadata(
                null, null, "ar", null,
                null, "Только издатель", "  ",
                null, 1440, null
        );

        ImportResult result = service.importPdf(pdfBytes, "partial.pdf", meta, userId);

        assertThat(result.book().muhaqqiqId()).isNull();
        assertThat(result.book().publisherId()).isNotNull();
        assertThat(result.book().publicationPlaceId()).isNull();
        assertThat(result.book().editionNumber()).isNull();
        assertThat(result.book().publishedYearHijri()).isEqualTo(1440);
        assertThat(result.book().publishedYearGregorian()).isNull();
        assertThat(publisherRepository.findById(result.book().publisherId()).orElseThrow().name())
                .isEqualTo("Только издатель");
    }

    @Test
    void importPdf_withoutAcademicData_keepsLegacyPathAndNullFKs() {
        // sanity: старый 7-args путь без academic FK продолжает работать
        byte[] pdfBytes = buildPdf(List.of("p"), null);

        ImportResult result = service.importPdf(pdfBytes, "no-academic.pdf",
                ImportMetadata.empty(), userId);

        assertThat(result.book().muhaqqiqId()).isNull();
        assertThat(result.book().publisherId()).isNull();
        assertThat(result.book().publicationPlaceId()).isNull();
        assertThat(result.book().editionNumber()).isNull();
        assertThat(result.book().publishedYearHijri()).isNull();
        assertThat(result.book().publishedYearGregorian()).isNull();
    }

    /**
     * Генерирует валидный PDF с указанными текстами на страницах.
     * Опционально устанавливает title в PDF document information.
     */
    private static byte[] buildPdf(List<String> pageTexts, String title) {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (title != null) {
                PDDocumentInformation info = new PDDocumentInformation();
                info.setTitle(title);
                doc.setDocumentInformation(info);
            }
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
