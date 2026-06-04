package ru.basnukaev.argumentmap.hadith.alminasa.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmHadithRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmNarratorRow;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmHadithStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmNarratorStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.service.dto.AlminasaImportSummary;
import ru.basnukaev.argumentmap.hadith.domain.NarratorRelation;
import ru.basnukaev.argumentmap.hadith.repository.HadithCrossrefRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRelationRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRepository;
import ru.basnukaev.argumentmap.hadith.service.ArabicTextNormalizer;

/**
 * Оркестрация импорта alminasa staging→hd_* (план 3, Task 5, решения 10-11).
 *
 * <p>Двухпроходный импорт: проход 1 — рави ({@link #importNarrators}), проход 2 —
 * хадисы со всеми сателлитами ({@link #importHadiths}); после хадисов —
 * финальный resolve-проход FK (crossrefs SQL-ом + narrator-relations в Java).
 * Каждый док маппится в собственной транзакции на бин-границе мапперов
 * ({@link AlminasaNarratorMapper}/{@link AlminasaHadithMapper}); этот
 * orchestration-бин и его keyset-циклы — БЕЗ {@code @Transactional}: цикл
 * обязан жить ВНЕ транзакции, иначе (а) self-invocation сломал бы per-док
 * границы (gotcha), (б) одна длинная транзакция на 82k доков не нужна и опасна.
 * Ошибка одного дока — лог WARN + счётчик failed + продолжаем (примеры в summary
 * cap {@value AlminasaImportSummary#FAILURES_CAP}).
 *
 * <p><b>Perf:</b> 82k хадисов × per-док транзакция — это one-shot админ-операция,
 * прогон в минуты-десятки минут приемлем. Преждевременно НЕ батчим: если живой
 * прогон покажет боль — батчинг как отдельный шаг (а пока простота важнее).
 *
 * <p><b>Re-runnable:</b> маппинг идемпотентен (upsert по external_id,
 * delete-recreate сателлитов), resolve-проход — тоже (обновляет только NULL FK).
 * Доки, упавшие в прошлый прогон (битый raw, временная ошибка), лечатся
 * повторным запуском — он до-импортирует их и до-резолвит их FK.
 *
 * <p><b>Известное ограничение (решение 11б):</b> резолв FK narrator-relations —
 * best-effort MVP. {@code top_students}/{@code top_scholars} alminasa хранят
 * КОРОТКИЕ формы имён («الزهري»), {@code full_name} рави — полные → hit-rate
 * совпадений низкий, и многие relations остаются с NULL FK (имя сохранено
 * verbatim в {@code related_name}). Настоящая риджаль-резолюция — backlog.
 */
@Service
public class AlminasaImportService {

    private static final Logger log = LoggerFactory.getLogger(AlminasaImportService.class);

    /** Размер батча keyset-страницы рави. */
    private static final int NARRATOR_BATCH = 200;
    /** Размер батча keyset-страницы хадисов. */
    private static final int HADITH_BATCH = 100;
    /** Размер батча выборки нерезолвленных relations. */
    private static final int RELATION_BATCH = 500;

    private final AmNarratorStagingDao narratorStagingDao;
    private final AmHadithStagingDao hadithStagingDao;
    private final AlminasaNarratorMapper narratorMapper;
    private final AlminasaHadithMapper hadithMapper;
    private final HadithCrossrefRepository crossrefRepository;
    private final NarratorRepository narratorRepository;
    private final NarratorRelationRepository relationRepository;

    public AlminasaImportService(AmNarratorStagingDao narratorStagingDao,
                                 AmHadithStagingDao hadithStagingDao,
                                 AlminasaNarratorMapper narratorMapper,
                                 AlminasaHadithMapper hadithMapper,
                                 HadithCrossrefRepository crossrefRepository,
                                 NarratorRepository narratorRepository,
                                 NarratorRelationRepository relationRepository) {
        this.narratorStagingDao = narratorStagingDao;
        this.hadithStagingDao = hadithStagingDao;
        this.narratorMapper = narratorMapper;
        this.hadithMapper = hadithMapper;
        this.crossrefRepository = crossrefRepository;
        this.narratorRepository = narratorRepository;
        this.relationRepository = relationRepository;
    }

    /**
     * Полный импорт: рави → хадисы (все сборники) → resolve-проход FK.
     *
     * @return объединённая сводка обоих проходов
     */
    public AlminasaImportSummary importAll() {
        AlminasaImportSummary narrators = importNarrators();
        AlminasaImportSummary hadiths = importHadiths(null);
        return merge(narrators, hadiths);
    }

    /**
     * Проход 1: keyset-цикл по {@code am_staging_narrator} → {@link
     * AlminasaNarratorMapper#mapNarrator} в per-док транзакции. Ошибка одного
     * рави не валит прогон.
     */
    public AlminasaImportSummary importNarrators() {
        int processed = 0;
        int failed = 0;
        List<String> failures = new ArrayList<>();

        Long afterId = null;
        while (true) {
            List<AmNarratorRow> page = narratorStagingDao.findPage(afterId, NARRATOR_BATCH);
            if (page.isEmpty()) {
                break;
            }
            for (AmNarratorRow row : page) {
                try {
                    narratorMapper.mapNarrator(row);
                    processed++;
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("alminasa: ошибка маппинга рави narrator_id={}: {}",
                            row.narratorId(), e.getMessage());
                    addFailure(failures, "narrator", String.valueOf(row.narratorId()), e);
                }
            }
            afterId = page.get(page.size() - 1).narratorId();
        }
        return new AlminasaImportSummary(processed, failed, 0, 0, 0, 0, failures);
    }

    /**
     * Проход 2: keyset-цикл по {@code am_staging_hadith}
     * ({@code ORDER BY book_id, hadith_serial_id}) → {@link
     * AlminasaHadithMapper#mapHadith} в per-док транзакции; после цикла —
     * resolve-проход FK ({@link #resolveCrossrefs} + {@link
     * #resolveNarratorRelations}).
     *
     * @param bookIdFilter если не {@code null} — импортировать только хадисы
     *                     этого сборника (фильтр в Java поверх keyset-обхода);
     *                     {@code null} — все сборники
     */
    public AlminasaImportSummary importHadiths(Integer bookIdFilter) {
        int processed = 0;
        int failed = 0;
        List<String> failures = new ArrayList<>();

        Integer afterBookId = null;
        Long afterSerial = null;
        while (true) {
            List<AmHadithRow> page = hadithStagingDao.findPage(afterBookId, afterSerial, HADITH_BATCH);
            if (page.isEmpty()) {
                break;
            }
            for (AmHadithRow row : page) {
                if (bookIdFilter != null && row.bookId() != bookIdFilter) {
                    continue; // чужой сборник — пропускаем (фильтр поверх keyset-обхода)
                }
                try {
                    hadithMapper.mapHadith(row);
                    processed++;
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("alminasa: ошибка маппинга хадиса hadith_id={}: {}",
                            row.hadithId(), e.getMessage());
                    addFailure(failures, "hadith", row.hadithId(), e);
                }
            }
            AmHadithRow last = page.get(page.size() - 1);
            afterBookId = last.bookId();
            afterSerial = last.hadithSerialId();
        }

        int crossrefsResolved = resolveCrossrefs();
        int relationsResolved = resolveNarratorRelations();

        return new AlminasaImportSummary(
                0, 0, processed, failed, crossrefsResolved, relationsResolved, failures);
    }

    /**
     * Resolve crossref-FK одним SQL UPDATE по уже импортированным хадисам
     * (план 3, решение 11а). Re-runnable — обновляет только NULL FK.
     */
    private int resolveCrossrefs() {
        return crossrefRepository.resolveRelatedHadithIds();
    }

    /**
     * Resolve narrator-relations FK в Java (план 3, решение 11б): загрузить
     * Map {@code normalized_name → ids} всех alminasa-рави в память (~11k —
     * дёшево), пройти все relations с NULL FK, резолвить ТОЛЬКО при ровно одном
     * кандидате (гомонимы → NULL). См. javadoc класса про known limitation
     * (короткие формы имён → низкий hit-rate).
     *
     * <p>Пагинация: {@code offset} продвигаем ТОЛЬКО на пропущенные
     * (нерезолвленные) строки. Резолвленные выпадают из выборки
     * {@code findUnresolved} на следующей итерации — если бы offset рос на ВСЕ
     * строки батча, мы перескакивали бы ещё-не-просмотренные нерезолвленные.
     * Цикл завершается, когда очередной батч пуст.
     *
     * @return число резолвленных relations
     */
    private int resolveNarratorRelations() {
        Map<String, List<UUID>> idsByName = narratorRepository.findExternalNormalizedNameIds();
        int resolved = 0;
        long offset = 0;
        while (true) {
            List<NarratorRelation> batch = relationRepository.findUnresolved(RELATION_BATCH, offset);
            if (batch.isEmpty()) {
                break;
            }
            int skipped = 0;
            for (NarratorRelation relation : batch) {
                String normalized = ArabicTextNormalizer.normalize(relation.relatedName());
                List<UUID> candidates = idsByName.get(normalized);
                if (candidates != null && candidates.size() == 1) {
                    relationRepository.updateRelatedNarratorId(relation.id(), candidates.get(0));
                    resolved++;
                } else {
                    skipped++; // нет кандидата / гомонимы → остаётся в выборке, листаем мимо
                }
            }
            offset += skipped;
        }
        return resolved;
    }

    /** Объединяет сводки двух проходов (рави + хадисы) в одну. */
    private AlminasaImportSummary merge(AlminasaImportSummary a, AlminasaImportSummary b) {
        List<String> failures = new ArrayList<>(a.failures());
        for (String f : b.failures()) {
            if (failures.size() >= AlminasaImportSummary.FAILURES_CAP) {
                break;
            }
            failures.add(f);
        }
        return new AlminasaImportSummary(
                a.narratorsProcessed() + b.narratorsProcessed(),
                a.narratorsFailed() + b.narratorsFailed(),
                a.hadithsProcessed() + b.hadithsProcessed(),
                a.hadithsFailed() + b.hadithsFailed(),
                a.crossrefsResolved() + b.crossrefsResolved(),
                a.relationsResolved() + b.relationsResolved(),
                failures);
    }

    /** Добавляет пример упавшего дока «вид:id: message» до cap-лимита. */
    private static void addFailure(List<String> failures, String kind, String id, Exception e) {
        if (failures.size() >= AlminasaImportSummary.FAILURES_CAP) {
            return;
        }
        failures.add(kind + ":" + id + ": " + e.getMessage());
    }
}
