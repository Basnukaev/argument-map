package ru.basnukaev.argumentmap.hadith.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ru.basnukaev.argumentmap.ai.LlmClient;
import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.exception.AdminOnlyException;
import ru.basnukaev.argumentmap.hadith.domain.Matn;
import ru.basnukaev.argumentmap.hadith.repository.MatnRepository;
import ru.basnukaev.argumentmap.hadith.web.InvalidMatnTextException;
import ru.basnukaev.argumentmap.hadith.web.MatnNotFoundException;
import ru.basnukaev.argumentmap.hadith.web.MatnTranslationNotConfiguredException;
import ru.basnukaev.argumentmap.hadith.web.dto.MatnTranslationResponse;

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

    private final MatnRepository matnRepository;
    private final LlmClient llmClient;

    public HadithTranslationService(MatnRepository matnRepository, LlmClient llmClient) {
        this.matnRepository = matnRepository;
        this.llmClient = llmClient;
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

        matnRepository.updateTranslation(matnId, lang, translated);
        log.info("AI-перевод матна {} на {} ({} симв.)", matnId, lang, translated.length());

        return new MatnTranslationResponse(matnId, lang, translated, false);
    }
}
