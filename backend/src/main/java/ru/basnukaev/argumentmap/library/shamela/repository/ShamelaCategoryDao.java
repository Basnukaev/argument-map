package ru.basnukaev.argumentmap.library.shamela.repository;

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

import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaCategoryRow;

/**
 * DAO для staging-таблицы {@code lib_shamela_category}. Bulk upsert батчами
 * по {@link #BATCH_SIZE} строк через {@code ON CONFLICT (id) DO UPDATE}.
 *
 * <p>Поле {@code deleted} из shamela транслируется в {@code deleted_at}:
 * {@code true} -&gt; текущий UTC-момент, {@code false} -&gt; {@code NULL}
 * (в т.ч. "восстановление" удалённой записи на следующем sync).
 */
@Repository
public class ShamelaCategoryDao {

    public static final int BATCH_SIZE = 1000;

    private static final Logger log = LoggerFactory.getLogger(ShamelaCategoryDao.class);

    private static final String COLUMNS = "id, name, display_order, deleted_at";

    private static final RowMapper<ShamelaCategoryRow> ROW_MAPPER = (rs, rn) -> {
        int order = rs.getInt("display_order");
        Integer displayOrder = rs.wasNull() ? null : order;
        return new ShamelaCategoryRow(
                rs.getLong("id"),
                rs.getString("name"),
                displayOrder,
                rs.getObject("deleted_at", OffsetDateTime.class) != null
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public ShamelaCategoryDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int upsertAll(List<ShamelaCategoryRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO lib_shamela_category (id, name, display_order, deleted_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    name = EXCLUDED.name,
                    display_order = EXCLUDED.display_order,
                    deleted_at = EXCLUDED.deleted_at
                """;
        int[][] result = jdbcTemplate.batchUpdate(sql, rows, BATCH_SIZE, (ps, row) -> {
            ps.setLong(1, row.id());
            ps.setString(2, row.name());
            if (row.displayOrder() == null) {
                ps.setNull(3, Types.INTEGER);
            } else {
                ps.setInt(3, row.displayOrder());
            }
            if (row.deleted()) {
                ps.setObject(4, OffsetDateTime.now(ZoneOffset.UTC));
            } else {
                ps.setNull(4, Types.TIMESTAMP_WITH_TIMEZONE);
            }
        });
        int total = sumAffected(result);
        log.info("shamela {} upsert: rows={}", "category", total);
        return total;
    }

    public List<ShamelaCategoryRow> findAll() {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_shamela_category ORDER BY id",
                ROW_MAPPER
        );
    }

    public Optional<ShamelaCategoryRow> findById(long id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_shamela_category WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    public int countAll() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lib_shamela_category WHERE deleted_at IS NULL",
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private static int sumAffected(int[][] batches) {
        int total = 0;
        for (int[] batch : batches) {
            for (int n : batch) {
                // postgres возвращает -2 (Statement.SUCCESS_NO_INFO) при batch upsert
                // через драйвер, поэтому считаем такие как 1 успешную операцию
                total += (n >= 0) ? n : 1;
            }
        }
        return total;
    }
}
