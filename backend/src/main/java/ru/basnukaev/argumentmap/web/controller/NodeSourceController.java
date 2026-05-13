package ru.basnukaev.argumentmap.web.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import ru.basnukaev.argumentmap.domain.NodeSource;
import ru.basnukaev.argumentmap.service.NodeSourceService;
import ru.basnukaev.argumentmap.web.dto.AttachSourceRequest;
import ru.basnukaev.argumentmap.web.dto.NodeSourceResponse;
import ru.basnukaev.argumentmap.web.mapper.DtoMappers;

@RestController
@RequestMapping("/api/v1/nodes/{nodeId}/sources")
public class NodeSourceController {

    private final NodeSourceService nodeSourceService;

    public NodeSourceController(NodeSourceService nodeSourceService) {
        this.nodeSourceService = nodeSourceService;
    }

    @PostMapping
    public ResponseEntity<NodeSourceResponse> attach(@PathVariable UUID nodeId,
                                                     @Valid @RequestBody AttachSourceRequest request) {
        NodeSource link = nodeSourceService.attachSource(
                nodeId, request.sourceId(), request.quote(), request.context(), request.location()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(DtoMappers.toResponse(link));
    }

    @GetMapping
    public List<NodeSourceResponse> list(@PathVariable UUID nodeId) {
        return nodeSourceService.getNodeSourcesWithLocation(nodeId).stream()
                .map(DtoMappers::toResponse).toList();
    }

    @DeleteMapping("/{sourceId}")
    public ResponseEntity<Void> detach(@PathVariable UUID nodeId, @PathVariable UUID sourceId) {
        nodeSourceService.detachSource(nodeId, sourceId);
        return ResponseEntity.noContent().build();
    }
}
