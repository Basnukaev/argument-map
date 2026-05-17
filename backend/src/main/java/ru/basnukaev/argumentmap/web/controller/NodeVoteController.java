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
import ru.basnukaev.argumentmap.domain.NodeVote;
import ru.basnukaev.argumentmap.domain.VoteStats;
import ru.basnukaev.argumentmap.service.NodeVoteService;
import ru.basnukaev.argumentmap.web.CurrentUser;
import ru.basnukaev.argumentmap.web.dto.CreateNodeVoteRequest;
import ru.basnukaev.argumentmap.web.dto.NodeVoteStatsResponse;

/**
 * REST endpoint'ы для голосования за вес аргументов.
 *
 * <ul>
 *   <li>POST /api/v1/nodes/{id}/vote - upsert голоса (тело {weight: ±1})
 *   <li>DELETE /api/v1/nodes/{id}/vote - снять голос (идемпотентен)
 *   <li>GET /api/v1/nodes/{id}/votes - агрегированная статистика + userVote
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/nodes/{nodeId}")
public class NodeVoteController {

    private final NodeVoteService nodeVoteService;

    public NodeVoteController(NodeVoteService nodeVoteService) {
        this.nodeVoteService = nodeVoteService;
    }

    @PostMapping("/vote")
    public ResponseEntity<NodeVoteStatsResponse> vote(@PathVariable UUID nodeId,
                                                      @Valid @RequestBody CreateNodeVoteRequest request,
                                                      @CurrentUser UUID userId) {
        NodeVote saved = nodeVoteService.vote(nodeId, userId, request.weight());
        VoteStats stats = nodeVoteService.getStatsForNode(nodeId);
        NodeVoteStatsResponse body = new NodeVoteStatsResponse(
                nodeId, stats.upvotes(), stats.downvotes(), stats.score(), saved.weight()
        );
        return ResponseEntity
                .created(URI.create("/api/v1/nodes/" + nodeId + "/votes"))
                .body(body);
    }

    @DeleteMapping("/vote")
    public ResponseEntity<Void> removeVote(@PathVariable UUID nodeId,
                                           @CurrentUser UUID userId) {
        nodeVoteService.removeVote(nodeId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/votes")
    public NodeVoteStatsResponse getStats(@PathVariable UUID nodeId,
                                          @CurrentUser UUID userId) {
        VoteStats stats = nodeVoteService.getStatsForNode(nodeId);
        Integer userVote = nodeVoteService.getUserVote(nodeId, userId).orElse(null);
        return new NodeVoteStatsResponse(
                nodeId, stats.upvotes(), stats.downvotes(), stats.score(), userVote
        );
    }
}
