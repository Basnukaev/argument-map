package ru.basnukaev.argumentmap.hadith.web;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.web.dto.HadithResponse;
import ru.basnukaev.argumentmap.web.dto.PageRequest;
import ru.basnukaev.argumentmap.web.dto.PagedResponse;

/**
 * REST endpoints для самих хадисов. Vision 49d Section 2.6 Phase 1.f.
 *
 * <p>Phase 1.f: simple list + single GET. Phase 1.g: bundled detail
 * (hadith + sanads + narrators + matns в одном payload).
 */
@RestController
@RequestMapping("/api/v1/hadith/hadiths")
public class HadithController {

    private final HadithRepository hadithRepository;

    public HadithController(HadithRepository hadithRepository) {
        this.hadithRepository = hadithRepository;
    }

    @GetMapping
    public PagedResponse<HadithResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID bookId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        PageRequest pr = PageRequest.from(page, size);
        List<HadithResponse> items = hadithRepository
                .findPage(q, status, bookId, pr.size(), pr.offset())
                .stream()
                .map(HadithController::toResponse)
                .toList();
        long total = hadithRepository.countFiltered(q, status, bookId);
        return PagedResponse.of(items, pr.page(), pr.size(), total);
    }

    @GetMapping("/{id}")
    public HadithResponse getOne(@PathVariable UUID id) {
        Hadith h = hadithRepository.findById(id)
                .orElseThrow(() -> new HadithNotFoundException(id));
        return toResponse(h);
    }

    private static HadithResponse toResponse(Hadith h) {
        return new HadithResponse(
                h.id(), h.primaryBookId(), h.primaryNumber(),
                h.normalizedMatn(), h.status(), h.sourceId(), h.createdAt()
        );
    }
}
