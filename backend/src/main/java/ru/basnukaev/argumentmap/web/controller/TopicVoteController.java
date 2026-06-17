package ru.basnukaev.argumentmap.web.controller;

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
import ru.basnukaev.argumentmap.domain.TopicVote;
import ru.basnukaev.argumentmap.domain.VoteStats;
import ru.basnukaev.argumentmap.service.TopicVoteService;
import ru.basnukaev.argumentmap.web.CurrentUser;
import ru.basnukaev.argumentmap.web.dto.CreateTopicVoteRequest;
import ru.basnukaev.argumentmap.web.dto.TopicVoteStatsResponse;

/**
 * REST endpoint'ы для голосования за темы (community-сигнал популярности,
 * ADR-053).
 *
 * <ul>
 *   <li>POST /api/v1/topics/{id}/vote - upsert голоса (тело {weight: ±1})
 *   <li>DELETE /api/v1/topics/{id}/vote - снять голос (идемпотентен)
 *   <li>GET /api/v1/topics/{id}/votes - агрегированная статистика + userVote
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/topics/{topicId}")
public class TopicVoteController {

    private final TopicVoteService topicVoteService;

    public TopicVoteController(TopicVoteService topicVoteService) {
        this.topicVoteService = topicVoteService;
    }

    @PostMapping("/vote")
    public ResponseEntity<TopicVoteStatsResponse> vote(@PathVariable UUID topicId,
                                                       @Valid @RequestBody CreateTopicVoteRequest request,
                                                       @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        TopicVote saved = topicVoteService.vote(topicId, userId, request.weight(), role);
        VoteStats stats = topicVoteService.getStats(topicId, userId, role);
        TopicVoteStatsResponse body = new TopicVoteStatsResponse(
                topicId, stats.upvotes(), stats.downvotes(), stats.score(), saved.weight()
        );
        return ResponseEntity
                .created(URI.create("/api/v1/topics/" + topicId + "/votes"))
                .body(body);
    }

    @DeleteMapping("/vote")
    public ResponseEntity<Void> removeVote(@PathVariable UUID topicId,
                                           @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        topicVoteService.removeVote(topicId, userId, role);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/votes")
    public TopicVoteStatsResponse getStats(@PathVariable UUID topicId) {
        // Guest view (roadmap 49.G): GET под permitAll. userId из
        // SecurityContext (null если аноним), не @CurrentUser (тот бросает
        // 401). read-guard ниже отдаёт агрегаты только при доступе к теме -
        // аноним видит голоса PUBLIC тем, PRIVATE/SHARED → 403. userVote=null
        // для анонима (getUserVote(null) → empty).
        UUID userId = SecurityContextUtils.currentUserIdOrNull();
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        // read-guard: отдаём агрегаты только если есть доступ к теме - иначе
        // утечка голосов приватных тем
        VoteStats stats = topicVoteService.getStats(topicId, userId, role);
        Integer userVote = topicVoteService.getUserVote(topicId, userId).orElse(null);
        return new TopicVoteStatsResponse(
                topicId, stats.upvotes(), stats.downvotes(), stats.score(), userVote
        );
    }
}
