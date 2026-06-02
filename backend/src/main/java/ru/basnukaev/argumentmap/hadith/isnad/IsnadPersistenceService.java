package ru.basnukaev.argumentmap.hadith.isnad;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.Sanad;
import ru.basnukaev.argumentmap.hadith.domain.SanadNarrator;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRepository;
import ru.basnukaev.argumentmap.hadith.repository.SanadRepository;
import ru.basnukaev.argumentmap.hadith.service.ArabicTextNormalizer;

/**
 * Персист извлечённого LLM иснада (ADR-059 amendment) в
 * hd_sanads / hd_narrators / hd_sanad_narrators — чтобы реальный
 * {@code /hadith}-explorer (через {@code SanadGraphService.buildGraph})
 * показывал граф иснада для импортированных хадисов. До этого извлечённый
 * иснад был эфемерным превью (ADR-059, in-memory граф).
 *
 * <p><b>Дедуп нарраторов (MVP)</b> — по нормализованному арабскому имени
 * ({@link ArabicTextNormalizer}). Один передатчик = одна строка
 * hd_narrators, переиспользуемая между хадисами/цепями. Дедуп несовершенен:
 * омонимы (разные исторические личности с одинаковой нормализованной формой)
 * будут ошибочно слиты, а вариативность написания
 * (الحميدي / عبد الله بن الزبير الحميدي) — наоборот раздвоит. Настоящая
 * rijal-резолюция (alminasa / справочник передатчиков) — future. Дедуп
 * сделан find-then-save: TOCTOU-гонка допустима для admin single-import
 * (последовательный, один поток).
 *
 * <p><b>Идемпотентность</b> — delete-recreate per hadith: повторный импорт
 * сносит существующие цепи хадиса и пересоздаёт из свежего извлечения (не
 * плодит дубли). Сами нарраторы при этом не удаляются (шарятся).
 *
 * <p><b>Маппинг позиций</b> — зеркалит {@code SanadGraphService}: извлечённая
 * цепь идёт top→companion (как в матне), а в БД position 0 = сподвижник
 * (Prophet-side). Реверсим список; transmission_phrase на позиции i — формула,
 * которой нарратор позиции i получил хадис от нарратора позиции i-1 (в сторону
 * Пророка ﷺ), что в точности соответствует {@code ExtractedNarrator.transmission}
 * после реверса.
 */
@Service
public class IsnadPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(IsnadPersistenceService.class);

    /** Лимит varchar(40) на hd_sanad_narrators.transmission_phrase. */
    private static final int TRANSMISSION_MAX_LEN = 40;

    private final SanadRepository sanadRepository;
    private final NarratorRepository narratorRepository;
    private final ObjectMapper objectMapper;

    public IsnadPersistenceService(SanadRepository sanadRepository,
                                   NarratorRepository narratorRepository,
                                   ObjectMapper objectMapper) {
        this.sanadRepository = sanadRepository;
        this.narratorRepository = narratorRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Персистит извлечённый иснад как одну цепь (primary chain) хадиса.
     *
     * @param hadithId         целевой хадис (hd_hadiths.id)
     * @param isnad            извлечённая цепь; no-op если null /
     *                         {@code !isnadFound} / пустые narrators
     * @param collectionBookId lib_books.id сборника (для
     *                         {@code compiled_in_book_id}, FK на lib_books;
     *                         nullable если книги-представления ещё нет)
     * @param collectionAr     арабское имя сборника (в metadata цепи)
     * @param collectionRu     русское имя сборника (в metadata цепи)
     */
    @Transactional
    public void persist(UUID hadithId, ExtractedIsnad isnad,
                        UUID collectionBookId, String collectionAr, String collectionRu) {
        if (isnad == null || !isnad.isnadFound() || isnad.narrators().isEmpty()) {
            return;
        }

        // delete-recreate: повторный импорт обновляет цепь, без дублей
        sanadRepository.deleteByHadithId(hadithId);

        // top→companion → companion→top: position 0 = сподвижник (Prophet-side)
        List<ExtractedNarrator> chain = new ArrayList<>(isnad.narrators());
        Collections.reverse(chain);

        UUID sanadId = UUID.randomUUID();
        sanadRepository.save(new Sanad(
                sanadId, hadithId,
                null,                 // chainGrade — не оцениваем извлечённую цепь
                null,                 // compiledById — составитель/книга не звено цепи
                collectionBookId,     // compiledInBookId → lib_books
                true,                 // primaryChain
                chainMetadata(collectionAr, collectionRu),
                Instant.now()
        ));

        // кэш внутри одного вызова: повторяющееся имя в цепи (редко, но
        // возможно) не делает лишний lookup и не плодит строк нарраторов
        Map<String, UUID> narratorIdByNormalized = new HashMap<>();
        for (int i = 0; i < chain.size(); i++) {
            ExtractedNarrator extracted = chain.get(i);
            UUID narratorId = resolveNarratorId(extracted.name(), narratorIdByNormalized);
            sanadRepository.saveNarratorLink(new SanadNarrator(
                    sanadId, i, narratorId, truncate(extracted.transmission())));
        }
    }

    /**
     * Резолвит narrator по нормализованному имени: in-call cache → БД →
     * создаёт новую строку. Возвращает id для линковки.
     */
    private UUID resolveNarratorId(String nameAr, Map<String, UUID> cache) {
        String normalized = ArabicTextNormalizer.normalize(nameAr);
        UUID cached = cache.get(normalized);
        if (cached != null) {
            return cached;
        }

        UUID id = narratorRepository.findByNameArNormalized(normalized)
                .map(Narrator::id)
                .orElseGet(() -> createNarrator(nameAr, normalized));
        cache.put(normalized, id);
        return id;
    }

    /** Новый narrator: только арабское имя + нормализованная форма; био пусто. */
    private UUID createNarrator(String nameAr, String normalized) {
        UUID id = UUID.randomUUID();
        narratorRepository.save(new Narrator(
                id, null, nameAr, normalized,
                null, null, null, null, null, null, null,
                null, null, 0,
                "{\"source\":\"ai_isnad_extraction\"}",
                Instant.now()
        ));
        return id;
    }

    private String chainMetadata(String collectionAr, String collectionRu) {
        Map<String, String> meta = new HashMap<>();
        meta.put("source", "ai_isnad_extraction");
        if (collectionAr != null) {
            meta.put("collectionAr", collectionAr);
        }
        if (collectionRu != null) {
            meta.put("collectionRu", collectionRu);
        }
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            // сериализация Map<String,String> не падает на практике;
            // defensive — цепь персистится с минимальным маркером источника
            log.warn("Не удалось сериализовать metadata цепи: {}", e.getMessage());
            return "{\"source\":\"ai_isnad_extraction\"}";
        }
    }

    /** Обрезка под varchar(40): formula передачи может прийти длиннее лимита. */
    private static String truncate(String transmission) {
        if (transmission == null) {
            return null;
        }
        return transmission.length() <= TRANSMISSION_MAX_LEN
                ? transmission
                : transmission.substring(0, TRANSMISSION_MAX_LEN);
    }
}
