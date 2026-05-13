package ru.basnukaev.argumentmap.library.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

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
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.LibraryFileRepository;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.VersioningConfiguration;

/**
 * Integration test для {@link ObjectStorageService} через Testcontainers
 * MinIO. Container shared между всеми тестами класса (static) -
 * один startup ~5-10 сек amortize.
 *
 * {@code @BeforeEach} создаёт bucket'ы если отсутствуют (idempotent) и
 * очищает их contents - test isolation.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Testcontainers
class ObjectStorageServiceIT {

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
    private ObjectStorageService service;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private LibraryFileRepository libraryFileRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private ObjectStorageProperties properties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String importedBooksBucket;
    private String derivedBucket;
    private UUID userId;

    @BeforeEach
    void setUp() {
        importedBooksBucket = properties.buckets().importedBooks();
        derivedBucket = properties.buckets().derived();

        ensureBucket(importedBooksBucket, true);
        ensureBucket(properties.buckets().userUploads(), true);
        ensureBucket(properties.buckets().pageImages(), true);
        ensureBucket(derivedBucket, false);

        clearBucket(importedBooksBucket);
        clearBucket(derivedBucket);

        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "user-" + userId, userId + "@example.com");
    }

    @Test
    void put_storesContent_andReturnsSha256HashAndSize() throws Exception {
        byte[] content = "Hello, MinIO!".getBytes(StandardCharsets.UTF_8);
        String expectedHash = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(content));

        PutResult result = service.put(importedBooksBucket, "greeting.txt",
                new ByteArrayInputStream(content), "text/plain");

        assertThat(result.contentHash()).hasSize(64).isEqualTo(expectedHash);
        assertThat(result.sizeBytes()).isEqualTo(content.length);
        assertThat(result.etag()).isNotBlank();
        assertThat(result.versionId()).isNotBlank();
    }

    @Test
    void put_emptyContent_returnsZeroSizeAndKnownEmptyHash() {
        PutResult result = service.put(importedBooksBucket, "empty.bin",
                new ByteArrayInputStream(new byte[0]), "application/octet-stream");

        assertThat(result.sizeBytes()).isZero();
        // SHA-256 of empty string
        assertThat(result.contentHash())
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void put_sameKey_creates2VersionsInVersionedBucket() {
        service.put(importedBooksBucket, "v.txt",
                new ByteArrayInputStream("v1".getBytes()), "text/plain");
        service.put(importedBooksBucket, "v.txt",
                new ByteArrayInputStream("v2-updated".getBytes()), "text/plain");

        ListObjectVersionsResponse versions = s3Client.listObjectVersions(
                r -> r.bucket(importedBooksBucket).prefix("v.txt"));
        long count = versions.versions().stream()
                .filter(v -> v.key().equals("v.txt"))
                .count();
        assertThat(count).isEqualTo(2);
    }

    @Test
    void putAndRegister_savesRowInLibraryFiles_withCorrectFields() {
        Book book = bookRepository.save(book("Шамаиль Мухаммедия"));
        byte[] content = "pdf-bytes".getBytes(StandardCharsets.UTF_8);

        LibraryFile registered = service.putAndRegister(
                importedBooksBucket, book.id() + "/01.pdf",
                new ByteArrayInputStream(content), "application/pdf",
                book.id(), "https://archive.org/x.pdf",
                LibraryFileSourceType.SHAMELA, 6, "{\"vol\":1}");

        assertThat(registered.bookId()).isEqualTo(book.id());
        assertThat(registered.bucket()).isEqualTo(importedBooksBucket);
        assertThat(registered.storageKey()).isEqualTo(book.id() + "/01.pdf");
        assertThat(registered.sourceType()).isEqualTo(LibraryFileSourceType.SHAMELA);
        assertThat(registered.contentHash()).hasSize(64);
        assertThat(registered.sizeBytes()).isEqualTo(content.length);
        assertThat(registered.shamelaMajorRelease()).isEqualTo(6);
        assertThat(registered.metadata()).contains("\"vol\"");

        LibraryFile reloaded = libraryFileRepository.findById(registered.fileId()).orElseThrow();
        assertThat(reloaded.contentHash()).isEqualTo(registered.contentHash());
    }

    @Test
    void putAndRegister_reupload_updatesExistingRow_notDuplicates() {
        Book book = bookRepository.save(book("X"));
        String key = book.id() + "/01.pdf";

        LibraryFile first = service.putAndRegister(importedBooksBucket, key,
                new ByteArrayInputStream("v1".getBytes()), "application/pdf",
                book.id(), "https://x/v1", LibraryFileSourceType.SHAMELA, 6, null);
        LibraryFile second = service.putAndRegister(importedBooksBucket, key,
                new ByteArrayInputStream("v2-longer".getBytes()), "application/pdf",
                book.id(), "https://x/v2", LibraryFileSourceType.SHAMELA, 7, null);

        assertThat(second.fileId()).isEqualTo(first.fileId());
        assertThat(second.contentHash()).isNotEqualTo(first.contentHash());
        assertThat(second.sizeBytes()).isEqualTo(9);
        assertThat(second.shamelaMajorRelease()).isEqualTo(7);

        assertThat(libraryFileRepository.findActiveByBookId(book.id())).hasSize(1);
    }

    @Test
    void get_returnsContentMatchingPut() throws Exception {
        byte[] content = randomBytes(4096);
        service.put(importedBooksBucket, "blob.bin",
                new ByteArrayInputStream(content), "application/octet-stream");

        try (ResponseInputStream<GetObjectResponse> in = service.get(
                importedBooksBucket, "blob.bin")) {
            assertThat(in.readAllBytes()).containsExactly(content);
        }
    }

    @Test
    void getRange_returnsExactSubset() throws Exception {
        byte[] content = IntStream.range(0, 100)
                .collect(java.io.ByteArrayOutputStream::new,
                        (b, i) -> b.write(i),
                        (a, b) -> {})
                .toByteArray();
        service.put(importedBooksBucket, "ranged.bin",
                new ByteArrayInputStream(content), "application/octet-stream");

        try (ResponseInputStream<GetObjectResponse> in = service.getRange(
                importedBooksBucket, "ranged.bin", 10, 19)) {
            byte[] chunk = in.readAllBytes();
            assertThat(chunk).hasSize(10);
            for (int i = 0; i < 10; i++) {
                assertThat(chunk[i]).isEqualTo((byte) (10 + i));
            }
        }
    }

    @Test
    void getRange_endBeyondFile_returnsTruncatedChunk() throws Exception {
        byte[] content = randomBytes(50);
        service.put(importedBooksBucket, "small.bin",
                new ByteArrayInputStream(content), "application/octet-stream");

        try (ResponseInputStream<GetObjectResponse> in = service.getRange(
                importedBooksBucket, "small.bin", 40, 1000)) {
            assertThat(in.readAllBytes()).hasSize(10);
        }
    }

    @Test
    void exists_returnsTrueForUploaded() {
        service.put(importedBooksBucket, "presence.txt",
                new ByteArrayInputStream("p".getBytes()), "text/plain");

        assertThat(service.exists(importedBooksBucket, "presence.txt")).isTrue();
    }

    @Test
    void exists_returnsFalseForMissing() {
        assertThat(service.exists(importedBooksBucket, "ghost.txt")).isFalse();
    }

    @Test
    void headObject_returnsCorrectMetadata() {
        byte[] content = "metadata-target".getBytes(StandardCharsets.UTF_8);
        service.put(importedBooksBucket, "meta.txt",
                new ByteArrayInputStream(content), "text/plain");

        StoredObject meta = service.headObject(importedBooksBucket, "meta.txt");

        assertThat(meta.sizeBytes()).isEqualTo(content.length);
        assertThat(meta.contentType()).isEqualTo("text/plain");
        assertThat(meta.etag()).isNotBlank();
        assertThat(meta.versionId()).isNotBlank();
        assertThat(meta.lastModified()).isAfter(Instant.now().minusSeconds(60));
    }

    @Test
    void headObject_missingKey_throwsNoSuchKeyException() {
        assertThatThrownBy(() -> service.headObject(importedBooksBucket, "ghost.txt"))
                .isInstanceOf(software.amazon.awssdk.services.s3.model.S3Exception.class);
    }

    @Test
    void softDelete_marksCatalogRow_andHidesObjectFromGet() {
        Book book = bookRepository.save(book("X"));
        LibraryFile file = service.putAndRegister(importedBooksBucket,
                book.id() + "/01.pdf",
                new ByteArrayInputStream("content".getBytes()), "application/pdf",
                book.id(), null, LibraryFileSourceType.SHAMELA, 6, null);

        boolean ok = service.softDelete(file);

        assertThat(ok).isTrue();
        assertThat(libraryFileRepository.findActiveByBookId(book.id())).isEmpty();
        LibraryFile catalogRow = libraryFileRepository.findById(file.fileId()).orElseThrow();
        assertThat(catalogRow.deletedAt()).isNotNull();
        assertThatThrownBy(() -> service.get(importedBooksBucket, file.storageKey()))
                .isInstanceOfAny(NoSuchKeyException.class,
                        software.amazon.awssdk.services.s3.model.S3Exception.class);
    }

    @Test
    void softDelete_versionsRetainedInBucket_forAuditTrail() {
        Book book = bookRepository.save(book("X"));
        LibraryFile file = service.putAndRegister(importedBooksBucket,
                book.id() + "/01.pdf",
                new ByteArrayInputStream("original".getBytes()), "application/pdf",
                book.id(), null, LibraryFileSourceType.SHAMELA, 6, null);

        service.softDelete(file);

        ListObjectVersionsResponse versions = s3Client.listObjectVersions(
                r -> r.bucket(importedBooksBucket).prefix(file.storageKey()));
        long realVersions = versions.versions().stream()
                .filter(v -> v.key().equals(file.storageKey()))
                .count();
        long deleteMarkers = versions.deleteMarkers().stream()
                .filter(m -> m.key().equals(file.storageKey()))
                .count();
        assertThat(realVersions).isEqualTo(1);
        assertThat(deleteMarkers).isEqualTo(1);
    }

    @Test
    void hardDelete_removesAllVersionsAndCatalogRow() {
        Book book = bookRepository.save(book("X"));
        LibraryFile file = service.putAndRegister(importedBooksBucket,
                book.id() + "/01.pdf",
                new ByteArrayInputStream("v1".getBytes()), "application/pdf",
                book.id(), null, LibraryFileSourceType.SHAMELA, 6, null);
        service.putAndRegister(importedBooksBucket, file.storageKey(),
                new ByteArrayInputStream("v2-newer".getBytes()), "application/pdf",
                book.id(), null, LibraryFileSourceType.SHAMELA, 6, null);

        boolean ok = service.hardDelete(file);

        assertThat(ok).isTrue();
        assertThat(libraryFileRepository.findById(file.fileId())).isEmpty();
        ListObjectVersionsResponse versions = s3Client.listObjectVersions(
                r -> r.bucket(importedBooksBucket).prefix(file.storageKey()));
        assertThat(versions.versions().stream()
                .filter(v -> v.key().equals(file.storageKey())).count()).isZero();
        assertThat(versions.deleteMarkers().stream()
                .filter(m -> m.key().equals(file.storageKey())).count()).isZero();
    }

    @Test
    void putAndRegister_resurrectsSoftDeletedRow_clearsDeletedAt() {
        Book book = bookRepository.save(book("X"));
        String key = book.id() + "/01.pdf";
        LibraryFile original = service.putAndRegister(importedBooksBucket, key,
                new ByteArrayInputStream("v1".getBytes()), "application/pdf",
                book.id(), null, LibraryFileSourceType.SHAMELA, 6, null);
        service.softDelete(original);
        // confirm soft-deleted state
        assertThat(libraryFileRepository.findById(original.fileId())
                .orElseThrow().deletedAt()).isNotNull();

        // re-upload с тем же ключом - upsert resurrects row через
        // EXCLUDED.deleted_at = NULL
        LibraryFile resurrected = service.putAndRegister(importedBooksBucket, key,
                new ByteArrayInputStream("v2".getBytes()), "application/pdf",
                book.id(), null, LibraryFileSourceType.SHAMELA, 6, null);

        assertThat(resurrected.fileId()).isEqualTo(original.fileId());
        assertThat(resurrected.deletedAt()).isNull();
        assertThat(libraryFileRepository.findActiveByBucketAndKey(importedBooksBucket, key))
                .isPresent();
    }

    @Test
    void put_inDerivedBucket_storesWithoutVersioning() {
        PutResult result = service.put(derivedBucket, "graph-export.svg",
                new ByteArrayInputStream("<svg/>".getBytes()), "image/svg+xml");

        // versioning не включён - versionId либо null либо "null" string
        assertThat(result.versionId()).satisfiesAnyOf(
                v -> assertThat(v).isNull(),
                v -> assertThat(v).isEqualTo("null"));
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

    private Book book(String title) {
        Instant now = Instant.now();
        return new Book(UUID.randomUUID(), BookType.BOOK, title, null, "ar",
                null, null, userId, now, now);
    }

    private byte[] randomBytes(int size) {
        byte[] arr = new byte[size];
        for (int i = 0; i < size; i++) {
            arr[i] = (byte) (i & 0xFF);
        }
        return arr;
    }
}
