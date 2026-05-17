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
            + "created_by, created_at, updated_at, "
            + "muhaqqiq_id, publisher_id, publication_place_id, "
            + "edition_number, published_year_hijri, published_year_gregorian";

    private static final RowMapper<Book> ROW_MAPPER = (rs, rn) -> {
        int edition = rs.getInt("edition_number");
        Integer editionOrNull = rs.wasNull() ? null : edition;
        int yearH = rs.getInt("published_year_hijri");
        Integer yearHOrNull = rs.wasNull() ? null : yearH;
        int yearG = rs.getInt("published_year_gregorian");
        Integer yearGOrNull = rs.wasNull() ? null : yearG;

        return new Book(
                rs.getObject("id", UUID.class),
                BookType.valueOf(rs.getString("book_type")),
                rs.getString("title"),
                rs.getObject("authority_id", UUID.class),
                rs.getString("language"),
                rs.getString("description"),
                rs.getString("metadata"),
                rs.getObject("created_by", UUID.class),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                rs.getObject("muhaqqiq_id", UUID.class),
                rs.getObject("publisher_id", UUID.class),
                rs.getObject("publication_place_id", UUID.class),
                editionOrNull,
                yearHOrNull,
                yearGOrNull
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public BookRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Book save(Book book) {
        jdbcTemplate.update(
                "INSERT INTO lib_books (" + COLUMNS + ") "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                book.id(),
                book.bookType().name(),
                book.title(),
                book.authorityId(),
                book.language(),
                book.description(),
                book.metadata(),
                book.createdBy(),
                odt(book.createdAt()),
                odt(book.updatedAt()),
                book.muhaqqiqId(),
                book.publisherId(),
                book.publicationPlaceId(),
                book.editionNumber(),
                book.publishedYearHijri(),
                book.publishedYearGregorian()
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

    /**
     * Пагинированный поиск книг с фильтрами.
     * Сортировка: created_at DESC (новые сверху).
     */
    public List<Book> findPage(String query, BookType type,
                               UUID authorityId, UUID publisherId,
                               int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT ").append(COLUMNS)
                .append(" FROM lib_books WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, query, type, authorityId, publisherId);
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    public long countFiltered(String query, BookType type,
                              UUID authorityId, UUID publisherId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM lib_books WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, query, type, authorityId, publisherId);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    private static void appendFilters(StringBuilder sql, List<Object> args,
                                      String query, BookType type,
                                      UUID authorityId, UUID publisherId) {
        if (query != null && !query.isBlank()) {
            sql.append(" AND title ILIKE ?");
            args.add("%" + query + "%");
        }
        if (type != null) {
            sql.append(" AND book_type = ?");
            args.add(type.name());
        }
        if (authorityId != null) {
            sql.append(" AND authority_id = ?");
            args.add(authorityId);
        }
        if (publisherId != null) {
            sql.append(" AND publisher_id = ?");
            args.add(publisherId);
        }
    }

    public boolean deleteById(UUID id) {
        return jdbcTemplate.update("DELETE FROM lib_books WHERE id = ?", id) > 0;
    }

    /**
     * Количество книг замапленных из shamela. Используется в admin
     * sync-status endpoint для отображения "сколько книг доступно
     * для чтения в /books". Использует GIN-индекс на metadata.
     */
    public int countMappedFromShamela() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lib_books WHERE metadata->>'shamela_book_id' IS NOT NULL",
                Integer.class
        );
        return count == null ? 0 : count;
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

    /**
     * Все книги пришедшие из shamela ETL (имеют {@code metadata->>'shamela_book_id'}).
     * Используется backfill сервисом для перечитывания academic metadata
     * через {@code ShamelaBibliographyParser} - 20.c follow-up для книг
     * импортированных до появления parser'а.
     */
    public List<Book> findAllShamelaSourced() {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_books "
                        + "WHERE metadata->>'shamela_book_id' IS NOT NULL "
                        + "ORDER BY created_at",
                ROW_MAPPER
        );
    }

    /**
     * Partial update только academic FK и years. Используется
     * bibliography backfill - остальные поля книги не трогаем (title,
     * description, authority_id и т.д. остаются нетронутыми).
     *
     * @return {@code true} если row обновлён (book найден)
     */
    public boolean updateAcademicMetadata(UUID bookId,
                                          UUID muhaqqiqId,
                                          UUID publisherId,
                                          UUID publicationPlaceId,
                                          Integer editionNumber,
                                          Integer publishedYearHijri,
                                          Integer publishedYearGregorian) {
        int rows = jdbcTemplate.update(
                "UPDATE lib_books SET "
                        + "muhaqqiq_id = ?, publisher_id = ?, publication_place_id = ?, "
                        + "edition_number = ?, published_year_hijri = ?, published_year_gregorian = ?, "
                        + "updated_at = now() "
                        + "WHERE id = ?",
                muhaqqiqId,
                publisherId,
                publicationPlaceId,
                editionNumber,
                publishedYearHijri,
                publishedYearGregorian,
                bookId
        );
        return rows > 0;
    }
}
