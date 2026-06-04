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
   (403 для остальных): перезапись курируемого перевода — админская
   операция.
3. **Permissions:** перевод — authenticated (любой залогиненный может
   триггернуть первый перевод: результат детерминированно полезен всем,
   мутация безопасна — заполнение NULL-поля); anonymous → 401.
4. **LLM недоступен** (ai.provider не задан / бина нет):
   `ObjectProvider<LlmClient>` пуст → 503 `llm-not-configured` (паттерн
   ai-edit). 404 `matn-not-found`. LlmApiException → 502
   `llm-upstream-error` (или существующий маппинг — сверить с
   GlobalExceptionHandler).
5. **Промпт:** system — переводчик хадисов (бережный к терминам:
   иснад-формулы не переводить буквально, салават сохранять ﷺ, академичный
   стиль); user — голый `text_ar` матна. Перевод = ТОЛЬКО матн (не
   full_text_ar с иснадом). Промпт-константы в сервисе, язык — параметром
   шаблона.
6. **Backend-слой:** `hadith/service/HadithTranslationService` (@Service):
   getMatn → cached-чек → LlmClient.complete → `MatnRepository.
   updateTranslation(matnId, lang, text)` (новый UPDATE-метод; только
   text_ru ИЛИ text_en по lang). @Transactional вокруг персиста, НЕ вокруг
   LLM-вызова (долгий внешний вызов вне транзакции: fetch→complete→tx
   update).
7. **Frontend:** в HadithDetailPage у primary-матна (и в MatnVariations у
   каждой вариации — если дёшево; иначе только primary) кнопки «RU»/«EN»:
   нет перевода → POST + лоадер «Перевод (5-15 сек)…» → текст под матном
   (dir=ltr/auto); есть перевод (textRu/textEn из detail) → сразу текст
   + кнопка скрыта/неактивна. 503 → тост «AI-провайдер не настроен».
   MatnDto уже несёт textRu/textEn? — сверить; если нет, добавить в DTO.
8. **Тесты:** unit/IT со СТАБОМ: `@TestConfiguration` бин LlmClient
   (фиксированный ответ) → happy 200 + персист в БД + `cached=false`;
   повторный вызов → `cached=true` + стаб НЕ вызван повторно (counter в
   стабе); force без ADMIN → 403; force ADMIN → повторный вызов стаба;
   без бина (отдельный IT-класс без TestConfiguration) → 503; 401/404;
   невалидный lang → 400. Vitest: кнопка → лоадер → текст; 503-тост;
   уже-переведённый рендер.

## Tasks

- [ ] **T1 backend**: MatnRepository.updateTranslation + Service + DTO +
  endpoint + handlers + IT (≥8 кейсов выше) + api-contract.md.
- [ ] **T2 regen types** + tsc.
- [ ] **T3 frontend**: кнопки/лоадер/рендер + i18n + vitest.
- [ ] **T4 верификация**: verify + vitest + tsc. Live-прогон — «ждёт
  ключ» (handoff: команда запуска с --ai.provider/ключом уже в
  CLAUDE.md/progress).
- [ ] **T5 review**: независимый review → fixes → roadmap 49.C финал.

## Definition of Done

1. IT со стабом зелёные (включая cached/force/503), verify полный.
2. UI-кнопки живые против стаба недоступны (без ключа) — UI-проверка
   ограничена vitest + ручная отметка «ждёт ключ» в handoff.
3. api-contract + types.ts в синхроне; review 0 Critical/Important.
