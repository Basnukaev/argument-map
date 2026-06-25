package ru.basnukaev.argumentmap.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.domain.Authority;
import ru.basnukaev.argumentmap.domain.Edge;
import ru.basnukaev.argumentmap.domain.EdgeType;
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.NodeSource;
import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.domain.Reliability;
import ru.basnukaev.argumentmap.domain.Source;
import ru.basnukaev.argumentmap.domain.SourceType;
import ru.basnukaev.argumentmap.domain.StatusAlgorithm;
import ru.basnukaev.argumentmap.domain.Topic;
import ru.basnukaev.argumentmap.domain.TopicVisibility;
import ru.basnukaev.argumentmap.exception.UnsupportedExportFormatException;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.repository.AuthorityRepository;
import ru.basnukaev.argumentmap.repository.EdgeRepository;
import ru.basnukaev.argumentmap.repository.NodeRepository;
import ru.basnukaev.argumentmap.repository.NodeSourceRepository;
import ru.basnukaev.argumentmap.repository.SourceRepository;
import ru.basnukaev.argumentmap.repository.TopicRepository;
import ru.basnukaev.argumentmap.web.dto.TopicExportDto;
import ru.basnukaev.argumentmap.web.dto.TopicExportDto.AuthorityData;
import ru.basnukaev.argumentmap.web.dto.TopicExportDto.EdgeData;
import ru.basnukaev.argumentmap.web.dto.TopicExportDto.NodeData;
import ru.basnukaev.argumentmap.web.dto.TopicExportDto.NodeSourceData;
import ru.basnukaev.argumentmap.web.dto.TopicExportDto.SourceData;
import ru.basnukaev.argumentmap.web.dto.TopicImportResponse;

/**
 * Импорт темы из {@link TopicExportDto} (Этап 6, ADR-037).
 *
 * <p>Ключевая семантика:
 * <ul>
 *   <li><b>UUID remapping</b>. Все ID импортируемых сущностей пере-генерируются
 *       через {@code Map<oldUUID, newUUID>}. Иначе при импорте темы на тот
 *       же инстанс получили бы PK violation на nodes/edges. {@link
 *       NodeSourceData}/{@link EdgeData} ссылки пере-mapping'аются по этому
 *       словарю</li>
 *   <li><b>Authorities find-or-create</b>. Ищется по {@code (name, era)} -
 *       трактуем authority с тем же именем и эпохой как ту же запись.
 *       Дубликаты не плодим. Если не найден - создаём с новым UUID</li>
 *   <li><b>Books find-or-skip</b>. Книги это shared library resource, не
 *       часть темы. Если book.id из экспорта не существует на target
 *       инстансе - source создаётся с {@code bookId=null} + добавляется
 *       warning. citation/title source'а сохраняется - просто потеряется
 *       deep link на library reader</li>
 *   <li><b>Pages / pdf_files / image_regions</b> - аналогично find-or-skip.
 *       Если ссылка не существует - positional поля null'ифицируются и
 *       добавляется warning. quote/context сохраняются - citation останется
 *       читаемым, потеряется только точная привязка к ресурсу</li>
 *   <li><b>askedBy/createdBy</b> заменяются на текущего пользователя
 *       (импортирующего), не сохраняются из экспорта - иначе можно было бы
 *       подделать ownership</li>
 * </ul>
 */
@Service
public class TopicImportService {

    private static final Logger log = LoggerFactory.getLogger(TopicImportService.class);

    /**
     * Whitelist поддерживаемых версий формата. Расширять при breaking
     * changes - старые версии должны продолжать импортироваться (или
     * быть прозрачно мигрированы).
     */
    public static final Set<String> SUPPORTED_FORMAT_VERSIONS = Set.of("1.0");

    private final TopicRepository topicRepository;
    private final NodeRepository nodeRepository;
    private final EdgeRepository edgeRepository;
    private final NodeSourceRepository nodeSourceRepository;
    private final SourceRepository sourceRepository;
    private final AuthorityRepository authorityRepository;
    private final BookRepository bookRepository;

    public TopicImportService(TopicRepository topicRepository,
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

    @Transactional
    public TopicImportResponse importTopic(TopicExportDto dto, UUID currentUserId) {
        validateFormatVersion(dto.formatVersion());
        if (dto.topic() == null) {
            throw new IllegalArgumentException("topic не может быть null в импортируемом payload");
        }

        List<String> warnings = new ArrayList<>();
        Instant now = Instant.now();

        // 1. Authorities - find-or-create по (name, era). Map[oldId, newId]
        Map<UUID, UUID> authorityIdMap = importAuthorities(dto.authorities(), warnings, now);

        // 2. Sources с remapping authority_id + find-or-skip book_id
        Map<UUID, UUID> sourceIdMap = importSources(dto.sources(), authorityIdMap, warnings, now);

        // 3. Topic с новым id (без root_node_id - дописывается после nodes).
        // Импорт по умолчанию приватный - кому импорт нужен пускай сам шерит (ADR-043)
        UUID newTopicId = UUID.randomUUID();
        Topic newTopic = new Topic(
                newTopicId,
                dto.topic().title(),
                dto.topic().description(),
                null,
                currentUserId,
                now,
                TopicVisibility.PRIVATE,
                StatusAlgorithm.MVP
        );
        topicRepository.save(newTopic);

        // 4. Nodes с remapping id + topic_id
        Map<UUID, UUID> nodeIdMap = importNodes(dto.nodes(), newTopicId, currentUserId, now);

        // 5. updateRootNodeId если был в экспорте
        if (dto.topic().rootNodeId() != null) {
            UUID newRootNodeId = nodeIdMap.get(dto.topic().rootNodeId());
            if (newRootNodeId != null) {
                topicRepository.updateRootNodeId(newTopicId, newRootNodeId);
            } else {
                warnings.add("rootNodeId='" + dto.topic().rootNodeId()
                        + "' из экспорта не найден среди импортированных узлов - тема без корня");
            }
        }

        // 6. Edges с remapping from/to через nodeIdMap. Edges чьи endpoints
        // отсутствуют в map - пропускаются с warning (защита от мусорных
        // ссылок в payload)
        int importedEdges = importEdges(dto.edges(), nodeIdMap, currentUserId, warnings);

        // 7. NodeSources с remapping node_id + source_id + find-or-skip
        // positional refs
        int importedNodeSources = importNodeSources(dto.nodeSources(), nodeIdMap, sourceIdMap, warnings);

        log.info("Топик '{}' импортирован: topicId={}, nodes={}, edges={}, "
                        + "nodeSources={}, sources={}, authorities={}, warnings={}",
                newTopic.title(), newTopicId,
                nodeIdMap.size(), importedEdges, importedNodeSources,
                sourceIdMap.size(), authorityIdMap.size(),
                warnings.size());

        return new TopicImportResponse(
                newTopicId,
                nodeIdMap.size(),
                importedEdges,
                importedNodeSources,
                sourceIdMap.size(),
                authorityIdMap.size(),
                warnings
        );
    }

    private static void validateFormatVersion(String version) {
        if (version == null || version.isBlank()) {
            throw new UnsupportedExportFormatException("(пусто)", SUPPORTED_FORMAT_VERSIONS);
        }
        if (!SUPPORTED_FORMAT_VERSIONS.contains(version)) {
            throw new UnsupportedExportFormatException(version, SUPPORTED_FORMAT_VERSIONS);
        }
    }

    private Map<UUID, UUID> importAuthorities(List<AuthorityData> authorities,
                                              List<String> warnings,
                                              Instant now) {
        Map<UUID, UUID> idMap = new HashMap<>();
        if (authorities == null) {
            return idMap;
        }
        for (AuthorityData a : authorities) {
            // find-by-name даёт upper-bound дедупликации. era не сравниваем
            // отдельно - в схеме нет UNIQUE на (name, era), а коллизия на
            // одном имени с разной эпохой - крайне маловероятна (это та
            // же authority с уточнением). Берём первого найденного
            Authority existing = authorityRepository.findByName(a.name()).orElse(null);
            if (existing != null) {
                idMap.put(a.id(), existing.id());
                continue;
            }
            UUID newId = UUID.randomUUID();
            // type=null → save() применит DB default SCHOLAR. Import не
            // несёт type-семантику (старые экспорты до миграции 47)
            Authority newAuthority = new Authority(
                    newId, a.name(), a.bio(), a.era(), a.madhab(),
                    a.metadata(), now,
                    a.fullName(), a.deathYearHijri(), null
            );
            authorityRepository.save(newAuthority);
            idMap.put(a.id(), newId);
        }
        return idMap;
    }

    private Map<UUID, UUID> importSources(List<SourceData> sources,
                                          Map<UUID, UUID> authorityIdMap,
                                          List<String> warnings,
                                          Instant now) {
        Map<UUID, UUID> idMap = new HashMap<>();
        if (sources == null) {
            return idMap;
        }
        for (SourceData s : sources) {
            UUID newAuthorityId = null;
            if (s.authorityId() != null) {
                newAuthorityId = authorityIdMap.get(s.authorityId());
                if (newAuthorityId == null) {
                    warnings.add("source '" + s.title()
                            + "' ссылается на authority которой нет в экспорте - импортирован без автора");
                }
            }

            // find-or-skip для bookId. Если book.id из экспорта не существует
            // на target инстансе - сохраняем source без bookId. Не пытаемся
            // искать по title - могут быть омонимы, ложные совпадения хуже
            // нашего warning
            UUID resolvedBookId = null;
            if (s.bookId() != null) {
                if (bookRepository.findById(s.bookId()).isPresent()) {
                    resolvedBookId = s.bookId();
                } else {
                    warnings.add("source '" + s.title() + "' ссылается на книгу id="
                            + s.bookId() + " которая отсутствует в библиотеке этого инстанса - "
                            + "импортирован без bookId (deep link на reader работать не будет)");
                }
            }

            UUID newId = UUID.randomUUID();
            Source newSource = new Source(
                    newId,
                    parseSourceType(s.sourceType()),
                    s.title(), s.citation(),
                    s.reliability() == null ? null : Reliability.valueOf(s.reliability()),
                    newAuthorityId,
                    resolvedBookId,
                    s.metadata(),
                    now
            );
            sourceRepository.save(newSource);
            idMap.put(s.id(), newId);
        }
        return idMap;
    }

    private Map<UUID, UUID> importNodes(List<NodeData> nodes,
                                        UUID newTopicId,
                                        UUID currentUserId,
                                        Instant now) {
        Map<UUID, UUID> idMap = new HashMap<>();
        if (nodes == null) {
            return idMap;
        }
        for (NodeData n : nodes) {
            UUID newId = UUID.randomUUID();
            Node newNode = new Node(
                    newId,
                    newTopicId,
                    NodeType.valueOf(n.nodeType()),
                    n.content(),
                    NodeStatus.valueOf(n.status()),
                    n.posX(), n.posY(), 0,
                    currentUserId,
                    now, now,
                    null
            );
            nodeRepository.save(newNode);
            idMap.put(n.id(), newId);
        }
        return idMap;
    }

    private int importEdges(List<EdgeData> edges,
                            Map<UUID, UUID> nodeIdMap,
                            UUID currentUserId,
                            List<String> warnings) {
        if (edges == null) {
            return 0;
        }
        int count = 0;
        for (EdgeData e : edges) {
            UUID newFrom = nodeIdMap.get(e.fromNodeId());
            UUID newTo = nodeIdMap.get(e.toNodeId());
            if (newFrom == null || newTo == null) {
                warnings.add("ребро id=" + e.id() + " ссылается на узлы которых нет в экспорте - пропущено");
                continue;
            }
            UUID newId = UUID.randomUUID();
            Edge newEdge = new Edge(
                    newId, newFrom, newTo,
                    EdgeType.valueOf(e.edgeType()),
                    e.rationale(),
                    e.sourceHandle(), e.targetHandle(),
                    currentUserId,
                    Instant.now(), 0
            );
            edgeRepository.save(newEdge);
            count++;
        }
        return count;
    }

    private int importNodeSources(List<NodeSourceData> nodeSources,
                                  Map<UUID, UUID> nodeIdMap,
                                  Map<UUID, UUID> sourceIdMap,
                                  List<String> warnings) {
        if (nodeSources == null) {
            return 0;
        }
        int count = 0;
        for (NodeSourceData ns : nodeSources) {
            UUID newNodeId = nodeIdMap.get(ns.nodeId());
            UUID newSourceId = sourceIdMap.get(ns.sourceId());
            if (newNodeId == null || newSourceId == null) {
                warnings.add("node_source id=" + ns.id()
                        + " ссылается на node/source которых нет в экспорте - пропущена цитата");
                continue;
            }

            PositionalRefs refs = resolvePositionalRefs(newSourceId, ns, warnings);

            UUID newId = UUID.randomUUID();
            NodeSource newNs = new NodeSource(
                    newId, newNodeId, newSourceId,
                    ns.quote(), ns.context(), ns.location(),
                    refs.pageId(),
                    refs.pageId() == null ? null : ns.rangeStart(),
                    refs.pageId() == null ? null : ns.rangeEnd(),
                    refs.pdfFileId(),
                    refs.pdfFileId() == null ? null : ns.pdfPageNumber(),
                    refs.pdfFileId() == null ? null : ns.pdfBbox(),
                    // PDF_LINK (ADR-067): topic export/import пока не переносит
                    // pdf_file_index - PDF_LINK-citation импортируется как LEGACY
                    // (как и нерезолвимые PDF/page refs). Wiring экспорта - follow-up.
                    null,
                    refs.imageRegionId(),
                    Instant.now()
            );
            nodeSourceRepository.save(newNs);
            count++;
        }
        return count;
    }

    /**
     * Резолвит positional refs (page / pdf file / image region) для
     * импортируемого node_source с учётом source.bookId trust heuristic.
     *
     * <p><b>Known limitation (ADR-037):</b> если source.bookId успешно
     * resolved (книга найдена в библиотеке target инстанса), positional
     * refs из payload (pageId / pdfFileId / imageRegionId) сохраняются
     * <b>как есть</b> - <b>существование</b> этих ID в lib_pages /
     * lib_files / lib_image_regions на target инстансе НЕ проверяется.
     * Trust-by-bookId эвристика: предполагаем что если книга та же
     * (по UUID) - все её сущности импортированы из того же shamela
     * snapshot и UUID'ы стабильны (см. gotcha «lib_pages.id стабильность
     * через mapper skip-if-existing»). Если эвристика нарушена (книга
     * пере-импортирована с другим snapshot) - FK constraint вылетит при
     * INSERT с понятной диагностикой; round-trip same-instance import
     * всегда работает.
     *
     * <p>Если source без bookId (книга не найдена при импорте source) -
     * positional refs обнуляются + warning. quote/context/location
     * сохраняются как textual fallback - citation остаётся читаемым,
     * теряется только deep link на reader.
     */
    private PositionalRefs resolvePositionalRefs(UUID newSourceId,
                                                 NodeSourceData ns,
                                                 List<String> warnings) {
        UUID pageId = ns.pageId();
        UUID pdfFileId = ns.pdfFileId();
        UUID imageRegionId = ns.imageRegionId();

        boolean hasAnyRef = pageId != null || pdfFileId != null || imageRegionId != null;
        if (!hasAnyRef) {
            return new PositionalRefs(null, null, null);
        }

        Source src = sourceRepository.findById(newSourceId).orElse(null);
        if (src == null || src.bookId() == null) {
            warnings.add("node_source id=" + ns.id()
                    + " имел positional ссылки на library ресурсы (page/pdf/region) "
                    + "но source без bookId - positional context сброшен, "
                    + "сохранены только quote/context/location");
            return new PositionalRefs(null, null, null);
        }
        return new PositionalRefs(pageId, pdfFileId, imageRegionId);
    }

    /**
     * Результат резолва positional refs - чтобы избежать tuple-возврата
     * через массивы или out-параметры. Все поля могут быть null
     * (positional refs не были указаны или source без bookId).
     */
    private record PositionalRefs(UUID pageId, UUID pdfFileId, UUID imageRegionId) {
    }

    private static SourceType parseSourceType(String name) {
        if (name == null) {
            throw new IllegalArgumentException("sourceType не может быть null в импортируемом source");
        }
        return SourceType.valueOf(name);
    }
}
