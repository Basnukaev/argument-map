package ru.basnukaev.argumentmap.web.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import ru.basnukaev.argumentmap.domain.Edge;
import ru.basnukaev.argumentmap.service.EdgeService;
import ru.basnukaev.argumentmap.web.CurrentUser;
import ru.basnukaev.argumentmap.web.dto.CreateEdgeRequest;
import ru.basnukaev.argumentmap.web.dto.EdgeResponse;
import ru.basnukaev.argumentmap.web.mapper.DtoMappers;

@RestController
@RequestMapping("/api/v1/edges")
public class EdgeController {

    private final EdgeService edgeService;

    public EdgeController(EdgeService edgeService) {
        this.edgeService = edgeService;
    }

    @PostMapping
    public ResponseEntity<EdgeResponse> create(@Valid @RequestBody CreateEdgeRequest request,
                                               @CurrentUser UUID userId) {
        Edge created = edgeService.createEdge(
                request.fromNodeId(), request.toNodeId(),
                request.edgeType(), request.rationale(), userId
        );
        return ResponseEntity.created(URI.create("/api/v1/edges/" + created.id()))
                .body(DtoMappers.toResponse(created));
    }

    @DeleteMapping("/{edgeId}")
    public ResponseEntity<Void> delete(@PathVariable UUID edgeId) {
        edgeService.deleteEdge(edgeId);
        return ResponseEntity.noContent().build();
    }
}
