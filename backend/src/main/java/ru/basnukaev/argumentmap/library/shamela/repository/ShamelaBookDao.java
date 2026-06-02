package ru.basnukaev.argumentmap.library.shamela.repository;

import static ru.basnukaev.argumentmap.library.shamela.repository.ShamelaDaoSupport.BATCH_SIZE;
import static ru.basnukaev.argumentmap.library.shamela.repository.ShamelaDaoSupport.getNullableBoolean;
import static ru.basnukaev.argumentmap.library.shamela.repository.ShamelaDaoSupport.getNullableInt;
import static ru.basnukaev.argumentmap.library.shamela.repository.ShamelaDaoSupport.getNullableLong;
import static ru.basnukaev.argumentmap.library.shamela.repository.ShamelaDaoSupport.setNullableBoolean;
import static ru.basnukaev.argumentmap.library.shamela.repository.ShamelaDaoSupport.setNullableInt;
import static ru.basnukaev.argumentmap.library.shamela.repository.ShamelaDaoSupport.setNullableJsonString;
import static ru.basnukaev.argumentmap.library.shamela.repository.ShamelaDaoSupport.setNullableLong;
import static ru.basnukaev.argumentmap.library.shamela.repository.ShamelaDaoSupport.setNullableString;
import static ru.basnukaev.argumentmap.library.shamela.repository.ShamelaDaoSupport.sumAffected;

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
 *       параметром как строка с явным {@code ::jsonb}-cast в SQL</li>
 *   <li>{@code imported_at} проставляется на INSERT и обновляется на
 *       каждый UPDATE текущим UTC-моментом</li>
 *   <li>{@code is_printed} - nullable Boolean, корректно мапится в SQL NULL</li>
 * </ul>
 */
@Repository
public class ShamelaBookDao {

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
            setNullableBoolean(ps, 7, row.isPrinted());
            ps.setInt(8, row.majorRelease());
            ps.setInt(9, row.minorRelease());
            setNullableString(ps, 10, row.bibliography());
            setNullableString(ps, 11, row.hint());
            setNullableJsonString(ps, 12, row.pdfLinksJson());
            setNullableJsonString(ps, 13, row.extraMetadataJson());
            ps.setObject(14, nowUtc);
            ps.setObject(15, row.deleted() ? nowUtc : null);
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
     * Поиск книг в staging-каталоге shamela. Один SQL с обогащением:
     * <ul>
     *   <li>LEFT JOIN на {@code lib_shamela_author} для имени автора</li>
     *   <li>EXISTS subquery в {@code lib_books} через GIN-индекс на
     *       {@code metadata->>'shamela_book_id'}</li>
     * </ul>
     *
     * <p>Поддерживает три режима матчинга в одном WHERE:
     * <ol>
     *   <li>Точное совпадение по {@code id::text} - если query это число
     *       вроде "1681"</li>
     *   <li>ILIKE substring по name (без преобразований case)</li>
     *   <li>Точное совпадение по name (приоритет в ORDER BY)</li>
     * </ol>
     *
     * <p>Сортировка: точное id-совпадение → точное name-совпадение →
     * ILIKE substring → по {@code LENGTH(name)} → по id.
     *
     * <p>Tombstoned записи ({@code deleted_at IS NOT NULL}) исключаются.
     *
     * @return ShamelaStagingBookView - read-only view для UI поиска
     */
    public List<ShamelaStagingBookView> searchByName(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String trimmed = query.trim();
        String like = "%" + trimmed + "%";
        String sql = """
                SELECT b.id, b.name, b.major_release, b.deleted_at,
                       a.name AS author_name,
                       EXISTS(
                           SELECT 1 FROM lib_books lb
                           WHERE lb.metadata->>'shamela_book_id' = b.id::text
                       ) AS is_mapped
                FROM lib_shamela_book b
                LEFT JOIN lib_shamela_author a ON a.id = b.author_id AND a.deleted_at IS NULL
                WHERE (b.name ILIKE ? OR b.id::text = ?) AND b.deleted_at IS NULL
                ORDER BY
                    CASE
                        WHEN b.id::text = ? THEN 0
                        WHEN b.name ILIKE ? THEN 1
                        ELSE 2
                    END,
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
                trimmed,
                trimmed,
                trimmed,
                limit
        );
    }

    /**
     * Общий SELECT с обогащением (author JOIN + isMapped EXISTS) для
     * paged-листинга и поиска. {@code <WHERE>} подставляется вызывающим -
     * один источник истины для строки выборки между findPage/searchByName.
     */
    private static final String ENRICHED_SELECT = """
            SELECT b.id, b.name, b.major_release, b.deleted_at,
                   a.name AS author_name,
                   EXISTS(
                       SELECT 1 FROM lib_books lb
                       WHERE lb.metadata->>'shamela_book_id' = b.id::text
                   ) AS is_mapped
            FROM lib_shamela_book b
            LEFT JOIN lib_shamela_author a ON a.id = b.author_id AND a.deleted_at IS NULL
            """;

    private static final RowMapper<ShamelaStagingBookView> VIEW_MAPPER = (rs, rn) -> new ShamelaStagingBookView(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("author_name"),
            rs.getInt("major_release"),
            rs.getBoolean("is_mapped")
    );

    /**
     * WHERE-clause для paged-листинга - один источник истины, чтобы
     * count не разъезжался с select. Если {@code q} null/blank - только
     * фильтр tombstoned (все живые книги). Иначе - name ILIKE substring
     * ИЛИ точное id-совпадение (если q это число).
     *
     * <p>Аргументы добавляются в {@code params} в порядке появления
     * плейсхолдеров в WHERE.
     */
    private static String appendListFilters(StringBuilder sql, List<Object> params, String q) {
        sql.append(" WHERE b.deleted_at IS NULL");
        if (q != null && !q.isBlank()) {
            String trimmed = q.trim();
            sql.append(" AND (b.name ILIKE ? OR b.id::text = ?)");
            params.add("%" + trimmed + "%");
            params.add(trimmed);
        }
        return q == null ? null : q.trim();
    }

    /**
     * Пагинированный листинг staging-каталога shamela для admin-страницы.
     * При {@code q} null/blank возвращает ВСЕ живые книги (paged) - чтобы
     * каталог был виден по умолчанию, не blank-until-search. При наличии
     * {@code q} - тот же name/id-матчинг что у {@link #searchByName} но
     * paged.
     *
     * <p>Сортировка детерминированная для стабильной пагинации: точное
     * id-совпадение → точное name-совпадение → по {@code LENGTH(name)} →
     * по {@code b.id}. id - tiebreaker (уникален), поэтому порядок
     * стабилен между страницами.
     *
     * <p>Tombstoned записи ({@code deleted_at IS NOT NULL}) исключаются -
     * тот же фильтр что в {@link #countFiltered}.
     */
    public List<ShamelaStagingBookView> findPage(String q, int limit, int offset) {
        StringBuilder sql = new StringBuilder(ENRICHED_SELECT);
        List<Object> params = new java.util.ArrayList<>();
        String trimmed = appendListFilters(sql, params, q);
        if (trimmed != null && !trimmed.isEmpty()) {
            // приоритет релевантности при поиске; повторяем trimmed для CASE
            sql.append("""
                    ORDER BY
                        CASE
                            WHEN b.id::text = ? THEN 0
                            WHEN b.name ILIKE ? THEN 1
                            ELSE 2
                        END,
                        LENGTH(b.name),
                        b.id
                    """);
            params.add(trimmed);
            params.add("%" + trimmed + "%");
        } else {
            // листинг без поиска - стабильный детерминированный порядок по id
            sql.append(" ORDER BY b.id");
        }
        sql.append(" LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql.toString(), VIEW_MAPPER, params.toArray());
    }

    /**
     * Количество строк подходящих под фильтр {@code q} - тот же
     * WHERE-источник что у {@link #findPage}, чтобы total совпадал с
     * количеством реально отдаваемых строк.
     */
    public int countFiltered(String q) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM lib_shamela_book b");
        List<Object> params = new java.util.ArrayList<>();
        appendListFilters(sql, params, q);
        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count == null ? 0 : count;
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
}
