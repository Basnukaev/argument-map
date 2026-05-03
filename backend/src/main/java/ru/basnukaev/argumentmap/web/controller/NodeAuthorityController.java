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
import ru.basnukaev.argumentmap.domain.NodeAuthority;
import ru.basnukaev.argumentmap.service.NodeAuthorityService;
import ru.basnukaev.argumentmap.web.dto.AttachAuthorityRequest;
import ru.basnukaev.argumentmap.web.dto.NodeAuthorityResponse;
import ru.basnukaev.argumentmap.web.mapper.DtoMappers;

@RestController
@RequestMapping("/api/v1/nodes/{nodeId}/authorities")
public class NodeAuthorityController {

    private final NodeAuthorityService nodeAuthorityService;

    public NodeAuthorityController(NodeAuthorityService nodeAuthorityService) {
        this.nodeAuthorityService = nodeAuthorityService;
    }

    @PostMapping
    public ResponseEntity<NodeAuthorityResponse> attach(@PathVariable UUID nodeId,
                                                        @Valid @RequestBody AttachAuthorityRequest request) {
        NodeAuthority link = nodeAuthorityService.attachAuthority(
                nodeId, request.authorityId(), request.stance()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(DtoMappers.toResponse(link));
    }

    @GetMapping
    public List<NodeAuthorityResponse> list(@PathVariable UUID nodeId) {
        return nodeAuthorityService.getNodeAuthorities(nodeId).stream()
                .map(DtoMappers::toResponse).toList();
    }

    @DeleteMapping("/{authorityId}")
    public ResponseEntity<Void> detach(@PathVariable UUID nodeId, @PathVariable UUID authorityId) {
        nodeAuthorityService.detachAuthority(nodeId, authorityId);
        return ResponseEntity.noContent().build();
    }
}
