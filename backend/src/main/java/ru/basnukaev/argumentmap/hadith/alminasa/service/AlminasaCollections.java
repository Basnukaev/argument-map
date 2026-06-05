package ru.basnukaev.argumentmap.hadith.alminasa.service;

import java.util.Map;
import java.util.Optional;

/**
 * Статическая карта 12 сборников alminasa: {@code book_id → (slug, nameAr, nameRu)}.
 * Источник — таблица плана 3 (live-зонд С56). {@code nameAr} здесь — фолбэк:
 * при создании коллекции приоритетнее {@code book_name} из самого дока (он
 * авторитетнее карты), карта используется только когда {@code book_name == null}.
 */
public final class AlminasaCollections {

    /** Сборник: стабильный slug + арабское/русское название (slug ≤ 50 — лимит схемы). */
    public record CollectionInfo(String slug, String nameAr, String nameRu) {
    }

    private static final Map<Integer, CollectionInfo> BY_BOOK_ID = Map.ofEntries(
            Map.entry(19, new CollectionInfo("muwatta", "موطأ مالك", "Муватта Малика")),
            Map.entry(121, new CollectionInfo("ahmad", "مسند أحمد بن حنبل", "Муснад Ахмада")),
            Map.entry(137, new CollectionInfo("darimi", "سنن الدارمي", "Сунан ад-Дарими")),
            Map.entry(146, new CollectionInfo("bukhari", "صحيح البخاري", "Сахих аль-Бухари")),
            Map.entry(158, new CollectionInfo("muslim", "صحيح مسلم", "Сахих Муслима")),
            Map.entry(173, new CollectionInfo("ibn-majah", "سنن ابن ماجه", "Сунан Ибн Маджи")),
            Map.entry(184, new CollectionInfo("abu-dawud", "سنن أبي داود", "Сунан Абу Дауда")),
            Map.entry(195, new CollectionInfo("tirmidhi", "جامع الترمذي", "Джами ат-Тирмизи")),
            Map.entry(319, new CollectionInfo("nasai", "سنن النسائي الصغرى", "Сунан ан-Насаи")),
            Map.entry(345, new CollectionInfo("ibn-khuzaymah", "صحيح ابن خزيمة", "Сахих Ибн Хузаймы")),
            Map.entry(454, new CollectionInfo("ibn-hibban", "صحيح ابن حبان", "Сахих Ибн Хиббана")),
            Map.entry(594, new CollectionInfo("mustadrak", "المستدرك على الصحيحين", "Мустадрак аль-Хакима"))
    );

    private AlminasaCollections() {
    }

    /** Метаданные сборника по {@code book_id}; пусто для неизвестного id. */
    public static Optional<CollectionInfo> byBookId(int bookId) {
        return Optional.ofNullable(BY_BOOK_ID.get(bookId));
    }

    /**
     * Метаданные сборника по alminasa external_id (формат {@code bookId-…},
     * напр. {@code 146-1}): префикс до первого дефиса → int bookId → карта.
     * Непарсится / нет дефиса / неизвестный bookId → пусто. Единая точка
     * резолва префикса для rulings/crossrefs (не дублировать парсинг).
     */
    public static Optional<CollectionInfo> byExternalId(String externalId) {
        if (externalId == null) {
            return Optional.empty();
        }
        int dash = externalId.indexOf('-');
        if (dash <= 0) {
            return Optional.empty();
        }
        try {
            return byBookId(Integer.parseInt(externalId.substring(0, dash)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Все 12 сборников {@code book_id → метаданные}, отсортированы по
     * {@code book_id} (детерминированный порядок строк каталога, план 5).
     */
    public static Map<Integer, CollectionInfo> all() {
        return new java.util.TreeMap<>(BY_BOOK_ID);
    }
}
