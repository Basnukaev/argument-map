package ru.basnukaev.argumentmap.hadith.alminasa.service.dto;

import java.util.List;
import java.util.UUID;

/**
 * Снапшот результата dry-run маппинга одного хадиса alminasa (план 3, Task 4)
 * — собирается из персистнутых внутри транзакции строк, которая затем
 * откатывается ({@code setRollbackOnly}). Под будущий admin-endpoint (план 5):
 * превью того, что создал бы реальный импорт, без записи в БД.
 *
 * @param hadithId         id строки {@code hd_hadiths} (эфемерный — откатится)
 * @param externalId       природный ключ источника (например «146-1»)
 * @param collectionSlug   slug сборника
 * @param status           CANONICAL / VARIANT
 * @param hadithType       тип (марфу'/...)
 * @param primaryNumber    номер в сборнике (nullable при коллизии)
 * @param chapterAr        глава
 * @param matnPreview      первые ~200 символов матна
 * @param sanad            звенья цепи (position, externalId, nameAr, formula)
 * @param editionsCount    число изданий
 * @param crossrefsCount   число такхридж-связей
 * @param rulingsCount     число вердиктов
 * @param explanationsCount число шархов
 */
public record AlminasaDryRunResult(
        UUID hadithId,
        String externalId,
        String collectionSlug,
        String status,
        String hadithType,
        Integer primaryNumber,
        String chapterAr,
        String matnPreview,
        List<SanadLinkPreview> sanad,
        int editionsCount,
        int crossrefsCount,
        int rulingsCount,
        int explanationsCount
) {

    /** Одно звено цепи в превью: позиция, природный id рави, имя, формула передачи. */
    public record SanadLinkPreview(
            int position,
            String externalId,
            String nameAr,
            String formula
    ) {
    }
}
