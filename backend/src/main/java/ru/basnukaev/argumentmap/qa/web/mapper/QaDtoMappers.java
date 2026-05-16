package ru.basnukaev.argumentmap.qa.web.mapper;

import ru.basnukaev.argumentmap.qa.domain.AnswerSource;
import ru.basnukaev.argumentmap.qa.domain.QuestionSource;
import ru.basnukaev.argumentmap.qa.repository.AnswerSourceRepository;
import ru.basnukaev.argumentmap.qa.repository.QuestionSourceRepository;
import ru.basnukaev.argumentmap.qa.web.dto.AnswerSourceResponse;
import ru.basnukaev.argumentmap.qa.web.dto.QuestionSourceResponse;
import ru.basnukaev.argumentmap.web.mapper.DtoMappers;

/**
 * Mappers для Q&amp;A модуля. Делегируют structured CitationResponse
 * core {@code DtoMappers.toCitationResponse} - reused без изменений
 * (ADR-018 platform pivot, валидация в Этапах 19.b и 19.d).
 *
 * <p>Перегрузка {@code toResponse} по типу row - один класс на оба
 * citation flow (question + answer), общий {@code DtoMappers.toCitationResponse}
 * делегирование.
 */
public final class QaDtoMappers {

    private QaDtoMappers() {
    }

    public static QuestionSourceResponse toResponse(QuestionSourceRepository.QuestionSourceWithLocation row) {
        QuestionSource link = row.qs();
        return new QuestionSourceResponse(
                link.id(),
                link.questionId(),
                link.sourceId(),
                link.quote(),
                link.context(),
                link.mode(),
                DtoMappers.toCitationResponse(row.citation()),
                link.createdAt()
        );
    }

    public static AnswerSourceResponse toResponse(AnswerSourceRepository.AnswerSourceWithLocation row) {
        AnswerSource link = row.as();
        return new AnswerSourceResponse(
                link.id(),
                link.answerId(),
                link.sourceId(),
                link.quote(),
                link.context(),
                link.mode(),
                DtoMappers.toCitationResponse(row.citation()),
                link.createdAt()
        );
    }
}
