package ru.basnukaev.argumentmap.library.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.library.domain.UserBookCollection;

/**
 * Доступ к user_book_collections (Vision 49d Section 2.2). Личные
 * коллекции книг пользователей. JDBC Template, snake_case columns.
 *
 * <p>Patterns mirror {@link BookMemberRepository}. UNIQUE constraint
 * (user_id, book_id, collection_name) защищает от дублей, repository
 * upgrade'ит DuplicateKeyException в исключение которое service-слой
 * обрабатывает идемпотентно.
 */
@Repository
public class UserBookCollectionRepository {

    private static final String COLUMNS =
            "id, user_id, book_id, collection_name, added_at";

    private static final RowMapper<UserBookCollection> ROW_MAPPER = (rs, rn) -> new UserBookCollection(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getObject("book_id", UUID.class),
            rs.getString("collection_name"),
            instant(rs, "added_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public UserBookCollectionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Сохраняет новую запись collection-entry. Если UNIQUE constraint
     * нарушен (тот же user_id + book_id + collection_name уже есть) -
     * бросает {@link DuplicateKeyException} - service делает idempotent.
     */
    public UserBookCollection save(UserBookCollection entry) {
        jdbcTemplate.update(
                "INSERT INTO user_book_collections (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?)",
                entry.id(),
                entry.userId(),
                entry.bookId(),
                entry.collectionName(),
                odt(entry.addedAt())
        );
        return entry;
    }

    /**
     * Все записи user'а (все коллекции, все книги). Сортировка по
     * added_at DESC (недавно добавленные сверху).
     */
    public List<UserBookCollection> findByUser(UUID userId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM user_book_collections "
                        + "WHERE user_id = ? ORDER BY added_at DESC",
                ROW_MAPPER, userId
        );
    }

    /**
     * Все книги user'а в конкретной коллекции (например "Избранное").
     */
    public List<UserBookCollection> findByUserAndCollection(UUID userId, String collectionName) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM user_book_collections "
                        + "WHERE user_id = ? AND collection_name = ? ORDER BY added_at DESC",
                ROW_MAPPER, userId, collectionName
        );
    }

    /**
     * Существует ли запись о книге в коллекции user'а. Для idempotent
     * add - не надо ловить DuplicateKey если можно проверить заранее.
     */
    public boolean exists(UUID userId, UUID bookId, String collectionName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_book_collections "
                        + "WHERE user_id = ? AND book_id = ? AND collection_name = ?",
                Integer.class, userId, bookId, collectionName
        );
        return count != null && count > 0;
    }

    /**
     * Удаляет конкретную запись. Возвращает количество удалённых
     * (0 если не было такой записи).
     */
    public int delete(UUID userId, UUID bookId, String collectionName) {
        return jdbcTemplate.update(
                "DELETE FROM user_book_collections "
                        + "WHERE user_id = ? AND book_id = ? AND collection_name = ?",
                userId, bookId, collectionName
        );
    }

    /**
     * Удаляет ВСЮ коллекцию user'а (все записи с данным
     * collection_name). Используется при rename - удалить старое +
     * пересоздать новое.
     */
    public int deleteCollection(UUID userId, String collectionName) {
        return jdbcTemplate.update(
                "DELETE FROM user_book_collections WHERE user_id = ? AND collection_name = ?",
                userId, collectionName
        );
    }

    /**
     * Список уникальных collection_name user'а - для side panel
     * "Мои коллекции" с counts.
     */
    public List<String> listCollectionNames(UUID userId) {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT collection_name FROM user_book_collections "
                        + "WHERE user_id = ? ORDER BY collection_name",
                String.class, userId
        );
    }
}
