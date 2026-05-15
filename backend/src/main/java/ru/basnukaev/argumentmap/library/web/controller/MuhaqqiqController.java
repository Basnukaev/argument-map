package ru.basnukaev.argumentmap.library.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.library.repository.MuhaqqiqRepository;
import ru.basnukaev.argumentmap.library.web.dto.MuhaqqiqResponse;

/**
 * Read-only endpoint для autocomplete в BookEditModal (Этап 20.d).
 * Create через {@code findOrCreate} в {@link MuhaqqiqRepository} -
 * происходит как сторонний эффект при save книги с новым именем
 * мухаккика, отдельный create endpoint не нужен.
 */
@RestController
@RequestMapping("/api/v1/library/muhaqqiqs")
public class MuhaqqiqController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final MuhaqqiqRepository repository;

    public MuhaqqiqController(MuhaqqiqRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<MuhaqqiqResponse> search(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "limit", required = false) Integer limit) {
        int effective = limit == null ? DEFAULT_LIMIT
                : Math.min(MAX_LIMIT, Math.max(1, limit));
        var rows = (query == null || query.isBlank())
                ? repository.findAll().stream().limit(effective).toList()
                : repository.searchByName(query, effective);
        return rows.stream()
                .map(m -> new MuhaqqiqResponse(m.id(), m.name(), m.fullName()))
                .toList();
    }
}
