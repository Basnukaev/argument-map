package ru.basnukaev.argumentmap.library.imports;

import java.util.UUID;

/**
 * Опциональные поля метаданных при загрузке PDF/EPUB пользователем через
 * {@code POST /api/v1/library/imports/file} (Этап 16.b). Все поля nullable -
 * если не заданы, {@link FileImportService} либо подставляет дефолт
 * (например {@code language="ar"}), либо пытается извлечь значение из
 * самого PDF (например {@code title} через {@code PDDocumentInformation}).
 *
 * @param title заголовок книги, override автоматически извлечённого
 *              из PDF metadata. Если оба null - используется
 *              {@code filename} без расширения
 * @param authorityId UUID существующего {@code Authority} - связывает
 *                    книгу с автором. Валидируется в
 *                    {@link ru.basnukaev.argumentmap.library.service.BookService}
 * @param language ISO 639-1 (например {@code "ar"} или {@code "ru"}).
 *                 Default - {@code "ar"} (платформа в первую очередь
 *                 арабоязычная)
 * @param description свободный текст, заметки о книге
 */
public record ImportMetadata(
        String title,
        UUID authorityId,
        String language,
        String description
) {
    public static ImportMetadata empty() {
        return new ImportMetadata(null, null, null, null);
    }
}
