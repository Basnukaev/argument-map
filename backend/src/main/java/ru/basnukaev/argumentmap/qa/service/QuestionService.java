package ru.basnukaev.argumentmap.qa.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.domain.AuditEntityType;
import ru.basnukaev.argumentmap.exception.QuestionNotFoundException;
import ru.basnukaev.argumentmap.exception.QuestionWriteAccessDeniedException;
import ru.basnukaev.argumentmap.qa.domain.Question;
import ru.basnukaev.argumentmap.qa.domain.QuestionStatus;
import ru.basnukaev.argumentmap.qa.repository.QuestionRepository;
import ru.basnukaev.argumentmap.service.AuditLogService;
import ru.basnukaev.argumentmap.service.PermissionService;

/**
 * Сервисный слой Q&amp;A приложения (Этап 19.a, ADR-032).
 */
@Service
public class QuestionService {

    private final QuestionRepository repository;
    private final AuditLogService auditLogService;
    private final PermissionService permissionService;

    public QuestionService(QuestionRepository repository, AuditLogService auditLogService,
                           PermissionService permissionService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
        this.permissionService = permissionService;
    }

    /**
     * Vision 49d Phase A.5: role-aware createQuestion. Требует STUDENT+.
     * Primary entry point из REST controller. USER role → 403
     * forbidden-insufficient-role.
     */
    @Transactional
    public Question createQuestion(String title, String body, UUID askedBy, String role) {
        permissionService.assertHasRoleAtLeast(askedBy, role, UserRole.STUDENT);
        return createQuestion(title, body, askedBy);
    }

    /**
     * Legacy overload без role-check. Для internal callers (ETL/import/
     * seed) и existing IT тестов которые не проходят через REST.
     */
    @Transactional
    public Question createQuestion(String title, String body, UUID askedBy) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title обязателен и не должен быть пустым");
        }
        Instant now = Instant.now();
        Question q = new Question(
                UUID.randomUUID(),
                title.trim(),
                body == null || body.isBlank() ? null : body.trim(),
                QuestionStatus.OPEN,
                askedBy,
                null,
                now,
                now
        );
        repository.save(q);

        // ADR-043 Amendment 3 (22.d) - audit CREATE
        Map<String, Object> snapshot = AuditLogService.snapshot()
                .put("title", q.title())
                .put("body", q.body())
                .put("status", QuestionStatus.OPEN.name())
                .build();
        auditLogService.logCreate(AuditEntityType.QUESTION, q.id(), null, null,
                askedBy, snapshot);

        return q;
    }

    @Transactional(readOnly = true)
    public List<Question> listQuestions(QuestionStatus status, String query) {
        return repository.findAll(status, query);
    }

    @Transactional(readOnly = true)
    public List<Question> listQuestionsPage(QuestionStatus status, String query,
                                            int limit, int offset) {
        return repository.findPage(status, query, limit, offset);
    }

    @Transactional(readOnly = true)
    public long countQuestions(QuestionStatus status, String query) {
        return repository.countFiltered(status, query);
    }

    @Transactional(readOnly = true)
    public Question getQuestion(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new QuestionNotFoundException(id));
    }

    /**
     * Partial update - title/body/status. null значения = no change в
     * соответствующем поле. Если ничего не передано в request, ничего
     * не меняется кроме {@code updated_at = now()}.
     *
     * <p><b>Семантика пустых строк</b> (code review Сессии 35, Important #2):
     * <ul>
     *   <li>{@code title}: {@code null} = no change. Whitespace-only или
     *       пустая строка {@code ""} → {@code IllegalArgumentException}
     *       (400). Title в Create имеет {@code @NotBlank}, инвариант
     *       сохраняется в PATCH</li>
     *   <li>{@code body}: {@code null} = no change. Пустая или whitespace
     *       строка = clear (body становится {@code null}). Body optional
     *       в Create, поэтому "сбрасывать в null" - валидный сценарий</li>
     * </ul>
     *
     * <p>Backward-compat без author/admin guard - используется internal
     * callers (тесты, миграционные сценарии). REST endpoint должен
     * использовать {@link #updateQuestion(UUID, String, String, QuestionStatus, UUID, String)}.
     */
    @Transactional
    public Question updateQuestion(UUID id, String title, String body, QuestionStatus status) {
        // Pre-check существования - даёт чистый 404 вместо silent no-op
        repository.findById(id).orElseThrow(() -> new QuestionNotFoundException(id));
        String normalizedTitle;
        if (title == null) {
            normalizedTitle = null;
        } else if (title.isBlank()) {
            throw new IllegalArgumentException(
                    "title не может быть пустым - используйте null для no-change");
        } else {
            normalizedTitle = title.trim();
        }
        String normalizedBody;
        if (body == null) {
            normalizedBody = null;
        } else if (body.isBlank()) {
            // Empty/blank body = clear (body nullable в schema)
            normalizedBody = "";
        } else {
            normalizedBody = body.trim();
        }
        repository.update(id, normalizedTitle, normalizedBody, status);
        return repository.findById(id).orElseThrow();
    }

    /**
     * Обновление вопроса с author/admin guard (ADR-043 Amendment, Этап 22.c).
     * Только автор (asked_by) или ADMIN могут редактировать.
     *
     * @throws QuestionWriteAccessDeniedException если не автор и не ADMIN (403)
     */
    @Transactional
    public Question updateQuestion(UUID id, String title, String body, QuestionStatus status,
                                   UUID actorUserId, String actorRole) {
        assertAuthorOrAdmin(id, actorUserId, actorRole);
        Question before = repository.findById(id).orElseThrow();
        Question after = updateQuestion(id, title, body, status);

        // ADR-043 Amendment 3 (22.d) - audit UPDATE с per-field diff
        Map<String, AuditLogService.FieldDiff> diff = AuditLogService.diff()
                .compare("title", before.title(), after.title())
                .compare("body", before.body(), after.body())
                .compare("status",
                        before.status() == null ? null : before.status().name(),
                        after.status() == null ? null : after.status().name())
                .build();
        if (!diff.isEmpty()) {
            auditLogService.logUpdate(AuditEntityType.QUESTION, id, null, null,
                    actorUserId, diff);
        }
        return after;
    }

    @Transactional
    public void deleteQuestion(UUID id) {
        boolean removed = repository.deleteById(id);
        if (!removed) {
            throw new QuestionNotFoundException(id);
        }
    }

    /**
     * Удаление вопроса с author/admin guard (ADR-043 Amendment, Этап 22.c).
     * Только автор (asked_by) или ADMIN могут удалить.
     *
     * @throws QuestionWriteAccessDeniedException если не автор и не ADMIN (403)
     */
    @Transactional
    public void deleteQuestion(UUID id, UUID actorUserId, String actorRole) {
        assertAuthorOrAdmin(id, actorUserId, actorRole);
        Question existing = repository.findById(id).orElseThrow();

        // ADR-043 Amendment 3 (22.d) - audit DELETE до самого delete
        Map<String, Object> snapshot = AuditLogService.snapshot()
                .put("title", existing.title())
                .put("body", existing.body())
                .put("status", existing.status() == null ? null : existing.status().name())
                .build();
        auditLogService.logDelete(AuditEntityType.QUESTION, id, null, null,
                actorUserId, snapshot);

        deleteQuestion(id);
    }

    private void assertAuthorOrAdmin(UUID id, UUID actorUserId, String actorRole) {
        Question question = repository.findById(id)
                .orElseThrow(() -> new QuestionNotFoundException(id));
        if (UserRole.ADMIN.equals(actorRole)) {
            return;
        }
        if (!question.askedBy().equals(actorUserId)) {
            throw new QuestionWriteAccessDeniedException(id, actorUserId);
        }
    }
}
