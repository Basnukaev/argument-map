package ru.basnukaev.argumentmap.web.mapper;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.domain.Authority;
import ru.basnukaev.argumentmap.domain.Edge;
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.NodeAuthority;
import ru.basnukaev.argumentmap.domain.NodeSource;
import ru.basnukaev.argumentmap.domain.Revision;
import ru.basnukaev.argumentmap.domain.Source;
import ru.basnukaev.argumentmap.domain.Topic;
import ru.basnukaev.argumentmap.service.GraphView;
import ru.basnukaev.argumentmap.web.dto.AuthorityResponse;
import ru.basnukaev.argumentmap.web.dto.EdgeResponse;
import ru.basnukaev.argumentmap.web.dto.GraphResponse;
import ru.basnukaev.argumentmap.web.dto.NodeAuthorityResponse;
import ru.basnukaev.argumentmap.web.dto.NodeResponse;
import ru.basnukaev.argumentmap.web.dto.NodeSourceResponse;
import ru.basnukaev.argumentmap.web.dto.RevisionResponse;
import ru.basnukaev.argumentmap.web.dto.SourceResponse;
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
        return new TopicResponse(
                topic.id(), topic.title(), topic.description(),
                topic.rootNodeId(), topic.createdBy(), topic.createdAt()
        );
    }

    public static NodeResponse toResponse(Node node) {
        return new NodeResponse(
                node.id(), node.topicId(), node.nodeType(), node.content(),
                node.status(), node.createdBy(),
                node.createdAt(), node.updatedAt()
        );
    }

    public static EdgeResponse toResponse(Edge edge) {
        return new EdgeResponse(
                edge.id(), edge.fromNodeId(), edge.toNodeId(),
                edge.edgeType(), edge.rationale(), edge.createdBy(), edge.createdAt()
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
        List<NodeResponse> nodes = graph.nodes().stream().map(DtoMappers::toResponse).toList();
        List<EdgeResponse> edges = graph.edges().stream().map(DtoMappers::toResponse).toList();
        return new GraphResponse(toResponse(graph.topic()), nodes, edges);
    }

    public static SourceResponse toResponse(Source source) {
        return new SourceResponse(
                source.id(), source.sourceType(), source.title(),
                source.citation(), source.reliability(),
                jsonFromString(source.metadata()),
                source.createdAt()
        );
    }

    public static AuthorityResponse toResponse(Authority authority) {
        return new AuthorityResponse(
                authority.id(), authority.name(), authority.bio(),
                authority.era(), authority.madhab(),
                jsonFromString(authority.metadata()),
                authority.createdAt()
        );
    }

    public static NodeSourceResponse toResponse(NodeSource link) {
        return new NodeSourceResponse(
                link.nodeId(), link.sourceId(),
                link.quote(), link.context(), link.createdAt()
        );
    }

    public static NodeAuthorityResponse toResponse(NodeAuthority link) {
        return new NodeAuthorityResponse(
                link.nodeId(), link.authorityId(), link.stance(), link.createdAt()
        );
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
