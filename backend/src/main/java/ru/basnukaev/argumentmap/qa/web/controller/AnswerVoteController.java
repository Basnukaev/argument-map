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
import ru.basnukaev.argumentmap.qa.domain.AnswerVote;
import ru.basnukaev.argumentmap.qa.service.AnswerVoteService;
import ru.basnukaev.argumentmap.qa.web.dto.AnswerVoteStatsResponse;
import ru.basnukaev.argumentmap.qa.web.dto.CreateAnswerVoteRequest;
import ru.basnukaev.argumentmap.web.CurrentUser;

/**
 * REST endpoint'ы для голосования за отдельные ответы Q&amp;A
 * (community-сигнал качества конкретного ответа).
 *
 * <ul>
 *   <li>POST /api/v1/answers/{id}/vote - upsert голоса (тело {weight: ±1})
 *   <li>DELETE /api/v1/answers/{id}/vote - снять голос (идемпотентен)
 *   <li>GET /api/v1/answers/{id}/vote - агрегированная статистика + userVote
 * </ul>
 *
 * <p>POST/DELETE требуют authenticated user ({@code @CurrentUser} бросает 401
 * если principal отсутствует). GET открыт - answers это open discussion,
 * агрегаты видны всем; userVote резолвится опционально через
 * {@code currentUserIdOrNull} (null для anonymous).
 *
 * <p>Зеркалит {@link QuestionVoteController} но на уровне ответов.
 */
@RestController
@RequestMapping("/api/v1/answers/{answerId}")
public class AnswerVoteController {

    private final AnswerVoteService answerVoteService;

    public AnswerVoteController(AnswerVoteService answerVoteService) {
        this.answerVoteService = answerVoteService;
    }

    @PostMapping("/vote")
    public ResponseEntity<AnswerVoteStatsResponse> vote(@PathVariable UUID answerId,
                                                        @Valid @RequestBody CreateAnswerVoteRequest request,
                                                        @CurrentUser UUID userId) {
        AnswerVote saved = answerVoteService.vote(answerId, userId, request.weight());
        VoteStats stats = answerVoteService.getStats(answerId);
        AnswerVoteStatsResponse body = new AnswerVoteStatsResponse(
                answerId, stats.upvotes(), stats.downvotes(), stats.score(), saved.weight()
        );
        return ResponseEntity
                .created(URI.create("/api/v1/answers/" + answerId + "/vote"))
                .body(body);
    }

    @DeleteMapping("/vote")
    public ResponseEntity<Void> removeVote(@PathVariable UUID answerId,
                                           @CurrentUser UUID userId) {
        answerVoteService.removeVote(answerId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/vote")
    public AnswerVoteStatsResponse getStats(@PathVariable UUID answerId) {
        VoteStats stats = answerVoteService.getStats(answerId);
        // answers это open discussion - GET без обязательного auth. userVote
        // резолвим опционально (null если anonymous или не голосовал)
        UUID userId = SecurityContextUtils.currentUserIdOrNull();
        Integer userVote = answerVoteService.getUserVote(answerId, userId).orElse(null);
        return new AnswerVoteStatsResponse(
                answerId, stats.upvotes(), stats.downvotes(), stats.score(), userVote
        );
    }
}
