package ru.basnukaev.argumentmap.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateNodeRequest(
        @NotBlank @Size(max = 10000) String content
) {
}
