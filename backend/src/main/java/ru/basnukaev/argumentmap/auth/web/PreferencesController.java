package ru.basnukaev.argumentmap.auth.web;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.auth.service.UserPreferenceService;
import ru.basnukaev.argumentmap.web.CurrentUser;

/**
 * REST для Settings screen. Все endpoints под текущим пользователем -
 * чтение/запись только своих preferences.
 *
 * Контракт:
 * <ul>
 *   <li>GET /api/v1/preferences - вернуть Map всех текущих prefs
 *   <li>PUT /api/v1/preferences - bulk update (body = Map)
 *   <li>PUT /api/v1/preferences/{key} - обновить один ключ (body = {"value": ...})
 *   <li>DELETE /api/v1/preferences/{key} - удалить ключ (revert на default)
 * </ul>
 *
 * Валидация (whitelist + типы) - в UserPreferenceService. Невалидный
 * ключ/значение → IllegalArgumentException → 400.
 */
@RestController
@RequestMapping("/api/v1/preferences")
public class PreferencesController {

    private final UserPreferenceService preferenceService;

    public PreferencesController(UserPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll(@CurrentUser UUID userId) {
        return ResponseEntity.ok(preferenceService.getAll(userId));
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> putAll(@CurrentUser UUID userId,
                                                     @RequestBody Map<String, Object> updates) {
        return ResponseEntity.ok(preferenceService.setAll(userId, updates));
    }

    @PutMapping("/{key}")
    public ResponseEntity<Map<String, Object>> putOne(@CurrentUser UUID userId,
                                                     @PathVariable String key,
                                                     @RequestBody SingleValueRequest body) {
        if (body == null) {
            throw new IllegalArgumentException("Тело запроса обязательно");
        }
        return ResponseEntity.ok(preferenceService.set(userId, key, body.value()));
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> delete(@CurrentUser UUID userId, @PathVariable String key) {
        preferenceService.delete(userId, key);
        return ResponseEntity.noContent().build();
    }

    /** Тело для single-key PUT - конверт {"value": ...} для гибкости (boolean / string / number). */
    public record SingleValueRequest(Object value) {
    }
}
