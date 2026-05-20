package ru.basnukaev.argumentmap.library.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.library.domain.UserBookCollection;
import ru.basnukaev.argumentmap.library.service.UserBookCollectionService;
import ru.basnukaev.argumentmap.library.web.dto.AddToCollectionRequest;
import ru.basnukaev.argumentmap.library.web.dto.CollectionEntryResponse;
import ru.basnukaev.argumentmap.web.CurrentUser;

/**
 * REST endpoints для personal book collections (Vision 49d Section 2.2).
 *
 * <p>Все endpoints scoped к {@code @CurrentUser} - user видит и
 * модифицирует только свои collections. Cross-user не возможен через
 * этот API. Admin доступ к чужим collections defer (нет use case).
 */
@RestController
@RequestMapping("/api/v1/library/collections")
public class UserBookCollectionController {

    private final UserBookCollectionService service;

    public UserBookCollectionController(UserBookCollectionService service) {
        this.service = service;
    }

    /**
     * Добавить книгу в коллекцию. Idempotent - повторный POST того же
     * (bookId, collectionName) возвращает existing entry без 409.
     */
    @PostMapping
    public ResponseEntity<CollectionEntryResponse> addToCollection(
            @Valid @RequestBody AddToCollectionRequest request,
            @CurrentUser UUID userId) {
        UserBookCollection entry = service.addToCollection(
                userId, request.bookId(), request.collectionName()
        );
        CollectionEntryResponse body = toResponse(entry);
        return ResponseEntity
                .created(URI.create("/api/v1/library/collections/" + entry.id()))
                .body(body);
    }

    /**
     * Удалить книгу из конкретной коллекции. ?name=Избранное по
     * default. Idempotent - 204 даже если книги не было.
     */
    @DeleteMapping("/{bookId}")
    public ResponseEntity<Void> removeFromCollection(
            @PathVariable UUID bookId,
            @RequestParam(required = false) String name,
            @CurrentUser UUID userId) {
        service.removeFromCollection(userId, bookId, name);
        return ResponseEntity.noContent().build();
    }

    /**
     * Список всех записей user'а во всех его коллекциях. Sorted
     * added_at DESC. Optional ?name= фильтр по конкретной коллекции.
     */
    @GetMapping
    public List<CollectionEntryResponse> listAll(
            @RequestParam(required = false) String name,
            @CurrentUser UUID userId) {
        List<UserBookCollection> entries = name == null || name.isBlank()
                ? service.listAll(userId)
                : service.listByCollection(userId, name);
        return entries.stream().map(UserBookCollectionController::toResponse).toList();
    }

    /**
     * Список уникальных collection names user'а - для side panel.
     */
    @GetMapping("/names")
    public List<String> listCollectionNames(@CurrentUser UUID userId) {
        return service.listCollectionNames(userId);
    }

    private static CollectionEntryResponse toResponse(UserBookCollection entry) {
        return new CollectionEntryResponse(
                entry.id(), entry.bookId(),
                entry.collectionName(), entry.addedAt()
        );
    }
}
