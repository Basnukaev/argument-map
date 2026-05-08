package ru.basnukaev.argumentmap.library.shamela.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaAuthorRow;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaBookRow;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaCategoryRow;

class ShamelaMasterReaderTest {

    private final ShamelaMasterReader reader = new ShamelaMasterReader();

    // ---------------- categories ----------------

    @Test
    void readCategories_returns_all_rows_with_correct_parsing(@TempDir Path tmp) throws SQLException {
        Path file = tmp.resolve("category.sqlite");
        try (Connection conn = openSqlite(file); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE category(id INTEGER, name TEXT, \"order\" TEXT, is_deleted TEXT)");
            insertCategory(conn, 1L, "العقيدة", "10", "0");
            insertCategory(conn, 2L, "الفقه", "20", "0");
            insertCategory(conn, 3L, "التفسير", "30", "0");
        }

        List<ShamelaCategoryRow> rows = reader.readCategories(file);

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).id()).isEqualTo(1L);
        assertThat(rows.get(0).name()).isEqualTo("العقيدة");
        assertThat(rows.get(0).displayOrder()).isEqualTo(10);
        assertThat(rows.get(0).deleted()).isFalse();
        assertThat(rows).extracting(ShamelaCategoryRow::id).containsExactly(1L, 2L, 3L);
    }

    @Test
    void readCategories_handles_blank_order_as_null(@TempDir Path tmp) throws SQLException {
        Path file = tmp.resolve("category.sqlite");
        try (Connection conn = openSqlite(file); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE category(id INTEGER, name TEXT, \"order\" TEXT, is_deleted TEXT)");
            insertCategory(conn, 1L, "пусто", "", "0");
            insertCategory(conn, 2L, "null", null, "0");
        }

        List<ShamelaCategoryRow> rows = reader.readCategories(file);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).displayOrder()).isNull();
        assertThat(rows.get(1).displayOrder()).isNull();
    }

    @Test
    void readCategories_marks_deleted_correctly(@TempDir Path tmp) throws SQLException {
        Path file = tmp.resolve("category.sqlite");
        try (Connection conn = openSqlite(file); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE category(id INTEGER, name TEXT, \"order\" TEXT, is_deleted TEXT)");
            insertCategory(conn, 1L, "deleted", "1", "1");
            insertCategory(conn, 2L, "alive", "2", "0");
        }

        List<ShamelaCategoryRow> rows = reader.readCategories(file);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).deleted()).isTrue();
        assertThat(rows.get(1).deleted()).isFalse();
    }

    @Test
    void readCategories_throws_on_missing_file(@TempDir Path tmp) {
        Path missing = tmp.resolve("nope.sqlite");

        assertThatThrownBy(() -> reader.readCategories(missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("файл не найден");
    }

    // ---------------- authors ----------------

    @Test
    void readAuthors_parses_unknown_year_99999_as_null(@TempDir Path tmp) throws SQLException {
        Path file = tmp.resolve("author.sqlite");
        try (Connection conn = openSqlite(file); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE author(id INTEGER, name TEXT, biography TEXT, death_number TEXT, is_deleted TEXT)");
            insertAuthor(conn, 1L, "Неизвестный", "био", "99999", "0");
        }

        List<ShamelaAuthorRow> rows = reader.readAuthors(file);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).deathYear()).isNull();
        assertThat(rows.get(0).biography()).isEqualTo("био");
    }

    @Test
    void readAuthors_parses_normal_year(@TempDir Path tmp) throws SQLException {
        Path file = tmp.resolve("author.sqlite");
        try (Connection conn = openSqlite(file); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE author(id INTEGER, name TEXT, biography TEXT, death_number TEXT, is_deleted TEXT)");
            insertAuthor(conn, 100L, "Ибн Таймия", "учёный", "728", "0");
            insertAuthor(conn, 101L, "пустая био", null, "456", "0");
        }

        List<ShamelaAuthorRow> rows = reader.readAuthors(file);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).id()).isEqualTo(100L);
        assertThat(rows.get(0).name()).isEqualTo("Ибн Таймия");
        assertThat(rows.get(0).deathYear()).isEqualTo(728);
        assertThat(rows.get(0).deleted()).isFalse();
        assertThat(rows.get(1).biography()).isNull();
        assertThat(rows.get(1).deathYear()).isEqualTo(456);
    }

    @Test
    void readAuthors_throws_on_missing_file(@TempDir Path tmp) {
        Path missing = tmp.resolve("nope.sqlite");

        assertThatThrownBy(() -> reader.readAuthors(missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("файл не найден");
    }

    // ---------------- books ----------------

    @Test
    void readBooks_handles_full_book_with_all_fields(@TempDir Path tmp) throws SQLException {
        Path file = tmp.resolve("book.sqlite");
        try (Connection conn = openSqlite(file); Statement stmt = conn.createStatement()) {
            stmt.execute(createBookTableSql());
            insertBook(conn, 41557L, "البخاري", "1", "100", "1", "256", "1",
                    "4", "0", "библиография", "hint-text",
                    "{\"files\":[\"/1/41557.pdf\"]}",
                    "{\"source\":\"shamela\"}",
                    "0");
        }

        List<ShamelaBookRow> rows = reader.readBooks(file);

        assertThat(rows).hasSize(1);
        ShamelaBookRow row = rows.get(0);
        assertThat(row.id()).isEqualTo(41557L);
        assertThat(row.name()).isEqualTo("البخاري");
        assertThat(row.categoryId()).isEqualTo(1L);
        assertThat(row.authorId()).isEqualTo(100L);
        assertThat(row.type()).isEqualTo(1);
        assertThat(row.publicationYear()).isEqualTo(256);
        assertThat(row.isPrinted()).isTrue();
        assertThat(row.majorRelease()).isEqualTo(4);
        assertThat(row.minorRelease()).isEqualTo(0);
        assertThat(row.bibliography()).isEqualTo("библиография");
        assertThat(row.hint()).isEqualTo("hint-text");
        assertThat(row.deleted()).isFalse();
    }

    @Test
    void readBooks_parses_jsonb_columns_as_raw_strings(@TempDir Path tmp) throws SQLException {
        Path file = tmp.resolve("book.sqlite");
        String pdfLinks = "{\"files\":[\"/1/41557.pdf\"]}";
        String metadata = "{\"source\":\"shamela\",\"v\":2}";
        try (Connection conn = openSqlite(file); Statement stmt = conn.createStatement()) {
            stmt.execute(createBookTableSql());
            insertBook(conn, 1L, "x", "1", "1", "1", "1000", "1",
                    "1", "0", "", "", pdfLinks, metadata, "0");
            insertBook(conn, 2L, "y", "1", "1", "1", "1000", "1",
                    "1", "0", "", "", null, null, "0");
        }

        List<ShamelaBookRow> rows = reader.readBooks(file);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).pdfLinksJson()).isEqualTo(pdfLinks);
        assertThat(rows.get(0).extraMetadataJson()).isEqualTo(metadata);
        assertThat(rows.get(1).pdfLinksJson()).isNull();
        assertThat(rows.get(1).extraMetadataJson()).isNull();
    }

    @Test
    void readBooks_defaults_missing_major_release_to_zero(@TempDir Path tmp) throws SQLException {
        Path file = tmp.resolve("book.sqlite");
        try (Connection conn = openSqlite(file); Statement stmt = conn.createStatement()) {
            stmt.execute(createBookTableSql());
            // major_release пустой, minor_release null - оба должны стать 0
            insertBook(conn, 1L, "noversion", "1", "1", "1", "1000", "1",
                    "", null, "", "", null, null, "0");
        }

        List<ShamelaBookRow> rows = reader.readBooks(file);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).majorRelease()).isZero();
        assertThat(rows.get(0).minorRelease()).isZero();
    }

    @Test
    void readBooks_parses_unknown_year_99999_as_null(@TempDir Path tmp) throws SQLException {
        Path file = tmp.resolve("book.sqlite");
        try (Connection conn = openSqlite(file); Statement stmt = conn.createStatement()) {
            stmt.execute(createBookTableSql());
            insertBook(conn, 1L, "x", "1", "1", "1", "99999", "1",
                    "1", "0", "", "", null, null, "0");
        }

        List<ShamelaBookRow> rows = reader.readBooks(file);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).publicationYear()).isNull();
    }

    @Test
    void readBooks_marks_deleted_correctly(@TempDir Path tmp) throws SQLException {
        Path file = tmp.resolve("book.sqlite");
        try (Connection conn = openSqlite(file); Statement stmt = conn.createStatement()) {
            stmt.execute(createBookTableSql());
            insertBook(conn, 1L, "alive", "1", "1", "1", "1000", "1", "1", "0", "", "", null, null, "0");
            insertBook(conn, 2L, "dead", "1", "1", "1", "1000", "1", "1", "0", "", "", null, null, "1");
        }

        List<ShamelaBookRow> rows = reader.readBooks(file);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).deleted()).isFalse();
        assertThat(rows.get(1).deleted()).isTrue();
    }

    @Test
    void readBooks_handles_blank_printed_as_null_boolean(@TempDir Path tmp) throws SQLException {
        Path file = tmp.resolve("book.sqlite");
        try (Connection conn = openSqlite(file); Statement stmt = conn.createStatement()) {
            stmt.execute(createBookTableSql());
            insertBook(conn, 1L, "x", "1", "1", "1", "1000", "", "1", "0", "", "", null, null, "0");
            insertBook(conn, 2L, "y", "1", "1", "1", "1000", "0", "1", "0", "", "", null, null, "0");
        }

        List<ShamelaBookRow> rows = reader.readBooks(file);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).isPrinted()).isNull();
        assertThat(rows.get(1).isPrinted()).isFalse();
    }

    @Test
    void readBooks_throws_on_missing_file(@TempDir Path tmp) {
        Path missing = tmp.resolve("nope.sqlite");

        assertThatThrownBy(() -> reader.readBooks(missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("файл не найден");
    }

    // ---------------- helpers ----------------

    private static Connection openSqlite(Path file) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
    }

    private static String createBookTableSql() {
        return "CREATE TABLE book("
                + "id INTEGER, name TEXT, category TEXT, author TEXT, type TEXT, "
                + "date TEXT, printed TEXT, major_release TEXT, minor_release TEXT, "
                + "bibliography TEXT, hint TEXT, pdf_links TEXT, metadata TEXT, is_deleted TEXT)";
    }

    private static void insertCategory(Connection conn, long id, String name, String order, String isDeleted)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO category(id, name, \"order\", is_deleted) VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, id);
            ps.setString(2, name);
            ps.setString(3, order);
            ps.setString(4, isDeleted);
            ps.executeUpdate();
        }
    }

    private static void insertAuthor(Connection conn, long id, String name, String biography,
                                     String deathNumber, String isDeleted) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO author(id, name, biography, death_number, is_deleted) VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, id);
            ps.setString(2, name);
            ps.setString(3, biography);
            ps.setString(4, deathNumber);
            ps.setString(5, isDeleted);
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
            ps.setLong(1, id);
            ps.setString(2, name);
            ps.setString(3, category);
            ps.setString(4, author);
            ps.setString(5, type);
            ps.setString(6, date);
            ps.setString(7, printed);
            ps.setString(8, majorRelease);
            ps.setString(9, minorRelease);
            ps.setString(10, bibliography);
            ps.setString(11, hint);
            ps.setString(12, pdfLinks);
            ps.setString(13, metadata);
            ps.setString(14, isDeleted);
            ps.executeUpdate();
        }
    }
}
