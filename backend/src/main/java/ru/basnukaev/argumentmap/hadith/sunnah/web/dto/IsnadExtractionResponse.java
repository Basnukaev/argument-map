package ru.basnukaev.argumentmap.hadith.sunnah.web.dto;

import ru.basnukaev.argumentmap.hadith.web.dto.SanadGraphResponse;

/**
 * Ответ admin-эндпоинта извлечения иснада из матна (ADR-059,
 * POST /api/v1/admin/sunnah/extract-isnad). Превью — граф эфемерный,
 * в БД ничего не пишется.
 *
 * <p>Три исхода:
 * <ul>
 *   <li>LLM не настроен → {@code {llmEnabled:false, isnadFound:false,
 *       graph:null, cleanedMatn:null}} — фронт показывает «AI не
 *       настроен».</li>
 *   <li>LLM настроен, иснад извлечён → {@code {llmEnabled:true,
 *       isnadFound:true, graph:<built>, cleanedMatn:...}}.</li>
 *   <li>LLM настроен, иснада нет / parse fail → {@code {llmEnabled:true,
 *       isnadFound:false, graph:null}}.</li>
 * </ul>
 *
 * @param llmEnabled  сконфигурирован ли LLM (ai.provider/ключ)
 * @param isnadFound  удалось ли извлечь цепочку
 * @param graph       граф под React Flow ({@link SanadGraphResponse}) или
 *                    null
 * @param cleanedMatn текст хадиса без иснад-префикса или null
 */
public record IsnadExtractionResponse(
        boolean llmEnabled,
        boolean isnadFound,
        SanadGraphResponse graph,
        String cleanedMatn) {

    /** LLM недоступен — graceful, фронт покажет подсказку про настройку. */
    public static IsnadExtractionResponse llmDisabled() {
        return new IsnadExtractionResponse(false, false, null, null);
    }

    /** LLM сработал, но иснад выделить не удалось (или parse fail). */
    public static IsnadExtractionResponse notFound() {
        return new IsnadExtractionResponse(true, false, null, null);
    }

    /** Иснад извлечён и граф построен. */
    public static IsnadExtractionResponse found(SanadGraphResponse graph, String cleanedMatn) {
        return new IsnadExtractionResponse(true, true, graph, cleanedMatn);
    }
}
