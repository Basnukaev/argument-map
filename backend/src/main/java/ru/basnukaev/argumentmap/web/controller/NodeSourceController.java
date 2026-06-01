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
import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.domain.NodeSource;
import ru.basnukaev.argumentmap.repository.NodeSourceRepository;
import ru.basnukaev.argumentmap.service.NodeSourceService;
import ru.basnukaev.argumentmap.web.CurrentUser;
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
                                                     @Valid @RequestBody AttachSourceRequest request,
                                                     @CurrentUser UUID userId) {
        // write-guard (ADR-043): citation - контентное изменение темы узла
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        NodeSource saved = nodeSourceService.attachSource(
                nodeId, request.sourceId(), request.quote(), request.context(), request.location(),
                userId, role
        );
        // Возврат через findByIdWithLocation - один JOIN запрос для structured citation
        NodeSourceResponse response = nodeSourceRepository
                .findByIdWithLocation(saved.id())
                .map(DtoMappers::toResponse)
                .orElseThrow();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<NodeSourceResponse> list(@PathVariable UUID nodeId,
                                         @CurrentUser UUID userId) {
        // read-guard (ADR-043): citations узлов приватных тем не утекают
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        return nodeSourceService.getNodeSourcesWithLocation(nodeId, userId, role).stream()
                .map(DtoMappers::toResponse).toList();
    }

    /**
     * Detach по surrogate id (миграция 25, ADR-FK-A). Раньше был
     * `/sources/{sourceId}` - удалял один-единственный link для пары
     * (node, source). Теперь N citations могут быть в той же паре с
     * разными положениями, поэтому detach точечный по id link'а.
     *
     * `nodeId` в path остался для consistency URL hierarchy +
     * potentially для авторизации (узел принадлежит user'у)
     */
    @DeleteMapping("/{nodeSourceId}")
    public ResponseEntity<Void> detach(@PathVariable UUID nodeId,
                                       @PathVariable UUID nodeSourceId,
                                       @CurrentUser UUID userId) {
        // write-guard (ADR-043) поверх node-scoped delete (IDOR-защита)
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        nodeSourceService.detachById(nodeId, nodeSourceId, userId, role);
        return ResponseEntity.noContent().build();
    }
}
