package ru.basnukaev.argumentmap.hadith.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.hadith.domain.Collection;
import ru.basnukaev.argumentmap.hadith.repository.CollectionRepository;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookContentKind;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.BookVisibility;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.service.BookService;

/**
 * Мост hd_collections ↔ lib_books — два представления одного сборника
 * (под-проект #3). Сборник хадисов (hd_collections: иснад-граф, hd_hadiths)
 * одновременно должен просматриваться как книга библиотеки
 * (lib_books, book_type=HADITH_COLLECTION). Этот сервис лениво создаёт
 * библиотечное представление и проставляет {@code hd_collections.book_id}.
 *
 * <p>Зеркалит ленивый мост {@code HadithCitationService.ensureSourceForHadith}:
 * один lib_books на сборник, идемпотентно (вернуть существующий book_id если
 * выставлен, иначе создать + проставить).
 *
 * <p><b>created_by:</b> sunnah-импорт — системный процесс без актора (в отличие
 * от shamela, который берёт created_by из админа). Владелец книги-представления
 * — фиксированный системный пользователь (миграция 65). visibility=PUBLIC —
 * как у shamela-книг (open library).
 */
@Service
public class BookCollectionBridgeService {

    private static final Logger log = LoggerFactory.getLogger(BookCollectionBridgeService.class);

    /** Системный пользователь — владелец книг-представлений сборников (миграция 65). */
    public static final UUID SYSTEM_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final CollectionRepository collectionRepository;
    private final BookService bookService;
    private final BookRepository bookRepository;

    public BookCollectionBridgeService(CollectionRepository collectionRepository,
                                       BookService bookService,
                                       BookRepository bookRepository) {
        this.collectionRepository = collectionRepository;
        this.bookService = bookService;
        this.bookRepository = bookRepository;
    }

    /**
     * Гарантирует библиотечное представление сборника: вернуть существующий
     * {@code book_id} либо создать lib_books-строку и проставить мост.
     * Идемпотентно.
     *
     * @return id книги-представления в lib_books
     */
    @Transactional
    public UUID ensureLibraryBookForCollection(Collection collection) {
        if (collection.bookId() != null) {
            return collection.bookId();
        }
        Book book = bookService.createBook(
                BookType.HADITH_COLLECTION,
                buildTitle(collection),
                null,
                "ar",
                null,
                null,
                SYSTEM_USER_ID,
                null, null, null, null, null, null,
                BookVisibility.PUBLIC
        );
        // Книга-мост: ни страниц, ни файла — маршрутизируется в /hadith,
        // reader не открывается. TEXT_ONLY явно (совпадает с default).
        bookRepository.updateContentKind(book.id(), BookContentKind.TEXT_ONLY);
        collectionRepository.updateBookId(collection.id(), book.id());
        return book.id();
    }

    /**
     * Non-fatal обёртка для ETL-пути: создание книги-представления не должно
     * ломать импорт хадисов. Любая ошибка логируется warn'ом и проглатывается
     * — мост можно достроить позже (ленивый, идемпотентный).
     */
    public void ensureLibraryBookForCollectionQuietly(Collection collection) {
        try {
            ensureLibraryBookForCollection(collection);
        } catch (RuntimeException e) {
            log.warn("Не удалось создать книгу-представление для сборника {} ({}): {}",
                    collection.slug(), collection.id(), e.getMessage());
        }
    }

    /** Заголовок книги: nameRu → nameAr → slug. */
    private static String buildTitle(Collection c) {
        return firstNonBlank(c.nameRu(), c.nameAr(), c.slug());
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
