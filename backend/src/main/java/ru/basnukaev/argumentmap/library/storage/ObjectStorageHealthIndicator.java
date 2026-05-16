package ru.basnukaev.argumentmap.library.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Health indicator для object storage (ADR-024, Этап 25.b operational
 * hardening). Делает {@code HeadBucket} на primary bucket
 * {@code library-imported-books} - lightweight ping ({@code HEAD}-запрос,
 * без body, latency ~10ms на здоровом MinIO).
 *
 * <p>Spring Boot Actuator auto-discover bean {@link HealthIndicator}
 * по имени {@code objectStorage} и добавляет в {@code /actuator/health}
 * композитный response под ключом {@code objectStorage}. При недоступном
 * S3 endpoint - status DOWN с details (endpoint + error message) +
 * overall health snapshot становится DOWN.
 *
 * <p>Поведение:
 * <ul>
 *   <li>HeadBucket OK → UP, details: {@code endpoint, bucket, latencyMs}</li>
 *   <li>S3Exception (4xx/5xx, bucket не существует, denied) → DOWN с
 *       error code и message</li>
 *   <li>NetworkException / timeout / connection refused → DOWN, exception
 *       propagated через Health.down(ex)</li>
 * </ul>
 *
 * <p>Используется load balancer / kubernetes readiness probe для решения
 * не направлять traffic на инстанс с broken storage connectivity.
 */
@Component("objectStorage")
public class ObjectStorageHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorageHealthIndicator.class);

    private final S3Client s3Client;
    private final ObjectStorageProperties properties;

    public ObjectStorageHealthIndicator(S3Client s3Client, ObjectStorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public Health health() {
        String bucket = properties.buckets().importedBooks();
        long started = System.nanoTime();
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            long latencyMs = (System.nanoTime() - started) / 1_000_000;
            return Health.up()
                    .withDetail("endpoint", properties.endpoint())
                    .withDetail("bucket", bucket)
                    .withDetail("latencyMs", latencyMs)
                    .build();
        } catch (S3Exception e) {
            log.warn("Object storage health check failed: HTTP {} {}",
                    e.statusCode(), e.awsErrorDetails() != null
                            ? e.awsErrorDetails().errorMessage() : e.getMessage());
            return Health.down()
                    .withDetail("endpoint", properties.endpoint())
                    .withDetail("bucket", bucket)
                    .withDetail("statusCode", e.statusCode())
                    .withDetail("errorCode", e.awsErrorDetails() != null
                            ? e.awsErrorDetails().errorCode() : "unknown")
                    .withDetail("message", e.getMessage())
                    .build();
        } catch (Exception e) {
            log.warn("Object storage health check failed", e);
            return Health.down(e)
                    .withDetail("endpoint", properties.endpoint())
                    .withDetail("bucket", bucket)
                    .build();
        }
    }
}
