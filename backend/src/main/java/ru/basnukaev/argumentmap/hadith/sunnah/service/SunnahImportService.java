package ru.basnukaev.argumentmap.hadith.sunnah.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.hadith.domain.Collection;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.Matn;
import ru.basnukaev.argumentmap.hadith.repository.CollectionRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.MatnRepository;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.SunnahDataSource;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahCollectionRow;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahHadithRow;
import ru.basnukaev.argumentmap.hadith.sunnah.repository.SunnahBookDao;
import ru.basnukaev.argumentmap.hadith.sunnah.repository.SunnahChapterDao;
import ru.basnukaev.argumentmap.hadith.sunnah.repository.SunnahCollectionDao;
import ru.basnukaev.argumentmap.hadith.sunnah.repository.SunnahHadithDao;
import ru.basnukaev.argumentmap.hadith.sunnah.web.dto.SunnahHadithBrowseItem;
import ru.basnukaev.argumentmap.hadith.sunnah.web.dto.SunnahHadithPreview;

/**
 * Оркестратор импорта sunnah.com: источник → staging (sn_staging_*) → hd_*.
 * Phase 5 ETL шаг 2.d.
 *
 * <p><b>Bulk-policy gate:</b> импорт строго <b>по одному сборнику</b> за вызов
 * ({@link #importCollection}), либо <b>по одному хадису</b> для верифицируемого
 * фазового пути ({@link #importSingle}). Источник передаётся параметром
 * ({@link SunnahDataSource}) — dump-reader сейчас, API-client позже.
 *
 * <p><b>Без оборачивающей транзакции (bulk):</b> чтение из внешнего источника
 * (MySQL-дамп) — вне транзакции Postgres; staging-upsert'ы идемпотентны
 * (ON CONFLICT), а атомарность записи в hd_* обеспечивает
 * {@code @Transactional} внутри {@link SunnahToHadithMapper}. Повторный прогон
 * безопасен (re-runnable).
 *
 * <p><b>Фазовый импорт (ADR-052):</b> {@link #browseHadiths} — пролистать
 * корпус источника до импорта; {@link #previewSingle} — DRY-RUN маппинга в
 * наш формат без записи в БД (через rollback-транзакцию); {@link #importSingle}
 * — импорт ровно одного хадиса.
 */
@Service
public class SunnahImportService {

    private static final Logger log = LoggerFactory.getLogger(SunnahImportService.class);

    private static final int SNIPPET_LEN = 200;

    private final SunnahCollectionDao collectionDao;
    private final SunnahBookDao bookDao;
    private final SunnahChapterDao chapterDao;
    private final SunnahHadithDao hadithDao;
    private final SunnahToHadithMapper mapper;
    private final CollectionRepository collectionRepository;
    private final HadithRepository hadithRepository;
    private final MatnRepository matnRepository;
    private final ObjectMapper objectMapper;

    public SunnahImportService(SunnahCollectionDao collectionDao,
                               SunnahBookDao bookDao,
                               SunnahChapterDao chapterDao,
                               SunnahHadithDao hadithDao,
                               SunnahToHadithMapper mapper,
                               CollectionRepository collectionRepository,
                               HadithRepository hadithRepository,
                               MatnRepository matnRepository,
                               ObjectMapper objectMapper) {
        this.collectionDao = collectionDao;
        this.bookDao = bookDao;
        this.chapterDao = chapterDao;
        this.hadithDao = hadithDao;
        this.mapper = mapper;
        this.collectionRepository = collectionRepository;
        this.hadithRepository = hadithRepository;
        this.matnRepository = matnRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Импортирует один сборник целиком: читает из источника, наполняет
     * staging, затем переносит в hd_* маппером.
     *
     * @throws IllegalArgumentException если сборника нет в источнике
     */
    public SunnahMappingResult importCollection(SunnahDataSource source, String collectionName) {
        stageCollection(source, collectionName);
        return mapper.mapCollection(collectionName);
    }

    /**
     * Импорт ровно одного хадиса по номеру (фазовый/верифицируемый путь,
     * ADR-052). Наполняет staging сборника (идемпотентно) и маппит единственную
     * строку. Идемпотентен по (collection_id, primary_number).
     *
     * @throws IllegalArgumentException если сборника нет в источнике
     * @throws SunnahHadithNotFoundException если хадиса с таким номером нет в
     *         источнике этого сборника
     */
    public SunnahMappingResult importSingle(SunnahDataSource source,
                                            String collectionName, String number) {
        requireHadithInSource(source, collectionName, number);
        stageCollection(source, collectionName);
        return mapper.mapSingle(collectionName, number);
    }

    /**
     * DRY-RUN превью маппинга одного хадиса — БЕЗ записи в БД (ADR-052).
     *
     * <p><b>Почему недеструктивно:</b> метод {@code @Transactional}, прогоняет
     * реальный код импорта (staging upsert + {@code mapper.mapSingle}) внутри
     * транзакции, читает только что записанные hd_*-строки чтобы собрать превью,
     * затем форсит rollback через
     * {@code setRollbackOnly()}. Чтения внутри транзакции видят незакоммиченные
     * записи, а rollback их откатывает (включая staging upserts) — БД не
     * мутируется. Превью В ТОЧНОСТИ равно тому, что создал бы реальный импорт:
     * тот же mapper, та же чистка/нормализация/grades.
     *
     * <p><b>Уже импортированный</b> хадис: {@code mapSingle} вернёт
     * SKIPPED_EXISTING (нового write нет), читаем существующую hd_*-строку —
     * она тоже продукт реального маппера. <b>Непригодный</b> (нечисловой номер
     * / пустой арабский matn): маппер его пропускает (нет строки), собираем
     * превью из staged-строки с {@code importable=false}.
     *
     * @throws IllegalArgumentException если сборника нет в источнике
     * @throws SunnahHadithNotFoundException если хадиса нет в источнике
     */
    @Transactional
    public SunnahHadithPreview previewSingle(SunnahDataSource source,
                                             String collectionName, String number) {
        requireHadithInSource(source, collectionName, number);
        try {
            stageCollection(source, collectionName);
            boolean alreadyImported = isAlreadyImported(collectionName, number);
            mapper.mapSingle(collectionName, number);
            return buildPreview(collectionName, number, alreadyImported);
        } finally {
            // форсим откат: staging upserts + любые hd_*-записи стираются
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }

    /**
     * Список хадисов, доступных в ИСТОЧНИКЕ для сборника (до импорта), с флагом
     * {@code alreadyImported}. Пагинация в памяти: источник отдаёт хадисы
     * пакетом на сборник (как и bulk-импорт), отдельного per-hadith чтения у
     * {@link SunnahDataSource} нет.
     *
     * @throws IllegalArgumentException если сборника нет в источнике
     */
    @Transactional(readOnly = true)
    public List<SunnahHadithBrowseItem> browseHadiths(SunnahDataSource source,
                                                      String collectionName,
                                                      int limit, int offset) {
        requireCollectionInSource(source, collectionName);
        List<SunnahHadithRow> all = source.readHadiths(collectionName);
        Optional<Collection> collection = collectionRepository.findBySlug(collectionName);

        List<SunnahHadithBrowseItem> page = new ArrayList<>();
        int end = Math.min(offset + limit, all.size());
        for (int i = Math.max(offset, 0); i < end; i++) {
            SunnahHadithRow row = all.get(i);
            page.add(new SunnahHadithBrowseItem(
                    row.hadithNumber(),
                    snippet(row.bodyAr()),
                    snippet(row.bodyEn()),
                    isImported(collection, row.hadithNumber())));
        }
        return page;
    }

    /** Всего хадисов в источнике для сборника — для PagedResponse.total. */
    @Transactional(readOnly = true)
    public long countHadiths(SunnahDataSource source, String collectionName) {
        requireCollectionInSource(source, collectionName);
        return source.readHadiths(collectionName).size();
    }

    // ---- внутреннее ----

    private void stageCollection(SunnahDataSource source, String collectionName) {
        List<SunnahCollectionRow> collections = source.readCollections().stream()
                .filter(c -> collectionName.equals(c.name()))
                .toList();
        if (collections.isEmpty()) {
            throw new IllegalArgumentException(
                    "Сборник не найден в источнике sunnah: " + collectionName);
        }
        collectionDao.upsertAll(collections);
        int books = bookDao.upsertAll(source.readBooks(collectionName));
        int chapters = chapterDao.upsertAll(source.readChapters(collectionName));
        int hadiths = hadithDao.upsertAll(source.readHadiths(collectionName));
        log.info("sunnah staging залит {}: books={} chapters={} hadiths={}",
                collectionName, books, chapters, hadiths);
    }

    private void requireCollectionInSource(SunnahDataSource source, String collectionName) {
        boolean exists = source.readCollections().stream()
                .anyMatch(c -> collectionName.equals(c.name()));
        if (!exists) {
            throw new IllegalArgumentException(
                    "Сборник не найден в источнике sunnah: " + collectionName);
        }
    }

    private void requireHadithInSource(SunnahDataSource source,
                                       String collectionName, String number) {
        requireCollectionInSource(source, collectionName);
        boolean exists = source.readHadiths(collectionName).stream()
                .anyMatch(h -> number.equals(h.hadithNumber()));
        if (!exists) {
            throw new SunnahHadithNotFoundException(collectionName, number);
        }
    }

    private boolean isAlreadyImported(String collectionName, String number) {
        return isImported(collectionRepository.findBySlug(collectionName), number);
    }

    private boolean isImported(Optional<Collection> collection, String number) {
        if (collection.isEmpty()) {
            return false;
        }
        Integer parsed = parseNumber(number);
        if (parsed == null) {
            return false;
        }
        return hadithRepository
                .findByCollectionIdAndPrimaryNumber(collection.get().id(), parsed)
                .isPresent();
    }

    /**
     * Собирает превью из записанных (внутри rollback-транзакции) hd_*-строк.
     * Если хадис непригоден (маппер пропустил) — собирает из staged-строки с
     * {@code importable=false}.
     */
    private SunnahHadithPreview buildPreview(String collectionName, String number,
                                             boolean alreadyImported) {
        Integer parsed = parseNumber(number);
        SunnahHadithRow staged = hadithDao.findByCollection(collectionName).stream()
                .filter(h -> number.equals(h.hadithNumber()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Staged-хадис исчез: " + collectionName + "/" + number));

        Optional<Collection> collection = collectionRepository.findBySlug(collectionName);
        Optional<Hadith> mapped = (parsed == null || collection.isEmpty())
                ? Optional.empty()
                : hadithRepository.findByCollectionIdAndPrimaryNumber(collection.get().id(), parsed);

        if (mapped.isEmpty()) {
            // непригоден (нечисловой номер / пустой matn) — маппер пропустил
            return new SunnahHadithPreview(
                    collectionName, parsed,
                    ru.basnukaev.argumentmap.hadith.domain.HadithStatus.VARIANT,
                    staged.bodyAr(), staged.bodyEn(),
                    ru.basnukaev.argumentmap.hadith.service.ArabicTextNormalizer.normalize(staged.bodyAr()),
                    parseGrades(staged.gradesJson()),
                    new SunnahHadithPreview.Structure(
                            staged.bookNumber(), null, null, staged.chapterId(), null, null),
                    null, false, alreadyImported);
        }

        Hadith h = mapped.get();
        List<Matn> matns = matnRepository.findByHadithId(h.id());
        Matn m = matns.isEmpty() ? null : matns.get(0);
        return new SunnahHadithPreview(
                collectionName, h.primaryNumber(), h.status(),
                m == null ? null : m.textAr(),
                m == null ? null : m.textEn(),
                h.normalizedMatn(),
                gradesFromMetadata(h.metadata()),
                structureFromMetadata(m == null ? null : m.metadata()),
                null, true, alreadyImported);
    }

    private List<SunnahHadithPreview.GradeView> parseGrades(String gradesJson) {
        // staged grades: [{graded_by, grade}] → preview [{scholar, grade}]
        List<SunnahHadithPreview.GradeView> out = new ArrayList<>();
        if (gradesJson == null || gradesJson.isBlank()) {
            return out;
        }
        try {
            JsonNode arr = objectMapper.readTree(gradesJson);
            if (arr.isArray()) {
                for (JsonNode g : arr) {
                    String scholar = textOrNull(g, "graded_by");
                    String grade = textOrNull(g, "grade");
                    if (scholar != null || grade != null) {
                        out.add(new SunnahHadithPreview.GradeView(scholar, grade));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("preview: разбор staged grades не удался: {}", e.getMessage());
        }
        return out;
    }

    private List<SunnahHadithPreview.GradeView> gradesFromMetadata(String metadataJson) {
        // записанный hd_hadiths.metadata.grades: [{scholar, grade}]
        List<SunnahHadithPreview.GradeView> out = new ArrayList<>();
        if (metadataJson == null || metadataJson.isBlank()) {
            return out;
        }
        try {
            JsonNode grades = objectMapper.readTree(metadataJson).get("grades");
            if (grades != null && grades.isArray()) {
                for (JsonNode g : grades) {
                    out.add(new SunnahHadithPreview.GradeView(
                            textOrNull(g, "scholar"), textOrNull(g, "grade")));
                }
            }
        } catch (Exception e) {
            log.warn("preview: разбор hd metadata grades не удался: {}", e.getMessage());
        }
        return out;
    }

    private SunnahHadithPreview.Structure structureFromMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return new SunnahHadithPreview.Structure(null, null, null, null, null, null);
        }
        try {
            JsonNode n = objectMapper.readTree(metadataJson);
            return new SunnahHadithPreview.Structure(
                    textOrNull(n, "bookNumber"), textOrNull(n, "bookNameAr"),
                    textOrNull(n, "bookNameEn"), textOrNull(n, "chapterId"),
                    textOrNull(n, "chapterTitleAr"), textOrNull(n, "chapterTitleEn"));
        } catch (Exception e) {
            log.warn("preview: разбор hd_matns metadata не удался: {}", e.getMessage());
            return new SunnahHadithPreview.Structure(null, null, null, null, null, null);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    private static String snippet(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= SNIPPET_LEN ? text : text.substring(0, SNIPPET_LEN) + "…";
    }

    /**
     * Строгий разбор номера (только ASCII-цифры) — зеркалит
     * {@code SunnahToHadithMapper.parseNumber}: "1a"/арабо-индийские → null.
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
