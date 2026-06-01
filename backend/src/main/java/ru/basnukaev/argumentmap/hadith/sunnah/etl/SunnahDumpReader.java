package ru.basnukaev.argumentmap.hadith.sunnah.etl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahBookRow;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahChapterRow;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahCollectionRow;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahHadithRow;

/**
 * Реализация {@link SunnahDataSource} из MySQL-дампа sunnah.com
 * ({@code github.com/sunnah-com/api}). Phase 5 ETL шаг 2.d (ADR-052).
 *
 * <p>Читает РЕАЛЬНУЮ (денормализованную) схему дампа, отличную от логической
 * модели spec.v1.yml:
 * <ul>
 *   <li>{@code Collections} → {@link SunnahCollectionRow} (флаги hasbooks/
 *       haschapters как 'yes'/'no');</li>
 *   <li>{@code BookData} → {@link SunnahBookRow} (book_number =
 *       {@code arabicBookNumber});</li>
 *   <li>{@code ChapterData} JOIN {@code BookData} (chapter хранит bookID, не
 *       bookNumber → резолвим через JOIN) → {@link SunnahChapterRow}; babID
 *       дробный → канонизируется ({@code stripTrailingZeros});</li>
 *   <li>{@code HadithTable} (консолидированная: arabic+english текст+grade в
 *       одной строке) → {@link SunnahHadithRow}; grade-строки → jsonb-массив
 *       {@code [{graded_by,grade}]}.</li>
 * </ul>
 *
 * <p>Подключается к дампу через переданный {@link DataSource} (MySQL),
 * отдельный от основного Postgres. Конструируется оркестратором импорта из
 * сконфигурированного MySQL-DataSource (симметрично shamela SQLite reader).
 *
 * <p><b>Допущения (приемлемы для пилота Бухари+Муслим, follow-up при
 * расширении объёма):</b>
 * <ul>
 *   <li>{@code HadithTable.bookNumber == BookData.arabicBookNumber} — если
 *       нумерации расходятся, обогащение имени книги/главы промахнётся (хадис
 *       НЕ теряется — попадает в hd_* без bookName/chapterTitle);</li>
 *   <li>{@code readChapters} использует INNER JOIN по {@code arabicBookID};
 *       в реальном дампе {@code BookData.arabicBookID} nullable — главы книги
 *       с {@code arabicBookID IS NULL} будут отброшены (главы потеряются, но
 *       не хадисы). Для Бухари+Муслим arabicBookID заполнен.</li>
 * </ul>
 */
public class SunnahDumpReader implements SunnahDataSource {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public SunnahDumpReader(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
    }

    private final RowMapper<SunnahCollectionRow> collectionMapper = (rs, rn) -> new SunnahCollectionRow(
            rs.getString("name"),
            yesNo(rs.getString("hasbooks")),
            yesNo(rs.getString("haschapters")),
            rs.getObject("totalhadith", Integer.class),
            rs.getObject("numhadith", Integer.class),
            rs.getString("arabicTitle"),
            rs.getString("englishTitle"),
            null,
            rs.getString("shortintro"),
            null
    );

    private final RowMapper<SunnahBookRow> bookMapper = (rs, rn) -> new SunnahBookRow(
            rs.getString("collection"),
            String.valueOf(rs.getInt("arabicBookNumber")),
            rs.getObject("firstNumber", Integer.class),
            rs.getObject("lastNumber", Integer.class),
            rs.getObject("totalNumber", Integer.class),
            rs.getString("arabicBookName"),
            rs.getString("englishBookName"),
            null
    );

    private final RowMapper<SunnahChapterRow> chapterMapper = (rs, rn) -> new SunnahChapterRow(
            rs.getString("collection"),
            String.valueOf(rs.getInt("arabicBookNumber")),
            canonicalDecimal(rs.getBigDecimal("babID")),
            rs.getString("arabicBabNumber"),
            rs.getString("englishBabNumber"),
            rs.getString("arabicBabName"),
            rs.getString("englishBabName"),
            rs.getString("arabicIntro"),
            rs.getString("englishIntro"),
            rs.getString("arabicEnding"),
            rs.getString("englishEnding"),
            null
    );

    private final RowMapper<SunnahHadithRow> hadithMapper = (rs, rn) -> new SunnahHadithRow(
            rs.getString("collection"),
            rs.getString("hadithNumber"),
            rs.getString("bookNumber"),
            canonicalDecimal(rs.getBigDecimal("babID")),
            rs.getObject("arabicURN", Long.class),
            rs.getObject("englishURN", Long.class),
            rs.getString("arabicText"),
            rs.getString("englishText"),
            buildGrades(rs.getString("englishgrade1"), rs.getString("arabicgrade1")),
            null
    );

    @Override
    public List<SunnahCollectionRow> readCollections() {
        return jdbc.query(
                "SELECT name, hasbooks, haschapters, totalhadith, numhadith, "
                        + "arabicTitle, englishTitle, shortintro FROM Collections",
                collectionMapper);
    }

    @Override
    public List<SunnahBookRow> readBooks(String collectionName) {
        return jdbc.query(
                "SELECT collection, arabicBookNumber, arabicBookName, englishBookName, "
                        + "firstNumber, lastNumber, totalNumber FROM BookData WHERE collection = ?",
                bookMapper, collectionName);
    }

    @Override
    public List<SunnahChapterRow> readChapters(String collectionName) {
        // ChapterData хранит bookID (decimal), не bookNumber — JOIN с BookData
        // даёт arabicBookNumber, согласованный с HadithTable.bookNumber
        return jdbc.query(
                "SELECT c.collection, b.arabicBookNumber, c.babID, "
                        + "c.arabicBabNumber, c.englishBabNumber, c.arabicBabName, c.englishBabName, "
                        + "c.arabicIntro, c.englishIntro, c.arabicEnding, c.englishEnding "
                        + "FROM ChapterData c "
                        + "JOIN BookData b ON b.collection = c.collection AND b.arabicBookID = c.arabicBookID "
                        + "WHERE c.collection = ?",
                chapterMapper, collectionName);
    }

    @Override
    public List<SunnahHadithRow> readHadiths(String collectionName) {
        return jdbc.query(
                "SELECT collection, bookNumber, babID, hadithNumber, arabicURN, englishURN, "
                        + "arabicText, englishText, arabicgrade1, englishgrade1 "
                        + "FROM HadithTable WHERE collection = ?",
                hadithMapper, collectionName);
    }

    /** 'yes'/'no' (sunnah-флаги) → Boolean; иначе null. */
    private static Boolean yesNo(String raw) {
        if (raw == null) {
            return null;
        }
        if ("yes".equalsIgnoreCase(raw.trim())) {
            return true;
        }
        if ("no".equalsIgnoreCase(raw.trim())) {
            return false;
        }
        return null;
    }

    /** babID (decimal с хвостовыми нулями: 1.0, 22.10) → канон "1", "22.1". */
    private static String canonicalDecimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    /**
     * Grade-строки дампа (свободный текст, напр. "Sahih") → jsonb-массив
     * {@code [{graded_by,grade}]} для sn_staging_hadith.grades. Берём
     * английский grade (приоритет), иначе арабский; пустой → null (нет ключа).
     * graded_by пустой — дамп не структурирует учёного-оценщика.
     */
    private String buildGrades(String englishGrade, String arabicGrade) {
        String grade = firstNonBlank(englishGrade, arabicGrade);
        if (grade == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(
                    List.of(Map.of("graded_by", "", "grade", grade)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать grades", e);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
