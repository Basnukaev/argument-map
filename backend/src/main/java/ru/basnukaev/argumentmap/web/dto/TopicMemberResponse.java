package ru.basnukaev.argumentmap.web.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

public record TopicMemberResponse(
        UUID id,
        UUID topicId,
        UUID userId,
        @Schema(allowableValues = {"MEMBER", "EDITOR"})
        String role,
        Instant addedAt,
        UUID addedBy
) {
}
