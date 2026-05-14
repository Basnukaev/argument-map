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
import ru.basnukaev.argumentmap.library.pdf.domain.PdfLocation;
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
 * Integration test для {@link PdfLinksSourceProvider} с MinIO catalog
 * cache (ADR-024, 25.b.6).
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

        upstreamPdfBytes = new byte[2_048];
        for (int i = 0; i < upstreamPdfBytes.length; i++) {
            upstreamPdfBytes[i] = (byte) (i % 251);
        }

        doAnswer(invocation -> {
            Path target = invocation.getArgument(1);
            Files.write(target, upstreamPdfBytes);
            return null;
        }).when(pdfFetcher).fetch(any(URI.class), any(Path.class));
    }

    @Test
    void locateFile_cacheMiss_downloadsUpstream_registersCatalog_returnsLocation() {
        Book book = saveShamelaBook(6, "01_book.pdf");

        PdfLocation loc = provider.locateFile(book, 0);

        assertThat(loc.bucket()).isEqualTo(importedBucket);
        assertThat(loc.storageKey()).isEqualTo(book.id() + "/01_book.pdf");
        assertThat(loc.sizeBytes()).isEqualTo(upstreamPdfBytes.length);
        assertThat(loc.contentType()).isEqualTo("application/pdf");

        LibraryFile registered = libraryFileRepository
                .findActiveByBucketAndKey(loc.bucket(), loc.storageKey()).orElseThrow();
        assertThat(registered.bookId()).isEqualTo(book.id());
        assertThat(registered.sourceType()).isEqualTo(LibraryFileSourceType.SHAMELA);
        assertThat(registered.shamelaMajorRelease()).isEqualTo(6);
        assertThat(registered.contentHash()).hasSize(64);

        assertThat(objectStorageService.exists(loc.bucket(), loc.storageKey())).isTrue();
        verify(pdfFetcher, times(1)).fetch(any(URI.class), any(Path.class));
    }

    @Test
    void locateFile_cacheHit_returnsExistingLocation_noUpstreamCall() {
        Book book = saveShamelaBook(6, "01_book.pdf");

        PdfLocation first = provider.locateFile(book, 0);
        verify(pdfFetcher, times(1)).fetch(any(URI.class), any(Path.class));

        PdfLocation second = provider.locateFile(book, 0);

        assertThat(second.storageKey()).isEqualTo(first.storageKey());
        assertThat(second.sizeBytes()).isEqualTo(first.sizeBytes());
        // Upstream НЕ вызывался повторно - чисто catalog hit
        verify(pdfFetcher, times(1)).fetch(any(URI.class), any(Path.class));
    }

    @Test
    void locateFile_returnedLocation_isReadableViaObjectStorageService() throws Exception {
        Book book = saveShamelaBook(6, "01_book.pdf");

        PdfLocation loc = provider.locateFile(book, 0);

        try (var stream = objectStorageService.get(loc.bucket(), loc.storageKey())) {
            byte[] read = stream.readAllBytes();
            assertThat(read).containsExactly(upstreamPdfBytes);
        }
    }

    @Test
    void locateFile_archiveOrgBookWithoutShamelaMajor_registersAsArchiveOrgType() {
        Book book = saveArchiveOrgBook("vol_1.pdf");

        PdfLocation loc = provider.locateFile(book, 0);

        LibraryFile registered = libraryFileRepository
                .findActiveByBucketAndKey(loc.bucket(), loc.storageKey()).orElseThrow();
        assertThat(registered.sourceType()).isEqualTo(LibraryFileSourceType.ARCHIVE_ORG);
        assertThat(registered.shamelaMajorRelease()).isNull();
    }

    @Test
    void locateFile_invalidFileIndex_throwsPdfNotAvailable() {
        Book book = saveShamelaBook(6, "01_book.pdf");

        assertThatThrownBy(() -> provider.locateFile(book, 99))
                .isInstanceOf(PdfNotAvailableException.class);
    }

    @Test
    void supports_returnsFalseForBookWithoutPdfLinks() {
        Book book = bookRepository.save(new Book(
                UUID.randomUUID(), BookType.BOOK, "No PDF book",
                null, "ar", null, "{}", userId, Instant.now(), Instant.now(),
                null, null, null, null, null, null));

        assertThat(provider.supports(book)).isFalse();
    }

    @Test
    void supports_returnsTrueForBookWithPdfLinks() {
        Book book = saveShamelaBook(6, "01_book.pdf");

        assertThat(provider.supports(book)).isTrue();
    }

    @Test
    void locateFile_multiVolume_separateStorageKeys_separateCatalogRows() {
        Book book = saveShamelaBookMultiVolume(6, "01_book.pdf", "02_book.pdf");

        provider.locateFile(book, 0);
        provider.locateFile(book, 1);

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
                Instant.now(), Instant.now(),
                null, null, null, null, null, null));
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
                Instant.now(), Instant.now(),
                null, null, null, null, null, null));
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
                Instant.now(), Instant.now(),
                null, null, null, null, null, null));
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
