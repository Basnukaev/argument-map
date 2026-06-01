package ru.basnukaev.argumentmap.qa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.domain.PdfBbox;
import ru.basnukaev.argumentmap.domain.Source;
import ru.basnukaev.argumentmap.domain.SourceType;
import ru.basnukaev.argumentmap.exception.BookNotFoundException;
import ru.basnukaev.argumentmap.exception.ImageRegionNotFoundException;
import ru.basnukaev.argumentmap.exception.InvalidCitationException;
import ru.basnukaev.argumentmap.exception.PageNotFoundException;
import ru.basnukaev.argumentmap.exception.QuestionNotFoundException;
import ru.basnukaev.argumentmap.exception.QuestionWriteAccessDeniedException;
import ru.basnukaev.argumentmap.exception.SourceNotFoundException;
import ru.basnukaev.argumentmap.qa.domain.Question;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.LibraryFile;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.pdf.service.PdfNotAvailableException;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.LibraryFileRepository;
import ru.basnukaev.argumentmap.library.repository.PageRepository;
import ru.basnukaev.argumentmap.qa.domain.QuestionSource;
import ru.basnukaev.argumentmap.qa.repository.QuestionRepository;
import ru.basnukaev.argumentmap.qa.repository.QuestionSourceRepository;
import ru.basnukaev.argumentmap.qa.web.dto.QuestionSourceResponse;
import ru.basnukaev.argumentmap.qa.web.mapper.QaDtoMappers;
import ru.basnukaev.argumentmap.repository.SourceRepository;
import ru.basnukaev.argumentmap.web.dto.CitationRequest;

/**
 * Сервис создания positional citation для Q&amp;A (Этап 19.b). Аналог
 * {@code NodeCitationService} с {@code questionId} вместо
 * {@code nodeId}. Та же валидация TEXT/PDF/REGION, та же ensure-or-create
 * Source per book, тот же snapshot location format.
 *
 * <p>Доказательство ADR-018 platform pivot: вся бизнес-логика citation
 * (validation, ensure-Source, snapshot, JOIN responses) переиспользуется
 * без изменений на другой сущности через параллельную иерархию.
 */
@Service
public class QuestionCitationService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final QuestionRepository questionRepository;
    private final BookRepository bookRepository;
    private final PageRepository pageRepository;
    private final SourceRepository sourceRepository;
    private final QuestionSourceRepository questionSourceRepository;
    private final LibraryFileRepository libraryFileRepository;
    private final JdbcTemplate jdbcTemplate;

    public QuestionCitationService(QuestionRepository questionRepository,
                                   BookRepository bookRepository,
                                   PageRepository pageRepository,
                                   SourceRepository sourceRepository,
                                   QuestionSourceRepository questionSourceRepository,
                                   LibraryFileRepository libraryFileRepository,
                                   JdbcTemplate jdbcTemplate) {
        this.questionRepository = questionRepository;
        this.bookRepository = bookRepository;
        this.pageRepository = pageRepository;
        this.sourceRepository = sourceRepository;
        this.questionSourceRepository = questionSourceRepository;
        this.libraryFileRepository = libraryFileRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Create citation с author/admin guard (ADR-043 Amendment, Q&amp;A guards).
     * Citation вешается на вопрос - мутация, доступная только автору вопроса
     * (asked_by) или ADMIN. Симметрично QuestionService.updateQuestion.
     * Без этого любой authenticated мог вешать citation на чужой вопрос.
     *
     * @throws QuestionNotFoundException если вопроса нет (404)
     * @throws QuestionWriteAccessDeniedException если не автор вопроса и не ADMIN (403)
     */
    @Transactional
    public QuestionSourceResponse createCitation(UUID questionId, CitationRequest req,
                                                 UUID actorUserId, String actorRole) {
        assertQuestionAuthorOrAdmin(questionId, actorUserId, actorRole);
        return createCitation(questionId, req);
    }

    /**
     * Legacy overload без author-guard. Internal callers + IT. REST endpoint
     * должен звать {@link #createCitation(UUID, CitationRequest, UUID, String)}.
     */
    @Transactional
    public QuestionSourceResponse createCitation(UUID questionId, CitationRequest req) {
        if (questionRepository.findById(questionId).isEmpty()) {
            throw new QuestionNotFoundException(questionId);
        }
        if (req.bookId() == null) {
            throw new InvalidCitationException("bookId обязателен");
        }
        Book book = bookRepository.findById(req.bookId())
                .orElseThrow(() -> new BookNotFoundException(req.bookId()));

        boolean isText = req.pageId() != null;
        boolean isPdf = req.pdfFileId() != null;
        boolean isRegion = req.imageRegionId() != null;
        int activeModes = (isText ? 1 : 0) + (isPdf ? 1 : 0) + (isRegion ? 1 : 0);
        if (activeModes != 1) {
            throw new InvalidCitationException(
                    "Ровно один из (pageId / pdfFileId / imageRegionId) должен быть указан, получено: "
                            + activeModes);
        }

        Page page = null;
        LibraryFile pdfFile = null;

        if (isText) {
            if (req.rangeStart() == null || req.rangeEnd() == null) {
                throw new InvalidCitationException("rangeStart и rangeEnd обязательны для TEXT mode");
            }
            if (req.rangeStart() < 0 || req.rangeEnd() <= req.rangeStart()) {
                throw new InvalidCitationException(
                        "Невалидный range: rangeStart=" + req.rangeStart()
                                + " rangeEnd=" + req.rangeEnd());
            }
            page = pageRepository.findById(req.pageId())
                    .orElseThrow(() -> new PageNotFoundException(req.pageId()));
            if (!page.bookId().equals(req.bookId())) {
                throw new InvalidCitationException(
                        "pageId=" + req.pageId() + " не принадлежит bookId=" + req.bookId());
            }
        }
        if (isPdf) {
            if (req.pdfPageNumber() == null || req.pdfPageNumber() < 1) {
                throw new InvalidCitationException("pdfPageNumber >= 1 обязателен для PDF mode");
            }
            if (req.pdfBbox() == null) {
                throw new InvalidCitationException("pdfBbox обязателен для PDF mode");
            }
            pdfFile = libraryFileRepository.findById(req.pdfFileId())
                    .filter(f -> f.deletedAt() == null)
                    .orElseThrow(() -> new PdfNotAvailableException(req.pdfFileId()));
        }
        if (isRegion) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM lib_image_regions WHERE id = ?",
                    Integer.class, req.imageRegionId());
            if (count == null || count == 0) {
                throw new ImageRegionNotFoundException(req.imageRegionId());
            }
        }

        Source source = sourceRepository.findByBookId(req.bookId()).orElseGet(() -> {
            Source created = new Source(
                    UUID.randomUUID(),
                    SourceType.BOOK,
                    book.title(),
                    null,
                    null,
                    book.authorityId(),
                    req.bookId(),
                    null,
                    Instant.now()
            );
            return sourceRepository.upsertByBookId(created);
        });

        String snapshotLocation = buildLocationSnapshot(book, page, pdfFile, req);

        Instant now = Instant.now();
        QuestionSource qs;
        if (isText) {
            qs = QuestionSource.textMode(
                    questionId, source.id(), req.quote(), req.context(), snapshotLocation,
                    req.pageId(), req.rangeStart(), req.rangeEnd(), now);
        } else if (isPdf) {
            qs = QuestionSource.pdfMode(
                    questionId, source.id(), req.quote(), req.context(), snapshotLocation,
                    req.pdfFileId(), req.pdfPageNumber(), pdfBboxToJson(req.pdfBbox()), now);
        } else {
            qs = QuestionSource.regionMode(
                    questionId, source.id(), req.quote(), req.context(), snapshotLocation,
                    req.imageRegionId(), now);
        }
        questionSourceRepository.save(qs);

        return questionSourceRepository.findByIdWithLocation(qs.id())
                .map(QaDtoMappers::toResponse)
                .orElseThrow(() -> new IllegalStateException(
                        "question_sources row только что создан но findByIdWithLocation empty"));
    }

    @Transactional(readOnly = true)
    public List<QuestionSourceResponse> getQuestionSourcesWithLocation(UUID questionId) {
        if (questionRepository.findById(questionId).isEmpty()) {
            throw new QuestionNotFoundException(questionId);
        }
        return questionSourceRepository.findByQuestionIdWithLocation(questionId).stream()
                .map(QaDtoMappers::toResponse)
                .toList();
    }

    /**
     * Detach с author/admin guard (ADR-043 Amendment) поверх question-scoped
     * delete. Удаление citation - мутация вопроса, только автор (asked_by)
     * или ADMIN. Question-scoped delete (WHERE id=? AND question_id=?) -
     * IDOR-защита: citation другого вопроса через путь данного вопроса не
     * удаляется (mismatch → 404, не leak'аем существование чужой citation).
     *
     * @throws QuestionNotFoundException если вопроса нет (404)
     * @throws QuestionWriteAccessDeniedException если не автор вопроса и не ADMIN (403)
     * @throws SourceNotFoundException если citation не существует ИЛИ
     *         принадлежит другому вопросу (404)
     */
    @Transactional
    public void detachById(UUID questionId, UUID questionSourceId,
                           UUID actorUserId, String actorRole) {
        assertQuestionAuthorOrAdmin(questionId, actorUserId, actorRole);
        boolean removed = questionSourceRepository.deleteByIdAndQuestion(questionSourceId, questionId);
        if (!removed) {
            throw new SourceNotFoundException(questionSourceId);
        }
    }

    /**
     * Legacy overload без author-guard и без parent-scope. Internal callers +
     * IT. REST endpoint должен звать
     * {@link #detachById(UUID, UUID, UUID, String)}.
     */
    @Transactional
    public void detachById(UUID questionSourceId) {
        boolean removed = questionSourceRepository.deleteById(questionSourceId);
        if (!removed) {
            throw new SourceNotFoundException(questionSourceId);
        }
    }

    /**
     * Guard: actor должен быть автором вопроса (asked_by) либо ADMIN.
     * Зеркалит QuestionService.assertAuthorOrAdmin.
     */
    private void assertQuestionAuthorOrAdmin(UUID questionId, UUID actorUserId, String actorRole) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException(questionId));
        if (UserRole.ADMIN.equals(actorRole)) {
            return;
        }
        if (!question.askedBy().equals(actorUserId)) {
            throw new QuestionWriteAccessDeniedException(questionId, actorUserId);
        }
    }

    private String buildLocationSnapshot(Book book, Page page, LibraryFile pdfFile, CitationRequest req) {
        String title = book.title() != null ? book.title() : "?";
        if (page != null) {
            String part = page.part() != null ? page.part() : "?";
            String printedOrInternal = page.printedPage() != null
                    ? page.printedPage()
                    : String.valueOf(page.pageNumber());
            return title + ", Т." + part + " стр." + printedOrInternal;
        }
        if (pdfFile != null) {
            return title + ", PDF стр." + req.pdfPageNumber();
        }
        return title + ", скан";
    }

    private String pdfBboxToJson(PdfBbox bbox) {
        try {
            return JSON.writeValueAsString(bbox);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать PdfBbox: " + bbox, e);
        }
    }
}
