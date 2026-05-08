package ru.basnukaev.argumentmap.library.shamela.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaBookContent;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaPageRow;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaTitleRow;

class ShamelaBookReaderTest {

    private static final long BOOK_ID = 35077L;

    private final ShamelaBookReader reader = new ShamelaBookReader();

    @Test
    void read_returns_pages_and_titles_with_book_id_set(@TempDir Path tmp) throws SQLException {
        Path file = tmp.resolve(BOOK_ID + ".sqlite");
        try (Connection c = open(file); Statement s = c.createStatement()) {
            createSchema(s);
            s.execute("INSERT INTO page (id, content, part, page, number, services) "
                    + "VALUES (1, 'p1', '1', '15', '1', '')");
            s.execute("INSERT INTO page (id, content, part, page, number, services) "
                    + "VALUES (2, 'p2', '1', '16', '2', '')");
            s.execute("INSERT INTO title (id, content, page, parent) "
                    + "VALUES (10, 'Глава 1', '15', '0')");
        }

        ShamelaBookContent content = reader.read(file, BOOK_ID);

        assertThat(content.pages()).hasSize(2)
                .allSatisfy(row -> assertThat(row.bookId()).isEqualTo(BOOK_ID));
        assertThat(content.titles()).hasSize(1)
                .allSatisfy(row -> assertThat(row.bookId()).isEqualTo(BOOK_ID));
    }

    @Test
    void read_pages_preserves_html_content_as_is(@TempDir Path tmp) throws SQLException {
        Path file = tmp.resolve(BOOK_ID + ".sqlite");
        String html = "<p>Hello <b>world</b></p>";
        try (Connection c = open(file); Statement s = c.createStatement()) {
            createSchema(s);
            // используем prepared insert через execute с эскейпингом кавычек - здесь html без кавычек
            s.execute("INSERT INTO page (id, content, part, page, number, services) "
                    + "VALUES (1, '" + html + "', 'P1', '5', '1', 'svc')");
        }

        ShamelaBookContent content = reader.read(file, BOOK_ID);

        assertThat(content.pages()).hasSize(1);
        ShamelaPageRow row = content.pages().get(0);
        assertThat(row.id()).isEqualTo(1);
        assertThat(row.content()).isEqualTo(html);
        assertThat(row.part()).isEqualTo("P1");
        assertThat(row.printedPage()).isEqualTo("5");
        assertThat(row.number()).isEqualTo("1");
        assertThat(row.services()).isEqualTo("svc");
    }

    @Test
    void read_titles_parses_root_parent_zero(@TempDir Path tmp) throws SQLException {
        Path file = tmp.resolve(BOOK_ID + ".sqlite");
        try (Connection c = open(file); Statement s = c.createStatement()) {
            createSchema(s);
            s.execute("INSERT INTO title (id, content, page, parent) "
                    + "VALUES (1, 'root chapter', '10', '0')");
        }

        ShamelaBookContent content = reader.read(file, BOOK_ID);

        assertThat(content.titles()).hasSize(1);
        ShamelaTitleRow row = content.titles().get(0);
        assertThat(row.parentId()).isEqualTo(0);
        assertThat(row.pageRef()).isEqualTo("10");
    }

    @Test
    void read_titles_parses_blank_parent_as_null(@TempDir Path tmp) throws SQLException {
        Path file = tmp.resolve(BOOK_ID + ".sqlite");
        try (Connection c = open(file); Statement s = c.createStatement()) {
            createSchema(s);
            s.execute("INSERT INTO title (id, content, page, parent) "
                    + "VALUES (1, 'no-parent chapter', '10', '')");
        }

        ShamelaBookContent content = reader.read(file, BOOK_ID);

        assertThat(content.titles()).hasSize(1);
        assertThat(content.titles().get(0).parentId()).isNull();
    }

    @Test
    void read_titles_parses_nested_parent(@TempDir Path tmp) throws SQLException {
        Path file = tmp.resolve(BOOK_ID + ".sqlite");
        try (Connection c = open(file); Statement s = c.createStatement()) {
            createSchema(s);
            s.execute("INSERT INTO title (id, content, page, parent) "
                    + "VALUES (7, 'nested chapter', '12', '5')");
        }

        ShamelaBookContent content = reader.read(file, BOOK_ID);

        assertThat(content.titles()).hasSize(1);
        ShamelaTitleRow row = content.titles().get(0);
        assertThat(row.id()).isEqualTo(7);
        assertThat(row.parentId()).isEqualTo(5);
    }

    @Test
    void read_handles_empty_book(@TempDir Path tmp) throws SQLException {
        Path file = tmp.resolve(BOOK_ID + ".sqlite");
        try (Connection c = open(file); Statement s = c.createStatement()) {
            createSchema(s);
            // никаких inserts - пустые таблицы
        }

        ShamelaBookContent content = reader.read(file, BOOK_ID);

        assertThat(content.pages()).isEmpty();
        assertThat(content.titles()).isEmpty();
    }

    @Test
    void read_throws_on_missing_file(@TempDir Path tmp) {
        Path missing = tmp.resolve("nope.sqlite");

        assertThatThrownBy(() -> reader.read(missing, BOOK_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("файл не найден");
    }

    @Test
    void read_handles_arabic_content_correctly(@TempDir Path tmp) throws SQLException {
        Path file = tmp.resolve(BOOK_ID + ".sqlite");
        String arabic = "الحمد لله رب العالمين";
        try (Connection c = open(file); Statement s = c.createStatement()) {
            createSchema(s);
            s.execute("INSERT INTO page (id, content, part, page, number, services) "
                    + "VALUES (1, '" + arabic + "', '1', '1', '1', '')");
            s.execute("INSERT INTO title (id, content, page, parent) "
                    + "VALUES (1, '" + arabic + "', '1', '0')");
        }

        ShamelaBookContent content = reader.read(file, BOOK_ID);

        assertThat(content.pages()).hasSize(1);
        assertThat(content.pages().get(0).content()).isEqualTo(arabic);
        assertThat(content.titles()).hasSize(1);
        assertThat(content.titles().get(0).content()).isEqualTo(arabic);
    }

    @Test
    void read_returns_multiple_titles_in_hierarchy(@TempDir Path tmp) throws SQLException {
        // проверка что несколько title-строк читаются корректно с разными parent-значениями
        Path file = tmp.resolve(BOOK_ID + ".sqlite");
        try (Connection c = open(file); Statement s = c.createStatement()) {
            createSchema(s);
            s.execute("INSERT INTO title (id, content, page, parent) VALUES (1, 'root', '1', '0')");
            s.execute("INSERT INTO title (id, content, page, parent) VALUES (2, 'child of 1', '5', '1')");
            s.execute("INSERT INTO title (id, content, page, parent) VALUES (3, 'orphan', '7', '')");
        }

        ShamelaBookContent content = reader.read(file, BOOK_ID);

        assertThat(content.titles()).hasSize(3);
        assertThat(content.titles())
                .extracting(ShamelaTitleRow::id, ShamelaTitleRow::parentId)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1, 0),
                        org.assertj.core.groups.Tuple.tuple(2, 1),
                        org.assertj.core.groups.Tuple.tuple(3, null)
                );
    }

    private static Connection open(Path sqliteFile) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + sqliteFile.toAbsolutePath());
    }

    private static void createSchema(Statement s) throws SQLException {
        s.execute("CREATE TABLE page (id INTEGER PRIMARY KEY, content TEXT, part TEXT, "
                + "page TEXT, number TEXT, services TEXT)");
        s.execute("CREATE TABLE title (id INTEGER PRIMARY KEY, content TEXT, "
                + "page TEXT, parent TEXT)");
    }
}
