package ru.basnukaev.argumentmap.library.shamela.etl;

import static ru.basnukaev.argumentmap.library.shamela.etl.SqliteValueParser.parseIntegerOrNull;

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

import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaBookContent;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaPageRow;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaTitleRow;

/**
 * Читает {@code {bookId}.sqlite} - одиночный SQLite-файл из распакованного
 * {@code books-store/{bookId}-{major}.zip}. Внутри ровно две таблицы:
 * {@code page} (страницы с HTML-контентом) и {@code title} (иерархия
 * заголовков-глав со ссылкой на страницу).
 *
 * <p>ВАЖНО: сам {@code bookId} в файле не хранится - он зашит в имени
 * файла, поэтому передаётся параметром и проставляется в каждый Row.
 *
 * <p>За один вызов {@code read(...)} открывается ровно один JDBC-Connection
 * (try-with-resources), две таблицы читаются последовательно. Это дешевле
 * чем два отдельных метода, потому что каждое открытие SQLite-Connection
 * это полноценный fopen + чтение заголовка.
 */
@Component
public class ShamelaBookReader {

    private static final Logger log = LoggerFactory.getLogger(ShamelaBookReader.class);

    private static final String SELECT_PAGES =
            "SELECT id, content, part, page, number, services FROM page";

    private static final String SELECT_TITLES =
            "SELECT id, content, page, parent FROM title";

    public ShamelaBookContent read(Path bookSqliteFile, long bookId) {
        if (bookSqliteFile == null || !Files.isRegularFile(bookSqliteFile)) {
            throw new IllegalArgumentException("файл не найден: " + bookSqliteFile);
        }
        String jdbcUrl = "jdbc:sqlite:" + bookSqliteFile.toAbsolutePath();
        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            List<ShamelaPageRow> pages = readPages(conn, bookId);
            List<ShamelaTitleRow> titles = readTitles(conn, bookId);
            log.info("shamela read book: bookId={} pages={} titles={}",
                    bookId, pages.size(), titles.size());
            return new ShamelaBookContent(pages, titles);
        } catch (SQLException e) {
            throw new ShamelaReaderException(
                    "ошибка чтения shamela SQLite: " + bookSqliteFile, e);
        }
    }

    private List<ShamelaPageRow> readPages(Connection conn, long bookId) throws SQLException {
        List<ShamelaPageRow> rows = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_PAGES)) {
            while (rs.next()) {
                rows.add(new ShamelaPageRow(
                        bookId,
                        rs.getInt("id"),
                        rs.getString("content"),
                        rs.getString("part"),
                        rs.getString("page"),
                        rs.getString("number"),
                        rs.getString("services")
                ));
            }
        }
        return rows;
    }

    private List<ShamelaTitleRow> readTitles(Connection conn, long bookId) throws SQLException {
        List<ShamelaTitleRow> rows = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_TITLES)) {
            while (rs.next()) {
                rows.add(new ShamelaTitleRow(
                        bookId,
                        rs.getInt("id"),
                        rs.getString("content"),
                        rs.getString("page"),
                        parseIntegerOrNull(rs.getString("parent"))
                ));
            }
        }
        return rows;
    }
}
