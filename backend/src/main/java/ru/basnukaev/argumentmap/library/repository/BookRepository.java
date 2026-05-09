package ru.basnukaev.argumentmap.library.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;

@Repository
public class BookRepository {

    private static final String COLUMNS =
            "id, book_type, title, authority_id, language, description, metadata, "
            + "created_by, created_at, updated_at";

    private static final RowMapper<Book> ROW_MAPPER = (rs, rn) -> new Book(
            rs.getObject("id", UUID.class),
            BookType.valueOf(rs.getString("book_type")),
            rs.getString("title"),
            rs.getObject("authority_id", UUID.class),
            rs.getString("language"),
            rs.getString("description"),
            rs.getString("metadata"),
            rs.getObject("created_by", UUID.class),
            instant(rs, "created_at"),
            instant(rs, "updated_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public BookRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Book save(Book book) {
        jdbcTemplate.update(
                "INSERT INTO lib_books (" + COLUMNS + ") "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)",
                book.id(),
                book.bookType().name(),
                book.title(),
                book.authorityId(),
                book.language(),
                book.description(),
                book.metadata(),
                book.createdBy(),
                odt(book.createdAt()),
                odt(book.updatedAt())
        );
        return book;
    }

    public Optional<Book> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_books WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    public List<Book> findAll(String query, BookType type) {
        StringBuilder sql = new StringBuilder("SELECT ").append(COLUMNS).append(" FROM lib_books");
        List<Object> args = new ArrayList<>();
        List<String> conditions = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            conditions.add("title ILIKE ?");
            args.add("%" + query + "%");
        }
        if (type != null) {
            conditions.add("book_type = ?");
            args.add(type.name());
        }
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        sql.append(" ORDER BY created_at");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    public boolean deleteById(UUID id) {
        return jdbcTemplate.update("DELETE FROM lib_books WHERE id = ?", id) > 0;
    }

    /**
     * Поиск книги по {@code metadata->>'shamela_book_id'} - используется
     * shamela-импортом для re-import detection (если книга уже была
     * замаплена из staging - возвращаем существующую вместо создания
     * дубликата). GIN-индекс на {@code metadata} уже есть из миграции 16,
     * запрос идёт по jsonb-операторам без full-scan.
     */
    public Optional<Book> findByShamelaBookId(long shamelaBookId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_books "
                        + "WHERE metadata->>'shamela_book_id' = ?",
                ROW_MAPPER,
                String.valueOf(shamelaBookId)
        ).stream().findFirst();
    }
}
