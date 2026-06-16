package ru.basnukaev.argumentmap.hadith.alminasa.etl.dto;

/**
 * Строка {@code am_staging_narrator_commentary} (narrator-commentary-12,
 * джарх/таʿдиль о рави). PK — {@code docId} = ES {@code _id} хита: в
 * {@code _source} нет природного int id, {@code _id} стабилен per-doc.
 * {@code narratorId} — ключ джойна на рави (= hd_narrators.external_id).
 * Полный {@code _source} едет в raw jsonb (forward-compat).
 */
public record AmNarratorCommentaryRow(
        String docId,
        int narratorId,
        String commenter,
        String book,
        String rawJson
) {
}
