package ru.basnukaev.argumentmap.hadith.alminasa.web.dto;

import java.time.OffsetDateTime;
import java.util.List;

import ru.basnukaev.argumentmap.hadith.alminasa.service.AlminasaImportLauncher.State;
import ru.basnukaev.argumentmap.hadith.alminasa.service.dto.AlminasaImportSummary;

/**
 * Статус async-импорта alminasa staging→hd_* (план 5). При RUNNING — живой
 * {@code processedSoFar}; по завершении — счётчики из последней сводки
 * ({@code lastSummary}) либо {@code error} (если упал). {@code status=IDLE}
 * без сводки и ошибки — импорт ещё не запускался.
 *
 * @param status            IDLE / RUNNING
 * @param kind              NARRATORS / HADITHS / ALL (null — ещё не запускался)
 * @param bookIdFilter      сборник-фильтр для HADITHS (null — все)
 * @param startedAt         старт текущего прогона (только при RUNNING)
 * @param processedSoFar    живой счётчик обработанных доков при RUNNING
 * @param narratorsProcessed смапленных рави (из последней сводки)
 * @param narratorsFailed   упавших рави
 * @param hadithsProcessed  смапленных хадисов
 * @param hadithsFailed     упавших хадисов
 * @param crossrefsResolved резолвленных такхридж-FK
 * @param relationsResolved резолвленных narrator-relations FK
 * @param failures          примеры упавших доков (cap 20)
 * @param error             текст ошибки последнего прогона (null — ОК)
 */
public record AlminasaImportStatusResponse(
        String status,
        String kind,
        Integer bookIdFilter,
        OffsetDateTime startedAt,
        int processedSoFar,
        int narratorsProcessed,
        int narratorsFailed,
        int hadithsProcessed,
        int hadithsFailed,
        int crossrefsResolved,
        int relationsResolved,
        List<String> failures,
        String error) {

    public static AlminasaImportStatusResponse from(State state) {
        AlminasaImportSummary s = state.lastSummary();
        return new AlminasaImportStatusResponse(
                state.status().name(),
                state.kind() == null ? null : state.kind().name(),
                state.bookIdFilter(),
                state.startedAt(),
                state.processedSoFar(),
                s == null ? 0 : s.narratorsProcessed(),
                s == null ? 0 : s.narratorsFailed(),
                s == null ? 0 : s.hadithsProcessed(),
                s == null ? 0 : s.hadithsFailed(),
                s == null ? 0 : s.crossrefsResolved(),
                s == null ? 0 : s.relationsResolved(),
                s == null ? List.of() : s.failures(),
                state.lastError());
    }
}
