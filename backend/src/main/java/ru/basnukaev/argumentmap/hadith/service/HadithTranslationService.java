package ru.basnukaev.argumentmap.hadith.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.ai.LlmApiException;
import ru.basnukaev.argumentmap.ai.LlmClient;
import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.exception.AdminOnlyException;
import ru.basnukaev.argumentmap.hadith.curation.domain.FieldOverride;
import ru.basnukaev.argumentmap.hadith.curation.domain.OverrideEntity;
import ru.basnukaev.argumentmap.hadith.curation.repository.OverrideRepository;
import ru.basnukaev.argumentmap.hadith.domain.Matn;
import ru.basnukaev.argumentmap.hadith.repository.MatnRepository;
import ru.basnukaev.argumentmap.hadith.web.InvalidMatnTextException;
import ru.basnukaev.argumentmap.hadith.web.MatnNotFoundException;
import ru.basnukaev.argumentmap.hadith.web.MatnTranslationNotConfiguredException;
import ru.basnukaev.argumentmap.hadith.web.dto.MatnTranslationResponse;
import ru.basnukaev.argumentmap.service.AuditLogService;

/**
 * AI-перевод текста матна (text_ar) на ru/en через {@link LlmClient}
 * (ADR-058) с персистом в hd_matns.text_ru/text_en (План 7).
 *
 * <p>Цепочка translate(): findById → 404 guard → text_ar null/blank
 * guard (422) → кэш-чек (уже переведён и !force → existing, cached=true)
 * → force-only-ADMIN guard (403) → isEnabled guard (503) →
 * {@link LlmClient#complete} (5-15с) → {@link MatnRepository#updateTranslation}
 * → MatnTranslationResponse(cached=false).
 *
 * <p><b>БЕЗ @Transactional на методе (нормативно, План 7 решение 6):</b>
 * иначе DB-коннект держался бы все 5-15с LLM-вызова, впустую занимая
 * pool-slot. Декомпозиция: findById (без tx) → guard'ы/кэш-чек →
 * complete() (вне любого tx) → updateTranslation (короткий tx на
 * репо-уровне). Паттерн зеркалит {@code AiEditService.enhance}.
 *
 * <p><b>Race trade-off (MVP, План 7 решение 2):</b> два одновременных
 * translate на один матн допускают двойной LLM-вызов — atomic-claim
 * (как {@code AiEditService.tryClaimAiEditProcessing}) сознательно НЕ
 * делаем. Оба перевода валидны, перезапись идемпотентна (последний
 * выигрывает), цена гонки — один лишний платный вызов. Atomic-claim —
 * backlog.
 *
 * <p>Retry: {@code @Retry(llmApi)} живёт на impl-методах
 * {@code complete()} и наследуется автоматически — сервис НЕ
 * аннотируем (двухаргументный {@code complete(system, user)} проходит
 * через Spring-прокси).
 */
@Service
public class HadithTranslationService {

    private static final Logger log = LoggerFactory.getLogger(HadithTranslationService.class);

    private static final String SYSTEM_PROMPT_RU = """
            Ты — профессиональный переводчик хадисов Пророка Мухаммада ﷺ с \
            арабского на русский язык. Переводи бережно и академично:
            - точно передавай смысл матна, сохраняя исламскую терминологию;
            - иснад-формулы передачи («حدثنا», «أخبرنا», «عن») если встречаются — \
            переводи естественно, не буквально-калькой;
            - салават «ﷺ» (صلى الله عليه وسلم) после имени Пророка сохраняй как \
            символ ﷺ;
            - не добавляй пояснений, комментариев или огласовок от себя — только \
            перевод текста.
            Верни ТОЛЬКО перевод, без преамбул и кавычек.""";

    private static final String SYSTEM_PROMPT_EN = """
            You are a professional translator of the hadith of Prophet \
            Muhammad ﷺ from Arabic into English. Translate carefully and \
            academically:
            - convey the meaning of the matn precisely, preserving Islamic \
            terminology;
            - transmission formulas of the isnad ("حدثنا", "أخبرنا", "عن") if \
            present — render naturally, not as a literal calque;
            - keep the salawat "ﷺ" (صلى الله عليه وسلم) after the Prophet's name \
            as the symbol ﷺ;
            - do not add explanations, commentary or vocalization of your own — \
            only the translation of the text.
            Return ONLY the translation, without preambles or quotes.""";

    private static final String AUDIT_ENTITY = "HD_FIELD_OVERRIDE";

    private final MatnRepository matnRepository;
    private final LlmClient llmClient;
    private final OverrideRepository overrideRepository;
    private final AuditLogService auditLogService;

    public HadithTranslationService(MatnRepository matnRepository, LlmClient llmClient,
                                    OverrideRepository overrideRepository,
                                    AuditLogService auditLogService) {
        this.matnRepository = matnRepository;
        this.llmClient = llmClient;
        this.overrideRepository = overrideRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Переводит text_ar матна на {@code lang} (ru|en), персистит и
     * возвращает результат. См. Javadoc класса по tx-границам и race.
     *
     * @param matnId матн
     * @param lang   'ru' либо 'en'
     * @param force  true — перезапись существующего перевода (ADMIN-only)
     * @param userId текущий пользователь (для 403-detail при force)
     * @param role   роль текущего пользователя (UserRole.*)
     * @return перевод + флаг cached
     * @throws MatnNotFoundException               404 — матн не найден
     * @throws InvalidMatnTextException            422 — text_ar пустой
     * @throws AdminOnlyException                  403 — force без ADMIN
     * @throws MatnTranslationNotConfiguredException 503 — LLM disabled
     */
    public MatnTranslationResponse translate(UUID matnId, String lang,
                                             boolean force, UUID userId, String role) {
        Matn matn = matnRepository.findById(matnId)
                .orElseThrow(() -> new MatnNotFoundException(matnId));

        if (matn.textAr() == null || matn.textAr().isBlank()) {
            throw new InvalidMatnTextException(matnId);
        }

        String existing = "ru".equals(lang) ? matn.textRu() : matn.textEn();
        boolean hasCached = existing != null && !existing.isBlank();
        if (hasCached && !force) {
            return new MatnTranslationResponse(matnId, lang, existing, true);
        }

        // force = перезапись курируемого перевода → admin-операция (План 7
        // решение 2). Без force первый перевод доступен любому залогиненному.
        if (force && !UserRole.ADMIN.equals(role)) {
            throw new AdminOnlyException(userId);
        }

        if (!llmClient.isEnabled()) {
            throw new MatnTranslationNotConfiguredException();
        }

        // LLM-вызов вне любой транзакции (см. Javadoc класса).
        String systemPrompt = "ru".equals(lang) ? SYSTEM_PROMPT_RU : SYSTEM_PROMPT_EN;
        String translated = llmClient.complete(systemPrompt, matn.textAr()).trim();
        if (translated.isBlank()) {
            // пустой ответ модели — upstream-проблема: не персистим пустую
            // строку (она бы навечно прошла cached-чек как «нет перевода»)
            throw new LlmApiException("LLM вернул пустой перевод", 502);
        }

        matnRepository.updateTranslation(matnId, lang, translated);
        log.info("AI-перевод матна {} на {} ({} симв.)", matnId, lang, translated.length());

        return new MatnTranslationResponse(matnId, lang, translated, false);
    }

    /**
     * Ручная правка сохранённого перевода матна — ADMIN перезаписывает перевод
     * БЕЗ вызова LLM (правка, не генерация). Правка живёт в overlay-таблице
     * {@code hd_field_overrides} (ADR-065 Фаза 6), НЕ в колонке {@code hd_matns},
     * потому переживает delete-recreate реимпорта alminasa (P0-1a-страховка
     * снята). Цепочка: ADMIN-guard (403) → findById (404) → text guard (422) →
     * upsert override + audit (одна транзакция).
     *
     * <p><b>Ключ override (§10 вопрос 2):</b> для PRIMARY-матна ключуем по
     * СТАБИЛЬНОМУ {@code (entity_id=hadith_id, field_name=primary_text_ru/en)} —
     * matn.id меняется на реимпорте, hadith_id нет. Для не-primary матна —
     * по {@code (entity_id=matn.id, field_name=text_ru/en)} (per-variation
     * путь Фазы 5; на реимпорте такой перевод не переживёт, но это не
     * накопленный C9-кейс). На ЧТЕНИИ перевод накладывается
     * {@code OverrideApplyService.applyMatns}.
     *
     * <p>Пишем напрямую через {@link OverrideRepository} (не через generic
     * {@code CurationOverrideService.upsert}), т.к. синтетический primary-ключ
     * {@code entity_id=hadith_id} не проходит generic-проверку существования
     * строки {@code hd_matns} по id (hadith_id — не matn.id). Аудит дублируем
     * вручную тем же {@link AuditLogService} (ADR-043).
     *
     * <p>Возвращает {@code cached=true}: текст в ответе — сохранённое
     * значение, не свежий LLM-результат. Семантика поля та же что у
     * уже-закэшированного перевода в {@link #translate}.
     *
     * @param matnId матн
     * @param lang   'ru' либо 'en'
     * @param text   новый перевод
     * @param userId текущий пользователь (для 403-detail + edited_by)
     * @param role   роль текущего пользователя (UserRole.*)
     * @return обновлённый перевод, {@code cached=true}
     * @throws AdminOnlyException        403 — не ADMIN
     * @throws MatnNotFoundException     404 — матн не найден
     * @throws InvalidMatnTextException  422 — text пустой после trim
     */
    @Transactional
    public MatnTranslationResponse editTranslation(UUID matnId, String lang,
                                                   String text, UUID userId, String role) {
        // Правка курируемого перевода — admin-операция (как force в translate).
        if (!UserRole.ADMIN.equals(role)) {
            throw new AdminOnlyException(userId);
        }

        Matn matn = matnRepository.findById(matnId)
                .orElseThrow(() -> new MatnNotFoundException(matnId));

        // @NotBlank ловит null/blank на уровне @Valid (400), но trim тут даёт
        // защиту от строки из одних пробелов и нормализует хвостовые пробелы.
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isBlank()) {
            throw new InvalidMatnTextException(matnId);
        }

        // primary → стабильный hadith-keyed синтетический ключ; иначе per-matn.
        UUID entityId = matn.isPrimary() ? matn.hadithId() : matnId;
        String fieldName = fieldNameFor(matn.isPrimary(), lang);
        upsertTranslationOverride(entityId, fieldName, trimmed, userId);

        log.info("Правка перевода матна {} (hadith {}, primary={}) на {} → overlay {} ({} симв.)",
                matnId, matn.hadithId(), matn.isPrimary(), lang, fieldName, trimmed.length());

        return new MatnTranslationResponse(matnId, lang, trimmed, true);
    }

    /** Синтетический primary-ключ для primary-матна, иначе реальная text-колонка. */
    private static String fieldNameFor(boolean isPrimary, String lang) {
        if (isPrimary) {
            return "ru".equals(lang) ? FieldOverride.PRIMARY_TEXT_RU : FieldOverride.PRIMARY_TEXT_EN;
        }
        return "ru".equals(lang) ? "text_ru" : "text_en";
    }

    /**
     * Upsert override перевода + audit_log в той же транзакции (ADR-043
     * consistency). Override всегда на {@code hd_matns}; {@code edited_by}
     * для audit — текущий ADMIN.
     */
    private void upsertTranslationOverride(UUID entityId, String fieldName,
                                           String text, UUID userId) {
        FieldOverride existing = overrideRepository
                .findOne(OverrideEntity.HD_MATNS, entityId, fieldName).orElse(null);
        FieldOverride toSave = new FieldOverride(
                existing != null ? existing.id() : UUID.randomUUID(),
                OverrideEntity.HD_MATNS.tableName(), entityId, fieldName,
                text, false, false, userId, Instant.now(), null);
        FieldOverride saved = overrideRepository.upsert(toSave);

        if (existing == null) {
            auditLogService.logCreate(AUDIT_ENTITY, saved.id(),
                    OverrideEntity.HD_MATNS.tableName(), entityId, userId,
                    Map.of("value", text, "field", fieldName));
        } else {
            Map<String, AuditLogService.FieldDiff> diff = new LinkedHashMap<>();
            diff.put("value", new AuditLogService.FieldDiff(existing.overrideValue(), text));
            auditLogService.logUpdate(AUDIT_ENTITY, saved.id(),
                    OverrideEntity.HD_MATNS.tableName(), entityId, userId, diff);
        }
    }
}
