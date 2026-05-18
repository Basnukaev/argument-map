package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

/**
 * Повторный перевод от того же переводчика на том же языке для одного узла.
 * Маппится в 409 node-translation-duplicate.
 */
public class NodeTranslationDuplicateException extends RuntimeException {

    private final UUID nodeId;
    private final String translatorName;
    private final String language;

    public NodeTranslationDuplicateException(UUID nodeId, String translatorName, String language) {
        super("Перевод от '%s' на язык '%s' для узла %s уже существует"
                .formatted(translatorName == null ? "анонимного переводчика" : translatorName,
                        language, nodeId));
        this.nodeId = nodeId;
        this.translatorName = translatorName;
        this.language = language;
    }

    public UUID getNodeId() {
        return nodeId;
    }

    public String getTranslatorName() {
        return translatorName;
    }

    public String getLanguage() {
        return language;
    }
}
