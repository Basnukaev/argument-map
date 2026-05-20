package ru.basnukaev.argumentmap.hadith.web;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRepository;
import ru.basnukaev.argumentmap.hadith.web.dto.NarratorResponse;
import ru.basnukaev.argumentmap.web.dto.PageRequest;
import ru.basnukaev.argumentmap.web.dto.PagedResponse;

/**
 * REST endpoints для Hadith Explorer narrators. Vision 49d Section
 * 2.6 Phase 1.b.
 *
 * <p>Phase 1: read-only (GET). Mutations (POST/PATCH/DELETE) - Phase
 * 2 после ETL pipeline для bulk import из sunnah.com/islamhouse.
 */
@RestController
@RequestMapping("/api/v1/hadith/narrators")
public class NarratorController {

    private final NarratorRepository narratorRepository;

    public NarratorController(NarratorRepository narratorRepository) {
        this.narratorRepository = narratorRepository;
    }

    /**
     * Paginated listing с search + filter by reliability.
     * Open для всех (read-only для guest view 2.5).
     */
    @GetMapping
    public PagedResponse<NarratorResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String reliability,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        PageRequest pr = PageRequest.from(page, size);
        List<NarratorResponse> items = narratorRepository
                .findPage(q, reliability, pr.size(), pr.offset())
                .stream()
                .map(NarratorController::toResponse)
                .toList();
        long total = narratorRepository.countFiltered(q, reliability);
        return PagedResponse.of(items, pr.page(), pr.size(), total);
    }

    @GetMapping("/{id}")
    public NarratorResponse getOne(@PathVariable UUID id) {
        Narrator n = narratorRepository.findById(id)
                .orElseThrow(() -> new NarratorNotFoundException(id));
        return toResponse(n);
    }

    private static NarratorResponse toResponse(Narrator n) {
        return new NarratorResponse(
                n.id(), n.authorityId(), n.nameAr(), n.kunya(), n.laqab(),
                n.yearBirthHijri(), n.yearDeathHijri(), n.birthplace(),
                n.primaryResidence(), n.reliabilityGrade(), n.reliabilityComment(),
                n.transmittedCountCached(), n.createdAt()
        );
    }
}
