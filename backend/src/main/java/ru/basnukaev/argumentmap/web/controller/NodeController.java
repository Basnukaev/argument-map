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
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.VoteStats;
import ru.basnukaev.argumentmap.repository.NodeSourceRepository;
import ru.basnukaev.argumentmap.repository.NodeVoteRepository;
import ru.basnukaev.argumentmap.service.NodeService;
import ru.basnukaev.argumentmap.web.CurrentUser;
import ru.basnukaev.argumentmap.web.dto.CreateNodeRequest;
import ru.basnukaev.argumentmap.web.dto.InlineCitationRef;
import ru.basnukaev.argumentmap.web.dto.NodeResponse;
import ru.basnukaev.argumentmap.web.dto.RevisionResponse;
import ru.basnukaev.argumentmap.web.dto.UpdateNodeRequest;
import ru.basnukaev.argumentmap.web.mapper.DtoMappers;

@RestController
@RequestMapping("/api/v1/nodes")
public class NodeController {

    private final NodeService nodeService;
    private final NodeVoteRepository nodeVoteRepository;
    private final NodeSourceRepository nodeSourceRepository;

    public NodeController(NodeService nodeService, NodeVoteRepository nodeVoteRepository,
                          NodeSourceRepository nodeSourceRepository) {
        this.nodeService = nodeService;
        this.nodeVoteRepository = nodeVoteRepository;
        this.nodeSourceRepository = nodeSourceRepository;
    }

    @PostMapping
    public ResponseEntity<NodeResponse> create(@Valid @RequestBody CreateNodeRequest request,
                                               @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRole();
        // Пустая строка translation - трактуем как «нет перевода». Бэк
        // упрощает: либо translation NOT NULL и не пустой, либо null.
        String translation = isBlank(request.translation()) ? null : request.translation();
        String translationLang = translation == null ? null : nullIfBlank(request.translationLang());
        String originalLang = nullIfBlank(request.originalLang());

        Node created = nodeService.createNode(
                request.topicId(), request.nodeType(), request.content(),
                translation, translationLang, originalLang,
                userId, role
        );
        // Только что созданный узел не имеет ни голосов ни node_sources -
        // VoteStats.EMPTY, userVote=null, inlineCitations=[]
        return ResponseEntity.created(URI.create("/api/v1/nodes/" + created.id()))
                .body(DtoMappers.toResponse(created, VoteStats.EMPTY, null, List.of()));
    }

    /**
     * PATCH принимает opt content и/или opt posX+posY и/или opt bilingual
     * (translation/translationLang/originalLang). Если есть content -
     * пишется revision. Если есть pos - меняются координаты без revision.
     * Bilingual поля - в одном transaction'е с content, без revision (это
     * metadata). Пустая строка translation = очистить (translationLang
     * тоже очищается). Можно несколько действий сразу - применятся
     * последовательно. Пустой запрос (без полей) - 400 validation.
     */
    @PatchMapping("/{nodeId}")
    public NodeResponse update(@PathVariable UUID nodeId,
                               @Valid @RequestBody UpdateNodeRequest request,
                               @CurrentUser UUID userId) {
        boolean hasContent = request.content() != null;
        boolean hasPosition = request.posX() != null && request.posY() != null;
        boolean hasTranslation = request.translation() != null;
        boolean hasTranslationLang = request.translationLang() != null;
        boolean hasOriginalLang = request.originalLang() != null;
        if (!hasContent && !hasPosition && !hasTranslation && !hasTranslationLang && !hasOriginalLang) {
            throw new IllegalArgumentException(
                    "Хотя бы одно из полей (content, posX+posY, translation, translationLang, originalLang) должно быть указано"
            );
        }

        String role = SecurityContextUtils.currentRole();
        Node node = null;
        // Объединяем content + bilingual в один updateContent (записывает
        // revision только для content, audit покрывает оба)
        if (hasContent || hasTranslation || hasTranslationLang || hasOriginalLang) {
            Object contentBox = hasContent ? request.content() : NodeService.NoChange.INSTANCE;
            Object translationBox = hasTranslation
                    ? (request.translation().isEmpty() ? null : request.translation())
                    : NodeService.NoChange.INSTANCE;
            Object translationLangBox = hasTranslationLang
                    ? (request.translationLang().isEmpty() ? null : request.translationLang())
                    : NodeService.NoChange.INSTANCE;
            // если translation очищается - lang тоже очищаем для консистентности
            if (translationBox == null && translationLangBox instanceof NodeService.NoChange) {
                translationLangBox = null;
            }
            Object originalLangBox = hasOriginalLang
                    ? (request.originalLang().isEmpty() ? null : request.originalLang())
                    : NodeService.NoChange.INSTANCE;
            node = nodeService.updateContent(nodeId, contentBox,
                    translationBox, translationLangBox, originalLangBox,
                    userId, role);
        }
        if (hasPosition) {
            node = nodeService.updatePosition(nodeId, request.posX(), request.posY(), userId, role);
        }
        // Vote статистика и inline citations подгружаются отдельно - PATCH не
        // меняет ни голоса ни источники, но фронту удобно получить актуальное
        // состояние карточки в одном ответе
        VoteStats stats = nodeVoteRepository.getStatsForNode(nodeId);
        Integer userVote = nodeVoteRepository.findByNodeAndUser(nodeId, userId)
                .map(v -> v.weight()).orElse(null);
        List<InlineCitationRef> citations = nodeSourceRepository.findInlineCitationsForNode(nodeId);
        return DtoMappers.toResponse(node, stats, userVote, citations);
    }

    /**
     * Bring to front: ставит узел на передний план через присваивание
     * нового z_index = max(z_index узлов темы) + 1. Endpoint dedicated -
     * клиенту не нужно знать max, сервер сам вычисляет. Запрос без тела.
     */
    @PostMapping("/{nodeId}/z-order/bring-to-front")
    public NodeResponse bringToFront(@PathVariable UUID nodeId,
                                     @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRole();
        Node node = nodeService.bringToFront(nodeId, userId, role);
        VoteStats stats = nodeVoteRepository.getStatsForNode(nodeId);
        Integer userVote = nodeVoteRepository.findByNodeAndUser(nodeId, userId)
                .map(v -> v.weight()).orElse(null);
        List<InlineCitationRef> citations = nodeSourceRepository.findInlineCitationsForNode(nodeId);
        return DtoMappers.toResponse(node, stats, userVote, citations);
    }

    /**
     * Send to back: ставит узел на задний план через присваивание z_index
     * = min(z_index узлов темы) - 1. Парный bring-to-front.
     */
    @PostMapping("/{nodeId}/z-order/send-to-back")
    public NodeResponse sendToBack(@PathVariable UUID nodeId,
                                   @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRole();
        Node node = nodeService.sendToBack(nodeId, userId, role);
        VoteStats stats = nodeVoteRepository.getStatsForNode(nodeId);
        Integer userVote = nodeVoteRepository.findByNodeAndUser(nodeId, userId)
                .map(v -> v.weight()).orElse(null);
        List<InlineCitationRef> citations = nodeSourceRepository.findInlineCitationsForNode(nodeId);
        return DtoMappers.toResponse(node, stats, userVote, citations);
    }

    @DeleteMapping("/{nodeId}")
    public ResponseEntity<Void> delete(@PathVariable UUID nodeId, @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRole();
        nodeService.deleteNode(nodeId, userId, role);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{nodeId}/revisions")
    public List<RevisionResponse> getRevisions(@PathVariable UUID nodeId,
                                               @CurrentUser UUID userId) {
        String role = SecurityContextUtils.currentRole();
        return nodeService.getRevisions(nodeId, userId, role).stream()
                .map(DtoMappers::toResponse).toList();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nullIfBlank(String s) {
        return isBlank(s) ? null : s;
    }
}
