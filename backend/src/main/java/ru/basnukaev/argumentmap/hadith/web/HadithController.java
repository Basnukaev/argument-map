package ru.basnukaev.argumentmap.hadith.web;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.Matn;
import ru.basnukaev.argumentmap.hadith.domain.Sanad;
import ru.basnukaev.argumentmap.hadith.domain.SanadNarrator;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.MatnRepository;
import ru.basnukaev.argumentmap.hadith.repository.SanadRepository;
import ru.basnukaev.argumentmap.hadith.web.dto.HadithDetailResponse;
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
    private final SanadRepository sanadRepository;
    private final MatnRepository matnRepository;

    public HadithController(HadithRepository hadithRepository,
                            SanadRepository sanadRepository,
                            MatnRepository matnRepository) {
        this.hadithRepository = hadithRepository;
        this.sanadRepository = sanadRepository;
        this.matnRepository = matnRepository;
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

    /**
     * Phase 1.g bundled detail: hadith + sanads (each с narrators)
     * + matns в одном payload. Primary endpoint для UI sanad graph viz.
     * 1 GET вместо 3+ (N+1 avoidance).
     */
    @GetMapping("/{id}/detail")
    public HadithDetailResponse getDetail(@PathVariable UUID id) {
        Hadith h = hadithRepository.findById(id)
                .orElseThrow(() -> new HadithNotFoundException(id));

        List<Sanad> sanads = sanadRepository.findByHadithId(id);
        List<UUID> sanadIds = sanads.stream().map(Sanad::id).toList();
        // Bulk fetch narrators avoiding N+1
        List<SanadNarrator> allLinks = sanadRepository.findNarratorsBySanadIds(sanadIds);

        List<HadithDetailResponse.SanadDto> sanadDtos = sanads.stream()
                .map(s -> new HadithDetailResponse.SanadDto(
                        s.id(), s.chainGrade(), s.compiledById(),
                        s.compiledInBookId(), s.primaryChain(),
                        allLinks.stream()
                                .filter(l -> l.sanadId().equals(s.id()))
                                .map(l -> new HadithDetailResponse.NarratorLinkDto(
                                        l.position(), l.narratorId(), l.transmissionPhrase()
                                ))
                                .toList()
                ))
                .toList();

        List<Matn> matns = matnRepository.findByHadithId(id);
        List<HadithDetailResponse.MatnDto> matnDtos = matns.stream()
                .map(m -> new HadithDetailResponse.MatnDto(
                        m.id(), m.textAr(), m.textRu(), m.textEn(),
                        m.sourceBookId(), m.printedNumber(), m.pageNo(),
                        m.volume(), m.isPrimary(), m.divergenceSummary()
                ))
                .toList();

        return new HadithDetailResponse(
                h.id(), h.primaryBookId(), h.primaryNumber(),
                h.normalizedMatn(), h.status(), h.sourceId(), h.createdAt(),
                sanadDtos, matnDtos
        );
    }

    private static HadithResponse toResponse(Hadith h) {
        return new HadithResponse(
                h.id(), h.primaryBookId(), h.primaryNumber(),
                h.normalizedMatn(), h.status(), h.sourceId(), h.createdAt()
        );
    }
}
