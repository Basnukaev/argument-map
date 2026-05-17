package ru.basnukaev.argumentmap.library.imports;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.OcrStatus;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.PageRepository;
import ru.basnukaev.argumentmap.library.storage.ObjectStorageProperties;
import ru.basnukaev.argumentmap.library.storage.ObjectStorageService;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.VersioningConfiguration;

/**
 * Integration test для {@link OcrService} (Этап 17.b, ADR-041).
 *
 * <p>Активируется только если на хосте установлен Tesseract +
 * eng.traineddata. {@code @EnabledIf("isTesseractAvailable")} - проверка
 * запускается перед каждым тестом и каждым SpringBoot контекстом
 * (через статический метод resolver Spring TestContext не обращается
 * к контексту, можно без Autowired).
 *
 * <p>Тест использует {@code language="eng"} вместо ara/rus по двум
 * причинам:
 * <ol>
 *   <li>Standard JDK font (Helvetica) рендерит ASCII надёжно во всех
 *       средах; арабский требует font с soup ligatures (часто нет в
 *       headless контейнере)</li>
 *   <li>Tesseract Latin recognition более стабилен чем cursive scripts;
 *       плюс minimal test fixture не должен ловить edge case arabic
 *       OCR quality</li>
 * </ol>
 *
 * <p>В CI/CD test runner без tesseract - тест skip'нется, BUILD SUCCESS.
 * Локально с tesseract - sanity check что Tess4j → Tesseract path
 * работает end-to-end.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Testcontainers
@EnabledIf(value = "isTesseractAvailable", disabledReason =
        "tesseract binary или eng.traineddata не найдены - тест пропущен. "
                + "Установить: sudo apt install tesseract-ocr tesseract-ocr-eng")
class OcrServiceIT {

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
        // Force eng-only для теста - надёжнее ara/rus в headless контейнере
        r.add("ocr.tessdata.path",
                () -> findTessdataPath() != null ? findTessdataPath() : "/usr/share/tesseract-ocr/4.00/tessdata");
    }

    @Autowired
    private OcrService service;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private ObjectStorageService objectStorageService;

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
                UUID.randomUUID(), BookType.MANUSCRIPT, "OCR Test Book",
                null, "en", null, null, userId, now, now,
                null, null, null, null, null, null
        ));
    }

    @Test
    void recognize_simpleEnglishText_populatesTextContentAndMarksDone() {
        // upload synthetic image с известным текстом
        byte[] png = buildPngWithText("Hello World", 600, 200);
        String storageKey = book.id() + "/page-1.png";
        objectStorageService.put(imagesBucket, storageKey,
                new ByteArrayInputStream(png), "image/png");

        Instant now = Instant.now();
        Page page = pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 1,
                null, null, null,
                "", null, null,
                imagesBucket, storageKey, now,
                OcrStatus.PENDING, null, null,
                now, now
        ));

        service.recognize(page.id());

        Page after = pageRepository.findById(page.id()).orElseThrow();
        assertThat(after.ocrStatus()).isEqualTo(OcrStatus.DONE);
        assertThat(after.ocrCompletedAt()).isNotNull();
        // Tesseract на минимальном fixture не гарантирует 100% точность,
        // проверяем что хотя бы один из ключевых слов распознан
        assertThat(after.textContent().toLowerCase())
                .containsAnyOf("hello", "world");
    }

    @Test
    void recognize_pageWithoutImage_marksFailed() {
        Instant now = Instant.now();
        Page page = pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 99,
                null, null, null,
                "text-only page", null, null,
                null, null, null,  // нет image pointer
                null, null, null,
                now, now
        ));

        service.recognize(page.id());

        Page after = pageRepository.findById(page.id()).orElseThrow();
        assertThat(after.ocrStatus()).isEqualTo(OcrStatus.FAILED);
        // text_content не тронут
        assertThat(after.textContent()).isEqualTo("text-only page");
    }

    /**
     * Генерирует PNG с текстом - JDK Graphics2D рисует ASCII через
     * стандартный Helvetica font. Достаточно для smoke OCR теста.
     */
    private static byte[] buildPngWithText(String text, int width, int height) {
        try {
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setColor(Color.BLACK);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 64));
            g.drawString(text, 30, 120);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("test fixture build failed", e);
        }
    }

    /**
     * Resolver для {@code @EnabledIf}. Должен быть статическим методом
     * без аргументов, возвращать boolean. Проверяет наличие tesseract
     * binary через which-эквивалент + eng.traineddata в стандартных
     * paths.
     */
    static boolean isTesseractAvailable() {
        // binary
        boolean hasBinary = false;
        for (String dir : new String[]{"/usr/bin", "/usr/local/bin",
                "/opt/homebrew/bin"}) {
            if (Files.exists(Paths.get(dir, "tesseract"))) {
                hasBinary = true;
                break;
            }
        }
        if (!hasBinary) {
            return false;
        }
        // eng training data
        String found = findTessdataPath();
        if (found == null) {
            return false;
        }
        return Files.exists(Paths.get(found, "eng.traineddata"));
    }

    /**
     * Найти tessdata каталог в стандартных paths Debian/macOS.
     * Возвращает null если ни один не существует.
     */
    static String findTessdataPath() {
        String[] candidates = {
                "/usr/share/tesseract-ocr/5/tessdata",
                "/usr/share/tesseract-ocr/4.00/tessdata",
                "/usr/share/tesseract-ocr/tessdata",
                "/usr/share/tessdata",
                "/opt/homebrew/share/tessdata"
        };
        for (String path : candidates) {
            if (Files.isDirectory(Paths.get(path))) {
                return path;
            }
        }
        return null;
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
