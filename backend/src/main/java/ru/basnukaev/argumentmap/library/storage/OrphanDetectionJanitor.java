package ru.basnukaev.argumentmap.library.storage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ru.basnukaev.argumentmap.library.domain.LibraryFile;
import ru.basnukaev.argumentmap.library.repository.LibraryFileRepository;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

/**
 * Cron janitor для детекции orphan-файлов между Postgres catalog
 * ({@code library_files}) и S3-compatible object storage (ADR-024,
 * Этап 25.b operational hardening, 5-й пункт).
 *
 * <p>Запускается по cron из {@code storage.janitor.cron} (по умолчанию
 * {@code 0 0 3 * * *} - 03:00 ежедневно: после backup'ов, до user-traffic
 * peak). Активируется через {@code storage.janitor.enabled=true} - по
 * умолчанию выключен (избегаем accidental scan в dev/test).
 *
 * <p>Два sweep'а:
 * <ul>
 *   <li><b>Forward sweep</b> (S3 → catalog) - для каждого bucket
 *       {@code listObjectsV2Paginator} → per object key проверка
 *       {@code findActiveByBucketAndKey}. Если row отсутствует или
 *       soft-deleted - object orphan в S3 (есть файл в bucket'е без
 *       активной catalog row)</li>
 *   <li><b>Reverse sweep</b> (catalog → S3) - {@code findAllActive} → per
 *       row {@code headObject}. Если {@link NoSuchKeyException} - catalog
 *       orphan (есть row но нет физического объекта)</li>
 * </ul>
 *
 * <p><b>Log-only, no auto-delete</b>: каждый orphan записывается через
 * {@code log.warn} с (type, bucket, key, size, age). Manual review через
 * grep логов или actuator metrics. Risk автоудаления - delete активного
 * файла при race condition (concurrent putAndRegister между listObjects
 * и check), поэтому решение пользователя через CLI / admin endpoint.
 *
 * <p>Перформанс: forward sweep делает 1 listObjectsV2 request на ~1000
 * keys + 1 SELECT на key. Для bucket'а с 10k files - ~10 list requests +
 * 10k catalog lookups (используется partial index
 * {@code idx_library_files_active}). Reverse sweep делает 1 SELECT all
 * + N headObject'ов. На объёмах 100k+ объектов перейти на cursor-based
 * batch processing.
 */
@Component
@ConditionalOnProperty(prefix = "storage.janitor", name = "enabled", havingValue = "true")
public class OrphanDetectionJanitor {

    private static final Logger log = LoggerFactory.getLogger(OrphanDetectionJanitor.class);

    private final S3Client s3Client;
    private final LibraryFileRepository libraryFileRepository;
    private final ObjectStorageProperties properties;

    public OrphanDetectionJanitor(
            S3Client s3Client,
            LibraryFileRepository libraryFileRepository,
            ObjectStorageProperties properties) {
        this.s3Client = s3Client;
        this.libraryFileRepository = libraryFileRepository;
        this.properties = properties;
    }

    /**
     * Главный entry-point. Запускается по cron из properties. Возвращает
     * {@link OrphanSweepResult} - используется тестами и опционально
     * exposed через admin endpoint в будущем.
     */
    @Scheduled(cron = "${storage.janitor.cron:0 0 3 * * *}")
    public OrphanSweepResult detectOrphans() {
        Instant startedAt = Instant.now();
        log.info("OrphanDetectionJanitor: sweep начат, endpoint={}", properties.endpoint());

        OrphanSweepResult result = new OrphanSweepResult();

        // Forward sweep: каждый bucket → list → check catalog
        for (String bucket : allBuckets()) {
            sweepBucketForward(bucket, result);
        }

        // Reverse sweep: catalog rows → check S3 headObject
        sweepCatalogReverse(result);

        Duration elapsed = Duration.between(startedAt, Instant.now());
        log.info("OrphanDetectionJanitor: sweep завершён за {}s. "
                        + "s3Scanned={}, catalogScanned={}, s3OnlyOrphans={}, catalogOnlyOrphans={}",
                elapsed.toSeconds(),
                result.getS3Scanned(),
                result.getCatalogScanned(),
                result.getS3OnlyOrphans(),
                result.getCatalogOnlyOrphans());
        return result;
    }

    private List<String> allBuckets() {
        ObjectStorageProperties.Buckets b = properties.buckets();
        return List.of(
                b.importedBooks(),
                b.userUploads(),
                b.pageImages(),
                b.derived()
        );
    }

    /**
     * Forward sweep одного bucket'а. listObjectsV2Paginator автоматически
     * листает страницы (1000 keys на страницу по S3 API limit). Для
     * каждого key - lookup в catalog по уникальному (bucket, storageKey).
     */
    private void sweepBucketForward(String bucket, OrphanSweepResult result) {
        try {
            ListObjectsV2Iterable pages = s3Client.listObjectsV2Paginator(
                    ListObjectsV2Request.builder().bucket(bucket).build());

            for (var page : pages) {
                for (S3Object obj : page.contents()) {
                    result.incS3Scanned();
                    String key = obj.key();
                    var active = libraryFileRepository.findActiveByBucketAndKey(bucket, key);
                    if (active.isEmpty()) {
                        result.incS3OnlyOrphan();
                        Duration age = obj.lastModified() != null
                                ? Duration.between(obj.lastModified(), Instant.now())
                                : Duration.ZERO;
                        log.warn(
                                "orphan type=s3-only bucket={} key={} sizeBytes={} ageHours={}",
                                bucket, key, obj.size(), age.toHours());
                    }
                }
            }
        } catch (S3Exception e) {
            // Bucket недоступен / нет прав - не валим весь sweep, переходим к следующему
            log.warn("OrphanDetectionJanitor: forward sweep bucket={} пропущен из-за S3 ошибки: HTTP {} {}",
                    bucket, e.statusCode(),
                    e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage());
        }
    }

    /**
     * Reverse sweep. Для каждой active row в catalog делает headObject -
     * NoSuchKey означает catalog orphan (запись есть, физического blob'а
     * нет). Также S3Exception 404 (некоторые провайдеры/версии SDK не
     * выдают typed NoSuchKeyException).
     */
    private void sweepCatalogReverse(OrphanSweepResult result) {
        List<LibraryFile> active = libraryFileRepository.findAllActive();
        Instant now = Instant.now();
        for (LibraryFile file : active) {
            result.incCatalogScanned();
            try {
                s3Client.headObject(HeadObjectRequest.builder()
                        .bucket(file.bucket())
                        .key(file.storageKey())
                        .build());
            } catch (NoSuchKeyException e) {
                recordCatalogOnly(file, now, result);
            } catch (S3Exception e) {
                if (e.statusCode() == 404) {
                    recordCatalogOnly(file, now, result);
                } else {
                    log.warn(
                            "OrphanDetectionJanitor: reverse sweep skipped fileId={} bucket={} key={} из-за S3 ошибки HTTP {}",
                            file.fileId(), file.bucket(), file.storageKey(), e.statusCode());
                }
            }
        }
    }

    private void recordCatalogOnly(LibraryFile file, Instant now, OrphanSweepResult result) {
        result.incCatalogOnlyOrphan();
        Duration age = file.downloadedAt() != null
                ? Duration.between(file.downloadedAt(), now)
                : Duration.ZERO;
        log.warn(
                "orphan type=catalog-only bucket={} key={} fileId={} sizeBytes={} ageHours={}",
                file.bucket(), file.storageKey(), file.fileId(),
                file.sizeBytes(), age.toHours());
    }

    /**
     * Mutable аккумулятор результатов sweep'а. Public getters - чтобы IT
     * тесты могли проверять counts. Inc-методы package-private (вызываются
     * только из этого класса).
     */
    public static final class OrphanSweepResult {
        private int s3Scanned;
        private int catalogScanned;
        private int s3OnlyOrphans;
        private int catalogOnlyOrphans;

        void incS3Scanned() { s3Scanned++; }
        void incCatalogScanned() { catalogScanned++; }
        void incS3OnlyOrphan() { s3OnlyOrphans++; }
        void incCatalogOnlyOrphan() { catalogOnlyOrphans++; }

        public int getS3Scanned() { return s3Scanned; }
        public int getCatalogScanned() { return catalogScanned; }
        public int getS3OnlyOrphans() { return s3OnlyOrphans; }
        public int getCatalogOnlyOrphans() { return catalogOnlyOrphans; }
    }
}
