package ru.basnukaev.argumentmap.library.shamela.service.mapper;

import static ru.basnukaev.argumentmap.library.shamela.service.mapper.ShamelaMapperUtils.blankToNull;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.repository.PageRepository;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaPageRow;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaPageDao;

/**
 * Маппинг {@code lib_shamela_page} → {@code lib_pages}. Поля:
 * <ul>
 *   <li>{@code page_number} = {@code shamela_page.id} (1-based monotonic,
 *       internal counter) - URL-state и navigation order в reader</li>
 *   <li>{@code printed_page} = маркер реального издания
 *       (source-first, ADR-021)</li>
 *   <li>{@code part} = том/juz' для multi-volume (nullable)</li>
 *   <li>{@code pdf_page_number} = NULL до подключения PDF</li>
 *   <li>{@code chapter_id} = NULL на MVP</li>
 * </ul>
 *
 * <p>Пустые страницы пропускаются - {@code lib_pages_content_present}
 * CHECK требует наличия text_content или image_url.
 *
 * <p>Защита от дублей: composite PK {@code (book_id, id)} в staging
 * гарантирует уникальность, но проверка на уровне маппера дешёвая и
 * предохраняет от UNIQUE-violation на {@code (book_id, page_number)}.
 */
@Service
public class ShamelaPageMapper {

    private final ShamelaPageDao shamelaPageDao;
    private final PageRepository pageRepository;

    public ShamelaPageMapper(ShamelaPageDao shamelaPageDao,
                             PageRepository pageRepository) {
        this.shamelaPageDao = shamelaPageDao;
        this.pageRepository = pageRepository;
    }

    /**
     * @return сколько page-записей создано
     */
    public int mapPages(UUID bookUuid, long shamelaBookId, Instant now) {
        List<ShamelaPageRow> pages = shamelaPageDao.findAllByBookId(shamelaBookId);
        if (pages.isEmpty()) {
            return 0;
        }
        Set<Integer> seenPageNumbers = new HashSet<>();
        int created = 0;
        for (ShamelaPageRow p : pages) {
            if (p.content() == null || p.content().isBlank()) {
                continue;
            }
            if (!seenPageNumbers.add(p.id())) {
                continue;
            }
            UUID pageUuid = UUID.randomUUID();
            String cleanedContent = ShamelaTextCleaner.clean(p.content());
            // После cleanup может оказаться что страница содержит только
            // CJK noise + whitespace - пропускаем чтобы не нарушить
            // lib_pages_content_present CHECK constraint
            if (cleanedContent == null || cleanedContent.isBlank()) {
                continue;
            }
            Page page = new Page(
                    pageUuid,
                    bookUuid,
                    null,
                    p.id(),
                    blankToNull(p.printedPage()),
                    blankToNull(p.part()),
                    null,
                    cleanedContent,
                    null,
                    now,
                    now
            );
            pageRepository.save(page);
            created++;
        }
        return created;
    }
}
