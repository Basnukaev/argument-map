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
            "id, book_id, chapter_id, page_number, printed_page, part, pdf_page_number, "
            + "text_content, image_url, formatted_content, created_at, updated_at";

    private static final RowMapper<Page> ROW_MAPPER = (rs, rn) -> {
        int pdfPage = rs.getInt("pdf_page_number");
        Integer pdfPageOrNull = rs.wasNull() ? null : pdfPage;
        return new Page(
                rs.getObject("id", UUID.class),
                rs.getObject("book_id", UUID.class),
                rs.getObject("chapter_id", UUID.class),
                rs.getInt("page_number"),
                rs.getString("printed_page"),
                rs.getString("part"),
                pdfPageOrNull,
                rs.getString("text_content"),
                rs.getString("image_url"),
                rs.getString("formatted_content"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public PageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Page save(Page page) {
        // formatted_content идёт через ::jsonb cast - JdbcTemplate
        // передаёт String, Postgres парсит. Это проще чем
        // PGobject + Types.OTHER и единообразно с тем как мы
        // храним другие jsonb колонки (metadata в lib_books)
        jdbcTemplate.update(
                "INSERT INTO lib_pages (" + COLUMNS + ") "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)",
                page.id(),
                page.bookId(),
                page.chapterId(),
                page.pageNumber(),
                page.printedPage(),
                page.part(),
                page.pdfPageNumber(),
                page.textContent(),
                page.imageUrl(),
                page.formattedContent(),
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

    /**
     * Уникальные значения {@code part} (томов/juz') в книге в порядке
     * первого появления. Используется для построения dropdown селектора
     * томов в reader-фронте: если у книги > 1 part, фронт показывает
     * dropdown, иначе скрывает.
     */
    public List<String> findDistinctPartsByBookId(UUID bookId) {
        return jdbcTemplate.queryForList(
                "SELECT part FROM lib_pages "
                        + "WHERE book_id = ? AND part IS NOT NULL "
                        + "GROUP BY part ORDER BY MIN(page_number)",
                String.class,
                bookId
        );
    }

    /**
     * Partial update только колонки {@code formatted_content}
     * (миграция 33, ADR-039). Используется admin editor flow когда
     * пользователь сохраняет ProseMirror JSON через PATCH endpoint.
     * Прочие поля (text_content, image_url, printed_page) не трогаются -
     * editor не меняет structural metadata страницы.
     *
     * <p>Также bump {@code updated_at} для отслеживания «когда страница
     * последний раз редактировалась».
     *
     * @return true если row updated, false если page id не найден
     */
    public boolean updateFormattedContent(UUID id, String formattedContent) {
        int rows = jdbcTemplate.update(
                "UPDATE lib_pages SET formatted_content = ?::jsonb, updated_at = now() "
                        + "WHERE id = ?",
                formattedContent,
                id
        );
        return rows > 0;
    }

    public boolean deleteById(UUID id) {
        return jdbcTemplate.update("DELETE FROM lib_pages WHERE id = ?", id) > 0;
    }
}
