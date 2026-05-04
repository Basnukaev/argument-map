package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.basnukaev.argumentmap.domain.NodeType;

public record CreateNodeRequest(
        @NotNull UUID topicId,
        @NotNull NodeType nodeType,
        @NotBlank @Size(max = 10000) String content
) {
}
