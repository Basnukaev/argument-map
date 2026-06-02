package ru.basnukaev.argumentmap.library.domain;

/**
 * State machine AI editing pass (ADR-042, Этап 17.e). 4-state machine
 * PENDING/PROCESSING/DONE/FAILED. Хранится в БД как VARCHAR (CHECK
 * constraint миграции 35).
 *
 * <ul>
 *   <li>{@code PENDING} - AI edit запрошен, ожидает worker thread.
 *       Кратковременное состояние - сразу переходит в PROCESSING
 *       при подхвате задачи из {@code aiEditTaskExecutor} queue</li>
 *   <li>{@code PROCESSING} - запрос к Anthropic API в работе.
 *       Длительность типично 5-15 секунд per page. Зависшие
 *       PROCESSING (>5 минут) - сигнал что worker thread умер,
 *       форсированный re-trigger допустим</li>
 *   <li>{@code DONE} - {@code formatted_content} заполнен валидным
 *       ProseMirror JSON. Можно перезапустить (overwrite предыдущего
 *       output) если качество не устраивает</li>
 *   <li>{@code FAILED} - exception в pipeline (Anthropic API down,
 *       invalid JSON response, rate limit). {@code formatted_content}
 *       остаётся как был (null либо предыдущий successful run). См.
 *       backend log для root cause</li>
 * </ul>
 *
 * <p>В БД хранится строковое имя (VARCHAR(20)) - не {@code @Enumerated}.
 * Конвертация через прямые string compares в сервисном слое.
 */
public final class AiEditStatus {

    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";

    private AiEditStatus() {
    }
}
