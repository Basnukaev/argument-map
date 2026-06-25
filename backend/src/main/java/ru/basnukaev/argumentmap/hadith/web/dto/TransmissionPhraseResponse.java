package ru.basnukaev.argumentmap.hadith.web.dto;

import java.util.UUID;

/**
 * Ответ правки формулы передачи звена иснада (курация Фаза 5.b). Возвращает
 * сохранённое EFFECTIVE-значение под стабильным ключом
 * {@code (hadithId, position)} — фронт рефетчит граф/detail для полной
 * пересборки, этот ответ подтверждает запись.
 */
public record TransmissionPhraseResponse(
        UUID hadithId,
        int position,
        String phrase
) {
}
