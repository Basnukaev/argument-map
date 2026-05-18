package ru.basnukaev.argumentmap.auth.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Запись настройки пользователя (Settings screen). Хранится в
 * user_preferences с UNIQUE(user_id, key). value - сериализованный JSON
 * (boolean, string, number), парсится в сервисе.
 */
public record UserPreference(
        UUID id,
        UUID userId,
        String key,
        String value,
        Instant updatedAt
) {
}
