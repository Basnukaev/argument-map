package ru.basnukaev.argumentmap.hadith.sunnah.web.dto;

/**
 * Элемент списка хадисов, доступных в источнике дампа sunnah.com для одного
 * сборника (фазовый импорт, ADR-052). Это превью ИСХОДНИКА (до импорта) —
 * чтобы admin мог пролистать корпус прежде чем коммитить хадис в нашу БД.
 *
 * <p>{@code alreadyImported} — есть ли уже строка {@code hd_hadiths} с
 * естественным ключом (collection_id, primary_number). Считается по
 * резолвленному сборнику в hd_collections; если сборник ещё не создан —
 * для всех {@code false} (ничего не импортировано).
 *
 * <p>Сниппеты — обрезанный (чистый, после {@link
 * ru.basnukaev.argumentmap.hadith.sunnah.etl.SunnahTextCleaner}) текст
 * matn+isnad единым блоком, как его отдаёт источник; для листинга, не для
 * выверенного отображения.
 *
 * @param number номер хадиса в сборнике (varchar — sunnah допускает "1a")
 * @param textArSnippet обрезанный арабский текст (nullable)
 * @param textEnSnippet обрезанный английский текст (nullable)
 * @param alreadyImported уже есть hd_hadiths для (collection, primaryNumber)
 */
public record SunnahHadithBrowseItem(
        String number,
        String textArSnippet,
        String textEnSnippet,
        boolean alreadyImported
) {
}
