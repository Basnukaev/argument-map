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
import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.NodeTranslation;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.exception.NodeNotFoundException;
import ru.basnukaev.argumentmap.exception.NodeTranslationDuplicateException;
import ru.basnukaev.argumentmap.exception.NodeTranslationNotFoundException;
import ru.basnukaev.argumentmap.exception.TopicAccessDeniedException;
import ru.basnukaev.argumentmap.repository.NodeTranslationRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class NodeTranslationServiceIT {

    @Autowired
    private NodeTranslationService translationService;

    @Autowired
    private NodeService nodeService;

    @Autowired
    private NodeTranslationRepository translationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ownerId;
    private UUID otherUserId;
    private UUID topicId;
    private Node node;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                ownerId, "owner-" + ownerId, ownerId + "@example.com"
        );
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                otherUserId, "other-" + otherUserId, otherUserId + "@example.com"
        );
        topicId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by, visibility) VALUES (?, ?, ?, 'PRIVATE')",
                topicId, "T", ownerId
        );
        node = nodeService.createNode(topicId, NodeType.EVIDENCE, "إنما الأعمال بالنيات", "ar", ownerId);
    }

    @Test
    void addTranslation_persists_andMakesFirstDefault() {
        NodeTranslation t = translationService.addTranslation(
                node.id(), "Кулиев", "ru", "Деяния оцениваются по намерениям",
                false, ownerId, UserRole.USER
        );

        assertThat(t.id()).isNotNull();
        assertThat(t.nodeId()).isEqualTo(node.id());
        assertThat(t.translatorName()).isEqualTo("Кулиев");
        assertThat(t.language()).isEqualTo("ru");
        assertThat(t.body()).isEqualTo("Деяния оцениваются по намерениям");
        // первый перевод узла - всегда default даже если клиент передал false
        assertThat(t.isDefault()).isTrue();
        assertThat(t.createdBy()).isEqualTo(ownerId);
    }

    @Test
    void addTranslation_anonymousAllowed() {
        NodeTranslation t = translationService.addTranslation(
                node.id(), null, "en", "By their intentions", false,
                ownerId, UserRole.USER
        );

        assertThat(t.translatorName()).isNull();
        assertThat(t.isDefault()).isTrue();
    }

    @Test
    void addTranslation_duplicateTranslatorAndLanguage_throws409() {
        translationService.addTranslation(node.id(), "Кулиев", "ru", "Деяния",
                false, ownerId, UserRole.USER);

        assertThatThrownBy(() -> translationService.addTranslation(
                node.id(), "Кулиев", "ru", "Другой текст", false,
                ownerId, UserRole.USER
        )).isInstanceOf(NodeTranslationDuplicateException.class);
    }

    @Test
    void addTranslation_duplicateAnonymousSameLanguage_throws409() {
        translationService.addTranslation(node.id(), null, "ru", "Перевод 1",
                false, ownerId, UserRole.USER);

        assertThatThrownBy(() -> translationService.addTranslation(
                node.id(), null, "ru", "Перевод 2", false,
                ownerId, UserRole.USER
        )).isInstanceOf(NodeTranslationDuplicateException.class);
    }

    @Test
    void addTranslation_differentLanguagesSameTranslator_bothAllowed() {
        translationService.addTranslation(node.id(), "Кулиев", "ru", "Деяния",
                false, ownerId, UserRole.USER);
        translationService.addTranslation(node.id(), "Кулиев", "en", "By intentions",
                false, ownerId, UserRole.USER);

        List<NodeTranslation> list = translationRepository.findByNodeId(node.id());
        assertThat(list).hasSize(2);
    }

    @Test
    void addTranslation_invalidLanguage_throws400() {
        assertThatThrownBy(() -> translationService.addTranslation(
                node.id(), "X", "fr", "body", false, ownerId, UserRole.USER
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addTranslation_blankBody_throws400() {
        assertThatThrownBy(() -> translationService.addTranslation(
                node.id(), "X", "ru", "   ", false, ownerId, UserRole.USER
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addTranslation_nodeMissing_throws404() {
        assertThatThrownBy(() -> translationService.addTranslation(
                UUID.randomUUID(), "X", "ru", "body", false, ownerId, UserRole.USER
        )).isInstanceOf(NodeNotFoundException.class);
    }

    @Test
    void addTranslation_nonOwner_throws403() {
        assertThatThrownBy(() -> translationService.addTranslation(
                node.id(), "X", "ru", "body", false, otherUserId, UserRole.USER
        )).isInstanceOf(TopicAccessDeniedException.class);
    }

    @Test
    void addTranslation_secondDefault_atomicallyDemotesFirst() {
        NodeTranslation first = translationService.addTranslation(
                node.id(), "A", "ru", "первый", false, ownerId, UserRole.USER
        );
        assertThat(first.isDefault()).isTrue();

        NodeTranslation second = translationService.addTranslation(
                node.id(), "B", "ru", "второй", true, ownerId, UserRole.USER
        );
        assertThat(second.isDefault()).isTrue();

        // первый должен потерять флаг default
        NodeTranslation reloadedFirst = translationRepository.findById(first.id()).orElseThrow();
        assertThat(reloadedFirst.isDefault()).isFalse();

        // ровно один default в node_translations для node
        long defaultCount = translationRepository.findByNodeId(node.id()).stream()
                .filter(NodeTranslation::isDefault).count();
        assertThat(defaultCount).isEqualTo(1);
    }

    @Test
    void setDefault_atomicSwap() {
        NodeTranslation first = translationService.addTranslation(
                node.id(), "A", "ru", "первый", true, ownerId, UserRole.USER
        );
        NodeTranslation second = translationService.addTranslation(
                node.id(), "B", "en", "second", false, ownerId, UserRole.USER
        );

        translationService.setDefault(second.id(), ownerId, UserRole.USER);

        NodeTranslation reloadedFirst = translationRepository.findById(first.id()).orElseThrow();
        NodeTranslation reloadedSecond = translationRepository.findById(second.id()).orElseThrow();
        assertThat(reloadedFirst.isDefault()).isFalse();
        assertThat(reloadedSecond.isDefault()).isTrue();
    }

    @Test
    void setDefault_nonOwner_throws403() {
        NodeTranslation t = translationService.addTranslation(
                node.id(), "X", "ru", "body", false, ownerId, UserRole.USER
        );

        assertThatThrownBy(() -> translationService.setDefault(t.id(), otherUserId, UserRole.USER))
                .isInstanceOf(TopicAccessDeniedException.class);
    }

    @Test
    void updateTranslation_persistsBodyAndTranslator() {
        NodeTranslation t = translationService.addTranslation(
                node.id(), "Кулиев", "ru", "первый текст", true,
                ownerId, UserRole.USER
        );

        NodeTranslation updated = translationService.updateTranslation(
                t.id(), "Османов", "обновлённый текст", ownerId, UserRole.USER
        );

        assertThat(updated.translatorName()).isEqualTo("Османов");
        assertThat(updated.body()).isEqualTo("обновлённый текст");
        // isDefault не меняется через PATCH
        assertThat(updated.isDefault()).isTrue();
    }

    @Test
    void updateTranslation_blankBody_keepsExistingBody() {
        NodeTranslation t = translationService.addTranslation(
                node.id(), "X", "ru", "original body", false,
                ownerId, UserRole.USER
        );

        NodeTranslation updated = translationService.updateTranslation(
                t.id(), "Y", "  ", ownerId, UserRole.USER
        );

        // body не очищается blank-строкой - keepingOriginal
        assertThat(updated.body()).isEqualTo("original body");
        assertThat(updated.translatorName()).isEqualTo("Y");
    }

    @Test
    void updateTranslation_missing_throws404() {
        assertThatThrownBy(() -> translationService.updateTranslation(
                UUID.randomUUID(), "X", "body", ownerId, UserRole.USER
        )).isInstanceOf(NodeTranslationNotFoundException.class);
    }

    @Test
    void removeTranslation_existing_deletes() {
        NodeTranslation t = translationService.addTranslation(
                node.id(), "X", "ru", "body", false, ownerId, UserRole.USER
        );

        translationService.removeTranslation(t.id(), ownerId, UserRole.USER);

        assertThat(translationRepository.findById(t.id())).isEmpty();
    }

    @Test
    void removeTranslation_default_promotesOldestRemaining() {
        NodeTranslation defaultT = translationService.addTranslation(
                node.id(), "A", "ru", "первый", false, ownerId, UserRole.USER
        );
        // тут A default (первый - автоматом)
        NodeTranslation second = translationService.addTranslation(
                node.id(), "B", "en", "second", false, ownerId, UserRole.USER
        );
        assertThat(defaultT.isDefault()).isTrue();
        assertThat(second.isDefault()).isFalse();

        translationService.removeTranslation(defaultT.id(), ownerId, UserRole.USER);

        // second должен стать новым default - promoted из oldest оставшийся
        NodeTranslation reloadedSecond = translationRepository.findById(second.id()).orElseThrow();
        assertThat(reloadedSecond.isDefault()).isTrue();
    }

    @Test
    void removeTranslation_nonOwner_throws403() {
        NodeTranslation t = translationService.addTranslation(
                node.id(), "X", "ru", "body", false, ownerId, UserRole.USER
        );

        assertThatThrownBy(() -> translationService.removeTranslation(t.id(), otherUserId, UserRole.USER))
                .isInstanceOf(TopicAccessDeniedException.class);
    }

    @Test
    void getForNode_returnsSortedDefaultFirst() {
        // explicitly first=non-default (но он станет default автоматически)
        translationService.addTranslation(node.id(), "A", "ru", "первый",
                false, ownerId, UserRole.USER);
        translationService.addTranslation(node.id(), "B", "en", "second",
                false, ownerId, UserRole.USER);
        // делаем явный B default
        List<NodeTranslation> all = translationRepository.findByNodeId(node.id());
        UUID bId = all.stream().filter(t -> "B".equals(t.translatorName()))
                .findFirst().orElseThrow().id();
        translationService.setDefault(bId, ownerId, UserRole.USER);

        List<NodeTranslation> sorted = translationService.getForNode(node.id(), ownerId, UserRole.USER);

        assertThat(sorted).hasSize(2);
        assertThat(sorted.get(0).translatorName()).isEqualTo("B");
        assertThat(sorted.get(0).isDefault()).isTrue();
        assertThat(sorted.get(1).translatorName()).isEqualTo("A");
        assertThat(sorted.get(1).isDefault()).isFalse();
    }
}
