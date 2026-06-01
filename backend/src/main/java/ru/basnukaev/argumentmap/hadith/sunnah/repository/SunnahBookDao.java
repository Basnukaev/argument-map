package ru.basnukaev.argumentmap.hadith.sunnah.repository;

import static ru.basnukaev.argumentmap.hadith.sunnah.repository.SunnahDaoSupport.sumAffected;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahBookRow;

/**
 * DAO staging-таблицы {@code sn_staging_book}. Phase 5 ETL шаг 2.
 * Upsert-идиома идентична {@link SunnahCollectionDao}.
 */
@Repository
public class SunnahBookDao {

    private static final String COLUMNS =
            "collection_name, book_number, hadith_start_number, hadith_end_number, "
                    + "number_of_hadith, name_ar, name_en, raw";

    private static final RowMapper<SunnahBookRow> ROW_MAPPER = (rs, rn) -> new SunnahBookRow(
            rs.getString("collection_name"),
            rs.getString("book_number"),
            rs.getObject("hadith_start_number", Integer.class),
            rs.getObject("hadith_end_number", Integer.class),
            rs.getObject("number_of_hadith", Integer.class),
            rs.getString("name_ar"),
            rs.getString("name_en"),
            rs.getString("raw")
    );

    private final JdbcTemplate jdbcTemplate;

    public SunnahBookDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int upsertAll(List<SunnahBookRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO sn_staging_book (
                    collection_name, book_number, hadith_start_number, hadith_end_number,
                    number_of_hadith, name_ar, name_en, raw
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (collection_name, book_number) DO UPDATE SET
                    hadith_start_number = EXCLUDED.hadith_start_number,
                    hadith_end_number = EXCLUDED.hadith_end_number,
                    number_of_hadith = EXCLUDED.number_of_hadith,
                    name_ar = EXCLUDED.name_ar,
                    name_en = EXCLUDED.name_en,
                    raw = EXCLUDED.raw,
                    imported_at = now()
                """;
        List<Object[]> args = rows.stream().map(r -> new Object[]{
                r.collectionName(), r.bookNumber(), r.hadithStartNumber(), r.hadithEndNumber(),
                r.numberOfHadith(), r.nameAr(), r.nameEn(), r.rawJson()
        }).toList();
        return sumAffected(jdbcTemplate.batchUpdate(sql, args));
    }

    public List<SunnahBookRow> findByCollection(String collectionName) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM sn_staging_book WHERE collection_name = ? "
                        + "ORDER BY book_number",
                ROW_MAPPER, collectionName
        );
    }

    public int countByCollection(String collectionName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sn_staging_book WHERE collection_name = ?",
                Integer.class, collectionName);
        return count == null ? 0 : count;
    }
}
