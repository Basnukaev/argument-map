package ru.basnukaev.argumentmap.library.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.exception.BookNotFoundException;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookVisibility;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.OcrStatus;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.PageRepository;
import ru.basnukaev.argumentmap.library.storage.ObjectStorageProperties;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.VersioningConfiguration;

/**
 * Integration test для {@link PageImageService} (Этап 17.a, ADR-041).
 * MinIO testcontainer + JPEG-фикстура генерируется in-memory через
 * BufferedImage + ImageIO. Не коммитим binary'и в репу.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Testcontainers
class PageImageServiceIT {

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

    @Autowired
    private PageImageService service;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private ObjectStorageProperties properties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private Book book;
    private String imagesBucket;

    @BeforeEach
    void setUp() {
        imagesBucket = properties.buckets().pageImages();
        ensureBucket(imagesBucket, true);
        clearBucket(imagesBucket);

        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "user-" + userId, userId + "@example.com");

        Instant now = Instant.now();
        book = bookRepository.save(new Book(
                UUID.randomUUID(), BookType.MANUSCRIPT, "Test Manuscript",
                null, "ar", null, null, userId, now, now,
                null, null, null, null, null, null
        , BookVisibility.PUBLIC));
    }

    @Test
    void uploadPageImage_createsPlaceholderPageWhenNoneExists() {
        byte[] jpeg = buildJpeg(400, 600);
        MockMultipartFile file = jpegFile(jpeg);

        Page page = service.uploadPageImage(book.id(), 1, file);

        assertThat(page).isNotNull();
        assertThat(page.bookId()).isEqualTo(book.id());
        assertThat(page.pageNumber()).isEqualTo(1);
        assertThat(page.imageBucket()).isEqualTo(imagesBucket);
        assertThat(page.imageStorageKey()).isEqualTo(book.id() + "/page-1.jpg");
        assertThat(page.imageUploadedAt()).isNotNull();
        assertThat(page.ocrStatus()).isEqualTo(OcrStatus.PENDING);
        // text_content is placeholder empty string - CHECK constraint satisfied
        assertThat(page.textContent()).isEqualTo("");
    }

    @Test
    void uploadPageImage_updatesExistingPageWhenPageNumberMatches() {
        // pre-existing page from say PDF-import path
        Instant now = Instant.now();
        Page existing = pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 5,
                null, null, null,
                "pre-existing PDF text", null, null, now, now
        ));
        byte[] jpeg = buildJpeg(800, 1200);
        MockMultipartFile file = jpegFile(jpeg);

        Page page = service.uploadPageImage(book.id(), 5, file);

        // same id - не создаём новую page, обновляем existing
        assertThat(page.id()).isEqualTo(existing.id());
        assertThat(page.imageBucket()).isEqualTo(imagesBucket);
        assertThat(page.imageStorageKey()).isEqualTo(book.id() + "/page-5.jpg");
        assertThat(page.ocrStatus()).isEqualTo(OcrStatus.PENDING);
        // text_content сохраняется - upload image не стирает existing text
        assertThat(page.textContent()).isEqualTo("pre-existing PDF text");
    }

    @Test
    void uploadPageImage_savesToMinIo() {
        byte[] jpeg = buildJpeg(400, 600);
        MockMultipartFile file = jpegFile(jpeg);

        Page page = service.uploadPageImage(book.id(), 7, file);

        // verify object in bucket via HEAD
        assertThat(s3Client.headObject(HeadObjectRequest.builder()
                .bucket(page.imageBucket())
                .key(page.imageStorageKey())
                .build()).contentLength()).isPositive();
    }

    @Test
    void uploadPageImage_pngContentTypeMappedToPngExtension() {
        byte[] png = buildPng(200, 300);
        MockMultipartFile file = new MockMultipartFile(
                "file", "scan.png", MediaType.IMAGE_PNG_VALUE, png);

        Page page = service.uploadPageImage(book.id(), 2, file);

        assertThat(page.imageStorageKey()).isEqualTo(book.id() + "/page-2.png");
    }

    @Test
    void uploadPageImage_unknownBookId_throwsBookNotFound() {
        byte[] jpeg = buildJpeg(100, 100);
        MockMultipartFile file = jpegFile(jpeg);
        UUID unknownBookId = UUID.randomUUID();

        assertThatThrownBy(() -> service.uploadPageImage(unknownBookId, 1, file))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void uploadPageImage_zeroPageNumber_throwsPageImageException() {
        byte[] jpeg = buildJpeg(100, 100);
        MockMultipartFile file = jpegFile(jpeg);

        assertThatThrownBy(() -> service.uploadPageImage(book.id(), 0, file))
                .isInstanceOf(PageImageException.class)
                .hasMessageContaining("pageNumber");
    }

    @Test
    void uploadPageImage_unknownMimeType_throwsPageImageException() {
        byte[] jpeg = buildJpeg(100, 100);
        // override content type to something not in whitelist
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.bmp", "image/bmp", jpeg);

        assertThatThrownBy(() -> service.uploadPageImage(book.id(), 1, file))
                .isInstanceOf(PageImageException.class)
                .hasMessageContaining("MIME");
    }

    @Test
    void uploadPageImage_reUploadOverwritesPointer() {
        // первый upload
        Page first = service.uploadPageImage(book.id(), 3,
                jpegFile(buildJpeg(200, 300)));
        Instant firstTs = first.imageUploadedAt();

        // повторный upload той же page → перезаписываем pointer и timestamp
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Page second = service.uploadPageImage(book.id(), 3,
                jpegFile(buildJpeg(400, 600)));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.imageUploadedAt()).isAfter(firstTs);
        assertThat(second.ocrStatus()).isEqualTo(OcrStatus.PENDING);
    }

    /**
     * Генерирует валидный JPEG with white background + black diagonal -
     * минимальное content которое decoder примет как изображение.
     */
    private static byte[] buildJpeg(int width, int height) {
        try {
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setColor(Color.BLACK);
            g.drawLine(0, 0, width, height);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("test fixture build failed", e);
        }
    }

    private static byte[] buildPng(int width, int height) {
        try {
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("test fixture build failed", e);
        }
    }

    private static MockMultipartFile jpegFile(byte[] bytes) {
        return new MockMultipartFile(
                "file", "scan.jpg", MediaType.IMAGE_JPEG_VALUE, bytes);
    }

    private void ensureBucket(String bucket, boolean withVersioning) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
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

    private void clearBucket(String bucket) {
        ListObjectVersionsResponse versions = s3Client.listObjectVersions(
                r -> r.bucket(bucket));
        versions.versions().forEach(v -> s3Client.deleteObject(
                r -> r.bucket(bucket).key(v.key()).versionId(v.versionId())));
        versions.deleteMarkers().forEach(m -> s3Client.deleteObject(
                r -> r.bucket(bucket).key(m.key()).versionId(m.versionId())));
    }
}
