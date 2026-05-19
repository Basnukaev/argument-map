package ru.basnukaev.argumentmap.library.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import ru.basnukaev.argumentmap.SharedMinioContainer;
import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookVisibility;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.LibraryFile;
import ru.basnukaev.argumentmap.library.domain.LibraryFileSourceType;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.LibraryFileRepository;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

/**
 * Integration test для {@link OrphanDetectionJanitor} через Testcontainers
 * MinIO. Janitor явно включается через {@code @TestPropertySource}
 * {@code storage.janitor.enabled=true} - в обычных условиях (главном
 * application.yml) выключен.
 *
 * <p>Cron заменён на never-firing expression ({@code -}) чтобы Spring
 * не запускал sweep автоматически - тесты вызывают
 * {@link OrphanDetectionJanitor#detectOrphans()} явно для verifiable
 * результата.
 *
 * <p>Сценарии:
 * <ul>
 *   <li>Matched pair (S3 + catalog) → no orphans</li>
 *   <li>S3 object без catalog row → s3-only orphan detected</li>
 *   <li>Catalog row без S3 object → catalog-only orphan detected</li>
 *   <li>Soft-deleted catalog row + S3 object → s3-only orphan
 *       (deleted_at IS NOT NULL == не active)</li>
 * </ul>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "storage.janitor.enabled=true",
        // 31 февраля - cron не fire'нет автоматически, sweep только вручную
        "storage.janitor.cron=0 0 0 31 2 ?"
})
class OrphanDetectionJanitorIT {

    @DynamicPropertySource
    static void minioProperties(DynamicPropertyRegistry r) {
        SharedMinioContainer.applyProperties(r);
    }

    @Autowired private OrphanDetectionJanitor janitor;
    @Autowired private ObjectStorageService storage;
    @Autowired private S3Client s3Client;
    @Autowired private LibraryFileRepository libraryFileRepository;
    @Autowired private BookRepository bookRepository;
    @Autowired private ObjectStorageProperties properties;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String bucket;
    private UUID userId;

    @BeforeEach
    void setUp() {
        bucket = properties.buckets().importedBooks();
        ensureBucket(bucket);
        ensureBucket(properties.buckets().userUploads());
        ensureBucket(properties.buckets().pageImages());
        ensureBucket(properties.buckets().derived());
        clearBucket(bucket);
        clearBucket(properties.buckets().userUploads());
        clearBucket(properties.buckets().pageImages());
        clearBucket(properties.buckets().derived());
        jdbcTemplate.update("DELETE FROM library_files");

        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "user-" + userId, userId + "@example.com");
    }

    @Test
    void detectOrphans_matchedPair_noOrphans() {
        Book book = bookRepository.save(book("Matched"));
        storage.putAndRegister(bucket, book.id() + "/01.pdf",
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)),
                "application/pdf",
                book.id(), null, LibraryFileSourceType.SHAMELA, 6, null);

        OrphanDetectionJanitor.OrphanSweepResult result = janitor.detectOrphans();

        assertThat(result.getS3OnlyOrphans()).isZero();
        assertThat(result.getCatalogOnlyOrphans()).isZero();
        assertThat(result.getS3Scanned()).isGreaterThanOrEqualTo(1);
        assertThat(result.getCatalogScanned()).isEqualTo(1);
    }

    @Test
    void detectOrphans_s3ObjectWithoutCatalogRow_detectedAsS3Only() {
        // Прямой put в S3 без registerInCatalog - симулирует ситуацию когда
        // объект попал в bucket вне обычного flow (manual upload, partial
        // failure, миграция из старой системы)
        storage.put(bucket, "ghost-key.pdf",
                new ByteArrayInputStream("orphaned".getBytes(StandardCharsets.UTF_8)),
                "application/pdf");

        OrphanDetectionJanitor.OrphanSweepResult result = janitor.detectOrphans();

        assertThat(result.getS3OnlyOrphans()).isEqualTo(1);
        assertThat(result.getCatalogOnlyOrphans()).isZero();
    }

    @Test
    void detectOrphans_catalogRowWithoutS3Object_detectedAsCatalogOnly() {
        // Прямой insert в catalog без physical S3 object - симулирует
        // ситуацию когда blob удалили вне catalog flow (manual `mc rm`,
        // disaster recovery с частичным restore, retention policy provider'а)
        Book book = bookRepository.save(book("PhantomCatalog"));
        LibraryFile phantom = new LibraryFile(
                UUID.randomUUID(), book.id(), bucket, "no-such-object.pdf",
                null, LibraryFileSourceType.SHAMELA, "deadbeef".repeat(8),
                1024L, "etag", Instant.now(), null, 6, "{}", null);
        libraryFileRepository.save(phantom);

        OrphanDetectionJanitor.OrphanSweepResult result = janitor.detectOrphans();

        assertThat(result.getCatalogOnlyOrphans()).isEqualTo(1);
        assertThat(result.getS3OnlyOrphans()).isZero();
        assertThat(result.getCatalogScanned()).isEqualTo(1);
    }

    @Test
    void detectOrphans_softDeletedRowPlusS3Object_detectedAsS3Only() {
        // soft-deleted catalog row + объект остался в bucket (versioned -
        // delete-marker hide latest, но object versions/orphan list всё ещё
        // показывают key через listObjectsV2 если в non-versioned bucket
        // или если delete-marker hide. Этот сценарий ловит case когда
        // softDelete был частично завершён - catalog ok, S3 не удалил)
        Book book = bookRepository.save(book("SoftDeleted"));
        // Прямой put + ручной insert catalog row с deleted_at - чтобы
        // обойти softDelete который удаляет и S3 object тоже
        String key = book.id() + "/soft.pdf";
        storage.put(bucket, key,
                new ByteArrayInputStream("soft".getBytes(StandardCharsets.UTF_8)),
                "application/pdf");
        LibraryFile softDeletedRow = new LibraryFile(
                UUID.randomUUID(), book.id(), bucket, key,
                null, LibraryFileSourceType.SHAMELA, "hash".repeat(16),
                4L, "etag", Instant.now(), null, 6, "{}",
                Instant.now()); // deleted_at != null
        libraryFileRepository.save(softDeletedRow);

        OrphanDetectionJanitor.OrphanSweepResult result = janitor.detectOrphans();

        // findActiveByBucketAndKey фильтрует deleted_at IS NULL → object
        // считается без active catalog row → s3-only orphan
        assertThat(result.getS3OnlyOrphans()).isEqualTo(1);
        // soft-deleted не попадает в findAllActive → reverse sweep его не
        // видит, поэтому catalog-only = 0 (правильное поведение - не
        // путать soft-deleted с настоящим orphan'ом)
        assertThat(result.getCatalogOnlyOrphans()).isZero();
        assertThat(result.getCatalogScanned()).isZero();
    }

    @Test
    void detectOrphans_multiBucketSweep_aggregatesAcrossAllFour() {
        // По одному orphan'у в imported-books и user-uploads
        storage.put(bucket, "imported-orphan.pdf",
                new ByteArrayInputStream("a".getBytes()), "application/pdf");
        storage.put(properties.buckets().userUploads(), "user-orphan.pdf",
                new ByteArrayInputStream("b".getBytes()), "application/pdf");

        OrphanDetectionJanitor.OrphanSweepResult result = janitor.detectOrphans();

        assertThat(result.getS3OnlyOrphans()).isEqualTo(2);
        assertThat(result.getS3Scanned()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void detectOrphans_mixedScenario_correctlyCountsBothTypes() {
        Book book = bookRepository.save(book("Mixed"));

        // 1. Matched pair
        storage.putAndRegister(bucket, book.id() + "/matched.pdf",
                new ByteArrayInputStream("matched".getBytes()), "application/pdf",
                book.id(), null, LibraryFileSourceType.SHAMELA, 6, null);

        // 2. S3-only (объект без catalog)
        storage.put(bucket, "s3-only.pdf",
                new ByteArrayInputStream("s3only".getBytes()), "application/pdf");

        // 3. Catalog-only (row без объекта)
        LibraryFile phantom = new LibraryFile(
                UUID.randomUUID(), book.id(), bucket, "catalog-only.pdf",
                null, LibraryFileSourceType.SHAMELA, "deadbeef".repeat(8),
                1024L, null, Instant.now(), null, 6, "{}", null);
        libraryFileRepository.save(phantom);

        OrphanDetectionJanitor.OrphanSweepResult result = janitor.detectOrphans();

        assertThat(result.getS3OnlyOrphans()).isEqualTo(1);
        assertThat(result.getCatalogOnlyOrphans()).isEqualTo(1);
        // catalogScanned = matched + phantom = 2
        assertThat(result.getCatalogScanned()).isEqualTo(2);
    }

    private void ensureBucket(String b) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(b).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(b).build());
        } catch (Exception e) {
            // bucket уже есть в другом state - продолжаем
        }
    }

    private void clearBucket(String b) {
        ListObjectVersionsResponse versions = s3Client.listObjectVersions(r -> r.bucket(b));
        versions.versions().forEach(v -> s3Client.deleteObject(
                r -> r.bucket(b).key(v.key()).versionId(v.versionId())));
        versions.deleteMarkers().forEach(m -> s3Client.deleteObject(
                r -> r.bucket(b).key(m.key()).versionId(m.versionId())));
    }

    private Book book(String title) {
        Instant now = Instant.now();
        return new Book(UUID.randomUUID(), BookType.BOOK, title, null, "ar",
                null, null, userId, now, now,
                null, null, null, null, null, null, BookVisibility.PUBLIC);
    }
}
