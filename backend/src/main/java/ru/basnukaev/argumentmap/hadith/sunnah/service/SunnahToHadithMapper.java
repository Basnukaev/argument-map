package ru.basnukaev.argumentmap.hadith.sunnah.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.hadith.domain.Collection;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.HadithStatus;
import ru.basnukaev.argumentmap.hadith.domain.Matn;
import ru.basnukaev.argumentmap.hadith.repository.CollectionRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.MatnRepository;
import ru.basnukaev.argumentmap.hadith.service.ArabicTextNormalizer;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahBookRow;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahChapterRow;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahCollectionRow;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahHadithRow;
import ru.basnukaev.argumentmap.hadith.sunnah.repository.SunnahBookDao;
import ru.basnukaev.argumentmap.hadith.sunnah.repository.SunnahChapterDao;
import ru.basnukaev.argumentmap.hadith.sunnah.repository.SunnahCollectionDao;
import ru.basnukaev.argumentmap.hadith.sunnah.repository.SunnahHadithDao;

/**
 * Маппер staging sn_staging_* → hd_collections / hd_hadiths / hd_matns.
 * Phase 5 ETL шаг 2.c (спека §6/§11). Пилот: Бухари + Муслим.
 *
 * <p><b>Что переносит:</b> арабский + английский текст, оценки учёных
 * (grades → hd_hadiths.metadata в формате {scholar,grade}), структуру
 * книга/глава (→ hd_matns.metadata). Каждый staging-хадис даёт один
 * hd_hadiths + один первичный hd_matns.
 *
 * <p><b>Идемпотентность:</b> по естественному ключу (collection_id,
 * primary_number) — повторный прогон пропускает уже импортированные.
 *
 * <p><b>Status = VARIANT</b> (не CANONICAL): импортированные хадисы
 * <i>не</i> выверены как канон в нашей системе (курируемые seed'ы —
 * CANONICAL). Оценки sunnah.com лежат в metadata.grades как информация,
 * статус из них автоматически НЕ выводится (академическая осторожность,
 * §11.3).
 *
 * <p><b>Чего НЕ делает (отложено):</b>
 * <ul>
 *   <li>дедуп вариаций между сборниками в один hd_hadiths с несколькими
 *       hd_matns (спека §6/§8.1) — требует fuzzy/LCS-сопоставления с
 *       порогом; пилот приоритезирует широту каталога и корректность;</li>
 *   <li>структурный иснад (hd_sanads) — sunnah.com не даёт цепочку,
 *       извлекается отдельной стадией IsnadExtraction (шаг 3);</li>
 *   <li>хадисы с нечисловым номером ("1a") или пустым арабским matn'ом —
 *       пропускаются и считаются в skippedInvalid.</li>
 * </ul>
 */
@Service
public class SunnahToHadithMapper {

    private static final Logger log = LoggerFactory.getLogger(SunnahToHadithMapper.class);

    private final SunnahCollectionDao collectionDao;
    private final SunnahBookDao bookDao;
    private final SunnahChapterDao chapterDao;
    private final SunnahHadithDao hadithDao;
    private final CollectionRepository collectionRepository;
    private final HadithRepository hadithRepository;
    private final MatnRepository matnRepository;
    private final ObjectMapper objectMapper;

    public SunnahToHadithMapper(SunnahCollectionDao collectionDao,
                                SunnahBookDao bookDao,
                                SunnahChapterDao chapterDao,
                                SunnahHadithDao hadithDao,
                                CollectionRepository collectionRepository,
                                HadithRepository hadithRepository,
                                MatnRepository matnRepository,
                                ObjectMapper objectMapper) {
        this.collectionDao = collectionDao;
        this.bookDao = bookDao;
        this.chapterDao = chapterDao;
        this.hadithDao = hadithDao;
        this.collectionRepository = collectionRepository;
        this.hadithRepository = hadithRepository;
        this.matnRepository = matnRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SunnahMappingResult mapCollection(String collectionName) {
        SunnahCollectionRow staging = collectionDao.findByName(collectionName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Нет staging-сборника sunnah: " + collectionName));
        Instant now = Instant.now();
        Collection collection = resolveCollection(staging, now);

        Map<String, SunnahBookRow> books = bookDao.findByCollection(collectionName).stream()
                .collect(Collectors.toMap(SunnahBookRow::bookNumber, b -> b,
                        (a, b) -> a, LinkedHashMap::new));
        Map<String, SunnahChapterRow> chapters = chapterDao.findByCollection(collectionName).stream()
                .collect(Collectors.toMap(c -> chapterKey(c.bookNumber(), c.chapterId()), c -> c,
                        (a, b) -> a, LinkedHashMap::new));

        int inserted = 0;
        int skippedExisting = 0;
        int skippedInvalid = 0;
        for (SunnahHadithRow row : hadithDao.findByCollection(collectionName)) {
            Integer number = parseNumber(row.hadithNumber());
            String normalized = ArabicTextNormalizer.normalize(row.bodyAr());
            if (number == null || normalized.isEmpty()) {
                skippedInvalid++;
                continue;
            }
            if (hadithRepository.findByCollectionIdAndPrimaryNumber(collection.id(), number).isPresent()) {
                skippedExisting++;
                continue;
            }
            UUID hadithId = UUID.randomUUID();
            hadithRepository.save(new Hadith(hadithId, collection.id(), number, normalized,
                    HadithStatus.VARIANT, null, buildHadithMetadata(row), now));
            matnRepository.save(new Matn(UUID.randomUUID(), hadithId, row.bodyAr(), normalized,
                    null, row.bodyEn(), collection.id(), number, null, null,
                    true, null, buildMatnMetadata(row, books, chapters), now));
            inserted++;
        }
        log.info("sunnah→hd mapping {}: inserted={} skippedExisting={} skippedInvalid={}",
                collectionName, inserted, skippedExisting, skippedInvalid);
        return new SunnahMappingResult(collectionName, inserted, skippedExisting, skippedInvalid);
    }

    /**
     * Резолв сборника: переиспользуем существующий по slug (НЕ перезаписываем —
     * сборник мог быть курирован), иначе создаём из staging-метаданных.
     */
    private Collection resolveCollection(SunnahCollectionRow staging, Instant now) {
        return collectionRepository.findBySlug(staging.name()).orElseGet(() -> {
            String nameAr = staging.titleAr() != null ? staging.titleAr() : staging.name();
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("source", "sunnah");
            if (staging.totalAvailableHadith() != null) {
                meta.put("totalAvailableHadith", staging.totalAvailableHadith());
            }
            return collectionRepository.save(new Collection(
                    UUID.randomUUID(), staging.name(), nameAr, staging.titleEn(), null,
                    null, staging.totalHadith(), writeJson(meta), now));
        });
    }

    private String buildHadithMetadata(SunnahHadithRow row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source", "sunnah");
        m.put("collection", row.collectionName());
        m.put("hadithNumber", row.hadithNumber());
        if (row.urnAr() != null) {
            m.put("urnAr", row.urnAr());
        }
        if (row.urnEn() != null) {
            m.put("urnEn", row.urnEn());
        }
        List<Map<String, Object>> grades = transformGrades(row.gradesJson());
        if (!grades.isEmpty()) {
            m.put("grades", grades);
        }
        return writeJson(m);
    }

    private String buildMatnMetadata(SunnahHadithRow row,
                                     Map<String, SunnahBookRow> books,
                                     Map<String, SunnahChapterRow> chapters) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (row.bookNumber() != null) {
            m.put("bookNumber", row.bookNumber());
            SunnahBookRow b = books.get(row.bookNumber());
            if (b != null) {
                putIfPresent(m, "bookNameAr", b.nameAr());
                putIfPresent(m, "bookNameEn", b.nameEn());
            }
        }
        if (row.chapterId() != null) {
            m.put("chapterId", row.chapterId());
            // title резолвим только при наличии bookNumber — chapterKey
            // составной (book/chapter); без книги lookup был бы "null/N"
            if (row.bookNumber() != null) {
                SunnahChapterRow ch = chapters.get(chapterKey(row.bookNumber(), row.chapterId()));
                if (ch != null) {
                    putIfPresent(m, "chapterTitleAr", ch.titleAr());
                    putIfPresent(m, "chapterTitleEn", ch.titleEn());
                }
            }
        }
        return m.isEmpty() ? null : writeJson(m);
    }

    /**
     * sunnah-grades {@code [{graded_by, grade}]} → формат hd_hadiths.metadata
     * {@code [{scholar, grade}]}, который читает HadithController.parseGrades.
     * Defensive: невалидный JSON → пустой список.
     */
    private List<Map<String, Object>> transformGrades(String gradesJson) {
        if (gradesJson == null || gradesJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode arr = objectMapper.readTree(gradesJson);
            if (!arr.isArray()) {
                return List.of();
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (JsonNode g : arr) {
                String scholar = textOrNull(g, "graded_by");
                String grade = textOrNull(g, "grade");
                if (scholar == null && grade == null) {
                    continue;
                }
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("scholar", scholar);
                entry.put("grade", grade);
                out.add(entry);
            }
            return out;
        } catch (JsonProcessingException e) {
            log.warn("sunnah grades parse failed ({}): {}", e.getMessage(), gradesJson);
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать metadata", e);
        }
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static String chapterKey(String bookNumber, String chapterId) {
        return bookNumber + "/" + chapterId;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    /**
     * Строгий разбор номера хадиса: только ASCII-цифры ("1a" → null,
     * арабо-индийские "١٢" → null). ASCII-ограничение намеренно: иначе
     * Character.isDigit + Integer.parseInt приняли бы "١٢"=12, что дало бы
     * коллизию в idempotency-ключе (collection_id, primary_number) с "12".
     */
    private static Integer parseNumber(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty() || !t.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return null;
        }
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
