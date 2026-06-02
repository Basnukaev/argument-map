package ru.basnukaev.argumentmap.library.archiveorg;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
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
 * (ADR-056 amendment b). Сервер обслуживает только {@code /metadata/{id}}
 * (JSON) - PDF не качаются, текст не извлекаем.
 *
 * <p>Покрывает: preview без записи + regex-обогащение (LLM disabled в
 * тестах → regex-only), import создаёт book + pdf_links (только original,
 * без OCR) + cover_url, content_kind=FILE_ONLY, lib_pages не создаются,
 * description plain-text, идемпотентность.
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

        // metadata: 2 тома (testbook1/2) original + OCR + обложка testbook0.
        // OCR (_text) присутствуют в источнике, но не должны попасть в импорт.
        // description с HTML - проверяем что в БД ляжет plain-text.
        String metaJson = "{\"metadata\":{\"title\":\"Тестовая книга\",\"creator\":\"Автор\","
                + "\"language\":\"Arabic\",\"description\":"
                + "\"<div>الناشر: دار الاختبار</div><br/>عدد المجلدات: 2\"},"
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
    void preview_noWrite_regexFallback() {
        long before = countBooks();
        ArchiveOrgPreview p = importService.preview(IDENTIFIER);

        assertThat(p.archiveOrgId()).isEqualTo(IDENTIFIER);
        assertThat(p.hasPdf()).isTrue();
        assertThat(p.files()).hasSize(3); // обложка + 2 тома (OCR отброшены)
        // LLM disabled в тестах → regex-обогащение: издатель/тома из description
        assertThat(p.publisher().value()).isEqualTo("دار الاختبار");
        assertThat(p.volumes().value()).isEqualTo("2");
        // description в превью plain-text (HTML снят)
        assertThat(p.rawDescription()).doesNotContain("<div>");
        assertThat(countBooks()).isEqualTo(before); // ничего не записано
    }

    @Test
    void import_createsBookWithOriginalsOnly_fileOnly_noPages() {
        ArchiveOrgImportResponse resp = importService.importBook(req());

        assertThat(resp.alreadyExisted()).isFalse();
        assertThat(resp.volumesRegistered()).isEqualTo(2);
        assertThat(resp.coverSet()).isTrue();
        assertThat(resp.pagesExtracted()).isZero();

        Book book = bookRepository.findById(resp.bookId()).orElseThrow();
        assertThat(book.title()).isEqualTo("Тестовая книга");
        assertThat(book.visibility()).isEqualTo("PUBLIC");
        assertThat(book.createdBy()).isEqualTo(ArchiveOrgImportService.SYSTEM_USER_ID);
        // metadata: archive_org_id + object-form pdf_links (только original).
        // jsonb переформатирует пробелы, поэтому парсим, а не string-match.
        assertMetadata(book.metadata());
        // description в БД plain-text (HTML снят)
        assertThat(book.description()).doesNotContain("<div>").doesNotContain("<br");
        assertThat(book.description()).contains("الناشر");
        // cover_url проставлен (thumbnail по умолчанию)
        String coverUrl = jdbcTemplate.queryForObject(
                "SELECT cover_url FROM lib_books WHERE id = ?", String.class, resp.bookId());
        assertThat(coverUrl).endsWith("/services/img/" + IDENTIFIER);
        assertThat(book.coverUrl()).isEqualTo(coverUrl);
        // lib_pages НЕ создаются (FILE_ONLY)
        assertThat(pageCount(resp.bookId())).isZero();
        assertThat(book.contentKind()).isEqualTo(BookContentKind.FILE_ONLY);
    }

    @Test
    void import_idempotent_secondImportReturnsExisting() {
        ArchiveOrgImportResponse first = importService.importBook(req());
        ArchiveOrgImportResponse second = importService.importBook(req());

        assertThat(second.alreadyExisted()).isTrue();
        assertThat(second.bookId()).isEqualTo(first.bookId());
        assertThat(bookRepository.findByArchiveOrgId(IDENTIFIER)).isPresent();
    }

    // ---------------- helpers ----------------

    private static ArchiveOrgImportRequest req() {
        return new ArchiveOrgImportRequest(
                "https://archive.org/details/" + IDENTIFIER,
                null, null, null, null, null, null, null, null, null, null,
                null, null);
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
            // обложка + 2 тома = 3 элемента (OCR отброшены, NO _text)
            assertThat(files).hasSize(3);
            // files[0] - обложка
            assertThat(files.get(0).path("variant").asText()).isEqualTo("original");
            assertThat(files.get(0).path("volumeNo").asInt()).isZero();
            assertThat(files.get(0).path("name").asText()).isEqualTo("testbook0.pdf");
            // ни одного _text / ocr-варианта
            for (JsonNode f : files) {
                assertThat(f.path("variant").asText()).isEqualTo("original");
                assertThat(f.path("name").asText()).doesNotContain("_text");
            }
            assertThat(files.get(1).path("name").asText()).isEqualTo("testbook1.pdf");
            assertThat(files.get(1).path("volumeNo").asInt()).isEqualTo(1);
            assertThat(files.get(2).path("name").asText()).isEqualTo("testbook2.pdf");
            assertThat(files.get(2).path("volumeNo").asInt()).isEqualTo(2);
        } catch (Exception e) {
            throw new AssertionError("невалидный metadata JSON: " + metadataJson, e);
        }
    }

    private static String fileJson(String name, String format, String source) {
        return "{\"name\":\"" + name + "\",\"format\":\"" + format
                + "\",\"source\":\"" + source + "\",\"size\":\"1000\"}";
    }
}
