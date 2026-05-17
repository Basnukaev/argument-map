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
import ru.basnukaev.argumentmap.domain.Reliability;
import ru.basnukaev.argumentmap.domain.Source;
import ru.basnukaev.argumentmap.domain.SourceType;
import ru.basnukaev.argumentmap.service.SourceService;
import ru.basnukaev.argumentmap.web.dto.CreateSourceRequest;
import ru.basnukaev.argumentmap.web.dto.PageRequest;
import ru.basnukaev.argumentmap.web.dto.PagedResponse;
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

    /**
     * Пагинированный список источников с фильтрами (Этап pagination).
     * Default page=0, size=20. Max size=100.
     *
     * <p>Фильтры (все опциональные, комбинируются через AND):
     * <ul>
     *   <li>{@code q} - подстрока в title (case-insensitive)</li>
     *   <li>{@code type} - whitelist {@link SourceType} (QURAN/HADITH/BOOK/ARTICLE/URL)</li>
     *   <li>{@code reliability} - whitelist {@link Reliability} (SAHIH/HASAN/DAIF).
     *       Допустим только когда {@code type=HADITH}; иначе 400 invalid-source</li>
     * </ul>
     */
    @GetMapping
    public PagedResponse<SourceResponse> list(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "type", required = false) SourceType type,
            @RequestParam(name = "reliability", required = false) Reliability reliability,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        PageRequest pr = PageRequest.from(page, size);
        List<Source> items = sourceService.listPage(type, reliability, query, pr.size(), pr.offset());
        long total = sourceService.countFiltered(type, reliability, query);
        List<SourceResponse> mapped = items.stream().map(DtoMappers::toResponse).toList();
        return PagedResponse.of(mapped, pr.page(), pr.size(), total);
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
