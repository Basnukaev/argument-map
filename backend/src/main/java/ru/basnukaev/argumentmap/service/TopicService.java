package ru.basnukaev.argumentmap.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.domain.Topic;
import ru.basnukaev.argumentmap.exception.TopicNotFoundException;
import ru.basnukaev.argumentmap.repository.NodeRepository;
import ru.basnukaev.argumentmap.repository.TopicRepository;
import ru.basnukaev.argumentmap.repository.TopicWithCounts;

@Service
public class TopicService {

    private final TopicRepository topicRepository;
    private final NodeRepository nodeRepository;

    public TopicService(TopicRepository topicRepository, NodeRepository nodeRepository) {
        this.topicRepository = topicRepository;
        this.nodeRepository = nodeRepository;
    }

    /**
     * Создаёт тему с корневым узлом-вопросом одной транзакцией.
     * Из-за циркулярного FK topics↔nodes тема сначала пишется без
     * root_node_id, затем создаётся узел, затем FK дописывается.
     * Откат любого шага откатывает все три (см. gotchas.md).
     */
    @Transactional
    public Topic createTopic(String title, String description,
                             String rootQuestionContent, UUID userId) {
        Instant now = Instant.now();

        Topic topic = new Topic(
                UUID.randomUUID(), title, description,
                null, userId, now
        );
        topicRepository.save(topic);

        Node rootQuestion = new Node(
                UUID.randomUUID(), topic.id(), NodeType.QUESTION,
                rootQuestionContent, NodeStatus.UNVERIFIED,
                null, null,
                userId, now, now
        );
        nodeRepository.save(rootQuestion);

        topicRepository.updateRootNodeId(topic.id(), rootQuestion.id());

        return topicRepository.findById(topic.id()).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Topic getTopic(UUID topicId) {
        return topicRepository.findById(topicId)
                .orElseThrow(() -> new TopicNotFoundException(topicId));
    }

    @Transactional(readOnly = true)
    public List<Topic> listTopics() {
        return topicRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<TopicWithCounts> listTopicsWithCounts() {
        return topicRepository.findAllWithCounts();
    }

    @Transactional(readOnly = true)
    public TopicWithCounts getTopicWithCounts(UUID topicId) {
        return topicRepository.findByIdWithCounts(topicId)
                .orElseThrow(() -> new TopicNotFoundException(topicId));
    }

    @Transactional
    public void deleteTopic(UUID topicId) {
        boolean removed = topicRepository.deleteById(topicId);
        if (!removed) {
            throw new TopicNotFoundException(topicId);
        }
    }
}
