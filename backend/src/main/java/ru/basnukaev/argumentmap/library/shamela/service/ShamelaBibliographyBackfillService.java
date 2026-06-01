package ru.basnukaev.argumentmap.library.shamela.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.MuhaqqiqRepository;
import ru.basnukaev.argumentmap.library.repository.PublicationPlaceRepository;
import ru.basnukaev.argumentmap.library.repository.PublisherRepository;
import ru.basnukaev.argumentmap.library.shamela.service.mapper.ParsedBibliography;
import ru.basnukaev.argumentmap.library.shamela.service.mapper.ShamelaBibliographyParser;

/**
 * Bulk-backfill academic metadata для книг которые были замаплены
 * из shamela **до** появления {@link ShamelaBibliographyParser}
 * (Этап 20.c). Запускается через
 * {@code POST /api/v1/admin/shamela/backfill-bibliography}.
 *
 * <p>Поведение - **non-destructive merge**: если парсер вернул значение
 * для поля, FK обновляется через {@code findOrCreate}; если парсер
 * не нашёл marker (поле {@code null} в {@link ParsedBibliography}),
 * существующее значение сохраняется. Это защищает от случайного
 * стирания admin-отредактированных полей (Этап 20.d).
 *
 * <p>Книги без shamela source (manually created через REST API) и книги
 * с blank/null {@code description} пропускаются: первые - не purview
 * shamela parser'а, вторые - parser вернёт {@link ParsedBibliography#empty()}.
 */
@Service
public class ShamelaBibliographyBackfillService {

    private static final Logger log = LoggerFactory.getLogger(
            ShamelaBibliographyBackfillService.class);

    private final BookRepository bookRepository;
    private final ShamelaBibliographyParser bibliographyParser;
    private final MuhaqqiqRepository muhaqqiqRepository;
    private final PublisherRepository publisherRepository;
    private final PublicationPlaceRepository publicationPlaceRepository;

    public ShamelaBibliographyBackfillService(BookRepository bookRepository,
                                              ShamelaBibliographyParser bibliographyParser,
                                              MuhaqqiqRepository muhaqqiqRepository,
                                              PublisherRepository publisherRepository,
                                              PublicationPlaceRepository publicationPlaceRepository) {
        this.bookRepository = bookRepository;
        this.bibliographyParser = bibliographyParser;
        this.muhaqqiqRepository = muhaqqiqRepository;
        this.publisherRepository = publisherRepository;
        this.publicationPlaceRepository = publicationPlaceRepository;
    }

    /**
     * Прогнать parser по всем shamela-sourced книгам.
     *
     * <p>Транзакция per-book: одна книга крашится - остальные продолжают
     * (через выход на уровень controller'а без rollback'а previous updates).
     * На MVP запускается админом руками, минимальный объём (десятки книг
     * на dev, тысячи в проде после bulk-import).
     */
    public BackfillResult backfillAll() {
        List<Book> books = bookRepository.findAllShamelaSourced();
        int scanned = 0;
        int updated = 0;
        int skipped = 0;
        for (Book book : books) {
            scanned++;
            try {
                if (backfillOne(book)) {
                    updated++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException e) {
                skipped++;
                log.warn("backfill failed for book {}: {}", book.id(), e.getMessage());
            }
        }
        log.info("bibliography backfill: scanned={} updated={} skipped={}", scanned, updated, skipped);
        return new BackfillResult(scanned, updated, skipped);
    }

    /**
     * Обновить одну книгу. Возвращает {@code true} если parser нашёл
     * хотя бы одно поле и был выполнен UPDATE. {@code false} - если
     * description пустой или parser ничего не выловил.
     */
    @Transactional
    public boolean backfillOne(Book book) {
        ParsedBibliography parsed = bibliographyParser.parse(book.description());
        if (parsed.isEmpty()) {
            return false;
        }

        UUID muhaqqiqId = parsed.muhaqqiq() != null
                ? muhaqqiqRepository.findOrCreate(parsed.muhaqqiq())
                : book.muhaqqiqId();
        UUID publisherId = parsed.publisher() != null
                ? publisherRepository.findOrCreate(parsed.publisher())
                : book.publisherId();
        UUID publicationPlaceId = parsed.publicationPlace() != null
                ? publicationPlaceRepository.findOrCreate(parsed.publicationPlace())
                : book.publicationPlaceId();
        Integer editionNumber = parsed.editionNumber() != null
                ? parsed.editionNumber() : book.editionNumber();
        Integer yearHijri = parsed.publishedYearHijri() != null
                ? parsed.publishedYearHijri() : book.publishedYearHijri();
        Integer yearGregorian = parsed.publishedYearGregorian() != null
                ? parsed.publishedYearGregorian() : book.publishedYearGregorian();

        boolean updated = bookRepository.updateAcademicMetadata(
                book.id(),
                muhaqqiqId,
                publisherId,
                publicationPlaceId,
                editionNumber,
                yearHijri,
                yearGregorian
        );

        // Thesis-поля (миграция 58) - заполняем если парсер их выловил
        // (академические рисала). Merge: parser-значение либо существующее.
        if (parsed.thesisDegree() != null || parsed.thesisSupervisor() != null
                || parsed.thesisInstitution() != null) {
            bookRepository.updateThesisMetadata(
                    book.id(),
                    parsed.thesisDegree() != null ? parsed.thesisDegree() : book.thesisDegree(),
                    parsed.thesisSupervisor() != null ? parsed.thesisSupervisor() : book.thesisSupervisor(),
                    parsed.thesisInstitution() != null ? parsed.thesisInstitution() : book.thesisInstitution()
            );
            updated = true;
        }
        return updated;
    }

    public record BackfillResult(int scanned, int updated, int skipped) {
    }
}
