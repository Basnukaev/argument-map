package ru.basnukaev.argumentmap.library.archiveorg;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Сырой ответ {@code GET https://archive.org/metadata/{identifier}}
 * (ADR-056). Один публичный вызов без авторизации возвращает
 * {@code { metadata: {...}, files: [...] }}.
 *
 * <p>{@code metadata} - словарь произвольных строковых полей издания
 * (title, creator, language, description, identifier, ...). Значения
 * могут быть строкой ИЛИ массивом строк (например {@code collection},
 * {@code subject}) - поэтому {@code Object}. Извлечение чистых
 * скалярных полей - в {@link ArchiveOrgMetadataMapper}.
 *
 * <p>{@code files} - список всех файлов item'а: PDF (оригинал-скан +
 * OCR-слой), thumbnails, OCR-артефакты, метаданные. Группировка
 * PDF в тома - в маппере.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArchiveOrgMetadata(
        Map<String, Object> metadata,
        List<FileEntry> files
) {

    /**
     * Один файл из {@code files[]}. {@code source} = {@code original}
     * (загруженный скан) / {@code derivative} (производный, в т.ч.
     * OCR-слой {@code *_text.pdf}). {@code format} - человекочитаемый
     * тип ({@code Image Container PDF}, {@code Additional Text PDF},
     * {@code Text PDF}, ...). {@code size} - строка байт (archive.org
     * отдаёт числа строками).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FileEntry(
            String name,
            String format,
            String source,
            String size
    ) {
        /** Размер в байтах либо null если archive.org не указал/невалидно. */
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public Long sizeBytes() {
            if (size == null || size.isBlank()) {
                return null;
            }
            try {
                return Long.parseLong(size.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
