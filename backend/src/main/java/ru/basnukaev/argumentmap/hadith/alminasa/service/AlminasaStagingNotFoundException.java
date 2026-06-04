package ru.basnukaev.argumentmap.hadith.alminasa.service;

/**
 * Хадис с данным {@code hadith_id} отсутствует в {@code am_staging_hadith}
 * (план 5, фикс M2). Бросается из {@link AlminasaHadithMapper#dryRunHadith}
 * при попытке dry-run нестейдженного id. Маппится в **404**
 * {@code alminasa-staging-not-found} в {@code GlobalExceptionHandler}.
 *
 * <p>Отделена от {@link AlminasaMappingException} (422 — застейджен, но матн
 * пустой/битый): семантически «нет данных» vs «данные есть, но невалидны».
 */
public class AlminasaStagingNotFoundException extends RuntimeException {

    private final String hadithId;

    public AlminasaStagingNotFoundException(String hadithId) {
        super("Хадис не найден в staging: " + hadithId);
        this.hadithId = hadithId;
    }

    public String hadithId() {
        return hadithId;
    }
}
