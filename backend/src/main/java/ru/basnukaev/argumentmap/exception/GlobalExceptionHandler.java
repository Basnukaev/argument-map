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

import ru.basnukaev.argumentmap.library.pdf.service.PdfNotAvailableException;
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

    @ExceptionHandler(MissingUserHeaderException.class)
    public ProblemDetail handleMissingUser(MissingUserHeaderException ex) {
        return problem(HttpStatus.BAD_REQUEST,
                "Отсутствует или невалидный заголовок X-User-Id",
                "missing-user-header", ex.getMessage());
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

    @ExceptionHandler(ShamelaImportException.class)
    public ProblemDetail handleShamelaImport(ShamelaImportException ex) {
        log.warn("shamela import error: {}", ex.getMessage());
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ошибка импорта shamela", "shamela-import-error", ex.getMessage());
    }

    @ExceptionHandler(ShamelaApiException.class)
    public ProblemDetail handleShamelaApi(ShamelaApiException ex) {
        log.warn("shamela API error: {}", ex.getMessage());
        return problem(HttpStatus.BAD_GATEWAY,
                "shamela API недоступна", "shamela-api-error", ex.getMessage());
    }

    @ExceptionHandler(ShamelaArchiveException.class)
    public ProblemDetail handleShamelaArchive(ShamelaArchiveException ex) {
        log.warn("shamela archive error: {}", ex.getMessage());
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ошибка распаковки архива shamela", "shamela-archive-error",
                ex.getMessage());
    }

    @ExceptionHandler(ShamelaReaderException.class)
    public ProblemDetail handleShamelaReader(ShamelaReaderException ex) {
        log.warn("shamela reader error: {}", ex.getMessage());
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ошибка чтения SQLite shamela", "shamela-reader-error",
                ex.getMessage());
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
