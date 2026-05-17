package ru.basnukaev.argumentmap.web.dto;

import java.time.Instant;
import java.util.UUID;

public record TopicMemberResponse(
        UUID id,
        UUID topicId,
        UUID userId,
        String role,
        Instant addedAt,
        UUID addedBy
) {
}
