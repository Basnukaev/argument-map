package ru.basnukaev.argumentmap.hadith.alminasa.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Один хит ES-ответа alminasa: {@code _id}, {@code _source} и {@code sort}
 * (значения сортировки для search_after-пагинации). План 2 alminasa.
 */
public record AlminasaHit(String id, JsonNode source, JsonNode sort) {
}
