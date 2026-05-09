package ru.basnukaev.argumentmap.library.shamela.repository;

import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaBookRow;

/**
 * DAO для staging-таблицы {@code lib_shamela_book}. Особенности:
 * <ul>
 *   <li>JSONB-колонки {@code pdf_links} и {@code extra_metadata} - подаются
 *       параметром как строка с явным {@code ::jsonb}-cast в SQL. Так же
 *       работает существующий {@code SourceRepository}/{@code BookRepository}
 *       (postgresql JDBC у нас в runtime-scope, прямой PGobject недоступен
 *       на compile)</li>
 *   <li>{@code imported_at} проставляется на INSERT и обновляется на
 *       каждый UPDATE текущим UTC-моментом</li>
 *   <li>{@code is_printed} - nullable Boolean, корректно мапится в SQL NULL
 *       через {@link java.sql.Types#BOOLEAN}</li>
 * </ul>
 */
@Repository
public class ShamelaBookDao {

    public static final int BATCH_SIZE = 1000;

    private static final Logger log = LoggerFactory.getLogger(ShamelaBookDao.class);

    private static final String COLUMNS = "id, name, category_id, author_id, type, "
            + "publication_year, is_printed, major_release, minor_release, "
            + "bibliography, hint, pdf_links, extra_metadata, imported_at, deleted_at";

    private static final RowMapper<ShamelaBookRow> ROW_MAPPER = (rs, rn) -> new ShamelaBookRow(
            rs.getLong("id"),
            rs.getString("name"),
            getNullableLong(rs, "category_id"),
            getNullableLong(rs, "author_id"),
            getNullableInt(rs, "type"),
            getNullableInt(rs, "publication_year"),
            getNullableBoolean(rs, "is_printed"),
            rs.getInt("major_release"),
            rs.getInt("minor_release"),
            rs.getString("bibliography"),
            rs.getString("hint"),
            rs.getString("pdf_links"),
            rs.getString("extra_metadata"),
            rs.getObject("deleted_at", OffsetDateTime.class) != null
    );

    private final JdbcTemplate jdbcTemplate;

    public ShamelaBookDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int upsertAll(List<ShamelaBookRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO lib_shamela_book (
                    id, name, category_id, author_id, type,
                    publication_year, is_printed, major_release, minor_release,
                    bibliography, hint, pdf_links, extra_metadata, imported_at, deleted_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    name = EXCLUDED.name,
                    category_id = EXCLUDED.category_id,
                    author_id = EXCLUDED.author_id,
                    type = EXCLUDED.type,
                    publication_year = EXCLUDED.publication_year,
                    is_printed = EXCLUDED.is_printed,
                    major_release = EXCLUDED.major_release,
                    minor_release = EXCLUDED.minor_release,
                    bibliography = EXCLUDED.bibliography,
                    hint = EXCLUDED.hint,
                    pdf_links = EXCLUDED.pdf_links,
                    extra_metadata = EXCLUDED.extra_metadata,
                    imported_at = EXCLUDED.imported_at,
                    deleted_at = EXCLUDED.deleted_at
                """;
        int[][] result = jdbcTemplate.batchUpdate(sql, rows, BATCH_SIZE, (ps, row) -> {
            OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
            ps.setLong(1, row.id());
            ps.setString(2, row.name());
            setNullableLong(ps, 3, row.categoryId());
            setNullableLong(ps, 4, row.authorId());
            setNullableInt(ps, 5, row.type());
            setNullableInt(ps, 6, row.publicationYear());
            if (row.isPrinted() == null) {
                ps.setNull(7, Types.BOOLEAN);
            } else {
                ps.setBoolean(7, row.isPrinted());
            }
            ps.setInt(8, row.majorRelease());
            ps.setInt(9, row.minorRelease());
            if (row.bibliography() == null) {
                ps.setNull(10, Types.VARCHAR);
            } else {
                ps.setString(10, row.bibliography());
            }
            if (row.hint() == null) {
                ps.setNull(11, Types.VARCHAR);
            } else {
                ps.setString(11, row.hint());
            }
            setNullableJsonString(ps, 12, row.pdfLinksJson());
            setNullableJsonString(ps, 13, row.extraMetadataJson());
            ps.setObject(14, nowUtc);
            if (row.deleted()) {
                ps.setObject(15, nowUtc);
            } else {
                ps.setNull(15, Types.TIMESTAMP_WITH_TIMEZONE);
            }
        });
        int total = sumAffected(result);
        log.info("shamela {} upsert: rows={}", "book", total);
        return total;
    }

    public List<ShamelaBookRow> findAll() {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_shamela_book ORDER BY id",
                ROW_MAPPER
        );
    }

    public Optional<ShamelaBookRow> findById(long id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_shamela_book WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    /**
     * Количество строк в staging-таблице. Используется в admin
     * sync-status endpoint для отображения "сколько книг доступно
     * для импорта".
     */
    public int countAll() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lib_shamela_book WHERE deleted_at IS NULL",
                Integer.class
        );
        return count == null ? 0 : count;
    }

    /**
     * Поиск по name через ILIKE с обогащением: подтягивает имя автора
     * через LEFT JOIN на {@code lib_shamela_author} и проверяет уже ли
     * книга замаплена в {@code lib_books} через EXISTS subquery
     * (использует GIN-индекс на {@code lib_books.metadata} из
     * миграции 16).
     *
     * <p>Один SQL вместо N+1 на фронте: JOIN дешевле чем 20+ запросов
     * к {@code findById} на каждый search-результат для подгрузки
     * authors. Search возвращает не более {@code limit} строк
     * упорядоченных по релевантности (точные совпадения сначала, потом
     * substring).
     *
     * <p>Tombstoned записи ({@code deleted_at IS NOT NULL}) исключаются
     * из результатов - админ не должен импортировать удалённые в
     * shamela книги.
     *
     * @return ShamelaStagingBookView - read-only view для UI поиска
     */
    public List<ShamelaStagingBookView> searchByName(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String like = "%" + query.trim() + "%";
        String sql = """
                SELECT b.id, b.name, b.major_release, b.deleted_at,
                       a.name AS author_name,
                       EXISTS(
                           SELECT 1 FROM lib_books lb
                           WHERE lb.metadata->>'shamela_book_id' = b.id::text
                       ) AS is_mapped
                FROM lib_shamela_book b
                LEFT JOIN lib_shamela_author a ON a.id = b.author_id AND a.deleted_at IS NULL
                WHERE b.name ILIKE ? AND b.deleted_at IS NULL
                ORDER BY
                    CASE WHEN b.name ILIKE ? THEN 0 ELSE 1 END,
                    LENGTH(b.name),
                    b.id
                LIMIT ?
                """;
        return jdbcTemplate.query(
                sql,
                (rs, rn) -> new ShamelaStagingBookView(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("author_name"),
                        rs.getInt("major_release"),
                        rs.getBoolean("is_mapped")
                ),
                like,
                query.trim(),    // exact-match получает приоритет в ORDER BY
                limit
        );
    }

    /**
     * View-record для поисковых результатов админ-страницы. Не
     * соответствует физической структуре staging-таблицы (это JOIN
     * staging book + author + EXISTS на lib_books), поэтому не
     * лежит в etl/dto/.
     */
    public record ShamelaStagingBookView(
            long id,
            String name,
            String authorName,
            int majorRelease,
            boolean isMapped
    ) {
    }

    private static void setNullableJsonString(java.sql.PreparedStatement ps, int idx, String json) throws SQLException {
        if (json == null) {
            ps.setNull(idx, Types.VARCHAR);
        } else {
            ps.setString(idx, json);
        }
    }

    private static void setNullableLong(java.sql.PreparedStatement ps, int idx, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.BIGINT);
        } else {
            ps.setLong(idx, value);
        }
    }

    private static void setNullableInt(java.sql.PreparedStatement ps, int idx, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, value);
        }
    }

    private static Long getNullableLong(java.sql.ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer getNullableInt(java.sql.ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Boolean getNullableBoolean(java.sql.ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
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
