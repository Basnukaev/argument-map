package ru.basnukaev.argumentmap.library.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.library.domain.ImageRegion;

@Repository
public class ImageRegionRepository {

    private static final String COLUMNS =
            "id, page_id, x, y, width, height, extracted_text, created_at";

    private static final RowMapper<ImageRegion> ROW_MAPPER = (rs, rn) -> new ImageRegion(
            rs.getObject("id", UUID.class),
            rs.getObject("page_id", UUID.class),
            rs.getDouble("x"),
            rs.getDouble("y"),
            rs.getDouble("width"),
            rs.getDouble("height"),
            rs.getString("extracted_text"),
            instant(rs, "created_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public ImageRegionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ImageRegion save(ImageRegion region) {
        jdbcTemplate.update(
                "INSERT INTO lib_image_regions (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                region.id(),
                region.pageId(),
                region.x(),
                region.y(),
                region.width(),
                region.height(),
                region.extractedText(),
                odt(region.createdAt())
        );
        return region;
    }

    public Optional<ImageRegion> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_image_regions WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    public List<ImageRegion> findByPageId(UUID pageId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_image_regions WHERE page_id = ? "
                        + "ORDER BY created_at",
                ROW_MAPPER,
                pageId
        );
    }

    public boolean deleteById(UUID id) {
        return jdbcTemplate.update("DELETE FROM lib_image_regions WHERE id = ?", id) > 0;
    }
}
