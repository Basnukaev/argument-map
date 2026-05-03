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
                DtoMappers.jsonToString(request.metadata())
        );
        return ResponseEntity.created(URI.create("/api/v1/authorities/" + created.id()))
                .body(DtoMappers.toResponse(created));
    }

    @GetMapping
    public List<AuthorityResponse> list(@RequestParam(name = "q", required = false) String query) {
        List<Authority> found = (query == null || query.isBlank())
                ? authorityService.listAuthorities()
                : authorityService.searchByName(query);
        return found.stream().map(DtoMappers::toResponse).toList();
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
