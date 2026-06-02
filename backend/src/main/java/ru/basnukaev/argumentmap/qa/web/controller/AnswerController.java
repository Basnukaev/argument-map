package ru.basnukaev.argumentmap.qa.web.controller;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.qa.domain.Answer;
import ru.basnukaev.argumentmap.qa.domain.Question;
import ru.basnukaev.argumentmap.qa.service.AnswerService;
import ru.basnukaev.argumentmap.qa.service.QuestionService;
import ru.basnukaev.argumentmap.qa.web.dto.AnswerResponse;
import ru.basnukaev.argumentmap.qa.web.dto.CreateAnswerRequest;
import ru.basnukaev.argumentmap.qa.web.dto.QuestionResponse;
import ru.basnukaev.argumentmap.qa.web.dto.UpdateAnswerRequest;
import ru.basnukaev.argumentmap.web.CurrentUser;

/**
 * REST endpoint для ответов в Q&amp;A (Этап 19.c, ADR-034).
 *
 * <p>URLs nested под {@code /questions/{questionId}/answers} для list/create
 * (понятна owning сущность). Update/delete + accept/revoke - под /answers
 * (либо /questions/{questionId}/accepted-answer) - симметричный паттерн
 * с {@code QuestionCitationController}.
 *
 * <p>X-User-Id обязателен только на POST (для author_id). Mutating ops
 * без auth на MVP - до появления Spring Security в Этапе 6.
 */
@RestController
@RequestMapping("/api/v1")
public class AnswerController {

    private final AnswerService answerService;
    private final QuestionService questionService;

    public AnswerController(AnswerService answerService, QuestionService questionService) {
        this.answerService = answerService;
        this.questionService = questionService;
    }

    @PostMapping("/questions/{questionId}/answers")
    public ResponseEntity<AnswerResponse> create(
            @PathVariable UUID questionId,
            @Valid @RequestBody CreateAnswerRequest request,
            @CurrentUser UUID currentUserId) {
        // Vision 49d Phase A.5: REST вход — role-aware overload (STUDENT+)
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        Answer created = answerService.createAnswer(questionId, request.body(), currentUserId, role);
        Question parent = questionService.getQuestion(questionId);
        return ResponseEntity
                .created(URI.create("/api/v1/answers/" + created.id()))
                .body(toResponse(created, parent.acceptedAnswerId()));
    }

    @GetMapping("/questions/{questionId}/answers")
    public List<AnswerResponse> list(@PathVariable UUID questionId) {
        // getAnswersForQuestion внутри сделает 404 если question отсутствует
        List<Answer> answers = answerService.getAnswersForQuestion(questionId);
        Question parent = questionService.getQuestion(questionId);
        UUID acceptedId = parent.acceptedAnswerId();
        return answers.stream()
                .map(a -> toResponse(a, acceptedId))
                .toList();
    }

    @PatchMapping("/answers/{answerId}")
    public AnswerResponse update(
            @PathVariable UUID answerId,
            @Valid @RequestBody UpdateAnswerRequest request,
            @CurrentUser UUID currentUserId) {
        // ADR-043 Amendment (Этап 22.c): only author or admin
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        Answer updated = answerService.updateAnswer(answerId, request.body(),
                currentUserId, role);
        Question parent = questionService.getQuestion(updated.questionId());
        return toResponse(updated, parent.acceptedAnswerId());
    }

    @DeleteMapping("/answers/{answerId}")
    public ResponseEntity<Void> delete(@PathVariable UUID answerId,
                                       @CurrentUser UUID currentUserId) {
        // ADR-043 Amendment (Этап 22.c): only author or admin
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        answerService.deleteAnswer(answerId, currentUserId, role);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/questions/{questionId}/accepted-answer/{answerId}")
    public QuestionResponse acceptAnswer(
            @PathVariable UUID questionId,
            @PathVariable UUID answerId,
            @CurrentUser UUID currentUserId) {
        // ADR-043 Amendment (Q&A guards): accept мутирует вопрос - только
        // автор вопроса или ADMIN
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        Question updated = answerService.acceptAnswer(questionId, answerId,
                currentUserId, role);
        return toQuestionResponse(updated);
    }

    @DeleteMapping("/questions/{questionId}/accepted-answer")
    public QuestionResponse revokeAcceptance(@PathVariable UUID questionId,
                                             @CurrentUser UUID currentUserId) {
        // ADR-043 Amendment (Q&A guards): revoke мутирует вопрос - только
        // автор вопроса или ADMIN
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        Question updated = answerService.revokeAcceptance(questionId,
                currentUserId, role);
        return toQuestionResponse(updated);
    }

    private static AnswerResponse toResponse(Answer a, UUID acceptedAnswerId) {
        return new AnswerResponse(
                a.id(),
                a.questionId(),
                a.body(),
                a.authorId(),
                a.createdAt(),
                a.updatedAt(),
                Objects.equals(a.id(), acceptedAnswerId)
        );
    }

    private static QuestionResponse toQuestionResponse(Question q) {
        // accept/revoke-answer это mutating endpoint - vote-данные не несём
        // (default 0/null). Полные voteScore/userVote отдают GET list/detail
        return new QuestionResponse(
                q.id(),
                q.title(),
                q.body(),
                q.status(),
                q.askedBy(),
                q.acceptedAnswerId(),
                q.createdAt(),
                q.updatedAt(),
                0,
                null
        );
    }
}
