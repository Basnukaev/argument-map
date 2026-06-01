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

import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.qa.service.QuestionCitationService;
import ru.basnukaev.argumentmap.qa.web.dto.QuestionSourceResponse;
import ru.basnukaev.argumentmap.web.CurrentUser;
import ru.basnukaev.argumentmap.web.dto.CitationRequest;

/**
 * REST для positional citation в Q&amp;A (Этап 19.b, валидация ADR-018).
 *
 * <p>POST {@code /api/v1/questions/{id}/citations} - create (TEXT/PDF/REGION).
 * <p>GET  {@code /api/v1/questions/{id}/sources} - list с structured citation.
 * <p>DELETE {@code /api/v1/questions/{id}/sources/{questionSourceId}} - detach.
 *
 * <p>Symmetric с {@code NodeCitationController}/{@code NodeSourceController}.
 * URL hierarchy сохраняет `questionId` в DELETE-пути для consistency и под
 * будущую авторизацию по владельцу question (зеркало {@code NodeSourceController}).
 * Legacy freeform attach (через AddSourceModal) для Q&amp;A не реализован -
 * schema готова, добавим если появится UX-кейс.
 */
@RestController
@RequestMapping("/api/v1/questions/{questionId}")
public class QuestionCitationController {

    private final QuestionCitationService service;

    public QuestionCitationController(QuestionCitationService service) {
        this.service = service;
    }

    @PostMapping("/citations")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionSourceResponse create(@PathVariable UUID questionId,
                                         @Valid @RequestBody CitationRequest request,
                                         @CurrentUser UUID userId) {
        // ADR-043 Amendment (Q&A guards): create citation - мутация вопроса,
        // только автор вопроса или ADMIN
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        return service.createCitation(questionId, request, userId, role);
    }

    // GET без guard: Q&A - open discussion, sources вопроса видны всем
    // authenticated (зеркало AnswerController.list)
    @GetMapping("/sources")
    public List<QuestionSourceResponse> list(@PathVariable UUID questionId) {
        return service.getQuestionSourcesWithLocation(questionId);
    }

    @DeleteMapping("/sources/{questionSourceId}")
    public ResponseEntity<Void> detach(@PathVariable UUID questionId,
                                       @PathVariable UUID questionSourceId,
                                       @CurrentUser UUID userId) {
        // ADR-043 Amendment (Q&A guards) + question-scoped delete (IDOR):
        // только автор вопроса или ADMIN
        String role = SecurityContextUtils.currentRoleOrAnonymous();
        service.detachById(questionId, questionSourceId, userId, role);
        return ResponseEntity.noContent().build();
    }
}
