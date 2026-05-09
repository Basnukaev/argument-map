package ru.basnukaev.argumentmap.library.shamela.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.shamela.api.ShamelaApiClient;
import ru.basnukaev.argumentmap.library.shamela.api.ShamelaApiProperties;
import ru.basnukaev.argumentmap.library.shamela.api.dto.MasterMetadata;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaBookRow;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaAuthorDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaBookDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaCategoryDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaPageDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaSyncStateDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaTitleDao;

/**
 * Интеграционный тест {@link ShamelaImportService}: моки только на
 * {@link ShamelaApiClient} (изолируем от сети), всё остальное реальное -
 * Extractor, Reader, DAO, Postgres через Testcontainers. Pipeline
 * прокатывается на zip-архивах с настоящими SQLite-файлами, собираемыми
 * программно в @TempDir.
 *
 * <p>{@code shamela.download-dir} переопределяется через
 * {@link DynamicPropertySource} в изолированный tempdir для всего
 * класса - так разные тесты не наступают друг другу на cleanup-ассерты.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ShamelaImportServiceIT {

    private static Path isolatedDownloadDir;

    @DynamicPropertySource
    static void overrideShamelaProps(DynamicPropertyRegistry registry) throws IOException {
        isolatedDownloadDir = Files.createTempDirectory("shamela-import-it-");
        registry.add("shamela.download-dir", () -> isolatedDownloadDir.toString());
    }

    @Autowired
    private ShamelaImportService service;

    @Autowired
    private ShamelaCategoryDao categoryDao;

    @Autowired
    private ShamelaAuthorDao authorDao;

    @Autowired
    private ShamelaBookDao bookDao;

    @Autowired
    private ShamelaPageDao pageDao;

    @Autowired
    private ShamelaTitleDao titleDao;

    @Autowired
    private ShamelaSyncStateDao syncStateDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ShamelaApiProperties props;

    @MockitoBean
    private ShamelaApiClient apiClient;

    @TempDir
    Path workspace;

    @BeforeEach
    void cleanupTablesAndDownloadDir() throws IOException {
        jdbcTemplate.update("DELETE FROM lib_shamela_page");
        jdbcTemplate.update("DELETE FROM lib_shamela_title");
        jdbcTemplate.update("DELETE FROM lib_shamela_book");
        jdbcTemplate.update("DELETE FROM lib_shamela_author");
        jdbcTemplate.update("DELETE FROM lib_shamela_category");
        jdbcTemplate.update(
                "UPDATE lib_shamela_sync_state SET master_version = 0, last_synced_at = NULL WHERE id = 1");
        // подчищаем download-dir чтобы assertCleanWorkDir не реагировал на
        // остаточные файлы от предыдущего теста
        if (Files.exists(isolatedDownloadDir)) {
            try (var stream = Files.walk(isolatedDownloadDir)) {
                stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    if (!p.equals(isolatedDownloadDir)) {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) { /* best-effort */ }
                    }
                });
            }
        }
    }

    // ---------------- syncMaster ----------------

    @Test
    void syncMaster_skips_download_when_version_unchanged() {
        when(apiClient.fetchMasterMetadata(0)).thenReturn(new MasterMetadata(null, 0));

        MasterSyncResult result = service.syncMaster();

        assertThat(result.changed()).isFalse();
        assertThat(result.previousVersion()).isZero();
        assertThat(result.currentVersion()).isZero();
        assertThat(result.categoriesCount()).isZero();
        assertThat(result.authorsCount()).isZero();
        assertThat(result.booksCount()).isZero();
        verify(apiClient, never()).downloadArchive(any(), any());
        assertThat(syncStateDao.getMasterVersion()).isZero();
    }

    @Test
    void syncMaster_runs_full_pipeline_and_updates_state() throws IOException, SQLException {
        Path masterZip = createMasterFixtureZip(workspace.resolve("fixtures"));
        when(apiClient.fetchMasterMetadata(0))
                .thenReturn(new MasterMetadata("https://example.invalid/master.zip", 1261));
        when(apiClient.downloadArchive(any(), any()))
                .thenAnswer(inv -> Files.copy(masterZip, ((Path) inv.getArgument(1)).resolve("master.zip")));

        MasterSyncResult result = service.syncMaster();

        assertThat(result.changed()).isTrue();
        assertThat(result.previousVersion()).isZero();
        assertThat(result.currentVersion()).isEqualTo(1261);
        assertThat(result.categoriesCount()).isEqualTo(2);
        assertThat(result.authorsCount()).isEqualTo(2);
        assertThat(result.booksCount()).isEqualTo(2);

        assertThat(categoryDao.findAll()).hasSize(2);
        assertThat(authorDao.findAll()).hasSize(2);
        assertThat(bookDao.findAll()).hasSize(2);
        assertThat(syncStateDao.getMasterVersion()).isEqualTo(1261);
        assertThat(syncStateDao.getLastSyncedAt()).isPresent();
        assertCleanDownloadDir();
    }

    @Test
    void syncMaster_throws_when_patch_url_is_blank() {
        when(apiClient.fetchMasterMetadata(0)).thenReturn(new MasterMetadata("", 1261));

        assertThatThrownBy(() -> service.syncMaster())
                .isInstanceOf(ShamelaImportException.class)
                .hasMessageContaining("patch_url пустой");
        verify(apiClient, never()).downloadArchive(any(), any());
        assertThat(syncStateDao.getMasterVersion()).isZero();
    }

    @Test
    void syncMaster_cleans_up_workdir_when_extraction_fails() throws IOException {
        Path corrupt = workspace.resolve("corrupt.zip");
        Files.writeString(corrupt, "not-a-zip");
        when(apiClient.fetchMasterMetadata(0))
                .thenReturn(new MasterMetadata("https://example.invalid/x.zip", 99));
        when(apiClient.downloadArchive(any(), any()))
                .thenAnswer(inv -> Files.copy(corrupt, ((Path) inv.getArgument(1)).resolve("master.zip")));

        assertThatThrownBy(() -> service.syncMaster())
                .isInstanceOf(RuntimeException.class);

        // версия не обновилась - finally сработал до updateMasterVersion,
        // и сам updateMasterVersion не был достигнут
        assertThat(syncStateDao.getMasterVersion()).isZero();
        assertCleanDownloadDir();
    }

    // ---------------- importBook ----------------

    @Test
    void importBook_throws_when_book_missing_in_staging() {
        assertThatThrownBy(() -> service.importBook(99999L))
                .isInstanceOf(ShamelaImportException.class)
                .hasMessageContaining("99999")
                .hasMessageContaining("syncMaster");
        verify(apiClient, never()).downloadArchive(any(), any());
    }

    @Test
    void importBook_runs_full_pipeline() throws IOException, SQLException {
        long bookId = 41557L;
        int majorRelease = 4;
        seedBookInStaging(bookId, majorRelease);
        Path bookZip = createBookFixtureZip(workspace.resolve("fixtures-book"), bookId);
        when(apiClient.downloadArchive(any(), any()))
                .thenAnswer(inv -> Files.copy(bookZip, ((Path) inv.getArgument(1)).resolve("book.zip")));

        BookImportResult result = service.importBook(bookId);

        assertThat(result.bookId()).isEqualTo(bookId);
        assertThat(result.majorRelease()).isEqualTo(majorRelease);
        assertThat(result.pagesCount()).isEqualTo(3);
        assertThat(result.titlesCount()).isEqualTo(2);
        assertThat(pageDao.countByBookId(bookId)).isEqualTo(3);
        assertThat(titleDao.countByBookId(bookId)).isEqualTo(2);
        URI expectedUrl = URI.create("https://" + props.filesHost()
                + "/books-store/" + bookId + "-" + majorRelease + ".zip");
        verify(apiClient).downloadArchive(eq(expectedUrl), any());
        assertCleanDownloadDir();
    }

    // ---------------- helpers ----------------

    private void seedBookInStaging(long bookId, int majorRelease) {
        bookDao.upsertAll(List.of(new ShamelaBookRow(
                bookId, "тестовая книга", null, null, null, null, null,
                majorRelease, 0, null, null, null, null, false
        )));
    }

    private void assertCleanDownloadDir() {
        if (!Files.exists(isolatedDownloadDir)) {
            return;
        }
        try (var stream = Files.list(isolatedDownloadDir)) {
            assertThat(stream).as("в %s остались файлы после finally cleanup", isolatedDownloadDir)
                    .isEmpty();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path createMasterFixtureZip(Path dir) throws IOException, SQLException {
        Files.createDirectories(dir);
        Path categoryDb = dir.resolve("category.sqlite");
        Path authorDb = dir.resolve("author.sqlite");
        Path bookDb = dir.resolve("book.sqlite");

        try (Connection conn = openSqlite(categoryDb); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE category(id INTEGER, name TEXT, \"order\" TEXT, is_deleted TEXT)");
            insertCategory(conn, 1L, "Хадис", "1", "0");
            insertCategory(conn, 2L, "Фикх", "2", "0");
        }
        try (Connection conn = openSqlite(authorDb); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE author(id INTEGER, name TEXT, biography TEXT, death_number TEXT, is_deleted TEXT)");
            insertAuthor(conn, 100L, "Аль-Бухари", "имам", "256", "0");
            insertAuthor(conn, 101L, "Муслим", "имам", "261", "0");
        }
        try (Connection conn = openSqlite(bookDb); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE book("
                    + "id INTEGER, name TEXT, category TEXT, author TEXT, type TEXT, "
                    + "date TEXT, printed TEXT, major_release TEXT, minor_release TEXT, "
                    + "bibliography TEXT, hint TEXT, pdf_links TEXT, metadata TEXT, is_deleted TEXT)");
            insertBook(conn, 41557L, "صحيح البخاري", "1", "100", "1",
                    "256", "1", "4", "0", "", "", null, null, "0");
            insertBook(conn, 41558L, "صحيح مسلم", "1", "101", "1",
                    "261", "1", "3", "0", "", "", null, null, "0");
        }

        Path zip = dir.resolve("master-0-1261.zip");
        try (OutputStream fos = Files.newOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            putBinaryEntry(zos, "category.sqlite", Files.readAllBytes(categoryDb));
            putBinaryEntry(zos, "author.sqlite", Files.readAllBytes(authorDb));
            putBinaryEntry(zos, "book.sqlite", Files.readAllBytes(bookDb));
        }
        return zip;
    }

    private static Path createBookFixtureZip(Path dir, long bookId) throws IOException, SQLException {
        Files.createDirectories(dir);
        Path sqlite = dir.resolve(bookId + ".sqlite");
        try (Connection conn = openSqlite(sqlite); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE page(id INTEGER, content TEXT, part TEXT, page TEXT, number TEXT, services TEXT)");
            stmt.execute("CREATE TABLE title(id INTEGER, content TEXT, page TEXT, parent TEXT)");
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO page(id, content, part, page, number, services) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, 1); ps.setString(2, "<p>الصفحة الأولى</p>"); ps.setString(3, "1");
                ps.setString(4, "1"); ps.setString(5, "1"); ps.setString(6, null); ps.executeUpdate();
                ps.setInt(1, 2); ps.setString(2, "<p>الصفحة الثانية</p>"); ps.setString(3, "1");
                ps.setString(4, "2"); ps.setString(5, "2"); ps.setString(6, null); ps.executeUpdate();
                ps.setInt(1, 3); ps.setString(2, "<p>الصفحة الثالثة</p>"); ps.setString(3, "1");
                ps.setString(4, "3"); ps.setString(5, "3"); ps.setString(6, null); ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO title(id, content, page, parent) VALUES (?, ?, ?, ?)")) {
                ps.setInt(1, 1); ps.setString(2, "Введение"); ps.setString(3, "1"); ps.setString(4, null); ps.executeUpdate();
                ps.setInt(1, 2); ps.setString(2, "Глава первая"); ps.setString(3, "2"); ps.setString(4, "1"); ps.executeUpdate();
            }
        }
        // shamela кладёт sqlite внутрь book-zip с именем {bookId}-{major}.sqlite
        // (наблюдалось на live: 1681-6.zip содержит 1681-6.sqlite). Старый
        // формат {bookId}.sqlite поддерживается через fallback в
        // ShamelaImportService.findBookSqlite, см. gotchas
        Path zip = dir.resolve(bookId + "-4.zip");
        try (OutputStream fos = Files.newOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            putBinaryEntry(zos, bookId + "-4.sqlite", Files.readAllBytes(sqlite));
        }
        return zip;
    }

    private static Connection openSqlite(Path file) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
    }

    private static void putBinaryEntry(ZipOutputStream zos, String name, byte[] content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content);
        zos.closeEntry();
    }

    private static void insertCategory(Connection conn, long id, String name, String order, String isDeleted)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO category(id, name, \"order\", is_deleted) VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, id); ps.setString(2, name); ps.setString(3, order); ps.setString(4, isDeleted);
            ps.executeUpdate();
        }
    }

    private static void insertAuthor(Connection conn, long id, String name, String biography,
                                     String deathNumber, String isDeleted) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO author(id, name, biography, death_number, is_deleted) VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, id); ps.setString(2, name); ps.setString(3, biography);
            ps.setString(4, deathNumber); ps.setString(5, isDeleted);
            ps.executeUpdate();
        }
    }

    private static void insertBook(Connection conn, long id, String name, String category, String author,
                                   String type, String date, String printed, String majorRelease,
                                   String minorRelease, String bibliography, String hint,
                                   String pdfLinks, String metadata, String isDeleted) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO book(id, name, category, author, type, date, printed, major_release, "
                        + "minor_release, bibliography, hint, pdf_links, metadata, is_deleted) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, id); ps.setString(2, name); ps.setString(3, category); ps.setString(4, author);
            ps.setString(5, type); ps.setString(6, date); ps.setString(7, printed); ps.setString(8, majorRelease);
            ps.setString(9, minorRelease); ps.setString(10, bibliography); ps.setString(11, hint);
            ps.setString(12, pdfLinks); ps.setString(13, metadata); ps.setString(14, isDeleted);
            ps.executeUpdate();
        }
    }
}
