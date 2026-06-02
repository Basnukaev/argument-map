package ru.basnukaev.argumentmap.qa.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.domain.VoteStats;
import ru.basnukaev.argumentmap.exception.InvalidVoteException;
import ru.basnukaev.argumentmap.exception.QuestionNotFoundException;
import ru.basnukaev.argumentmap.qa.domain.QuestionVote;
import ru.basnukaev.argumentmap.qa.repository.QuestionRepository;
import ru.basnukaev.argumentmap.qa.repository.QuestionVoteRepository;

/**
 * Голосование за вопросы Q&amp;A (community-сигнал популярности за
 * вопрос&amp;ответ).
 *
 * <p>Контракт permission: questions это open discussion (без visibility model,
 * см. backend/CLAUDE.md «Q&amp;A guards»). Голосовать может любой
 * authenticated user - не нужен read/write access check как у тем. Достаточно
 * чтобы вопрос существовал (иначе 404) и weight был валидным (-1/+1, иначе 400).
 *
 * <p>Зеркалит {@link ru.basnukaev.argumentmap.service.TopicVoteService} но без
 * permission-проверок.
 */
@Service
public class QuestionVoteService {

    private final QuestionVoteRepository questionVoteRepository;
    private final QuestionRepository questionRepository;

    public QuestionVoteService(QuestionVoteRepository questionVoteRepository,
                               QuestionRepository questionRepository) {
        this.questionVoteRepository = questionVoteRepository;
        this.questionRepository = questionRepository;
    }

    /**
     * Записать (или обновить) голос user'а за вопрос. weight должен быть
     * -1 или +1. Идемпотентен: повторный vote с тем же weight - no-op (upsert).
     *
     * @throws QuestionNotFoundException если вопроса нет (404)
     * @throws InvalidVoteException      если weight не из {-1, +1} (400)
     */
    @Transactional
    public QuestionVote vote(UUID questionId, UUID userId, int weight) {
        if (weight != 1 && weight != -1) {
            throw new InvalidVoteException(
                    "Weight должен быть -1 или +1, получено: " + weight
            );
        }
        assertQuestionExists(questionId);

        QuestionVote vote = new QuestionVote(
                UUID.randomUUID(), questionId, userId, weight, Instant.now()
        );
        return questionVoteRepository.save(vote);
    }

    /**
     * Удалить голос user'а за вопрос. Идемпотентен: если голоса не было -
     * возвращает false, но не бросает. Это для UI clear-vote операции.
     *
     * @throws QuestionNotFoundException если вопроса нет (404)
     */
    @Transactional
    public boolean removeVote(UUID questionId, UUID userId) {
        assertQuestionExists(questionId);
        return questionVoteRepository.deleteByQuestionAndUser(questionId, userId);
    }

    /**
     * Агрегаты голосов вопроса. Резолвит вопрос (404 если нет). Без
     * permission-guard - questions это open discussion, агрегаты видны всем.
     *
     * @throws QuestionNotFoundException если вопроса нет (404)
     */
    @Transactional(readOnly = true)
    public VoteStats getStats(UUID questionId) {
        assertQuestionExists(questionId);
        return questionVoteRepository.getStatsForQuestion(questionId);
    }

    /**
     * Текущий vote user'а за вопрос: -1, +1 либо empty если не голосовал.
     */
    @Transactional(readOnly = true)
    public Optional<Integer> getUserVote(UUID questionId, UUID userId) {
        return questionVoteRepository.getUserVote(questionId, userId);
    }

    private void assertQuestionExists(UUID questionId) {
        if (questionRepository.findById(questionId).isEmpty()) {
            throw new QuestionNotFoundException(questionId);
        }
    }
}
