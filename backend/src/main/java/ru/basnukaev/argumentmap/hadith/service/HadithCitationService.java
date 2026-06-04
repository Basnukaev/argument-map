package ru.basnukaev.argumentmap.hadith.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.domain.NodeSource;
import ru.basnukaev.argumentmap.domain.Source;
import ru.basnukaev.argumentmap.domain.SourceType;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.repository.CollectionRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.web.HadithNotFoundException;
import ru.basnukaev.argumentmap.service.NodeSourceService;
import ru.basnukaev.argumentmap.service.SourceService;

/**
 * Линковка хадисов из {@code hd_*} в узлы графа как опору (под-проект #2).
 *
 * <p>Переиспользует мост {@code Hadith.sourceId → sources.id}: хадис-цитата —
 * обычная опора-источник (sourceType=HADITH) в {@code node_sources}, без
 * параллельной node↔hadith таблицы. Спека:
 * {@code docs/specs/2026-06-01-hadith-node-citation-design.md}.
 */
@Service
public class HadithCitationService {

    private final HadithRepository hadithRepository;
    private final CollectionRepository collectionRepository;
    private final SourceService sourceService;
    private final NodeSourceService nodeSourceService;

    public HadithCitationService(HadithRepository hadithRepository,
                                 CollectionRepository collectionRepository,
                                 SourceService sourceService,
                                 NodeSourceService nodeSourceService) {
        this.hadithRepository = hadithRepository;
        this.collectionRepository = collectionRepository;
        this.sourceService = sourceService;
        this.nodeSourceService = nodeSourceService;
    }

    /**
     * Прикрепляет хадис к узлу: гарантирует Source для хадиса, затем линкует
     * его в {@code node_sources} (с authz — assertCanWrite на тему узла внутри
     * {@link NodeSourceService}).
     *
     * @throws HadithNotFoundException если хадиса нет
     */
    @Transactional
    public NodeSource attachHadithToNode(UUID nodeId, UUID hadithId,
                                         UUID actorUserId, String actorRole) {
        Hadith hadith = hadithRepository.findById(hadithId)
                .orElseThrow(() -> new HadithNotFoundException(hadithId));
        UUID sourceId = ensureSourceForHadith(hadith);
        return nodeSourceService.attachSource(nodeId, sourceId, null, null, null,
                actorUserId, actorRole);
    }

    /** Один Source на хадис: вернуть существующий либо создать + выставить source_id. */
    private UUID ensureSourceForHadith(Hadith hadith) {
        if (hadith.sourceId() != null) {
            return hadith.sourceId();
        }
        Source source = sourceService.createSource(
                SourceType.HADITH, buildTitle(hadith), null, null, null, null, null);
        hadithRepository.updateSourceId(hadith.id(), source.id());
        return source.id();
    }

    /** «<Сборник> №<номер>» для title источника; деградирует до «Хадис №N». */
    private String buildTitle(Hadith hadith) {
        String collection = hadith.collectionId() == null ? null
                : collectionRepository.findById(hadith.collectionId())
                        .map(c -> firstNonBlank(c.nameRu(), c.nameAr(), c.slug()))
                        .orElse(null);
        String number = hadith.primaryNumber() != null ? " №" + hadith.primaryNumber() : "";
        return (collection != null ? collection : "Хадис") + number;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
