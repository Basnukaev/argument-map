package ru.basnukaev.argumentmap.hadith.isnad;

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
 * Извлечение иснада (цепочки передатчиков) из матна арабского хадиса
 * через swappable LLM (ADR-058, ADR-059).
 *
 * <p>sunnah.com отдаёт matn+иснад единым блобом — структурного иснада
 * нет. Этот сервис просит LLM выделить упорядоченную цепочку
 * передатчиков и очищенный матн. Результат эфемерный (превью): в БД не
 * пишется, граф строится in-memory (см. {@code SanadGraphService}).
 *
 * <p>Graceful degradation — сервис никогда не бросает: LLM disabled,
 * пустой матн, мусор в ответе или upstream-ошибка → {@link
 * Optional#empty()}, и endpoint отдаёт {@code llmEnabled:false} /
 * {@code isnadFound:false}. Зависимость от LLM строго опциональна.
 */
@Service
public class IsnadExtractionService {

    private static final Logger log = LoggerFactory.getLogger(IsnadExtractionService.class);

    /** Системная инструкция — роль и строгое требование чистого JSON. */
    private static final String SYSTEM_PROMPT = """
            Ты — извлекатель иснада (цепочки передатчиков) из арабского \
            хадиса. Верни СТРОГО JSON без markdown, без пояснений.""";

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public IsnadExtractionService(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Сконфигурирован ли LLM. Caller (endpoint) проверяет ДО загрузки
     * матна, чтобы отдать {@code llmEnabled:false} синхронно, не трогая
     * источник sunnah-дампа (иначе 503 при disabled LLM).
     */
    public boolean isLlmEnabled() {
        return llmClient.isEnabled();
    }

    /**
     * Извлечь иснад из арабского матна.
     *
     * @param matnArabic арабский текст хадиса с иснад-префиксом
     * @return {@link ExtractedIsnad} либо {@link Optional#empty()} если
     *         LLM disabled / матн пуст / ответ не распарсился (graceful
     *         fallback — метод НЕ бросает). При {@code isnadFound=false}
     *         внутри distinguishes «LLM сработал, но иснада нет» от
     *         «LLM недоступен» (последнее → empty Optional).
     */
    public Optional<ExtractedIsnad> extract(String matnArabic) {
        if (!llmClient.isEnabled()) {
            log.debug("IsnadExtraction пропущен — LlmClient disabled");
            return Optional.empty();
        }
        if (matnArabic == null || matnArabic.isBlank()) {
            return Optional.empty();
        }

        String userPrompt = buildUserPrompt(matnArabic);

        String rawResponse;
        try {
            rawResponse = llmClient.complete(SYSTEM_PROMPT, userPrompt);
        } catch (RuntimeException e) {
            // LlmApiException / IllegalState — graceful fallback, не бросаем
            log.warn("IsnadExtraction: LLM вызов упал: {}", e.getMessage());
            return Optional.empty();
        }

        return parseResponse(rawResponse);
    }

    /**
     * User-промпт: инструкция о порядке передатчиков (top→companion),
     * семантике transmission и форме JSON + сам матн.
     */
    private String buildUserPrompt(String matn) {
        return """
                Выдели иснад (цепочку передатчиков) из арабского хадиса ниже.

                Верни JSON РОВНО такой формы:
                {
                  "isnadFound": true,
                  "narrators": [
                    {"name": "...", "transmission": "..."}
                  ],
                  "cleanedMatn": "..."
                }

                Правила:
                - narrators упорядочены СВЕРХУ ВНИЗ по матну: narrators[0] —
                  прямой источник составителя (первое имя в тексте иснада),
                  narrators[последний] — сподвижник (сахаби), который слышал
                  Пророка ﷺ.
                - "name" — имя передатчика на арабском как стоит в матне.
                - "transmission" — формула, которой ЭТОТ передатчик получил
                  хадис от СЛЕДУЮЩЕГО передатчика в сторону Пророка
                  (حدثنا / أخبرنا / سمعت / عن / أنّ / عن النبي …).
                - "cleanedMatn" — текст хадиса БЕЗ иснад-префикса (только
                  содержание матна).
                - Если иснад выделить нельзя — верни {"isnadFound": false,
                  "narrators": [], "cleanedMatn": null}.
                - Никакого текста кроме JSON.

                Хадис:
                %s""".formatted(matn);
    }

    /**
     * Распарсить ответ LLM лениво: trim, снять ```json fences, распарсить
     * ObjectMapper'ом. Любая ошибка → warn + empty (НЕ бросаем).
     */
    private Optional<ExtractedIsnad> parseResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return Optional.empty();
        }
        String cleaned = stripMarkdownFence(rawResponse);
        try {
            JsonNode root = objectMapper.readTree(cleaned);
            if (root == null || !root.isObject()) {
                log.warn("IsnadExtraction: ответ не JSON-объект");
                return Optional.empty();
            }
            boolean isnadFound = root.path("isnadFound").asBoolean(false);
            List<ExtractedNarrator> narrators = parseNarrators(root.get("narrators"));
            String cleanedMatn = text(root, "cleanedMatn");
            // LLM-ответ без передатчиков трактуем как «не найдено», даже
            // если он выставил isnadFound=true — пустая цепь бесполезна.
            boolean found = isnadFound && !narrators.isEmpty();
            return Optional.of(new ExtractedIsnad(found, narrators, cleanedMatn));
        } catch (Exception e) {
            log.warn("IsnadExtraction: не удалось распарсить ответ LLM: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private List<ExtractedNarrator> parseNarrators(JsonNode node) {
        List<ExtractedNarrator> result = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return result;
        }
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String name = text(item, "name");
            String transmission = text(item, "transmission");
            // Без имени передатчик бесполезен — пропускаем.
            if (name != null) {
                result.add(new ExtractedNarrator(name, transmission));
            }
        }
        return result;
    }

    /** String-поле либо null. Пустую/blank строку нормализуем в null. */
    private String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || !node.isValueNode()) {
            return null;
        }
        String value = node.asText();
        return (value == null || value.isBlank()) ? null : value.trim();
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
