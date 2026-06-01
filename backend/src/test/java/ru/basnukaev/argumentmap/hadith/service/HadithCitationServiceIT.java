package ru.basnukaev.argumentmap.hadith.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.NodeSource;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.domain.Source;
import ru.basnukaev.argumentmap.domain.SourceType;
import ru.basnukaev.argumentmap.exception.TopicWriteAccessDeniedException;
import ru.basnukaev.argumentmap.hadith.domain.Collection;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.HadithStatus;
import ru.basnukaev.argumentmap.hadith.repository.CollectionRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.web.HadithNotFoundException;
import ru.basnukaev.argumentmap.repository.NodeSourceRepository;
import ru.basnukaev.argumentmap.repository.SourceRepository;
import ru.basnukaev.argumentmap.service.NodeService;

/**
 * IT для HadithCitationService (под-проект #2): прикрепление хадиса к узлу
 * через мост Source. Проверяет ensure-source, node_source link,
 * идемпотентность source, 404 и authz.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class HadithCitationServiceIT {

    @Autowired
    private HadithCitationService hadithCitationService;

    @Autowired
    private NodeService nodeService;

    @Autowired
    private CollectionRepository collectionRepository;

    @Autowired
    private HadithRepository hadithRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private NodeSourceRepository nodeSourceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ownerId;
    private UUID topicId;
    private UUID hadithId;

    @BeforeEach
    void setUp() {
        ownerId = insertUser();
        topicId = UUID.randomUUID();
        // PUBLIC: чужой USER может читать, но НЕ писать → тест бьёт именно
        // write-gate (PRIVATE отбил бы раньше на чтении)
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by, visibility) VALUES (?, ?, ?, 'PUBLIC')",
                topicId, "T", ownerId);

        Instant now = Instant.now();
        UUID collectionId = UUID.randomUUID();
        // уникальный slug — DevHadithSeeder уже занял "bukhari" (committed);
        // nameRu держим для проверки title источника
        collectionRepository.save(new Collection(collectionId, "hcit-coll",
                "صحيح البخاري", "Sahih al-Bukhari", "Сахих аль-Бухари", null, 7563, null, now));
        hadithId = UUID.randomUUID();
        hadithRepository.save(new Hadith(hadithId, collectionId, 1,
                "انما الاعمال بالنيات", HadithStatus.CANONICAL, null, null, now));
    }

    @Test
    void attach_creates_hadith_source_and_node_source_link() {
        Node node = nodeService.createNode(topicId, NodeType.CLAIM, "тезис", ownerId);

        NodeSource link = hadithCitationService.attachHadithToNode(
                node.id(), hadithId, ownerId, UserRole.USER);

        assertThat(link.nodeId()).isEqualTo(node.id());
        // создан Source типа HADITH с человекочитаемым title
        Source source = sourceRepository.findById(link.sourceId()).orElseThrow();
        assertThat(source.sourceType()).isEqualTo(SourceType.HADITH);
        assertThat(source.title()).isEqualTo("Сахих аль-Бухари №1");
        // мост проставлен: hd_hadiths.source_id
        assertThat(hadithRepository.findById(hadithId).orElseThrow().sourceId())
                .isEqualTo(link.sourceId());
        // node_source link существует
        assertThat(nodeSourceRepository.findByNodeId(node.id()))
                .extracting(NodeSource::sourceId)
                .contains(link.sourceId());
    }

    @Test
    void attach_reuses_existing_source_on_second_attach() {
        Node node = nodeService.createNode(topicId, NodeType.CLAIM, "тезис", ownerId);

        UUID firstSource = hadithCitationService
                .attachHadithToNode(node.id(), hadithId, ownerId, UserRole.USER).sourceId();
        UUID secondSource = hadithCitationService
                .attachHadithToNode(node.id(), hadithId, ownerId, UserRole.USER).sourceId();

        // один Source на хадис (мост переиспользуется), не плодим дубли
        assertThat(secondSource).isEqualTo(firstSource);
    }

    @Test
    void attach_unknown_hadith_throws_not_found() {
        Node node = nodeService.createNode(topicId, NodeType.CLAIM, "тезис", ownerId);

        assertThatThrownBy(() -> hadithCitationService.attachHadithToNode(
                node.id(), UUID.randomUUID(), ownerId, UserRole.USER))
                .isInstanceOf(HadithNotFoundException.class);
    }

    @Test
    void attach_by_non_writer_is_denied() {
        Node node = nodeService.createNode(topicId, NodeType.CLAIM, "тезис", ownerId);
        UUID otherUser = insertUser();

        // тема PRIVATE (owner=ownerId) → чужой USER не может писать
        assertThatThrownBy(() -> hadithCitationService.attachHadithToNode(
                node.id(), hadithId, otherUser, UserRole.USER))
                .isInstanceOf(TopicWriteAccessDeniedException.class);
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                id, "u-" + id, id + "@test.com");
        return id;
    }
}
