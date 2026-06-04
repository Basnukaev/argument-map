package ru.basnukaev.argumentmap.hadith.alminasa.service.dto;

import java.util.List;

/**
 * Сводка прогона импорта alminasa staging→hd_* (план 3, Task 5).
 *
 * <p>Счётчики обработанных/упавших доков по двум проходам (рави, хадисы) +
 * результаты финального resolve-прохода FK (crossrefs SQL-ом,
 * narrator-relations в Java). {@code failures} — примеры упавших доков
 * (формат «вид:id: message»), CAP {@value #FAILURES_CAP}: ошибок может быть
 * много (битые raw на 82k доков), но в summary держим срез для диагностики.
 */
public record AlminasaImportSummary(
        int narratorsProcessed,
        int narratorsFailed,
        int hadithsProcessed,
        int hadithsFailed,
        int crossrefsResolved,
        int relationsResolved,
        List<String> failures) {

    /** Лимит примеров упавших доков в {@link #failures} (решение 10). */
    public static final int FAILURES_CAP = 20;
}
