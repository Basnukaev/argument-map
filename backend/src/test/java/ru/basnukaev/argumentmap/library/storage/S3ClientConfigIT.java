package ru.basnukaev.argumentmap.library.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Smoke-test что Spring context поднимает {@link S3Client} bean из
 * {@link S3ClientConfig} с {@link ObjectStorageProperties} из
 * {@code application.yml}. Не обращается к реальному MinIO -
 * integration-test с S3 операциями появится в 25.b.4 с
 * {@code ObjectStorageService} (Testcontainers MinIO).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class S3ClientConfigIT {

    @Autowired
    private S3Client s3Client;

    @Autowired
    private ObjectStorageProperties properties;

    @Test
    void s3Client_isWiredAsSpringBean() {
        assertThat(s3Client).isNotNull();
    }

    @Test
    void properties_haveDefaultsFromApplicationYml() {
        assertThat(properties.endpoint()).isEqualTo("http://localhost:9000");
        assertThat(properties.region()).isEqualTo("us-east-1");
        assertThat(properties.accessKey()).isEqualTo("minioadmin");
        assertThat(properties.secretKey()).isEqualTo("minioadmin");
        assertThat(properties.pathStyleAccess()).isTrue();
        assertThat(properties.maxRetries()).isEqualTo(3);
        assertThat(properties.connectTimeout().toSeconds()).isEqualTo(5);
        assertThat(properties.readTimeout().toSeconds()).isEqualTo(30);
    }

    @Test
    void buckets_haveFourNamedBuckets_byCriticality() {
        ObjectStorageProperties.Buckets buckets = properties.buckets();
        assertThat(buckets.importedBooks()).isEqualTo("library-imported-books");
        assertThat(buckets.userUploads()).isEqualTo("library-user-uploads");
        assertThat(buckets.pageImages()).isEqualTo("library-page-images");
        assertThat(buckets.derived()).isEqualTo("derived-artifacts");
    }
}
