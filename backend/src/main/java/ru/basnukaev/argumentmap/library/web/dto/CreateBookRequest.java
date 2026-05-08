package ru.basnukaev.argumentmap.library.web.dto;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.basnukaev.argumentmap.library.domain.BookType;

public record CreateBookRequest(
        @NotNull BookType bookType,
        @NotBlank @Size(max = 500) String title,
        UUID authorityId,
        @NotBlank @Size(max = 32) String language,
        @Size(max = 5000) String description,
        JsonNode metadata
) {
}
