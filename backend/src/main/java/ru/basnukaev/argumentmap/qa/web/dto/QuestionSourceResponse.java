package ru.basnukaev.argumentmap.qa.web.dto;

import java.time.Instant;
import java.util.UUID;

import ru.basnukaev.argumentmap.domain.CitationMode;
import ru.basnukaev.argumentmap.web.dto.CitationResponse;

/**
 * Response для question_sources с structured citation (ADR-028).
 * Аналог {@code NodeSourceResponse} с {@code questionId} вместо
 * {@code nodeId}.
 */
public record QuestionSourceResponse(
        UUID id,
        UUID questionId,
        UUID sourceId,
        String quote,
        String context,
        CitationMode mode,
        CitationResponse citation,
        Instant createdAt
) {
}
