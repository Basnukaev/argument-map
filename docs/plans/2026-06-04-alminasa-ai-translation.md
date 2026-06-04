# alminasa Hadith Ingestion — Plan 7: AI-перевод матна on-demand (ru/en)

> **Оркестрация:** OMC. ГЕЙТ: live-прогон БЕЗ AI-ключа невозможен — код +
> тесты со стабом `LlmClient`; live-проверка помечается «ждёт ключ»
> (handoff). Провайдер swappable — ADR-058, новых ADR не требуется.

**Goal:** кнопка «Перевести (ru/en)» на матне в Hadith Explorer →
`LlmClient` (ADR-058) → перевод персистится в `hd_matns.text_ru/text_en`
(колонки СУЩЕСТВУЮТ с Плана 1 схемы hd_matns) → последующие читатели
получают перевод без повторного LLM-вызова.

**Спека:** `docs/specs/2026-06-03-alminasa-hadith-source-design.md` §E
(«AI-перевод on-demand»).

## Дизайн-решения

1. **Endpoint:** `POST /api/v1/hadith/matns/{matnId}/translate` body
   `{"lang": "ru"|"en"}` (union-валидация) → 200
   `MatnTranslationResponse(matnId, lang, text, cached)`.
   Синхронный (LLM 5-15с — как бывший extract-isnad; фронт показывает
   лоадер с подписью). Скилл-чеклист new-rest-endpoint обязателен.
2. **Идемпотентность/кэш:** `text_{lang}` уже заполнен → 200 existing,
   `cached=true`, БЕЗ LLM-вызова. `?force=true` — регенерация, ADMIN-only
   (403 для остальных, через существующий AdminOnly-паттерн): перезапись
   курируемого перевода — админская операция.
   **Race двух одновременных translate (фикс критика, решение
   зафиксировано):** двойной LLM-вызов допустим для MVP — оба перевода
   валидны, перезапись идемпотентна, цена — один лишний вызов;
   atomic-claim (как AiEditService.tryClaimAiEditProcessing) — backlog.
3. **Permissions:** перевод — authenticated (любой залогиненный может
   триггернуть первый перевод: результат детерминированно полезен всем,
   мутация безопасна — заполнение NULL-поля); anonymous → 401.
4. **LLM недоступен (фикс критика — РЕАЛЬНЫЙ паттерн кодовой базы):**
   `LlmClient`-бин ВСЕГДА есть (ровно один impl, matchIfMissing);
   «не сконфигурирован» = `llmClient.isEnabled()==false` (sentinel-ключ).
   НЕ ObjectProvider. Pre-flight в контроллере: `!isEnabled()` → новый
   `MatnTranslationNotConfiguredException` → 503 `llm-not-configured`
   (НЕ реюзать AiEditNotConfiguredException — library-scoped имя) +
   регистрация хендлера в GlobalExceptionHandler. 404 — новый
   `MatnNotFoundException` (в кодовой базе НЕТ — создать+хендлер).
   `LlmApiException` — сверить существующий маппинг, при отсутствии →
   502. Edge: `text_ar` матна null/blank → 422 (guard ДО LLM-вызова).
   Retry: `@Retry(llmApi)` живёт на impl-методах complete() — наследуется
   автоматически, НЕ аннотировать сервис.
5. **Промпт:** system — переводчик хадисов (бережный к терминам:
   иснад-формулы не переводить буквально, салават сохранять ﷺ, академичный
   стиль); user — голый `text_ar` матна. Перевод = ТОЛЬКО матн (не
   full_text_ar с иснадом). Промпт-константы в сервисе, язык — параметром
   шаблона.
6. **Backend-слой:** `hadith/service/HadithTranslationService` (@Service):
   getMatn → cached-чек → LlmClient.complete → `MatnRepository.
   updateTranslation(matnId, lang, text)`. **Tx-границы (фикс критика,
   нормативно): сервис-метод `translate()` БЕЗ @Transactional** — иначе
   DB-коннект держится все 5-15с LLM-вызова (pool-slot впустую).
   Декомпозиция: findById (без tx) → guard'ы/кэш-чек → complete() (вне
   любого tx) → updateTranslation (короткий @Transactional НА
   РЕПО-методе либо отдельном тонком персист-методе). Паттерн зеркалит
   AiEditService.enhance (без @Transactional). updateTranslation — ДВА
   отдельных UPDATE-стейтмента по lang (не один с условными колонками —
   иначе занулится вторая).
7. **Frontend:** в HadithDetailPage у primary-матна (и в MatnVariations у
   каждой вариации — если дёшево; иначе только primary) кнопки «RU»/«EN»:
   нет перевода → POST + лоадер «Перевод (5-15 сек)…» → текст под матном
   (dir=ltr/auto); есть перевод (textRu/textEn из detail) → сразу текст
   + кнопка скрыта/неактивна. 503 → тост «AI-провайдер не настроен».
   MatnDto УЖЕ несёт textRu/textEn (сверено критикой) — DTO не трогать.
   ВНИМАНИЕ: MatnVariations рендерит только textRu — добавить
   textEn-рендер. Миграций НЕТ (колонки существуют — подтверждено).
8. **Тесты:** unit/IT со СТАБОМ: `@TestConfiguration` бин LlmClient
   (фиксированный ответ) → happy 200 + персист в БД + `cached=false`;
   повторный вызов → `cached=true` + стаб НЕ вызван повторно (counter в
   стабе); force без ADMIN → 403; force ADMIN → повторный вызов стаба;
   без бина (отдельный IT-класс без TestConfiguration) → 503; 401/404;
   невалидный lang → 400. Vitest: кнопка → лоадер → текст; 503-тост;
   уже-переведённый рендер.

## Tasks

- [x] **T1 backend**: MatnRepository.updateTranslation + Service + DTO +
  endpoint + handlers + IT (≥8 кейсов выше) + api-contract.md.
- [x] **T2 regen types** + tsc.
- [x] **T3 frontend**: кнопки/лоадер/рендер + i18n + vitest.
- [x] **T4 верификация**: verify + vitest + tsc. Live-прогон — «ждёт
  ключ» (handoff: команда запуска с --ai.provider/ключом уже в
  CLAUDE.md/progress).
- [x] **T5 review**: независимый review → fixes → roadmap 49.C финал.

## Definition of Done

1. IT со стабом зелёные (включая cached/force/503), verify полный.
2. UI-кнопки живые против стаба недоступны (без ключа) — UI-проверка
   ограничена vitest + ручная отметка «ждёт ключ» в handoff.
3. api-contract + types.ts в синхроне; review 0 Critical/Important.
