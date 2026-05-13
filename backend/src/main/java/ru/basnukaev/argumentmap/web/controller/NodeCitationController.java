package ru.basnukaev.argumentmap.web.controller;

import jakarta.validation.Valid;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.service.NodeCitationService;
import ru.basnukaev.argumentmap.web.dto.CitationRequest;
import ru.basnukaev.argumentmap.web.dto.NodeSourceResponse;

/**
 * REST API для positional citation (Этап 18.f, ADR-026 + ADR-027).
 *
 * <p>В отличие от {@link NodeSourceController#attach} (legacy freeform
 * через AddSourceModal), этот endpoint принимает structured citation с
 * привязкой к book/page/pdf - используется CitationPicker на фронте.
 */
@RestController
@RequestMapping("/api/v1/nodes/{nodeId}/citations")
public class NodeCitationController {

    private final NodeCitationService service;

    public NodeCitationController(NodeCitationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NodeSourceResponse create(@PathVariable UUID nodeId,
                                     @Valid @RequestBody CitationRequest request) {
        return service.createCitation(nodeId, request);
    }
}
