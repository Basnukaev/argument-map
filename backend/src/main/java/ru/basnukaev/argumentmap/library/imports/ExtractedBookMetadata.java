package ru.basnukaev.argumentmap.library.imports;

import java.util.List;

/**
 * Структурированные библиографические метаданные арабской книги,
 * извлечённые LLM из сырого описания (ADR-058). Все поля nullable -
 * LLM возвращает null если поле не удалось определить из описания.
 *
 * @param titleAr       заголовок книги на арабском
 * @param authors       список авторов (может быть пустым)
 * @param publisher     издательство (دار النشر)
 * @param place         место издания (город)
 * @param editionText   текст издания как есть, e.g. "الطبعة الثالثة عشر"
 * @param editionNumber номер издания цифрой (13 из "الثالثة عشر"), если
 *                      распознан, иначе null
 * @param yearHijri     год по хиджре
 * @param yearGregorian год по григорианскому календарю
 * @param volumes       число томов (عدد المجلدات)
 */
public record ExtractedBookMetadata(
        String titleAr,
        List<String> authors,
        String publisher,
        String place,
        String editionText,
        Integer editionNumber,
        Integer yearHijri,
        Integer yearGregorian,
        Integer volumes) {
}
