package ru.basnukaev.argumentmap.library.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.library.domain.Page;

@Repository
public class PageRepository {

    private static final String COLUMNS =
            "id, book_id, chapter_id, page_number, text_content, image_url, "
            + "created_at, updated_at";

    private static final RowMapper<Page> ROW_MAPPER = (rs, rn) -> new Page(
            rs.getObject("id", UUID.class),
            rs.getObject("book_id", UUID.class),
            rs.getObject("chapter_id", UUID.class),
            rs.getInt("page_number"),
            rs.getString("text_content"),
            rs.getString("image_url"),
            instant(rs, "created_at"),
            instant(rs, "updated_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public PageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Page save(Page page) {
        jdbcTemplate.update(
                "INSERT INTO lib_pages (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                page.id(),
                page.bookId(),
                page.chapterId(),
                page.pageNumber(),
                page.textContent(),
                page.imageUrl(),
                odt(page.createdAt()),
                odt(page.updatedAt())
        );
        return page;
    }

    public Optional<Page> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_pages WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    public List<Page> findByBookIdRange(UUID bookId, int fromPage, int toPage) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_pages "
                        + "WHERE book_id = ? AND page_number BETWEEN ? AND ? "
                        + "ORDER BY page_number",
                ROW_MAPPER,
                bookId,
                fromPage,
                toPage
        );
    }

    public boolean deleteById(UUID id) {
        return jdbcTemplate.update("DELETE FROM lib_pages WHERE id = ?", id) > 0;
    }
}
