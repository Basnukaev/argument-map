package ru.basnukaev.argumentmap.web.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.domain.NodeTranslation;
import ru.basnukaev.argumentmap.service.NodeTranslationService;
import ru.basnukaev.argumentmap.web.CurrentUser;
import ru.basnukaev.argumentmap.web.dto.CreateNodeTranslationRequest;
import ru.basnukaev.argumentmap.web.dto.NodeTranslationRef;
import ru.basnukaev.argumentmap.web.dto.UpdateNodeTranslationRequest;

/**
 * REST-эндпоинты multi-translation узлов (миграция 45).
 *
 * <ul>
 *   <li>POST   /api/v1/nodes/{nodeId}/translations    - добавить перевод</li>
 *   <li>GET    /api/v1/nodes/{nodeId}/translations    - список переводов</li>
 *   <li>PATCH  /api/v1/nodes/translations/{id}        - обновить body/translator</li>
 *   <li>POST   /api/v1/nodes/translations/{id}/default - сделать default</li>
 *   <li>DELETE /api/v1/nodes/translations/{id}        - удалить</li>
 * </ul>
 *
 * <p>Все mutating endpoints требуют canWriteTopic (та же permission что
 * и узел сам). GET требует canReadTopic.
 */
@RestController
@RequestMapping("/api/v1/nodes")
public class NodeTranslationController {

    private final NodeTranslationService translationService;

    public NodeTranslationController(NodeTranslationService translationService) {
        this.translationService = translationService;
    }

    @PostMapping("/{nodeId}/translations")
    public ResponseEntity<NodeTranslationRef> addTranslation(@PathVariable UUID nodeId,
                                                             @Valid @RequestBody CreateNodeTranslationRequest request,
                                                             @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRole();
        boolean isDefault = request.isDefault() != null && request.isDefault();
        NodeTranslation created = translationService.addTranslation(
                nodeId, request.translatorName(), request.language(),
                request.body(), isDefault, userId, role
        );
        return ResponseEntity
                .created(URI.create("/api/v1/nodes/translations/" + created.id()))
                .body(toRef(created));
    }

    @GetMapping("/{nodeId}/translations")
    public List<NodeTranslationRef> listTranslations(@PathVariable UUID nodeId,
                                                     @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRole();
        return translationService.getForNode(nodeId, userId, role).stream()
                .map(NodeTranslationController::toRef).toList();
    }

    @PatchMapping("/translations/{translationId}")
    public NodeTranslationRef updateTranslation(@PathVariable UUID translationId,
                                                @Valid @RequestBody UpdateNodeTranslationRequest request,
                                                @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRole();
        NodeTranslation updated = translationService.updateTranslation(
                translationId, request.translatorName(), request.body(),
                userId, role
        );
        return toRef(updated);
    }

    /**
     * Atomic переключение default-перевода. Используется отдельный POST
     * action а не PATCH с isDefault=true потому что это меняет state
     * других переводов того же узла (снимает флаг). Семантически - не
     * партиальный update одной записи а domain action.
     */
    @PostMapping("/translations/{translationId}/default")
    public NodeTranslationRef setDefault(@PathVariable UUID translationId,
                                         @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRole();
        NodeTranslation updated = translationService.setDefault(translationId, userId, role);
        return toRef(updated);
    }

    @DeleteMapping("/translations/{translationId}")
    public ResponseEntity<Void> deleteTranslation(@PathVariable UUID translationId,
                                                  @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRole();
        translationService.removeTranslation(translationId, userId, role);
        return ResponseEntity.noContent().build();
    }

    private static NodeTranslationRef toRef(NodeTranslation t) {
        return new NodeTranslationRef(
                t.id(), t.translatorName(), t.language(), t.body(), t.isDefault()
        );
    }
}
