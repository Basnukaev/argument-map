package ru.basnukaev.argumentmap.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAuthorityRequest(
        @NotBlank @Size(max = 500) String name,
        @Size(max = 10000) String bio,
        @Size(max = 100) String era,
        @Size(max = 100) String madhab,
        JsonNode metadata
) {
}
