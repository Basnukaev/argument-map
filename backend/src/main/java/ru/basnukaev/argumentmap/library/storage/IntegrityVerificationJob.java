package ru.basnukaev.argumentmap.library.storage;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ru.basnukaev.argumentmap.library.domain.LibraryFile;
import ru.basnukaev.argumentmap.library.repository.LibraryFileRepository;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Cron job для еженедельной проверки integrity физических объектов в S3
 * против {@code content_hash} в Postgres catalog (ADR-024, Этап 25.b,
 * 6-й пункт operational hardening).
 *
 * <p>Дополняет {@link OrphanDetectionJanitor}: тот ловит несоответствие
 * «есть/нет», этот - bit-rot и silent corruption (объект на месте, но
 * байты отличаются от того что было записано). Типичные источники:
 * disk-rot, провайдерский баг replication, ручное mc cp поверх объекта,
 * partial restore из backup.
 *
 * <p>Запускается по cron из {@code storage.integrity.cron} (по умолчанию
 * {@code 0 0 4 * * SUN} - воскресенье 04:00: после ночных backup'ов,
 * минимальная user-нагрузка, weekly cadence потому что SHA-256 sweep
 * всей библиотеки тяжелее ежедневного orphan-list - читаем full body
 * каждого blob'а). Активируется через {@code storage.integrity.enabled=true} -
 * по умолчанию выключен (избегаем accidental sweep в dev/test).
 *
 * <p>Алгоритм:
 * <ul>
 *   <li>{@code findAllActive} → для каждой row {@code getObject} +
 *       streaming SHA-256 через {@link MessageDigest}. Hash сравнивается
 *       case-insensitive с {@link LibraryFile#contentHash()}</li>
 *   <li>Mismatch → {@code log.error} CORRUPTION с (fileId, bucket, key,
 *       expected, actual). Не fix'им автоматически - manual review
 *       (потенциально нужно re-download из source_url или restore из
 *       backup)</li>
 *   <li>{@link NoSuchKeyException} → {@code log.warn} MISSING. Не
 *       дублируем отдельный counter/report - это покрыто
 *       {@link OrphanDetectionJanitor} reverse sweep'ом</li>
 *   <li>S3 transient errors → {@code log.warn}, продолжаем со
 *       следующей row (не валим весь sweep)</li>
 * </ul>
 *
 * <p>Throttling: между files делаем {@link Thread#sleep} на
 * {@code storage.integrity.delay-millis} (default 100ms) - чтобы не
 * нагрузить S3 endpoint при крупной library. В IT тестах ставим 0
 * для быстрого прогона.
 *
 * <p>Перформанс: для library 10k files × средний размер 20MB → ~200GB
 * read из S3. Network throughput 100Mbps → ~4.5 часа. Это acceptable
 * для weekly job на воскресной ночи. На объёмах 100k+ перейти на
 * sampled verification (random subset) или offload в отдельный worker.
 */
@Component
@ConditionalOnProperty(prefix = "storage.integrity", name = "enabled", havingValue = "true")
public class IntegrityVerificationJob {

    private static final Logger log = LoggerFactory.getLogger(IntegrityVerificationJob.class);
    private static final HexFormat HEX = HexFormat.of();
    /**
     * 64KB - тот же буфер что и в {@link ObjectStorageService} put-flow.
     * Balance между memory footprint и syscall overhead на bulk IO.
     */
    private static final int BUFFER_SIZE = 64 * 1024;

    private final S3Client s3Client;
    private final LibraryFileRepository libraryFileRepository;
    private final long delayMillis;

    public IntegrityVerificationJob(
            S3Client s3Client,
            LibraryFileRepository libraryFileRepository,
            IntegrityVerificationProperties properties) {
        this.s3Client = s3Client;
        this.libraryFileRepository = libraryFileRepository;
        this.delayMillis = properties.delayMillis();
    }

    /**
     * Главный entry-point. Запускается по cron из properties. Возвращает
     * {@link IntegrityResult} - используется тестами и опционально
     * exposed через admin endpoint в будущем.
     */
    @Scheduled(cron = "${storage.integrity.cron:0 0 4 * * SUN}")
    public IntegrityResult verifyIntegrity() {
        Instant startedAt = Instant.now();
        log.info("IntegrityVerificationJob: sweep начат");

        IntegrityResult result = new IntegrityResult();
        List<LibraryFile> active = libraryFileRepository.findAllActive();

        for (LibraryFile file : active) {
            result.incScanned();
            verifyFile(file, result);
            sleepBetweenFiles();
        }

        Duration elapsed = Duration.between(startedAt, Instant.now());
        log.info("IntegrityVerificationJob: sweep завершён за {}s. "
                        + "scanned={}, corrupted={}, missing={}, errors={}",
                elapsed.toSeconds(),
                result.getScanned(),
                result.getCorrupted(),
                result.getMissing(),
                result.getErrors());
        return result;
    }

    /**
     * Verify одного файла: stream body через SHA-256, сравнение hex
     * case-insensitive с catalog hash. Все exception flows
     * увеличивают соответствующий counter и продолжают со следующего
     * файла (sweep не падает целиком).
     */
    private void verifyFile(LibraryFile file, IntegrityResult result) {
        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(file.bucket())
                .key(file.storageKey())
                .build();

        try (ResponseInputStream<GetObjectResponse> body = s3Client.getObject(req)) {
            String actualHash = streamSha256(body);
            String expectedHash = file.contentHash();

            if (!actualHash.equalsIgnoreCase(expectedHash)) {
                result.incCorrupted();
                log.error(
                        "integrity CORRUPTION fileId={} bucket={} key={} expectedHash={} actualHash={}",
                        file.fileId(), file.bucket(), file.storageKey(),
                        expectedHash, actualHash);
            }
        } catch (NoSuchKeyException e) {
            recordMissing(file, result);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                // Некоторые SDK версии выдают S3Exception 404 вместо typed
                // NoSuchKeyException - оба обрабатываем как missing
                recordMissing(file, result);
            } else {
                result.incErrors();
                log.warn(
                        "IntegrityVerificationJob: skipped fileId={} bucket={} key={} из-за S3 ошибки HTTP {}",
                        file.fileId(), file.bucket(), file.storageKey(), e.statusCode());
            }
        } catch (IOException e) {
            result.incErrors();
            log.warn(
                    "IntegrityVerificationJob: skipped fileId={} bucket={} key={} из-за IO ошибки: {}",
                    file.fileId(), file.bucket(), file.storageKey(), e.getMessage());
        }
    }

    private void recordMissing(LibraryFile file, IntegrityResult result) {
        result.incMissing();
        // Не создаём отдельный report - catalog-only orphan уже покрыт
        // OrphanDetectionJanitor reverse sweep. Здесь просто log для
        // self-contained трейса этого sweep'а
        log.warn(
                "integrity MISSING fileId={} bucket={} key={} (см. OrphanDetectionJanitor для consolidated orphan report)",
                file.fileId(), file.bucket(), file.storageKey());
    }

    /**
     * Throttle между files. В реальном prod ~100ms даёт ~10 files/sec
     * cap на S3 throughput - safe для shared endpoint'а. В тестах
     * ставим 0 через {@code storage.integrity.delay-millis=0}.
     */
    private void sleepBetweenFiles() {
        if (delayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("IntegrityVerificationJob: прерван между files");
        }
    }

    /**
     * Streaming SHA-256: читаем body chunk by chunk, обновляем digest
     * без загрузки всего blob'а в memory. Возвращает hex-encoded hash.
     */
    private String streamSha256(InputStream content) throws IOException {
        MessageDigest digest = newSha256();
        byte[] buf = new byte[BUFFER_SIZE];
        int n;
        while ((n = content.read(buf)) != -1) {
            digest.update(buf, 0, n);
        }
        return HEX.formatHex(digest.digest());
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 гарантирован JDK с Java 1.4.2+
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Mutable аккумулятор результатов sweep'а. Public getters - чтобы
     * IT тесты могли проверять counts. Inc-методы package-private
     * (вызываются только из этого класса).
     */
    public static final class IntegrityResult {
        private int scanned;
        private int corrupted;
        private int missing;
        private int errors;

        void incScanned() { scanned++; }
        void incCorrupted() { corrupted++; }
        void incMissing() { missing++; }
        void incErrors() { errors++; }

        public int getScanned() { return scanned; }
        public int getCorrupted() { return corrupted; }
        public int getMissing() { return missing; }
        public int getErrors() { return errors; }
    }
}
