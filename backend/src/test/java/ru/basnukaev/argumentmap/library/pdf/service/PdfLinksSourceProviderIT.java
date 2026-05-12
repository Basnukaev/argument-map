package ru.basnukaev.argumentmap.library.pdf.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.LibraryFile;
import ru.basnukaev.argumentmap.library.domain.LibraryFileSourceType;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.LibraryFileRepository;
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
 * Integration test для {@link PdfLinksSourceProvider} с двухуровневым
 * кешем (local temp file + MinIO catalog, ADR-024 + 25.b.5).
 *
 * <p>Mock'аем {@link PdfFetcher} - симулируем upstream download записью
 * фиксированного content в target file. Реальные HTTP не делаем.
 * Testcontainers MinIO даёт изолированный bucket для catalog/storage.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Testcontainers
class PdfLinksSourceProviderIT {

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

    @MockitoBean
    private PdfFetcher pdfFetcher;

    @Autowired
    private PdfLinksSourceProvider provider;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private LibraryFileRepository libraryFileRepository;

    @Autowired
    private ObjectStorageService objectStorageService;

    @Autowired
    private ObjectStorageProperties storageProperties;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    Path tempDir;

    private UUID userId;
    private String importedBucket;
    private byte[] upstreamPdfBytes;

    @BeforeEach
    void setUp() {
        importedBucket = storageProperties.buckets().importedBooks();
        ensureBucket(importedBucket);

        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "user-" + userId, userId + "@example.com");

        // Симулируем PDF content - 2KB фиксированных bytes
        upstreamPdfBytes = new byte[2_048];
        for (int i = 0; i < upstreamPdfBytes.length; i++) {
            upstreamPdfBytes[i] = (byte) (i % 251);
        }

        // Mock'аем PdfFetcher.fetch так что он пишет известный content в
        // указанный target Path - имитация successful HTTP download
        doAnswer(invocation -> {
            Path target = invocation.getArgument(1);
            Files.write(target, upstreamPdfBytes);
            return null;
        }).when(pdfFetcher).fetch(any(URI.class), any(Path.class));
    }

    @Test
    void downloadFile_cacheMiss_downloadsUpstream_uploadsToMinio_registersCatalog() throws Exception {
        Book book = saveShamelaBook(6, "01_book.pdf");
        Path bookDir = tempDir.resolve(book.id().toString());

        Path result = provider.downloadFile(book, 0, bookDir);

        assertThat(result).isRegularFile();
        assertThat(Files.readAllBytes(result)).hasSize(upstreamPdfBytes.length);

        String storageKey = book.id() + "/01_book.pdf";
        LibraryFile registered = libraryFileRepository
                .findActiveByBucketAndKey(importedBucket, storageKey).orElseThrow();
        assertThat(registered.bookId()).isEqualTo(book.id());
        assertThat(registered.sourceType()).isEqualTo(LibraryFileSourceType.SHAMELA);
        assertThat(registered.shamelaMajorRelease()).isEqualTo(6);
        assertThat(registered.sizeBytes()).isEqualTo(upstreamPdfBytes.length);
        assertThat(registered.contentHash()).hasSize(64);

        assertThat(objectStorageService.exists(importedBucket, storageKey)).isTrue();
        verify(pdfFetcher, times(1)).fetch(any(URI.class), any(Path.class));
    }

    @Test
    void downloadFile_localCacheHit_skipsUpstreamAndMinio() {
        Book book = saveShamelaBook(6, "01_book.pdf");
        Path bookDir = tempDir.resolve(book.id().toString());

        provider.downloadFile(book, 0, bookDir);
        verify(pdfFetcher, times(1)).fetch(any(URI.class), any(Path.class));

        Path second = provider.downloadFile(book, 0, bookDir);

        assertThat(second).isRegularFile();
        // Local file cache hit - не зовём upstream вторично
        verify(pdfFetcher, times(1)).fetch(any(URI.class), any(Path.class));
    }

    @Test
    void downloadFile_minioCacheHit_skipsUpstream_pullsFromMinio() throws Exception {
        Book book = saveShamelaBook(6, "01_book.pdf");
        Path bookDir = tempDir.resolve(book.id().toString());

        provider.downloadFile(book, 0, bookDir);
        verify(pdfFetcher, times(1)).fetch(any(URI.class), any(Path.class));

        // Имитируем restart backend - local file исчезает, MinIO+catalog
        // остаются
        Path localFile = bookDir.resolve("01_book.pdf");
        Files.deleteIfExists(localFile);
        assertThat(localFile).doesNotExist();

        Path restored = provider.downloadFile(book, 0, bookDir);

        assertThat(restored).isRegularFile();
        assertThat(Files.size(restored)).isEqualTo(upstreamPdfBytes.length);
        // Upstream НЕ дёргался повторно - содержимое из MinIO
        verify(pdfFetcher, times(1)).fetch(any(URI.class), any(Path.class));
    }

    @Test
    void downloadFile_archiveOrgBookWithoutShamelaMajor_registersAsArchiveOrgType() {
        Book book = saveArchiveOrgBook("vol_1.pdf");
        Path bookDir = tempDir.resolve(book.id().toString());

        provider.downloadFile(book, 0, bookDir);

        String storageKey = book.id() + "/vol_1.pdf";
        LibraryFile registered = libraryFileRepository
                .findActiveByBucketAndKey(importedBucket, storageKey).orElseThrow();
        assertThat(registered.sourceType()).isEqualTo(LibraryFileSourceType.ARCHIVE_ORG);
        assertThat(registered.shamelaMajorRelease()).isNull();
    }

    @Test
    void downloadFile_invalidFileIndex_throwsPdfNotAvailable() {
        Book book = saveShamelaBook(6, "01_book.pdf");
        Path bookDir = tempDir.resolve(book.id().toString());

        assertThatThrownBy(() -> provider.downloadFile(book, 99, bookDir))
                .isInstanceOf(PdfNotAvailableException.class);
    }

    @Test
    void supports_returnsFalseForBookWithoutPdfLinks() {
        Book book = bookRepository.save(new Book(
                UUID.randomUUID(), BookType.BOOK, "No PDF book",
                null, "ar", null, "{}", userId, Instant.now(), Instant.now()));

        assertThat(provider.supports(book)).isFalse();
    }

    @Test
    void supports_returnsTrueForBookWithPdfLinks() {
        Book book = saveShamelaBook(6, "01_book.pdf");

        assertThat(provider.supports(book)).isTrue();
    }

    @Test
    void downloadFile_secondVolumeUsesSeparateStorageKey() throws Exception {
        Book book = saveShamelaBookMultiVolume(6, "01_book.pdf", "02_book.pdf");
        Path bookDir = tempDir.resolve(book.id().toString());

        provider.downloadFile(book, 0, bookDir);
        provider.downloadFile(book, 1, bookDir);

        assertThat(libraryFileRepository.findActiveByBookId(book.id())).hasSize(2);
        assertThat(objectStorageService.exists(importedBucket, book.id() + "/01_book.pdf")).isTrue();
        assertThat(objectStorageService.exists(importedBucket, book.id() + "/02_book.pdf")).isTrue();
        verify(pdfFetcher, times(2)).fetch(any(URI.class), any(Path.class));
    }

    private Book saveShamelaBook(int majorRelease, String pdfFilename) {
        String metadataJson = String.format(
                "{\"shamela_book_id\":1503,\"shamela_major_release\":%d," +
                        "\"pdf_links\":{" +
                        "\"root\":\"https://archive.org/download/test/\"," +
                        "\"size\":2048," +
                        "\"files\":[\"%s\"]" +
                        "}}", majorRelease, pdfFilename);
        return bookRepository.save(new Book(
                UUID.randomUUID(), BookType.BOOK, "Shamela book",
                null, "ar", null, metadataJson, userId,
                Instant.now(), Instant.now()));
    }

    private Book saveShamelaBookMultiVolume(int majorRelease, String... pdfFilenames) {
        StringBuilder files = new StringBuilder();
        for (int i = 0; i < pdfFilenames.length; i++) {
            if (i > 0) files.append(',');
            files.append('"').append(pdfFilenames[i]).append('"');
        }
        String metadataJson = String.format(
                "{\"shamela_book_id\":1503,\"shamela_major_release\":%d," +
                        "\"pdf_links\":{" +
                        "\"root\":\"https://archive.org/download/test/\"," +
                        "\"size\":4096," +
                        "\"files\":[%s]" +
                        "}}", majorRelease, files);
        return bookRepository.save(new Book(
                UUID.randomUUID(), BookType.BOOK, "Multi-volume book",
                null, "ar", null, metadataJson, userId,
                Instant.now(), Instant.now()));
    }

    private Book saveArchiveOrgBook(String pdfFilename) {
        String metadataJson = String.format(
                "{\"pdf_links\":{" +
                        "\"root\":\"https://archive.org/download/test/\"," +
                        "\"size\":2048," +
                        "\"files\":[\"%s\"]" +
                        "}}", pdfFilename);
        return bookRepository.save(new Book(
                UUID.randomUUID(), BookType.BOOK, "ArchiveOrg book",
                null, "ar", null, metadataJson, userId,
                Instant.now(), Instant.now()));
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
