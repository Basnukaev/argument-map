package ru.basnukaev.argumentmap.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.domain.Authority;
import ru.basnukaev.argumentmap.domain.AuthorityType;
import ru.basnukaev.argumentmap.domain.HadithGrade;
import ru.basnukaev.argumentmap.domain.HadithGradeValue;
import ru.basnukaev.argumentmap.domain.HadithGradeWithScholar;
import ru.basnukaev.argumentmap.domain.Source;
import ru.basnukaev.argumentmap.domain.SourceType;
import ru.basnukaev.argumentmap.exception.AuthorityNotFoundException;
import ru.basnukaev.argumentmap.exception.HadithGradeAccessDeniedException;
import ru.basnukaev.argumentmap.exception.HadithGradeDuplicateException;
import ru.basnukaev.argumentmap.exception.HadithGradeNotFoundException;
import ru.basnukaev.argumentmap.exception.InvalidHadithGradeException;
import ru.basnukaev.argumentmap.exception.InvalidScholarAuthorityException;
import ru.basnukaev.argumentmap.exception.SourceNotFoundException;
import ru.basnukaev.argumentmap.repository.AuthorityRepository;
import ru.basnukaev.argumentmap.repository.HadithGradeRepository;
import ru.basnukaev.argumentmap.repository.SourceRepository;

/**
 * Бизнес-логика multi-grading хадисов.
 *
 * <p>Контракт permission:
 * <ul>
 *   <li>add - любой authenticated user. Scholar - reference в authorities,
 *       не сам user. Это эквивалентно «занести в каталог факт что Бухари
 *       поставил SAHIH», авторство grade'а в системе - createdBy.</li>
 *   <li>update / delete - только createdBy либо ADMIN
 *       ({@link HadithGradeAccessDeniedException} → 403).</li>
 * </ul>
 *
 * <p>Бизнес-правила:
 * <ul>
 *   <li>Grade можно добавить только на source с {@code sourceType=HADITH}.
 *       Попытка grade'нуть BOOK / QURAN / ARTICLE → 400 invalid-hadith-grade.</li>
 *   <li>Один scholar - одна оценка для одного source. Повторная попытка →
 *       409 hadith-grade-duplicate (caller должен использовать PATCH).</li>
 * </ul>
 */
@Service
public class HadithGradeService {

    private final HadithGradeRepository hadithGradeRepository;
    private final SourceRepository sourceRepository;
    private final AuthorityRepository authorityRepository;
    private final PermissionService permissionService;

    public HadithGradeService(HadithGradeRepository hadithGradeRepository,
                              SourceRepository sourceRepository,
                              AuthorityRepository authorityRepository,
                              PermissionService permissionService) {
        this.hadithGradeRepository = hadithGradeRepository;
        this.sourceRepository = sourceRepository;
        this.authorityRepository = authorityRepository;
        this.permissionService = permissionService;
    }

    /**
     * Role-aware overload (Vision 49d Section 2.4): требует роль
     * SCHOLAR+. После Phase A.3 — primary entry point из REST controller.
     * Бросает {@link ru.basnukaev.argumentmap.exception.InsufficientRoleException}
     * для USER/STUDENT.
     */
    @Transactional
    public HadithGrade addGrade(UUID sourceId, UUID scholarId,
                                HadithGradeValue grade,
                                String gradeCitation, String comment,
                                UUID actorUserId, String actorRole) {
        permissionService.assertHasRoleAtLeast(actorUserId, actorRole, UserRole.SCHOLAR);
        return addGrade(sourceId, scholarId, grade, gradeCitation, comment, actorUserId);
    }

    /**
     * Legacy overload без role-check — оставлен для internal callers
     * (ETL/import/scheduled jobs) и existing IT тестов. Production REST
     * traffic идёт через role-aware overload выше.
     */
    @Transactional
    public HadithGrade addGrade(UUID sourceId, UUID scholarId,
                                HadithGradeValue grade,
                                String gradeCitation, String comment,
                                UUID actorUserId) {
        Source source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new SourceNotFoundException(sourceId));
        // user-facing валидация: оценку можно поставить только хадису
        // (см. ADR-43 / hadith-grades). Здесь programming error vs user
        // input разделены - не type-mismatch (IllegalStateException), а
        // пользовательская ошибка → InvalidHadithGradeException → 400
        if (!source.isHadith()) {
            throw new InvalidHadithGradeException(
                    "Оценку можно добавить только на хадис (sourceType=HADITH), получено: "
                            + source.sourceType()
            );
        }
        if (grade == null) {
            throw new InvalidHadithGradeException("Поле grade обязательно");
        }
        Authority scholar = authorityRepository.findById(scholarId)
                .orElseThrow(() -> new AuthorityNotFoundException(scholarId));
        // Семантическая валидация: оценивать хадис может только учёный
        // (muhaddith), не издательство, не тахкик и не «прочие». До
        // миграции 47 authorities был flat namespace, валидации не было
        if (!AuthorityType.SCHOLAR.equals(scholar.type())) {
            throw new InvalidScholarAuthorityException(scholarId, scholar.type());
        }
        if (hadithGradeRepository.existsForSourceAndScholar(sourceId, scholarId)) {
            throw new HadithGradeDuplicateException(sourceId, scholarId);
        }

        HadithGrade entity = new HadithGrade(
                UUID.randomUUID(), sourceId, scholarId, grade,
                gradeCitation, comment, Instant.now(), actorUserId
        );
        try {
            return hadithGradeRepository.save(entity);
        } catch (DuplicateKeyException dup) {
            // race-condition между existsForSourceAndScholar и save -
            // unique-индекс защищает, превращаем в семантическое 409
            throw new HadithGradeDuplicateException(sourceId, scholarId);
        }
    }

    @Transactional
    public HadithGrade updateGrade(UUID gradeId,
                                   HadithGradeValue newGrade,
                                   String gradeCitation, String comment,
                                   UUID actorUserId, String actorRole) {
        HadithGrade existing = hadithGradeRepository.findById(gradeId)
                .orElseThrow(() -> new HadithGradeNotFoundException(gradeId));
        assertCanModify(existing, actorUserId, actorRole);

        if (newGrade == null) {
            throw new InvalidHadithGradeException("Поле grade обязательно при update");
        }
        // Re-validate scholar type на каждый update. Сценарий: при addGrade
        // scholar был SCHOLAR, потом ADMIN изменил его type (через прямой SQL
        // либо future AuthorityService.updateAuthority) на PUBLISHER/MUHAQQIQ.
        // Stale grade row остался валидным с точки зрения CHECK constraint
        // (scholarId FK не проверяет type), но семантически broken - оценка
        // хадиса от не-учёного. Здесь "lazy fix" - при первом update'е
        // блокируем дальнейшие изменения, требуя fix scholar.type через
        // ADMIN либо ручное удаление grade
        Authority scholar = authorityRepository.findById(existing.scholarId())
                .orElseThrow(() -> new AuthorityNotFoundException(existing.scholarId()));
        if (!AuthorityType.SCHOLAR.equals(scholar.type())) {
            throw new InvalidScholarAuthorityException(existing.scholarId(), scholar.type());
        }

        boolean updated = hadithGradeRepository.update(gradeId, newGrade, gradeCitation, comment);
        if (!updated) {
            // только что был existing - значит row пропал между select и update
            // (concurrent delete). Не race в нашей системе обычно, но честно
            throw new HadithGradeNotFoundException(gradeId);
        }
        return new HadithGrade(
                existing.id(), existing.sourceId(), existing.scholarId(), newGrade,
                gradeCitation, comment, existing.createdAt(), existing.createdBy()
        );
    }

    @Transactional
    public void removeGrade(UUID gradeId, UUID actorUserId, String actorRole) {
        HadithGrade existing = hadithGradeRepository.findById(gradeId)
                .orElseThrow(() -> new HadithGradeNotFoundException(gradeId));
        assertCanModify(existing, actorUserId, actorRole);
        hadithGradeRepository.deleteById(gradeId);
    }

    @Transactional(readOnly = true)
    public List<HadithGradeWithScholar> listForSource(UUID sourceId) {
        if (sourceRepository.findById(sourceId).isEmpty()) {
            throw new SourceNotFoundException(sourceId);
        }
        return hadithGradeRepository.findBySourceIdWithScholar(sourceId);
    }

    private static void assertCanModify(HadithGrade grade, UUID actorUserId, String actorRole) {
        if (UserRole.ADMIN.equals(actorRole)) {
            return;
        }
        if (grade.createdBy().equals(actorUserId)) {
            return;
        }
        throw new HadithGradeAccessDeniedException(grade.id(), actorUserId);
    }
}
