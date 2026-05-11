package ru.basnukaev.argumentmap.library.shamela.service.mapper;

import static ru.basnukaev.argumentmap.library.shamela.service.mapper.ShamelaMapperUtils.blankToNull;
import static ru.basnukaev.argumentmap.library.shamela.service.mapper.ShamelaMapperUtils.normalizeName;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ru.basnukaev.argumentmap.domain.Authority;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaAuthorRow;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaAuthorDao;
import ru.basnukaev.argumentmap.repository.AuthorityRepository;

/**
 * Резолвинг {@code lib_shamela_book.author_id} в {@code Authority} в
 * доменной модели argument-map. Сценарии:
 * <ul>
 *   <li>{@code authorId == null} - возврат anonymous-Authority
 *       ({@link #ANONYMOUS_AUTHORITY_NAME})</li>
 *   <li>{@code authorId} есть в {@code lib_shamela_author} - exact-match
 *       по нормализованному имени в {@code authorities}, fallback на
 *       создание новой записи</li>
 *   <li>{@code authorId} dangling FK (нет в shamela_author) -
 *       anonymous-Authority + warn-лог. Бывает при частичном sync</li>
 * </ul>
 */
@Service
public class ShamelaAuthorityResolver {

    /**
     * Имя для специальной anonymous-Authority, к которой привязываются
     * книги без автора (или с неразрешимым автором). Префикс
     * {@code shamela:} отделяет от пользовательских вводов и других
     * ETL-источников.
     */
    public static final String ANONYMOUS_AUTHORITY_NAME = "shamela:anonymous";

    private static final Logger log = LoggerFactory.getLogger(ShamelaAuthorityResolver.class);

    private final ShamelaAuthorDao shamelaAuthorDao;
    private final AuthorityRepository authorityRepository;

    public ShamelaAuthorityResolver(ShamelaAuthorDao shamelaAuthorDao,
                                    AuthorityRepository authorityRepository) {
        this.shamelaAuthorDao = shamelaAuthorDao;
        this.authorityRepository = authorityRepository;
    }

    public UUID resolve(Long shamelaAuthorId) {
        if (shamelaAuthorId == null) {
            return resolveAnonymous();
        }
        Optional<ShamelaAuthorRow> shamelaAuthor = shamelaAuthorDao.findById(shamelaAuthorId);
        if (shamelaAuthor.isEmpty()) {
            log.warn("shamela map: author_id={} не найден в lib_shamela_author, fallback на anonymous",
                    shamelaAuthorId);
            return resolveAnonymous();
        }
        String normalized = normalizeName(shamelaAuthor.get().name());
        if (normalized == null) {
            return resolveAnonymous();
        }
        Optional<Authority> existing = authorityRepository.findByName(normalized);
        if (existing.isPresent()) {
            return existing.get().id();
        }
        Authority created = new Authority(
                UUID.randomUUID(),
                normalized,
                blankToNull(shamelaAuthor.get().biography()),
                null,
                null,
                null,
                Instant.now()
        );
        authorityRepository.save(created);
        return created.id();
    }

    private UUID resolveAnonymous() {
        Optional<Authority> existing = authorityRepository.findByName(ANONYMOUS_AUTHORITY_NAME);
        if (existing.isPresent()) {
            return existing.get().id();
        }
        Authority created = new Authority(
                UUID.randomUUID(),
                ANONYMOUS_AUTHORITY_NAME,
                "Импорт shamela без указания автора",
                null,
                null,
                null,
                Instant.now()
        );
        authorityRepository.save(created);
        return created.id();
    }
}
