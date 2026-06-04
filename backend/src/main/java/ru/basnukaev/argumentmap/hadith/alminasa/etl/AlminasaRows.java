package ru.basnukaev.argumentmap.hadith.alminasa.etl;

import com.fasterxml.jackson.databind.JsonNode;

import ru.basnukaev.argumentmap.hadith.alminasa.api.dto.AlminasaHit;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmExplanationRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmHadithRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmNarratorRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmRulingRow;

/**
 * Статическая фабрика: ES-хит alminasa → staging-row. Горячие поля
 * вынимаются из {@code _source}, остальное едет в raw jsonb как есть
 * (forward-compat, спека §A). Падает с {@link IllegalArgumentException}
 * на структурно-битом доке — краулер тогда уходит в FAILED с понятным
 * сообщением (fail-fast: индекс — статичный снапшот, битый док = баг
 * наших ожиданий, а не «грязные данные»).
 */
public final class AlminasaRows {

    private AlminasaRows() {
    }

    public static AmHadithRow fromHadithHit(AlminasaHit hit) {
        JsonNode src = hit.source();
        String hadithId = requireText(src, "hadith_id");
        int dash = hadithId.indexOf('-');
        int bookId;
        try {
            bookId = Integer.parseInt(hadithId.substring(0, Math.max(dash, 0)));
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            throw new IllegalArgumentException(
                    "alminasa hadith_id не в формате {bookId}-{serial}: " + hadithId, e);
        }
        long serial = src.path("hadith_serial_id").asLong(-1);
        if (serial < 0) {
            throw new IllegalArgumentException(
                    "alminasa док без hadith_serial_id: " + hadithId);
        }
        return new AmHadithRow(
                hadithId,
                bookId,
                serial,
                textOrNull(src, "book_name"),
                textOrNull(src, "type"),
                textOrNull(src, "chapter"),
                textOrNull(src, "sub_chapter"),
                src.toString()
        );
    }

    public static AmNarratorRow fromNarratorHit(AlminasaHit hit) {
        long id;
        try {
            id = Long.parseLong(hit.id());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("alminasa narrator _id не numeric: " + hit.id(), e);
        }
        JsonNode src = hit.source();
        return new AmNarratorRow(
                id,
                textOrNull(src, "full_name"),
                textOrNull(src, "grade"),
                textOrNull(src, "level"),
                src.toString()
        );
    }

    public static AmExplanationRow fromExplanationHit(AlminasaHit hit) {
        JsonNode src = hit.source();
        JsonNode hadith = src.path("hadith");
        JsonNode explanation = src.path("explanation");
        return new AmExplanationRow(
                hit.id(),
                requireText(hadith, "hadith_id"),
                textOrNull(explanation, "explanation_book_name"),
                textOrNull(explanation, "explanation_book_author"),
                src.toString()
        );
    }

    public static AmRulingRow fromRulingHit(AlminasaHit hit) {
        JsonNode src = hit.source();
        JsonNode dod = src.path("ruler_dod");
        return new AmRulingRow(
                hit.id(),
                requireText(src, "hadith_id"),
                textOrNull(src, "ruler"),
                dod.isNumber() ? dod.asInt() : null,
                textOrNull(src, "narrations_type"),
                src.toString()
        );
    }

    private static String requireText(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("alminasa док без обязательного поля " + field);
        }
        return value.trim();
    }

    /** Текст поля с trim'ом (источник содержит trailing spaces), null если нет. */
    private static String textOrNull(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
