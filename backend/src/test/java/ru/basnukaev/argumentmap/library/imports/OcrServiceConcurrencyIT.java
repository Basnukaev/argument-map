package ru.basnukaev.argumentmap.library.imports;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.BookVisibility;
import ru.basnukaev.argumentmap.library.domain.OcrStatus;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.PageRepository;

/**
 * IT для OCR atomic-claim защиты от concurrent re-trigger (Bug-hunt
 * Tier-3 #1). НЕ требует Tesseract (в отличие от {@link OcrServiceIT}) -
 * проверяет именно check-then-act guard: если страница уже PROCESSING
 * (другой вызов в полёте), {@link OcrService#recognize} выходит до
 * запуска тяжёлого Tesseract recognize, не перезаписывая статус.
 *
 * <p>Тот же подход что и {@code AiEditServiceIT.enhance_alreadyProcessing}:
 * эмулируем concurrent-winner предустановкой PROCESSING, затем второй
 * вызов должен спокойно выйти (claim вернул false). Если бы guard
 * отсутствовал, второй вызов пошёл бы качать image из MinIO и упал бы /
 * запустил дубль OCR.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OcrServiceConcurrencyIT {

    @Autowired
    private OcrService service;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Book book;

    @BeforeEach
    void setUp() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "user-" + userId, userId + "@example.com");
        Instant now = Instant.now();
        book = bookRepository.save(new Book(
                UUID.randomUUID(), BookType.MANUSCRIPT, "OCR Concurrency Book",
                null, "ar", null, null, userId, now, now,
                null, null, null, null, null, null,
                BookVisibility.PUBLIC));
    }

    @Test
    void recognize_alreadyProcessing_bailsWithoutOverwritingStatus() {
        // Страница с image pointer (precondition пройдёт) но уже PROCESSING -
        // эмулируем concurrent-winner который застолбил claim первым.
        Instant now = Instant.now();
        Page page = pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 1,
                null, null, null,
                "", null, null,
                "page-images", book.id() + "/page-1.png", now,
                OcrStatus.PROCESSING, now, null,
                null, null, null,
                now, now
        ));

        // claim вернёт false (статус уже PROCESSING) → recognize выходит
        // не трогая MinIO/Tesseract. Без guard'а пошёл бы download и FAILED.
        service.recognize(page.id());

        Page after = pageRepository.findById(page.id()).orElseThrow();
        // статус остался PROCESSING (loser не перезаписал в FAILED/DONE)
        assertThat(after.ocrStatus()).isEqualTo(OcrStatus.PROCESSING);
        assertThat(after.ocrCompletedAt()).isNull();
    }

    @Test
    void recognize_noImagePointer_marksFailedBeforeClaim() {
        // sanity: precondition (нет скана) отрабатывает до claim - страница
        // без image pointer не претендует на PROCESSING, сразу FAILED.
        Instant now = Instant.now();
        Page page = pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 2,
                null, null, null,
                "text-only", null, null,
                null, null, null,
                OcrStatus.PENDING, null, null,
                null, null, null,
                now, now
        ));

        service.recognize(page.id());

        Page after = pageRepository.findById(page.id()).orElseThrow();
        assertThat(after.ocrStatus()).isEqualTo(OcrStatus.FAILED);
        assertThat(after.textContent()).isEqualTo("text-only");
    }
}
