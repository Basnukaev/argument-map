package ru.basnukaev.argumentmap.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.domain.Edge;
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.Topic;
import ru.basnukaev.argumentmap.exception.TopicNotFoundException;
import ru.basnukaev.argumentmap.repository.EdgeRepository;
import ru.basnukaev.argumentmap.repository.NodeRepository;
import ru.basnukaev.argumentmap.repository.TopicRepository;

@Service
public class GraphService {

    private final TopicRepository topicRepository;
    private final NodeRepository nodeRepository;
    private final EdgeRepository edgeRepository;

    public GraphService(TopicRepository topicRepository,
                        NodeRepository nodeRepository,
                        EdgeRepository edgeRepository) {
        this.topicRepository = topicRepository;
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
    }

    @Transactional(readOnly = true)
    public GraphView getGraph(UUID topicId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new TopicNotFoundException(topicId));
        List<Node> nodes = nodeRepository.findByTopicId(topicId);
        List<Edge> edges = edgeRepository.findByTopicId(topicId);
        return new GraphView(topic, nodes, edges);
    }
}
