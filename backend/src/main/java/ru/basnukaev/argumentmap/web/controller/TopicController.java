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
import ru.basnukaev.argumentmap.domain.Topic;
import ru.basnukaev.argumentmap.repository.TopicWithCounts;
import ru.basnukaev.argumentmap.service.GraphService;
import ru.basnukaev.argumentmap.service.GraphView;
import ru.basnukaev.argumentmap.service.NodeProjectionService;
import ru.basnukaev.argumentmap.service.NodeProjectionService.NodeProjectionBatch;
import ru.basnukaev.argumentmap.service.PermissionService;
import ru.basnukaev.argumentmap.service.TopicService;
import ru.basnukaev.argumentmap.web.CurrentUser;
import ru.basnukaev.argumentmap.web.dto.CreateTopicRequest;
import ru.basnukaev.argumentmap.web.dto.GraphResponse;
import ru.basnukaev.argumentmap.web.dto.PageRequest;
import ru.basnukaev.argumentmap.web.dto.PagedResponse;
import ru.basnukaev.argumentmap.web.dto.TopicResponse;
import ru.basnukaev.argumentmap.web.dto.UpdateTopicRequest;
import ru.basnukaev.argumentmap.web.dto.UpdateTopicStatusAlgorithmRequest;
import ru.basnukaev.argumentmap.web.dto.UpdateTopicVisibilityRequest;
import ru.basnukaev.argumentmap.web.mapper.DtoMappers;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/topics")
public class TopicController {

    private final TopicService topicService;
    private final GraphService graphService;
    private final PermissionService permissionService;
    private final NodeProjectionService nodeProjectionService;

    public TopicController(TopicService topicService, GraphService graphService,
                           PermissionService permissionService,
                           NodeProjectionService nodeProjectionService) {
        this.topicService = topicService;
        this.graphService = graphService;
        this.permissionService = permissionService;
        this.nodeProjectionService = nodeProjectionService;
    }

    @PostMapping
    public ResponseEntity<TopicResponse> create(@Valid @RequestBody CreateTopicRequest request,
                                                @CurrentUser UUID userId) {
        Topic created = topicService.createTopic(
                request.title(), request.description(),
                request.rootQuestion(), request.visibility(), userId
        );
        // Дополнительный SQL за актуальными nodeCount/edgeCount после create -
        // ответ должен честно отражать состояние темы (rootQuestion = 1 узел)
        TopicResponse body = DtoMappers.toResponse(topicService.getTopicWithCounts(created.id()));
        return ResponseEntity.created(URI.create("/api/v1/topics/" + created.id())).body(body);
    }

    /**
     * Пагинированный список тем видимых user'у (ADR-043).
     *
     * <p>Пагинация: ?page=&size= (default 0/20, max size=100).
     * Фильтр: ?visibility= (PRIVATE/SHARED/PUBLIC) внутри set'а уже
     * видимых пользователю. Например USER+visibility=PUBLIC = только
     * PUBLIC темы (свои+чужие); USER+visibility=PRIVATE = только свои
     * PRIVATE.
     *
     * <p>ADMIN видит все темы без visibility-clipping.
     */
    @GetMapping
    public PagedResponse<TopicResponse> list(
            @CurrentUser UUID userId,
            @RequestParam(name = "visibility", required = false) String visibility,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "sort", required = false) String sort) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        PageRequest pr = PageRequest.from(page, size);
        // Vision 49d Section 2.1: sort whitelist (recent/popular/alphabetical)
        // - валидация в TopicRepository.orderByForSort через switch с default
        List<TopicWithCounts> items = topicService.listVisibleTopicsPage(
                userId, role, visibility, pr.size(), pr.offset(), sort);
        long total = topicService.countVisibleTopics(userId, role, visibility);
        List<TopicResponse> mapped = items.stream().map(DtoMappers::toResponse).toList();
        return PagedResponse.of(mapped, pr.page(), pr.size(), total);
    }

    @GetMapping("/{topicId}")
    public TopicResponse getOne(@PathVariable UUID topicId, @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        permissionService.assertCanRead(topicId, userId, role);
        return DtoMappers.toResponse(topicService.getTopicWithCounts(topicId));
    }

    @DeleteMapping("/{topicId}")
    public ResponseEntity<Void> delete(@PathVariable UUID topicId, @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        topicService.deleteTopic(topicId, userId, role);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{topicId}/graph")
    public GraphResponse getGraph(@PathVariable UUID topicId, @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        permissionService.assertCanRead(topicId, userId, role);
        GraphView graph = graphService.getGraph(topicId);
        // Bulk-load через NodeProjectionService: 4 SQL на весь граф, не N+1 на
        // каждый узел. NodeResponse получает vote-поля + inlineCitations +
        // translations для рендеринга [N]-маркеров и переключателя языков
        List<UUID> nodeIds = graph.nodes().stream().map(n -> n.id()).toList();
        NodeProjectionBatch batch = nodeProjectionService.batch(nodeIds, userId);
        return DtoMappers.toResponse(graph,
                batch.stats(), batch.userVotes(),
                batch.citations(), batch.translations());
    }

    /**
     * Partial update title/description темы (backlog tech debt #10).
     * Owner + EDITOR (через {@code assertCanWrite}). Возвращает
     * обновлённую тему. См. /visibility и /status-algorithm для других
     * partial-update endpoint'ов
     */
    @PatchMapping("/{topicId}")
    public TopicResponse patchTopic(@PathVariable UUID topicId,
                                    @Valid @RequestBody UpdateTopicRequest request,
                                    @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        Topic updated = topicService.updateTopic(
                topicId, request.title(), request.description(), userId, role);
        // Возвращаем с counts чтобы list/details одинаково отображались
        return DtoMappers.toResponse(topicService.getTopicWithCounts(updated.id()));
    }

    @PatchMapping("/{topicId}/visibility")
    public TopicResponse updateVisibility(@PathVariable UUID topicId,
                                          @Valid @RequestBody UpdateTopicVisibilityRequest request,
                                          @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        Topic updated = topicService.updateVisibility(topicId, request.visibility(), userId, role);
        return DtoMappers.toResponse(updated);
    }

    /**
     * Сменить алгоритм пересчёта статусов узлов (ADR-044). Только owner.
     * Тригерит пересчёт всего графа под новым алгоритмом - тема не
     * остаётся в inconsistent state
     */
    @PatchMapping("/{topicId}/status-algorithm")
    public TopicResponse updateStatusAlgorithm(@PathVariable UUID topicId,
                                               @Valid @RequestBody UpdateTopicStatusAlgorithmRequest request,
                                               @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        Topic updated = topicService.updateStatusAlgorithm(topicId, request.algorithm(), userId, role);
        return DtoMappers.toResponse(updated);
    }
}
