package ru.basnukaev.argumentmap.web.mapper;

import java.util.List;

import ru.basnukaev.argumentmap.domain.Edge;
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.Revision;
import ru.basnukaev.argumentmap.domain.Topic;
import ru.basnukaev.argumentmap.service.GraphView;
import ru.basnukaev.argumentmap.web.dto.EdgeResponse;
import ru.basnukaev.argumentmap.web.dto.GraphResponse;
import ru.basnukaev.argumentmap.web.dto.NodeResponse;
import ru.basnukaev.argumentmap.web.dto.RevisionResponse;
import ru.basnukaev.argumentmap.web.dto.TopicResponse;

public final class DtoMappers {

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
                node.status(), node.weight(), node.createdBy(),
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
}
