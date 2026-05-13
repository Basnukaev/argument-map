package ru.basnukaev.argumentmap.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.domain.NodeSource;

@Repository
public class NodeSourceRepository {

    private static final String COLUMNS =
            "node_id, source_id, quote, context, location, "
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
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)",
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

    public Optional<NodeSource> findByIds(UUID nodeId, UUID sourceId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM node_sources WHERE node_id = ? AND source_id = ?",
                ROW_MAPPER,
                nodeId, sourceId
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

    public boolean delete(UUID nodeId, UUID sourceId) {
        return jdbcTemplate.update(
                "DELETE FROM node_sources WHERE node_id = ? AND source_id = ?",
                nodeId, sourceId
        ) > 0;
    }

    /**
     * Прицепом к NodeSource - computed location string и bookId (из Source).
     * Computed location склеивается на бэке через CASE: TEXT mode даёт
     * book.title + part + printedPage + range, PDF mode - book.title + PDF
     * стр N + регион, REGION mode - book.title + скан стр N, LEGACY -
     * fallback на снепшот ns.location.
     */
    public record NodeSourceWithLocation(NodeSource ns, String computedLocation, UUID bookId) {
    }

    private static final String JOIN_LOCATION_SQL = """
            SELECT %COLS%,
              s.book_id AS src_book_id,
              CASE
                WHEN ns.page_id IS NOT NULL THEN
                  COALESCE(b.title, '?') || ', Т.' || COALESCE(p.part, '?')
                    || ' стр.' || COALESCE(p.printed_page, p.page_number::text)
                WHEN ns.pdf_file_id IS NOT NULL THEN
                  COALESCE(b.title, '?') || ', PDF стр.' || ns.pdf_page_number
                WHEN ns.image_region_id IS NOT NULL THEN
                  COALESCE(b.title, '?') || ', скан стр.' ||
                  COALESCE(p2.printed_page, p2.page_number::text)
                ELSE ns.location
              END AS computed_location
            FROM node_sources ns
            LEFT JOIN sources s ON s.id = ns.source_id
            LEFT JOIN lib_books b ON b.id = s.book_id
            LEFT JOIN lib_pages p ON p.id = ns.page_id
            LEFT JOIN lib_image_regions ir ON ir.id = ns.image_region_id
            LEFT JOIN lib_pages p2 ON p2.id = ir.page_id
            """.replace("%COLS%", prefixedColumns());

    private static String prefixedColumns() {
        // ns.node_id, ns.source_id, ..., ns.created_at - явный префикс
        // во избежание ambiguity с p.page_id и т.п.
        StringBuilder sb = new StringBuilder();
        for (String c : COLUMNS.split(", ")) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("ns.").append(c);
        }
        return sb.toString();
    }

    public List<NodeSourceWithLocation> findByNodeIdWithLocation(UUID nodeId) {
        return jdbcTemplate.query(
                JOIN_LOCATION_SQL + " WHERE ns.node_id = ? ORDER BY ns.created_at",
                (rs, rn) -> new NodeSourceWithLocation(
                        ROW_MAPPER.mapRow(rs, rn),
                        rs.getString("computed_location"),
                        rs.getObject("src_book_id", UUID.class)
                ),
                nodeId
        );
    }

    public Optional<NodeSourceWithLocation> findByPkWithLocation(UUID nodeId, UUID sourceId) {
        return jdbcTemplate.query(
                JOIN_LOCATION_SQL + " WHERE ns.node_id = ? AND ns.source_id = ?",
                (rs, rn) -> new NodeSourceWithLocation(
                        ROW_MAPPER.mapRow(rs, rn),
                        rs.getString("computed_location"),
                        rs.getObject("src_book_id", UUID.class)
                ),
                nodeId, sourceId
        ).stream().findFirst();
    }
}
