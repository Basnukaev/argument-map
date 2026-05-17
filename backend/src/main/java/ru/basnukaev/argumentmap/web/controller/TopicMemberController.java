package ru.basnukaev.argumentmap.web.controller;

import java.net.URI;
import java.util.List;
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
import ru.basnukaev.argumentmap.domain.TopicMember;
import ru.basnukaev.argumentmap.service.TopicMemberService;
import ru.basnukaev.argumentmap.web.CurrentUser;
import ru.basnukaev.argumentmap.web.dto.AddTopicMemberRequest;
import ru.basnukaev.argumentmap.web.dto.TopicMemberResponse;
import ru.basnukaev.argumentmap.web.dto.UpdateTopicMemberRequest;
import ru.basnukaev.argumentmap.web.mapper.DtoMappers;

/**
 * Управление членами SHARED-тем (ADR-043). Endpoint'ы:
 * <ul>
 *   <li>POST /api/v1/topics/{id}/members - добавить (owner)
 *   <li>GET /api/v1/topics/{id}/members - список (read access к теме)
 *   <li>PATCH /api/v1/topics/{id}/members/{memberId} - сменить роль (owner)
 *   <li>DELETE /api/v1/topics/{id}/members/{memberId} - удалить (owner или self-leave)
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/topics/{topicId}/members")
public class TopicMemberController {

    private final TopicMemberService topicMemberService;

    public TopicMemberController(TopicMemberService topicMemberService) {
        this.topicMemberService = topicMemberService;
    }

    @PostMapping
    public ResponseEntity<TopicMemberResponse> add(@PathVariable UUID topicId,
                                                   @Valid @RequestBody AddTopicMemberRequest request,
                                                   @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRole();
        TopicMember added = topicMemberService.addMember(
                topicId, request.userId(), request.role(), userId, role
        );
        return ResponseEntity
                .created(URI.create("/api/v1/topics/" + topicId + "/members/" + added.id()))
                .body(DtoMappers.toResponse(added));
    }

    @GetMapping
    public List<TopicMemberResponse> list(@PathVariable UUID topicId,
                                          @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRole();
        return topicMemberService.listMembers(topicId, userId, role).stream()
                .map(DtoMappers::toResponse).toList();
    }

    @PatchMapping("/{memberId}")
    public TopicMemberResponse update(@PathVariable UUID topicId,
                                      @PathVariable UUID memberId,
                                      @Valid @RequestBody UpdateTopicMemberRequest request,
                                      @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRole();
        TopicMember updated = topicMemberService.updateMemberRole(
                topicId, memberId, request.role(), userId, role
        );
        return DtoMappers.toResponse(updated);
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> delete(@PathVariable UUID topicId,
                                       @PathVariable UUID memberId,
                                       @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRole();
        topicMemberService.removeMember(topicId, memberId, userId, role);
        return ResponseEntity.noContent().build();
    }
}
