package ru.basnukaev.argumentmap.qa.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.service.PermissionService;
import ru.basnukaev.argumentmap.domain.AuditEntityType;
import ru.basnukaev.argumentmap.exception.AnswerNotFoundException;
import ru.basnukaev.argumentmap.exception.AnswerWriteAccessDeniedException;
import ru.basnukaev.argumentmap.exception.QuestionNotFoundException;
import ru.basnukaev.argumentmap.exception.QuestionWriteAccessDeniedException;
import ru.basnukaev.argumentmap.qa.domain.Answer;
import ru.basnukaev.argumentmap.qa.domain.Question;
import ru.basnukaev.argumentmap.qa.repository.AnswerRepository;
import ru.basnukaev.argumentmap.qa.repository.QuestionRepository;
import ru.basnukaev.argumentmap.service.AuditLogService;

/**
 * Сервисный слой ответов в Q&amp;A приложении (Этап 19.c, ADR-034).
 */
@Service
public class AnswerService {

    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final AuditLogService auditLogService;
    private final PermissionService permissionService;

    public AnswerService(AnswerRepository answerRepository, QuestionRepository questionRepository,
                         AuditLogService auditLogService, PermissionService permissionService) {
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
        this.auditLogService = auditLogService;
        this.permissionService = permissionService;
    }

    /**
     * Vision 49d Phase A.5: role-aware createAnswer. Требует STUDENT+.
     * Primary entry из REST controller. USER role → 403.
     */
    @Transactional
    public Answer createAnswer(UUID questionId, String body, UUID authorId, String role) {
        permissionService.assertHasRoleAtLeast(authorId, role, UserRole.STUDENT);
        return createAnswer(questionId, body, authorId);
    }

    /**
     * Legacy overload без role-check. Internal callers + IT.
     */
    @Transactional
    public Answer createAnswer(UUID questionId, String body, UUID authorId) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body обязателен и не должен быть пустым");
        }
        // Pre-check существования question - чистый 404 вместо FK violation
        questionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException(questionId));
        Instant now = Instant.now();
        Answer a = new Answer(
                UUID.randomUUID(),
                questionId,
                body.trim(),
                authorId,
                now,
                now
        );
        answerRepository.save(a);

        // ADR-043 Amendment 3 (22.d) - audit CREATE с parent=QUESTION
        Map<String, Object> snapshot = AuditLogService.snapshot()
                .put("body", a.body())
                .build();
        auditLogService.logCreate(AuditEntityType.ANSWER, a.id(),
                AuditEntityType.QUESTION, questionId, authorId, snapshot);

        return a;
    }

    /**
     * Список ответов на вопрос. Сортировка - принятый ответ первым,
     * остальные по {@code created_at}. Если у вопроса нет принятого ответа,
     * сортировка по {@code created_at}.
     */
    @Transactional(readOnly = true)
    public List<Answer> getAnswersForQuestion(UUID questionId) {
        Question q = questionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException(questionId));
        return answerRepository.findByQuestionIdSortedByAccepted(questionId, q.acceptedAnswerId());
    }

    /**
     * Backward-compat: без author check, оставлено для internal callers
     * (миграционные/тестовые сценарии). REST endpoint должен использовать
     * {@link #updateAnswer(UUID, String, UUID, String)}.
     */
    @Transactional
    public Answer updateAnswer(UUID answerId, String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body обязателен и не должен быть пустым");
        }
        answerRepository.findById(answerId)
                .orElseThrow(() -> new AnswerNotFoundException(answerId));
        answerRepository.update(answerId, body.trim());
        return answerRepository.findById(answerId).orElseThrow();
    }

    /**
     * Обновление ответа с author/admin guard (ADR-043 Amendment, Этап 22.c).
     * Только автор ответа или ADMIN могут редактировать.
     *
     * @throws AnswerWriteAccessDeniedException если не автор и не ADMIN (403)
     */
    @Transactional
    public Answer updateAnswer(UUID answerId, String body, UUID actorUserId, String actorRole) {
        assertAuthorOrAdmin(answerId, actorUserId, actorRole);
        Answer before = answerRepository.findById(answerId).orElseThrow();
        Answer after = updateAnswer(answerId, body);

        // ADR-043 Amendment 3 (22.d) - audit UPDATE body diff
        Map<String, AuditLogService.FieldDiff> diff = AuditLogService.diff()
                .compare("body", before.body(), after.body())
                .build();
        if (!diff.isEmpty()) {
            auditLogService.logUpdate(AuditEntityType.ANSWER, answerId,
                    AuditEntityType.QUESTION, before.questionId(),
                    actorUserId, diff);
        }
        return after;
    }

    @Transactional
    public void deleteAnswer(UUID answerId) {
        boolean removed = answerRepository.deleteById(answerId);
        if (!removed) {
            throw new AnswerNotFoundException(answerId);
        }
    }

    /**
     * Удаление ответа с author/admin guard (ADR-043 Amendment, Этап 22.c).
     * Только автор ответа или ADMIN могут удалить.
     *
     * @throws AnswerWriteAccessDeniedException если не автор и не ADMIN (403)
     */
    @Transactional
    public void deleteAnswer(UUID answerId, UUID actorUserId, String actorRole) {
        assertAuthorOrAdmin(answerId, actorUserId, actorRole);
        Answer existing = answerRepository.findById(answerId).orElseThrow();

        // ADR-043 Amendment 3 (22.d) - audit DELETE с parent=QUESTION
        Map<String, Object> snapshot = AuditLogService.snapshot()
                .put("body", existing.body())
                .build();
        auditLogService.logDelete(AuditEntityType.ANSWER, answerId,
                AuditEntityType.QUESTION, existing.questionId(),
                actorUserId, snapshot);

        // Если удаляемый ответ был принятым - сбрасываем lifecycle вопроса.
        // FK accepted_answer_id ON DELETE SET NULL обнулит ссылку, но
        // questions.status остаётся 'ANSWERED' → вопрос завис в ANSWERED
        // без accepted answer. revokeAcceptedAnswer ставит status=OPEN.
        resetQuestionStatusIfAcceptedAnswerDeleted(existing);

        deleteAnswer(answerId);
    }

    /**
     * Если переданный ответ является принятым ответом своего вопроса -
     * снимает acceptance (status → OPEN) ПЕРЕД удалением. Иначе вопрос
     * остался бы в неконсистентном состоянии ANSWERED + accepted_answer_id
     * = NULL (через FK ON DELETE SET NULL).
     */
    private void resetQuestionStatusIfAcceptedAnswerDeleted(Answer answer) {
        questionRepository.findById(answer.questionId()).ifPresent(question -> {
            if (answer.id().equals(question.acceptedAnswerId())) {
                questionRepository.revokeAcceptedAnswer(answer.questionId());
            }
        });
    }

    private void assertAuthorOrAdmin(UUID answerId, UUID actorUserId, String actorRole) {
        Answer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new AnswerNotFoundException(answerId));
        if (UserRole.ADMIN.equals(actorRole)) {
            return;
        }
        if (!answer.authorId().equals(actorUserId)) {
            throw new AnswerWriteAccessDeniedException(answerId, actorUserId);
        }
    }

    /**
     * Backward-compat: без author check, оставлено для internal callers
     * и IT. REST endpoint должен использовать
     * {@link #acceptAnswer(UUID, UUID, UUID, String)}.
     *
     * <p>Принять ответ как accepted. Обновляет {@code questions.accepted_answer_id}
     * и переводит status в {@code ANSWERED}. Проверяет что answer принадлежит
     * указанному question - иначе {@code IllegalArgumentException} (→ 400).
     *
     * @return обновлённый Question
     */
    @Transactional
    public Question acceptAnswer(UUID questionId, UUID answerId) {
        questionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException(questionId));
        Answer a = answerRepository.findById(answerId)
                .orElseThrow(() -> new AnswerNotFoundException(answerId));
        if (!a.questionId().equals(questionId)) {
            throw new IllegalArgumentException(
                    "Ответ " + answerId + " не принадлежит вопросу " + questionId);
        }
        questionRepository.setAcceptedAnswer(questionId, answerId);
        return questionRepository.findById(questionId).orElseThrow();
    }

    /**
     * Принять ответ с author/admin guard (ADR-043 Amendment, Q&amp;A guards).
     * Accept мутирует родительский вопрос (accepted_answer_id + status=
     * ANSWERED), поэтому доступно только автору вопроса или ADMIN -
     * симметрично updateQuestion/deleteQuestion. Без этого любой
     * authenticated user мог принять ответ на чужой вопрос.
     *
     * @throws QuestionWriteAccessDeniedException если не автор вопроса и не ADMIN (403)
     */
    @Transactional
    public Question acceptAnswer(UUID questionId, UUID answerId,
                                 UUID actorUserId, String actorRole) {
        assertQuestionAuthorOrAdmin(questionId, actorUserId, actorRole);
        return acceptAnswer(questionId, answerId);
    }

    /**
     * Backward-compat: без author check. REST endpoint должен
     * использовать {@link #revokeAcceptance(UUID, UUID, String)}.
     *
     * <p>Снять принятие ответа: {@code accepted_answer_id = NULL} + status = OPEN.
     */
    @Transactional
    public Question revokeAcceptance(UUID questionId) {
        questionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException(questionId));
        questionRepository.revokeAcceptedAnswer(questionId);
        return questionRepository.findById(questionId).orElseThrow();
    }

    /**
     * Снять принятие с author/admin guard (ADR-043 Amendment). Как и
     * acceptAnswer - мутация родительского вопроса, только автор или ADMIN.
     *
     * @throws QuestionWriteAccessDeniedException если не автор вопроса и не ADMIN (403)
     */
    @Transactional
    public Question revokeAcceptance(UUID questionId, UUID actorUserId, String actorRole) {
        assertQuestionAuthorOrAdmin(questionId, actorUserId, actorRole);
        return revokeAcceptance(questionId);
    }

    /**
     * Guard: actor должен быть автором вопроса (asked_by) либо ADMIN.
     * Зеркалит {@code QuestionService.assertAuthorOrAdmin} - accept/revoke
     * концептуально мутируют вопрос, не ответ.
     */
    private void assertQuestionAuthorOrAdmin(UUID questionId, UUID actorUserId, String actorRole) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException(questionId));
        if (UserRole.ADMIN.equals(actorRole)) {
            return;
        }
        if (!question.askedBy().equals(actorUserId)) {
            throw new QuestionWriteAccessDeniedException(questionId, actorUserId);
        }
    }
}
