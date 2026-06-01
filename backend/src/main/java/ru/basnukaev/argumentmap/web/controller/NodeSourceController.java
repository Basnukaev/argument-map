package ru.basnukaev.argumentmap.web.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
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
import ru.basnukaev.argumentmap.domain.NodeSource;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.repository.CollectionRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.MatnRepository;
import ru.basnukaev.argumentmap.repository.NodeSourceRepository;
import ru.basnukaev.argumentmap.repository.NodeSourceRepository.NodeSourceWithLocation;
import ru.basnukaev.argumentmap.service.NodeSourceService;
import ru.basnukaev.argumentmap.web.CurrentUser;
import ru.basnukaev.argumentmap.web.dto.AttachSourceRequest;
import ru.basnukaev.argumentmap.web.dto.HadithRef;
import ru.basnukaev.argumentmap.web.dto.NodeSourceResponse;
import ru.basnukaev.argumentmap.web.mapper.DtoMappers;

@RestController
@RequestMapping("/api/v1/nodes/{nodeId}/sources")
public class NodeSourceController {

    private final NodeSourceService nodeSourceService;
    private final NodeSourceRepository nodeSourceRepository;
    private final HadithRepository hadithRepository;
    private final MatnRepository matnRepository;
    private final CollectionRepository collectionRepository;

    public NodeSourceController(NodeSourceService nodeSourceService,
                                 NodeSourceRepository nodeSourceRepository,
                                 HadithRepository hadithRepository,
                                 MatnRepository matnRepository,
                                 CollectionRepository collectionRepository) {
        this.nodeSourceService = nodeSourceService;
        this.nodeSourceRepository = nodeSourceRepository;
        this.hadithRepository = hadithRepository;
        this.matnRepository = matnRepository;
        this.collectionRepository = collectionRepository;
    }

    @PostMapping
    public ResponseEntity<NodeSourceResponse> attach(@PathVariable UUID nodeId,
                                                     @Valid @RequestBody AttachSourceRequest request,
                                                     @CurrentUser UUID userId) {
        // write-guard (ADR-043): citation - контентное изменение темы узла
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        NodeSource saved = nodeSourceService.attachSource(
                nodeId, request.sourceId(), request.quote(), request.context(), request.location(),
                userId, role
        );
        // Возврат через findByIdWithLocation - один JOIN запрос для structured citation
        NodeSourceResponse response = nodeSourceRepository
                .findByIdWithLocation(saved.id())
                .map(DtoMappers::toResponse)
                .orElseThrow();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<NodeSourceResponse> list(@PathVariable UUID nodeId,
                                         @CurrentUser UUID userId) {
        // read-guard (ADR-043): citations узлов приватных тем не утекают
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        List<NodeSourceWithLocation> rows =
                nodeSourceService.getNodeSourcesWithLocation(nodeId, userId, role);
        // под-проект #2: обогащаем хадис-опоры (sourceType=HADITH) полями matn/
        // сборник/статус. Не-хадисы получают hadith=null.
        Map<UUID, HadithRef> hadithRefBySourceId = buildHadithRefs(rows);
        return rows.stream()
                .map(row -> DtoMappers.toResponse(
                        row, hadithRefBySourceId.get(row.ns().sourceId())))
                .toList();
    }

    /**
     * Строит map sourceId→HadithRef для хадис-опор данного списка (под-проект #2).
     * Хадис-опору узнаём обратным lookup'ом {@code hd_hadiths.source_id IN (...)};
     * previewMatn батчем (тот же механизм что у hadith-list), имя сборника —
     * per-hadith (N мало). Не-хадисы в map не попадают → enrichment даёт null.
     */
    private Map<UUID, HadithRef> buildHadithRefs(List<NodeSourceWithLocation> rows) {
        List<UUID> sourceIds = rows.stream()
                .map(r -> r.ns().sourceId())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        List<Hadith> hadiths = hadithRepository.findBySourceIds(sourceIds);
        if (hadiths.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> previews = matnRepository.findPrimaryTextByHadithIds(
                hadiths.stream().map(Hadith::id).toList());
        Map<UUID, HadithRef> result = new HashMap<>();
        for (Hadith h : hadiths) {
            result.put(h.sourceId(), new HadithRef(
                    h.id(), h.primaryNumber(), resolveCollectionName(h.collectionId()),
                    previews.get(h.id()), h.status()));
        }
        return result;
    }

    /** Имя сборника nameRu→nameAr→slug (как HadithCitationService#buildTitle). */
    private String resolveCollectionName(UUID collectionId) {
        if (collectionId == null) {
            return null;
        }
        return collectionRepository.findById(collectionId)
                .map(c -> firstNonBlank(c.nameRu(), c.nameAr(), c.slug()))
                .orElse(null);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    /**
     * Detach по surrogate id (миграция 25, ADR-FK-A). Раньше был
     * `/sources/{sourceId}` - удалял один-единственный link для пары
     * (node, source). Теперь N citations могут быть в той же паре с
     * разными положениями, поэтому detach точечный по id link'а.
     *
     * `nodeId` в path остался для consistency URL hierarchy +
     * potentially для авторизации (узел принадлежит user'у)
     */
    @DeleteMapping("/{nodeSourceId}")
    public ResponseEntity<Void> detach(@PathVariable UUID nodeId,
                                       @PathVariable UUID nodeSourceId,
                                       @CurrentUser UUID userId) {
        // write-guard (ADR-043) поверх node-scoped delete (IDOR-защита)
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        nodeSourceService.detachById(nodeId, nodeSourceId, userId, role);
        return ResponseEntity.noContent().build();
    }
}
