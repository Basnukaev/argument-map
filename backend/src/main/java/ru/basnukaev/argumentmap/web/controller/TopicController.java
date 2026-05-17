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
import ru.basnukaev.argumentmap.domain.Topic;
import ru.basnukaev.argumentmap.service.GraphService;
import ru.basnukaev.argumentmap.service.PermissionService;
import ru.basnukaev.argumentmap.service.TopicService;
import ru.basnukaev.argumentmap.web.CurrentUser;
import ru.basnukaev.argumentmap.web.dto.CreateTopicRequest;
import ru.basnukaev.argumentmap.web.dto.GraphResponse;
import ru.basnukaev.argumentmap.web.dto.TopicResponse;
import ru.basnukaev.argumentmap.web.dto.UpdateTopicVisibilityRequest;
import ru.basnukaev.argumentmap.web.mapper.DtoMappers;

@RestController
@RequestMapping("/api/v1/topics")
public class TopicController {

    private final TopicService topicService;
    private final GraphService graphService;
    private final PermissionService permissionService;

    public TopicController(TopicService topicService, GraphService graphService,
                           PermissionService permissionService) {
        this.topicService = topicService;
        this.graphService = graphService;
        this.permissionService = permissionService;
    }

    @PostMapping
    public ResponseEntity<TopicResponse> create(@Valid @RequestBody CreateTopicRequest request,
                                                @CurrentUser UUID userId) {
        Topic created = topicService.createTopic(
                request.title(), request.description(),
                request.rootQuestion(), request.visibility(), userId
        );
        // Дополнительный SQL за актуальными nodeCount/edgeCount после create -
        // ответ должен честно отражать состояние темы (rootQuestion = 1 узел)
        TopicResponse body = DtoMappers.toResponse(topicService.getTopicWithCounts(created.id()));
        return ResponseEntity.created(URI.create("/api/v1/topics/" + created.id())).body(body);
    }

    @GetMapping
    public List<TopicResponse> list(@CurrentUser UUID userId) {
        // ADR-043: только видимые user'у темы (PRIVATE owned + SHARED member + PUBLIC).
        // ADMIN получает все темы - bypass в TopicService.
        String role = SecurityContextUtils.currentRole();
        return topicService.listVisibleTopicsWithCounts(userId, role).stream()
                .map(DtoMappers::toResponse).toList();
    }

    @GetMapping("/{topicId}")
    public TopicResponse getOne(@PathVariable UUID topicId, @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRole();
        permissionService.assertCanRead(topicId, userId, role);
        return DtoMappers.toResponse(topicService.getTopicWithCounts(topicId));
    }

    @DeleteMapping("/{topicId}")
    public ResponseEntity<Void> delete(@PathVariable UUID topicId, @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRole();
        topicService.deleteTopic(topicId, userId, role);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{topicId}/graph")
    public GraphResponse getGraph(@PathVariable UUID topicId, @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRole();
        permissionService.assertCanRead(topicId, userId, role);
        return DtoMappers.toResponse(graphService.getGraph(topicId));
    }

    @PatchMapping("/{topicId}/visibility")
    public TopicResponse updateVisibility(@PathVariable UUID topicId,
                                          @Valid @RequestBody UpdateTopicVisibilityRequest request,
                                          @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRole();
        Topic updated = topicService.updateVisibility(topicId, request.visibility(), userId, role);
        return DtoMappers.toResponse(updated);
    }
}
