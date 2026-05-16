package ru.basnukaev.argumentmap.library.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

/**
 * Integration test для {@link ObjectStorageHealthIndicator} через
 * Testcontainers MinIO. Проверяет health flow:
 *
 * <ul>
 *   <li>Healthy bucket → UP с latencyMs детали</li>
 *   <li>Несуществующий bucket → DOWN с statusCode 404</li>
 * </ul>
 */
@Testcontainers
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ObjectStorageHealthIndicatorIT {

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

    @Autowired private ObjectStorageHealthIndicator healthIndicator;
    @Autowired private S3Client s3Client;
    @Autowired private ObjectStorageProperties properties;

    @BeforeEach
    void createBucketIfNeeded() {
        try {
            s3Client.headBucket(b -> b.bucket(properties.buckets().importedBooks()));
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket(properties.buckets().importedBooks())
                    .build());
        } catch (Exception e) {
            // bucket уже есть в другом state - продолжаем
        }
    }

    @Test
    void health_returns_UP_when_bucket_exists() {
        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKeys("endpoint", "bucket", "latencyMs");
        assertThat(health.getDetails().get("bucket"))
                .isEqualTo(properties.buckets().importedBooks());
        assertThat((Long) health.getDetails().get("latencyMs"))
                .as("latencyMs должна быть positive number")
                .isGreaterThanOrEqualTo(0L)
                .isLessThan(5000L);
    }

    @Test
    void health_returns_DOWN_when_bucket_missing() throws Exception {
        // Временно удаляем bucket и проверяем что health становится DOWN
        s3Client.deleteBucket(b -> b.bucket(properties.buckets().importedBooks()));
        try {
            Health health = healthIndicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsKey("statusCode");
            assertThat(health.getDetails().get("statusCode")).isEqualTo(404);
        } finally {
            // Восстанавливаем bucket чтобы другие тесты в той же sequence
            // не сломались - testcontainer shared между классами через
            // static + Spring context cache
            s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket(properties.buckets().importedBooks())
                    .build());
        }
    }
}
