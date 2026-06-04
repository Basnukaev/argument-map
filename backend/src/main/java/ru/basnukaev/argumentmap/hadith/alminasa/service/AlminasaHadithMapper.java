package ru.basnukaev.argumentmap.hadith.alminasa.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.basnukaev.argumentmap.hadith.alminasa.etl.AlminasaIsnadParser;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmExplanationRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmHadithRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmRulingRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.IsnadLink;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.ParsedIsnad;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmExplanationStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmHadithStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmRulingStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.service.AlminasaCollections.CollectionInfo;
import ru.basnukaev.argumentmap.hadith.alminasa.service.dto.AlminasaDryRunResult;
import ru.basnukaev.argumentmap.hadith.alminasa.service.dto.AlminasaDryRunResult.SanadLinkPreview;
import ru.basnukaev.argumentmap.hadith.domain.Collection;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.HadithCrossref;
import ru.basnukaev.argumentmap.hadith.domain.HadithEdition;
import ru.basnukaev.argumentmap.hadith.domain.HadithExplanation;
import ru.basnukaev.argumentmap.hadith.domain.HadithRuling;
import ru.basnukaev.argumentmap.hadith.domain.HadithStatus;
import ru.basnukaev.argumentmap.hadith.domain.Matn;
import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.Sanad;
import ru.basnukaev.argumentmap.hadith.domain.SanadNarrator;
import ru.basnukaev.argumentmap.hadith.repository.CollectionRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithCrossrefRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithEditionRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithExplanationRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithRulingRepository;
import ru.basnukaev.argumentmap.hadith.repository.MatnRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRepository;
import ru.basnukaev.argumentmap.hadith.repository.SanadRepository;
import ru.basnukaev.argumentmap.hadith.service.ArabicTextNormalizer;

/**
 * Ядро маппера: {@code am_staging_hadith} → {@code hd_hadiths} + сателлиты
 * (matns/editions/sanads+narrators/crossrefs/rulings/explanations) (план 3,
 * Task 4, решения 1-3, 6-9, 12).
 *
 * <p>Детерминированный (БЕЗ AI). Идемпотентный upsert хадиса по
 * {@code external_id}; сателлиты — delete-recreate per hadith. Порядок в
 * транзакции (решение 9): resolve UUID → deleteByHadithId ВСЕХ сателлитов →
 * re-insert. {@code @Transactional} на public-методах; бин отделён от
 * orchestration-бина (решение 10).
 */
@Service
public class AlminasaHadithMapper {

    private static final Logger log = LoggerFactory.getLogger(AlminasaHadithMapper.class);

    static final String SOURCE = "alminasa";

    /** Лимит varchar(40) на hd_sanad_narrators.transmission_phrase. */
    private static final int TRANSMISSION_MAX_LEN = 40;
    /** Лимит varchar(40) на hd_hadiths.hadith_type. */
    private static final int HADITH_TYPE_MAX_LEN = 40;
    /** Длина превью матна в dry-run. */
    private static final int MATN_PREVIEW_LEN = 200;

    private final AmHadithStagingDao hadithStagingDao;
    private final AmRulingStagingDao rulingStagingDao;
    private final AmExplanationStagingDao explanationStagingDao;
    private final CollectionRepository collectionRepository;
    private final HadithRepository hadithRepository;
    private final MatnRepository matnRepository;
    private final HadithEditionRepository editionRepository;
    private final SanadRepository sanadRepository;
    private final HadithCrossrefRepository crossrefRepository;
    private final HadithRulingRepository rulingRepository;
    private final HadithExplanationRepository explanationRepository;
    private final NarratorRepository narratorRepository;
    private final AlminasaNarratorMapper narratorMapper;
    private final ObjectMapper objectMapper;

    public AlminasaHadithMapper(AmHadithStagingDao hadithStagingDao,
                                AmRulingStagingDao rulingStagingDao,
                                AmExplanationStagingDao explanationStagingDao,
                                CollectionRepository collectionRepository,
                                HadithRepository hadithRepository,
                                MatnRepository matnRepository,
                                HadithEditionRepository editionRepository,
                                SanadRepository sanadRepository,
                                HadithCrossrefRepository crossrefRepository,
                                HadithRulingRepository rulingRepository,
                                HadithExplanationRepository explanationRepository,
                                NarratorRepository narratorRepository,
                                AlminasaNarratorMapper narratorMapper,
                                ObjectMapper objectMapper) {
        this.hadithStagingDao = hadithStagingDao;
        this.rulingStagingDao = rulingStagingDao;
        this.explanationStagingDao = explanationStagingDao;
        this.collectionRepository = collectionRepository;
        this.hadithRepository = hadithRepository;
        this.matnRepository = matnRepository;
        this.editionRepository = editionRepository;
        this.sanadRepository = sanadRepository;
        this.crossrefRepository = crossrefRepository;
        this.rulingRepository = rulingRepository;
        this.explanationRepository = explanationRepository;
        this.narratorRepository = narratorRepository;
        this.narratorMapper = narratorMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * Маппит один staging-хадис в {@code hd_hadiths} + сателлиты.
     *
     * @return id строки {@code hd_hadiths} (стабилен между повторными прогонами)
     * @throws AlminasaMappingException пустой/отсутствующий {@code matn_with_tashkeel}
     */
    @Transactional
    public UUID mapHadith(AmHadithRow row) {
        JsonNode raw = parse(row.rawJson(), row.hadithId());
        String externalId = row.hadithId();

        String matnText = text(raw, "matn_with_tashkeel");
        if (matnText == null) {
            throw new AlminasaMappingException(
                    "Пустой matn_with_tashkeel — хадис без матна бессмыслен", externalId, null);
        }

        Collection collection = resolveOrCreateCollection(row, raw);
        UUID collectionId = collection.id();

        Integer primaryNumber = resolvePrimaryNumber(raw, collectionId, externalId);

        Optional<Hadith> existing = hadithRepository.findByExternalId(SOURCE, externalId);
        UUID hadithId = existing.map(Hadith::id).orElseGet(UUID::randomUUID);

        String status = (row.bookId() == 146 || row.bookId() == 158)
                ? HadithStatus.CANONICAL : HadithStatus.VARIANT;
        String fullTextAr = text(raw, "hadith");

        Hadith hadith = new Hadith(
                hadithId,
                collectionId,
                primaryNumber,
                ArabicTextNormalizer.normalize(matnText),
                status,
                existing.map(Hadith::sourceId).orElse(null),
                buildHadithMetadata(raw),
                existing.map(Hadith::createdAt).orElseGet(Instant::now),
                SOURCE,
                externalId,
                truncate(text(raw, "type"), HADITH_TYPE_MAX_LEN),
                text(raw, "chapter"),
                text(raw, "sub_chapter"),
                fullTextAr
        );

        if (existing.isPresent()) {
            hadithRepository.update(hadith);
        } else {
            hadithRepository.save(hadith);
        }

        // delete-recreate ВСЕХ сателлитов (решение 9): порядок resolve → delete → insert
        matnRepository.deleteByHadithId(hadithId);
        editionRepository.deleteByHadithId(hadithId);
        sanadRepository.deleteByHadithId(hadithId);
        crossrefRepository.deleteByHadithId(hadithId);
        rulingRepository.deleteByHadithId(hadithId);
        explanationRepository.deleteByHadithId(hadithId);

        insertMatn(hadithId, collectionId, matnText, primaryNumber, raw);
        insertEditions(hadithId, raw);
        insertSanad(hadithId, raw, fullTextAr);
        insertCrossrefs(hadithId, externalId, raw);
        insertRulings(hadithId, externalId, raw);
        insertExplanations(hadithId, externalId);

        return hadithId;
    }

    /**
     * DRY-RUN превью маппинга — БЕЗ записи в БД. Прогоняет реальный
     * {@link #mapHadith} внутри транзакции, собирает снапшот из персистнутых
     * строк, затем форсит rollback ({@code setRollbackOnly}). БД не мутируется.
     *
     * @throws AlminasaMappingException хадиса нет в staging / пустой матн
     */
    @Transactional
    public AlminasaDryRunResult dryRunHadith(String hadithId) {
        AmHadithRow row = hadithStagingDao.findById(hadithId)
                .orElseThrow(() -> new AlminasaMappingException(
                        "Хадис не найден в staging: " + hadithId, hadithId, null));
        try {
            // self-invocation НАМЕРЕННА: @Transactional mapHadith тут no-op,
            // маппинг живёт в транзакции dryRun — setRollbackOnly откатывает
            // ВСЁ, включая cross-bean записи ensureNarrator (REQUIRED).
            // НЕ выносить в отдельный бин и НЕ ставить REQUIRES_NEW.
            UUID id = mapHadith(row);
            return buildDryRunResult(id);
        } finally {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }

    // ── collection ────────────────────────────────────────────────────────────

    private Collection resolveOrCreateCollection(AmHadithRow row, JsonNode raw) {
        Optional<CollectionInfo> info = AlminasaCollections.byBookId(row.bookId());
        String slug = info.map(CollectionInfo::slug).orElse("book-" + row.bookId());
        return collectionRepository.findBySlug(slug).orElseGet(() -> {
            String nameAr = text(raw, "book_name");
            if (nameAr == null) {
                nameAr = info.map(CollectionInfo::nameAr).orElse(slug);
            }
            String nameRu = info.map(CollectionInfo::nameRu).orElse(null);
            return collectionRepository.save(new Collection(
                    UUID.randomUUID(), slug, nameAr, null, nameRu,
                    null, null, "{\"source\":\"" + SOURCE + "\"}", Instant.now()));
        });
    }

    /**
     * primary_number из {@code number[0]} (defensive: int ИЛИ строка; пусто → null).
     * Пре-чек UNIQUE(collection_id, primary_number): занято ДРУГИМ external_id →
     * null + WARN (решение 12).
     */
    private Integer resolvePrimaryNumber(JsonNode raw, UUID collectionId, String externalId) {
        Integer number = firstNumber(raw.path("number"));
        if (number == null) {
            return null;
        }
        Optional<Hadith> occupant = hadithRepository.findByCollectionIdAndPrimaryNumber(collectionId, number);
        if (occupant.isPresent() && !externalId.equals(occupant.get().externalId())) {
            log.warn("alminasa primary_number={} в collection={} занят другим external_id={} — "
                    + "у {} primary_number=NULL (номера в metadata.numbers)",
                    number, collectionId, occupant.get().externalId(), externalId);
            return null;
        }
        return number;
    }

    /** Первый элемент {@code number[]} как int (элемент бывает int ИЛИ строка). */
    private static Integer firstNumber(JsonNode arr) {
        if (!arr.isArray() || arr.isEmpty()) {
            return null;
        }
        JsonNode first = arr.get(0);
        if (first.isInt() || first.isLong()) {
            return first.asInt();
        }
        if (first.isTextual()) {
            try {
                return Integer.valueOf(first.asText().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    // ── satellites ──────────────────────────────────────────────────────────────

    private void insertMatn(UUID hadithId, UUID collectionId, String matnText,
                            Integer primaryNumber, JsonNode raw) {
        matnRepository.save(new Matn(
                UUID.randomUUID(), hadithId, matnText, ArabicTextNormalizer.normalize(matnText),
                null, null, collectionId, primaryNumber,
                intOrNull(raw, "page"), intOrNull(raw, "volume"),
                true, null, null, Instant.now()));
    }

    private void insertEditions(UUID hadithId, JsonNode raw) {
        JsonNode editions = raw.path("editions");
        if (!editions.isArray()) {
            return;
        }
        for (JsonNode ed : editions) {
            editionRepository.save(new HadithEdition(
                    UUID.randomUUID(), hadithId,
                    text(ed, "edition"), intOrNull(ed, "page"), intOrNull(ed, "volume")));
        }
    }

    /**
     * Цепь иснада (решения 1-3): парс {@code hadith}-поля → пусто → хадис БЕЗ цепи
     * (не ошибка); иначе один {@link Sanad}, реверс links (position 0 = сподвижник),
     * transmission_phrase = receivedVia (truncate 40), narrator id через
     * {@link AlminasaNarratorMapper#ensureNarrator}.
     */
    private void insertSanad(UUID hadithId, JsonNode raw, String fullTextAr) {
        ParsedIsnad parsed = AlminasaIsnadParser.parse(fullTextAr);
        if (parsed.links().isEmpty()) {
            return; // нет rawy-тегов → хадис без цепи (решение 1)
        }

        // narrators[]-entry лукап по id из rawJson (метаданные звеньев, решение 3)
        Map<String, JsonNode> entryById = new HashMap<>();
        JsonNode narratorsArr = raw.path("narrators");
        if (narratorsArr.isArray()) {
            for (JsonNode n : narratorsArr) {
                String id = text(n, "id");
                if (id != null) {
                    entryById.put(id, n);
                }
            }
        }

        // collector→companion → companion→collector: position 0 = сподвижник
        List<IsnadLink> chain = new ArrayList<>(parsed.links());
        Collections.reverse(chain);

        UUID sanadId = UUID.randomUUID();
        sanadRepository.save(new Sanad(
                sanadId, hadithId,
                null,   // chainGrade — не оцениваем
                null,   // compiledById — составитель не звено цепи
                null,   // compiledInBookId — мост к lib_books вне скоупа
                true,   // primaryChain
                buildSanadMetadata(parsed.collectorPhrase()),
                Instant.now()));

        // кэш externalId→UUID на один вызов: дубль рави в цепи → тот же UUID
        Map<String, UUID> narratorIdByExternal = new HashMap<>();
        for (int i = 0; i < chain.size(); i++) {
            IsnadLink link = chain.get(i);
            UUID narratorId = narratorIdByExternal.computeIfAbsent(
                    link.externalId(),
                    extId -> narratorMapper.ensureNarrator(
                            extId, entryById.get(extId), link.nameInText()));
            sanadRepository.saveNarratorLink(new SanadNarrator(
                    sanadId, i, narratorId, truncate(link.receivedVia(), TRANSMISSION_MAX_LEN)));
        }
    }

    private String buildSanadMetadata(String collectorPhrase) {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("source", SOURCE);
        if (collectorPhrase != null) {
            meta.put("collectorPhrase", collectorPhrase);
        }
        return writeJson(meta);
    }

    /**
     * Такхридж-crossrefs (решение 12 + Task 4): {@code raw_narrations} минус
     * собственный hadith_id; relation_type='TARIQ'; note = JSON-строка номеров
     * сиблинга из {@code narrations_numbers} (если есть); related_hadith_id NULL
     * (резолв позже).
     */
    private void insertCrossrefs(UUID hadithId, String externalId, JsonNode raw) {
        JsonNode rawNarrations = raw.path("raw_narrations");
        if (!rawNarrations.isArray()) {
            return;
        }
        Map<String, JsonNode> numbersById = new HashMap<>();
        JsonNode narrNumbers = raw.path("narrations_numbers");
        if (narrNumbers.isArray()) {
            for (JsonNode entry : narrNumbers) {
                String id = text(entry, "narration_id");
                if (id != null) {
                    numbersById.putIfAbsent(id, entry.path("number"));
                }
            }
        }
        for (JsonNode sibling : rawNarrations) {
            String relatedExternalId = sibling.asText(null);
            if (relatedExternalId == null || relatedExternalId.equals(externalId)) {
                continue; // self пропускаем (решение: raw_narrations содержит сам хадис)
            }
            String note = numbersNote(numbersById.get(relatedExternalId));
            crossrefRepository.save(new HadithCrossref(
                    UUID.randomUUID(), hadithId, relatedExternalId, null,
                    "TARIQ", note, Instant.now()));
        }
    }

    private String numbersNote(JsonNode numbers) {
        if (numbers == null || !numbers.isArray() || numbers.isEmpty()) {
            return null;
        }
        return writeJson(numbers);
    }

    /**
     * Рулинги = union двух источников + дедуп (решение 7):
     * (а) embedded {@code rulings[]} hadith-дока — строка на entry (source='embedded');
     * (б) rulings-доки по ВЕРХНЕМУ hadith_id — одна строка на док (source='index').
     * Дедуп по (ruler_name, ruling_text, book_name, page, volume), embedded приоритет.
     */
    private void insertRulings(UUID hadithId, String externalId, JsonNode raw) {
        List<RulingCandidate> candidates = new ArrayList<>();
        candidates.addAll(embeddedRulings(raw));
        candidates.addAll(indexRulings(externalId));

        for (RulingCandidate c : dedupRulings(candidates)) {
            rulingRepository.save(new HadithRuling(
                    UUID.randomUUID(), hadithId, c.rulerName(), c.rulerDeathYear(),
                    c.rulingText(), c.bookName(), c.page(), c.volume(),
                    c.metadata(), Instant.now()));
        }
    }

    /** embedded rulings[] hadith-дока → строка на entry (решение 7а). */
    private List<RulingCandidate> embeddedRulings(JsonNode raw) {
        List<RulingCandidate> result = new ArrayList<>();
        JsonNode arr = raw.path("rulings");
        if (!arr.isArray()) {
            return result;
        }
        for (JsonNode r : arr) {
            ObjectNode meta = objectMapper.createObjectNode();
            meta.put("source", "embedded");
            result.add(new RulingCandidate(
                    text(r, "ruler"), intOrNull(r, "ruler_dod"), text(r, "ruling"),
                    text(r, "book_name"), intOrNull(r, "page"), intOrNull(r, "volume"),
                    writeJson(meta)));
        }
        return result;
    }

    /**
     * rulings-доки по верхнему hadith_id (решение 7б): ОДНА строка на док (= один
     * учёный). ruler/dod — с верха; ruling_text = уникальные inner ruling join «؛ »;
     * book/page/volume — inner с hadith_id==текущий иначе первый inner; metadata =
     * {source:'index', relatedExternalId, narrations:[{id,page,volume}], narrationsType}.
     */
    private List<RulingCandidate> indexRulings(String externalId) {
        List<RulingCandidate> result = new ArrayList<>();
        for (AmRulingRow doc : rulingStagingDao.findByHadithId(externalId)) {
            JsonNode raw = parse(doc.rawJson(), null);
            JsonNode inner = raw.path("rulings");
            if (!inner.isArray() || inner.isEmpty()) {
                continue;
            }

            // уникальные ruling-тексты в порядке появления
            LinkedHashMap<String, Boolean> uniqueRulings = new LinkedHashMap<>();
            JsonNode chosen = inner.get(0);
            JsonNode current = null;
            ArrayNode narrations = objectMapper.createArrayNode();
            for (JsonNode entry : inner) {
                String rulingText = text(entry, "ruling");
                if (rulingText != null) {
                    uniqueRulings.putIfAbsent(rulingText, Boolean.TRUE);
                }
                if (externalId.equals(text(entry, "hadith_id"))) {
                    current = entry;
                }
                ObjectNode n = objectMapper.createObjectNode();
                n.put("id", text(entry, "hadith_id"));
                n.put("page", intOrNull(entry, "page"));
                n.put("volume", intOrNull(entry, "volume"));
                narrations.add(n);
            }
            JsonNode location = current != null ? current : chosen;

            ObjectNode meta = objectMapper.createObjectNode();
            meta.put("source", "index");
            meta.put("relatedExternalId", text(location, "hadith_id"));
            meta.set("narrations", narrations);
            if (doc.narrationsType() != null) {
                meta.put("narrationsType", doc.narrationsType());
            }

            result.add(new RulingCandidate(
                    doc.ruler(), doc.rulerDod(),
                    String.join("؛ ", uniqueRulings.keySet()),
                    text(location, "book_name"), intOrNull(location, "page"),
                    intOrNull(location, "volume"), writeJson(meta)));
        }
        return result;
    }

    /**
     * Дедуп по природному ключу (ruler_name, ruling_text, book_name, page, volume);
     * embedded-кандидаты идут первыми → приоритетны (package-private, unit-тестируем).
     */
    static List<RulingCandidate> dedupRulings(List<RulingCandidate> candidates) {
        LinkedHashMap<String, RulingCandidate> byKey = new LinkedHashMap<>();
        for (RulingCandidate c : candidates) {
            String key = c.rulerName() + "|" + c.rulingText() + "|" + c.bookName()
                    + "|" + c.page() + "|" + c.volume();
            byKey.putIfAbsent(key, c);
        }
        return new ArrayList<>(byKey.values());
    }

    /** Кандидат рулинга до дедупа (package-private под unit-тест дедупа). */
    record RulingCandidate(
            String rulerName, Integer rulerDeathYear, String rulingText,
            String bookName, Integer page, Integer volume, String metadata) {
    }

    /**
     * Шархи (решение 8): один rulings-док explanation → одна строка kind=SHARH;
     * text = join {@code hadith_explanation_array[].sharh} через «\n\n»;
     * author/book trim; author_death_year null; metadata={esId}.
     */
    private void insertExplanations(UUID hadithId, String externalId) {
        for (AmExplanationRow doc : explanationStagingDao.findByHadithId(externalId)) {
            JsonNode raw = parse(doc.rawJson(), null);
            JsonNode explanation = raw.path("explanation");

            List<String> segments = new ArrayList<>();
            JsonNode arr = explanation.path("hadith_explanation_array");
            if (arr.isArray()) {
                for (JsonNode seg : arr) {
                    String sharh = text(seg, "sharh");
                    if (sharh != null) {
                        segments.add(sharh);
                    }
                }
            }
            if (segments.isEmpty()) {
                continue;
            }

            ObjectNode meta = objectMapper.createObjectNode();
            meta.put("esId", doc.esId());

            explanationRepository.save(new HadithExplanation(
                    UUID.randomUUID(), hadithId, "SHARH",
                    trimOrNull(text(explanation, "explanation_book_name")),
                    trimOrNull(text(explanation, "explanation_book_author")),
                    null,
                    intOrNull(explanation, "explanation_page"),
                    intOrNull(explanation, "explanation_volume"),
                    String.join("\n\n", segments), writeJson(meta), Instant.now()));
        }
    }

    private String buildHadithMetadata(JsonNode raw) {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("source", SOURCE);
        JsonNode numbers = raw.path("number");
        if (numbers.isArray() && !numbers.isEmpty()) {
            meta.set("numbers", numbers);
        }
        JsonNode serialNode = raw.path("hadith_serial_id");
        if (serialNode.isNumber()) {
            meta.put("serial", serialNode.asLong());
        } else {
            String serial = text(raw, "hadith_serial_id");
            if (serial != null) {
                meta.put("serial", serial);
            }
        }
        return writeJson(meta);
    }

    // ── dry-run snapshot ──────────────────────────────────────────────────────

    private AlminasaDryRunResult buildDryRunResult(UUID hadithId) {
        Hadith h = hadithRepository.findById(hadithId).orElseThrow();
        String slug = h.collectionId() == null ? null
                : collectionRepository.findById(h.collectionId()).map(Collection::slug).orElse(null);

        List<Matn> matns = matnRepository.findByHadithId(hadithId);
        String matnPreview = matns.isEmpty() ? null : preview(matns.get(0).textAr());

        List<SanadLinkPreview> sanadPreview = buildSanadPreview(hadithId);

        return new AlminasaDryRunResult(
                hadithId, h.externalId(), slug, h.status(), h.hadithType(),
                h.primaryNumber(), h.chapterAr(), matnPreview, sanadPreview,
                editionRepository.findByHadithId(hadithId).size(),
                crossrefRepository.findByHadithId(hadithId).size(),
                rulingRepository.findByHadithId(hadithId).size(),
                explanationRepository.findByHadithId(hadithId).size());
    }

    private List<SanadLinkPreview> buildSanadPreview(UUID hadithId) {
        List<Sanad> sanads = sanadRepository.findByHadithId(hadithId);
        if (sanads.isEmpty()) {
            return List.of();
        }
        List<SanadNarrator> links = sanadRepository.findNarratorsBySanadId(sanads.get(0).id());
        List<UUID> narratorIds = links.stream().map(SanadNarrator::narratorId).distinct().toList();
        Map<UUID, Narrator> byId = new HashMap<>();
        for (Narrator n : narratorRepository.findByIds(narratorIds)) {
            byId.put(n.id(), n);
        }
        List<SanadLinkPreview> preview = new ArrayList<>(links.size());
        for (SanadNarrator link : links) {
            Narrator n = byId.get(link.narratorId());
            preview.add(new SanadLinkPreview(
                    link.position(),
                    n == null ? null : n.externalId(),
                    n == null ? null : n.nameAr(),
                    link.transmissionPhrase()));
        }
        return preview;
    }

    private static String preview(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= MATN_PREVIEW_LEN ? text : text.substring(0, MATN_PREVIEW_LEN);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private JsonNode parse(String rawJson, String hadithId) {
        try {
            return objectMapper.readTree(rawJson);
        } catch (JsonProcessingException e) {
            throw new AlminasaMappingException(
                    "Не удалось разобрать raw JSON хадиса: " + e.getMessage(), hadithId, null);
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            log.warn("Не удалось сериализовать metadata: {}", e.getMessage());
            return "{\"source\":\"" + SOURCE + "\"}";
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return s.isBlank() ? null : s;
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull() || !v.canConvertToInt()) {
            return null;
        }
        return v.asInt();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
