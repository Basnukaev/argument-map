package ru.basnukaev.argumentmap.qa.web.dto;

import java.time.Instant;
import java.util.UUID;

import ru.basnukaev.argumentmap.qa.domain.QuestionStatus;

public record QuestionResponse(
        UUID id,
        String title,
        String body,
        QuestionStatus status,
        UUID askedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
