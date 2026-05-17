package ru.basnukaev.argumentmap.library.domain;

/**
 * State machine OCR pipeline (ADR-041). Хранится в БД как VARCHAR (см.
 * CHECK constraint миграции 34) - не TypeScript enum, чтобы можно было
 * безопасно добавлять новые состояния в будущем без code-generation
 * round-trip между бэкендом и фронтом.
 *
 * <ul>
 *   <li>{@code PENDING} - страница загружена, OCR ещё не запускался.
 *       Может быть форсированно перезапущен через re-OCR endpoint</li>
 *   <li>{@code PROCESSING} - OCR в работе. Длительные зависшие
 *       PROCESSING (>10 минут) - сигнал краша async-таска, retry через
 *       re-OCR (17.d)</li>
 *   <li>{@code DONE} - {@code text_content} заполнен Tesseract output.
 *       Можно перезапустить OCR (overwrite) если квалитет недостаточный</li>
 *   <li>{@code FAILED} - exception в OCR pipeline. {@code text_content}
 *       остаётся как был. См. backend log для root cause. Re-OCR может
 *       помочь если проблема была transient (например native binding
 *       race на старте)</li>
 * </ul>
 *
 * <p>В БД хранится строковое имя (VARCHAR(20)) - не {@code @Enumerated}
 * JPA-style. Конвертация через прямые string compares в сервисном слое.
 */
public final class OcrStatus {

    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";

    private OcrStatus() {
    }
}
