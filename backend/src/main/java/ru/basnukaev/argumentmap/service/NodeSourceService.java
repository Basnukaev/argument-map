package ru.basnukaev.argumentmap.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.domain.NodeSource;
import ru.basnukaev.argumentmap.exception.NodeNotFoundException;
import ru.basnukaev.argumentmap.exception.SourceNotFoundException;
import ru.basnukaev.argumentmap.repository.NodeRepository;
import ru.basnukaev.argumentmap.repository.NodeSourceRepository;
import ru.basnukaev.argumentmap.repository.SourceRepository;

@Service
public class NodeSourceService {

    private final NodeSourceRepository nodeSourceRepository;
    private final NodeRepository nodeRepository;
    private final SourceRepository sourceRepository;

    public NodeSourceService(NodeSourceRepository nodeSourceRepository,
                             NodeRepository nodeRepository,
                             SourceRepository sourceRepository) {
        this.nodeSourceRepository = nodeSourceRepository;
        this.nodeRepository = nodeRepository;
        this.sourceRepository = sourceRepository;
    }

    @Transactional
    public NodeSource attachSource(UUID nodeId, UUID sourceId, String quote, String context) {
        if (nodeRepository.findById(nodeId).isEmpty()) {
            throw new NodeNotFoundException(nodeId);
        }
        if (sourceRepository.findById(sourceId).isEmpty()) {
            throw new SourceNotFoundException(sourceId);
        }
        NodeSource link = new NodeSource(nodeId, sourceId, quote, context, Instant.now());
        nodeSourceRepository.save(link);
        return link;
    }

    @Transactional(readOnly = true)
    public List<NodeSource> getNodeSources(UUID nodeId) {
        if (nodeRepository.findById(nodeId).isEmpty()) {
            throw new NodeNotFoundException(nodeId);
        }
        return nodeSourceRepository.findByNodeId(nodeId);
    }

    @Transactional
    public void detachSource(UUID nodeId, UUID sourceId) {
        boolean removed = nodeSourceRepository.delete(nodeId, sourceId);
        if (!removed) {
            throw new SourceNotFoundException(sourceId);
        }
    }
}
