package ru.basnukaev.argumentmap.library.shamela.service.mapper;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaBookRow;
import ru.basnukaev.argumentmap.library.shamela.service.ShamelaImportException;

/**
 * Сборка {@code metadata} jsonb для {@code lib_books} из shamela-staging.
 * Содержит только shamela-специфичные поля - универсальные (title, language)
 * хранятся в обычных колонках {@code lib_books}.
 *
 * <p>Поля:
 * <ul>
 *   <li>{@code shamela_book_id} - для re-import detection через GIN-индекс</li>
 *   <li>{@code shamela_major_release} - для PDF endpoint URL-конструирования</li>
 *   <li>{@code pdf_links} - raw shamela JSON для будущих PDF-нужд</li>
 * </ul>
 */
@Component
public class ShamelaBookMetadataBuilder {

    private final ObjectMapper objectMapper;

    public ShamelaBookMetadataBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String build(ShamelaBookRow shamelaBook) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("shamela_book_id", shamelaBook.id());
            root.put("shamela_major_release", shamelaBook.majorRelease());
            if (shamelaBook.pdfLinksJson() != null && !shamelaBook.pdfLinksJson().isBlank()) {
                JsonNode pdfLinks = objectMapper.readTree(shamelaBook.pdfLinksJson());
                root.set("pdf_links", pdfLinks);
            }
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new ShamelaImportException(
                    "ошибка построения metadata jsonb для shamela book id=" + shamelaBook.id(), e);
        }
    }
}
