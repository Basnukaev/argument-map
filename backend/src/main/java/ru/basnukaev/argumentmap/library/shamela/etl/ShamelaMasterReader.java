package ru.basnukaev.argumentmap.library.shamela.etl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaAuthorRow;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaBookRow;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaCategoryRow;

/**
 * Читает три master-таблицы shamela ({@code category}, {@code author},
 * {@code book}) из соответствующих SQLite-файлов после распаковки
 * master-zip через {@link ShamelaArchiveExtractor}.
 *
 * <p>Eager-чтение в {@code List<...Row>}: master-таблицы небольшие
 * (~12MB SQLite, ~1-2MB heap при разборе), поэтому Stream-API не нужен -
 * проще держать весь набор в памяти и передать DAO для bulk upsert.
 *
 * <p>Подключение - через {@code DriverManager.getConnection("jdbc:sqlite:...")},
 * без Spring DataSource. Каждый вызов открывает свой Connection и
 * закрывает его в try-with-resources - чтобы один и тот же ридер
 * последовательно открывал три разных файла без shared state.
 */
@Component
public class ShamelaMasterReader {

    private static final Logger log = LoggerFactory.getLogger(ShamelaMasterReader.class);

    /**
     * Читает {@code category.sqlite}. {@code "order"} - зарезервированное
     * слово SQL, оборачиваем в двойные кавычки.
     */
    public List<ShamelaCategoryRow> readCategories(Path categorySqliteFile) {
        ensureFileExists(categorySqliteFile);
        String sql = "SELECT id, name, \"order\", is_deleted FROM category";
        List<ShamelaCategoryRow> rows = new ArrayList<>();
        try (Connection conn = openConnection(categorySqliteFile);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                rows.add(new ShamelaCategoryRow(
                        rs.getLong("id"),
                        rs.getString("name"),
                        SqliteValueParser.parseIntegerOrNull(rs.getString("order")),
                        SqliteValueParser.isDeletedFlag(rs.getString("is_deleted"))
                ));
            }
        } catch (SQLException e) {
            throw new ShamelaReaderException("ошибка чтения shamela category SQLite: " + categorySqliteFile, e);
        }
        log.info("shamela read categories: count={}", rows.size());
        return rows;
    }

    public List<ShamelaAuthorRow> readAuthors(Path authorSqliteFile) {
        ensureFileExists(authorSqliteFile);
        String sql = "SELECT id, name, biography, death_number, is_deleted FROM author";
        List<ShamelaAuthorRow> rows = new ArrayList<>();
        try (Connection conn = openConnection(authorSqliteFile);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                rows.add(new ShamelaAuthorRow(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("biography"),
                        SqliteValueParser.parseYearOrNull(rs.getString("death_number")),
                        SqliteValueParser.isDeletedFlag(rs.getString("is_deleted"))
                ));
            }
        } catch (SQLException e) {
            throw new ShamelaReaderException("ошибка чтения shamela author SQLite: " + authorSqliteFile, e);
        }
        log.info("shamela read authors: count={}", rows.size());
        return rows;
    }

    /**
     * Читает {@code book.sqlite}. {@code pdf_links} и {@code metadata} -
     * сырые JSON-строки, не парсим в JsonNode тут (DAO положит в
     * {@code jsonb}-колонку как-есть, postgres сам валидирует).
     * {@code major_release}/{@code minor_release} в shamela могут быть
     * пустыми для старых записей - дефолтим в 0 чтобы согласовать с
     * NOT NULL в lib_shamela_book.
     */
    public List<ShamelaBookRow> readBooks(Path bookSqliteFile) {
        ensureFileExists(bookSqliteFile);
        String sql = "SELECT id, name, category, author, type, date, printed, "
                + "major_release, minor_release, bibliography, hint, "
                + "pdf_links, metadata, is_deleted FROM book";
        List<ShamelaBookRow> rows = new ArrayList<>();
        try (Connection conn = openConnection(bookSqliteFile);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Integer majorParsed = SqliteValueParser.parseIntegerOrNull(rs.getString("major_release"));
                Integer minorParsed = SqliteValueParser.parseIntegerOrNull(rs.getString("minor_release"));
                rows.add(new ShamelaBookRow(
                        rs.getLong("id"),
                        rs.getString("name"),
                        SqliteValueParser.parseLongOrNull(rs.getString("category")),
                        SqliteValueParser.parseLongOrNull(rs.getString("author")),
                        SqliteValueParser.parseIntegerOrNull(rs.getString("type")),
                        SqliteValueParser.parseYearOrNull(rs.getString("date")),
                        SqliteValueParser.parseBoolOrNull(rs.getString("printed")),
                        majorParsed != null ? majorParsed : 0,
                        minorParsed != null ? minorParsed : 0,
                        rs.getString("bibliography"),
                        rs.getString("hint"),
                        rs.getString("pdf_links"),
                        rs.getString("metadata"),
                        SqliteValueParser.isDeletedFlag(rs.getString("is_deleted"))
                ));
            }
        } catch (SQLException e) {
            throw new ShamelaReaderException("ошибка чтения shamela book SQLite: " + bookSqliteFile, e);
        }
        log.info("shamela read books: count={}", rows.size());
        return rows;
    }

    private static void ensureFileExists(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("файл не найден: " + path);
        }
    }

    private static Connection openConnection(Path sqliteFile) throws SQLException {
        // абсолютный путь нужен потому что DriverManager не учитывает workdir,
        // а путь может прийти относительный (например из тестового @TempDir)
        String absolute = sqliteFile.toAbsolutePath().toString();
        return DriverManager.getConnection("jdbc:sqlite:" + absolute);
    }
}
