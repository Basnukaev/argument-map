package ru.basnukaev.argumentmap.exception;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import ru.basnukaev.argumentmap.library.imports.AiEditNotConfiguredException;
import ru.basnukaev.argumentmap.library.imports.AnthropicApiException;
import ru.basnukaev.argumentmap.library.imports.FileImportException;
import ru.basnukaev.argumentmap.library.imports.PageImageException;
import ru.basnukaev.argumentmap.library.imports.web.UnsupportedMediaTypeException;
import ru.basnukaev.argumentmap.library.pdf.service.PdfNotAvailableException;
import ru.basnukaev.argumentmap.library.pdf.service.RangeNotSatisfiableException;
import ru.basnukaev.argumentmap.library.shamela.api.ShamelaApiException;
import ru.basnukaev.argumentmap.library.shamela.etl.ShamelaArchiveException;
import ru.basnukaev.argumentmap.library.shamela.etl.ShamelaReaderException;
import ru.basnukaev.argumentmap.library.shamela.service.ShamelaImportException;
import ru.basnukaev.argumentmap.library.shamela.service.ShamelaNotFoundException;

/**
 * Глобальный обработчик исключений → Problem Details (RFC 7807).
 * Spring сам выставит Content-Type: application/problem+json.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_TYPE_BASE = "https://argumentmap.example/errors/";

    @ExceptionHandler(TopicNotFoundException.class)
    public ProblemDetail handleTopicNotFound(TopicNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Тема не найдена", "topic-not-found", ex.getMessage());
    }

    @ExceptionHandler(NodeNotFoundException.class)
    public ProblemDetail handleNodeNotFound(NodeNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Узел не найден", "node-not-found", ex.getMessage());
    }

    @ExceptionHandler(InvalidVoteException.class)
    public ProblemDetail handleInvalidVote(InvalidVoteException ex) {
        return problem(HttpStatus.BAD_REQUEST,
                "Невалидный голос", "invalid-vote", ex.getMessage());
    }

    @ExceptionHandler(NodeIsRootException.class)
    public ProblemDetail handleNodeIsRoot(NodeIsRootException ex) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT,
                "Корневой узел нельзя удалить",
                "node-is-root",
                "Корневой вопрос темы нельзя удалить отдельно - "
                        + "удалите тему целиком, чтобы убрать корень");
        pd.setProperty("nodeId", ex.getNodeId().toString());
        pd.setProperty("topicId", ex.getTopicId().toString());
        return pd;
    }

    @ExceptionHandler(EdgeNotFoundException.class)
    public ProblemDetail handleEdgeNotFound(EdgeNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Ребро не найдено", "edge-not-found", ex.getMessage());
    }

    @ExceptionHandler(InvalidEdgeException.class)
    public ProblemDetail handleInvalidEdge(InvalidEdgeException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "Невалидное ребро", "invalid-edge", ex.getMessage());
    }

    @ExceptionHandler(SourceNotFoundException.class)
    public ProblemDetail handleSourceNotFound(SourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Источник не найден",
                "source-not-found", ex.getMessage());
    }

    @ExceptionHandler(AuthorityNotFoundException.class)
    public ProblemDetail handleAuthorityNotFound(AuthorityNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Авторитет не найден",
                "authority-not-found", ex.getMessage());
    }

    @ExceptionHandler(InvalidSourceException.class)
    public ProblemDetail handleInvalidSource(InvalidSourceException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "Невалидный источник", "invalid-source", ex.getMessage());
    }

    @ExceptionHandler(BookNotFoundException.class)
    public ProblemDetail handleBookNotFound(BookNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Книга не найдена",
                "book-not-found", ex.getMessage());
    }

    @ExceptionHandler(QuestionNotFoundException.class)
    public ProblemDetail handleQuestionNotFound(QuestionNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Вопрос не найден",
                "question-not-found", ex.getMessage());
    }

    @ExceptionHandler(AnswerNotFoundException.class)
    public ProblemDetail handleAnswerNotFound(AnswerNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Ответ не найден",
                "answer-not-found", ex.getMessage());
    }

    @ExceptionHandler(PageNotFoundException.class)
    public ProblemDetail handlePageNotFound(PageNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Страница не найдена",
                "page-not-found", ex.getMessage());
    }

    @ExceptionHandler(InvalidBookException.class)
    public ProblemDetail handleInvalidBook(InvalidBookException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "Невалидная книга", "invalid-book", ex.getMessage());
    }

    @ExceptionHandler(InvalidCitationException.class)
    public ProblemDetail handleInvalidCitation(InvalidCitationException ex) {
        return problem(HttpStatus.BAD_REQUEST,
                "Невалидная цитата", "invalid-citation", ex.getMessage());
    }

    @ExceptionHandler(ImageRegionNotFoundException.class)
    public ProblemDetail handleImageRegionNotFound(ImageRegionNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND,
                "Image region не найден", "image-region-not-found", ex.getMessage());
    }

    @ExceptionHandler(PdfNotAvailableException.class)
    public ProblemDetail handlePdfNotAvailable(PdfNotAvailableException ex) {
        return problem(HttpStatus.NOT_FOUND,
                "PDF недоступен", "pdf-not-available", ex.getMessage());
    }

    @ExceptionHandler(RangeNotSatisfiableException.class)
    public ProblemDetail handleRangeNotSatisfiable(RangeNotSatisfiableException ex) {
        ProblemDetail pd = problem(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                "Range вне диапазона файла",
                "range-not-satisfiable",
                ex.getMessage());
        pd.setProperty("start", ex.start());
        pd.setProperty("totalSize", ex.totalSize());
        return pd;
    }

    @ExceptionHandler(MissingUserHeaderException.class)
    public ProblemDetail handleMissingUser(MissingUserHeaderException ex) {
        return problem(HttpStatus.BAD_REQUEST,
                "Отсутствует или невалидный заголовок X-User-Id",
                "missing-user-header", ex.getMessage());
    }

    // ---- auth (ADR-040, Этап 21) ----

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND,
                "Пользователь не найден", "user-not-found", ex.getMessage());
    }

    @ExceptionHandler(EmailAlreadyTakenException.class)
    public ProblemDetail handleEmailTaken(EmailAlreadyTakenException ex) {
        return problem(HttpStatus.CONFLICT,
                "Email уже зарегистрирован", "email-already-taken", ex.getMessage());
    }

    @ExceptionHandler(UsernameAlreadyTakenException.class)
    public ProblemDetail handleUsernameTaken(UsernameAlreadyTakenException ex) {
        return problem(HttpStatus.CONFLICT,
                "Имя пользователя занято", "username-already-taken", ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        // Унифицированный ответ - не leak'аем какой именно факт неверный.
        return problem(HttpStatus.UNAUTHORIZED,
                "Неверный email или пароль", "invalid-credentials",
                "Неверный email или пароль");
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ProblemDetail handleInvalidToken(InvalidTokenException ex) {
        return problem(HttpStatus.UNAUTHORIZED,
                "Невалидный или истёкший токен", "invalid-token", ex.getMessage());
    }

    // ---- permissions (ADR-043, Этап 22) ----

    @ExceptionHandler(TopicAccessDeniedException.class)
    public ProblemDetail handleTopicAccessDenied(TopicAccessDeniedException ex) {
        ProblemDetail pd = problem(HttpStatus.FORBIDDEN,
                "Нет доступа к теме", "forbidden-topic-access",
                "У вас нет прав на чтение этой темы");
        pd.setProperty("topicId", ex.getTopicId().toString());
        pd.setProperty("userId", ex.getUserId().toString());
        return pd;
    }

    @ExceptionHandler(TopicWriteAccessDeniedException.class)
    public ProblemDetail handleTopicWriteAccessDenied(TopicWriteAccessDeniedException ex) {
        ProblemDetail pd = problem(HttpStatus.FORBIDDEN,
                "Нет прав на изменение темы", "forbidden-topic-write",
                "У вас нет прав на запись в эту тему");
        pd.setProperty("topicId", ex.getTopicId().toString());
        pd.setProperty("userId", ex.getUserId().toString());
        return pd;
    }

    @ExceptionHandler(TopicMemberNotFoundException.class)
    public ProblemDetail handleTopicMemberNotFound(TopicMemberNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND,
                "Член темы не найден", "topic-member-not-found", ex.getMessage());
    }

    // ---- permissions для library books (ADR-043 Amendment, Этап 22.c) ----

    @ExceptionHandler(BookAccessDeniedException.class)
    public ProblemDetail handleBookAccessDenied(BookAccessDeniedException ex) {
        ProblemDetail pd = problem(HttpStatus.FORBIDDEN,
                "Нет доступа к книге", "forbidden-book-access",
                "У вас нет прав на чтение этой книги");
        pd.setProperty("bookId", ex.getBookId().toString());
        pd.setProperty("userId", ex.getUserId().toString());
        return pd;
    }

    @ExceptionHandler(BookWriteAccessDeniedException.class)
    public ProblemDetail handleBookWriteAccessDenied(BookWriteAccessDeniedException ex) {
        ProblemDetail pd = problem(HttpStatus.FORBIDDEN,
                "Нет прав на изменение книги", "forbidden-book-write",
                "У вас нет прав на запись в эту книгу");
        pd.setProperty("bookId", ex.getBookId().toString());
        pd.setProperty("userId", ex.getUserId().toString());
        return pd;
    }

    @ExceptionHandler(BookMemberNotFoundException.class)
    public ProblemDetail handleBookMemberNotFound(BookMemberNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND,
                "Член книги не найден", "book-member-not-found", ex.getMessage());
    }

    // ---- permissions для Q&A author/admin guards (ADR-043 Amendment) ----

    @ExceptionHandler(AnswerWriteAccessDeniedException.class)
    public ProblemDetail handleAnswerWriteDenied(AnswerWriteAccessDeniedException ex) {
        ProblemDetail pd = problem(HttpStatus.FORBIDDEN,
                "Нет прав на изменение ответа", "forbidden-answer-write",
                "Только автор ответа или администратор может изменять/удалять его");
        pd.setProperty("answerId", ex.getAnswerId().toString());
        pd.setProperty("userId", ex.getUserId().toString());
        return pd;
    }

    @ExceptionHandler(QuestionWriteAccessDeniedException.class)
    public ProblemDetail handleQuestionWriteDenied(QuestionWriteAccessDeniedException ex) {
        ProblemDetail pd = problem(HttpStatus.FORBIDDEN,
                "Нет прав на изменение вопроса", "forbidden-question-write",
                "Только автор вопроса или администратор может изменять/удалять его");
        pd.setProperty("questionId", ex.getQuestionId().toString());
        pd.setProperty("userId", ex.getUserId().toString());
        return pd;
    }

    @ExceptionHandler(AdminOnlyException.class)
    public ProblemDetail handleAdminOnly(AdminOnlyException ex) {
        ProblemDetail pd = problem(HttpStatus.FORBIDDEN,
                "Только администратор", "forbidden-admin-only",
                "Этот endpoint доступен только пользователям с ролью ADMIN");
        pd.setProperty("userId", ex.getUserId().toString());
        return pd;
    }

    @ExceptionHandler(InsufficientRoleException.class)
    public ProblemDetail handleInsufficientRole(InsufficientRoleException ex) {
        ProblemDetail pd = problem(HttpStatus.FORBIDDEN,
                "Недостаточно прав", "forbidden-insufficient-role",
                "Текущая роль " + ex.getCurrentRole()
                        + " не позволяет выполнить операцию, требуется "
                        + ex.getRequiredRole());
        pd.setProperty("userId", ex.getUserId().toString());
        pd.setProperty("currentRole", ex.getCurrentRole());
        pd.setProperty("requiredRole", ex.getRequiredRole());
        return pd;
    }

    @ExceptionHandler(DeletedTopicAuditAccessDeniedException.class)
    public ProblemDetail handleDeletedTopicAuditDenied(DeletedTopicAuditAccessDeniedException ex) {
        ProblemDetail pd = problem(HttpStatus.FORBIDDEN,
                "Нет прав на audit удалённой темы", "forbidden-deleted-topic-audit",
                "Audit удалённой темы доступен только администратору "
                        + "(compliance forensics). Бывший владелец не видит историю.");
        pd.setProperty("topicId", ex.getTopicId().toString());
        pd.setProperty("userId", ex.getUserId().toString());
        return pd;
    }

    @ExceptionHandler(DeletedBookAuditAccessDeniedException.class)
    public ProblemDetail handleDeletedBookAuditDenied(DeletedBookAuditAccessDeniedException ex) {
        ProblemDetail pd = problem(HttpStatus.FORBIDDEN,
                "Нет прав на audit удалённой книги", "forbidden-deleted-book-audit",
                "Audit удалённой книги доступен только администратору "
                        + "(compliance forensics). Бывший владелец не видит историю.");
        pd.setProperty("bookId", ex.getBookId().toString());
        pd.setProperty("userId", ex.getUserId().toString());
        return pd;
    }

    // ---- multi-grading хадисов (Этап hadith-grades) ----

    @ExceptionHandler(HadithGradeNotFoundException.class)
    public ProblemDetail handleHadithGradeNotFound(HadithGradeNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND,
                "Оценка хадиса не найдена", "hadith-grade-not-found", ex.getMessage());
    }

    // ---- Hadith Explorer (Vision 49d Section 2.6) ----

    @ExceptionHandler(ru.basnukaev.argumentmap.hadith.web.NarratorNotFoundException.class)
    public ProblemDetail handleNarratorNotFound(
            ru.basnukaev.argumentmap.hadith.web.NarratorNotFoundException ex) {
        ProblemDetail pd = problem(HttpStatus.NOT_FOUND,
                "Narrator не найден", "narrator-not-found", ex.getMessage());
        pd.setProperty("narratorId", ex.getNarratorId().toString());
        return pd;
    }

    @ExceptionHandler(InvalidHadithGradeException.class)
    public ProblemDetail handleInvalidHadithGrade(InvalidHadithGradeException ex) {
        return problem(HttpStatus.BAD_REQUEST,
                "Невалидная оценка хадиса", "invalid-hadith-grade", ex.getMessage());
    }

    @ExceptionHandler(HadithGradeDuplicateException.class)
    public ProblemDetail handleHadithGradeDuplicate(HadithGradeDuplicateException ex) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT,
                "Учёный уже оценил этот хадис", "hadith-grade-duplicate",
                ex.getMessage());
        pd.setProperty("sourceId", ex.getSourceId().toString());
        pd.setProperty("scholarId", ex.getScholarId().toString());
        return pd;
    }

    @ExceptionHandler(HadithGradeAccessDeniedException.class)
    public ProblemDetail handleHadithGradeAccessDenied(HadithGradeAccessDeniedException ex) {
        ProblemDetail pd = problem(HttpStatus.FORBIDDEN,
                "Нет прав на изменение оценки", "forbidden-hadith-grade-write",
                "Только автор оценки или администратор может изменять/удалять её");
        pd.setProperty("gradeId", ex.getGradeId().toString());
        pd.setProperty("userId", ex.getUserId().toString());
        return pd;
    }

    @ExceptionHandler(InvalidScholarAuthorityException.class)
    public ProblemDetail handleInvalidScholarAuthority(InvalidScholarAuthorityException ex) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST,
                "Неподходящий тип авторитета для оценки хадиса",
                "invalid-scholar-authority", ex.getMessage());
        pd.setProperty("authorityId", ex.getAuthorityId().toString());
        pd.setProperty("actualType", ex.getActualType());
        pd.setProperty("expectedType", "SCHOLAR");
        return pd;
    }

    @ExceptionHandler(InvalidAuthorityTypeException.class)
    public ProblemDetail handleInvalidAuthorityType(InvalidAuthorityTypeException ex) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST,
                "Невалидный тип authority", "invalid-authority-type",
                ex.getMessage());
        pd.setProperty("invalidType", ex.getInvalidType());
        return pd;
    }

    // ---- node translations (миграция 45, translator attribution) ----

    @ExceptionHandler(NodeTranslationNotFoundException.class)
    public ProblemDetail handleNodeTranslationNotFound(NodeTranslationNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND,
                "Перевод узла не найден", "node-translation-not-found", ex.getMessage());
    }

    @ExceptionHandler(NodeTranslationDuplicateException.class)
    public ProblemDetail handleNodeTranslationDuplicate(NodeTranslationDuplicateException ex) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT,
                "Перевод от того же переводчика на тот же язык уже существует",
                "node-translation-duplicate",
                ex.getMessage());
        pd.setProperty("nodeId", ex.getNodeId().toString());
        pd.setProperty("translatorName", ex.getTranslatorName());
        pd.setProperty("language", ex.getLanguage());
        return pd;
    }

    @ExceptionHandler(UnsupportedExportFormatException.class)
    public ProblemDetail handleUnsupportedExportFormat(UnsupportedExportFormatException ex) {
        ProblemDetail pd = problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "Неподдерживаемая версия формата экспорта",
                "unsupported-format-version",
                ex.getMessage());
        pd.setProperty("receivedVersion", ex.getReceivedVersion());
        pd.setProperty("supportedVersions", ex.getSupportedVersions());
        return pd;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Нарушение целостности данных: {}", ex.getMessage());
        return problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "Нарушение целостности данных",
                "data-integrity-violation",
                "Запрос нарушает ограничение БД (FK, unique или CHECK)");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST,
                "Ошибка валидации", "validation",
                "Запрос содержит невалидные поля");
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(),
                        fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "невалидное значение"))
                .toList();
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST,
                "Некорректный аргумент", "illegal-argument", ex.getMessage());
    }

    // ---- shamela ETL ----
    // Порядок важен: ShamelaNotFoundException ловится первым через
    // более конкретный handler (404), общий ShamelaImportException -
    // фолбэк для остальных ошибок уровня сервиса (500). Spring выбирает
    // самый специфичный handler по иерархии типов

    @ExceptionHandler(ShamelaNotFoundException.class)
    public ProblemDetail handleShamelaNotFound(ShamelaNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND,
                "Запись shamela не найдена", "shamela-not-found", ex.getMessage());
    }

    // shamela handlers - ERROR-level (failed operation для admin endpoint),
    // ex как 2-й параметр SLF4J = full stack trace в логах. Иначе при
    // wrapped exception (`new ShamelaImportException(msg, cause)`) причина
    // теряется: getMessage() показывает только наш wrapper-message, cause
    // не упоминается. Без stack trace отладка failed sync становится
    // догадками - что именно упало внутри download/extract/upsert pipeline

    @ExceptionHandler(ShamelaImportException.class)
    public ProblemDetail handleShamelaImport(ShamelaImportException ex) {
        log.error("shamela import error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ошибка импорта shamela", "shamela-import-error", ex.getMessage());
    }

    @ExceptionHandler(ShamelaApiException.class)
    public ProblemDetail handleShamelaApi(ShamelaApiException ex) {
        log.error("shamela API error", ex);
        return problem(HttpStatus.BAD_GATEWAY,
                "shamela API недоступна", "shamela-api-error", ex.getMessage());
    }

    @ExceptionHandler(ShamelaArchiveException.class)
    public ProblemDetail handleShamelaArchive(ShamelaArchiveException ex) {
        log.error("shamela archive error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ошибка распаковки архива shamela", "shamela-archive-error",
                ex.getMessage());
    }

    @ExceptionHandler(ShamelaReaderException.class)
    public ProblemDetail handleShamelaReader(ShamelaReaderException ex) {
        log.error("shamela reader error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ошибка чтения SQLite shamela", "shamela-reader-error",
                ex.getMessage());
    }

    // ---- file import (Этап 16) ----

    @ExceptionHandler(FileImportException.class)
    public ProblemDetail handleFileImport(FileImportException ex) {
        log.warn("file import error: {}", ex.getMessage());
        return problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "Ошибка импорта файла", "file-import-error", ex.getMessage());
    }

    @ExceptionHandler(PageImageException.class)
    public ProblemDetail handlePageImage(PageImageException ex) {
        log.warn("page image upload error: {}", ex.getMessage());
        return problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "Ошибка загрузки изображения страницы",
                "page-image-error", ex.getMessage());
    }

    // ---- AI editing (Этап 17.e, ADR-042) ----

    @ExceptionHandler(AiEditNotConfiguredException.class)
    public ProblemDetail handleAiEditNotConfigured(AiEditNotConfiguredException ex) {
        // 503 Service Unavailable - configuration issue, не bug.
        // Detail для admin: что делать (env var).
        return problem(HttpStatus.SERVICE_UNAVAILABLE,
                "AI editing не настроен",
                "ai-edit-not-configured",
                ex.getMessage());
    }

    @ExceptionHandler(AnthropicApiException.class)
    public ProblemDetail handleAnthropicApi(AnthropicApiException ex) {
        log.warn("Anthropic API error (status={}): {}",
                ex.statusCode(), ex.getMessage());
        // 502 Bad Gateway если upstream вернул не-2xx;
        // 503 если IOException / connection failed (statusCode=0)
        HttpStatus status = ex.statusCode() == 0
                ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
        ProblemDetail pd = problem(status,
                "Anthropic API недоступен",
                "anthropic-api-error",
                ex.getMessage());
        if (ex.statusCode() > 0) {
            pd.setProperty("upstreamStatus", ex.statusCode());
        }
        return pd;
    }

    @ExceptionHandler(UnsupportedMediaTypeException.class)
    public ProblemDetail handleUnsupportedMediaType(UnsupportedMediaTypeException ex) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Неподдерживаемый тип файла", "unsupported-media-type", ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("upload size exceeded: maxSize={} bytes", ex.getMaxUploadSize());
        return problem(HttpStatus.PAYLOAD_TOO_LARGE,
                "Превышен максимальный размер файла",
                "payload-too-large",
                "Размер загружаемого файла превышает лимит "
                        + ex.getMaxUploadSize() + " bytes");
    }

    private ProblemDetail problem(HttpStatus status, String title, String typeSlug, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create(ERROR_TYPE_BASE + typeSlug));
        return pd;
    }

    public record FieldError(String field, String message) {
    }
}
