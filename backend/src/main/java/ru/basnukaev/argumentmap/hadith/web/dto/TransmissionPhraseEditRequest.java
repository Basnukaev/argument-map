package ru.basnukaev.argumentmap.hadith.web.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Тело {@code PATCH /api/v1/hadith/sanad-narrators/transmission-phrase}
 * (курация Фаза 5.b, ADR-065 amendment): ADMIN правит формулу передачи
 * (риваят-глагол حدثنا/عن) звена иснада на позиции {@code position} хадиса
 * {@code hadithId}.
 *
 * <p>Ключ override синтетический и СТАБИЛЬНЫЙ: {@code entity_id=hadithId},
 * {@code field_name='transmission_phrase@'+position} — переживает
 * delete-recreate реимпорта (sanad_id пересоздаётся, hadith_id нет). alminasa =
 * 1 sanad на хадис, потому {@code position} однозначно адресует звено.
 *
 * @param hadithId стабильный ключ (PK хадиса), не sanad_id
 * @param position позиция звена в цепи (0 = сподвижник)
 * @param phrase   новая формула передачи (не пустая; EDIT-only — скрытие не
 *                 поддержано, пустой риваят-глагол путает)
 */
public record TransmissionPhraseEditRequest(
        @NotNull(message = "hadithId обязателен") UUID hadithId,
        @NotNull(message = "position обязателен")
        @PositiveOrZero(message = "position не может быть отрицательным") Integer position,
        @NotNull(message = "phrase обязателен")
        @Size(max = 200, message = "phrase не должен превышать 200 символов") String phrase
) {
}
