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
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.service.NodeService;
import ru.basnukaev.argumentmap.web.CurrentUser;
import ru.basnukaev.argumentmap.web.dto.CreateNodeRequest;
import ru.basnukaev.argumentmap.web.dto.NodeResponse;
import ru.basnukaev.argumentmap.web.dto.RevisionResponse;
import ru.basnukaev.argumentmap.web.dto.UpdateNodeRequest;
import ru.basnukaev.argumentmap.web.mapper.DtoMappers;

@RestController
@RequestMapping("/api/v1/nodes")
public class NodeController {

    private final NodeService nodeService;

    public NodeController(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @PostMapping
    public ResponseEntity<NodeResponse> create(@Valid @RequestBody CreateNodeRequest request,
                                               @CurrentUser UUID userId) {
        Node created = nodeService.createNode(
                request.topicId(), request.nodeType(), request.content(),
                request.weight(), userId
        );
        return ResponseEntity.created(URI.create("/api/v1/nodes/" + created.id()))
                .body(DtoMappers.toResponse(created));
    }

    @PatchMapping("/{nodeId}")
    public NodeResponse updateContent(@PathVariable UUID nodeId,
                                      @Valid @RequestBody UpdateNodeRequest request,
                                      @CurrentUser UUID userId) {
        Node updated = nodeService.updateContent(nodeId, request.content(), userId);
        return DtoMappers.toResponse(updated);
    }

    @DeleteMapping("/{nodeId}")
    public ResponseEntity<Void> delete(@PathVariable UUID nodeId) {
        nodeService.deleteNode(nodeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{nodeId}/revisions")
    public List<RevisionResponse> getRevisions(@PathVariable UUID nodeId) {
        return nodeService.getRevisions(nodeId).stream().map(DtoMappers::toResponse).toList();
    }
}
