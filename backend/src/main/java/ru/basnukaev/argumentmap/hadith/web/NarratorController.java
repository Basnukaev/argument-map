package ru.basnukaev.argumentmap.hadith.web;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.hadith.curation.domain.OverrideEntity;
import ru.basnukaev.argumentmap.hadith.curation.service.OverrideApplyService;
import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.NarratorCommentary;
import ru.basnukaev.argumentmap.hadith.domain.NarratorRelation;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorCommentaryRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRelationRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRepository;
import ru.basnukaev.argumentmap.hadith.web.dto.HadithResponse;
import ru.basnukaev.argumentmap.hadith.web.dto.NarratorCommentaryDto;
import ru.basnukaev.argumentmap.hadith.web.dto.NarratorRelationDto;
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
    private final HadithRepository hadithRepository;
    private final NarratorRelationRepository relationRepository;
    private final NarratorCommentaryRepository commentaryRepository;
    private final OverrideApplyService overrideApply;

    public NarratorController(NarratorRepository narratorRepository,
                              HadithRepository hadithRepository,
                              NarratorRelationRepository relationRepository,
                              NarratorCommentaryRepository commentaryRepository,
                              OverrideApplyService overrideApply) {
        this.narratorRepository = narratorRepository;
        this.hadithRepository = hadithRepository;
        this.relationRepository = relationRepository;
        this.commentaryRepository = commentaryRepository;
        this.overrideApply = overrideApply;
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
        // list-путь: relations/commentaries не строим (null) — без N+1; сеть
        // передатчиков и цитаты учёных приходят только в getOne (detail).
        List<NarratorResponse> items = narratorRepository
                .findPage(q, reliability, pr.size(), pr.offset())
                .stream()
                .map(n -> toResponse(n, null, null))
                .toList();
        long total = narratorRepository.countFiltered(q, reliability);
        return PagedResponse.of(items, pr.page(), pr.size(), total);
    }

    @GetMapping("/{id}")
    public NarratorResponse getOne(@PathVariable UUID id) {
        Narrator n = narratorRepository.findById(id)
                .orElseThrow(() -> new NarratorNotFoundException(id));
        // detail-путь: сеть передатчиков (top_students/top_scholars) — один запрос.
        List<NarratorRelationDto> relations = relationRepository.findByNarratorId(id).stream()
                .map(NarratorController::toRelationDto)
                .toList();
        // detail-путь: джарх/таʿдиль-цитаты учёных о рави (ADR-061) — один запрос.
        // Курация (ADR-065 §4.3): record-hidden цитата заблудшего критика
        // читателю не отдаётся; ADMIN видит с hiddenByAdmin+причиной (reveal).
        boolean reveal = UserRole.ADMIN.equals(SecurityContextUtils.currentRoleOrAnonymous());
        List<NarratorCommentaryDto> commentaries = overrideApply.applyRecordHide(
                OverrideEntity.HD_NARRATOR_COMMENTARIES, commentaryRepository.findByNarratorId(id),
                NarratorCommentary::id, reveal, NarratorController::toCommentaryDto);
        return toResponse(n, relations, commentaries);
    }

    /**
     * Phase 2 (علم الرجال): хадисы, в иснадах которых встречается этот
     * передатчик. Paginated — у плодовитых сподвижников (Абу Хурайра)
     * счёт идёт на тысячи. 404 если narrator'а нет.
     */
    @GetMapping("/{id}/transmitted")
    public PagedResponse<HadithResponse> transmitted(
            @PathVariable UUID id,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        narratorRepository.findById(id)
                .orElseThrow(() -> new NarratorNotFoundException(id));
        PageRequest pr = PageRequest.from(page, size);
        List<HadithResponse> items = hadithRepository
                .findByNarratorIdPage(id, pr.size(), pr.offset())
                .stream()
                .map(HadithResponse::from)
                .toList();
        long total = hadithRepository.countByNarratorId(id);
        return PagedResponse.of(items, pr.page(), pr.size(), total);
    }

    private static NarratorResponse toResponse(Narrator n, List<NarratorRelationDto> relations,
                                               List<NarratorCommentaryDto> commentaries) {
        return new NarratorResponse(
                n.id(), n.authorityId(), n.nameAr(), n.kunya(), n.laqab(),
                n.yearBirthHijri(), n.yearDeathHijri(), n.birthplace(),
                n.primaryResidence(), n.reliabilityGrade(), n.reliabilityComment(),
                n.transmittedCountCached(), n.createdAt(),
                n.tabaqa(), n.gradeText(), n.bornOnText(), n.diedOnText(),
                n.deathPlace(), relations, commentaries
        );
    }

    private static NarratorRelationDto toRelationDto(NarratorRelation r) {
        return new NarratorRelationDto(
                r.relatedNarratorId(), r.relatedName(), r.role(), r.cnt());
    }

    private static NarratorCommentaryDto toCommentaryDto(NarratorCommentary c,
                                                         boolean hiddenByAdmin, String hideReason) {
        return new NarratorCommentaryDto(
                c.id(), c.commenter(), c.commenterDeathYear(), c.bookName(), c.author(),
                c.page(), c.volume(), c.comments(), hiddenByAdmin, hideReason);
    }
}
