package ru.basnukaev.argumentmap.library.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.library.domain.Page;

@Repository
public class PageRepository {

    private static final String COLUMNS =
            "id, book_id, chapter_id, page_number, printed_page, part, pdf_page_number, "
            + "text_content, image_url, formatted_content, "
            + "image_bucket, image_storage_key, image_uploaded_at, "
            + "ocr_status, ocr_started_at, ocr_completed_at, "
            + "ai_edit_status, ai_edit_started_at, ai_edit_completed_at, "
            + "created_at, updated_at";

    private static final RowMapper<Page> ROW_MAPPER = (rs, rn) -> {
        int pdfPage = rs.getInt("pdf_page_number");
        Integer pdfPageOrNull = rs.wasNull() ? null : pdfPage;
        return new Page(
                rs.getObject("id", UUID.class),
                rs.getObject("book_id", UUID.class),
                rs.getObject("chapter_id", UUID.class),
                rs.getInt("page_number"),
                rs.getString("printed_page"),
                rs.getString("part"),
                pdfPageOrNull,
                rs.getString("text_content"),
                rs.getString("image_url"),
                rs.getString("formatted_content"),
                rs.getString("image_bucket"),
                rs.getString("image_storage_key"),
                instant(rs, "image_uploaded_at"),
                rs.getString("ocr_status"),
                instant(rs, "ocr_started_at"),
                instant(rs, "ocr_completed_at"),
                rs.getString("ai_edit_status"),
                instant(rs, "ai_edit_started_at"),
                instant(rs, "ai_edit_completed_at"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public PageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Page save(Page page) {
        // formatted_content идёт через ::jsonb cast - JdbcTemplate
        // передаёт String, Postgres парсит. Это проще чем
        // PGobject + Types.OTHER и единообразно с тем как мы
        // храним другие jsonb колонки (metadata в lib_books)
        jdbcTemplate.update(
                "INSERT INTO lib_pages (" + COLUMNS + ") "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, "
                        + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                page.id(),
                page.bookId(),
                page.chapterId(),
                page.pageNumber(),
                page.printedPage(),
                page.part(),
                page.pdfPageNumber(),
                page.textContent(),
                page.imageUrl(),
                page.formattedContent(),
                page.imageBucket(),
                page.imageStorageKey(),
                odt(page.imageUploadedAt()),
                page.ocrStatus(),
                odt(page.ocrStartedAt()),
                odt(page.ocrCompletedAt()),
                page.aiEditStatus(),
                odt(page.aiEditStartedAt()),
                odt(page.aiEditCompletedAt()),
                odt(page.createdAt()),
                odt(page.updatedAt())
        );
        return page;
    }

    public Optional<Page> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_pages WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    public Optional<Page> findByBookAndPageNumber(UUID bookId, int pageNumber) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_pages "
                        + "WHERE book_id = ? AND page_number = ?",
                ROW_MAPPER,
                bookId,
                pageNumber
        ).stream().findFirst();
    }

    public List<Page> findByBookIdRange(UUID bookId, int fromPage, int toPage) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_pages "
                        + "WHERE book_id = ? AND page_number BETWEEN ? AND ? "
                        + "ORDER BY page_number",
                ROW_MAPPER,
                bookId,
                fromPage,
                toPage
        );
    }

    /**
     * Уникальные значения {@code part} (томов/juz') в книге в порядке
     * первого появления. Используется для построения dropdown селектора
     * томов в reader-фронте: если у книги > 1 part, фронт показывает
     * dropdown, иначе скрывает.
     */
    public List<String> findDistinctPartsByBookId(UUID bookId) {
        return jdbcTemplate.queryForList(
                "SELECT part FROM lib_pages "
                        + "WHERE book_id = ? AND part IS NOT NULL "
                        + "GROUP BY part ORDER BY MIN(page_number)",
                String.class,
                bookId
        );
    }

    /**
     * Partial update только колонки {@code formatted_content}
     * (миграция 33, ADR-039). Используется admin editor flow когда
     * пользователь сохраняет ProseMirror JSON через PATCH endpoint.
     * Прочие поля (text_content, image_url, printed_page) не трогаются -
     * editor не меняет structural metadata страницы.
     *
     * <p>Также bump {@code updated_at} для отслеживания «когда страница
     * последний раз редактировалась».
     *
     * @return true если row updated, false если page id не найден
     */
    public boolean updateFormattedContent(UUID id, String formattedContent) {
        int rows = jdbcTemplate.update(
                "UPDATE lib_pages SET formatted_content = ?::jsonb, updated_at = now() "
                        + "WHERE id = ?",
                formattedContent,
                id
        );
        return rows > 0;
    }

    /**
     * Установить или обновить pointer на uploaded image страницы
     * (миграция 34, ADR-041). Используется {@code PageImageService}
     * после успешного S3 put. {@code ocr_status} переводится в
     * {@code PENDING} - signal для downstream OCR pipeline что
     * страница ждёт обработки.
     *
     * @return true если row updated, false если page id не найден
     */
    public boolean updateImagePointer(UUID id, String bucket, String storageKey,
                                       java.time.Instant uploadedAt, String ocrStatus) {
        int rows = jdbcTemplate.update(
                "UPDATE lib_pages SET "
                        + "image_bucket = ?, image_storage_key = ?, image_uploaded_at = ?, "
                        + "ocr_status = ?, updated_at = now() "
                        + "WHERE id = ?",
                bucket,
                storageKey,
                odt(uploadedAt),
                ocrStatus,
                id
        );
        return rows > 0;
    }

    /**
     * Обновить OCR state machine - текущий status + opt timestamps
     * (started/completed). Используется {@code OcrService} для перевода
     * page между состояниями pipeline.
     *
     * <p>Все поля кроме {@code id}/{@code ocrStatus} nullable - можно
     * обновить только status (например на PROCESSING без completed_at).
     *
     * @return true если row updated, false если page id не найден
     */
    public boolean updateOcrStatus(UUID id, String ocrStatus,
                                    java.time.Instant ocrStartedAt,
                                    java.time.Instant ocrCompletedAt) {
        int rows = jdbcTemplate.update(
                "UPDATE lib_pages SET ocr_status = ?, "
                        + "ocr_started_at = COALESCE(?, ocr_started_at), "
                        + "ocr_completed_at = COALESCE(?, ocr_completed_at), "
                        + "updated_at = now() "
                        + "WHERE id = ?",
                ocrStatus,
                odt(ocrStartedAt),
                odt(ocrCompletedAt),
                id
        );
        return rows > 0;
    }

    /**
     * Перезаписать text_content с результатом OCR + бамп updated_at +
     * установить ocr_status=DONE и completed_at=now. Атомарная транзакция -
     * либо весь успех, либо вся ошибка. Используется {@code OcrService}
     * при успешном завершении recognize.
     *
     * @return true если row updated, false если page id не найден
     */
    public boolean updateTextContentAndMarkDone(UUID id, String textContent,
                                                  java.time.Instant completedAt) {
        int rows = jdbcTemplate.update(
                "UPDATE lib_pages SET "
                        + "text_content = ?, ocr_status = 'DONE', "
                        + "ocr_completed_at = ?, updated_at = now() "
                        + "WHERE id = ?",
                textContent,
                odt(completedAt),
                id
        );
        return rows > 0;
    }

    /**
     * Обновить AI edit state machine (миграция 35, ADR-042) - текущий
     * status + opt timestamps. Используется {@code AiEditService} для
     * перевода page между состояниями pipeline.
     *
     * <p>{@code COALESCE} логика: при PROCESSING передаём started_at,
     * completed_at=null - existing completed_at не затирается. При
     * DONE/FAILED передаём completed_at, started_at=null - existing
     * started_at не затирается.
     *
     * @return true если row updated, false если page id не найден
     */
    public boolean updateAiEditStatus(UUID id, String aiEditStatus,
                                       java.time.Instant aiEditStartedAt,
                                       java.time.Instant aiEditCompletedAt) {
        int rows = jdbcTemplate.update(
                "UPDATE lib_pages SET ai_edit_status = ?, "
                        + "ai_edit_started_at = COALESCE(?, ai_edit_started_at), "
                        + "ai_edit_completed_at = COALESCE(?, ai_edit_completed_at), "
                        + "updated_at = now() "
                        + "WHERE id = ?",
                aiEditStatus,
                odt(aiEditStartedAt),
                odt(aiEditCompletedAt),
                id
        );
        return rows > 0;
    }

    /**
     * Атомарно записать AI-сгенерированный ProseMirror JSON в
     * {@code formatted_content} + установить {@code ai_edit_status=DONE}
     * + {@code ai_edit_completed_at=now} (миграция 35, ADR-042).
     * Используется {@code AiEditService} при успешном LLM response.
     *
     * @return true если row updated, false если page id не найден
     */
    public boolean updateFormattedContentAndMarkAiEditDone(UUID id, String formattedContent,
                                                             java.time.Instant completedAt) {
        int rows = jdbcTemplate.update(
                "UPDATE lib_pages SET formatted_content = ?::jsonb, "
                        + "ai_edit_status = 'DONE', ai_edit_completed_at = ?, "
                        + "updated_at = now() "
                        + "WHERE id = ?",
                formattedContent,
                odt(completedAt),
                id
        );
        return rows > 0;
    }

    public boolean deleteById(UUID id) {
        return jdbcTemplate.update("DELETE FROM lib_pages WHERE id = ?", id) > 0;
    }
}
