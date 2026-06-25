package ru.basnukaev.argumentmap.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/**
 * PDF-локация citation. {@code fileId} заполнен для FK-режима PDF
 * (user-upload книги через library_files); {@code fileIndex} - для
 * режима PDF_LINK (FILE_ONLY archive.org-сканы, 0-based ordinal в
 * pdf_links.files[], ADR-067). Ровно одно из двух не-null. Display-путь
 * адресует PDF по fileIndex+page+bbox, так что наличие любого из двух
 * полей достаточно для рендера выделения.
 */
public record PdfRef(
        UUID fileId,
        Integer fileIndex,
        Integer pageNumber,
        JsonNode bbox
) {
}
