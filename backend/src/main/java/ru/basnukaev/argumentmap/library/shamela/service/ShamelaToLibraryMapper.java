package ru.basnukaev.argumentmap.library.shamela.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.MuhaqqiqRepository;
import ru.basnukaev.argumentmap.library.repository.PublicationPlaceRepository;
import ru.basnukaev.argumentmap.library.repository.PublisherRepository;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaBookRow;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaBookDao;
import ru.basnukaev.argumentmap.library.shamela.service.mapper.ParsedBibliography;
import ru.basnukaev.argumentmap.library.shamela.service.mapper.ShamelaAuthorityResolver;
import ru.basnukaev.argumentmap.library.shamela.service.mapper.ShamelaBibliographyParser;
import ru.basnukaev.argumentmap.library.shamela.service.mapper.ShamelaBookMetadataBuilder;
import ru.basnukaev.argumentmap.library.shamela.service.mapper.ShamelaChapterMapper;
import ru.basnukaev.argumentmap.library.shamela.service.mapper.ShamelaPageMapper;

/**
 * Orchestrator маппинга из staging-таблиц {@code lib_shamela_*} в
 * доменную модель библиотеки. Второй слой ETL (ADR-020) - первый слой
 * ({@link ShamelaMasterSyncService} + {@link ShamelaBookImportService})
 * наполняет staging, маппер переводит staging → домен.
 *
 * <p>Класс - facade над 4 узкоспециализированными компонентами
 * ({@link ShamelaAuthorityResolver}, {@link ShamelaBookMetadataBuilder},
 * {@link ShamelaChapterMapper}, {@link ShamelaPageMapper}). Сам только
 * координирует выполнение в правильном порядке внутри одной
 * {@link Transactional}.
 *
 * <p>Идемпотентность: re-import возвращает существующую книгу без
 * пересоздания. Это безопасно для FK-ссылок от {@code node_sources} -
 * удаление+пересоздание сломало бы привязки источников к узлам
 * argument-map. Если нужен честный re-import (новый major_release
 * с обновлённым контентом) - на MVP надо вручную удалить
 * `lib_books`-запись. Future task - smart-merge с сохранением FK.
 *
 * <p>Транзакционность: один вызов {@link #mapBook} - атомарная единица.
 * Если упадёт в середине, откат вернёт всё к состоянию до начала.
 * Размер транзакции для одной книги ~100KB-2MB, лок держится секунды -
 * приемлемо.
 *
 * <p>Решения по полям:
 * <ul>
 *   <li>{@code book_type} = {@code BOOK} на MVP. {@code shamela_book.type}
 *       integer 1-3+ имеет неясную семантику - уточнить после live-данных</li>
 *   <li>{@code language} = {@code "ar"} (каталог shamela арабский)</li>
 *   <li>{@code chapter_id} в {@code lib_pages} - NULL на MVP. Связь
 *       page→chapter откладывается на future iteration</li>
 *   <li>{@code muhaqqiq_id} / {@code publisher_id} /
 *       {@code publication_place_id} / {@code edition_number} /
 *       {@code published_year_hijri/gregorian} - заполняются Этапом 20.c
 *       через {@link ShamelaBibliographyParser} (regex над
 *       {@code shamela_book.bibliography}). Парсер консервативен -
 *       любое из 6 полей может остаться NULL если marker не найден.
 *       Для админских правок руками будет Этап 20.d BookEditModal</li>
 * </ul>
 */
@Service
public class ShamelaToLibraryMapper {

    /**
     * Имя для специальной anonymous-Authority. Сохраняется как
     * public-константа для существующих consumers (тесты).
     */
    public static final String ANONYMOUS_AUTHORITY_NAME = ShamelaAuthorityResolver.ANONYMOUS_AUTHORITY_NAME;

    private static final String SHAMELA_LANGUAGE = "ar";

    private static final Logger log = LoggerFactory.getLogger(ShamelaToLibraryMapper.class);

    private final ShamelaBookDao shamelaBookDao;
    private final BookRepository bookRepository;
    private final ShamelaAuthorityResolver authorityResolver;
    private final ShamelaBookMetadataBuilder metadataBuilder;
    private final ShamelaChapterMapper chapterMapper;
    private final ShamelaPageMapper pageMapper;
    private final ShamelaBibliographyParser bibliographyParser;
    private final MuhaqqiqRepository muhaqqiqRepository;
    private final PublisherRepository publisherRepository;
    private final PublicationPlaceRepository publicationPlaceRepository;

    public ShamelaToLibraryMapper(ShamelaBookDao shamelaBookDao,
                                  BookRepository bookRepository,
                                  ShamelaAuthorityResolver authorityResolver,
                                  ShamelaBookMetadataBuilder metadataBuilder,
                                  ShamelaChapterMapper chapterMapper,
                                  ShamelaPageMapper pageMapper,
                                  ShamelaBibliographyParser bibliographyParser,
                                  MuhaqqiqRepository muhaqqiqRepository,
                                  PublisherRepository publisherRepository,
                                  PublicationPlaceRepository publicationPlaceRepository) {
        this.shamelaBookDao = shamelaBookDao;
        this.bookRepository = bookRepository;
        this.authorityResolver = authorityResolver;
        this.metadataBuilder = metadataBuilder;
        this.chapterMapper = chapterMapper;
        this.pageMapper = pageMapper;
        this.bibliographyParser = bibliographyParser;
        this.muhaqqiqRepository = muhaqqiqRepository;
        this.publisherRepository = publisherRepository;
        this.publicationPlaceRepository = publicationPlaceRepository;
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

        UUID authorityId = authorityResolver.resolve(shamelaBook.authorId());
        ParsedBibliography parsedBiblio = bibliographyParser.parse(shamelaBook.bibliography());
        UUID muhaqqiqId = parsedBiblio.muhaqqiq() == null
                ? null : muhaqqiqRepository.findOrCreate(parsedBiblio.muhaqqiq());
        UUID publisherId = parsedBiblio.publisher() == null
                ? null : publisherRepository.findOrCreate(parsedBiblio.publisher());
        UUID publicationPlaceId = parsedBiblio.publicationPlace() == null
                ? null : publicationPlaceRepository.findOrCreate(parsedBiblio.publicationPlace());

        Instant now = Instant.now();
        UUID bookUuid = UUID.randomUUID();
        Book book = new Book(
                bookUuid,
                BookType.BOOK,
                titleOrPlaceholder(shamelaBook.name()),
                authorityId,
                SHAMELA_LANGUAGE,
                blankToNull(shamelaBook.bibliography()),
                metadataBuilder.build(shamelaBook),
                createdBy,
                now,
                now,
                muhaqqiqId,
                publisherId,
                publicationPlaceId,
                parsedBiblio.editionNumber(),
                parsedBiblio.publishedYearHijri(),
                parsedBiblio.publishedYearGregorian(),
                ru.basnukaev.argumentmap.library.domain.BookVisibility.PUBLIC
        );
        bookRepository.save(book);

        int chaptersCount = chapterMapper.mapChapters(bookUuid, shamelaBookId, now);
        int pagesCount = pageMapper.mapPages(bookUuid, shamelaBookId, now);

        log.info("shamela mapped: shamelaBookId={} -> lib_books={} chapters={} pages={} authority={}",
                shamelaBookId, bookUuid, chaptersCount, pagesCount, authorityId);
        return MappedBookResult.freshlyCreated(bookUuid, shamelaBookId, authorityId,
                chaptersCount, pagesCount);
    }

    private static String titleOrPlaceholder(String raw) {
        if (raw == null || raw.isBlank()) {
            return "(без названия)";
        }
        return raw.trim();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
