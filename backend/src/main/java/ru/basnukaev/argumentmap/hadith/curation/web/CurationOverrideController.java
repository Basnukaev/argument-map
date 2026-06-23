package ru.basnukaev.argumentmap.hadith.curation.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.hadith.curation.service.CurationOverrideService;
import ru.basnukaev.argumentmap.hadith.curation.web.dto.CurationOverridePutRequest;
import ru.basnukaev.argumentmap.hadith.curation.web.dto.CurationOverrideResponse;
import ru.basnukaev.argumentmap.web.CurrentUser;

/**
 * Generic REST курации данных hadith-домена (ADR-065 §6). Форма override
 * одинакова для 8 сущностей → generic вместо 8× per-entity boilerplate.
 * ADMIN-only (гейт в сервисе). {@code @CurrentUser} обязателен — anonymous
 * отсекается резолвером (401). Коды ошибок — {@link CurationException}.
 */
@RestController
@RequestMapping("/api/v1/admin/curation/overrides")
public class CurationOverrideController {

    private final CurationOverrideService service;

    public CurationOverrideController(CurationOverrideService service) {
        this.service = service;
    }

    /** Upsert правки поля и/или скрытия (idempotent по UNIQUE-ключу). */
    @PutMapping
    public CurationOverrideResponse upsert(@Valid @RequestBody CurationOverridePutRequest request,
                                           @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        return service.upsert(request, userId, role);
    }

    /** Откат правки к импортному значению (удалить override). */
    @DeleteMapping
    public ResponseEntity<Void> delete(@RequestParam String entityTable,
                                       @RequestParam UUID entityId,
                                       @RequestParam String fieldName,
                                       @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        service.delete(entityTable, entityId, fieldName, userId, role);
        return ResponseEntity.noContent().build();
    }

    /** Список overrides записи (admin-вид «что переопределено/скрыто»). */
    @GetMapping
    public List<CurationOverrideResponse> list(@RequestParam String entityTable,
                                               @RequestParam UUID entityId,
                                               @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        return service.list(entityTable, entityId, userId, role);
    }
}
