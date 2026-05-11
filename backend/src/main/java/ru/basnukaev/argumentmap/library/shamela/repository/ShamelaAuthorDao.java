package ru.basnukaev.argumentmap.library.shamela.repository;

import static ru.basnukaev.argumentmap.library.shamela.repository.ShamelaDaoSupport.BATCH_SIZE;
import static ru.basnukaev.argumentmap.library.shamela.repository.ShamelaDaoSupport.setNullableInt;
import static ru.basnukaev.argumentmap.library.shamela.repository.ShamelaDaoSupport.setNullableString;
import static ru.basnukaev.argumentmap.library.shamela.repository.ShamelaDaoSupport.sumAffected;

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

import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaAuthorRow;

/**
 * DAO для staging-таблицы {@code lib_shamela_author}. Bulk upsert
 * батчами {@link ShamelaDaoSupport#BATCH_SIZE}, tombstone через
 * {@code deleted_at} как в {@link ShamelaCategoryDao}.
 */
@Repository
public class ShamelaAuthorDao {

    private static final Logger log = LoggerFactory.getLogger(ShamelaAuthorDao.class);

    private static final String COLUMNS = "id, name, biography, death_year, deleted_at";

    private static final RowMapper<ShamelaAuthorRow> ROW_MAPPER = (rs, rn) -> {
        int year = rs.getInt("death_year");
        Integer deathYear = rs.wasNull() ? null : year;
        return new ShamelaAuthorRow(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("biography"),
                deathYear,
                rs.getObject("deleted_at", OffsetDateTime.class) != null
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public ShamelaAuthorDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int upsertAll(List<ShamelaAuthorRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO lib_shamela_author (id, name, biography, death_year, deleted_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    name = EXCLUDED.name,
                    biography = EXCLUDED.biography,
                    death_year = EXCLUDED.death_year,
                    deleted_at = EXCLUDED.deleted_at
                """;
        int[][] result = jdbcTemplate.batchUpdate(sql, rows, BATCH_SIZE, (ps, row) -> {
            ps.setLong(1, row.id());
            ps.setString(2, row.name());
            setNullableString(ps, 3, row.biography());
            setNullableInt(ps, 4, row.deathYear());
            if (row.deleted()) {
                ps.setObject(5, OffsetDateTime.now(ZoneOffset.UTC));
            } else {
                ps.setNull(5, Types.TIMESTAMP_WITH_TIMEZONE);
            }
        });
        int total = sumAffected(result);
        log.info("shamela {} upsert: rows={}", "author", total);
        return total;
    }

    public List<ShamelaAuthorRow> findAll() {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_shamela_author ORDER BY id",
                ROW_MAPPER
        );
    }

    public Optional<ShamelaAuthorRow> findById(long id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_shamela_author WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    public int countAll() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lib_shamela_author WHERE deleted_at IS NULL",
                Integer.class
        );
        return count == null ? 0 : count;
    }
}
