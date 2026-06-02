package ru.basnukaev.argumentmap.qa.web.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.domain.VoteStats;
import ru.basnukaev.argumentmap.qa.domain.QuestionVote;
import ru.basnukaev.argumentmap.qa.service.QuestionVoteService;
import ru.basnukaev.argumentmap.qa.web.dto.CreateQuestionVoteRequest;
import ru.basnukaev.argumentmap.qa.web.dto.QuestionVoteStatsResponse;
import ru.basnukaev.argumentmap.web.CurrentUser;

/**
 * REST endpoint'ы для голосования за вопросы Q&amp;A (community-сигнал
 * популярности за вопрос&amp;ответ).
 *
 * <ul>
 *   <li>POST /api/v1/questions/{id}/vote - upsert голоса (тело {weight: ±1})
 *   <li>DELETE /api/v1/questions/{id}/vote - снять голос (идемпотентен)
 *   <li>GET /api/v1/questions/{id}/votes - агрегированная статистика + userVote
 * </ul>
 *
 * <p>POST/DELETE требуют authenticated user ({@code @CurrentUser} бросает 401
 * если principal отсутствует). GET открыт - questions это open discussion,
 * агрегаты видны всем; userVote резолвится опционально через
 * {@code currentUserIdOrNull} (null для anonymous).
 */
@RestController
@RequestMapping("/api/v1/questions/{questionId}")
public class QuestionVoteController {

    private final QuestionVoteService questionVoteService;

    public QuestionVoteController(QuestionVoteService questionVoteService) {
        this.questionVoteService = questionVoteService;
    }

    @PostMapping("/vote")
    public ResponseEntity<QuestionVoteStatsResponse> vote(@PathVariable UUID questionId,
                                                          @Valid @RequestBody CreateQuestionVoteRequest request,
                                                          @CurrentUser UUID userId) {
        QuestionVote saved = questionVoteService.vote(questionId, userId, request.weight());
        VoteStats stats = questionVoteService.getStats(questionId);
        QuestionVoteStatsResponse body = new QuestionVoteStatsResponse(
                questionId, stats.upvotes(), stats.downvotes(), stats.score(), saved.weight()
        );
        return ResponseEntity
                .created(URI.create("/api/v1/questions/" + questionId + "/votes"))
                .body(body);
    }

    @DeleteMapping("/vote")
    public ResponseEntity<Void> removeVote(@PathVariable UUID questionId,
                                           @CurrentUser UUID userId) {
        questionVoteService.removeVote(questionId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/votes")
    public QuestionVoteStatsResponse getStats(@PathVariable UUID questionId) {
        VoteStats stats = questionVoteService.getStats(questionId);
        // questions это open discussion - GET без обязательного auth. userVote
        // резолвим опционально (null если anonymous или не голосовал)
        UUID userId = SecurityContextUtils.currentUserIdOrNull();
        Integer userVote = questionVoteService.getUserVote(questionId, userId).orElse(null);
        return new QuestionVoteStatsResponse(
                questionId, stats.upvotes(), stats.downvotes(), stats.score(), userVote
        );
    }
}
