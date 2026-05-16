package ru.basnukaev.argumentmap.web.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import ru.basnukaev.argumentmap.domain.Source;
import ru.basnukaev.argumentmap.service.SourceService;
import ru.basnukaev.argumentmap.web.dto.CreateSourceRequest;
import ru.basnukaev.argumentmap.web.dto.SourceResponse;
import ru.basnukaev.argumentmap.web.mapper.DtoMappers;

@RestController
@RequestMapping("/api/v1/sources")
public class SourceController {

    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @PostMapping
    public ResponseEntity<SourceResponse> create(@Valid @RequestBody CreateSourceRequest request) {
        Source created = sourceService.createSource(
                request.sourceType(), request.title(), request.citation(),
                request.reliability(), request.authorityId(), request.bookId(),
                DtoMappers.jsonToString(request.metadata())
        );
        return ResponseEntity.created(URI.create("/api/v1/sources/" + created.id()))
                .body(DtoMappers.toResponse(created));
    }

    @GetMapping
    public List<SourceResponse> list(@RequestParam(name = "q", required = false) String query) {
        List<Source> found = (query == null || query.isBlank())
                ? sourceService.listSources()
                : sourceService.searchByTitle(query);
        return found.stream().map(DtoMappers::toResponse).toList();
    }

    @GetMapping("/{sourceId}")
    public SourceResponse getOne(@PathVariable UUID sourceId) {
        return DtoMappers.toResponse(sourceService.getSource(sourceId));
    }

    @DeleteMapping("/{sourceId}")
    public ResponseEntity<Void> delete(@PathVariable UUID sourceId) {
        sourceService.deleteSource(sourceId);
        return ResponseEntity.noContent().build();
    }
}
