package ru.basnukaev.argumentmap.library.shamela.repository;

import static ru.basnukaev.argumentmap.library.shamela.repository.ShamelaDaoSupport.BATCH_SIZE;
import static ru.basnukaev.argumentmap.library.shamela.repository.ShamelaDaoSupport.setNullableInt;
import static ru.basnukaev.argumentmap.library.shamela.repository.ShamelaDaoSupport.setNullableString;
import static ru.basnukaev.argumentmap.library.shamela.repository.ShamelaDaoSupport.sumAffected;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaTitleRow;

/**
 * DAO для staging-таблицы {@code lib_shamela_title}. Составной PK
 * {@code (book_id, id)}, как в {@link ShamelaPageDao}. {@code parent_id}
 * - nullable Integer (в shamela 0 означает корневой заголовок, но
 * парсер уже превращает его в null при необходимости).
 */
@Repository
public class ShamelaTitleDao {

    private static final Logger log = LoggerFactory.getLogger(ShamelaTitleDao.class);

    private static final String COLUMNS = "book_id, id, content, page_ref, parent_id";

    private static final RowMapper<ShamelaTitleRow> ROW_MAPPER = (rs, rn) -> {
        int parent = rs.getInt("parent_id");
        Integer parentId = rs.wasNull() ? null : parent;
        return new ShamelaTitleRow(
                rs.getLong("book_id"),
                rs.getInt("id"),
                rs.getString("content"),
                rs.getString("page_ref"),
                parentId
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public ShamelaTitleDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int upsertAll(List<ShamelaTitleRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO lib_shamela_title (book_id, id, content, page_ref, parent_id)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (book_id, id) DO UPDATE SET
                    content = EXCLUDED.content,
                    page_ref = EXCLUDED.page_ref,
                    parent_id = EXCLUDED.parent_id
                """;
        int[][] result = jdbcTemplate.batchUpdate(sql, rows, BATCH_SIZE, (ps, row) -> {
            ps.setLong(1, row.bookId());
            ps.setInt(2, row.id());
            ps.setString(3, row.content());
            setNullableString(ps, 4, row.pageRef());
            setNullableInt(ps, 5, row.parentId());
        });
        int total = sumAffected(result);
        log.info("shamela {} upsert: rows={}", "title", total);
        return total;
    }

    public int countByBookId(long bookId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lib_shamela_title WHERE book_id = ?",
                Integer.class,
                bookId
        );
        return count == null ? 0 : count;
    }

    /**
     * Все заголовки книги в порядке возрастания id (shamela вставляет
     * id монотонно в порядке появления заголовка в книге - даёт
     * естественный sort_order для маппера).
     */
    public List<ShamelaTitleRow> findAllByBookId(long bookId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_shamela_title WHERE book_id = ? ORDER BY id",
                ROW_MAPPER,
                bookId
        );
    }

    public Optional<ShamelaTitleRow> findByBookIdAndId(long bookId, int id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_shamela_title WHERE book_id = ? AND id = ?",
                ROW_MAPPER,
                bookId, id
        ).stream().findFirst();
    }
}
