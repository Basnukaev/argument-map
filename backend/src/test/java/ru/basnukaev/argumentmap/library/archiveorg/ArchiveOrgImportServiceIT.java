package ru.basnukaev.argumentmap.library.archiveorg;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookContentKind;
import ru.basnukaev.argumentmap.library.repository.BookRepository;

/**
 * Pipeline-IT для {@link ArchiveOrgImportService} с реальным Postgres
 * (Testcontainers) и локальным {@link HttpServer}-stub'ом archive.org
 * (ADR-056). Сервер обслуживает {@code /metadata/{id}} (JSON) и
 * {@code /download/{id}/{file}} (мини-PDF для извлечения текста).
 *
 * <p>Покрывает: preview без записи, import создаёт book + pdf_links
 * (object-form) + cover_url, test-mode извлекает ровно N страниц,
 * идемпотентность.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ArchiveOrgImportServiceIT {

    private static final String IDENTIFIER = "testbook";
    private static HttpServer server;

    @Autowired
    private ArchiveOrgImportService importService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void archiveOrgProps(DynamicPropertyRegistry registry) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        String base = "http://127.0.0.1:" + port;

        // metadata: 2 тома (testbook1/2) original + OCR + обложка testbook0
        String metaJson = "{\"metadata\":{\"title\":\"Тестовая книга\",\"creator\":\"Автор\","
                + "\"language\":\"Arabic\",\"description\":\"<div>описание</div>\"},"
                + "\"files\":["
                + fileJson("testbook0.pdf", "Image Container PDF", "original")
                + "," + fileJson("testbook0_text.pdf", "Additional Text PDF", "derivative")
                + "," + fileJson("testbook1.pdf", "Image Container PDF", "original")
                + "," + fileJson("testbook1_text.pdf", "Additional Text PDF", "derivative")
                + "," + fileJson("testbook2.pdf", "Image Container PDF", "original")
                + "," + fileJson("testbook2_text.pdf", "Additional Text PDF", "derivative")
                + "]}";

        server.createContext("/metadata/" + IDENTIFIER, exchange -> {
            byte[] body = metaJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        // любой /download/... отдаёт 5-страничный PDF (для test-mode N<5)
        byte[] pdf = fivePagePdf();
        server.createContext("/download/", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/pdf");
            exchange.sendResponseHeaders(200, pdf.length);
            exchange.getResponseBody().write(pdf);
            exchange.close();
        });

        server.start();
        registry.add("archiveorg.base-url", () -> base);
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @BeforeEach
    void cleanup() {
        // тесты делят один Testcontainer + один IDENTIFIER; чистим прошлый
        // импорт чтобы idempotency-lookup не возвращал чужую книгу
        jdbcTemplate.update(
                "DELETE FROM lib_pages WHERE book_id IN "
                        + "(SELECT id FROM lib_books WHERE metadata->>'archive_org_id' = ?)",
                IDENTIFIER);
        jdbcTemplate.update(
                "DELETE FROM lib_books WHERE metadata->>'archive_org_id' = ?", IDENTIFIER);
    }

    @Test
    void preview_noWrite() {
        long before = countBooks();
        ArchiveOrgPreview p = importService.preview(IDENTIFIER);

        assertThat(p.archiveOrgId()).isEqualTo(IDENTIFIER);
        assertThat(p.hasPdf()).isTrue();
        assertThat(p.files()).hasSize(3); // обложка + 2 тома
        assertThat(countBooks()).isEqualTo(before); // ничего не записано
    }

    @Test
    void import_createsBookWithPdfLinksAndCover_noTextByDefault() {
        ArchiveOrgImportRequest req = req(false, null);
        ArchiveOrgImportResponse resp = importService.importBook(req);

        assertThat(resp.alreadyExisted()).isFalse();
        assertThat(resp.volumesRegistered()).isEqualTo(2);
        assertThat(resp.coverSet()).isTrue();
        assertThat(resp.pagesExtracted()).isZero();

        Book book = bookRepository.findById(resp.bookId()).orElseThrow();
        assertThat(book.title()).isEqualTo("Тестовая книга");
        assertThat(book.visibility()).isEqualTo("PUBLIC");
        assertThat(book.createdBy()).isEqualTo(ArchiveOrgImportService.SYSTEM_USER_ID);
        // metadata: archive_org_id + object-form pdf_links (variant, volumeNo).
        // jsonb переформатирует пробелы, поэтому парсим, а не string-match.
        assertMetadata(book.metadata());
        // cover_url проставлен (thumbnail по умолчанию) - и в БД, и через
        // RowMapper в Book.coverUrl() (миграция 67 wiring end-to-end)
        String coverUrl = jdbcTemplate.queryForObject(
                "SELECT cover_url FROM lib_books WHERE id = ?", String.class, resp.bookId());
        assertThat(coverUrl).endsWith("/services/img/" + IDENTIFIER);
        assertThat(book.coverUrl()).isEqualTo(coverUrl);
        // pages не извлечены
        assertThat(pageCount(resp.bookId())).isZero();
        // content_kind: файл есть (pdf_links), текста нет (extractText=false)
        assertThat(book.contentKind()).isEqualTo(BookContentKind.FILE_ONLY);
    }

    @Test
    void import_testMode_extractsExactlyNPagesPerVolume() {
        ArchiveOrgImportRequest req = req(true, 2);
        ArchiveOrgImportResponse resp = importService.importBook(req);

        // 2 тома × 2 страницы (testModePages=2, PDF имеет 5)
        assertThat(resp.pagesExtracted()).isEqualTo(4);
        assertThat(pageCount(resp.bookId())).isEqualTo(4);
        // content_kind: файл + извлечён НЕпустой текст ("Page number N") → TEXT_AND_FILE
        assertThat(bookRepository.findById(resp.bookId()).orElseThrow().contentKind())
                .isEqualTo(BookContentKind.TEXT_AND_FILE);
    }

    @Test
    void import_idempotent_secondImportReturnsExisting() {
        ArchiveOrgImportResponse first = importService.importBook(req(false, null));
        ArchiveOrgImportResponse second = importService.importBook(req(false, null));

        assertThat(second.alreadyExisted()).isTrue();
        assertThat(second.bookId()).isEqualTo(first.bookId());
        assertThat(bookRepository.findByArchiveOrgId(IDENTIFIER)).isPresent();
    }

    // ---------------- helpers ----------------

    private static ArchiveOrgImportRequest req(boolean extractText, Integer testModePages) {
        return new ArchiveOrgImportRequest(
                "https://archive.org/details/" + IDENTIFIER,
                null, null, null, null, null, null, null, null, null, null,
                null, null, extractText, testModePages);
    }

    private long countBooks() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lib_books WHERE metadata->>'archive_org_id' = ?",
                Long.class, IDENTIFIER);
    }

    private int pageCount(java.util.UUID bookId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lib_pages WHERE book_id = ?", Integer.class, bookId);
        return n != null ? n : 0;
    }

    private static void assertMetadata(String metadataJson) {
        try {
            JsonNode root = new ObjectMapper().readTree(metadataJson);
            assertThat(root.path("archive_org_id").asText()).isEqualTo(IDENTIFIER);
            JsonNode pdfLinks = root.path("pdf_links");
            assertThat(pdfLinks.path("cover").asInt()).isEqualTo(1);
            assertThat(pdfLinks.path("root").asText()).endsWith("/download/" + IDENTIFIER + "/");
            JsonNode files = pdfLinks.path("files");
            // обложка(orig+ocr) + 2 тома(orig+ocr) = 6 элементов object-form
            assertThat(files).hasSize(6);
            assertThat(files.get(0).path("variant").asText()).isEqualTo("original");
            assertThat(files.get(0).path("volumeNo").asInt()).isZero(); // обложка
            boolean hasOcr = false;
            boolean hasOriginal = false;
            for (JsonNode f : files) {
                if ("ocr".equals(f.path("variant").asText())) {
                    hasOcr = true;
                }
                if ("original".equals(f.path("variant").asText())) {
                    hasOriginal = true;
                }
            }
            assertThat(hasOcr).isTrue();
            assertThat(hasOriginal).isTrue();
        } catch (Exception e) {
            throw new AssertionError("невалидный metadata JSON: " + metadataJson, e);
        }
    }

    private static String fileJson(String name, String format, String source) {
        return "{\"name\":\"" + name + "\",\"format\":\"" + format
                + "\",\"source\":\"" + source + "\",\"size\":\"1000\"}";
    }

    /** Минимальный 5-страничный PDF с текстом - для теста извлечения. */
    private static byte[] fivePagePdf() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 1; i <= 5; i++) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(100, 700);
                    cs.showText("Page number " + i);
                    cs.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}
