package ru.basnukaev.argumentmap.library.imports;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.ai.LlmClient;

/**
 * Извлечение библиографических метаданных арабской книги из сырого
 * описания через LLM (ADR-058). Используется как «умный» fallback/upgrade
 * над regex-парсером описаний (archive.org / shamela) - LLM устойчивее к
 * вариативной разметке и формулировкам.
 *
 * <p>Graceful degradation - сервис никогда не бросает: если LLM disabled
 * или вернул мусор, возвращает {@link Optional#empty()}, и caller
 * откатывается на regex-парсер. Это даёт зависимость от LLM строго
 * опциональной.
 *
 * <p>На момент написания НЕ wired в archive.org pipeline (отдельная
 * фаза). Пока самостоятельный сервис + unit-тесты.
 */
@Service
public class BookMetadataExtractionService {

    private static final Logger log =
            LoggerFactory.getLogger(BookMetadataExtractionService.class);

    /**
     * Системная инструкция - роль и строгое требование чистого JSON.
     * Через {@code LlmClient.complete(system, user)}: для Anthropic это
     * top-level system, для OpenAI - первое system-message.
     */
    private static final String SYSTEM_PROMPT = """
            Ты — извлекатель библиографических метаданных арабских \
            исламских книг. Верни СТРОГО JSON-объект без markdown, без \
            пояснений.""";

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public BookMetadataExtractionService(LlmClient llmClient,
                                         ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Извлечь метаданные из сырого описания книги.
     *
     * <p>rawDescription может содержать HTML (например описание с
     * archive.org / shamela) - его можно передавать как есть: LLM
     * нормально парсит HTML. Для экономии токенов мы снимаем очевидные
     * теги простым regex перед отправкой.
     *
     * @param rawDescription сырое описание (plain text либо HTML)
     * @return {@link ExtractedBookMetadata} либо {@link Optional#empty()}
     *         если LLM disabled / описание пустое / ответ не распарсился
     *         (graceful fallback - метод НЕ бросает)
     */
    public Optional<ExtractedBookMetadata> extract(String rawDescription) {
        if (!llmClient.isEnabled()) {
            log.debug("BookMetadataExtraction пропущен - LlmClient disabled");
            return Optional.empty();
        }
        if (rawDescription == null || rawDescription.isBlank()) {
            return Optional.empty();
        }

        String cleaned = HtmlText.stripTags(rawDescription);
        if (cleaned == null) {
            return Optional.empty();
        }
        String userPrompt = buildUserPrompt(cleaned);

        String rawResponse;
        try {
            rawResponse = llmClient.complete(SYSTEM_PROMPT, userPrompt);
        } catch (RuntimeException e) {
            // LlmApiException / IllegalState - graceful fallback, не бросаем
            log.warn("BookMetadataExtraction: LLM вызов упал: {}", e.getMessage());
            return Optional.empty();
        }

        return parseResponse(rawResponse);
    }

    /**
     * Сформировать user-промпт: описание + явный список полей и форма
     * JSON. Числовые поля просим как числа либо null, авторов - массив
     * строк.
     */
    private String buildUserPrompt(String description) {
        return """
                Извлеки библиографические метаданные из описания арабской книги ниже.

                Верни JSON-объект РОВНО с такими ключами:
                {
                  "titleAr": string|null,        // заголовок книги на арабском
                  "authors": string[],            // авторы (пустой массив если нет)
                  "publisher": string|null,       // издательство (دار النشر)
                  "place": string|null,           // место издания (город)
                  "editionText": string|null,     // текст издания, e.g. "الطبعة الثالثة عشر"
                  "editionNumber": int|null,      // номер издания цифрой (13 из порядкового), иначе null
                  "yearHijri": int|null,          // год по хиджре
                  "yearGregorian": int|null,      // год григорианский
                  "volumes": int|null             // число томов (عدد المجلدات)
                }

                Если поле не определяется из описания — ставь null (для authors — []).
                Никакого текста кроме JSON.

                Описание:
                %s""".formatted(description);
    }

    /**
     * Распарсить ответ LLM лениво: trim, снять ```json fences, распарсить
     * ObjectMapper'ом. Любая ошибка → warn + empty (НЕ бросаем).
     */
    private Optional<ExtractedBookMetadata> parseResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return Optional.empty();
        }
        String cleaned = stripMarkdownFence(rawResponse);
        try {
            JsonNode root = objectMapper.readTree(cleaned);
            if (root == null || !root.isObject()) {
                log.warn("BookMetadataExtraction: ответ не JSON-объект");
                return Optional.empty();
            }
            ExtractedBookMetadata metadata = new ExtractedBookMetadata(
                    text(root, "titleAr"),
                    stringArray(root, "authors"),
                    text(root, "publisher"),
                    text(root, "place"),
                    text(root, "editionText"),
                    integer(root, "editionNumber"),
                    integer(root, "yearHijri"),
                    integer(root, "yearGregorian"),
                    integer(root, "volumes"));
            return Optional.of(metadata);
        } catch (Exception e) {
            log.warn("BookMetadataExtraction: не удалось распарсить ответ LLM: {}",
                    e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * String-поле либо null. Пустую/blank строку нормализуем в null.
     */
    private String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || !node.isValueNode()) {
            return null;
        }
        String value = node.asText();
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * Integer-поле либо null. Принимает число либо числовую строку
     * ("13" → 13); нечисловые → null.
     */
    private Integer integer(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isInt() || node.isLong()) {
            return node.asInt();
        }
        if (node.isTextual()) {
            try {
                return Integer.valueOf(node.asText().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Массив строк. null/не-массив → пустой список. Blank-элементы
     * отбрасываются.
     */
    private List<String> stringArray(JsonNode root, String field) {
        JsonNode node = root.get(field);
        List<String> result = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return result;
        }
        for (JsonNode item : node) {
            if (item != null && item.isValueNode()) {
                String value = item.asText();
                if (value != null && !value.isBlank()) {
                    result.add(value.trim());
                }
            }
        }
        return result;
    }

    /**
     * Снять markdown code fence вокруг JSON если LLM проигнорировал
     * инструкцию «без markdown». Patterns: {@code ```json ... ```},
     * {@code ``` ... ```}.
     */
    private String stripMarkdownFence(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            } else {
                trimmed = trimmed.substring(3);
            }
            int lastFence = trimmed.lastIndexOf("```");
            if (lastFence >= 0) {
                trimmed = trimmed.substring(0, lastFence);
            }
            trimmed = trimmed.trim();
        }
        return trimmed;
    }
}
