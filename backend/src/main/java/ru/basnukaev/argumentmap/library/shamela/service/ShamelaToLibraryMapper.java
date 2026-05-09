package ru.basnukaev.argumentmap.library.shamela.service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.basnukaev.argumentmap.domain.Authority;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.Chapter;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.ChapterRepository;
import ru.basnukaev.argumentmap.library.repository.PageRepository;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaAuthorRow;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaBookRow;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaPageRow;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaTitleRow;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaAuthorDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaBookDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaPageDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaTitleDao;
import ru.basnukaev.argumentmap.repository.AuthorityRepository;

/**
 * Маппинг из staging-таблиц {@code lib_shamela_*} в доменную модель
 * библиотеки ({@code lib_books}/{@code authorities}/{@code lib_chapters}/
 * {@code lib_pages}). Второй слой ETL (ADR-020) - первый слой
 * {@link ShamelaImportService} наполняет staging, маппер переводит
 * staging → домен.
 *
 * <p>Идемпотентность: re-import возвращает существующую книгу без
 * пересоздания. Это безопасно для FK-ссылок от {@code node_sources} -
 * удаление+пересоздание сломало бы привязки источников к узлам
 * argument-map. Если нужен честный re-import (новый major_release из
 * shamela с обновлённым контентом) - на MVP надо вручную удалить
 * `lib_books`-запись и вызвать {@link #mapBook} снова. Future task -
 * smart-merge с сохранением FK.
 *
 * <p>Транзакционность: один вызов {@link #mapBook} - атомарная
 * единица. Если упадёт в середине (например, на 500-й chapter из 1000),
 * откат вернёт всё к состоянию до начала. Размер транзакции для одной
 * книги ~100KB-2MB, лок держится секунды - приемлемо.
 *
 * <p>Решения по полям:
 * <ul>
 *   <li>{@code book_type} - всегда {@code BOOK} на MVP. {@code shamela_book.type}
 *       integer 1-3+ имеет неясную семантику (нет docs от shamela);
 *       уточнить после live-syncMaster и реальных данных. Расширение -
 *       без миграции, только в маппере</li>
 *   <li>{@code language} - всегда {@code "ar"}. Каталог shamela арабский</li>
 *   <li>{@code authorityId} - резолв по {@code shamela_book.author_id} →
 *       {@code shamela_author.name}, exact-match с trim+collapse-whitespace.
 *       Fallback: anonymous Authority (имя {@value #ANONYMOUS_AUTHORITY_NAME})
 *       для null/dangling/empty</li>
 *   <li>{@code chapter_id} в {@code lib_pages} - всегда NULL на MVP.
 *       Связь page→chapter через {@code shamela_title.page} (TEXT с
 *       возможным range "1-3") - отложена на future iteration. Главы
 *       создаются для tree-навигации в reader (Этап 18) независимо</li>
 *   <li>{@code metadata} jsonb - {@code shamela_book_id} (long) для
 *       re-import detection, {@code shamela_major_release} (int),
 *       {@code pdf_links} (raw shamela json) для будущего PDF endpoint.
 *       GIN-индекс на metadata уже есть из миграции 16</li>
 * </ul>
 */
@Service
public class ShamelaToLibraryMapper {

    /**
     * Имя для специальной anonymous-Authority, к которой привязываются
     * книги без автора (или с неразрешимым автором). Префикс
     * {@code shamela:} отделяет от пользовательских вводов и от
     * других ETL-источников.
     */
    public static final String ANONYMOUS_AUTHORITY_NAME = "shamela:anonymous";

    private static final String SHAMELA_LANGUAGE = "ar";
    private static final String EMPTY_TITLE_PLACEHOLDER = "(без названия)";

    private static final Logger log = LoggerFactory.getLogger(ShamelaToLibraryMapper.class);

    private final ShamelaBookDao shamelaBookDao;
    private final ShamelaAuthorDao shamelaAuthorDao;
    private final ShamelaTitleDao shamelaTitleDao;
    private final ShamelaPageDao shamelaPageDao;
    private final BookRepository bookRepository;
    private final AuthorityRepository authorityRepository;
    private final ChapterRepository chapterRepository;
    private final PageRepository pageRepository;
    private final ObjectMapper objectMapper;

    public ShamelaToLibraryMapper(ShamelaBookDao shamelaBookDao,
                                  ShamelaAuthorDao shamelaAuthorDao,
                                  ShamelaTitleDao shamelaTitleDao,
                                  ShamelaPageDao shamelaPageDao,
                                  BookRepository bookRepository,
                                  AuthorityRepository authorityRepository,
                                  ChapterRepository chapterRepository,
                                  PageRepository pageRepository,
                                  ObjectMapper objectMapper) {
        this.shamelaBookDao = shamelaBookDao;
        this.shamelaAuthorDao = shamelaAuthorDao;
        this.shamelaTitleDao = shamelaTitleDao;
        this.shamelaPageDao = shamelaPageDao;
        this.bookRepository = bookRepository;
        this.authorityRepository = authorityRepository;
        this.chapterRepository = chapterRepository;
        this.pageRepository = pageRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MappedBookResult mapBook(long shamelaBookId, UUID createdBy) {
        ShamelaBookRow shamelaBook = shamelaBookDao.findById(shamelaBookId).orElseThrow(() ->
                new ShamelaNotFoundException(
                        "shamela book id=" + shamelaBookId + " не найдена в lib_shamela_book - "
                                + "сначала выполни syncMaster() или importBook()"));

        Optional<Book> existing = bookRepository.findByShamelaBookId(shamelaBookId);
        if (existing.isPresent()) {
            Book book = existing.get();
            log.info("shamela map skip: bookId={} уже замаплен в lib_books id={}",
                    shamelaBookId, book.id());
            return MappedBookResult.alreadyMapped(book.id(), shamelaBookId, book.authorityId());
        }

        UUID authorityId = resolveAuthority(shamelaBook.authorId());
        Instant now = Instant.now();
        UUID bookUuid = UUID.randomUUID();
        Book book = new Book(
                bookUuid,
                BookType.BOOK,
                sanitizeTitle(shamelaBook.name()),
                authorityId,
                SHAMELA_LANGUAGE,
                blankToNull(shamelaBook.bibliography()),
                buildShamelaMetadata(shamelaBook),
                createdBy,
                now,
                now
        );
        bookRepository.save(book);

        int chaptersCount = mapChapters(bookUuid, shamelaBookId, now);
        int pagesCount = mapPages(bookUuid, shamelaBookId, now);

        log.info("shamela mapped: shamelaBookId={} -> lib_books={} chapters={} pages={} authority={}",
                shamelaBookId, bookUuid, chaptersCount, pagesCount, authorityId);
        return MappedBookResult.freshlyCreated(bookUuid, shamelaBookId, authorityId,
                chaptersCount, pagesCount);
    }

    private UUID resolveAuthority(Long shamelaAuthorId) {
        if (shamelaAuthorId == null) {
            return resolveAnonymousAuthority();
        }
        Optional<ShamelaAuthorRow> shamelaAuthor = shamelaAuthorDao.findById(shamelaAuthorId);
        if (shamelaAuthor.isEmpty()) {
            // dangling FK - shamela_book.author_id указывает на автора которого
            // нет в lib_shamela_author. Бывает при частичном sync. Treat as
            // anonymous, не падаем
            log.warn("shamela map: author_id={} не найден в lib_shamela_author, fallback на anonymous",
                    shamelaAuthorId);
            return resolveAnonymousAuthority();
        }
        String normalized = normalizeName(shamelaAuthor.get().name());
        if (normalized == null) {
            return resolveAnonymousAuthority();
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

    private UUID resolveAnonymousAuthority() {
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

    /**
     * Строит {@code metadata} jsonb для книги. Содержит только
     * shamela-специфичные поля - универсальные поля (title, language)
     * хранятся в обычных колонках {@code lib_books}.
     */
    private String buildShamelaMetadata(ShamelaBookRow shamelaBook) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("shamela_book_id", shamelaBook.id());
            root.put("shamela_major_release", shamelaBook.majorRelease());
            if (shamelaBook.pdfLinksJson() != null && !shamelaBook.pdfLinksJson().isBlank()) {
                JsonNode pdfLinks = objectMapper.readTree(shamelaBook.pdfLinksJson());
                root.set("pdf_links", pdfLinks);
            }
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new ShamelaImportException(
                    "ошибка построения metadata jsonb для shamela book id=" + shamelaBook.id(), e);
        }
    }

    /**
     * Маппинг {@code lib_shamela_title} → {@code lib_chapters} с
     * сохранением parent-tree. Алгоритм - BFS от root-titles вглубь:
     * на момент создания child-чаптера его parent уже сохранён, и его
     * UUID известен через {@code shamelaIdToChapterUuid}.
     *
     * <p>{@code order_index} = индекс title в монотонном порядке id
     * (shamela вставляет id в порядке появления заголовка в книге).
     *
     * <p>Защита от битых данных: если {@code parent_id} ссылается на
     * несуществующий title (orphan) - такой title becomes root, не
     * падаем. Циклы в parent-tree не должны быть в shamela по природе
     * данных, но если случатся - повиснем в очереди (BFS не зайдёт в
     * них, потому что root-фильтр требует parent в self-таблице).
     *
     * @return сколько chapter-записей создано
     */
    private int mapChapters(UUID bookUuid, long shamelaBookId, Instant now) {
        List<ShamelaTitleRow> titles = shamelaTitleDao.findAllByBookId(shamelaBookId);
        if (titles.isEmpty()) {
            return 0;
        }
        Map<Integer, ShamelaTitleRow> byId = new HashMap<>();
        for (ShamelaTitleRow t : titles) {
            byId.put(t.id(), t);
        }
        Map<Integer, Integer> orderById = new HashMap<>();
        for (int i = 0; i < titles.size(); i++) {
            orderById.put(titles.get(i).id(), i);
        }
        Map<Integer, List<ShamelaTitleRow>> children = new HashMap<>();
        Queue<ShamelaTitleRow> queue = new ArrayDeque<>();
        for (ShamelaTitleRow t : titles) {
            if (t.parentId() == null || !byId.containsKey(t.parentId())) {
                queue.add(t);
            } else {
                children.computeIfAbsent(t.parentId(), k -> new ArrayList<>()).add(t);
            }
        }
        Map<Integer, UUID> shamelaIdToChapterUuid = new HashMap<>();
        int created = 0;
        while (!queue.isEmpty()) {
            ShamelaTitleRow t = queue.poll();
            UUID parentUuid = (t.parentId() != null && byId.containsKey(t.parentId()))
                    ? shamelaIdToChapterUuid.get(t.parentId())
                    : null;
            UUID chapterUuid = UUID.randomUUID();
            Chapter chapter = new Chapter(
                    chapterUuid,
                    bookUuid,
                    parentUuid,
                    sanitizeTitle(t.content()),
                    orderById.get(t.id()),
                    now
            );
            chapterRepository.save(chapter);
            shamelaIdToChapterUuid.put(t.id(), chapterUuid);
            created++;
            List<ShamelaTitleRow> kids = children.get(t.id());
            if (kids != null) {
                queue.addAll(kids);
            }
        }
        return created;
    }

    /**
     * Маппинг {@code lib_shamela_page} → {@code lib_pages}. {@code page_number}
     * = {@code shamela_page.id} (shamela 1-based monotonic). {@code chapter_id}
     * = NULL на MVP - связь откладывается. Пустые страницы (NULL/blank
     * content) пропускаются - {@code lib_pages_content_present} CHECK
     * требует наличия text_content или image_url.
     *
     * @return сколько page-записей создано
     */
    private int mapPages(UUID bookUuid, long shamelaBookId, Instant now) {
        List<ShamelaPageRow> pages = shamelaPageDao.findAllByBookId(shamelaBookId);
        if (pages.isEmpty()) {
            return 0;
        }
        Set<Integer> seenPageNumbers = new HashSet<>();
        int created = 0;
        for (ShamelaPageRow p : pages) {
            if (p.content() == null || p.content().isBlank()) {
                continue;
            }
            if (!seenPageNumbers.add(p.id())) {
                // composite PK (book_id, id) гарантирует уникальность в staging,
                // но защита на уровне маппера дешёвая - предохраняет от UNIQUE
                // violation на (book_id, page_number) в lib_pages
                continue;
            }
            UUID pageUuid = UUID.randomUUID();
            Page page = new Page(
                    pageUuid,
                    bookUuid,
                    null,
                    p.id(),
                    p.content(),
                    null,
                    now,
                    now
            );
            pageRepository.save(page);
            created++;
        }
        return created;
    }

    private static String normalizeName(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().replaceAll("\\s+", " ");
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String sanitizeTitle(String raw) {
        if (raw == null || raw.isBlank()) {
            return EMPTY_TITLE_PLACEHOLDER;
        }
        return raw.trim();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
