package ru.basnukaev.argumentmap.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Перевод текста узла с attribution переводчика. Один узел может иметь
 * несколько переводов от разных переводчиков (Кулиев, Sahih International,
 * Османов и т.д.) на разных языках (миграция 45).
 *
 * <p>{@code translatorName} - nullable: анонимный перевод (без указания
 * переводчика) допустим. UNIQUE constraint покрывает оба случая через
 * partial indexes: (node_id, translator_name, language) когда
 * translator_name NOT NULL, и (node_id, language) когда NULL.
 *
 * <p>{@code isDefault} - какой перевод показывать по умолчанию в UI.
 * Внутри одного узла одновременно только один default-перевод (логика
 * atomic switch в {@code NodeTranslationService.setDefault}).
 *
 * <p>{@code language} ∈ {ru, en}. Whitelist в БД через CHECK.
 *
 * <p>{@code originalLang} остаётся на {@code Node} - это свойство
 * оригинала, не перевода.
 */
public record NodeTranslation(
        UUID id,
        UUID nodeId,
        String translatorName,
        String language,
        String body,
        boolean isDefault,
        Instant createdAt,
        UUID createdBy
) {
}
