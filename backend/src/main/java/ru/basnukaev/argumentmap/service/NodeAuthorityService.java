package ru.basnukaev.argumentmap.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.domain.NodeAuthority;
import ru.basnukaev.argumentmap.domain.Stance;
import ru.basnukaev.argumentmap.exception.AuthorityNotFoundException;
import ru.basnukaev.argumentmap.exception.NodeNotFoundException;
import ru.basnukaev.argumentmap.repository.AuthorityRepository;
import ru.basnukaev.argumentmap.repository.NodeAuthorityRepository;
import ru.basnukaev.argumentmap.repository.NodeRepository;

@Service
public class NodeAuthorityService {

    private final NodeAuthorityRepository nodeAuthorityRepository;
    private final NodeRepository nodeRepository;
    private final AuthorityRepository authorityRepository;

    public NodeAuthorityService(NodeAuthorityRepository nodeAuthorityRepository,
                                NodeRepository nodeRepository,
                                AuthorityRepository authorityRepository) {
        this.nodeAuthorityRepository = nodeAuthorityRepository;
        this.nodeRepository = nodeRepository;
        this.authorityRepository = authorityRepository;
    }

    @Transactional
    public NodeAuthority attachAuthority(UUID nodeId, UUID authorityId, Stance stance) {
        if (nodeRepository.findById(nodeId).isEmpty()) {
            throw new NodeNotFoundException(nodeId);
        }
        if (authorityRepository.findById(authorityId).isEmpty()) {
            throw new AuthorityNotFoundException(authorityId);
        }
        NodeAuthority link = new NodeAuthority(nodeId, authorityId, stance, Instant.now());
        nodeAuthorityRepository.save(link);
        return link;
    }

    @Transactional(readOnly = true)
    public List<NodeAuthority> getNodeAuthorities(UUID nodeId) {
        if (nodeRepository.findById(nodeId).isEmpty()) {
            throw new NodeNotFoundException(nodeId);
        }
        return nodeAuthorityRepository.findByNodeId(nodeId);
    }

    @Transactional
    public void detachAuthority(UUID nodeId, UUID authorityId) {
        boolean removed = nodeAuthorityRepository.delete(nodeId, authorityId);
        if (!removed) {
            throw new AuthorityNotFoundException(authorityId);
        }
    }
}
