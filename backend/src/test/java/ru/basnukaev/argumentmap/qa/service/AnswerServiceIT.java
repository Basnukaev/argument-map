package ru.basnukaev.argumentmap.qa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.exception.AnswerNotFoundException;
import ru.basnukaev.argumentmap.exception.QuestionClosedException;
import ru.basnukaev.argumentmap.exception.QuestionNotFoundException;
import ru.basnukaev.argumentmap.exception.QuestionWriteAccessDeniedException;
import ru.basnukaev.argumentmap.qa.domain.Answer;
import ru.basnukaev.argumentmap.qa.domain.Question;
import ru.basnukaev.argumentmap.qa.domain.QuestionStatus;
import ru.basnukaev.argumentmap.qa.repository.QuestionRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AnswerServiceIT {

    @Autowired private AnswerService answerService;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID questionId;

    @BeforeEach
    void setUp() {
        userId = createUser();
        questionId = createQuestion("Каков статус Х?");
    }

    // ---------- create ----------

    @Test
    void createAnswer_success_returnsAnswerWithFields() {
        Answer created = answerService.createAnswer(questionId, "Ответ на вопрос", userId);

        assertThat(created.id()).isNotNull();
        assertThat(created.questionId()).isEqualTo(questionId);
        assertThat(created.body()).isEqualTo("Ответ на вопрос");
        assertThat(created.authorId()).isEqualTo(userId);
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.updatedAt()).isNotNull();
    }

    @Test
    void createAnswer_questionNotFound_throws404() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> answerService.createAnswer(missing, "тело", userId))
                .isInstanceOf(QuestionNotFoundException.class);
    }

    @Test
    void createAnswer_blankBody_throws400() {
        assertThatThrownBy(() -> answerService.createAnswer(questionId, "   ", userId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- list ----------

    @Test
    void getAnswersForQuestion_empty_returnsEmptyList() {
        List<Answer> answers = answerService.getAnswersForQuestion(questionId);
        assertThat(answers).isEmpty();
    }

    @Test
    void getAnswersForQuestion_questionNotFound_throws404() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> answerService.getAnswersForQuestion(missing))
                .isInstanceOf(QuestionNotFoundException.class);
    }

    @Test
    void getAnswersForQuestion_noAcceptedAnswer_sortedByCreatedAt() {
        Answer first = answerService.createAnswer(questionId, "первый", userId);
        sleepMs(10);
        Answer second = answerService.createAnswer(questionId, "второй", userId);
        sleepMs(10);
        Answer third = answerService.createAnswer(questionId, "третий", userId);

        List<Answer> answers = answerService.getAnswersForQuestion(questionId);

        assertThat(answers).extracting(Answer::id)
                .containsExactly(first.id(), second.id(), third.id());
    }

    @Test
    void getAnswersForQuestion_withAcceptedAnswer_acceptedFirst() {
        Answer first = answerService.createAnswer(questionId, "первый", userId);
        sleepMs(10);
        Answer second = answerService.createAnswer(questionId, "второй", userId);
        sleepMs(10);
        Answer third = answerService.createAnswer(questionId, "третий", userId);

        // принимаем средний - он должен встать первым
        answerService.acceptAnswer(questionId, second.id());

        List<Answer> answers = answerService.getAnswersForQuestion(questionId);

        assertThat(answers).extracting(Answer::id)
                .containsExactly(second.id(), first.id(), third.id());
    }

    // ---------- update ----------

    @Test
    void updateAnswer_success_changesBody() {
        Answer created = answerService.createAnswer(questionId, "original", userId);

        Answer updated = answerService.updateAnswer(created.id(), "edited");

        assertThat(updated.body()).isEqualTo("edited");
        assertThat(updated.id()).isEqualTo(created.id());
    }

    @Test
    void updateAnswer_notFound_throws404() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> answerService.updateAnswer(missing, "new body"))
                .isInstanceOf(AnswerNotFoundException.class);
    }

    @Test
    void updateAnswer_blankBody_throws400() {
        Answer created = answerService.createAnswer(questionId, "original", userId);
        assertThatThrownBy(() -> answerService.updateAnswer(created.id(), "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- delete ----------

    @Test
    void deleteAnswer_success_removesRow() {
        Answer created = answerService.createAnswer(questionId, "to delete", userId);

        answerService.deleteAnswer(created.id());

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM answers WHERE id = ?", Long.class, created.id());
        assertThat(count).isEqualTo(0L);
    }

    @Test
    void deleteAnswer_notFound_throws404() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> answerService.deleteAnswer(missing))
                .isInstanceOf(AnswerNotFoundException.class);
    }

    // ---------- accept ----------

    @Test
    void acceptAnswer_success_updatesQuestionAndStatus() {
        Answer a = answerService.createAnswer(questionId, "правильный ответ", userId);

        Question updated = answerService.acceptAnswer(questionId, a.id());

        assertThat(updated.acceptedAnswerId()).isEqualTo(a.id());
        assertThat(updated.status()).isEqualTo(QuestionStatus.ANSWERED);
    }

    @Test
    void acceptAnswer_answerNotInQuestion_throws400() {
        // Ответ принадлежит другому вопросу
        UUID otherQuestionId = createQuestion("другой вопрос");
        Answer foreignAnswer = answerService.createAnswer(otherQuestionId, "чужой", userId);

        assertThatThrownBy(() -> answerService.acceptAnswer(questionId, foreignAnswer.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не принадлежит");
    }

    @Test
    void acceptAnswer_questionNotFound_throws404() {
        UUID missing = UUID.randomUUID();
        Answer a = answerService.createAnswer(questionId, "тело", userId);
        assertThatThrownBy(() -> answerService.acceptAnswer(missing, a.id()))
                .isInstanceOf(QuestionNotFoundException.class);
    }

    @Test
    void acceptAnswer_answerNotFound_throws404() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> answerService.acceptAnswer(questionId, missing))
                .isInstanceOf(AnswerNotFoundException.class);
    }

    @Test
    void acceptAnswer_onClosedQuestion_throwsAndDoesNotReopen() {
        // Баг #4 Tier-3: CLOSED - терминальное модераторское состояние.
        // Принятие ответа НЕ должно молча вернуть вопрос в ANSWERED.
        Answer a = answerService.createAnswer(questionId, "ответ", userId);
        jdbcTemplate.update("UPDATE questions SET status = 'CLOSED' WHERE id = ?", questionId);

        assertThatThrownBy(() -> answerService.acceptAnswer(questionId, a.id()))
                .isInstanceOf(QuestionClosedException.class);

        // вопрос остался CLOSED, accepted_answer_id не выставлен
        Question q = questionRepository.findById(questionId).orElseThrow();
        assertThat(q.status()).isEqualTo(QuestionStatus.CLOSED);
        assertThat(q.acceptedAnswerId()).isNull();
    }

    @Test
    void acceptAnswer_onClosedQuestion_viaRoleOverload_alsoBlocked() {
        // guard срабатывает и через role-aware overload (даже у автора/ADMIN)
        Answer a = answerService.createAnswer(questionId, "ответ", userId);
        jdbcTemplate.update("UPDATE questions SET status = 'CLOSED' WHERE id = ?", questionId);

        assertThatThrownBy(() ->
                answerService.acceptAnswer(questionId, a.id(), userId, UserRole.ADMIN))
                .isInstanceOf(QuestionClosedException.class);
    }

    @Test
    void acceptAnswer_byNonAuthor_throws403() {
        // ADR-043 Amendment: accept мутирует вопрос - только автор вопроса
        // или ADMIN. questionId создан userId; другой user → 403.
        Answer a = answerService.createAnswer(questionId, "ответ", userId);
        UUID otherUser = createUser();

        assertThatThrownBy(() ->
                answerService.acceptAnswer(questionId, a.id(), otherUser, UserRole.STUDENT))
                .isInstanceOf(QuestionWriteAccessDeniedException.class);
    }

    @Test
    void acceptAnswer_byAuthor_succeeds() {
        Answer a = answerService.createAnswer(questionId, "ответ", userId);

        Question updated = answerService.acceptAnswer(questionId, a.id(), userId, UserRole.STUDENT);

        assertThat(updated.acceptedAnswerId()).isEqualTo(a.id());
    }

    @Test
    void acceptAnswer_byAdmin_succeeds() {
        // ADMIN bypass - может принять ответ на чужой вопрос
        Answer a = answerService.createAnswer(questionId, "ответ", userId);
        UUID admin = createUser();

        Question updated = answerService.acceptAnswer(questionId, a.id(), admin, UserRole.ADMIN);

        assertThat(updated.acceptedAnswerId()).isEqualTo(a.id());
    }

    @Test
    void revokeAcceptance_byNonAuthor_throws403() {
        Answer a = answerService.createAnswer(questionId, "ответ", userId);
        answerService.acceptAnswer(questionId, a.id());
        UUID otherUser = createUser();

        assertThatThrownBy(() ->
                answerService.revokeAcceptance(questionId, otherUser, UserRole.STUDENT))
                .isInstanceOf(QuestionWriteAccessDeniedException.class);
    }

    // ---------- revoke ----------

    @Test
    void revokeAcceptance_success_clearsFieldAndStatusBackToOpen() {
        Answer a = answerService.createAnswer(questionId, "ответ", userId);
        answerService.acceptAnswer(questionId, a.id());

        Question reverted = answerService.revokeAcceptance(questionId);

        assertThat(reverted.acceptedAnswerId()).isNull();
        assertThat(reverted.status()).isEqualTo(QuestionStatus.OPEN);
    }

    @Test
    void deleteAnswer_acceptedAnswer_resetsQuestionStatusToOpen() {
        // Удаление принятого ответа: FK ON DELETE SET NULL обнулит
        // accepted_answer_id, но status застрял бы в ANSWERED без фикса.
        // deleteAnswer должен revoke acceptance (status → OPEN).
        Answer a = answerService.createAnswer(questionId, "принятый", userId);
        answerService.acceptAnswer(questionId, a.id());

        answerService.deleteAnswer(a.id(), userId, UserRole.STUDENT);

        Question q = questionRepository.findById(questionId).orElseThrow();
        assertThat(q.acceptedAnswerId()).isNull();
        assertThat(q.status()).isEqualTo(QuestionStatus.OPEN);
    }

    @Test
    void deleteAnswer_nonAcceptedAnswer_leavesQuestionStatusUnchanged() {
        // Удаление НЕ принятого ответа не должно трогать lifecycle вопроса.
        Answer accepted = answerService.createAnswer(questionId, "принятый", userId);
        Answer other = answerService.createAnswer(questionId, "другой", userId);
        answerService.acceptAnswer(questionId, accepted.id());

        answerService.deleteAnswer(other.id(), userId, UserRole.STUDENT);

        Question q = questionRepository.findById(questionId).orElseThrow();
        assertThat(q.acceptedAnswerId()).isEqualTo(accepted.id());
        assertThat(q.status()).isEqualTo(QuestionStatus.ANSWERED);
    }

    // ---------- cascade / ON DELETE ----------

    @Test
    void cascadeDelete_questionDeleted_answersGone() {
        answerService.createAnswer(questionId, "a1", userId);
        answerService.createAnswer(questionId, "a2", userId);

        Long before = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM answers WHERE question_id = ?", Long.class, questionId);
        assertThat(before).isEqualTo(2L);

        jdbcTemplate.update("DELETE FROM questions WHERE id = ?", questionId);

        Long after = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM answers WHERE question_id = ?", Long.class, questionId);
        assertThat(after).isEqualTo(0L);
    }

    @Test
    void onDeleteSetNull_acceptedAnswerDeleted_fkBecomesNull_statusUnchanged() {
        // Создаём ответ, принимаем его (status = ANSWERED), удаляем напрямую SQL.
        // ON DELETE SET NULL должно убрать FK, но status остаётся ANSWERED
        // (это business decision сервиса, не БД-каскада)
        Answer a = answerService.createAnswer(questionId, "будет удалён", userId);
        answerService.acceptAnswer(questionId, a.id());

        jdbcTemplate.update("DELETE FROM answers WHERE id = ?", a.id());

        Question after = questionRepository.findById(questionId).orElseThrow();
        assertThat(after.acceptedAnswerId()).isNull();
        // status НЕ возвращается в OPEN автоматически - schema-level cascade
        // не знает про business meaning. Это интенциональный design choice
        assertThat(after.status()).isEqualTo(QuestionStatus.ANSWERED);
    }

    @Test
    void bulkInsert_thenList_returnsAllInOrder() {
        // 5 ответов в порядке создания - все должны вернуться, ordering by created_at
        Answer a1 = answerService.createAnswer(questionId, "1", userId);
        sleepMs(5);
        Answer a2 = answerService.createAnswer(questionId, "2", userId);
        sleepMs(5);
        Answer a3 = answerService.createAnswer(questionId, "3", userId);
        sleepMs(5);
        Answer a4 = answerService.createAnswer(questionId, "4", userId);
        sleepMs(5);
        Answer a5 = answerService.createAnswer(questionId, "5", userId);

        List<Answer> answers = answerService.getAnswersForQuestion(questionId);

        assertThat(answers).hasSize(5);
        assertThat(answers).extracting(Answer::id)
                .containsExactly(a1.id(), a2.id(), a3.id(), a4.id(), a5.id());
    }

    // ---------- helpers ----------

    private UUID createUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                id, "u-" + id, id + "@e.com");
        return id;
    }

    private UUID createQuestion(String title) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO questions (id, title, status, asked_by) VALUES (?, ?, 'OPEN', ?)",
                id, title, userId);
        return id;
    }

    /** Минимальная пауза - чтобы created_at отличались между ответами. */
    private static void sleepMs(long ms) {
        try {
            Thread.sleep(Duration.ofMillis(ms));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
