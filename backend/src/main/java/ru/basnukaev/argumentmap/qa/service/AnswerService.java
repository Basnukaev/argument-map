package ru.basnukaev.argumentmap.qa.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.exception.AnswerNotFoundException;
import ru.basnukaev.argumentmap.exception.QuestionNotFoundException;
import ru.basnukaev.argumentmap.qa.domain.Answer;
import ru.basnukaev.argumentmap.qa.domain.Question;
import ru.basnukaev.argumentmap.qa.repository.AnswerRepository;
import ru.basnukaev.argumentmap.qa.repository.QuestionRepository;

/**
 * Сервисный слой ответов в Q&amp;A приложении (Этап 19.c, ADR-034).
 */
@Service
public class AnswerService {

    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;

    public AnswerService(AnswerRepository answerRepository, QuestionRepository questionRepository) {
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
    }

    @Transactional
    public Answer createAnswer(UUID questionId, String body, UUID authorId) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body обязателен и не должен быть пустым");
        }
        // Pre-check существования question - чистый 404 вместо FK violation
        questionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException(questionId));
        Instant now = Instant.now();
        Answer a = new Answer(
                UUID.randomUUID(),
                questionId,
                body.trim(),
                authorId,
                now,
                now
        );
        answerRepository.save(a);
        return a;
    }

    /**
     * Список ответов на вопрос. Сортировка - принятый ответ первым,
     * остальные по {@code created_at}. Если у вопроса нет принятого ответа,
     * сортировка по {@code created_at}.
     */
    @Transactional(readOnly = true)
    public List<Answer> getAnswersForQuestion(UUID questionId) {
        Question q = questionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException(questionId));
        return answerRepository.findByQuestionIdSortedByAccepted(questionId, q.acceptedAnswerId());
    }

    @Transactional
    public Answer updateAnswer(UUID answerId, String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body обязателен и не должен быть пустым");
        }
        // Pre-check для чистого 404
        answerRepository.findById(answerId)
                .orElseThrow(() -> new AnswerNotFoundException(answerId));
        answerRepository.update(answerId, body.trim());
        return answerRepository.findById(answerId).orElseThrow();
    }

    @Transactional
    public void deleteAnswer(UUID answerId) {
        boolean removed = answerRepository.deleteById(answerId);
        if (!removed) {
            throw new AnswerNotFoundException(answerId);
        }
    }

    /**
     * Принять ответ как accepted. Обновляет {@code questions.accepted_answer_id}
     * и переводит status в {@code ANSWERED}. Проверяет что answer принадлежит
     * указанному question - иначе {@code IllegalArgumentException} (→ 400).
     *
     * @return обновлённый Question
     */
    @Transactional
    public Question acceptAnswer(UUID questionId, UUID answerId) {
        questionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException(questionId));
        Answer a = answerRepository.findById(answerId)
                .orElseThrow(() -> new AnswerNotFoundException(answerId));
        if (!a.questionId().equals(questionId)) {
            throw new IllegalArgumentException(
                    "Ответ " + answerId + " не принадлежит вопросу " + questionId);
        }
        questionRepository.setAcceptedAnswer(questionId, answerId);
        return questionRepository.findById(questionId).orElseThrow();
    }

    /**
     * Снять принятие ответа: {@code accepted_answer_id = NULL} + status = OPEN.
     */
    @Transactional
    public Question revokeAcceptance(UUID questionId) {
        questionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException(questionId));
        questionRepository.revokeAcceptedAnswer(questionId);
        return questionRepository.findById(questionId).orElseThrow();
    }
}
