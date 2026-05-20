package ru.basnukaev.argumentmap.library.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.exception.BookNotFoundException;
import ru.basnukaev.argumentmap.library.domain.UserBookCollection;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.UserBookCollectionRepository;

/**
 * Personal book collections - Vision 49d Section 2.2.
 *
 * <p>Контракт permission: каждый user видит и модифицирует только
 * свои коллекции. Cross-user access не allowed (controller вызывает
 * service с {@code @CurrentUser} userId).
 *
 * <p>Default collection name - {@link UserBookCollection#DEFAULT_COLLECTION}
 * ("Избранное"). Если caller передаёт null/blank - используем default.
 *
 * <p>Idempotent: повторный addToCollection того же (user, book, name)
 * - no-op, не бросает 409. UI ожидает что click на "В избранное"
 * всегда успешен (либо уже там, либо добавили).
 */
@Service
public class UserBookCollectionService {

    private final UserBookCollectionRepository repository;
    private final BookRepository bookRepository;

    public UserBookCollectionService(UserBookCollectionRepository repository,
                                     BookRepository bookRepository) {
        this.repository = repository;
        this.bookRepository = bookRepository;
    }

    /**
     * Добавить книгу в коллекцию user'а. Idempotent - если уже
     * добавлена, возвращает existing entry (а не бросает 409).
     *
     * <p>Validate book exists - иначе 404 book-not-found.
     */
    @Transactional
    public UserBookCollection addToCollection(UUID userId, UUID bookId, String collectionName) {
        if (bookRepository.findById(bookId).isEmpty()) {
            throw new BookNotFoundException(bookId);
        }
        String name = normalizeCollectionName(collectionName);
        if (repository.exists(userId, bookId, name)) {
            // Idempotent: возвращаем existing entry без INSERT
            return repository.findByUserAndCollection(userId, name).stream()
                    .filter(e -> e.bookId().equals(bookId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("exists returned true but findByUserAndCollection не нашёл"));
        }
        UserBookCollection entry = new UserBookCollection(
                UUID.randomUUID(), userId, bookId, name, Instant.now()
        );
        return repository.save(entry);
    }

    /**
     * Удалить книгу из коллекции user'а. Idempotent - если её там не
     * было, no-op (возвращает 0).
     */
    @Transactional
    public int removeFromCollection(UUID userId, UUID bookId, String collectionName) {
        return repository.delete(userId, bookId, normalizeCollectionName(collectionName));
    }

    @Transactional(readOnly = true)
    public List<UserBookCollection> listAll(UUID userId) {
        return repository.findByUser(userId);
    }

    @Transactional(readOnly = true)
    public List<UserBookCollection> listByCollection(UUID userId, String collectionName) {
        return repository.findByUserAndCollection(userId, normalizeCollectionName(collectionName));
    }

    @Transactional(readOnly = true)
    public List<String> listCollectionNames(UUID userId) {
        return repository.listCollectionNames(userId);
    }

    private String normalizeCollectionName(String name) {
        if (name == null || name.isBlank()) {
            return UserBookCollection.DEFAULT_COLLECTION;
        }
        String trimmed = name.trim();
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("collection_name не должно быть длиннее 100 символов");
        }
        return trimmed;
    }
}
