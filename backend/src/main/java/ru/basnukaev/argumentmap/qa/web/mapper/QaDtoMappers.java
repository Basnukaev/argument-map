package ru.basnukaev.argumentmap.qa.web.mapper;

import ru.basnukaev.argumentmap.qa.domain.QuestionSource;
import ru.basnukaev.argumentmap.qa.repository.QuestionSourceRepository;
import ru.basnukaev.argumentmap.qa.web.dto.QuestionSourceResponse;
import ru.basnukaev.argumentmap.web.mapper.DtoMappers;

/**
 * Mappers для Q&amp;A модуля. Делегируют structured CitationResponse
 * core {@code DtoMappers.toCitationResponse} - reused без изменений
 * (ADR-018 platform pivot, валидация в Этапе 19.b).
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
}
