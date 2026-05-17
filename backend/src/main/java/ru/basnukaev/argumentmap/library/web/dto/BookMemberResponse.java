package ru.basnukaev.argumentmap.library.web.dto;

import java.time.Instant;
import java.util.UUID;

public record BookMemberResponse(
        UUID id,
        UUID bookId,
        UUID userId,
        String role,
        Instant addedAt,
        UUID addedBy
) {
}
