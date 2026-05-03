package ru.basnukaev.argumentmap.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.basnukaev.argumentmap.domain.Reliability;
import ru.basnukaev.argumentmap.domain.SourceType;

public record CreateSourceRequest(
        @NotNull SourceType sourceType,
        @NotBlank @Size(max = 500) String title,
        @Size(max = 2000) String citation,
        Reliability reliability,
        JsonNode metadata
) {
}
