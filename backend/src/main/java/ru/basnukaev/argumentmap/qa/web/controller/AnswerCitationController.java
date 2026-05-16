package ru.basnukaev.argumentmap.qa.web.controller;

import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.qa.service.AnswerCitationService;
import ru.basnukaev.argumentmap.qa.web.dto.AnswerSourceResponse;
import ru.basnukaev.argumentmap.web.dto.CitationRequest;

/**
 * REST для positional citation в Q&amp;A answers (Этап 19.d, ADR-033
 * итерация 3).
 *
 * <p>POST {@code /api/v1/answers/{id}/citations} - create (TEXT/PDF/REGION).
 * <p>GET  {@code /api/v1/answers/{id}/sources} - list с structured citation.
 * <p>DELETE {@code /api/v1/answers/{id}/sources/{answerSourceId}} - detach.
 *
 * <p>Symmetric с {@code QuestionCitationController}. URL hierarchy сохраняет
 * {@code answerId} в DELETE-пути для consistency и под будущую авторизацию
 * по владельцу answer (зеркало паттерна для questions).
 *
 * <p>Legacy freeform attach для answers не реализован - schema готова,
 * добавим если появится UX-кейс.
 */
@RestController
@RequestMapping("/api/v1/answers/{answerId}")
public class AnswerCitationController {

    private final AnswerCitationService service;

    public AnswerCitationController(AnswerCitationService service) {
        this.service = service;
    }

    @PostMapping("/citations")
    @ResponseStatus(HttpStatus.CREATED)
    public AnswerSourceResponse create(@PathVariable UUID answerId,
                                       @Valid @RequestBody CitationRequest request) {
        return service.createCitation(answerId, request);
    }

    @GetMapping("/sources")
    public List<AnswerSourceResponse> list(@PathVariable UUID answerId) {
        return service.getAnswerSourcesWithLocation(answerId);
    }

    @DeleteMapping("/sources/{answerSourceId}")
    public ResponseEntity<Void> detach(@PathVariable UUID answerId,
                                       @PathVariable UUID answerSourceId) {
        service.detachById(answerSourceId);
        return ResponseEntity.noContent().build();
    }
}
