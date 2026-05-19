package ru.basnukaev.argumentmap;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton MinIO testcontainer для всего test suite. Стартует один раз
 * на JVM (Surefire/Failsafe fork) и переиспользуется всеми IT - экономит
 * 5-10 сек × N IT классов на каждый verify-прогон.
 * <p>
 * Использование:
 * <pre>
 *   {@code @DynamicPropertySource}
 *   static void minioProperties(DynamicPropertyRegistry r) {
 *       SharedMinioContainer.applyProperties(r);
 *   }
 * </pre>
 * Изоляция bucket'ов между тестами - per-test bucket creation/cleanup в
 * {@code @BeforeEach} (см. ObjectStorageServiceIT как образец). Container
 * не stop'ается явно - JVM shutdown hook от testcontainers сам tear down'нет.
 */
public final class SharedMinioContainer {
    private static final String IMAGE = "minio/minio:RELEASE.2025-07-23T15-54-02Z-cpuv1";
    public static final String USERNAME = "minioadmin";
    public static final String PASSWORD = "minioadmin";

    public static final MinIOContainer INSTANCE = create();

    private static MinIOContainer create() {
        MinIOContainer c = new MinIOContainer(DockerImageName.parse(IMAGE))
                .withUserName(USERNAME)
                .withPassword(PASSWORD);
        c.start();
        return c;
    }

    public static void applyProperties(DynamicPropertyRegistry r) {
        r.add("storage.endpoint", INSTANCE::getS3URL);
        r.add("storage.access-key", () -> USERNAME);
        r.add("storage.secret-key", () -> PASSWORD);
    }

    private SharedMinioContainer() {}
}
