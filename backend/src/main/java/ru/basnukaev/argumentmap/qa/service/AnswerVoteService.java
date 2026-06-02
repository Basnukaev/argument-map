package ru.basnukaev.argumentmap.qa.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.domain.VoteStats;
import ru.basnukaev.argumentmap.exception.AnswerNotFoundException;
import ru.basnukaev.argumentmap.exception.InvalidVoteException;
import ru.basnukaev.argumentmap.qa.domain.AnswerVote;
import ru.basnukaev.argumentmap.qa.repository.AnswerRepository;
import ru.basnukaev.argumentmap.qa.repository.AnswerVoteRepository;

/**
 * Голосование за отдельные ответы Q&amp;A (community-сигнал качества
 * конкретного ответа).
 *
 * <p>Контракт permission: answers это open discussion (без visibility model,
 * см. backend/CLAUDE.md «Q&amp;A guards»). Голосовать может любой
 * authenticated user - не нужен read/write access check как у тем. Достаточно
 * чтобы ответ существовал (иначе 404) и weight был валидным (-1/+1, иначе 400).
 *
 * <p>Зеркалит {@link QuestionVoteService} но на уровне ответов.
 */
@Service
public class AnswerVoteService {

    private final AnswerVoteRepository answerVoteRepository;
    private final AnswerRepository answerRepository;

    public AnswerVoteService(AnswerVoteRepository answerVoteRepository,
                             AnswerRepository answerRepository) {
        this.answerVoteRepository = answerVoteRepository;
        this.answerRepository = answerRepository;
    }

    /**
     * Записать (или обновить) голос user'а за ответ. weight должен быть
     * -1 или +1. Идемпотентен: повторный vote с тем же weight - no-op (upsert).
     *
     * @throws AnswerNotFoundException если ответа нет (404)
     * @throws InvalidVoteException    если weight не из {-1, +1} (400)
     */
    @Transactional
    public AnswerVote vote(UUID answerId, UUID userId, int weight) {
        if (weight != 1 && weight != -1) {
            throw new InvalidVoteException(
                    "Weight должен быть -1 или +1, получено: " + weight
            );
        }
        assertAnswerExists(answerId);

        AnswerVote vote = new AnswerVote(
                UUID.randomUUID(), answerId, userId, weight, Instant.now()
        );
        return answerVoteRepository.save(vote);
    }

    /**
     * Удалить голос user'а за ответ. Идемпотентен: если голоса не было -
     * возвращает false, но не бросает. Это для UI clear-vote операции.
     *
     * @throws AnswerNotFoundException если ответа нет (404)
     */
    @Transactional
    public boolean removeVote(UUID answerId, UUID userId) {
        assertAnswerExists(answerId);
        return answerVoteRepository.deleteByAnswerAndUser(answerId, userId);
    }

    /**
     * Агрегаты голосов ответа. Резолвит ответ (404 если нет). Без
     * permission-guard - answers это open discussion, агрегаты видны всем.
     *
     * @throws AnswerNotFoundException если ответа нет (404)
     */
    @Transactional(readOnly = true)
    public VoteStats getStats(UUID answerId) {
        assertAnswerExists(answerId);
        return answerVoteRepository.getStatsForAnswer(answerId);
    }

    /**
     * Текущий vote user'а за ответ: -1, +1 либо empty если не голосовал.
     */
    @Transactional(readOnly = true)
    public Optional<Integer> getUserVote(UUID answerId, UUID userId) {
        return answerVoteRepository.getUserVote(answerId, userId);
    }

    private void assertAnswerExists(UUID answerId) {
        if (answerRepository.findById(answerId).isEmpty()) {
            throw new AnswerNotFoundException(answerId);
        }
    }
}
