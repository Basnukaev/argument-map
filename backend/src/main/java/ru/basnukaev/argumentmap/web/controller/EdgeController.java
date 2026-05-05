package ru.basnukaev.argumentmap.web.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import ru.basnukaev.argumentmap.web.dto.UpdateEdgeRequest;
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
                request.edgeType(), request.rationale(),
                request.sourceHandle(), request.targetHandle(),
                userId
        );
        return ResponseEntity.created(URI.create("/api/v1/edges/" + created.id()))
                .body(DtoMappers.toResponse(created));
    }

    /**
     * PATCH принимает любую комбинацию полей: fromNodeId, toNodeId, edgeType,
     * rationale, sourceHandle, targetHandle. Поля null сохраняют существующее
     * значение, не-null - применяются. После применения проверяется selfloop,
     * граница темы, матрица ADR-010. Если валидация не проходит - 422
     * invalid-edge, ребро в БД не меняется. Пустой запрос (все поля null) -
     * 400 illegal-argument. Используется для reconnect (ADR-014).
     */
    @PatchMapping("/{edgeId}")
    public EdgeResponse update(@PathVariable UUID edgeId,
                               @Valid @RequestBody UpdateEdgeRequest request) {
        if (request.fromNodeId() == null && request.toNodeId() == null
                && request.edgeType() == null && request.rationale() == null
                && request.sourceHandle() == null && request.targetHandle() == null) {
            throw new IllegalArgumentException(
                    "Хотя бы одно поле должно быть указано для PATCH"
            );
        }
        Edge updated = edgeService.updateEdge(
                edgeId,
                request.fromNodeId(), request.toNodeId(), request.edgeType(),
                request.rationale(), request.sourceHandle(), request.targetHandle()
        );
        return DtoMappers.toResponse(updated);
    }

    @DeleteMapping("/{edgeId}")
    public ResponseEntity<Void> delete(@PathVariable UUID edgeId) {
        edgeService.deleteEdge(edgeId);
        return ResponseEntity.noContent().build();
    }
}
