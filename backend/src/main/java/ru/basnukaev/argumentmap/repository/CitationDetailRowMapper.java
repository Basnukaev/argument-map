package ru.basnukaev.argumentmap.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import ru.basnukaev.argumentmap.domain.CitationDetail;

/**
 * Shared маппер строки JOIN-результата в {@link CitationDetail}.
 *
 * <p>До этого 3 идентичных приватных метода жили в
 * {@code NodeSourceRepository}, {@code AnswerSourceRepository},
 * {@code QuestionSourceRepository} - все с одинаковой SQL JOIN-схемой
 * (authority + book + muhaqqiq + publisher + page + pdf + image_region).
 * Дедупликация: 3x58 LOC → 1x58 LOC.
 *
 * <p>Контракт: {@code ResultSet} должен содержать колонки JOIN'a из
 * {@code JOIN_LOCATION_SQL} (см. NodeSourceRepository / *SourceRepository).
 * Алиасы колонок фиксированы (authority_id, book_title, page_id, и т.д.) -
 * изменение SQL должно одновременно отражаться здесь.
 */
public final class CitationDetailRowMapper {

    private CitationDetailRowMapper() {
        // util class
    }

    /**
     * Преобразовать текущую строку {@code ResultSet} в {@link CitationDetail}.
     * Все integer поля nullable (через {@code rs.wasNull()} pattern).
     */
    public static CitationDetail fromRow(ResultSet rs) throws SQLException {
        int edition = rs.getInt("edition_number");
        Integer editionOrNull = rs.wasNull() ? null : edition;
        int yearH = rs.getInt("published_year_hijri");
        Integer yearHOrNull = rs.wasNull() ? null : yearH;
        int yearG = rs.getInt("published_year_gregorian");
        Integer yearGOrNull = rs.wasNull() ? null : yearG;
        int deathY = rs.getInt("author_death_year_hijri");
        Integer deathYOrNull = rs.wasNull() ? null : deathY;
        int pageNum = rs.getInt("page_page_number");
        Integer pageNumOrNull = rs.wasNull() ? null : pageNum;
        int rangeStart = rs.getInt("range_start");
        Integer rangeStartOrNull = rs.wasNull() ? null : rangeStart;
        int rangeEnd = rs.getInt("range_end");
        Integer rangeEndOrNull = rs.wasNull() ? null : rangeEnd;
        int pdfPage = rs.getInt("pdf_page_number");
        Integer pdfPageOrNull = rs.wasNull() ? null : pdfPage;
        int regPage = rs.getInt("region_page_number");
        Integer regPageOrNull = rs.wasNull() ? null : regPage;

        return new CitationDetail(
                rs.getObject("authority_id", UUID.class),
                rs.getString("authority_name"),
                rs.getString("author_full_name"),
                deathYOrNull,

                rs.getObject("src_book_id", UUID.class),
                rs.getString("book_title"),
                rs.getString("book_language"),

                rs.getObject("muhaqqiq_id", UUID.class),
                rs.getString("muhaqqiq_name"),
                rs.getString("muhaqqiq_full_name"),

                rs.getObject("publisher_id", UUID.class),
                rs.getString("publisher_name"),
                rs.getObject("publication_place_id", UUID.class),
                rs.getString("publication_place_name"),
                editionOrNull,
                yearHOrNull,
                yearGOrNull,

                rs.getObject("page_id", UUID.class),
                rs.getString("page_part"),
                rs.getString("page_printed_page"),
                pageNumOrNull,
                rangeStartOrNull,
                rangeEndOrNull,

                rs.getObject("pdf_file_id", UUID.class),
                pdfPageOrNull,
                rs.getString("pdf_bbox"),

                rs.getObject("image_region_id", UUID.class),
                rs.getString("region_printed_page"),
                regPageOrNull
        );
    }
}
