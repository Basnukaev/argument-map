package ru.basnukaev.argumentmap.library.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.library.domain.BookMember;

/**
 * Доступ к lib_book_members (ADR-043 Amendment, Этап 22.c). M:N линковка
 * SHARED-книги и со-редакторов. JDBC Template, snake_case columns.
 * Аналог {@link ru.basnukaev.argumentmap.repository.TopicMemberRepository}.
 */
@Repository
public class BookMemberRepository {

    private static final String COLUMNS =
            "id, book_id, user_id, role, added_at, added_by";

    private static final RowMapper<BookMember> ROW_MAPPER = (rs, rn) -> new BookMember(
            rs.getObject("id", UUID.class),
            rs.getObject("book_id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("role"),
            instant(rs, "added_at"),
            rs.getObject("added_by", UUID.class)
    );

    private final JdbcTemplate jdbcTemplate;

    public BookMemberRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public BookMember save(BookMember member) {
        jdbcTemplate.update(
                "INSERT INTO lib_book_members (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?)",
                member.id(),
                member.bookId(),
                member.userId(),
                member.role(),
                odt(member.addedAt()),
                member.addedBy()
        );
        return member;
    }

    public Optional<BookMember> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_book_members WHERE id = ?",
                ROW_MAPPER, id
        ).stream().findFirst();
    }

    public List<BookMember> findByBookId(UUID bookId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_book_members WHERE book_id = ? ORDER BY added_at",
                ROW_MAPPER, bookId
        );
    }

    public List<BookMember> findByUserId(UUID userId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_book_members WHERE user_id = ? ORDER BY added_at",
                ROW_MAPPER, userId
        );
    }

    public Optional<BookMember> findByBookAndUser(UUID bookId, UUID userId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_book_members WHERE book_id = ? AND user_id = ?",
                ROW_MAPPER, bookId, userId
        ).stream().findFirst();
    }

    public boolean existsByBookAndUser(UUID bookId, UUID userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lib_book_members WHERE book_id = ? AND user_id = ?",
                Integer.class, bookId, userId
        );
        return count != null && count > 0;
    }

    public boolean delete(UUID id) {
        return jdbcTemplate.update("DELETE FROM lib_book_members WHERE id = ?", id) > 0;
    }

    public void updateRole(UUID id, String newRole) {
        jdbcTemplate.update("UPDATE lib_book_members SET role = ? WHERE id = ?", newRole, id);
    }
}
