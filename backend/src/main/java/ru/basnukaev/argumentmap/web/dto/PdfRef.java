package ru.basnukaev.argumentmap.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record PdfRef(
        UUID fileId,
        Integer pageNumber,
        JsonNode bbox
) {
}
