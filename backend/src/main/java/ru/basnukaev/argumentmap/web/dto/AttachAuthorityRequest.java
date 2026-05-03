package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import ru.basnukaev.argumentmap.domain.Stance;

public record AttachAuthorityRequest(
        @NotNull UUID authorityId,
        @NotNull Stance stance
) {
}
