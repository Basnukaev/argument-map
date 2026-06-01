package ru.basnukaev.argumentmap.hadith.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.hadith.repository.CollectionRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.web.dto.CollectionResponse;

/**
 * REST для сборников хадисов ({@code hd_collections}) — под-проект #1:
 * chip-фильтр + превью. Под-проект #3 (примирение с библиотечным «Сборник
 * хадисов») пока не трогаем.
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
                .map(c -> new CollectionResponse(
                        c.id(), c.slug(), c.nameAr(), c.nameEn(), c.nameRu(),
                        c.totalHadith(), counts.getOrDefault(c.id(), 0L)))
                .toList();
    }
}
