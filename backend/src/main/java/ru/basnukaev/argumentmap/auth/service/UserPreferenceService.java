package ru.basnukaev.argumentmap.auth.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.auth.domain.UserPreference;
import ru.basnukaev.argumentmap.auth.repository.UserPreferenceRepository;

/**
 * Settings screen - управление user-preferences. Whitelisted keys +
 * валидация значений (enum-whitelist для строк, type для boolean).
 *
 * Whitelist живёт в коде (Map ALLOWED_VALUES) - изменение схемы prefs
 * не требует миграции (jsonb принимает любое валидное значение).
 *
 * Невалидный ключ или значение → IllegalArgumentException → 400 через
 * GlobalExceptionHandler.
 */
@Service
public class UserPreferenceService {

    /** Whitelisted ключи и допустимые значения для enum-prefs. null = boolean (true/false). */
    static final Map<String, Set<String>> ALLOWED_VALUES = Map.of(
            "locale", Set.of("ru", "ar", "en"),
            "arabicFont", Set.of("naskh", "kufi", "tahoma"),
            "textSize", Set.of("small", "medium", "large", "xl"),
            "theme", Set.of("system", "light", "dark"),
            "hideTashkeelByDefault", Set.of(),
            "transliteration", Set.of()
    );

    /** Ключи с boolean значением (whitelist enum пуст). */
    private static final Set<String> BOOLEAN_KEYS = Set.of(
            "hideTashkeelByDefault", "transliteration"
    );

    private final UserPreferenceRepository repository;
    private final ObjectMapper objectMapper;

    public UserPreferenceService(UserPreferenceRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Возвращает все preferences пользователя как Map. Если ключа нет
     * в БД - в карту не попадает (frontend применяет дефолты сам).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAll(UUID userId) {
        List<UserPreference> rows = repository.findByUserId(userId);
        Map<String, Object> out = new LinkedHashMap<>();
        for (UserPreference p : rows) {
            try {
                out.put(p.key(), objectMapper.readValue(p.value(), Object.class));
            } catch (JsonProcessingException e) {
                // повреждённый jsonb - пропускаем, дефолт на фронте отработает
            }
        }
        return out;
    }

    /**
     * Установить один ключ. Валидация + upsert. Возвращает обновлённую
     * карту всех prefs.
     */
    @Transactional
    public Map<String, Object> set(UUID userId, String key, Object value) {
        validate(key, value);
        try {
            String json = objectMapper.writeValueAsString(value);
            repository.upsert(userId, key, json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Не удалось сериализовать значение для ключа " + key);
        }
        return getAll(userId);
    }

    /**
     * Bulk update - применить map ключ→значение. Все валидируются перед
     * любой записью (атомарность - либо все ок, либо ничего; @Transactional
     * откатит уже сделанные upsert если последующий упадёт).
     */
    @Transactional
    public Map<String, Object> setAll(UUID userId, Map<String, Object> updates) {
        for (Map.Entry<String, Object> e : updates.entrySet()) {
            validate(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Object> e : updates.entrySet()) {
            try {
                String json = objectMapper.writeValueAsString(e.getValue());
                repository.upsert(userId, e.getKey(), json);
            } catch (JsonProcessingException ex) {
                throw new IllegalArgumentException(
                        "Не удалось сериализовать значение для ключа " + e.getKey());
            }
        }
        return getAll(userId);
    }

    /**
     * Удалить ключ - revert на дефолт (фронт читает getAll() и не находит
     * ключа, применяет дефолтное значение).
     */
    @Transactional
    public void delete(UUID userId, String key) {
        if (!ALLOWED_VALUES.containsKey(key)) {
            throw new IllegalArgumentException("Неизвестный ключ настройки: " + key);
        }
        repository.delete(userId, key);
    }

    private void validate(String key, Object value) {
        if (!ALLOWED_VALUES.containsKey(key)) {
            throw new IllegalArgumentException("Неизвестный ключ настройки: " + key);
        }
        if (value == null) {
            throw new IllegalArgumentException("Значение настройки '" + key + "' не может быть null");
        }
        if (BOOLEAN_KEYS.contains(key)) {
            if (!(value instanceof Boolean)) {
                throw new IllegalArgumentException(
                        "Значение '" + key + "' должно быть boolean, получено: " + value.getClass().getSimpleName());
            }
            return;
        }
        // enum-string keys
        Set<String> allowed = ALLOWED_VALUES.get(key);
        if (!(value instanceof String s)) {
            throw new IllegalArgumentException(
                    "Значение '" + key + "' должно быть строкой, получено: " + value.getClass().getSimpleName());
        }
        if (!allowed.contains(s)) {
            throw new IllegalArgumentException(
                    "Недопустимое значение для '" + key + "': '" + s + "'. Допустимые: " + allowed);
        }
    }
}
