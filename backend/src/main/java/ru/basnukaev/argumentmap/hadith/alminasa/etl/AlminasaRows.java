package ru.basnukaev.argumentmap.hadith.alminasa.etl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.basnukaev.argumentmap.hadith.alminasa.api.dto.AlminasaHit;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmAmbiguousRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmCommentaryRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmExplanationRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmHadithRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmNarratorCommentaryRow;
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

    /**
     * Комментарий-иляль (علل): hit._source.commentary → {@link AmCommentaryRow}.
     * PK = commentary.id; raw = вложенный {@code commentary}-узел (маппер читает
     * из него commentary_text / full_text / full_text_html напрямую). narrations
     * — массив hadith_id-строк (ключ джойна на хадис).
     */
    public static AmCommentaryRow fromCommentaryHit(AlminasaHit hit) {
        JsonNode commentary = hit.source().path("commentary");
        JsonNode idNode = commentary.path("id");
        if (!idNode.canConvertToInt()) {
            throw new IllegalArgumentException(
                    "alminasa commentary без numeric id: " + commentary.path("id"));
        }
        JsonNode narrations = commentary.path("narrations");
        if (!narrations.isArray()) {
            throw new IllegalArgumentException(
                    "alminasa commentary без массива narrations: id=" + idNode.asInt());
        }
        return new AmCommentaryRow(
                idNode.asInt(),
                textOrNull(commentary, "book_name"),
                textOrNull(commentary, "author_name"),
                narrations.toString(),
                commentary.toString()
        );
    }

    /**
     * Словарная статья гариб (غريب): hit._source → {@link AmAmbiguousRow}.
     * PK = id; raw = полный _source (длинный {@code explanation} внутри).
     */
    public static AmAmbiguousRow fromAmbiguousHit(AlminasaHit hit) {
        JsonNode src = hit.source();
        JsonNode idNode = src.path("id");
        if (!idNode.canConvertToInt()) {
            throw new IllegalArgumentException(
                    "alminasa ambiguous без numeric id: " + src.path("id"));
        }
        return new AmAmbiguousRow(
                idNode.asInt(),
                textOrNull(src, "book_name"),
                textOrNull(src, "author"),
                src.toString()
        );
    }

    /**
     * Цитата джарх/таʿдиль о рави (narrator-commentary-12): hit → {@link
     * AmNarratorCommentaryRow}. PK doc_id = ES {@code _id} (в {@code _source}
     * нет природного int id). narrator_id (ключ джойна на рави) — из
     * {@code _source.id} (string, = hd_narrators.external_id). commenter/book —
     * горячие колонки; полный {@code _source} едет в raw jsonb.
     */
    public static AmNarratorCommentaryRow fromNarratorCommentaryHit(AlminasaHit hit) {
        JsonNode src = hit.source();
        JsonNode idNode = src.path("id");
        int narratorId;
        try {
            // id в _source — строка ("4396"); приводим к int (ключ джойна на рави)
            narratorId = Integer.parseInt(idNode.asText().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "alminasa narrator-commentary без numeric _source.id: " + idNode, e);
        }
        return new AmNarratorCommentaryRow(
                hit.id(),
                narratorId,
                textOrNull(src, "commenter"),
                textOrNull(src, "book"),
                rawWithCommenterDod(hit)
        );
    }

    /**
     * raw для narrator-commentary = {@code _source}, но с гарантированным
     * {@code commenter_dod} (год смерти критика): в live он в {@code _source},
     * в тест-фикстуре — только в {@code sort[0]} (решение 4 плана). Если в
     * {@code _source} нет, а в {@code sort[0]} есть число — инжектим, чтобы
     * маппер всегда читал из raw без доступа к {@code sort}.
     */
    private static String rawWithCommenterDod(AlminasaHit hit) {
        JsonNode src = hit.source();
        if (!src.path("commenter_dod").isMissingNode() && !src.path("commenter_dod").isNull()) {
            return src.toString();
        }
        JsonNode sort0 = hit.sort() != null ? hit.sort().path(0) : null;
        if (sort0 != null && sort0.isNumber() && src.isObject()) {
            ObjectNode copy = (ObjectNode) src.deepCopy();
            copy.put("commenter_dod", sort0.asInt());
            return copy.toString();
        }
        return src.toString();
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
