package ru.basnukaev.argumentmap.hadith.alminasa.api.dto;

import java.util.List;

/** Страница ES-ответа: точный total ({@code track_total_hits:true}) + хиты. */
public record AlminasaPage(long totalHits, List<AlminasaHit> hits) {
}
