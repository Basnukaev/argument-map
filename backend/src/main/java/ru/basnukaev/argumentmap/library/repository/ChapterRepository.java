package ru.basnukaev.argumentmap.library.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.library.domain.Chapter;

@Repository
public class ChapterRepository {

    private static final String COLUMNS =
            "id, book_id, parent_chapter_id, title, order_index, start_page_number, created_at";

    private static final RowMapper<Chapter> ROW_MAPPER = (rs, rn) -> {
        int startPage = rs.getInt("start_page_number");
        Integer startPageOrNull = rs.wasNull() ? null : startPage;
        return new Chapter(
                rs.getObject("id", UUID.class),
                rs.getObject("book_id", UUID.class),
                rs.getObject("parent_chapter_id", UUID.class),
                rs.getString("title"),
                rs.getInt("order_index"),
                startPageOrNull,
                instant(rs, "created_at")
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public ChapterRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Chapter save(Chapter chapter) {
        jdbcTemplate.update(
                "INSERT INTO lib_chapters (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?)",
                chapter.id(),
                chapter.bookId(),
                chapter.parentChapterId(),
                chapter.title(),
                chapter.orderIndex(),
                chapter.startPageNumber(),
                odt(chapter.createdAt())
        );
        return chapter;
    }

    public Optional<Chapter> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_chapters WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    public List<Chapter> findByBookId(UUID bookId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_chapters WHERE book_id = ? "
                        + "ORDER BY parent_chapter_id NULLS FIRST, order_index",
                ROW_MAPPER,
                bookId
        );
    }

    public boolean deleteById(UUID id) {
        return jdbcTemplate.update("DELETE FROM lib_chapters WHERE id = ?", id) > 0;
    }
}
