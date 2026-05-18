package ru.basnukaev.argumentmap.qa.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.domain.AuditEntityType;
import ru.basnukaev.argumentmap.exception.AnswerNotFoundException;
import ru.basnukaev.argumentmap.exception.AnswerWriteAccessDeniedException;
import ru.basnukaev.argumentmap.exception.QuestionNotFoundException;
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

    public AnswerService(AnswerRepository answerRepository, QuestionRepository questionRepository,
                         AuditLogService auditLogService) {
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
        this.auditLogService = auditLogService;
    }

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
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("body", a.body());
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
        if (!Objects.equals(before.body(), after.body())) {
            Map<String, AuditLogService.FieldDiff> diff = new LinkedHashMap<>();
            diff.put("body", new AuditLogService.FieldDiff(before.body(), after.body()));
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
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("body", existing.body());
        auditLogService.logDelete(AuditEntityType.ANSWER, answerId,
                AuditEntityType.QUESTION, existing.questionId(),
                actorUserId, snapshot);

        deleteAnswer(answerId);
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
     * Принять ответ как accepted. Обновляет {@code questions.accepted_answer_id}
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
     * Снять принятие ответа: {@code accepted_answer_id = NULL} + status = OPEN.
     */
    @Transactional
    public Question revokeAcceptance(UUID questionId) {
        questionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException(questionId));
        questionRepository.revokeAcceptedAnswer(questionId);
        return questionRepository.findById(questionId).orElseThrow();
    }
}
