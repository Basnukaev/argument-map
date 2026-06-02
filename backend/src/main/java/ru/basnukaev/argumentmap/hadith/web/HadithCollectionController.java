package ru.basnukaev.argumentmap.hadith.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.hadith.domain.Collection;
import ru.basnukaev.argumentmap.hadith.repository.CollectionRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.web.dto.CollectionResponse;

/**
 * REST для сборников хадисов ({@code hd_collections}) — под-проект #1:
 * chip-фильтр + превью. Под-проект #3: мост к библиотечному представлению
 * (поле {@code bookId} в ответе + обратный lookup {@code GET /by-book/{bookId}}).
 */
@RestController
@RequestMapping("/api/v1/hadith/collections")
public class HadithCollectionController {

    private final CollectionRepository collectionRepository;
    private final HadithRepository hadithRepository;

    public HadithCollectionController(CollectionRepository collectionRepository,
                                      HadithRepository hadithRepository) {
        this.collectionRepository = collectionRepository;
        this.hadithRepository = hadithRepository;
    }

    /** Все сборники + реальное число импортированных хадисов в каждом. */
    @GetMapping
    public List<CollectionResponse> list() {
        Map<UUID, Long> counts = hadithRepository.countByCollectionGrouped();
        return collectionRepository.findAll().stream()
                .map(c -> toResponse(c, counts.getOrDefault(c.id(), 0L)))
                .toList();
    }

    /**
     * Обратный lookup моста (под-проект #3): по id книги-представления
     * ({@code lib_books.id}) вернуть соответствующий сборник хадисов. Фронт
     * вызывает из BookReader чтобы дать ссылку «открыть в иснад-графе».
     *
     * <p>Выбран отдельный endpoint вместо расширения {@code BookDetailResponse}
     * (lower-risk: не трогает контракт книги и его многочисленные IT).
     *
     * @throws CollectionNotFoundException 404 если книга не является
     *         представлением сборника
     */
    @GetMapping("/by-book/{bookId}")
    public CollectionResponse byBook(@PathVariable UUID bookId) {
        Collection c = collectionRepository.findByBookId(bookId)
                .orElseThrow(() -> new CollectionNotFoundException(bookId));
        long count = hadithRepository.countByCollectionGrouped()
                .getOrDefault(c.id(), 0L);
        return toResponse(c, count);
    }

    private static CollectionResponse toResponse(Collection c, long hadithCount) {
        return new CollectionResponse(
                c.id(), c.slug(), c.nameAr(), c.nameEn(), c.nameRu(),
                c.totalHadith(), hadithCount, c.bookId());
    }
}
