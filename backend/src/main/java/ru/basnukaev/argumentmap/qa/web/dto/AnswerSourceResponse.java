package ru.basnukaev.argumentmap.qa.web.dto;

import java.time.Instant;
import java.util.UUID;

import ru.basnukaev.argumentmap.domain.CitationMode;
import ru.basnukaev.argumentmap.web.dto.CitationResponse;

/**
 * Response для answer_sources с structured citation (ADR-028, Этап 19.d).
 * Аналог {@code QuestionSourceResponse} с {@code answerId} вместо
 * {@code questionId}. 3-я итерация параллельной иерархии ADR-033.
 */
public record AnswerSourceResponse(
        UUID id,
        UUID answerId,
        UUID sourceId,
        String quote,
        String context,
        CitationMode mode,
        CitationResponse citation,
        Instant createdAt
) {
}
