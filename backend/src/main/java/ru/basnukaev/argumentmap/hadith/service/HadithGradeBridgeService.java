package ru.basnukaev.argumentmap.hadith.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.domain.HadithGrade;
import ru.basnukaev.argumentmap.domain.HadithGradeValue;
import ru.basnukaev.argumentmap.domain.Source;
import ru.basnukaev.argumentmap.domain.SourceType;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.repository.CollectionRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.web.HadithNotFoundException;
import ru.basnukaev.argumentmap.service.HadithGradeService;
import ru.basnukaev.argumentmap.service.SourceService;

/**
 * Мост alminasa-хадиса ({@code hd_hadiths}) к механизму ручных оценок учёных
 * ({@code hadith_grades}) — ADR-062 Option B.
 *
 * <p>Существующий {@code HadithGradeService.addGrade} пишет оценку по
 * {@code sources.id}, а фронт работает в hadith-домене и оперирует
 * {@code hd_hadiths.id}. Этот сервис лениво гарантирует Source
 * (sourceType=HADITH) для хадиса (паттерн {@code HadithCitationService}) и
 * делегирует в {@code HadithGradeService} — без backfill 32k Source-строк.
 * Резолв source реюзится между под-проектом #2 (citation) и оценками: один
 * Source на хадис.
 */
@Service
public class HadithGradeBridgeService {

    private final HadithRepository hadithRepository;
    private final CollectionRepository collectionRepository;
    private final SourceService sourceService;
    private final HadithGradeService hadithGradeService;

    public HadithGradeBridgeService(HadithRepository hadithRepository,
                                    CollectionRepository collectionRepository,
                                    SourceService sourceService,
                                    HadithGradeService hadithGradeService) {
        this.hadithRepository = hadithRepository;
        this.collectionRepository = collectionRepository;
        this.sourceService = sourceService;
        this.hadithGradeService = hadithGradeService;
    }

    /**
     * Добавляет оценку учёного на хадис по {@code hadithId}. Резолвит/создаёт
     * Source хадиса, затем зовёт role-aware {@code addGrade} (permission
     * SCHOLAR+, enum-валидация, dedup — внутри {@link HadithGradeService}).
     *
     * @throws HadithNotFoundException если хадиса нет (404)
     */
    @Transactional
    public HadithGrade addGradeForHadith(UUID hadithId, UUID scholarId,
                                         HadithGradeValue grade,
                                         String gradeCitation, String comment,
                                         UUID actorUserId, String actorRole) {
        Hadith hadith = hadithRepository.findById(hadithId)
                .orElseThrow(() -> new HadithNotFoundException(hadithId));
        UUID sourceId = ensureSourceForHadith(hadith);
        return hadithGradeService.addGrade(
                sourceId, scholarId, grade, gradeCitation, comment, actorUserId, actorRole);
    }

    /**
     * Один Source на хадис: вернуть существующий либо создать +
     * выставить {@code source_id} (тот же контракт, что
     * {@code HadithCitationService.ensureSourceForHadith}).
     */
    private UUID ensureSourceForHadith(Hadith hadith) {
        if (hadith.sourceId() != null) {
            return hadith.sourceId();
        }
        Source source = sourceService.createSource(
                SourceType.HADITH, buildTitle(hadith), null, null, null, null, null);
        hadithRepository.updateSourceId(hadith.id(), source.id());
        return source.id();
    }

    /** «<Сборник> №<номер>» для title источника; деградирует до «Хадис №N». */
    private String buildTitle(Hadith hadith) {
        String collection = hadith.collectionId() == null ? null
                : collectionRepository.findById(hadith.collectionId())
                        .map(c -> firstNonBlank(c.nameRu(), c.nameAr(), c.slug()))
                        .orElse(null);
        String number = hadith.primaryNumber() != null ? " №" + hadith.primaryNumber() : "";
        return (collection != null ? collection : "Хадис") + number;
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
