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
import ru.basnukaev.argumentmap.repository.NodeSourceRepository;
import ru.basnukaev.argumentmap.service.NodeSourceService;
import ru.basnukaev.argumentmap.web.dto.AttachSourceRequest;
import ru.basnukaev.argumentmap.web.dto.NodeSourceResponse;
import ru.basnukaev.argumentmap.web.mapper.DtoMappers;

@RestController
@RequestMapping("/api/v1/nodes/{nodeId}/sources")
public class NodeSourceController {

    private final NodeSourceService nodeSourceService;
    private final NodeSourceRepository nodeSourceRepository;

    public NodeSourceController(NodeSourceService nodeSourceService,
                                 NodeSourceRepository nodeSourceRepository) {
        this.nodeSourceService = nodeSourceService;
        this.nodeSourceRepository = nodeSourceRepository;
    }

    @PostMapping
    public ResponseEntity<NodeSourceResponse> attach(@PathVariable UUID nodeId,
                                                     @Valid @RequestBody AttachSourceRequest request) {
        nodeSourceService.attachSource(
                nodeId, request.sourceId(), request.quote(), request.context(), request.location()
        );
        // ADR-028: возвращаем response с structured citation - findByPkWithLocation
        // делает тот же 9-JOIN что и list, чтобы клиент получил полную структуру сразу
        NodeSourceResponse response = nodeSourceRepository
                .findByPkWithLocation(nodeId, request.sourceId())
                .map(DtoMappers::toResponse)
                .orElseThrow();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
