package ru.basnukaev.argumentmap.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.domain.Authority;
import ru.basnukaev.argumentmap.domain.Edge;
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.NodeSource;
import ru.basnukaev.argumentmap.domain.Source;
import ru.basnukaev.argumentmap.domain.Topic;
import ru.basnukaev.argumentmap.exception.TopicNotFoundException;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.repository.AuthorityRepository;
import ru.basnukaev.argumentmap.repository.EdgeRepository;
import ru.basnukaev.argumentmap.repository.NodeRepository;
import ru.basnukaev.argumentmap.repository.NodeSourceRepository;
import ru.basnukaev.argumentmap.repository.SourceRepository;
import ru.basnukaev.argumentmap.repository.TopicRepository;
import ru.basnukaev.argumentmap.web.dto.TopicExportDto;
import ru.basnukaev.argumentmap.web.dto.TopicExportDto.AuthorityData;
import ru.basnukaev.argumentmap.web.dto.TopicExportDto.BookRef;
import ru.basnukaev.argumentmap.web.dto.TopicExportDto.EdgeData;
import ru.basnukaev.argumentmap.web.dto.TopicExportDto.NodeData;
import ru.basnukaev.argumentmap.web.dto.TopicExportDto.NodeSourceData;
import ru.basnukaev.argumentmap.web.dto.TopicExportDto.SourceData;
import ru.basnukaev.argumentmap.web.dto.TopicExportDto.TopicData;

/**
 * Сборка {@link TopicExportDto} из доменных entity (Этап 6, ADR-037).
 *
 * <p>Алгоритм:
 * <ol>
 *   <li>Загрузить topic / nodes / edges по {@code topicId}</li>
 *   <li>Для каждого node загрузить его node_sources (без 9-LEFT-JOIN -
 *       structured citation восстанавливается при импорте локально)</li>
 *   <li>Собрать unique source-id из всех node_sources → загрузить sources</li>
 *   <li>Собрать unique authority-id из sources → загрузить authorities</li>
 *   <li>Собрать unique book-id из sources → загрузить books (hint only)</li>
 * </ol>
 *
 * <p>Revisions намеренно исключены - история не нужна для backup/обмена,
 * увеличила бы размер в 10x.
 */
@Service
public class TopicExportService {

    /**
     * Текущая версия формата экспорта. При breaking changes - новый номер +
     * расширение whitelist'а в {@code TopicImportService}.
     */
    public static final String CURRENT_FORMAT_VERSION = "1.0";

    private final TopicRepository topicRepository;
    private final NodeRepository nodeRepository;
    private final EdgeRepository edgeRepository;
    private final NodeSourceRepository nodeSourceRepository;
    private final SourceRepository sourceRepository;
    private final AuthorityRepository authorityRepository;
    private final BookRepository bookRepository;

    public TopicExportService(TopicRepository topicRepository,
                              NodeRepository nodeRepository,
                              EdgeRepository edgeRepository,
                              NodeSourceRepository nodeSourceRepository,
                              SourceRepository sourceRepository,
                              AuthorityRepository authorityRepository,
                              BookRepository bookRepository) {
        this.topicRepository = topicRepository;
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.nodeSourceRepository = nodeSourceRepository;
        this.sourceRepository = sourceRepository;
        this.authorityRepository = authorityRepository;
        this.bookRepository = bookRepository;
    }

    @Transactional(readOnly = true)
    public TopicExportDto exportTopic(UUID topicId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new TopicNotFoundException(topicId));

        List<Node> nodes = nodeRepository.findByTopicId(topicId);
        List<Edge> edges = edgeRepository.findByTopicId(topicId);

        // batch: все node_sources за один SQL (не N findByNodeId)
        List<UUID> nodeIds = nodes.stream().map(Node::id).toList();
        List<NodeSource> allNodeSources = nodeSourceRepository.findByNodeIds(nodeIds);

        // unique source-id из всех node_sources
        // LinkedHashSet для стабильного порядка sources в экспорте (по
        // порядку первого появления, не random)
        Set<UUID> sourceIds = new LinkedHashSet<>();
        for (NodeSource ns : allNodeSources) {
            sourceIds.add(ns.sourceId());
        }

        // batch: все sources за один SQL
        Map<UUID, Source> sourcesById = new LinkedHashMap<>();
        for (Source s : sourceRepository.findByIds(sourceIds)) {
            sourcesById.put(s.id(), s);
        }

        // unique authority-id из sources (sources.authority_id может быть
        // null - skip null)
        Set<UUID> authorityIds = new LinkedHashSet<>();
        for (Source s : sourcesById.values()) {
            if (s.authorityId() != null) {
                authorityIds.add(s.authorityId());
            }
        }
        // batch: все authorities за один SQL
        Map<UUID, Authority> authoritiesById = new LinkedHashMap<>();
        for (Authority a : authorityRepository.findByIds(authorityIds)) {
            authoritiesById.put(a.id(), a);
        }

        // unique book-id из sources (для hint при импорте)
        Set<UUID> bookIds = new LinkedHashSet<>();
        for (Source s : sourcesById.values()) {
            if (s.bookId() != null) {
                bookIds.add(s.bookId());
            }
        }
        // batch: все books за один SQL
        Map<UUID, Book> booksById = new LinkedHashMap<>();
        for (Book b : bookRepository.findByIds(bookIds)) {
            booksById.put(b.id(), b);
        }

        return new TopicExportDto(
                CURRENT_FORMAT_VERSION,
                Instant.now(),
                toTopicData(topic),
                nodes.stream().map(TopicExportService::toNodeData).toList(),
                edges.stream().map(TopicExportService::toEdgeData).toList(),
                allNodeSources.stream().map(TopicExportService::toNodeSourceData).toList(),
                sourcesById.values().stream().map(TopicExportService::toSourceData).toList(),
                authoritiesById.values().stream().map(TopicExportService::toAuthorityData).toList(),
                booksById.values().stream().map(TopicExportService::toBookRef).toList()
        );
    }

    private static TopicData toTopicData(Topic t) {
        return new TopicData(
                t.id(), t.title(), t.description(),
                t.rootNodeId(), t.createdBy(), t.createdAt()
        );
    }

    private static NodeData toNodeData(Node n) {
        return new NodeData(
                n.id(), n.topicId(),
                n.nodeType().name(), n.content(), n.status().name(),
                n.posX(), n.posY(),
                n.createdBy(), n.createdAt(), n.updatedAt()
        );
    }

    private static EdgeData toEdgeData(Edge e) {
        return new EdgeData(
                e.id(), e.fromNodeId(), e.toNodeId(),
                e.edgeType().name(), e.rationale(),
                e.sourceHandle(), e.targetHandle(),
                e.createdBy(), e.createdAt()
        );
    }

    private static NodeSourceData toNodeSourceData(NodeSource ns) {
        return new NodeSourceData(
                ns.id(), ns.nodeId(), ns.sourceId(),
                ns.quote(), ns.context(), ns.location(),
                ns.pageId(), ns.rangeStart(), ns.rangeEnd(),
                ns.pdfFileId(), ns.pdfPageNumber(), ns.pdfBbox(),
                ns.imageRegionId(),
                ns.createdAt()
        );
    }

    private static SourceData toSourceData(Source s) {
        return new SourceData(
                s.id(), s.sourceType().name(), s.title(), s.citation(),
                Optional.ofNullable(s.reliability()).map(Enum::name).orElse(null),
                s.authorityId(), s.bookId(), s.metadata(),
                s.createdAt()
        );
    }

    private static AuthorityData toAuthorityData(Authority a) {
        return new AuthorityData(
                a.id(), a.name(), a.bio(), a.era(), a.madhab(),
                a.metadata(), a.createdAt(),
                a.fullName(), a.deathYearHijri()
        );
    }

    private static BookRef toBookRef(Book b) {
        return new BookRef(b.id(), b.title(), b.authorityId());
    }
}
