package ru.basnukaev.argumentmap.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.domain.CitationDetail;
import ru.basnukaev.argumentmap.domain.NodeSource;

@Repository
public class NodeSourceRepository {

    private static final String COLUMNS =
            "id, node_id, source_id, quote, context, location, "
            + "page_id, range_start, range_end, "
            + "pdf_file_id, pdf_page_number, pdf_bbox, "
            + "image_region_id, "
            + "created_at";

    private static final RowMapper<NodeSource> ROW_MAPPER = (rs, rn) -> {
        int rangeStart = rs.getInt("range_start");
        Integer rangeStartOrNull = rs.wasNull() ? null : rangeStart;
        int rangeEnd = rs.getInt("range_end");
        Integer rangeEndOrNull = rs.wasNull() ? null : rangeEnd;
        int pdfPage = rs.getInt("pdf_page_number");
        Integer pdfPageOrNull = rs.wasNull() ? null : pdfPage;

        return new NodeSource(
                rs.getObject("id", UUID.class),
                rs.getObject("node_id", UUID.class),
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
                rs.getObject("image_region_id", UUID.class),
                instant(rs, "created_at")
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public NodeSourceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public NodeSource save(NodeSource link) {
        jdbcTemplate.update(
                "INSERT INTO node_sources (" + COLUMNS + ") "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)",
                link.id(),
                link.nodeId(),
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
                link.imageRegionId(),
                odt(link.createdAt())
        );
        return link;
    }

    public Optional<NodeSource> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM node_sources WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    public List<NodeSource> findByNodeId(UUID nodeId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM node_sources WHERE node_id = ? ORDER BY created_at",
                ROW_MAPPER,
                nodeId
        );
    }

    public List<NodeSource> findBySourceId(UUID sourceId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM node_sources WHERE source_id = ? ORDER BY created_at",
                ROW_MAPPER,
                sourceId
        );
    }

    /** Удалить конкретную привязку по surrogate id (миграция 25, ADR-FK-A) */
    public boolean deleteById(UUID id) {
        return jdbcTemplate.update("DELETE FROM node_sources WHERE id = ?", id) > 0;
    }

    /**
     * Backward-compat: возвращает first link для пары (node, source).
     * После миграции 25 (FK variant A) пара не уникальна - может быть N
     * citations на разных страницах/range. Used в legacy тестах и legacy
     * AddSourceModal flow (one freeform link per pair)
     */
    public Optional<NodeSource> findByIds(UUID nodeId, UUID sourceId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM node_sources WHERE node_id = ? AND source_id = ? "
                        + "ORDER BY created_at LIMIT 1",
                ROW_MAPPER,
                nodeId, sourceId
        ).stream().findFirst();
    }

    /**
     * Backward-compat: удалить **все** links для пары (node, source).
     * Используется legacy detach flow. Для точечного detach использовать
     * {@link #deleteById(UUID)}
     */
    public boolean delete(UUID nodeId, UUID sourceId) {
        return jdbcTemplate.update(
                "DELETE FROM node_sources WHERE node_id = ? AND source_id = ?",
                nodeId, sourceId
        ) > 0;
    }

    /**
     * NodeSource + structured citation для academic display (ADR-028).
     * CitationDetail включает 27 raw полей из 9 LEFT JOIN - frontend
     * рендерит каждое поле в своём блоке.
     */
    public record NodeSourceWithLocation(NodeSource ns, CitationDetail citation) {
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
            FROM node_sources ns
            LEFT JOIN sources s                   ON s.id = ns.source_id
            LEFT JOIN lib_books b                 ON b.id = s.book_id
            LEFT JOIN authorities a               ON a.id = b.authority_id
            LEFT JOIN lib_muhaqqiqs mh            ON mh.id = b.muhaqqiq_id
            LEFT JOIN lib_publishers pub          ON pub.id = b.publisher_id
            LEFT JOIN lib_publication_places pl   ON pl.id = b.publication_place_id
            LEFT JOIN lib_pages p                 ON p.id = ns.page_id
            LEFT JOIN lib_image_regions ir        ON ir.id = ns.image_region_id
            LEFT JOIN lib_pages p2                ON p2.id = ir.page_id
            """.replace("%COLS%", prefixedColumns());

    private static String prefixedColumns() {
        StringBuilder sb = new StringBuilder();
        for (String c : COLUMNS.split(", ")) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("ns.").append(c);
        }
        return sb.toString();
    }

    private static CitationDetail citationFromRow(ResultSet rs) throws SQLException {
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

    public List<NodeSourceWithLocation> findByNodeIdWithLocation(UUID nodeId) {
        return jdbcTemplate.query(
                JOIN_LOCATION_SQL + " WHERE ns.node_id = ? ORDER BY ns.created_at",
                (rs, rn) -> new NodeSourceWithLocation(
                        ROW_MAPPER.mapRow(rs, rn),
                        citationFromRow(rs)
                ),
                nodeId
        );
    }

    public Optional<NodeSourceWithLocation> findByIdWithLocation(UUID id) {
        return jdbcTemplate.query(
                JOIN_LOCATION_SQL + " WHERE ns.id = ?",
                (rs, rn) -> new NodeSourceWithLocation(
                        ROW_MAPPER.mapRow(rs, rn),
                        citationFromRow(rs)
                ),
                id
        ).stream().findFirst();
    }
}
