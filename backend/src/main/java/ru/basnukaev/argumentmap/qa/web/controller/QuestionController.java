package ru.basnukaev.argumentmap.qa.web.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import ru.basnukaev.argumentmap.qa.domain.Question;
import ru.basnukaev.argumentmap.qa.domain.QuestionStatus;
import ru.basnukaev.argumentmap.qa.service.QuestionService;
import ru.basnukaev.argumentmap.qa.web.dto.CreateQuestionRequest;
import ru.basnukaev.argumentmap.qa.web.dto.QuestionResponse;
import ru.basnukaev.argumentmap.qa.web.dto.UpdateQuestionRequest;
import ru.basnukaev.argumentmap.web.CurrentUser;

/**
 * REST endpoint для Q&amp;A вопросов (Этап 19.a, ADR-032).
 *
 * <p>Только POST требует {@code X-User-Id} (для заполнения {@code asked_by}).
 * GET/PATCH/DELETE без auth на MVP - до появления authorization будут
 * mutating операции открыты. Спецификация в api-contract.md.
 */
@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController {

    private final QuestionService service;

    public QuestionController(QuestionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<QuestionResponse> create(
            @Valid @RequestBody CreateQuestionRequest request,
            @CurrentUser UUID currentUserId) {
        Question created = service.createQuestion(request.title(), request.body(), currentUserId);
        return ResponseEntity
                .created(URI.create("/api/v1/questions/" + created.id()))
                .body(toResponse(created));
    }

    @GetMapping
    public List<QuestionResponse> list(
            @RequestParam(name = "status", required = false) QuestionStatus status,
            @RequestParam(name = "q", required = false) String query) {
        return service.listQuestions(status, query).stream()
                .map(QuestionController::toResponse)
                .toList();
    }

    @GetMapping("/{questionId}")
    public QuestionResponse getOne(@PathVariable UUID questionId) {
        return toResponse(service.getQuestion(questionId));
    }

    @PatchMapping("/{questionId}")
    public QuestionResponse update(
            @PathVariable UUID questionId,
            @Valid @RequestBody UpdateQuestionRequest request) {
        Question updated = service.updateQuestion(
                questionId, request.title(), request.body(), request.status());
        return toResponse(updated);
    }

    @DeleteMapping("/{questionId}")
    public ResponseEntity<Void> delete(@PathVariable UUID questionId) {
        service.deleteQuestion(questionId);
        return ResponseEntity.noContent().build();
    }

    private static QuestionResponse toResponse(Question q) {
        return new QuestionResponse(
                q.id(),
                q.title(),
                q.body(),
                q.status(),
                q.askedBy(),
                q.acceptedAnswerId(),
                q.createdAt(),
                q.updatedAt()
        );
    }
}
