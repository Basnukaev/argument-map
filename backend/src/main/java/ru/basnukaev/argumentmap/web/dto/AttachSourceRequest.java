package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AttachSourceRequest(
        @NotNull UUID sourceId,
        @Size(max = 10000) String quote,
        @Size(max = 2000) String context
) {
}
