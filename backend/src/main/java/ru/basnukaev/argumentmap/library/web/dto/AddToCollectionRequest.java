package ru.basnukaev.argumentmap.library.web.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * POST /api/v1/library/collections - добавить книгу в коллекцию.
 *
 * @param bookId книга для добавления (обязательное)
 * @param collectionName опциональное имя коллекции, default "Избранное"
 */
public record AddToCollectionRequest(
        @NotNull(message = "Поле bookId обязательно")
        UUID bookId,

        @Size(max = 100, message = "collectionName не должно быть длиннее 100 символов")
        @Schema(description = "Имя коллекции (default \"Избранное\")",
                example = "Избранное")
        String collectionName
) {
}
