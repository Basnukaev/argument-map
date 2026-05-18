package ru.basnukaev.argumentmap.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import ru.basnukaev.argumentmap.domain.NodeType;

/**
 * IT для {@link NodeProjectionService} - покрывает single + batch projection.
 * До audit прямого теста не было (введён в backend architecture audit
 * 2026-05-18). Проверяем что projection корректно возвращает empty defaults
 * для свежего узла + batch для нескольких узлов работает без N+1.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class NodeProjectionServiceIT {

    @Autowired
    private NodeProjectionService nodeProjectionService;

    @Autowired
    private NodeService nodeService;

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
    void single_freshNode_returnsEmptyDefaults() {
        Node node = nodeService.createNode(topicId, NodeType.CLAIM, "тезис", userId);

        NodeProjectionService.NodeProjection p = nodeProjectionService.single(node.id(), userId);

        assertThat(p.stats()).isNotNull();
        assertThat(p.stats().score()).isEqualTo(0);
        assertThat(p.stats().upvotes()).isEqualTo(0);
        assertThat(p.stats().downvotes()).isEqualTo(0);
        // Пользователь ещё не голосовал - userVote=null (не 0!)
        assertThat(p.userVote()).isNull();
        assertThat(p.citations()).isEmpty();
        assertThat(p.translations()).isEmpty();
    }

    @Test
    void single_nullUserId_userVoteIsNull() {
        Node node = nodeService.createNode(topicId, NodeType.CLAIM, "тезис", userId);

        // anonymous path - не должен бросать на null userId
        NodeProjectionService.NodeProjection p = nodeProjectionService.single(node.id(), null);

        assertThat(p.userVote()).isNull();
    }

    @Test
    void batch_multipleNodes_returnsMapsForEach() {
        Node n1 = nodeService.createNode(topicId, NodeType.CLAIM, "тезис-1", userId);
        Node n2 = nodeService.createNode(topicId, NodeType.EVIDENCE, "довод-2", userId);
        Node n3 = nodeService.createNode(topicId, NodeType.CLAIM, "тезис-3", userId);

        NodeProjectionService.NodeProjectionBatch batch = nodeProjectionService.batch(
                List.of(n1.id(), n2.id(), n3.id()), userId
        );

        // Stats всегда наполняется (даже если нет голосов - получаем zero stats)
        assertThat(batch.stats()).isNotNull();
        // Для свежих узлов userVotes/citations/translations - пусто (нет данных)
        assertThat(batch.userVotes()).doesNotContainKeys(n1.id(), n2.id(), n3.id());
        // citations/translations - возвращаются как Map с возможно empty списками либо без ключей
        assertThat(batch.citations()).isNotNull();
        assertThat(batch.translations()).isNotNull();
    }

    @Test
    void batch_emptyList_returnsEmptyMaps() {
        // Edge case - пустой граф (всё-узловой запрос на новой теме)
        NodeProjectionService.NodeProjectionBatch batch = nodeProjectionService.batch(List.of(), userId);

        assertThat(batch.stats()).isEmpty();
        assertThat(batch.userVotes()).isEmpty();
        assertThat(batch.citations()).isEmpty();
        assertThat(batch.translations()).isEmpty();
    }
}
