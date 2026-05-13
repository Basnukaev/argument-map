package ru.basnukaev.argumentmap.library.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

import ru.basnukaev.argumentmap.library.domain.LibraryFile;
import ru.basnukaev.argumentmap.library.domain.LibraryFileSourceType;
import ru.basnukaev.argumentmap.library.repository.LibraryFileRepository;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Высокоуровневый API для object storage (ADR-024).
 * Обёртывает {@link S3Client} и интегрирует с {@link LibraryFileRepository}
 * (Postgres catalog) для unified put + register flow.
 *
 * <p>Ключевые операции:
 * <ul>
 *   <li>{@code put} - upload с SHA-256 verification. Использует temp file
 *       для retry-safety (AWS SDK может пере-читать stream при transient
 *       errors)</li>
 *   <li>{@code putAndRegister} - high-level: put в bucket + insert/update
 *       в catalog atomically. Идемпотентен через
 *       {@code findActiveByBucketAndKey}</li>
 *   <li>{@code get} / {@code getRange} - download full или partial chunk
 *       через S3 Range header (для streaming больших PDF)</li>
 *   <li>{@code softDelete} - помечает {@code library_files.deleted_at},
 *       создаёт S3 delete-marker (latest version скрыт но история
 *       сохранена для versioned bucket'ов)</li>
 *   <li>{@code hardDelete} - admin two-phase: физически удаляет все
 *       версии объекта в S3 + DELETE row из catalog. Используется только
 *       для GDPR right-to-delete</li>
 * </ul>
 */
@Service
public class ObjectStorageService {

    private static final HexFormat HEX = HexFormat.of();
    /**
     * 64KB - balance между memory footprint и syscall overhead на bulk IO.
     * 8KB давал 6400 iterations на 50MB PDF; 64KB - 800. JDK {@code Files.copy}
     * default - 16KB, но для blob > 10MB крупнее буфер выигрывает.
     */
    private static final int BUFFER_SIZE = 64 * 1024;

    private final S3Client s3Client;
    private final LibraryFileRepository libraryFileRepository;

    public ObjectStorageService(S3Client s3Client, LibraryFileRepository libraryFileRepository) {
        this.s3Client = s3Client;
        this.libraryFileRepository = libraryFileRepository;
    }

    /**
     * Upload объекта в bucket. Stream'ит {@code content} в temp file
     * параллельно вычисляя SHA-256, потом загружает file в S3 через
     * {@code RequestBody.fromFile} - AWS SDK сам управляет retry,
     * содержимое перечитывается с диска.
     *
     * <p>Размер берётся из реального content (через {@code Files.size}
     * после stream'а), не из аргумента - это убирает class ошибок где
     * caller передал неверный hint. Если caller знает size заранее -
     * может использовать его для self-validation после возврата
     * {@link PutResult#sizeBytes()}.
     */
    public PutResult put(String bucket, String storageKey,
                          InputStream content, String contentType) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("objstorage-", ".tmp");
            String contentHash = streamToFileAndHash(content, tempFile);
            long actualSize = Files.size(tempFile);

            PutObjectRequest req = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(storageKey)
                    .contentType(contentType)
                    .contentLength(actualSize)
                    .build();

            PutObjectResponse resp = s3Client.putObject(req, RequestBody.fromFile(tempFile));

            return new PutResult(contentHash, resp.eTag(), actualSize, resp.versionId());
        } catch (IOException e) {
            throw new ObjectStorageException(
                    "не удалось записать объект " + bucket + "/" + storageKey, e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            }
        }
    }

    /**
     * High-level операция: {@link #put} в bucket + atomic upsert в
     * {@link LibraryFileRepository}. Идемпотентен - re-upload того же
     * {@code (bucket, storageKey)} обновляет существующую catalog row
     * вместо создания дубликата.
     *
     * <p>Через {@code upsertByBucketAndKey} (PostgreSQL {@code ON CONFLICT}) -
     * убирает race condition при concurrent first-load. Две parallel сессии
     * могут безопасно вызывать этот метод на один (bucket, key) - вторая
     * перезапишет catalog row первой, в S3 создастся вторая version (versioning
     * ON), читатели через {@code findActive*} видят consistent latest.
     *
     * <p>При re-upload объект в bucket'е получает новую версию (если
     * versioning ON), в catalog обновляется {@code content_hash},
     * {@code etag}, {@code downloaded_at}, {@code size_bytes}.
     */
    public LibraryFile putAndRegister(
            String bucket, String storageKey,
            InputStream content, String contentType,
            UUID bookId, String sourceUrl, LibraryFileSourceType sourceType,
            Integer shamelaMajorRelease, String metadataJson) {

        PutResult result = put(bucket, storageKey, content, contentType);

        LibraryFile candidate = new LibraryFile(
                UUID.randomUUID(), bookId, bucket, storageKey,
                sourceUrl, sourceType, result.contentHash(), result.sizeBytes(),
                result.etag(), Instant.now(), null, shamelaMajorRelease,
                metadataJson != null ? metadataJson : "{}", null
        );
        return libraryFileRepository.upsertByBucketAndKey(candidate);
    }

    /**
     * Скачивает весь объект как InputStream. Caller должен закрыть
     * stream после использования.
     */
    public ResponseInputStream<GetObjectResponse> get(String bucket, String storageKey) {
        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .build();
        return s3Client.getObject(req);
    }

    /**
     * Скачивает chunk объекта через S3 Range header. Используется для
     * streaming больших PDF без загрузки в memory.
     *
     * @param startInclusive первый байт (0-based)
     * @param endInclusive последний байт (включительно). Если больше
     *                     реального размера - S3 вернёт {@code 206 Partial Content}
     *                     с обрезанным диапазоном
     */
    public ResponseInputStream<GetObjectResponse> getRange(
            String bucket, String storageKey, long startInclusive, long endInclusive) {
        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .range("bytes=" + startInclusive + "-" + endInclusive)
                .build();
        return s3Client.getObject(req);
    }

    /**
     * Существует ли объект (HEAD request, не качает body).
     */
    public boolean exists(String bucket, String storageKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket).key(storageKey).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            // 404 от MinIO может прилететь как S3Exception с statusCode 404
            // вместо typed NoSuchKeyException - depends on SDK version
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    /**
     * Метаданные объекта - size, etag, content-type, version-id. Не
     * качает body. Throws {@link NoSuchKeyException} если объект не
     * существует.
     */
    public StoredObject headObject(String bucket, String storageKey) {
        HeadObjectResponse resp = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(bucket).key(storageKey).build());
        return new StoredObject(
                resp.contentLength(),
                resp.contentType(),
                resp.eTag(),
                resp.versionId(),
                resp.lastModified()
        );
    }

    /**
     * Soft-delete: помечает catalog row как удалённый ({@code deleted_at}
     * + S3 createDeleteMarker для versioned bucket'ов). Объект в bucket'е
     * остаётся (history доступна), API возвращает 404 на последующий
     * {@link #get}. Hard-delete - см. {@link #hardDelete(LibraryFile)}.
     *
     * @return {@code true} если catalog row существовал и был помечен
     */
    public boolean softDelete(LibraryFile file) {
        boolean catalogUpdated = libraryFileRepository.softDelete(
                file.fileId(), Instant.now());
        if (catalogUpdated) {
            // В versioned bucket это создаёт delete-marker как latest version.
            // В non-versioned - физически удаляет.
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(file.bucket())
                    .key(file.storageKey())
                    .build());
        }
        return catalogUpdated;
    }

    /**
     * Hard-delete: admin two-phase action для GDPR right-to-delete.
     * Физически удаляет ВСЕ версии объекта в bucket'е + DELETE row из
     * catalog. После этой операции восстановление невозможно.
     */
    public boolean hardDelete(LibraryFile file) {
        ListObjectVersionsResponse versions = s3Client.listObjectVersions(
                ListObjectVersionsRequest.builder()
                        .bucket(file.bucket())
                        .prefix(file.storageKey())
                        .build());

        // Удаляем все версии объекта (включая delete-markers если есть)
        versions.versions().stream()
                .filter(v -> v.key().equals(file.storageKey()))
                .forEach(v -> s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(file.bucket())
                        .key(file.storageKey())
                        .versionId(v.versionId())
                        .build()));
        versions.deleteMarkers().stream()
                .filter(m -> m.key().equals(file.storageKey()))
                .forEach(m -> s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(file.bucket())
                        .key(file.storageKey())
                        .versionId(m.versionId())
                        .build()));

        return libraryFileRepository.hardDelete(file.fileId());
    }

    /**
     * Stream содержимое в файл, параллельно обновляя MessageDigest.
     * Один проход InputStream, hash готов после full read.
     */
    private String streamToFileAndHash(InputStream content, Path target) throws IOException {
        MessageDigest digest = newSha256();
        try (var out = Files.newOutputStream(target)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int n;
            while ((n = content.read(buf)) != -1) {
                digest.update(buf, 0, n);
                out.write(buf, 0, n);
            }
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
}
