package ru.basnukaev.argumentmap.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Авторитет/учёный/издательство/тахкик. Field {@code type} (миграция 47)
 * - семантическая роль ({@link AuthorityType}). До миграции 47 был flat
 * namespace, теперь жёсткая типизация через CHECK constraint в БД.
 */
public record Authority(
        UUID id,
        String name,
        String bio,
        String era,
        String madhab,
        String metadata,
        Instant createdAt,
        String fullName,
        Integer deathYearHijri,
        String type
) {
}
