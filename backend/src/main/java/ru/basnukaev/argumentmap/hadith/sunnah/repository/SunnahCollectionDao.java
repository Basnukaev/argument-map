package ru.basnukaev.argumentmap.hadith.sunnah.repository;

import static ru.basnukaev.argumentmap.hadith.sunnah.repository.SunnahDaoSupport.sumAffected;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahCollectionRow;

/**
 * DAO staging-таблицы {@code sn_staging_collection}. Phase 5 ETL шаг 2.
 *
 * <p>Upsert через {@code batchUpdate(sql, List&lt;Object[]&gt;)}: null-элементы
 * массива становятся SQL NULL, jsonb-колонка {@code raw} получает значение
 * через {@code ?::jsonb}. {@code imported_at} — DEFAULT now() на INSERT,
 * {@code now()} в {@code ON CONFLICT}. См. {@link SunnahDaoSupport}.
 */
@Repository
public class SunnahCollectionDao {

    private static final String COLUMNS =
            "name, has_books, has_chapters, total_hadith, total_available_hadith, "
                    + "title_ar, title_en, short_intro_ar, short_intro_en, raw";

    private static final RowMapper<SunnahCollectionRow> ROW_MAPPER = (rs, rn) -> new SunnahCollectionRow(
            rs.getString("name"),
            rs.getObject("has_books", Boolean.class),
            rs.getObject("has_chapters", Boolean.class),
            rs.getObject("total_hadith", Integer.class),
            rs.getObject("total_available_hadith", Integer.class),
            rs.getString("title_ar"),
            rs.getString("title_en"),
            rs.getString("short_intro_ar"),
            rs.getString("short_intro_en"),
            rs.getString("raw")
    );

    private final JdbcTemplate jdbcTemplate;

    public SunnahCollectionDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int upsertAll(List<SunnahCollectionRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO sn_staging_collection (
                    name, has_books, has_chapters, total_hadith, total_available_hadith,
                    title_ar, title_en, short_intro_ar, short_intro_en, raw
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (name) DO UPDATE SET
                    has_books = EXCLUDED.has_books,
                    has_chapters = EXCLUDED.has_chapters,
                    total_hadith = EXCLUDED.total_hadith,
                    total_available_hadith = EXCLUDED.total_available_hadith,
                    title_ar = EXCLUDED.title_ar,
                    title_en = EXCLUDED.title_en,
                    short_intro_ar = EXCLUDED.short_intro_ar,
                    short_intro_en = EXCLUDED.short_intro_en,
                    raw = EXCLUDED.raw,
                    imported_at = now()
                """;
        List<Object[]> args = rows.stream().map(r -> new Object[]{
                r.name(), r.hasBooks(), r.hasChapters(), r.totalHadith(), r.totalAvailableHadith(),
                r.titleAr(), r.titleEn(), r.shortIntroAr(), r.shortIntroEn(), r.rawJson()
        }).toList();
        return sumAffected(jdbcTemplate.batchUpdate(sql, args));
    }

    public List<SunnahCollectionRow> findAll() {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM sn_staging_collection ORDER BY name",
                ROW_MAPPER
        );
    }

    public Optional<SunnahCollectionRow> findByName(String name) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM sn_staging_collection WHERE name = ?",
                ROW_MAPPER, name
        ).stream().findFirst();
    }

    public int countAll() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sn_staging_collection", Integer.class);
        return count == null ? 0 : count;
    }
}
