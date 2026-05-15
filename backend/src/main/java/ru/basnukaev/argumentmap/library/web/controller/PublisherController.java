package ru.basnukaev.argumentmap.library.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.library.repository.PublisherRepository;
import ru.basnukaev.argumentmap.library.web.dto.PublisherResponse;

/**
 * Read-only endpoint для autocomplete в BookEditModal (Этап 20.d).
 */
@RestController
@RequestMapping("/api/v1/library/publishers")
public class PublisherController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final PublisherRepository repository;

    public PublisherController(PublisherRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<PublisherResponse> search(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "limit", required = false) Integer limit) {
        int effective = limit == null ? DEFAULT_LIMIT
                : Math.min(MAX_LIMIT, Math.max(1, limit));
        var rows = (query == null || query.isBlank())
                ? repository.findAll().stream().limit(effective).toList()
                : repository.searchByName(query, effective);
        return rows.stream()
                .map(p -> new PublisherResponse(p.id(), p.name()))
                .toList();
    }
}
