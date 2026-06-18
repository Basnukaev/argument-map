package ru.basnukaev.argumentmap.library.web.controller;

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
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.library.domain.ImageRegion;
import ru.basnukaev.argumentmap.library.service.ImageRegionService;
import ru.basnukaev.argumentmap.library.web.dto.CreateImageRegionRequest;
import ru.basnukaev.argumentmap.library.web.dto.ImageRegionResponse;
import ru.basnukaev.argumentmap.library.web.mapper.LibraryDtoMappers;

/**
 * REST API над {@link ImageRegionService} (Этап 17.c, ADR-041).
 *
 * <ul>
 *   <li>{@code POST /api/v1/library/pages/{pageId}/regions} - создать
 *       регион (body {@link CreateImageRegionRequest}). 201 Created +
 *       Location header + {@link ImageRegionResponse}</li>
 *   <li>{@code GET /api/v1/library/pages/{pageId}/regions} - список
 *       регионов страницы (sorted by created_at)</li>
 *   <li>{@code DELETE /api/v1/library/pages/regions/{regionId}} -
 *       удалить регион. 204 No Content. Path {@code /regions/{id}}
 *       без pageId - регион уникален через id, не нужен parent в URL</li>
 * </ul>
 *
 * <p>Update / PATCH endpoint не реализован - regions immutable
 * по дизайну. Если нужно изменить координаты - удалить + создать новый
 * (UX-флоу draw new region проще чем edit corners).
 */
@RestController
@RequestMapping("/api/v1/library")
public class ImageRegionController {

    private final ImageRegionService imageRegionService;

    public ImageRegionController(ImageRegionService imageRegionService) {
        this.imageRegionService = imageRegionService;
    }

    @PostMapping("/pages/{pageId}/regions")
    public ResponseEntity<ImageRegionResponse> create(
            @PathVariable UUID pageId,
            @Valid @RequestBody CreateImageRegionRequest request) {
        ImageRegion region = imageRegionService.create(
                pageId,
                request.x(), request.y(),
                request.width(), request.height(),
                request.extractedText()
        );
        return ResponseEntity
                .created(URI.create("/api/v1/library/pages/regions/" + region.id()))
                .body(LibraryDtoMappers.toResponse(region));
    }

    @GetMapping("/pages/{pageId}/regions")
    public List<ImageRegionResponse> list(@PathVariable UUID pageId) {
        // GET под permitAll /library/pages/** — читаем принципала
        // anonymous-safe; read-guard на родительскую книгу — в сервисе.
        UUID currentUserId = SecurityContextUtils.currentUserIdOrNull();
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        return imageRegionService.listByPage(pageId, currentUserId, role).stream()
                .map(LibraryDtoMappers::toResponse)
                .toList();
    }

    @DeleteMapping("/pages/regions/{regionId}")
    public ResponseEntity<Void> delete(@PathVariable UUID regionId) {
        imageRegionService.delete(regionId);
        return ResponseEntity.noContent().build();
    }
}
