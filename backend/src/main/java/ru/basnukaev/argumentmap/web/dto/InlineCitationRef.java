package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

import ru.basnukaev.argumentmap.domain.Reliability;
import ru.basnukaev.argumentmap.domain.SourceType;

/**
 * Лёгкая ссылка на цитату для inline-маркеров [N] в тексте узла.
 *
 * <p>Заполняется bulk-load в {@code NodeResponse.inlineCitations} - frontend
 * парсит `[1]`, `[2]` и находит ref по {@link #ordinal} (1-based). Подход A
 * (implicit ordinal) - порядок совпадает с {@code ORDER BY ns.created_at ASC}
 * в {@link ru.basnukaev.argumentmap.repository.NodeSourceRepository}.
 *
 * <p>Поля плоские (не nested CitationResponse) чтобы popover отрисовать без
 * лишних null-проверок: title - всегда что-то отдаём (fallback на source.title
 * → book.title → "—"), quote/citation/reliability nullable
 *
 * @param ordinal 1-based порядковый номер источника в списке node_sources
 * @param nodeSourceId id записи node_sources (для deep-link и detach)
 * @param sourceId id записи sources (через node_sources.source_id)
 * @param sourceType QURAN/HADITH/BOOK/ARTICLE/URL - используется для иконки/badge
 * @param title заголовок для header popover'а
 * @param citation короткая академическая строка ссылки (если есть)
 * @param quote сама цитата из источника (если есть)
 * @param reliability SAHIH/HASAN/DAIF - только для HADITH
 */
public record InlineCitationRef(
        int ordinal,
        UUID nodeSourceId,
        UUID sourceId,
        SourceType sourceType,
        String title,
        String citation,
        String quote,
        Reliability reliability
) {
}
