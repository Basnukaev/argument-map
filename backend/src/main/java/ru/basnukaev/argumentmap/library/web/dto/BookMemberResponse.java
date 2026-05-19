package ru.basnukaev.argumentmap.library.web.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

public record BookMemberResponse(
        UUID id,
        UUID bookId,
        UUID userId,
        @Schema(allowableValues = {"MEMBER", "EDITOR"})
        String role,
        Instant addedAt,
        UUID addedBy
) {
}
