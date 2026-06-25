package ru.basnukaev.argumentmap.qa.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.domain.CitationDetail;
import ru.basnukaev.argumentmap.qa.domain.AnswerSource;
import ru.basnukaev.argumentmap.repository.CitationDetailRowMapper;

/**
 * JDBC repository для answer_sources (Этап 19.d, ADR-033 итерация 3).
 * Зеркалит {@code QuestionSourceRepository}: те же 9 LEFT JOIN для
 * structured citation (ADR-028), surrogate UUID PK (ADR-029).
 *
 * <p>Внимание - SQL alias для answer_sources не может быть "as" (это
 * reserved keyword Postgres). Используется {@code ansrc}.
 */
@Repository
public class AnswerSourceRepository {

    private static final String COLUMNS =
            "id, answer_id, source_id, quote, context, location, "
            + "page_id, range_start, range_end, "
            + "pdf_file_id, pdf_page_number, pdf_bbox, "
            + "pdf_file_index, "
            + "image_region_id, "
            + "created_at";

    private static final RowMapper<AnswerSource> ROW_MAPPER = (rs, rn) -> {
        int rangeStart = rs.getInt("range_start");
        Integer rangeStartOrNull = rs.wasNull() ? null : rangeStart;
        int rangeEnd = rs.getInt("range_end");
        Integer rangeEndOrNull = rs.wasNull() ? null : rangeEnd;
        int pdfPage = rs.getInt("pdf_page_number");
        Integer pdfPageOrNull = rs.wasNull() ? null : pdfPage;
        int pdfFileIndex = rs.getInt("pdf_file_index");
        Integer pdfFileIndexOrNull = rs.wasNull() ? null : pdfFileIndex;

        return new AnswerSource(
                rs.getObject("id", UUID.class),
                rs.getObject("answer_id", UUID.class),
                rs.getObject("source_id", UUID.class),
                rs.getString("quote"),
                rs.getString("context"),
                rs.getString("location"),
                rs.getObject("page_id", UUID.class),
                rangeStartOrNull,
                rangeEndOrNull,
                rs.getObject("pdf_file_id", UUID.class),
                pdfPageOrNull,
                rs.getString("pdf_bbox"),
                pdfFileIndexOrNull,
                rs.getObject("image_region_id", UUID.class),
                instant(rs, "created_at")
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public AnswerSourceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AnswerSource save(AnswerSource link) {
        jdbcTemplate.update(
                "INSERT INTO answer_sources (" + COLUMNS + ") "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)",
                link.id(),
                link.answerId(),
                link.sourceId(),
                link.quote(),
                link.context(),
                link.location(),
                link.pageId(),
                link.rangeStart(),
                link.rangeEnd(),
                link.pdfFileId(),
                link.pdfPageNumber(),
                link.pdfBbox(),
                link.pdfFileIndex(),
                link.imageRegionId(),
                odt(link.createdAt())
        );
        return link;
    }

    public Optional<AnswerSource> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM answer_sources WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    public List<AnswerSource> findByAnswerId(UUID answerId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM answer_sources WHERE answer_id = ? ORDER BY created_at",
                ROW_MAPPER,
                answerId
        );
    }

    public boolean deleteById(UUID id) {
        return jdbcTemplate.update("DELETE FROM answer_sources WHERE id = ?", id) > 0;
    }

    // Answer-scoped delete: удаляет citation только если она принадлежит
    // указанному ответу. Защита от IDOR - DELETE
    // /answers/{answerId}/sources/{id} не должен удалять citation другого
    // ответа по голому surrogate id (зеркало
    // NodeSourceRepository.deleteByIdAndNode).
    public boolean deleteByIdAndAnswer(UUID id, UUID answerId) {
        return jdbcTemplate.update(
                "DELETE FROM answer_sources WHERE id = ? AND answer_id = ?",
                id, answerId) > 0;
    }

    public record AnswerSourceWithLocation(AnswerSource as, CitationDetail citation) {
    }

    private static final String JOIN_LOCATION_SQL = """
            SELECT %COLS%,
              s.book_id AS src_book_id,
              a.id AS authority_id,
              a.name AS authority_name,
              a.full_name AS author_full_name,
              a.death_year_hijri AS author_death_year_hijri,
              b.title AS book_title,
              b.language AS book_language,
              b.edition_number,
              b.published_year_hijri,
              b.published_year_gregorian,
              mh.id AS muhaqqiq_id,
              mh.name AS muhaqqiq_name,
              mh.full_name AS muhaqqiq_full_name,
              pub.id AS publisher_id,
              pub.name AS publisher_name,
              pl.id AS publication_place_id,
              pl.name AS publication_place_name,
              p.part AS page_part,
              p.printed_page AS page_printed_page,
              p.page_number AS page_page_number,
              p2.printed_page AS region_printed_page,
              p2.page_number AS region_page_number
            FROM answer_sources ansrc
            LEFT JOIN sources s                   ON s.id = ansrc.source_id
            LEFT JOIN lib_books b                 ON b.id = s.book_id
            LEFT JOIN authorities a               ON a.id = b.authority_id
            LEFT JOIN lib_muhaqqiqs mh            ON mh.id = b.muhaqqiq_id
            LEFT JOIN lib_publishers pub          ON pub.id = b.publisher_id
            LEFT JOIN lib_publication_places pl   ON pl.id = b.publication_place_id
            LEFT JOIN lib_pages p                 ON p.id = ansrc.page_id
            LEFT JOIN lib_image_regions ir        ON ir.id = ansrc.image_region_id
            LEFT JOIN lib_pages p2                ON p2.id = ir.page_id
            """.replace("%COLS%", prefixedColumns());

    private static String prefixedColumns() {
        StringBuilder sb = new StringBuilder();
        for (String c : COLUMNS.split(", ")) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("ansrc.").append(c);
        }
        return sb.toString();
    }

    public List<AnswerSourceWithLocation> findByAnswerIdWithLocation(UUID answerId) {
        return jdbcTemplate.query(
                JOIN_LOCATION_SQL + " WHERE ansrc.answer_id = ? ORDER BY ansrc.created_at",
                (rs, rn) -> new AnswerSourceWithLocation(
                        ROW_MAPPER.mapRow(rs, rn),
                        CitationDetailRowMapper.fromRow(rs)
                ),
                answerId
        );
    }

    public Optional<AnswerSourceWithLocation> findByIdWithLocation(UUID id) {
        return jdbcTemplate.query(
                JOIN_LOCATION_SQL + " WHERE ansrc.id = ?",
                (rs, rn) -> new AnswerSourceWithLocation(
                        ROW_MAPPER.mapRow(rs, rn),
                        CitationDetailRowMapper.fromRow(rs)
                ),
                id
        ).stream().findFirst();
    }
}
