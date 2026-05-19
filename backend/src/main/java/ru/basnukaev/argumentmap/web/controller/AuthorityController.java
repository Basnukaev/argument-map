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
import ru.basnukaev.argumentmap.domain.Authority;
import ru.basnukaev.argumentmap.service.AuthorityService;
import ru.basnukaev.argumentmap.web.dto.AuthorityResponse;
import ru.basnukaev.argumentmap.web.dto.CreateAuthorityRequest;
import ru.basnukaev.argumentmap.web.dto.PageRequest;
import ru.basnukaev.argumentmap.web.dto.PagedResponse;
import ru.basnukaev.argumentmap.web.mapper.DtoMappers;

@RestController
@RequestMapping("/api/v1/authorities")
public class AuthorityController {

    private final AuthorityService authorityService;

    public AuthorityController(AuthorityService authorityService) {
        this.authorityService = authorityService;
    }

    @PostMapping
    public ResponseEntity<AuthorityResponse> create(@Valid @RequestBody CreateAuthorityRequest request) {
        Authority created = authorityService.createAuthority(
                request.name(), request.bio(), request.era(), request.madhab(),
                DtoMappers.jsonToString(request.metadata()),
                request.type()
        );
        return ResponseEntity.created(URI.create("/api/v1/authorities/" + created.id()))
                .body(DtoMappers.toResponse(created));
    }

    /**
     * Пагинированный список авторитетов (Этап pagination).
     *
     * <p>Фильтры (опциональные):
     * <ul>
     *   <li>{@code q} - подстрока в name (case-insensitive)</li>
     *   <li>{@code era} - exact match по veka/эпохе (свободный текст,
     *       не enum: "XIII-XIV век", "сахаба", "табиины" и т.д.)</li>
     * </ul>
     *
     * <p>Note: {@code madhab} как фильтр - не в MVP. Свободный текст и
     * variability (ханбалитский / Hanbali / حنبلي) делает фильтр без
     * нормализации малополезным. Когда понадобится - вводим master-data
     * таблицу мазхабов с FK.
     */
    @GetMapping
    public PagedResponse<AuthorityResponse> list(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "era", required = false) String era,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        PageRequest pr = PageRequest.from(page, size);
        List<Authority> items = authorityService.listPage(query, era, pr.size(), pr.offset());
        long total = authorityService.countFiltered(query, era);
        List<AuthorityResponse> mapped = items.stream().map(DtoMappers::toResponse).toList();
        return PagedResponse.of(mapped, pr.page(), pr.size(), total);
    }

    @GetMapping("/{authorityId}")
    public AuthorityResponse getOne(@PathVariable UUID authorityId) {
        return DtoMappers.toResponse(authorityService.getAuthority(authorityId));
    }

    @DeleteMapping("/{authorityId}")
    public ResponseEntity<Void> delete(@PathVariable UUID authorityId) {
        authorityService.deleteAuthority(authorityId);
        return ResponseEntity.noContent().build();
    }
}
