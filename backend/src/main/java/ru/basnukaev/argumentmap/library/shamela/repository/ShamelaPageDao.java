package ru.basnukaev.argumentmap.library.shamela.repository;

import java.sql.Types;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaPageRow;

/**
 * DAO для staging-таблицы {@code lib_shamela_page}. Составной PK
 * {@code (book_id, id)} - {@code id} уникален в пределах одной книги
 * (shamela-соглашение). {@code ON CONFLICT (book_id, id) DO UPDATE}
 * перезаписывает контент при повторном sync.
 */
@Repository
public class ShamelaPageDao {

    public static final int BATCH_SIZE = 1000;

    private static final Logger log = LoggerFactory.getLogger(ShamelaPageDao.class);

    private static final String COLUMNS = "book_id, id, content, part, printed_page, number, services";

    private static final RowMapper<ShamelaPageRow> ROW_MAPPER = (rs, rn) -> new ShamelaPageRow(
            rs.getLong("book_id"),
            rs.getInt("id"),
            rs.getString("content"),
            rs.getString("part"),
            rs.getString("printed_page"),
            rs.getString("number"),
            rs.getString("services")
    );

    private final JdbcTemplate jdbcTemplate;

    public ShamelaPageDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int upsertAll(List<ShamelaPageRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO lib_shamela_page (book_id, id, content, part, printed_page, number, services)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (book_id, id) DO UPDATE SET
                    content = EXCLUDED.content,
                    part = EXCLUDED.part,
                    printed_page = EXCLUDED.printed_page,
                    number = EXCLUDED.number,
                    services = EXCLUDED.services
                """;
        int[][] result = jdbcTemplate.batchUpdate(sql, rows, BATCH_SIZE, (ps, row) -> {
            ps.setLong(1, row.bookId());
            ps.setInt(2, row.id());
            ps.setString(3, row.content());
            setNullableString(ps, 4, row.part());
            setNullableString(ps, 5, row.printedPage());
            setNullableString(ps, 6, row.number());
            setNullableString(ps, 7, row.services());
        });
        int total = sumAffected(result);
        log.info("shamela {} upsert: rows={}", "page", total);
        return total;
    }

    public int countByBookId(long bookId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lib_shamela_page WHERE book_id = ?",
                Integer.class,
                bookId
        );
        return count == null ? 0 : count;
    }

    public Optional<ShamelaPageRow> findByBookIdAndId(long bookId, int id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_shamela_page WHERE book_id = ? AND id = ?",
                ROW_MAPPER,
                bookId, id
        ).stream().findFirst();
    }

    private static void setNullableString(java.sql.PreparedStatement ps, int idx, String value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(idx, Types.VARCHAR);
        } else {
            ps.setString(idx, value);
        }
    }

    private static int sumAffected(int[][] batches) {
        int total = 0;
        for (int[] batch : batches) {
            for (int n : batch) {
                total += (n >= 0) ? n : 1;
            }
        }
        return total;
    }
}
