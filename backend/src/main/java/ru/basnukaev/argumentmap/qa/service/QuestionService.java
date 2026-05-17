package ru.basnukaev.argumentmap.qa.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.exception.QuestionNotFoundException;
import ru.basnukaev.argumentmap.qa.domain.Question;
import ru.basnukaev.argumentmap.qa.domain.QuestionStatus;
import ru.basnukaev.argumentmap.qa.repository.QuestionRepository;

/**
 * Сервисный слой Q&amp;A приложения (Этап 19.a, ADR-032).
 */
@Service
public class QuestionService {

    private final QuestionRepository repository;

    public QuestionService(QuestionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Question createQuestion(String title, String body, UUID askedBy) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title обязателен и не должен быть пустым");
        }
        Instant now = Instant.now();
        Question q = new Question(
                UUID.randomUUID(),
                title.trim(),
                body == null || body.isBlank() ? null : body.trim(),
                QuestionStatus.OPEN,
                askedBy,
                null,
                now,
                now
        );
        repository.save(q);
        return q;
    }

    @Transactional(readOnly = true)
    public List<Question> listQuestions(QuestionStatus status, String query) {
        return repository.findAll(status, query);
    }

    @Transactional(readOnly = true)
    public List<Question> listQuestionsPage(QuestionStatus status, String query,
                                            int limit, int offset) {
        return repository.findPage(status, query, limit, offset);
    }

    @Transactional(readOnly = true)
    public long countQuestions(QuestionStatus status, String query) {
        return repository.countFiltered(status, query);
    }

    @Transactional(readOnly = true)
    public Question getQuestion(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new QuestionNotFoundException(id));
    }

    /**
     * Partial update - title/body/status. null значения = no change в
     * соответствующем поле. Если ничего не передано в request, ничего
     * не меняется кроме {@code updated_at = now()}.
     *
     * <p><b>Семантика пустых строк</b> (code review Сессии 35, Important #2):
     * <ul>
     *   <li>{@code title}: {@code null} = no change. Whitespace-only или
     *       пустая строка {@code ""} → {@code IllegalArgumentException}
     *       (400). Title в Create имеет {@code @NotBlank}, инвариант
     *       сохраняется в PATCH</li>
     *   <li>{@code body}: {@code null} = no change. Пустая или whitespace
     *       строка = clear (body становится {@code null}). Body optional
     *       в Create, поэтому "сбрасывать в null" - валидный сценарий</li>
     * </ul>
     */
    @Transactional
    public Question updateQuestion(UUID id, String title, String body, QuestionStatus status) {
        // Pre-check существования - даёт чистый 404 вместо silent no-op
        repository.findById(id).orElseThrow(() -> new QuestionNotFoundException(id));
        String normalizedTitle;
        if (title == null) {
            normalizedTitle = null;
        } else if (title.isBlank()) {
            throw new IllegalArgumentException(
                    "title не может быть пустым - используйте null для no-change");
        } else {
            normalizedTitle = title.trim();
        }
        String normalizedBody;
        if (body == null) {
            normalizedBody = null;
        } else if (body.isBlank()) {
            // Empty/blank body = clear (body nullable в schema)
            normalizedBody = "";
        } else {
            normalizedBody = body.trim();
        }
        repository.update(id, normalizedTitle, normalizedBody, status);
        return repository.findById(id).orElseThrow();
    }

    @Transactional
    public void deleteQuestion(UUID id) {
        boolean removed = repository.deleteById(id);
        if (!removed) {
            throw new QuestionNotFoundException(id);
        }
    }
}
