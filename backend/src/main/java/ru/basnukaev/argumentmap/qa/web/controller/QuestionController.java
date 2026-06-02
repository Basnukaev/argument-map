package ru.basnukaev.argumentmap.qa.web.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.domain.VoteStats;
import ru.basnukaev.argumentmap.qa.domain.Question;
import ru.basnukaev.argumentmap.qa.domain.QuestionStatus;
import ru.basnukaev.argumentmap.qa.repository.QuestionVoteRepository;
import ru.basnukaev.argumentmap.qa.service.QuestionService;
import ru.basnukaev.argumentmap.qa.web.dto.CreateQuestionRequest;
import ru.basnukaev.argumentmap.qa.web.dto.QuestionResponse;
import ru.basnukaev.argumentmap.qa.web.dto.UpdateQuestionRequest;
import ru.basnukaev.argumentmap.web.CurrentUser;
import ru.basnukaev.argumentmap.web.dto.PageRequest;
import ru.basnukaev.argumentmap.web.dto.PagedResponse;

/**
 * REST endpoint для Q&amp;A вопросов (Этап 19.a, ADR-032).
 *
 * <p>Только POST требует {@code X-User-Id} (для заполнения {@code asked_by}).
 * GET/PATCH/DELETE без auth на MVP - до появления authorization будут
 * mutating операции открыты. Спецификация в api-contract.md.
 */
@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController {

    private final QuestionService service;
    private final QuestionVoteRepository questionVoteRepository;

    public QuestionController(QuestionService service,
                             QuestionVoteRepository questionVoteRepository) {
        this.service = service;
        this.questionVoteRepository = questionVoteRepository;
    }

    @PostMapping
    public ResponseEntity<QuestionResponse> create(
            @Valid @RequestBody CreateQuestionRequest request,
            @CurrentUser UUID currentUserId) {
        // Vision 49d Phase A.5: REST вход — role-aware overload (STUDENT+)
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        Question created = service.createQuestion(request.title(), request.body(), currentUserId, role);
        // свежий вопрос не имеет голосов - voteScore=0, userVote=null
        return ResponseEntity
                .created(URI.create("/api/v1/questions/" + created.id()))
                .body(toResponse(created, 0, null));
    }

    /**
     * Пагинированный список вопросов (Этап pagination). Status/q фильтры
     * существовали; добавлены ?page=&size= с PagedResponse wrapper.
     */
    @GetMapping
    public PagedResponse<QuestionResponse> list(
            @RequestParam(name = "status", required = false) QuestionStatus status,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "sort", required = false) String sort) {
        PageRequest pr = PageRequest.from(page, size);
        // Vision 49d Section 2.1: sort whitelist (recent/popular/alphabetical)
        List<Question> items = service.listQuestionsPage(status, query, pr.size(), pr.offset(), sort);
        long total = service.countQuestions(status, query);
        // Bulk-load голосов: 2 SQL на всю страницу, не N+1. questions это open
        // discussion - GET без обязательного auth, userVote резолвим опционально
        // (null если anonymous). voteScore = upvotes-downvotes
        UUID currentUserId = SecurityContextUtils.currentUserIdOrNull();
        List<UUID> questionIds = items.stream().map(Question::id).toList();
        Map<UUID, VoteStats> statsByQuestion = questionVoteRepository.getStatsForQuestions(questionIds);
        Map<UUID, Integer> userVotesByQuestion =
                questionVoteRepository.getUserVotesForQuestions(questionIds, currentUserId);
        List<QuestionResponse> mapped = items.stream()
                .map(q -> toResponse(q,
                        statsByQuestion.getOrDefault(q.id(), VoteStats.EMPTY).score(),
                        userVotesByQuestion.get(q.id())))
                .toList();
        return PagedResponse.of(mapped, pr.page(), pr.size(), total);
    }

    /** Vision 49d Phase 2 - POST view increment endpoint */
    @PostMapping("/{questionId}/views")
    public ResponseEntity<Void> incrementView(@PathVariable UUID questionId) {
        service.incrementViewCount(questionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{questionId}")
    public QuestionResponse getOne(@PathVariable UUID questionId) {
        Question q = service.getQuestion(questionId);
        // point-load голосов. questions это open discussion - GET без auth,
        // userVote опционален (null если anonymous либо не голосовал)
        UUID currentUserId = SecurityContextUtils.currentUserIdOrNull();
        int score = questionVoteRepository.getStatsForQuestion(questionId).score();
        Integer userVote = questionVoteRepository.getUserVote(questionId, currentUserId).orElse(null);
        return toResponse(q, score, userVote);
    }

    @PatchMapping("/{questionId}")
    public QuestionResponse update(
            @PathVariable UUID questionId,
            @Valid @RequestBody UpdateQuestionRequest request,
            @CurrentUser UUID currentUserId) {
        // ADR-043 Amendment (Этап 22.c): only author or admin
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        Question updated = service.updateQuestion(
                questionId, request.title(), request.body(), request.status(),
                currentUserId, role);
        // PATCH-ответ не несёт vote-данных (mutating endpoint) - default 0/null
        return toResponse(updated, 0, null);
    }

    @DeleteMapping("/{questionId}")
    public ResponseEntity<Void> delete(@PathVariable UUID questionId,
                                       @CurrentUser UUID currentUserId) {
        // ADR-043 Amendment (Этап 22.c): only author or admin
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        service.deleteQuestion(questionId, currentUserId, role);
        return ResponseEntity.noContent().build();
    }

    private static QuestionResponse toResponse(Question q, int voteScore, Integer userVote) {
        return new QuestionResponse(
                q.id(),
                q.title(),
                q.body(),
                q.status(),
                q.askedBy(),
                q.acceptedAnswerId(),
                q.createdAt(),
                q.updatedAt(),
                voteScore,
                userVote
        );
    }
}
