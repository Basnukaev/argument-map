package ru.basnukaev.argumentmap.hadith.alminasa.web.dto;

import java.util.List;

import ru.basnukaev.argumentmap.hadith.alminasa.service.dto.AlminasaDryRunResult;
import ru.basnukaev.argumentmap.hadith.alminasa.service.dto.AlminasaDryRunResult.SanadLinkPreview;

/**
 * Превью dry-run маппинга одного хадиса alminasa ДО записи в БД (план 5,
 * решение 3). Маппинг из {@link AlminasaDryRunResult} (собран внутри
 * откатываемой транзакции — БД не мутируется). {@code hadithId} НЕ выносим:
 * он эфемерный (откатился), фронту не нужен.
 *
 * @param externalId       природный ключ источника (например «146-1»)
 * @param collectionSlug   slug сборника
 * @param status           CANONICAL / VARIANT
 * @param hadithType       тип (марфу'/...)
 * @param primaryNumber    номер в сборнике (nullable при коллизии)
 * @param chapterAr        глава
 * @param matnPreview      первые ~200 символов матна
 * @param chain            звенья цепи (position, externalId, nameAr, formula)
 * @param editionsCount    число изданий
 * @param crossrefsCount   число такхридж-связей
 * @param rulingsCount     число вердиктов
 * @param explanationsCount число шархов
 */
public record AlminasaDryRunResponse(
        String externalId,
        String collectionSlug,
        String status,
        String hadithType,
        Integer primaryNumber,
        String chapterAr,
        String matnPreview,
        List<ChainLink> chain,
        int editionsCount,
        int crossrefsCount,
        int rulingsCount,
        int explanationsCount) {

    /** Одно звено цепи в превью: позиция, природный id рави, имя, формула передачи. */
    public record ChainLink(
            int position,
            String externalId,
            String nameAr,
            String formula) {

        static ChainLink from(SanadLinkPreview link) {
            return new ChainLink(link.position(), link.externalId(), link.nameAr(), link.formula());
        }
    }

    public static AlminasaDryRunResponse from(AlminasaDryRunResult result) {
        List<ChainLink> chain = result.sanad().stream().map(ChainLink::from).toList();
        return new AlminasaDryRunResponse(
                result.externalId(),
                result.collectionSlug(),
                result.status(),
                result.hadithType(),
                result.primaryNumber(),
                result.chapterAr(),
                result.matnPreview(),
                chain,
                result.editionsCount(),
                result.crossrefsCount(),
                result.rulingsCount(),
                result.explanationsCount());
    }
}
