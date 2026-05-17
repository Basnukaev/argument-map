package ru.basnukaev.argumentmap.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTopicRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 10000) String rootQuestion,
        // ADR-043: visibility опциональный, default PRIVATE на бэке
        @Pattern(regexp = "PRIVATE|SHARED|PUBLIC",
                message = "visibility должен быть PRIVATE, SHARED или PUBLIC")
        String visibility
) {
}
