package ru.basnukaev.argumentmap.library.pdf.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.UUID;

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
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.LibraryFile;
import ru.basnukaev.argumentmap.library.domain.LibraryFileSourceType;
import ru.basnukaev.argumentmap.library.pdf.domain.PdfLocation;
import ru.basnukaev.argumentmap.library.pdf.domain.PdfMetadata;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.storage.ObjectStorageProperties;
import ru.basnukaev.argumentmap.library.storage.ObjectStorageService;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.VersioningConfiguration;

/**
 * Integration test для {@link UserUploadProvider} - проверка что PDF
 * загруженный через {@code FileImportService} доступен на чтение через
 * {@code PdfService.getMetadata} / {@code PdfService.locate} (Этап 16.h,
 * закрывает critical gap из code review Сессии 37).
 *
 * <p>Setup пишет blob в MinIO через {@link ObjectStorageService#putAndRegister}
 * (как делает реальный {@code FileImportService}), потом проверяем что
 * provider правильно резолвит metadata и location.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Testcontainers
class UserUploadProviderIT {

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
    private UserUploadProvider provider;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private ObjectStorageService objectStorageService;

    @Autowired
    private ObjectStorageProperties storageProperties;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private String uploadsBucket;
    private byte[] samplePdfBytes;

    @BeforeEach
    void setUp() {
        uploadsBucket = storageProperties.buckets().userUploads();
        ensureBucket(uploadsBucket);

        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "user-" + userId, userId + "@example.com");

        samplePdfBytes = new byte[1_024];
        for (int i = 0; i < samplePdfBytes.length; i++) {
            samplePdfBytes[i] = (byte) (i % 251);
        }
    }

    @Test
    void supports_returnsTrueForUserUploadedBook() {
        Book book = saveUserUploadedBook(42, "manual.pdf");

        assertThat(provider.supports(book)).isTrue();
    }

    @Test
    void supports_returnsFalseForShamelaImportedBook() {
        // Эмулируем shamela-книгу: pdf_links в metadata + blob с
        // sourceType=SHAMELA в catalog (не USER_UPLOAD)
        Book book = saveBookWithSourceType("01_book.pdf", LibraryFileSourceType.SHAMELA);

        assertThat(provider.supports(book)).isFalse();
    }

    @Test
    void supports_returnsFalseForBookWithoutAnyBlob() {
        Book book = bookRepository.save(new Book(
                UUID.randomUUID(), BookType.BOOK, "Empty book",
                null, "ar", null, "{}", userId,
                Instant.now(), Instant.now(),
                null, null, null, null, null, null));

        assertThat(provider.supports(book)).isFalse();
    }

    @Test
    void getMetadata_returnsSingleFileWithPageCount() {
        Book book = saveUserUploadedBook(42, "manual.pdf");

        PdfMetadata meta = provider.getMetadata(book);

        assertThat(meta.root()).isNull();
        assertThat(meta.hasCover()).isFalse();
        assertThat(meta.totalSizeBytes()).isEqualTo(samplePdfBytes.length);
        assertThat(meta.files()).hasSize(1);
        assertThat(meta.files().get(0).index()).isZero();
        assertThat(meta.files().get(0).filename()).isEqualTo("manual.pdf");
        assertThat(meta.files().get(0).label()).isEqualTo("manual");
        assertThat(meta.files().get(0).isCover()).isFalse();
        assertThat(meta.files().get(0).sizeBytes()).isEqualTo(samplePdfBytes.length);
        assertThat(meta.files().get(0).pageCount()).isEqualTo(42);
    }

    @Test
    void getMetadata_returnsNullPageCountWhenMetadataMissingField() {
        Book book = saveUserUploadedBookWithoutPageCount("manual.pdf");

        PdfMetadata meta = provider.getMetadata(book);

        assertThat(meta.files()).hasSize(1);
        assertThat(meta.files().get(0).pageCount()).isNull();
    }

    @Test
    void locateFile_returnsCorrectBucketAndKey() {
        Book book = saveUserUploadedBook(42, "manual.pdf");

        PdfLocation loc = provider.locateFile(book, 0);

        assertThat(loc.bucket()).isEqualTo(uploadsBucket);
        assertThat(loc.storageKey()).isEqualTo(book.id() + "/manual.pdf");
        assertThat(loc.sizeBytes()).isEqualTo(samplePdfBytes.length);
        assertThat(loc.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void locateFile_invalidFileIndex_throwsPdfNotAvailable() {
        Book book = saveUserUploadedBook(42, "manual.pdf");

        assertThatThrownBy(() -> provider.locateFile(book, 1))
                .isInstanceOf(PdfNotAvailableException.class);
    }

    @Test
    void locateFile_bookWithoutBlob_throwsPdfNotAvailable() {
        Book book = bookRepository.save(new Book(
                UUID.randomUUID(), BookType.BOOK, "No blob book",
                null, "ar", null, "{}", userId,
                Instant.now(), Instant.now(),
                null, null, null, null, null, null));

        assertThatThrownBy(() -> provider.locateFile(book, 0))
                .isInstanceOf(PdfNotAvailableException.class);
    }

    @Test
    void locatedLocation_isReadableThroughObjectStorageService() throws Exception {
        Book book = saveUserUploadedBook(42, "manual.pdf");

        PdfLocation loc = provider.locateFile(book, 0);

        try (var stream = objectStorageService.get(loc.bucket(), loc.storageKey())) {
            byte[] read = stream.readAllBytes();
            assertThat(read).containsExactly(samplePdfBytes);
        }
    }

    /**
     * Эмулирует то что делает {@code FileImportService} - создаёт book
     * с user-upload metadata, заливает blob в bucket + регистрирует в
     * catalog с {@code sourceType=USER_UPLOAD}.
     */
    private Book saveUserUploadedBook(int pageCount, String filename) {
        String metadataJson = String.format(
                "{\"user_uploaded\":true,\"original_filename\":\"%s\",\"pdf_page_count\":%d}",
                filename, pageCount);
        Book book = bookRepository.save(new Book(
                UUID.randomUUID(), BookType.BOOK, "User upload",
                null, "ar", null, metadataJson, userId,
                Instant.now(), Instant.now(),
                null, null, null, null, null, null));
        String storageKey = book.id() + "/" + filename;
        objectStorageService.putAndRegister(
                uploadsBucket, storageKey,
                new ByteArrayInputStream(samplePdfBytes),
                "application/pdf",
                book.id(), null, LibraryFileSourceType.USER_UPLOAD,
                null, null);
        return book;
    }

    private Book saveUserUploadedBookWithoutPageCount(String filename) {
        String metadataJson = String.format(
                "{\"user_uploaded\":true,\"original_filename\":\"%s\"}", filename);
        Book book = bookRepository.save(new Book(
                UUID.randomUUID(), BookType.BOOK, "User upload no count",
                null, "ar", null, metadataJson, userId,
                Instant.now(), Instant.now(),
                null, null, null, null, null, null));
        String storageKey = book.id() + "/" + filename;
        objectStorageService.putAndRegister(
                uploadsBucket, storageKey,
                new ByteArrayInputStream(samplePdfBytes),
                "application/pdf",
                book.id(), null, LibraryFileSourceType.USER_UPLOAD,
                null, null);
        return book;
    }

    private Book saveBookWithSourceType(String filename, LibraryFileSourceType sourceType) {
        // Книга с pdf_links - shamela-style. Registered blob с НЕ USER_UPLOAD
        // source-type - наш provider не должен его поддержать
        String metadataJson = String.format(
                "{\"shamela_book_id\":1503,\"shamela_major_release\":6," +
                        "\"pdf_links\":{\"root\":\"https://archive.org/download/x/\"," +
                        "\"files\":[\"%s\"]}}", filename);
        Book book = bookRepository.save(new Book(
                UUID.randomUUID(), BookType.BOOK, "Shamela book",
                null, "ar", null, metadataJson, userId,
                Instant.now(), Instant.now(),
                null, null, null, null, null, null));
        String bucket = storageProperties.buckets().importedBooks();
        ensureBucket(bucket);
        String storageKey = book.id() + "/" + filename;
        LibraryFile registered = objectStorageService.putAndRegister(
                bucket, storageKey,
                new ByteArrayInputStream(samplePdfBytes),
                "application/pdf",
                book.id(), "https://archive.org/download/x/" + filename,
                sourceType, 6, null);
        // sanity check setup
        assertThat(registered.sourceType()).isEqualTo(sourceType);
        return book;
    }

    private void ensureBucket(String bucket) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
        s3Client.putBucketVersioning(PutBucketVersioningRequest.builder()
                .bucket(bucket)
                .versioningConfiguration(VersioningConfiguration.builder()
                        .status(BucketVersioningStatus.ENABLED)
                        .build())
                .build());
    }
}
