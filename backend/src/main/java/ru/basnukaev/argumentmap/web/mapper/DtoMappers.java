package ru.basnukaev.argumentmap.web.mapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.domain.Authority;
import ru.basnukaev.argumentmap.domain.CitationDetail;
import ru.basnukaev.argumentmap.domain.Edge;
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.NodeSource;
import ru.basnukaev.argumentmap.domain.Revision;
import ru.basnukaev.argumentmap.domain.Source;
import ru.basnukaev.argumentmap.domain.Topic;
import ru.basnukaev.argumentmap.domain.TopicMember;
import ru.basnukaev.argumentmap.domain.VoteStats;
import ru.basnukaev.argumentmap.repository.NodeSourceRepository;
import ru.basnukaev.argumentmap.repository.TopicWithCounts;
import ru.basnukaev.argumentmap.service.GraphView;
import ru.basnukaev.argumentmap.web.dto.AuthorityCitationRef;
import ru.basnukaev.argumentmap.web.dto.AuthorityResponse;
import ru.basnukaev.argumentmap.web.dto.BookCitationRef;
import ru.basnukaev.argumentmap.web.dto.CitationResponse;
import ru.basnukaev.argumentmap.web.dto.EdgeResponse;
import ru.basnukaev.argumentmap.web.dto.GraphResponse;
import ru.basnukaev.argumentmap.web.dto.LocationRef;
import ru.basnukaev.argumentmap.web.dto.MuhaqqiqRef;
import ru.basnukaev.argumentmap.web.dto.NodeResponse;
import ru.basnukaev.argumentmap.web.dto.NodeSourceResponse;
import ru.basnukaev.argumentmap.web.dto.PdfRef;
import ru.basnukaev.argumentmap.web.dto.PublicationPlaceRef;
import ru.basnukaev.argumentmap.web.dto.PublisherRef;
import ru.basnukaev.argumentmap.web.dto.RegionRef;
import ru.basnukaev.argumentmap.web.dto.RevisionResponse;
import ru.basnukaev.argumentmap.web.dto.SourceResponse;
import ru.basnukaev.argumentmap.web.dto.TopicMemberResponse;
import ru.basnukaev.argumentmap.web.dto.TopicResponse;

public final class DtoMappers {

    /**
     * ObjectMapper для конвертации jsonb-колонок (хранятся как String)
     * ↔ {@link JsonNode} в DTO. Отдельный инстанс — нет необходимости
     * в кастомных модулях Spring'овского, для readTree/toString их хватает.
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    private DtoMappers() {
    }

    public static TopicResponse toResponse(Topic topic) {
        return toResponse(topic, 0, 0);
    }

    public static TopicResponse toResponse(Topic topic, int nodeCount, int edgeCount) {
        return new TopicResponse(
                topic.id(), topic.title(), topic.description(),
                topic.rootNodeId(), topic.createdBy(), topic.createdAt(),
                topic.visibility(),
                nodeCount, edgeCount
        );
    }

    public static TopicResponse toResponse(TopicWithCounts twc) {
        return toResponse(twc.topic(), twc.nodeCount(), twc.edgeCount());
    }

    public static TopicMemberResponse toResponse(TopicMember member) {
        return new TopicMemberResponse(
                member.id(), member.topicId(), member.userId(),
                member.role(), member.addedAt(), member.addedBy()
        );
    }

    /**
     * Mapper для одного узла без vote-данных. Используется когда vote-агрегация
     * не нужна (например пара legacy IT-кейсов через прямую реконструкцию).
     * Vote-поля заполняются нулями, userVote = null. Если важна актуальная
     * статистика - использовать перегрузку с VoteStats/userVote.
     */
    public static NodeResponse toResponse(Node node) {
        return toResponse(node, VoteStats.EMPTY, null);
    }

    public static NodeResponse toResponse(Node node, VoteStats stats, Integer userVote) {
        VoteStats s = stats == null ? VoteStats.EMPTY : stats;
        return new NodeResponse(
                node.id(), node.topicId(), node.nodeType(), node.content(),
                node.status(),
                node.posX(), node.posY(), node.zIndex(),
                node.createdBy(),
                node.createdAt(), node.updatedAt(),
                s.upvotes(), s.downvotes(), s.score(),
                userVote
        );
    }

    public static EdgeResponse toResponse(Edge edge) {
        return new EdgeResponse(
                edge.id(), edge.fromNodeId(), edge.toNodeId(),
                edge.edgeType(), edge.rationale(),
                edge.sourceHandle(), edge.targetHandle(),
                edge.createdBy(), edge.createdAt()
        );
    }

    public static RevisionResponse toResponse(Revision revision) {
        return new RevisionResponse(
                revision.id(), revision.nodeId(),
                revision.contentBefore(), revision.contentAfter(),
                revision.changedBy(), revision.changedAt()
        );
    }

    public static GraphResponse toResponse(GraphView graph) {
        return toResponse(graph, Map.of(), Map.of());
    }

    /**
     * Перегрузка для GraphView с vote-данными. statsByNode/userVotesByNode -
     * bulk-загруженные maps из {@link ru.basnukaev.argumentmap.repository.NodeVoteRepository}.
     * Отсутствующий nodeId в map трактуется как {@link VoteStats#EMPTY} /
     * userVote=null. Один SQL на весь граф - не N+1.
     */
    public static GraphResponse toResponse(GraphView graph,
                                           Map<UUID, VoteStats> statsByNode,
                                           Map<UUID, Integer> userVotesByNode) {
        Map<UUID, VoteStats> stats = statsByNode == null ? Map.of() : statsByNode;
        Map<UUID, Integer> userVotes = userVotesByNode == null ? Map.of() : userVotesByNode;
        List<NodeResponse> nodes = graph.nodes().stream()
                .map(n -> toResponse(n, stats.getOrDefault(n.id(), VoteStats.EMPTY), userVotes.get(n.id())))
                .toList();
        List<EdgeResponse> edges = graph.edges().stream().map(DtoMappers::toResponse).toList();
        return new GraphResponse(toResponse(graph.topic()), nodes, edges);
    }

    public static SourceResponse toResponse(Source source) {
        return new SourceResponse(
                source.id(), source.sourceType(), source.title(),
                source.citation(), source.reliability(),
                source.authorityId(),
                source.bookId(),
                jsonFromString(source.metadata()),
                source.createdAt()
        );
    }

    public static AuthorityResponse toResponse(Authority authority) {
        return new AuthorityResponse(
                authority.id(), authority.name(), authority.bio(),
                authority.era(), authority.madhab(),
                jsonFromString(authority.metadata()),
                authority.createdAt(),
                authority.fullName(),
                authority.deathYearHijri()
        );
    }

    /**
     * Mapper для GET endpoints (ADR-028) - использует structured CitationDetail
     * из 9-JOIN SQL. Frontend получает nullable nested refs и рендерит каждое
     * поле в своём блоке.
     */
    public static NodeSourceResponse toResponse(NodeSourceRepository.NodeSourceWithLocation row) {
        NodeSource link = row.ns();
        // legacySnapshot - заполняется только для LEGACY mode (freeform citation
        // через AddSourceModal). Для TEXT/PDF/REGION snapshot хранится в БД для
        // forensic трейса, но не отдаётся клиенту - там есть structured citation
        String legacySnapshot = link.mode() == ru.basnukaev.argumentmap.domain.CitationMode.LEGACY
                ? link.location()
                : null;
        return new NodeSourceResponse(
                link.id(),
                link.nodeId(),
                link.sourceId(),
                link.quote(),
                link.context(),
                link.mode(),
                toCitationResponse(row.citation()),
                legacySnapshot,
                link.createdAt()
        );
    }

    public static CitationResponse toCitationResponse(CitationDetail c) {
        if (c == null) return null;
        return new CitationResponse(
                toAuthorityRef(c),
                toBookRef(c),
                toMuhaqqiqRef(c),
                toPublisherRef(c),
                toPublicationPlaceRef(c),
                toLocationRef(c),
                toPdfRef(c),
                toRegionRef(c)
        );
    }

    private static AuthorityCitationRef toAuthorityRef(CitationDetail c) {
        if (c.authorityId() == null) return null;
        return new AuthorityCitationRef(
                c.authorityId(), c.authorityName(),
                c.authorFullName(), c.authorDeathYearHijri()
        );
    }

    private static BookCitationRef toBookRef(CitationDetail c) {
        if (c.bookId() == null) return null;
        return new BookCitationRef(
                c.bookId(), c.bookTitle(), c.bookLanguage(),
                c.editionNumber(), c.publishedYearHijri(), c.publishedYearGregorian()
        );
    }

    private static MuhaqqiqRef toMuhaqqiqRef(CitationDetail c) {
        if (c.muhaqqiqId() == null) return null;
        return new MuhaqqiqRef(c.muhaqqiqId(), c.muhaqqiqName(), c.muhaqqiqFullName());
    }

    private static PublisherRef toPublisherRef(CitationDetail c) {
        if (c.publisherId() == null) return null;
        return new PublisherRef(c.publisherId(), c.publisherName());
    }

    private static PublicationPlaceRef toPublicationPlaceRef(CitationDetail c) {
        if (c.publicationPlaceId() == null) return null;
        return new PublicationPlaceRef(c.publicationPlaceId(), c.publicationPlaceName());
    }

    private static LocationRef toLocationRef(CitationDetail c) {
        if (c.pageId() == null) return null;
        return new LocationRef(
                c.pageId(), c.part(), c.printedPage(),
                c.pageNumber(), c.rangeStart(), c.rangeEnd()
        );
    }

    private static PdfRef toPdfRef(CitationDetail c) {
        if (c.pdfFileId() == null) return null;
        return new PdfRef(c.pdfFileId(), c.pdfPageNumber(), jsonFromString(c.pdfBbox()));
    }

    private static RegionRef toRegionRef(CitationDetail c) {
        if (c.imageRegionId() == null) return null;
        return new RegionRef(c.imageRegionId(), c.regionPrintedPage(), c.regionPageNumber());
    }

    /**
     * Сериализует JsonNode в JSON-строку для записи в jsonb-колонку.
     * Возвращает null если на входе null.
     */
    public static String jsonToString(JsonNode node) {
        return node == null ? null : node.toString();
    }

    private static JsonNode jsonFromString(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return JSON.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "БД содержит невалидный JSON в jsonb-колонке: " + raw, e
            );
        }
    }
}
