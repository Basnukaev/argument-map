package ru.basnukaev.argumentmap.qa.web.dto;

import java.time.Instant;
import java.util.UUID;

import ru.basnukaev.argumentmap.qa.domain.QuestionStatus;

public record QuestionResponse(
        UUID id,
        String title,
        String body,
        QuestionStatus status,
        UUID askedBy,
        UUID acceptedAnswerId,
        Instant createdAt,
        Instant updatedAt,
        // голосование за вопросы (community-сигнал популярности за
        // вопрос&ответ). voteScore = upvotes - downvotes (нетто, может быть
        // отрицательным). На list/detail заполнены через bulk/point-load из
        // question_votes; на mutating endpoint'ах (create/update) default 0/null.
        // userVote ∈ {-1, +1, null} - голос вызывающего user'а (null = не
        // голосовал либо anonymous)
        int voteScore,
        Integer userVote
) {
}
