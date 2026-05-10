package ru.basnukaev.argumentmap.library.shamela.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.domain.Authority;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.Chapter;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.ChapterRepository;
import ru.basnukaev.argumentmap.library.repository.PageRepository;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaAuthorRow;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaBookRow;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaPageRow;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaTitleRow;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaAuthorDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaBookDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaPageDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaTitleDao;
import ru.basnukaev.argumentmap.repository.AuthorityRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ShamelaToLibraryMapperIT {

    @Autowired private ShamelaToLibraryMapper mapper;
    @Autowired private ShamelaBookDao shamelaBookDao;
    @Autowired private ShamelaAuthorDao shamelaAuthorDao;
    @Autowired private ShamelaTitleDao shamelaTitleDao;
    @Autowired private ShamelaPageDao shamelaPageDao;
    @Autowired private BookRepository bookRepository;
    @Autowired private AuthorityRepository authorityRepository;
    @Autowired private ChapterRepository chapterRepository;
    @Autowired private PageRepository pageRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID testUserId;

    @BeforeEach
    void cleanup() {
        // снизу вверх по FK: pages → chapters → books → authorities → staging-pages → staging-titles → ...
        jdbcTemplate.update("DELETE FROM lib_image_regions");
        jdbcTemplate.update("DELETE FROM lib_pages");
        jdbcTemplate.update("DELETE FROM lib_chapters");
        jdbcTemplate.update("DELETE FROM lib_books");
        jdbcTemplate.update("DELETE FROM authorities");
        jdbcTemplate.update("DELETE FROM lib_shamela_page");
        jdbcTemplate.update("DELETE FROM lib_shamela_title");
        jdbcTemplate.update("DELETE FROM lib_shamela_book");
        jdbcTemplate.update("DELETE FROM lib_shamela_author");
        jdbcTemplate.update("DELETE FROM lib_shamela_category");
        // user-таблица - не удаляем целиком, только наш test-user
        testUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?) ON CONFLICT (id) DO NOTHING",
                testUserId, "shamela-mapper-it-" + testUserId, testUserId + "@test.local"
        );
    }

    // ---------------- happy path ----------------

    @Test
    void mapBook_persistsPrintedPageAndPartFromShamela() {
        // регрессия: source-first нумерация (ADR-021, миграция 19).
        // Раньше Mapper брал только id страницы из shamela, теряя
        // printed_page (маркер оригинального издания) и part (том/juz').
        // После миграции 19 эти поля сохраняются в lib_pages
        seedAuthor(100L, "ابن كثير", null);
        seedBookWithPdfLinks(7000L, "تفسير ابن كثير", 100L, 1, null);
        seedPageWithMarkers(7000L, 1, "introduction-content", "المقدمة", "أ");
        seedPageWithMarkers(7000L, 2, "first-chapter-content", "1", "47");
        seedPage(7000L, 3, "page-without-markers");

        MappedBookResult result = mapper.mapBook(7000L, testUserId);
        List<Page> pages = pageRepository.findByBookIdRange(result.bookId(), 1, Integer.MAX_VALUE);

        assertThat(pages).hasSize(3);
        assertThat(pages.get(0).printedPage()).isEqualTo("أ");
        assertThat(pages.get(0).part()).isEqualTo("المقدمة");
        assertThat(pages.get(0).pdfPageNumber()).isNull();
        assertThat(pages.get(1).printedPage()).isEqualTo("47");
        assertThat(pages.get(1).part()).isEqualTo("1");
        // страница без markers - все поля null (blankToNull обрабатывает
        // и null, и пустую строку одинаково)
        assertThat(pages.get(2).printedPage()).isNull();
        assertThat(pages.get(2).part()).isNull();
    }

    @Test
    void mapBook_creates_book_chapters_pages_and_resolves_authority() {
        long shamelaBookId = 41557L;
        long shamelaAuthorId = 100L;
        seedAuthor(shamelaAuthorId, "Аль-Бухари", "Имам ахль ас-сунна");
        seedBook(shamelaBookId, "صحيح البخاري", shamelaAuthorId, 4, "библиография", "[\"/1/41557.pdf\"]");
        seedTitle(shamelaBookId, 1, "كتاب الإيمان", null, "1");
        seedTitle(shamelaBookId, 2, "باب الإخلاص", 1, "2");
        seedPage(shamelaBookId, 1, "<p>الصفحة الأولى</p>");
        seedPage(shamelaBookId, 2, "<p>الصفحة الثانية</p>");
        seedPage(shamelaBookId, 3, "<p>الصفحة الثالثة</p>");

        MappedBookResult result = mapper.mapBook(shamelaBookId, testUserId);

        assertThat(result.created()).isTrue();
        assertThat(result.shamelaBookId()).isEqualTo(shamelaBookId);
        assertThat(result.chaptersCount()).isEqualTo(2);
        assertThat(result.pagesCount()).isEqualTo(3);

        Book book = bookRepository.findById(result.bookId()).orElseThrow();
        assertThat(book.bookType()).isEqualTo(BookType.BOOK);
        assertThat(book.title()).isEqualTo("صحيح البخاري");
        assertThat(book.language()).isEqualTo("ar");
        assertThat(book.description()).isEqualTo("библиография");
        assertThat(book.createdBy()).isEqualTo(testUserId);

        Authority authority = authorityRepository.findById(book.authorityId()).orElseThrow();
        assertThat(authority.name()).isEqualTo("Аль-Бухари");
        assertThat(authority.bio()).isEqualTo("Имам ахль ас-сунна");
    }

    @Test
    void mapBook_stores_shamela_fields_in_metadata_jsonb() throws Exception {
        long shamelaBookId = 41557L;
        seedAuthor(100L, "Автор", null);
        String pdfLinksJson = "{\"files\":[\"/1/41557.pdf\"]}";
        seedBookWithPdfLinks(shamelaBookId, "название", 100L, 7, pdfLinksJson);

        MappedBookResult result = mapper.mapBook(shamelaBookId, testUserId);

        Book book = bookRepository.findById(result.bookId()).orElseThrow();
        JsonNode metadata = objectMapper.readTree(book.metadata());
        assertThat(metadata.get("shamela_book_id").asLong()).isEqualTo(shamelaBookId);
        assertThat(metadata.get("shamela_major_release").asInt()).isEqualTo(7);
        assertThat(metadata.get("pdf_links").get("files").get(0).asText()).isEqualTo("/1/41557.pdf");
    }

    // ---------------- re-import idempotency ----------------

    @Test
    void mapBook_returns_already_mapped_on_repeat() {
        long shamelaBookId = 41557L;
        seedAuthor(100L, "Автор", null);
        seedBook(shamelaBookId, "книга", 100L, 1, null, null);
        seedPage(shamelaBookId, 1, "<p>контент</p>");

        MappedBookResult first = mapper.mapBook(shamelaBookId, testUserId);
        assertThat(first.created()).isTrue();
        assertThat(first.pagesCount()).isEqualTo(1);

        MappedBookResult second = mapper.mapBook(shamelaBookId, testUserId);
        assertThat(second.created()).isFalse();
        assertThat(second.bookId()).isEqualTo(first.bookId());
        assertThat(second.pagesCount()).isZero();

        // sanity: только одна Book-запись и одна Authority с этим именем
        assertThat(bookRepository.findAll(null, null)).hasSize(1);
        assertThat(authorityRepository.findAll()).hasSize(1);
        assertThat(pageRepository.findByBookIdRange(first.bookId(), 1, 100)).hasSize(1);
    }

    // ---------------- authority resolution ----------------

    @Test
    void mapBook_uses_anonymous_authority_when_author_id_null() {
        long shamelaBookId = 1L;
        seedBook(shamelaBookId, "анонимная книга", null, 1, null, null);

        MappedBookResult result = mapper.mapBook(shamelaBookId, testUserId);

        Authority anonymous = authorityRepository.findById(result.authorityId()).orElseThrow();
        assertThat(anonymous.name()).isEqualTo(ShamelaToLibraryMapper.ANONYMOUS_AUTHORITY_NAME);
    }

    // Сценарий "dangling author_id" (FK на несуществующего автора) тестируется
    // только защитной веткой кода - на уровне БД lib_shamela_book.author_id имеет
    // FK на lib_shamela_author с ON DELETE SET NULL, что гарантирует невозможность
    // dangling через нормальный DAO insert. Защитная ветка в Mapper остаётся как
    // safety-net на случай программного нарушения инварианта (manual SQL/debug),
    // но проверять её через тест нельзя без отключения FK

    @Test
    void mapBook_reuses_existing_authority_with_same_normalized_name() {
        long firstBook = 1L;
        long secondBook = 2L;
        // лидерующие/трейлинг-пробелы в shamela-имени должны схлопываться
        seedAuthor(100L, "  Ибн   Таймия  ", null);
        seedBook(firstBook, "книга1", 100L, 1, null, null);
        seedBook(secondBook, "книга2", 100L, 2, null, null);

        MappedBookResult r1 = mapper.mapBook(firstBook, testUserId);
        MappedBookResult r2 = mapper.mapBook(secondBook, testUserId);

        assertThat(r1.authorityId()).isEqualTo(r2.authorityId());
        Authority authority = authorityRepository.findById(r1.authorityId()).orElseThrow();
        assertThat(authority.name()).isEqualTo("Ибн Таймия");
        // в БД ровно одна Authority - переиспользована
        assertThat(authorityRepository.findAll()).hasSize(1);
    }

    @Test
    void mapBook_reuses_authority_already_present_from_other_source() {
        long shamelaBookId = 1L;
        // Authority уже есть в БД (например, добавлена пользователем вручную),
        // shamela импорт должен её переиспользовать
        UUID existingId = UUID.randomUUID();
        authorityRepository.save(new Authority(existingId, "Аль-Газали", "Уже был в БД",
                null, null, null, java.time.Instant.now()));
        seedAuthor(50L, "Аль-Газали", "shamela bio");
        seedBook(shamelaBookId, "Ихья", 50L, 1, null, null);

        MappedBookResult result = mapper.mapBook(shamelaBookId, testUserId);

        assertThat(result.authorityId()).isEqualTo(existingId);
        // bio существующей не перезатёрся
        Authority authority = authorityRepository.findById(existingId).orElseThrow();
        assertThat(authority.bio()).isEqualTo("Уже был в БД");
    }

    // ---------------- chapter tree ----------------

    @Test
    void mapBook_builds_correct_chapter_tree() {
        long shamelaBookId = 1L;
        seedAuthor(100L, "автор", null);
        seedBook(shamelaBookId, "tree-book", 100L, 1, null, null);
        // root1 -> child11 -> grand111
        // root2
        seedTitle(shamelaBookId, 10, "root1", null, "1");
        seedTitle(shamelaBookId, 20, "child11", 10, "2");
        seedTitle(shamelaBookId, 30, "grand111", 20, "3");
        seedTitle(shamelaBookId, 40, "root2", null, "4");
        seedPage(shamelaBookId, 1, "<p>page1</p>");

        MappedBookResult result = mapper.mapBook(shamelaBookId, testUserId);

        List<Chapter> chapters = chapterRepository.findByBookId(result.bookId());
        assertThat(chapters).hasSize(4);

        // root-уровень - те у кого parentChapterId = null
        List<Chapter> roots = chapters.stream().filter(c -> c.parentChapterId() == null).toList();
        assertThat(roots).extracting(Chapter::title).containsExactlyInAnyOrder("root1", "root2");

        Chapter root1 = chapters.stream().filter(c -> c.title().equals("root1")).findFirst().orElseThrow();
        Chapter child11 = chapters.stream().filter(c -> c.title().equals("child11")).findFirst().orElseThrow();
        Chapter grand111 = chapters.stream().filter(c -> c.title().equals("grand111")).findFirst().orElseThrow();
        assertThat(child11.parentChapterId()).isEqualTo(root1.id());
        assertThat(grand111.parentChapterId()).isEqualTo(child11.id());
    }

    @Test
    void mapBook_treats_orphan_parent_as_root() {
        long shamelaBookId = 1L;
        seedBook(shamelaBookId, "orphan-test", null, 1, null, null);
        // parent_id=999 - такого title нет в книге, должен стать root
        seedTitle(shamelaBookId, 10, "orphan", 999, "1");

        MappedBookResult result = mapper.mapBook(shamelaBookId, testUserId);

        List<Chapter> chapters = chapterRepository.findByBookId(result.bookId());
        assertThat(chapters).hasSize(1);
        assertThat(chapters.get(0).parentChapterId()).isNull();
    }

    // ---------------- pages ----------------

    @Test
    void mapBook_skips_blank_content_pages() {
        // lib_shamela_page.content NOT NULL - null нельзя протестировать через DAO,
        // но empty/whitespace-only возможны. Mapper их пропускает: lib_pages_content_present
        // CHECK требует text_content или image_url, а imageUrl у shamela-импорта всегда null
        long shamelaBookId = 1L;
        seedBook(shamelaBookId, "skip-test", null, 1, null, null);
        seedPage(shamelaBookId, 1, "<p>real content</p>");
        seedPage(shamelaBookId, 2, "");           // blank
        seedPage(shamelaBookId, 3, "   \n  ");    // whitespace-only

        MappedBookResult result = mapper.mapBook(shamelaBookId, testUserId);

        assertThat(result.pagesCount()).isEqualTo(1);
        List<Page> pages = pageRepository.findByBookIdRange(result.bookId(), 1, 100);
        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).pageNumber()).isEqualTo(1);
        assertThat(pages.get(0).textContent()).isEqualTo("<p>real content</p>");
        assertThat(pages.get(0).chapterId()).isNull(); // MVP - не привязываем к chapter
    }

    // ---------------- validation ----------------

    @Test
    void mapBook_throws_when_shamela_book_missing_in_staging() {
        long ghost = 99999L;

        assertThatThrownBy(() -> mapper.mapBook(ghost, testUserId))
                .isInstanceOf(ShamelaImportException.class)
                .hasMessageContaining("99999")
                .hasMessageContaining("syncMaster");
    }

    // ---------------- helpers ----------------

    private void seedAuthor(long id, String name, String bio) {
        shamelaAuthorDao.upsertAll(List.of(new ShamelaAuthorRow(id, name, bio, null, false)));
    }

    private void seedBook(long id, String name, Long authorId, int majorRelease,
                          String bibliography, String pdfLinksJson) {
        seedBookWithPdfLinks(id, name, authorId, majorRelease, pdfLinksJson, bibliography);
    }

    /**
     * Для книг с реалистичными pdf_links значениями. Вторичный helper для
     * тестов которые проверяют metadata-структуру.
     */
    private void seedBookWithPdfLinks(long id, String name, Long authorId, int majorRelease,
                                      String pdfLinksJson) {
        seedBookWithPdfLinks(id, name, authorId, majorRelease, pdfLinksJson, null);
    }

    private void seedBookWithPdfLinks(long id, String name, Long authorId, int majorRelease,
                                      String pdfLinksJson, String bibliography) {
        shamelaBookDao.upsertAll(List.of(new ShamelaBookRow(
                id, name, null, authorId, null, null, null,
                majorRelease, 0, bibliography, null, pdfLinksJson, null, false
        )));
    }

    private void seedTitle(long bookId, int titleId, String content, Integer parentId, String pageRef) {
        shamelaTitleDao.upsertAll(List.of(new ShamelaTitleRow(bookId, titleId, content, pageRef, parentId)));
    }

    private void seedPage(long bookId, int pageId, String content) {
        shamelaPageDao.upsertAll(List.of(new ShamelaPageRow(bookId, pageId, content,
                null, null, null, null)));
    }

    private void seedPageWithMarkers(long bookId, int pageId, String content,
                                     String part, String printedPage) {
        shamelaPageDao.upsertAll(List.of(new ShamelaPageRow(bookId, pageId, content,
                part, printedPage, null, null)));
    }

    /**
     * Не используется напрямую, но оставлен для будущих тестов где нужно
     * проверить что mapper не падает на {@code Optional<ShamelaBookRow>}
     * c пустым результатом - текущий happy path этого не покрывает,
     * только validation.
     */
    @SuppressWarnings("unused")
    private Optional<ShamelaBookRow> reloadBookFromStaging(long id) {
        return shamelaBookDao.findById(id);
    }
}
