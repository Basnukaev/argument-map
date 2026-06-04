# alminasa Hadith Ingestion — Plan 6: Hadith Explorer на alminasa-данных

> **Оркестрация:** OMC. ГЕЙТ: вкладки علل (иляль) / غريب (гариб) — контракты
> ES-индексов НЕ сняты (нет HAR) → НЕ реализуются и НЕ выдумываются;
> фиксируются как «ждёт HAR от Абдулы». Всё остальное — в скоупе.

**Goal:** фронт Hadith Explorer раскрывает богатые alminasa-данные из `hd_*`:
тип хадиса, глава, **кликабельный иснад** (рендер `full_text_ar`), печатные
издания, вердикты учёных (rulings), шархи, такхридж/طرق (crossrefs), у рави —
табака/джарх-та'диль/сеть передатчиков. Вместо прежнего sunnah-сэмпла.

**Спека:** `docs/specs/2026-06-03-alminasa-hadith-source-design.md` §E.
**База:** разведка API (карта ниже выверена живым кодом), Планы 1-5.

## Карта изменений backend (ТОЛЬКО web-слой wiring; домен/репозитории/
## миграции ГОТОВЫ Планами 1-3 — фикс M1 критика: это тонкий маппинг-слой)

Существующие endpoints НЕ переименовываются — расширяются DTO:

1. **`HadithDetailResponse` +7 полей**: `hadithType`, `chapterAr`,
   `subChapterAr`, `fullTextAr`, `editions: List<EditionDto>`,
   `rulings: List<RulingDto>`, `explanations: List<ExplanationDto>`,
   `crossrefs: List<CrossrefDto>` (такхридж — В СКОУПЕ, пользовательский
   запрос). `HadithController.getDetail` грузит 4 репозитория Плана 1
   (`findByHadithId` — по одному запросу, single-detail: N+1 нет).
   **Шарх inline (фикс M4, решение зафиксировано):** 59KB × few docs в
   detail-ответе приемлемо (collapsible UI + SWR-кэш; canonical 146-1 —
   один шарх). Отсечка/lazy-endpoint — ТОЛЬКО если живой прогон покажет
   многомегабайтные ответы (backlog-note, не сейчас).
2. **`NarratorResponse` +6 полей**: `tabaqa`, `gradeText`, `bornOnText`,
   `diedOnText`, `deathPlace` (поле домена ЕСТЬ, в DTO не было),
   `relations: List<NarratorRelationDto>` — ТОЛЬКО в getOne (detail);
   list-эндпоинт relations не строит (null) — без N+1; `toResponse`
   list-варианта НЕ трогать.
3. **`SanadGraphResponse.NarratorData` +3 поля**: `tabaqa`, `gradeText`,
   `externalId` (для клик-резолва иснада на фронте). ВНИМАНИЕ (M1):
   расширение record требует правки ОБОИХ конструкторов —
   `narratorData()` (SanadGraphService:214) И синтетического
   `prophetNode()` (:206, 16-арг) — иначе compile error; плюс
   hand-written фронтовый тип `apps/hadith/types.ts:21-39` (он НЕ
   генерится). Сеть передатчиков в узлы графа НЕ кладём.
4. Новые DTO: `EditionDto`; `RulingDto` (rulerName, rulerDeathYear,
   rulingText, bookName, page, volume, **source** ('embedded'|'index' из
   metadata, фикс M2), **relatedExternalId** (nullable, из metadata) —
   UI обязан различать «вердикт на этот хадис» от «вердикта на
   параллельную передачу»); `ExplanationDto` (kind, bookName, author,
   page, volume, text); `CrossrefDto` (relatedExternalId,
   relatedHadithId nullable, note); `NarratorRelationDto`
   (relatedNarratorId nullable, relatedName, role, cnt).
5. `NarratorRelationRepository.findByNarratorId` есть; батч-метод НЕ нужен
   (relations только в narrator-detail).
6. IT: расширить `HadithControllerIT`/`NarratorControllerIT` — detail
   alminasa-хадиса возвращает новые поля (фикстура через репозитории),
   пустые списки для хадиса без сателлитов (legacy/seeded рендер не
   ломается); sanad-graph отдаёт externalId; ruling source-поле.
7. api-contract.md: обновить 3 секции (detail :3411 / narrator /
   sanad-graph :3418).

## Карта изменений frontend

`frontend/src/apps/hadith/` (структура из разведки: pages + components):

1. **HadithDetailPage**:
   - бейдж `hadithType` (مرفوع/موقوف/…) + `chapterAr`/`subChapterAr` в
     шапке (RTL, `hasArabicScript`);
   - **Архитектура клик-резолва (фикс C2, нормативно):** sanad-graph
     фетч ПОДНИМАЕТСЯ в HadithDetailPage (один фетч; `SanadGraph`
     рефакторится на controlled-режим — graph и `onNarratorSelect`
     пропсами; самофетч остаётся фолбэком для других экранов).
     Страница строит `Map<externalId, NarratorData>` из graph.nodes и
     владеет selected-state ОДНОГО NarratorPanel (текст-клики и
     граф-клики открывают одну панель — двух конкурирующих панелей нет).
     Клик из текста → NarratorData ИЗ УЖЕ загруженного графа, БЕЗ
     доп. фетча (Interpretation B критика). Пока graph грузится — рави в
     тексте рендерятся НЕ-кликабельно (guard), по загрузке становятся
     кликабельными;
   - **секция «Иснад (текст)»**: рендер `fullTextAr` — util
     `parseIsnadHtml.ts` (НЕ dangerouslySetInnerHTML). **Контракт (фикс
     C3):** токенизация ТОЛЬКО по `<a class=rawy id=N>ИМЯ</a>` (атрибуты
     без кавычек — зеркало backend-regex AlminasaIsnadParser:29) и
     `<a class=matn>…</a>` (БЕЗ id — стилевое выделение матна, НИКОГДА
     не кликабелен, не входит в rawy-карту); литеральный текст между
     тегами (включая `"`, `،`, пробелы) — plain-спаны как есть; хадис
     без rawy-тегов → секция рендерится целиком не-интерактивно (это
     ожидаемый класс данных, не ошибка); externalId нет в карте →
     не-кликабельный span (stub-рави без цепи). Unit-кейсы: реальный
     146-1 из фикстуры, пустая строка, без тегов, matn-only, имя с
     амперсандом/`<` (не ломается);
   - **секция «Вердикты» (rulings)**: список ruler + (ум. N г.х.) + текст
     + книга/страница; `source='index'` с relatedExternalId → подпись
     «на параллельную передачу {id}» (фикс M2);
   - **секция «Шарх»**: explanations kind=SHARH, collapsible (текст до
     59KB!), book/author в заголовке; kind=ILAL/GHARIB пока не приходят
     (гейт) — рендер generic по kind, ничего не выдумывать;
   - **секция «Такхридж»**: crossrefs — «передаётся в N местах»;
     resolved (`relatedHadithId != null`) → линк на detail сиблинга,
     unresolved → текст external id + note-номера;
   - **«Издания»**: компактный список editions.
   - Все секции graceful при пустых данных (legacy/без сателлитов —
     секция скрыта).
2. **NarratorPanel / NarratorDetailPage (фикс M3, нормативно):** панель
   СЕЙЧАС рендерит `generation` и `reliabilityComment` — у alminasa-рави
   ОБА null (generation живёт в metadata, которое alminasa-маппер не
   пишет; reliabilityComment маппер ставит null). Обязательная проводка:
   поле «поколение» = `tabaqa` с фолбэком `generation`; блок verbatim
   джарх = `gradeText` с фолбэком `reliabilityComment`. Плюс born/died
   проза (bornOnText/diedOnText), deathPlace; **сеть передатчиков**:
   relations STUDENT/SCHOLAR списками с cnt, resolved → линк на рави.
   Stub-рави (`metadata.stubFromTag`) рендерятся именем-only — null-гарды
   панели уже есть, это ожидаемо.
3. **HadithListPage**: фильтр по сборнику уже есть (collectionId) —
   проверить, что 12 alminasa-коллекций появляются в фильтре
   (CollectionResponse без изменений); бейдж типа в карточке списка НЕ
   добавляем (list-DTO не расширяем — экономия; тип виден в detail).
4. **Вкладки علل/غريب**: НЕ реализуются. В UI НЕ показывать пустые табы.
   Зафиксировано «ждёт HAR» (handoff).
5. i18n `hadith.detail.*` новые ключи ru+ar; vitest: parseIsnadHtml unit,
   detail-секции рендер с MSW-фикстурой (alminasa-подобный ответ),
   клик-резолв рави, graceful-пустота.

## Tasks

- [x] **T1 backend** (коммит 1): DTO-расширения + 5 новых DTO +
  контроллеры + SanadGraphService externalId/tabaqa/gradeText + IT +
  api-contract.md.
- [x] **T2 regen** (коммит 2): backend up → generate-api → tsc.
- [x] **T3 frontend** (коммит 3): parseIsnadHtml + секции
  HadithDetailPage + NarratorPanel/Detail + i18n + тесты.
- [x] **T4 верификация**: verify + vitest + tsc; playwright headless по
  живым данным (дев-краул, как План 5 T4, совместить прогон с ним если
  Планы идут подряд) — скриншот detail с кликабельным иснадом; очистка
  dev-данных (SQL Плана 5 T4).
- [x] **T5 review**: независимый review → fixes → roadmap.

## Definition of Done

1. Detail хадиса 146-1 (dev-краул; требует доступности alminasa из env —
   иначе сидировать staging фикстурой и импортировать) показывает: тип,
   главу, кликабельный иснад (клик открывает карточку рави с данными ИЗ
   графа, без доп. фетча), **≥1 вердикт (البخاري) с учёным + годом смерти
   + provenance-подписью** (фикс C1: фикстура несёт 1 embedded-вердикт;
   второй зависит от rulings-индекса в краул-объёме — числом не гейтим),
   шарх (collapsible), такхридж, 2 издания.
2. У рави: табака (tabaqa, не пустой generation) + verbatim джарх
   (gradeText) + сеть передатчиков.
3. علل/غريب отсутствуют в UI; зафиксированы «ждёт HAR» в handoff.
4. verify + vitest + tsc зелёные; review 0 Critical/Important.
5. Напоминание ревью: клик-резолв в React Flow проверить руками в
   браузере (playwright headless не полностью повторяет семантику).
