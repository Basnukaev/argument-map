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

import ru.basnukaev.argumentmap.SharedMinioContainer;
import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

/**
 * Integration test для {@link ObjectStorageHealthIndicator} через
 * Testcontainers MinIO. Проверяет health flow:
 *
 * <ul>
 *   <li>Healthy bucket → UP с latencyMs детали</li>
 *   <li>Несуществующий bucket → DOWN с statusCode 404</li>
 * </ul>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ObjectStorageHealthIndicatorIT {

    @DynamicPropertySource
    static void minioProperties(DynamicPropertyRegistry r) {
        SharedMinioContainer.applyProperties(r);
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
        // Временно удаляем bucket и проверяем что health становится DOWN.
        // С SharedMinioContainer (shared между классами) bucket мог
        // содержать objects+versions от других тестов - явно чистим
        // перед delete, иначе S3 вернёт 409 BucketNotEmpty
        String bucket = properties.buckets().importedBooks();
        emptyBucket(bucket);
        s3Client.deleteBucket(b -> b.bucket(bucket));
        try {
            Health health = healthIndicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsKey("statusCode");
            assertThat(health.getDetails().get("statusCode")).isEqualTo(404);
        } finally {
            // Восстанавливаем bucket чтобы другие тесты в той же sequence
            // не сломались - SharedMinioContainer shared между классами
            s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket(bucket)
                    .build());
        }
    }

    /**
     * Удаляет все objects и version markers из bucket'а - prerequisite для
     * delete bucket'а с включённым versioning. С shared MinIO container
     * bucket может содержать legacy state от других IT.
     */
    private void emptyBucket(String bucket) {
        ListObjectVersionsResponse versions = s3Client.listObjectVersions(
                b -> b.bucket(bucket));
        var toDelete = new java.util.ArrayList<ObjectIdentifier>();
        versions.versions().forEach(v -> toDelete.add(
                ObjectIdentifier.builder().key(v.key()).versionId(v.versionId()).build()));
        versions.deleteMarkers().forEach(m -> toDelete.add(
                ObjectIdentifier.builder().key(m.key()).versionId(m.versionId()).build()));
        if (!toDelete.isEmpty()) {
            s3Client.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(Delete.builder().objects(toDelete).build())
                    .build());
        }
    }
}
