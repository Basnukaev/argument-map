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
import ru.basnukaev.argumentmap.exception.AnswerNotFoundException;
import ru.basnukaev.argumentmap.exception.AnswerWriteAccessDeniedException;
import ru.basnukaev.argumentmap.exception.BookNotFoundException;
import ru.basnukaev.argumentmap.exception.ImageRegionNotFoundException;
import ru.basnukaev.argumentmap.exception.InvalidCitationException;
import ru.basnukaev.argumentmap.exception.PageNotFoundException;
import ru.basnukaev.argumentmap.exception.SourceNotFoundException;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.LibraryFile;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.pdf.service.PdfNotAvailableException;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.LibraryFileRepository;
import ru.basnukaev.argumentmap.library.repository.PageRepository;
import ru.basnukaev.argumentmap.qa.domain.Answer;
import ru.basnukaev.argumentmap.qa.domain.AnswerSource;
import ru.basnukaev.argumentmap.qa.repository.AnswerRepository;
import ru.basnukaev.argumentmap.qa.repository.AnswerSourceRepository;
import ru.basnukaev.argumentmap.qa.web.dto.AnswerSourceResponse;
import ru.basnukaev.argumentmap.qa.web.mapper.QaDtoMappers;
import ru.basnukaev.argumentmap.repository.SourceRepository;
import ru.basnukaev.argumentmap.web.dto.CitationRequest;

/**
 * Сервис создания positional citation для Q&amp;A answers (Этап 19.d).
 * Аналог {@code QuestionCitationService} с {@code answerId} вместо
 * {@code questionId}. Та же валидация TEXT/PDF/REGION, та же
 * ensure-or-create Source per book, тот же snapshot location format.
 *
 * <p>Третья итерация ADR-033 параллельной иерархии - подтверждает что
 * platform pivot (ADR-018) масштабируется на 3-ю сущность без перехода
 * на generic citations table.
 */
@Service
public class AnswerCitationService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AnswerRepository answerRepository;
    private final BookRepository bookRepository;
    private final PageRepository pageRepository;
    private final SourceRepository sourceRepository;
    private final AnswerSourceRepository answerSourceRepository;
    private final LibraryFileRepository libraryFileRepository;
    private final JdbcTemplate jdbcTemplate;

    public AnswerCitationService(AnswerRepository answerRepository,
                                 BookRepository bookRepository,
                                 PageRepository pageRepository,
                                 SourceRepository sourceRepository,
                                 AnswerSourceRepository answerSourceRepository,
                                 LibraryFileRepository libraryFileRepository,
                                 JdbcTemplate jdbcTemplate) {
        this.answerRepository = answerRepository;
        this.bookRepository = bookRepository;
        this.pageRepository = pageRepository;
        this.sourceRepository = sourceRepository;
        this.answerSourceRepository = answerSourceRepository;
        this.libraryFileRepository = libraryFileRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Create citation с author/admin guard (ADR-043 Amendment, Q&amp;A guards).
     * Citation вешается на ответ - мутация, доступная только автору ответа
     * (author_id) или ADMIN. Симметрично AnswerService.updateAnswer. Без
     * этого любой authenticated мог вешать citation на чужой ответ.
     *
     * @throws AnswerNotFoundException если ответа нет (404)
     * @throws AnswerWriteAccessDeniedException если не автор ответа и не ADMIN (403)
     */
    @Transactional
    public AnswerSourceResponse createCitation(UUID answerId, CitationRequest req,
                                               UUID actorUserId, String actorRole) {
        assertAnswerAuthorOrAdmin(answerId, actorUserId, actorRole);
        return createCitation(answerId, req);
    }

    /**
     * Legacy overload без author-guard. Internal callers + IT. REST endpoint
     * должен звать {@link #createCitation(UUID, CitationRequest, UUID, String)}.
     */
    @Transactional
    public AnswerSourceResponse createCitation(UUID answerId, CitationRequest req) {
        if (answerRepository.findById(answerId).isEmpty()) {
            throw new AnswerNotFoundException(answerId);
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
        AnswerSource as;
        if (isText) {
            as = AnswerSource.textMode(
                    answerId, source.id(), req.quote(), req.context(), snapshotLocation,
                    req.pageId(), req.rangeStart(), req.rangeEnd(), now);
        } else if (isPdf) {
            as = AnswerSource.pdfMode(
                    answerId, source.id(), req.quote(), req.context(), snapshotLocation,
                    req.pdfFileId(), req.pdfPageNumber(), pdfBboxToJson(req.pdfBbox()), now);
        } else {
            as = AnswerSource.regionMode(
                    answerId, source.id(), req.quote(), req.context(), snapshotLocation,
                    req.imageRegionId(), now);
        }
        answerSourceRepository.save(as);

        return answerSourceRepository.findByIdWithLocation(as.id())
                .map(QaDtoMappers::toResponse)
                .orElseThrow(() -> new IllegalStateException(
                        "answer_sources row только что создан но findByIdWithLocation empty"));
    }

    @Transactional(readOnly = true)
    public List<AnswerSourceResponse> getAnswerSourcesWithLocation(UUID answerId) {
        if (answerRepository.findById(answerId).isEmpty()) {
            throw new AnswerNotFoundException(answerId);
        }
        return answerSourceRepository.findByAnswerIdWithLocation(answerId).stream()
                .map(QaDtoMappers::toResponse)
                .toList();
    }

    /**
     * Detach с author/admin guard (ADR-043 Amendment) поверх answer-scoped
     * delete. Удаление citation - мутация ответа, только автор (author_id)
     * или ADMIN. Answer-scoped delete (WHERE id=? AND answer_id=?) -
     * IDOR-защита: citation другого ответа через путь данного ответа не
     * удаляется (mismatch → 404, не leak'аем существование чужой citation).
     *
     * @throws AnswerNotFoundException если ответа нет (404)
     * @throws AnswerWriteAccessDeniedException если не автор ответа и не ADMIN (403)
     * @throws SourceNotFoundException если citation не существует ИЛИ
     *         принадлежит другому ответу (404)
     */
    @Transactional
    public void detachById(UUID answerId, UUID answerSourceId,
                           UUID actorUserId, String actorRole) {
        assertAnswerAuthorOrAdmin(answerId, actorUserId, actorRole);
        boolean removed = answerSourceRepository.deleteByIdAndAnswer(answerSourceId, answerId);
        if (!removed) {
            throw new SourceNotFoundException(answerSourceId);
        }
    }

    /**
     * Legacy overload без author-guard и без parent-scope. Internal callers +
     * IT. REST endpoint должен звать
     * {@link #detachById(UUID, UUID, UUID, String)}.
     */
    @Transactional
    public void detachById(UUID answerSourceId) {
        boolean removed = answerSourceRepository.deleteById(answerSourceId);
        if (!removed) {
            throw new SourceNotFoundException(answerSourceId);
        }
    }

    /**
     * Guard: actor должен быть автором ответа (author_id) либо ADMIN.
     * Зеркалит AnswerService.assertAuthorOrAdmin.
     */
    private void assertAnswerAuthorOrAdmin(UUID answerId, UUID actorUserId, String actorRole) {
        Answer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new AnswerNotFoundException(answerId));
        if (UserRole.ADMIN.equals(actorRole)) {
            return;
        }
        if (!answer.authorId().equals(actorUserId)) {
            throw new AnswerWriteAccessDeniedException(answerId, actorUserId);
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
