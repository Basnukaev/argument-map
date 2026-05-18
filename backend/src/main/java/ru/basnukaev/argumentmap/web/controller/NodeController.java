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
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.VoteStats;
import ru.basnukaev.argumentmap.service.NodeProjectionService;
import ru.basnukaev.argumentmap.service.NodeProjectionService.NodeProjection;
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
    private final NodeProjectionService nodeProjectionService;

    public NodeController(NodeService nodeService,
                          NodeProjectionService nodeProjectionService) {
        this.nodeService = nodeService;
        this.nodeProjectionService = nodeProjectionService;
    }

    @PostMapping
    public ResponseEntity<NodeResponse> create(@Valid @RequestBody CreateNodeRequest request,
                                               @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRole();
        String originalLang = nullIfBlank(request.originalLang());

        Node created = nodeService.createNode(
                request.topicId(), request.nodeType(), request.content(),
                originalLang, userId, role
        );
        // Только что созданный узел не имеет ни голосов ни node_sources ни
        // переводов - VoteStats.EMPTY, userVote=null, citations/translations=[]
        return ResponseEntity.created(URI.create("/api/v1/nodes/" + created.id()))
                .body(DtoMappers.toResponse(created, VoteStats.EMPTY, null, List.of(), List.of()));
    }

    /**
     * PATCH принимает opt content и/или opt posX+posY и/или opt originalLang.
     * Если есть content - пишется revision. Если есть pos - меняются
     * координаты без revision. originalLang - в одном transaction'е с
     * content, без revision (это metadata). Можно несколько действий сразу.
     * Пустой запрос (без полей) - 400 validation.
     */
    @PatchMapping("/{nodeId}")
    public NodeResponse update(@PathVariable UUID nodeId,
                               @Valid @RequestBody UpdateNodeRequest request,
                               @CurrentUser UUID userId) {
        boolean hasContent = request.content() != null;
        boolean hasPosition = request.posX() != null && request.posY() != null;
        boolean hasOriginalLang = request.originalLang() != null;
        if (!hasContent && !hasPosition && !hasOriginalLang) {
            throw new IllegalArgumentException(
                    "Хотя бы одно из полей (content, posX+posY, originalLang) должно быть указано"
            );
        }

        String role = SecurityContextUtils.currentRole();
        Node node = null;
        if (hasContent || hasOriginalLang) {
            Object contentBox = hasContent ? request.content() : NodeService.NoChange.INSTANCE;
            Object originalLangBox = hasOriginalLang
                    ? (request.originalLang().isEmpty() ? null : request.originalLang())
                    : NodeService.NoChange.INSTANCE;
            node = nodeService.updateContent(nodeId, contentBox, originalLangBox, userId, role);
        }
        if (hasPosition) {
            node = nodeService.updatePosition(nodeId, request.posX(), request.posY(), userId, role);
        }
        return enrichResponse(node, userId);
    }

    /**
     * Bring to front: ставит узел на передний план через присваивание
     * нового z_index = max(z_index узлов темы) + 1. Endpoint dedicated -
     * клиенту не нужно знать max, сервер сам вычисляет. Запрос без тела.
     */
    @PostMapping("/{nodeId}/z-order/bring-to-front")
    public NodeResponse bringToFront(@PathVariable UUID nodeId,
                                     @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRole();
        Node node = nodeService.bringToFront(nodeId, userId, role);
        return enrichResponse(node, userId);
    }

    /**
     * Send to back: ставит узел на задний план через присваивание z_index
     * = min(z_index узлов темы) - 1. Парный bring-to-front.
     */
    @PostMapping("/{nodeId}/z-order/send-to-back")
    public NodeResponse sendToBack(@PathVariable UUID nodeId,
                                   @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRole();
        Node node = nodeService.sendToBack(nodeId, userId, role);
        return enrichResponse(node, userId);
    }

    @DeleteMapping("/{nodeId}")
    public ResponseEntity<Void> delete(@PathVariable UUID nodeId, @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRole();
        nodeService.deleteNode(nodeId, userId, role);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{nodeId}/revisions")
    public List<RevisionResponse> getRevisions(@PathVariable UUID nodeId,
                                               @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRole();
        return nodeService.getRevisions(nodeId, userId, role).stream()
                .map(DtoMappers::toResponse).toList();
    }

    /**
     * Обогащает Node проекцией (votes / citations / translations) и мэппит в
     * NodeResponse. Заменяет 4-строчный inline fragment который дублировался
     * в трёх методах (PATCH / bringToFront / sendToBack).
     */
    private NodeResponse enrichResponse(Node node, UUID userId) {
        NodeProjection projection = nodeProjectionService.single(node.id(), userId);
        return DtoMappers.toResponse(node,
                projection.stats(), projection.userVote(),
                projection.citations(), projection.translations());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nullIfBlank(String s) {
        return isBlank(s) ? null : s;
    }
}
