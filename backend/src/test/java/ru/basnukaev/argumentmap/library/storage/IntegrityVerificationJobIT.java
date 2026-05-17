package ru.basnukaev.argumentmap.library.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
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
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
 * Integration test для {@link IntegrityVerificationJob} через
 * Testcontainers MinIO. Job явно включается через
 * {@code @TestPropertySource} {@code storage.integrity.enabled=true} -
 * в обычном application.yml выключен.
 *
 * <p>Cron заменён на never-firing expression (31 февраля) чтобы Spring
 * не запускал sweep автоматически - тесты вызывают
 * {@link IntegrityVerificationJob#verifyIntegrity()} явно. Delay между
 * files = 0 для быстрого прогона (production default 100ms).
 *
 * <p>Сценарии:
 * <ul>
 *   <li>Object + правильный hash → no corruption, scanned=1</li>
 *   <li>Object + wrong hash в catalog → corrupted=1</li>
 *   <li>Catalog row без S3 object → missing=1</li>
 *   <li>Mixed: 1 ok + 1 corrupted + 1 missing → правильные counters</li>
 * </ul>
 */
@Testcontainers
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "storage.integrity.enabled=true",
        // 31 февраля - cron не fire'нет автоматически, sweep только вручную
        "storage.integrity.cron=0 0 0 31 2 ?",
        // throttle off для быстрых тестов
        "storage.integrity.delay-millis=0"
})
class IntegrityVerificationJobIT {

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

    @Autowired private IntegrityVerificationJob job;
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
    void verifyIntegrity_objectMatchesCatalogHash_noCorruption() {
        // putAndRegister вычисляет SHA-256 сам и сохраняет в catalog -
        // valid baseline scenario
        Book book = bookRepository.save(book("Healthy"));
        storage.putAndRegister(bucket, book.id() + "/healthy.pdf",
                new ByteArrayInputStream("healthy content".getBytes(StandardCharsets.UTF_8)),
                "application/pdf",
                book.id(), null, LibraryFileSourceType.SHAMELA, 6, null);

        IntegrityVerificationJob.IntegrityResult result = job.verifyIntegrity();

        assertThat(result.getScanned()).isEqualTo(1);
        assertThat(result.getCorrupted()).isZero();
        assertThat(result.getMissing()).isZero();
        assertThat(result.getErrors()).isZero();
    }

    @Test
    void verifyIntegrity_objectWithWrongCatalogHash_detectedAsCorrupted() {
        // Put объект через нормальный flow, затем вручную обновляем
        // catalog row с заведомо неверным hash - симулирует bit-rot
        // когда blob изменился (или catalog был corrupt'нут)
        Book book = bookRepository.save(book("BitRot"));
        LibraryFile registered = storage.putAndRegister(bucket, book.id() + "/bitrot.pdf",
                new ByteArrayInputStream("original bytes".getBytes(StandardCharsets.UTF_8)),
                "application/pdf",
                book.id(), null, LibraryFileSourceType.SHAMELA, 6, null);

        // Заведомо невалидный hash (всё нули) - реальный SHA-256
        // "original bytes" совершенно другой
        jdbcTemplate.update(
                "UPDATE library_files SET content_hash = ? WHERE file_id = ?",
                "0".repeat(64), registered.fileId());

        IntegrityVerificationJob.IntegrityResult result = job.verifyIntegrity();

        assertThat(result.getScanned()).isEqualTo(1);
        assertThat(result.getCorrupted()).isEqualTo(1);
        assertThat(result.getMissing()).isZero();
    }

    @Test
    void verifyIntegrity_catalogRowWithoutS3Object_detectedAsMissing() {
        // Прямой insert в catalog без physical S3 object - симулирует
        // ситуацию когда blob удалили вне catalog flow (тот же кейс
        // что OrphanDetectionJanitor catalog-only orphan, но здесь
        // ловится через getObject NoSuchKey а не headObject)
        Book book = bookRepository.save(book("PhantomCatalog"));
        LibraryFile phantom = new LibraryFile(
                UUID.randomUUID(), book.id(), bucket, "no-such-object.pdf",
                null, LibraryFileSourceType.SHAMELA, "deadbeef".repeat(8),
                1024L, "etag", Instant.now(), null, 6, "{}", null);
        libraryFileRepository.save(phantom);

        IntegrityVerificationJob.IntegrityResult result = job.verifyIntegrity();

        assertThat(result.getScanned()).isEqualTo(1);
        assertThat(result.getMissing()).isEqualTo(1);
        assertThat(result.getCorrupted()).isZero();
    }

    @Test
    void verifyIntegrity_softDeletedRow_skippedFromSweep() {
        // soft-deleted rows не возвращаются findAllActive → integrity
        // sweep их не трогает. Гарантируем consistency с
        // OrphanDetectionJanitor (тот reverse sweep тоже работает по
        // findAllActive)
        Book book = bookRepository.save(book("SoftDeleted"));
        LibraryFile softDeleted = new LibraryFile(
                UUID.randomUUID(), book.id(), bucket, "soft.pdf",
                null, LibraryFileSourceType.SHAMELA, "hash".repeat(16),
                4L, "etag", Instant.now(), null, 6, "{}",
                Instant.now()); // deleted_at != null
        libraryFileRepository.save(softDeleted);

        IntegrityVerificationJob.IntegrityResult result = job.verifyIntegrity();

        assertThat(result.getScanned()).isZero();
        assertThat(result.getCorrupted()).isZero();
        assertThat(result.getMissing()).isZero();
    }

    @Test
    void verifyIntegrity_mixedScenario_correctlyCountsAllOutcomes() {
        Book book = bookRepository.save(book("Mixed"));

        // 1. Healthy: правильный hash через putAndRegister
        storage.putAndRegister(bucket, book.id() + "/healthy.pdf",
                new ByteArrayInputStream("healthy".getBytes()), "application/pdf",
                book.id(), null, LibraryFileSourceType.SHAMELA, 6, null);

        // 2. Corrupted: object на месте, hash в catalog подменен
        LibraryFile corrupted = storage.putAndRegister(bucket, book.id() + "/corrupted.pdf",
                new ByteArrayInputStream("corrupted".getBytes()), "application/pdf",
                book.id(), null, LibraryFileSourceType.SHAMELA, 6, null);
        jdbcTemplate.update(
                "UPDATE library_files SET content_hash = ? WHERE file_id = ?",
                "ffff".repeat(16), corrupted.fileId());

        // 3. Missing: catalog row без S3 object
        LibraryFile phantom = new LibraryFile(
                UUID.randomUUID(), book.id(), bucket, "phantom.pdf",
                null, LibraryFileSourceType.SHAMELA, "deadbeef".repeat(8),
                1024L, null, Instant.now(), null, 6, "{}", null);
        libraryFileRepository.save(phantom);

        IntegrityVerificationJob.IntegrityResult result = job.verifyIntegrity();

        assertThat(result.getScanned()).isEqualTo(3);
        assertThat(result.getCorrupted()).isEqualTo(1);
        assertThat(result.getMissing()).isEqualTo(1);
        assertThat(result.getErrors()).isZero();
    }

    @Test
    void verifyIntegrity_hashCheckIsCaseInsensitive() {
        // SHA-256 hex может быть upper/lower case в разных источниках
        // (наш HexFormat.of() даёт lower-case, но catalog мог быть
        // импортирован из системы с upper-case). Verify работает
        // case-insensitive
        Book book = bookRepository.save(book("CaseTest"));
        LibraryFile registered = storage.putAndRegister(bucket, book.id() + "/case.pdf",
                new ByteArrayInputStream("case content".getBytes()), "application/pdf",
                book.id(), null, LibraryFileSourceType.SHAMELA, 6, null);

        // Переводим hash в upper-case в catalog
        jdbcTemplate.update(
                "UPDATE library_files SET content_hash = UPPER(content_hash) WHERE file_id = ?",
                registered.fileId());

        IntegrityVerificationJob.IntegrityResult result = job.verifyIntegrity();

        assertThat(result.getCorrupted()).isZero();
        assertThat(result.getScanned()).isEqualTo(1);
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
