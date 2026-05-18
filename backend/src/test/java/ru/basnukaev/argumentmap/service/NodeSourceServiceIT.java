package ru.basnukaev.argumentmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.NodeSource;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.domain.Source;
import ru.basnukaev.argumentmap.domain.SourceType;
import ru.basnukaev.argumentmap.exception.NodeNotFoundException;
import ru.basnukaev.argumentmap.exception.SourceNotFoundException;
import ru.basnukaev.argumentmap.repository.NodeSourceRepository;

/**
 * IT для {@link NodeSourceService} - покрывает attach/getNodeSources/detach.
 * До audit прямого теста не было, покрытие шло косвенно через
 * NodeSourceControllerIT. Здесь явная проверка edge cases (несуществующий
 * node, source, detach по несуществующему link id).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class NodeSourceServiceIT {

    @Autowired
    private NodeSourceService nodeSourceService;

    @Autowired
    private NodeService nodeService;

    @Autowired
    private SourceService sourceService;

    @Autowired
    private NodeSourceRepository nodeSourceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID topicId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "u-" + userId, userId + "@test.com"
        );
        topicId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by) VALUES (?, ?, ?)",
                topicId, "T", userId
        );
    }

    @Test
    void attachSource_whenNodeMissing_throwsNodeNotFoundException() {
        Source s = sourceService.createSource(SourceType.BOOK, "Книга", null, null, null, null, null);
        UUID missingNode = UUID.randomUUID();

        assertThatThrownBy(() -> nodeSourceService.attachSource(
                missingNode, s.id(), "цитата", "контекст", "стр. 10"
        )).isInstanceOf(NodeNotFoundException.class);
    }

    @Test
    void attachSource_whenSourceMissing_throwsSourceNotFoundException() {
        Node node = nodeService.createNode(topicId, NodeType.CLAIM, "тезис", userId);
        UUID missingSource = UUID.randomUUID();

        assertThatThrownBy(() -> nodeSourceService.attachSource(
                node.id(), missingSource, "цитата", null, null
        )).isInstanceOf(SourceNotFoundException.class);
    }

    @Test
    void attachSource_validNodeAndSource_persistsLink() {
        Node node = nodeService.createNode(topicId, NodeType.CLAIM, "тезис", userId);
        Source s = sourceService.createSource(SourceType.QURAN, "Коран", null, null, null, null, null);

        NodeSource link = nodeSourceService.attachSource(
                node.id(), s.id(), "цитата", "контекст", "сура 2:255"
        );

        assertThat(link.id()).isNotNull();
        assertThat(link.nodeId()).isEqualTo(node.id());
        assertThat(link.sourceId()).isEqualTo(s.id());
        assertThat(link.quote()).isEqualTo("цитата");

        List<NodeSource> list = nodeSourceService.getNodeSources(node.id());
        assertThat(list).hasSize(1);
        assertThat(list.get(0).id()).isEqualTo(link.id());
    }

    @Test
    void getNodeSources_whenNodeMissing_throws() {
        assertThatThrownBy(() -> nodeSourceService.getNodeSources(UUID.randomUUID()))
                .isInstanceOf(NodeNotFoundException.class);
    }

    @Test
    void getNodeSourcesWithLocation_whenNodeMissing_throws() {
        assertThatThrownBy(() -> nodeSourceService.getNodeSourcesWithLocation(UUID.randomUUID()))
                .isInstanceOf(NodeNotFoundException.class);
    }

    @Test
    void detachById_whenMissing_throwsSourceNotFoundException() {
        // Использует SourceNotFoundException (legacy naming для "ссылка не
        // существует") - покрываем чтобы зафиксировать текущий контракт.
        assertThatThrownBy(() -> nodeSourceService.detachById(UUID.randomUUID()))
                .isInstanceOf(SourceNotFoundException.class);
    }

    @Test
    void detachById_validId_removesLink() {
        Node node = nodeService.createNode(topicId, NodeType.CLAIM, "T", userId);
        Source s = sourceService.createSource(SourceType.BOOK, "B", null, null, null, null, null);
        NodeSource link = nodeSourceService.attachSource(node.id(), s.id(), "q", null, null);

        nodeSourceService.detachById(link.id());

        assertThat(nodeSourceService.getNodeSources(node.id())).isEmpty();
    }

    @Test
    void attachSource_multipleSourcesOnSameNode_keepsAll() {
        // После миграции 25 (FK variant A) суррогатный id PK позволяет N
        // citations из одного source на одном узле. Проверяем что service
        // действительно вернёт оба link'а.
        Node node = nodeService.createNode(topicId, NodeType.CLAIM, "T", userId);
        Source s = sourceService.createSource(SourceType.BOOK, "B", null, null, null, null, null);

        nodeSourceService.attachSource(node.id(), s.id(), "цитата 1", null, "стр. 1");
        nodeSourceService.attachSource(node.id(), s.id(), "цитата 2", null, "стр. 50");

        List<NodeSource> list = nodeSourceService.getNodeSources(node.id());
        assertThat(list).hasSize(2);
        assertThat(list).extracting(NodeSource::quote).containsExactlyInAnyOrder("цитата 1", "цитата 2");
    }
}
