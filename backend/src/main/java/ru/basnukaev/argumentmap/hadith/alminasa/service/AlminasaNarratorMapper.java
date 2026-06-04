package ru.basnukaev.argumentmap.hadith.alminasa.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmNarratorRow;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmNarratorStagingDao;
import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.NarratorRelation;
import ru.basnukaev.argumentmap.hadith.domain.NarratorReliability;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRelationRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRepository;
import ru.basnukaev.argumentmap.hadith.service.ArabicTextNormalizer;

/**
 * Маппер рави alminasa: {@code am_staging_narrator} → {@code hd_narrators} +
 * {@code hd_narrator_relations} (план 3, Task 3, решения 4-5).
 *
 * <p>Идемпотентный upsert по природному ключу {@code (external_source='alminasa',
 * external_id=narratorId)}: find → update (сохраняя id/createdAt/счётчик
 * существующего) | save. Relations — delete-recreate per narrator.
 *
 * <p>{@code @Transactional} на public-методах; бин отделён от orchestration-бина
 * (план 3, решение 10 — self-invocation gotcha).
 */
@Service
public class AlminasaNarratorMapper {

    private static final Logger log = LoggerFactory.getLogger(AlminasaNarratorMapper.class);

    static final String SOURCE = "alminasa";

    /** Лимит varchar(120) на hd_narrators.tabaqa. */
    private static final int TABAQA_MAX_LEN = 120;

    /** «سنة 94» либо голое 1-4-значное число — best-effort хиджри-год (решение 5). */
    private static final Pattern HIJRI_SANA = Pattern.compile("سنة\\s*(\\d{1,4})");
    private static final Pattern HIJRI_BARE = Pattern.compile("\\b(\\d{1,4})\\b");

    /** Разбор записи сети передатчиков «имя - (N)»: имя может содержать дефисы, N в конце. */
    private static final Pattern RELATION_ENTRY = Pattern.compile("^(.*)-\\s*\\((\\d+)\\)\\s*$");

    private final AmNarratorStagingDao narratorStagingDao;
    private final NarratorRepository narratorRepository;
    private final NarratorRelationRepository relationRepository;
    private final ObjectMapper objectMapper;

    public AlminasaNarratorMapper(AmNarratorStagingDao narratorStagingDao,
                                  NarratorRepository narratorRepository,
                                  NarratorRelationRepository relationRepository,
                                  ObjectMapper objectMapper) {
        this.narratorStagingDao = narratorStagingDao;
        this.narratorRepository = narratorRepository;
        this.relationRepository = relationRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Маппит одну staging-строку рави в {@code hd_narrators} (upsert) +
     * пересоздаёт {@code hd_narrator_relations}.
     *
     * @return id строки {@code hd_narrators} (существующей или новой)
     */
    @Transactional
    public UUID mapNarrator(AmNarratorRow row) {
        JsonNode raw = parse(row.rawJson());
        String externalId = String.valueOf(row.narratorId());

        Optional<Narrator> existing = narratorRepository.findByExternalId(SOURCE, externalId);
        UUID id = existing.map(Narrator::id).orElseGet(UUID::randomUUID);

        String fullName = text(raw, "full_name");
        // full_name из row — авторитетнее (staging-колонка), но в raw то же значение
        if (fullName == null) {
            fullName = row.fullName();
        }
        String normalized = ArabicTextNormalizer.normalize(fullName);
        String gradeText = text(raw, "grade");
        String level = text(raw, "level");

        Narrator narrator = new Narrator(
                id,
                existing.map(Narrator::authorityId).orElse(null),
                fullName,
                normalized,
                text(raw, "nickname"),                 // kunya
                text(raw, "origin"),                   // laqab (нисба)
                hijriYear(text(raw, "born_on")),       // year_birth_hijri
                hijriYear(text(raw, "died_on")),       // year_death_hijri
                null,                                   // birthplace (нет отдельного поля)
                truncatePlace(text(raw, "died_in")),   // death_place
                truncatePlace(text(raw, "lived_in")),  // primary_residence
                reliabilityGrade(level, gradeText),
                null,                                   // reliability_comment (verbatim — в grade_text)
                existing.map(Narrator::transmittedCountCached).orElse(0),
                buildMetadata(raw),
                existing.map(Narrator::createdAt).orElseGet(Instant::now),
                SOURCE,
                externalId,
                truncate(level, TABAQA_MAX_LEN),        // tabaqa
                gradeText,                              // grade_text (дословно)
                text(raw, "born_on"),                   // born_on_text (проза)
                text(raw, "died_on")                    // died_on_text (проза)
        );

        if (existing.isPresent()) {
            narratorRepository.update(narrator);
        } else {
            narratorRepository.save(narrator);
        }

        recreateRelations(id, raw);
        return id;
    }

    /**
     * Резолв рави звена иснада (план 3, решение 3) для маппера хадисов:
     * <ol>
     *   <li>найден в {@code hd_narrators} по external_id → вернуть его id;</li>
     *   <li>есть в staging → полный {@link #mapNarrator};</li>
     *   <li>иначе stub из {@code narrators[]}-entry hadith-дока (full_name,
     *       is_companion→SAHABI / is_unknown→UNKNOWN / префикс grade, level→tabaqa);</li>
     *   <li>entry == null (тег без metadata И без staging) → stub из имени тега
     *       ({@code nameInText}), UNKNOWN, {@code metadata.stubFromTag=true}, WARN.</li>
     * </ol>
     *
     * @param externalId            id рави из тега (атрибут {@code id=})
     * @param hadithDocNarratorEntry соответствующий элемент {@code narrators[]} hadith-дока (nullable)
     * @param nameInText            содержимое тега рави (для stub из тега, шаг 4)
     */
    @Transactional
    public UUID ensureNarrator(String externalId, JsonNode hadithDocNarratorEntry, String nameInText) {
        Optional<Narrator> existing = narratorRepository.findByExternalId(SOURCE, externalId);
        if (existing.isPresent()) {
            return existing.get().id();
        }

        Optional<AmNarratorRow> staging = parseExternalId(externalId)
                .flatMap(narratorStagingDao::findById);
        if (staging.isPresent()) {
            return mapNarrator(staging.get());
        }

        if (hadithDocNarratorEntry != null && !hadithDocNarratorEntry.isNull()) {
            return stubFromEntry(externalId, hadithDocNarratorEntry);
        }

        return stubFromTag(externalId, nameInText);
    }

    /** Stub из {@code narrators[]}-entry hadith-дока: метаданные звена есть, staging нет. */
    private UUID stubFromEntry(String externalId, JsonNode entry) {
        String fullName = text(entry, "full_name");
        String level = text(entry, "level");
        String gradeText = text(entry, "grade");
        boolean isCompanion = entry.path("is_companion").asBoolean(false);
        boolean isUnknown = entry.path("is_unknown").asBoolean(false);

        String reliability;
        if (isCompanion || "صحابي".equals(level)) {
            reliability = NarratorReliability.SAHABI;
        } else if (isUnknown) {
            reliability = NarratorReliability.UNKNOWN;
        } else {
            reliability = reliabilityFromGradePrefix(gradeText);
        }

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("source", SOURCE);
        metadata.put("stub", true);
        if (entry.hasNonNull("hasCommentary")) {
            metadata.put("hasCommentary", entry.path("hasCommentary").asBoolean());
        }
        String reference = text(entry, "reference");
        if (reference != null) {
            metadata.put("reference", reference);
        }

        UUID id = UUID.randomUUID();
        narratorRepository.save(new Narrator(
                id, null, fullName, ArabicTextNormalizer.normalize(fullName),
                null, null, null, null, null, null, null,
                reliability, null, 0, writeJson(metadata), Instant.now(),
                SOURCE, externalId, truncate(level, TABAQA_MAX_LEN), gradeText, null, null
        ));
        return id;
    }

    /** Stub из имени тега: рави нет ни в staging, ни в narrators[] (решение 3, edge). */
    private UUID stubFromTag(String externalId, String nameInText) {
        String name = nameInText == null ? "" : nameInText.trim();
        log.warn("alminasa рави external_id={} отсутствует в staging и в narrators[] — "
                + "stub из имени тега «{}»", externalId, name);

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("source", SOURCE);
        metadata.put("stub", true);
        metadata.put("stubFromTag", true);

        UUID id = UUID.randomUUID();
        narratorRepository.save(new Narrator(
                id, null, name, ArabicTextNormalizer.normalize(name),
                null, null, null, null, null, null, null,
                NarratorReliability.UNKNOWN, null, 0, writeJson(metadata), Instant.now(),
                SOURCE, externalId, null, null, null, null
        ));
        return id;
    }

    /** Delete-recreate сети передатчиков из {@code top_students}/{@code top_scholars}. */
    private void recreateRelations(UUID narratorId, JsonNode raw) {
        relationRepository.deleteByNarratorId(narratorId);
        saveRelations(narratorId, raw.path("top_students"), "STUDENT");
        saveRelations(narratorId, raw.path("top_scholars"), "SCHOLAR");
    }

    private void saveRelations(UUID narratorId, JsonNode arr, String role) {
        if (!arr.isArray()) {
            return;
        }
        for (JsonNode el : arr) {
            String entry = el.asText(null);
            if (entry == null || entry.isBlank()) {
                continue;
            }
            ParsedRelation parsed = parseRelation(entry);
            relationRepository.save(new NarratorRelation(
                    UUID.randomUUID(), narratorId, null,
                    parsed.name(), role, parsed.cnt(), Instant.now()));
        }
    }

    /** «الزهري - (24)» → name=«الزهري», cnt=24; не распарсилось → имя=строка целиком, cnt=null. */
    static ParsedRelation parseRelation(String entry) {
        Matcher m = RELATION_ENTRY.matcher(entry);
        if (m.matches()) {
            String name = m.group(1).trim();
            Integer cnt = parseIntSafe(m.group(2));
            return new ParsedRelation(name, cnt);
        }
        return new ParsedRelation(entry.trim(), null);
    }

    /** Разобранная запись сети передатчиков. */
    record ParsedRelation(String name, Integer cnt) {
    }

    /**
     * Производный enum надёжности (решение 4): {@code level=='صحابي'} → SAHABI;
     * иначе по префиксу grade. Поле {@code is_unknown} в narrator-доке ОТСУТСТВУЕТ
     * (есть только в narrators[] hadith-дока) — здесь не используется.
     */
    static String reliabilityGrade(String level, String gradeText) {
        if ("صحابي".equals(level)) {
            return NarratorReliability.SAHABI;
        }
        return reliabilityFromGradePrefix(gradeText);
    }

    /** Префикс grade → enum: ثقة→THIQA, صدوق→SADUQ, مقبول→MAQBUL, ضعيف→DAIF, متروك→MATRUK; иначе UNKNOWN. */
    static String reliabilityFromGradePrefix(String gradeText) {
        if (gradeText == null) {
            return NarratorReliability.UNKNOWN;
        }
        String g = gradeText.trim();
        if (g.startsWith("ثقة")) {
            return NarratorReliability.THIQA;
        }
        if (g.startsWith("صدوق")) {
            return NarratorReliability.SADUQ;
        }
        if (g.startsWith("مقبول")) {
            return NarratorReliability.MAQBUL;
        }
        if (g.startsWith("ضعيف")) {
            return NarratorReliability.DAIF;
        }
        if (g.startsWith("متروك")) {
            return NarratorReliability.MATRUK;
        }
        return NarratorReliability.UNKNOWN;
    }

    /** Best-effort хиджри-год из прозы (решение 5): «سنة N» или голое 1-4-значное число; иначе null. */
    static Integer hijriYear(String prose) {
        if (prose == null || prose.isBlank()) {
            return null;
        }
        Matcher sana = HIJRI_SANA.matcher(prose);
        if (sana.find()) {
            return parseIntSafe(sana.group(1));
        }
        Matcher bare = HIJRI_BARE.matcher(prose);
        if (bare.find()) {
            return parseIntSafe(bare.group(1));
        }
        return null;
    }

    /** metadata jsonb: {source, extendedFullName, bookTitles[]} — только ненулевые поля. */
    private String buildMetadata(JsonNode raw) {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("source", SOURCE);
        String extendedFullName = text(raw, "extended_full_name");
        if (extendedFullName != null) {
            meta.put("extendedFullName", extendedFullName);
        }
        JsonNode bookTitles = raw.path("book_titles");
        if (bookTitles.isArray() && !bookTitles.isEmpty()) {
            ArrayNode titles = meta.putArray("bookTitles");
            for (JsonNode t : bookTitles) {
                String title = t.asText(null);
                if (title != null && !title.isBlank()) {
                    titles.add(title);
                }
            }
        }
        return writeJson(meta);
    }

    private JsonNode parse(String rawJson) {
        try {
            return objectMapper.readTree(rawJson);
        } catch (JsonProcessingException e) {
            throw new AlminasaMappingException(
                    "Не удалось разобрать raw JSON рави: " + e.getMessage(), null, null);
        }
    }

    private static Optional<Long> parseExternalId(String externalId) {
        try {
            return Optional.of(Long.parseLong(externalId.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            // сериализация собранного ObjectNode на практике не падает
            log.warn("Не удалось сериализовать metadata рави: {}", e.getMessage());
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

    /** died_in/lived_in → place varchar(120). */
    private static String truncatePlace(String place) {
        return truncate(place, 120);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static Integer parseIntSafe(String s) {
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
