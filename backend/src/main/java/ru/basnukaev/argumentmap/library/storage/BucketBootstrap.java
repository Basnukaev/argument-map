package ru.basnukaev.argumentmap.library.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.VersioningConfiguration;

/**
 * Создаёт 4 bucket'а (ADR-024) при старте приложения если их нет, и
 * включает versioning для 3 critical bucket'ов
 * ({@code library-imported-books}, {@code library-user-uploads},
 * {@code library-page-images}). {@code derived-artifacts} без versioning -
 * re-derivable.
 *
 * <p>Включается флагом {@code storage.bucket-bootstrap.enabled=true} -
 * по умолчанию off, чтобы IT не пытались создавать buckets через
 * application bean (они создают сами через {@code @BeforeEach}).
 * В dev profile (application-local) можно включить - удобно для
 * docker-compose first-run без ручного {@code mc mb}.
 *
 * <p>Идемпотентен: {@code HeadBucket} → {@code CreateBucket} только
 * при 404. Versioning переустанавливается каждый запуск (no-op если
 * уже ENABLED) - дешевле чем GET + conditional PUT.
 */
@Component
@ConditionalOnProperty(prefix = "storage.bucket-bootstrap", name = "enabled",
        havingValue = "true", matchIfMissing = false)
public class BucketBootstrap {

    private static final Logger log = LoggerFactory.getLogger(BucketBootstrap.class);

    private final S3Client s3Client;
    private final ObjectStorageProperties properties;

    public BucketBootstrap(S3Client s3Client, ObjectStorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureBuckets() {
        ObjectStorageProperties.Buckets buckets = properties.buckets();
        ensureBucket(buckets.importedBooks(), true);
        ensureBucket(buckets.userUploads(), true);
        ensureBucket(buckets.pageImages(), true);
        ensureBucket(buckets.derived(), false);
        log.info("bucket bootstrap завершён - все 4 bucket'а доступны");
    }

    private void ensureBucket(String bucket, boolean withVersioning) {
        boolean exists = bucketExists(bucket);
        if (!exists) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("создан bucket: {}", bucket);
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

    private boolean bucketExists(String bucket) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        } catch (S3Exception e) {
            // 404 от MinIO иногда приходит как S3Exception с statusCode 404
            // вместо typed NoSuchBucketException - depends on SDK version
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }
}
