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
import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
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
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        Edge created = edgeService.createEdge(
                request.fromNodeId(), request.toNodeId(),
                request.edgeType(), request.rationale(),
                request.sourceHandle(), request.targetHandle(),
                userId, role
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
                               @Valid @RequestBody UpdateEdgeRequest request,
                               @CurrentUser UUID userId) {
        if (request.fromNodeId() == null && request.toNodeId() == null
                && request.edgeType() == null && request.rationale() == null
                && request.sourceHandle() == null && request.targetHandle() == null) {
            throw new IllegalArgumentException(
                    "Хотя бы одно поле должно быть указано для PATCH"
            );
        }
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        Edge updated = edgeService.updateEdge(
                edgeId,
                request.fromNodeId(), request.toNodeId(), request.edgeType(),
                request.rationale(), request.sourceHandle(), request.targetHandle(),
                userId, role
        );
        return DtoMappers.toResponse(updated);
    }

    /**
     * Bring to front: ставит ребро на передний план через присваивание
     * нового z_index = max(z_index рёбер темы) + 1. Endpoint dedicated -
     * клиенту не нужно знать max, сервер сам вычисляет. Запрос без тела.
     * Mirror POST /api/v1/nodes/{nodeId}/z-order/bring-to-front.
     */
    @PostMapping("/{edgeId}/z-order/bring-to-front")
    public EdgeResponse bringToFront(@PathVariable UUID edgeId,
                                     @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        Edge edge = edgeService.bringToFront(edgeId, userId, role);
        return DtoMappers.toResponse(edge);
    }

    /**
     * Send to back: ставит ребро на задний план через присваивание z_index
     * = min(z_index рёбер темы) - 1. Парный bring-to-front.
     */
    @PostMapping("/{edgeId}/z-order/send-to-back")
    public EdgeResponse sendToBack(@PathVariable UUID edgeId,
                                   @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        Edge edge = edgeService.sendToBack(edgeId, userId, role);
        return DtoMappers.toResponse(edge);
    }

    @DeleteMapping("/{edgeId}")
    public ResponseEntity<Void> delete(@PathVariable UUID edgeId, @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        edgeService.deleteEdge(edgeId, userId, role);
        return ResponseEntity.noContent().build();
    }
}
