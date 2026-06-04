package ru.basnukaev.argumentmap.hadith.web.dto;

import java.util.UUID;

/**
 * Ответ AI-перевода матна (План 7). {@code cached=true} — перевод уже
 * существовал, LLM не вызывался; {@code cached=false} — свежий вызов.
 */
public record MatnTranslationResponse(
        UUID matnId,
        String lang,
        String text,
        boolean cached
) {
}
