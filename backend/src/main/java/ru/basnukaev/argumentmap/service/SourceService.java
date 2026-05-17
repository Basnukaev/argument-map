package ru.basnukaev.argumentmap.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.domain.Reliability;
import ru.basnukaev.argumentmap.domain.Source;
import ru.basnukaev.argumentmap.domain.SourceType;
import ru.basnukaev.argumentmap.exception.AuthorityNotFoundException;
import ru.basnukaev.argumentmap.exception.BookNotFoundException;
import ru.basnukaev.argumentmap.exception.InvalidSourceException;
import ru.basnukaev.argumentmap.exception.SourceNotFoundException;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.repository.AuthorityRepository;
import ru.basnukaev.argumentmap.repository.SourceRepository;

@Service
public class SourceService {

    private final SourceRepository sourceRepository;
    private final AuthorityRepository authorityRepository;
    private final BookRepository bookRepository;

    public SourceService(SourceRepository sourceRepository,
                         AuthorityRepository authorityRepository,
                         BookRepository bookRepository) {
        this.sourceRepository = sourceRepository;
        this.authorityRepository = authorityRepository;
        this.bookRepository = bookRepository;
    }

    @Transactional
    public Source createSource(SourceType sourceType, String title, String citation,
                               Reliability reliability, UUID authorityId, UUID bookId,
                               String metadataJson) {
        if (reliability != null && sourceType != SourceType.HADITH) {
            throw new InvalidSourceException(
                    "поле reliability допустимо только для типа HADITH"
            );
        }
        if (authorityId != null && authorityRepository.findById(authorityId).isEmpty()) {
            throw new AuthorityNotFoundException(authorityId);
        }
        if (bookId != null && bookRepository.findById(bookId).isEmpty()) {
            // 404 даёт чёткую ошибку клиенту вместо FK-violation 500
            throw new BookNotFoundException(bookId);
        }
        Source source = new Source(
                UUID.randomUUID(), sourceType, title, citation,
                reliability, authorityId, bookId, metadataJson, Instant.now()
        );
        sourceRepository.save(source);
        return source;
    }

    @Transactional(readOnly = true)
    public Source getSource(UUID id) {
        return sourceRepository.findById(id)
                .orElseThrow(() -> new SourceNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Source> listSources() {
        return sourceRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Source> searchByTitle(String query) {
        return sourceRepository.searchByTitle(query);
    }

    /**
     * Пагинированный список с фильтрами (Этап pagination). Combination
     * type≠HADITH + reliability!=null → {@link InvalidSourceException}
     * (400, не 422) - это ошибка клиента в построении query, не нарушение
     * бизнес-инварианта в payload. Бросается до SQL-запроса.
     */
    @Transactional(readOnly = true)
    public List<Source> listPage(SourceType type, Reliability reliability, String query,
                                 int limit, int offset) {
        validateFilters(type, reliability);
        return sourceRepository.findPage(type, reliability, query, limit, offset);
    }

    @Transactional(readOnly = true)
    public long countFiltered(SourceType type, Reliability reliability, String query) {
        validateFilters(type, reliability);
        return sourceRepository.countFiltered(type, reliability, query);
    }

    private static void validateFilters(SourceType type, Reliability reliability) {
        if (reliability != null && type != null && type != SourceType.HADITH) {
            // IllegalArgumentException → 400 (handler). Это ошибка query-params,
            // не нарушение бизнес-инварианта payload (которое было бы 422
            // через InvalidSourceException).
            throw new IllegalArgumentException(
                    "фильтр reliability допустим только при type=HADITH, получен type=" + type
            );
        }
    }

    @Transactional
    public void deleteSource(UUID id) {
        boolean removed = sourceRepository.deleteById(id);
        if (!removed) {
            throw new SourceNotFoundException(id);
        }
    }
}
