package ru.basnukaev.argumentmap.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.domain.AuditEntityType;
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.NodeTranslation;
import ru.basnukaev.argumentmap.exception.NodeNotFoundException;
import ru.basnukaev.argumentmap.exception.NodeTranslationDuplicateException;
import ru.basnukaev.argumentmap.exception.NodeTranslationNotFoundException;
import ru.basnukaev.argumentmap.repository.NodeRepository;
import ru.basnukaev.argumentmap.repository.NodeTranslationRepository;

/**
 * Бизнес-логика multi-translation узлов (миграция 45).
 *
 * <p>Контракт permission:
 * <ul>
 *   <li>add / update / setDefault / remove - требуют canWriteTopic
 *       (та же логика что и узел сам). Translation - часть содержания темы</li>
 * </ul>
 *
 * <p>Бизнес-правила:
 * <ul>
 *   <li>{@code language} ∈ {ru, en}. Невалидное → 400 illegal-argument.</li>
 *   <li>{@code body} обязательно non-blank.</li>
 *   <li>Один переводчик - один перевод на узел на язык. Дубликат → 409
 *       node-translation-duplicate. Анонимный переводчик
 *       (translatorName=null) тоже unique per language.</li>
 *   <li>{@code isDefault=true} при add: автоматически снимаем флаг с
 *       остальных переводов узла. Atomic в одной транзакции.</li>
 *   <li>Первый добавляемый перевод узла - всегда default (даже если
 *       клиент передал isDefault=false). Узел не может быть без default'а
 *       пока есть хоть один перевод.</li>
 *   <li>При delete default'а: автоматически promoted'им oldest оставшийся
 *       перевод в новый default. Если переводов больше нет - всё ок.</li>
 * </ul>
 *
 * <p>Audit (ADR-043 Amendment 3): все mutation методы пишут в audit_log
 * через {@link AuditLogService}. parent_entity = NODE (родительский узел).
 */
@Service
public class NodeTranslationService {

    private static final Set<String> ALLOWED_LANGUAGES = Set.of("ru", "en");

    private final NodeTranslationRepository translationRepository;
    private final NodeRepository nodeRepository;
    private final PermissionService permissionService;
    private final AuditLogService auditLogService;

    public NodeTranslationService(NodeTranslationRepository translationRepository,
                                  NodeRepository nodeRepository,
                                  PermissionService permissionService,
                                  AuditLogService auditLogService) {
        this.translationRepository = translationRepository;
        this.nodeRepository = nodeRepository;
        this.permissionService = permissionService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public NodeTranslation addTranslation(UUID nodeId,
                                          String translatorName,
                                          String language,
                                          String body,
                                          boolean isDefault,
                                          UUID userId, String role) {
        Node node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
        permissionService.assertCanWrite(node.topicId(), userId, role);

        validateLanguage(language);
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Поле body обязательно (текст перевода)");
        }
        // нормализуем translator_name: blank → null (анонимный)
        String normalizedTranslator = (translatorName == null || translatorName.isBlank())
                ? null
                : translatorName.trim();

        if (translationRepository.existsForNodeTranslatorLanguage(nodeId, normalizedTranslator, language)) {
            throw new NodeTranslationDuplicateException(nodeId, normalizedTranslator, language);
        }

        // первый перевод узла - всегда default, иначе по флагу клиента
        List<NodeTranslation> existing = translationRepository.findByNodeId(nodeId);
        boolean hasExisting = !existing.isEmpty();
        boolean effectiveDefault = !hasExisting || isDefault;

        NodeTranslation entity = new NodeTranslation(
                UUID.randomUUID(), nodeId, normalizedTranslator, language, body,
                effectiveDefault, Instant.now(), userId
        );
        NodeTranslation saved;
        try {
            saved = translationRepository.save(entity);
        } catch (DuplicateKeyException dup) {
            throw new NodeTranslationDuplicateException(nodeId, normalizedTranslator, language);
        }

        // если новый перевод default - atomic switch через setDefault.
        // Использует тот же helper что и явный setDefault endpoint - один источник
        // истины для default switching logic.
        if (effectiveDefault && hasExisting) {
            translationRepository.setDefault(saved.id(), nodeId);
        }

        // audit CREATE - parent = NODE (родительский узел)
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("translatorName", normalizedTranslator);
        snapshot.put("language", language);
        snapshot.put("isDefault", effectiveDefault);
        auditLogService.logCreate(AuditEntityType.NODE_TRANSLATION, saved.id(),
                AuditEntityType.NODE, nodeId, userId, snapshot);

        return saved;
    }

    @Transactional
    public NodeTranslation updateTranslation(UUID translationId,
                                             String translatorName,
                                             String body,
                                             UUID userId, String role) {
        NodeTranslation existing = translationRepository.findById(translationId)
                .orElseThrow(() -> new NodeTranslationNotFoundException(translationId));
        Node node = nodeRepository.findById(existing.nodeId())
                .orElseThrow(() -> new NodeNotFoundException(existing.nodeId()));
        permissionService.assertCanWrite(node.topicId(), userId, role);

        String resolvedTranslator = (translatorName == null || translatorName.isBlank())
                ? null
                : translatorName.trim();
        String resolvedBody = (body == null || body.isBlank()) ? existing.body() : body;

        boolean updated = translationRepository.update(translationId, resolvedTranslator, resolvedBody);
        if (!updated) {
            throw new NodeTranslationNotFoundException(translationId);
        }

        // audit UPDATE - per-field diff для translator_name и body
        Map<String, AuditLogService.FieldDiff> diff = new LinkedHashMap<>();
        if (!java.util.Objects.equals(existing.translatorName(), resolvedTranslator)) {
            diff.put("translatorName", new AuditLogService.FieldDiff(
                    existing.translatorName(), resolvedTranslator));
        }
        if (!java.util.Objects.equals(existing.body(), resolvedBody)) {
            diff.put("body", new AuditLogService.FieldDiff(
                    existing.body(), resolvedBody));
        }
        if (!diff.isEmpty()) {
            auditLogService.logUpdate(AuditEntityType.NODE_TRANSLATION, translationId,
                    AuditEntityType.NODE, existing.nodeId(), userId, diff);
        }

        return new NodeTranslation(
                existing.id(), existing.nodeId(), resolvedTranslator,
                existing.language(), resolvedBody, existing.isDefault(),
                existing.createdAt(), existing.createdBy()
        );
    }

    @Transactional
    public NodeTranslation setDefault(UUID translationId, UUID userId, String role) {
        NodeTranslation existing = translationRepository.findById(translationId)
                .orElseThrow(() -> new NodeTranslationNotFoundException(translationId));
        Node node = nodeRepository.findById(existing.nodeId())
                .orElseThrow(() -> new NodeNotFoundException(existing.nodeId()));
        permissionService.assertCanWrite(node.topicId(), userId, role);

        translationRepository.setDefault(translationId, existing.nodeId());

        // audit UPDATE с change=isDefault: false → true (логически - смена
        // флага default для этого перевода). Old=false т.к. setDefault имеет
        // смысл только если он не был default'ом до того
        if (!existing.isDefault()) {
            Map<String, AuditLogService.FieldDiff> diff = Map.of(
                    "isDefault", new AuditLogService.FieldDiff(false, true)
            );
            auditLogService.logUpdate(AuditEntityType.NODE_TRANSLATION, translationId,
                    AuditEntityType.NODE, existing.nodeId(), userId, diff);
        }

        return new NodeTranslation(
                existing.id(), existing.nodeId(), existing.translatorName(),
                existing.language(), existing.body(), true,
                existing.createdAt(), existing.createdBy()
        );
    }

    @Transactional
    public void removeTranslation(UUID translationId, UUID userId, String role) {
        NodeTranslation existing = translationRepository.findById(translationId)
                .orElseThrow(() -> new NodeTranslationNotFoundException(translationId));
        Node node = nodeRepository.findById(existing.nodeId())
                .orElseThrow(() -> new NodeNotFoundException(existing.nodeId()));
        permissionService.assertCanWrite(node.topicId(), userId, role);

        // audit DELETE - до самого delete (после existing уже не достать)
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("translatorName", existing.translatorName());
        snapshot.put("language", existing.language());
        snapshot.put("isDefault", existing.isDefault());
        auditLogService.logDelete(AuditEntityType.NODE_TRANSLATION, translationId,
                AuditEntityType.NODE, existing.nodeId(), userId, snapshot);

        translationRepository.deleteById(translationId);

        // promote oldest оставшийся перевод в default если удалили default
        if (existing.isDefault()) {
            Optional<NodeTranslation> oldest = translationRepository.findOldestByNodeId(existing.nodeId());
            oldest.ifPresent(t -> translationRepository.setDefault(t.id(), existing.nodeId()));
        }
    }

    @Transactional(readOnly = true)
    public List<NodeTranslation> getForNode(UUID nodeId, UUID userId, String role) {
        Node node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
        permissionService.assertCanRead(node.topicId(), userId, role);
        return translationRepository.findByNodeId(nodeId);
    }

    private void validateLanguage(String language) {
        if (language == null || !ALLOWED_LANGUAGES.contains(language)) {
            throw new IllegalArgumentException(
                    "Недопустимое значение language: '" + language
                            + "'. Допустимые: " + ALLOWED_LANGUAGES);
        }
    }
}
