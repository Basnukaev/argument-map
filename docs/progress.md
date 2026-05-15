# Журнал работы

Последние сессии. Новые записи - **сверху**

Формат записи описан в `docs/doc-hygiene.md` Принцип 5

**Архив:**
- Сессии 0-21: [`docs/archive/progress-sessions-1-21.md`](archive/progress-sessions-1-21.md)
- Сессии 22-29: [`docs/archive/progress-sessions-22-29.md`](archive/progress-sessions-22-29.md)

---

## 2026-05-15 - Сессия 33 (frontend) - полная RTL/i18n локализация

Пользователь дал детальный план фикса RTL/LTR + bidi для двуязычного
интерфейса (RU/AR): 10 шагов от единого модуля определения арабского
скрипта до документации. По ходу сессии расширилось до полной i18n-
локализации - все хардкод-русские строки в видимом UI заменены на
ключи из словаря.

### Архитектурные артефакты (durable)

- **`frontend/src/shared/i18n/`** - расширен новыми primitives:
  - `script.ts` - единый `hasArabicScript` (Unicode blocks Arabic/
    Supplement/Extended-A/Presentation Forms). Inline regex'ы
    `/[؀-ۿ]/` запрещены - заменены на импорт
  - `useFormatDate.ts` - локаль-aware Intl.DateTimeFormat (ru-RU/ar)
    с стилями `full`/`short`. Стабилен через `useCallback([locale])`
  - `useNumberFormat.ts` - локаль-aware Intl.NumberFormat
  - `dictionary.ts` - расширен с ~22 до ~280 ключей в 15+
    namespace'ах. DictKey union literal type даёт compile-time safety

- **`frontend/docs/i18n-guide.md`** - canonical reference ~280 строк
  для будущих сессий: 3 понятия которые нельзя путать (локаль UI /
  язык контента / направление текста), алгоритмы добавления UI/layout/
  иконки/контента, mixed-content через `<bdi>`, форматирование, что
  зеркалится / не зеркалится, чек-лист перед PR, 8 пар анти-паттернов
  ❌ vs ✅. Cross-link из `frontend/CLAUDE.md` и `coding-standards.md`

- **Token refactor**: `STATUS_TOKENS`, `NODE_TYPE_TOKENS`,
  `EDGE_TYPE_TOKENS`, `NODE_TYPE_META`, `EDGE_TYPE_META` - поле
  `label/hint: string` → `labelKey/hintKey: DictKey`. Удалён
  `NODE_TYPE_LABEL`. `getContextualEdgeLabel` → `getContextualEdgeLabelKey`.
  Один контракт «токен описывает визуал, переводы в словаре»

- **Tailwind logical classes** - все физические `ml/mr/pl/pr/left/right/
  text-left/border-l/rounded-l-*` заменены на `ms/me/ps/pe/start/end/
  text-start/border-s/rounded-s-*` во всём `src/` кроме `NodeCard.tsx`
  и `CompactMiniMap.tsx` (граф React Flow - пространственная структура)

### Сделано (~30 атомарных коммитов)

Основные группы:
- **Foundation** (`b3f724c`, `f8e1e13`, `f2ed968`, `133d484`) - модуль
  script.ts, dictionary expansion, useFormatDate/useNumberFormat
- **Token refactor** (`1a2679c`, `2e4b8f1`) - labelKey/hintKey DictKey
- **Mechanic fixes** (`0d64867` физ.классы, `bb93e2b` Header бренд,
  `3accf3a` NodeCard dir=auto+naskh, `e3f67fc` bidi-изоляция dates/IDs,
  `8e062e4` панели/тосты по локали, `0c73474` RtlRow shamela inline,
  `0a93b6f` FreeformCite dir=auto authority)
- **i18n покрытие компонентов** (`08c9dd3`, `b829426`, `b458f56`,
  `9052413`, `47ee880`, `80b795b`, `de14bdf`, `8a99e07`) - 25+
  компонентов от Header до AdminShamela
- **Hotfix** (`4a8eff5`) - useT/useFormatDate стабилизированы через
  useCallback после диагностики infinite-loop fetch в TopicGraphPage
- **Docs** (`7ef433d`, `d450277`) - i18n-guide.md + coding-standards
  раздел RTL/bidi + CLAUDE.md правила
- **Post-review cleanups** (`3581272` и др.) - после code review
  feedback (12 Important issues все закрыты)

### Code review (subagent)

Запрошен через `/superpowers:requesting-code-review` после первой
итерации (21 commit). Результат: 11 strengths, **0 Critical**, **12
Important**, 7 Minor, verdict **Ready to merge**. Все Important
закрыты в follow-up commits (~10 шт)

### Ключевые design decisions

- **Locale UI vs Content language vs Text direction** - три разных
  понятия, не смешивать. UI следует `useLocaleStore`, контент -
  `dir="auto"`, шрифт - `hasArabicScript`. Раньше было
  `book.language === 'ar' ? 'rtl' : 'ltr'` в нескольких местах -
  ломалось на «RU UI + AR книга»
- **Inline shamela формат для метаданных** (вместо infobox) - в обеих
  локалях `Label: value` на одной строке. Direction родителя зеркалит
  порядок автоматически
- **Граф React Flow не зеркалится** - canvas/позиции/minimap остаются
  LTR. Меняется только текст внутри узлов (dir=auto + font-naskh) и
  UI-панели вокруг канваса
- **FormModal как DRY-точка** - «Отмена» переведена один раз в shared
  компоненте, автоматически покрывает все формы

### Что НЕ сделано (backlog для следующих сессий)

- **Внутренние формы AddSourceModal** - SourceSearchForm/
  SourceCreateForm/AttachFields placeholder'ы захардкожены
- **ESLint pre-commit rule** на cyrillic literals в JSX
- **AR locale parameterized tests** - сейчас завязаны на default `ru`
- **Bibliography parser 20.c** - была планируемая работа Сессии 33,
  переехала в Сессию 34

### Метрики

- 30 атомарных коммитов, push в origin/master в конце сессии
- 143/143 тестов
- Build/typecheck чист, ESLint только 16 safe warnings про `t in deps`

---

## 2026-05-14 - Сессия 32 (full-stack) - 20.f LibraryCite redesign + i18n + FK variant A

После Сессии 31 (бэк 20.a-b + frontend 20.f первая итерация) пользователь
дал три feedback'а: карточка citation выглядит «двух-колоночной» (mixed
RTL/LTR), не получается создать вторую citation на ту же книгу
(`fk_error`), нужны переводимые labels с переключателем локали + ширина
header книги выровнена

### Сделано (4 функциональных коммита)

- **`72ddd0b`** `feat(frontend): SourceCard «всё к правому борту»` -
  применён дизайн D из handoff bundle Claude Design (claude.ai/design).
  12 атомов в `shared/components/citation/sourceCard/`:
  Bdi / Chip / Collapsible / FlexValue / HijriYear / Label / PrimaryButton /
  QuoteBlock / RtlRow / SourceCard / SourceCardHeader / cardShell.
  Концепция variant D: вся карточка `dir="rtl"`, всё к правому борту,
  `<bdi dir="ltr">` для cyrillic, quote `dir="auto"` (UA bidi resolve)
- **`c1a6ff1`** `feat: i18n minimal + structured BookHeader` -
  shared/i18n/ (dictionary ru/ar 22 keys, useLocaleStore zustand,
  useT hook). Backend BookDetailResponse extended с nested
  Authority/Muhaqqiq/Publisher/PublicationPlace refs (BookService
  резолвит FK). BookHeader переписан structured с RtlRow + переводимые
  labels (Автор/Тахкик/Издатель/Издание/Год)
- **`d86e010`** `feat(frontend): RU/AR locale toggle + layout fix` -
  LocaleSwitch chip в Header, localStorage persist, LocaleEffect
  синхронизирует `<html lang dir>`. Tailwind logical classes
  (ms-/me-/ps-/pe-/border-s-/text-start) автоматически mirror'ятся.
  BookHeader wrapped в Card для consistency width с PageView,
  ReaderModeSwitch (Текст/PDF) перенесён в sticky toolbar
- **`8f3b2c9`** `feat: FK variant A` - миграция 25 заменяет
  `node_sources_pkey (node_id, source_id)` на surrogate `id UUID PK`.
  Backward-compat aliases `findByIds/delete` в repository для legacy
  flow. Now user может прицепить N разных фрагментов одной книги к
  одному узлу - то что нужно для бахс анализа. DELETE endpoint
  изменился на `/sources/{nodeSourceId}` (breaking change path param)

Bidi quirks fix'ы (`3588d62`, `bcfc18f`) - ушли в pre-redesign, потом
полностью заменены SourceCard handoff'ом

### Решения

- **Variant D «всё к правому борту»** rejected my previous подход с
  один-direction-на-строку. Все рядки в RTL контейнере, latin/cyrillic
  через `<bdi dir="ltr">` сидят справа но читаются LTR. Чище structure,
  работает в обе локали без переделок
- **Ручной i18n dictionary** (без i18next/react-intl) - 22 keys, ручной
  type-safe через DictKey union. Простой zustand store + LocaleEffect.
  Когда словарь вырастет за 200+ keys - можно migrate на i18next без
  изменения вызывающего кода (useT hook сохраняется)
- **FK variant A vs B vs C** - выбран A (surrogate id PK). B (composite
  с positional fields) overkill для текущей user feedback. C (frontend
  replace dialog) теряет данные. A даёт реальный multi-citation use case
- **LTR wrapper для publisher · place pair** - в RTL row flex reverses
  order. Wrap pair в `dir="ltr"` chip-span сохраняет visual «Дар Тайба ·
  Эр-Рияд» вместо реверсного «Эр-Рияд · Дар Тайба»
- **Shamela parser НЕ извлекает academic fields** - проверено: mapper
  сохраняет raw `bibliography` text в `description`, regex / parser
  нужно создать (Этап 20.c)

### Проблемы

- **Duplicate API requests** в dev - React StrictMode двойной mount.
  Tried: AbortController + onCountsChange via useRef + ref guard.
  Ref guard сломал re-mount (state lost). Откат к AbortController +
  принятие 2 request в dev tab (production = 1 request, by-design React)
- 27 call sites `new Book(...)` + new Authority(...) - rewrite в 18+8
  файлах. Возможно future refactor на builder pattern
- DELETE path break: `/sources/{sourceId}` → `/sources/{nodeSourceId}`.
  Обновлены NodeDetailsPanel.test.tsx + NodeSourceControllerIT.
  api-contract.md не обновлён - **TODO для следующей сессии**

### Следующий шаг

**Сессия 33 - этап 20.c Shamela bibliography parser**

В БД (по результатам `SELECT description FROM lib_books WHERE
description IS NOT NULL LIMIT 5`) можно увидеть форматы. Plan:

1. Создать `ShamelaBibliographyParser` в
   `backend/src/main/java/ru/basnukaev/argumentmap/library/shamela/service/`
   - regex для каждого поля (мухаккик `تحقيق:`, publisher `الناشر:` /
     `دار`, place, edition `الطبعة:`, year hijri `هـ` / gregorian `م`)
   - Return record `ParsedBibliography(muhaqqiqName, publisherName,
     placeName, editionNumber, yearHijri, yearGregorian)` - все nullable
2. Интегрировать в `ShamelaToLibraryMapper.mapBook` - после resolving
   authority вызвать parser, для каждого non-null field вызвать
   `*Repository.findOrCreate(name)` и заполнить FK на book
3. Unit-тесты на ~10 реальных bibliography строк (extract from
   production-БД через `psql`)
4. Endpoint backfill в `ShamelaAdminController`:
   `POST /api/v1/admin/shamela/backfill-academic-metadata` - перебор
   всех замапленных книг, parser + UPDATE academic fields
5. После backfill - smoke на `/books/{id}` любой shamela-imported книги:
   BookHeader должен показать structured metadata

**Доделки следующей сессии (низкоприоритетные):**

- `api-contract.md` update: NodeSourceResponse получил `id`, DELETE
  path меняется на `/sources/{nodeSourceId}`, BookDetailResponse +
  nested refs. Добавить historic line про migration 25 FK variant A
- ADR-029 для FK variant A (decisional - surrogate vs composite PK)
- ADR-030 для i18n архитектуры (минимальный manual dictionary vs
  i18next - обоснование выбора)
- gotcha: «React StrictMode duplicate requests in dev» - by-design,
  AbortController не fix'ит (request уже на network к моменту cleanup)
- `roadmap.md` обновить - проставить `[x]` на 20.f + FK fix добавить
  как Этап 23 (или подэтап существующего)

### Инфраструктура (Сессия 33 entry)

- Postgres :5432, миграции до 25 включительно applied
- Backend :9090 + JDWP :5005 running
- Frontend :5173 running с HMR + i18n locale persist в localStorage
- Smoke: book `02bcfa43-...` имеет filled academic data
  (мухаккик/publisher/place/edition/years), `/books/{id}` показывает
  structured BookHeader, `/topics/a6617d11-...` citation card работает
- 425/425 backend IT, 143/143 frontend tests pass

---

## 2026-05-14 - Сессия 31 (backend) - Этап 20.a-b academic citation metadata ЗАКРЫТ

Реализован ADR-028 - расширение схемы для бахс-grade academic citation.
Нормализованный middle path: справочники для high-reuse полей +
расширение `authorities` для академического имени автора + per-book
скаляры

### Сделано

- `f3338b3` `docs: design spec ADR-028`
- `e6450ae` `docs: implementation plan ADR-028`
- `8033fcb` миграция 24: ALTER `authorities` + `full_name` +
  `death_year_hijri`, CREATE `lib_publishers` / `lib_publication_places` /
  `lib_muhaqqiqs` (UNIQUE name), ALTER `lib_books` + 3 FK + 3 scalars,
  3 CHECK + 4 BTREE индекса
- `48959a5` 3 справочника Publisher / PublicationPlace / Muhaqqiq -
  record + JDBC repository с `findOrCreate`, 18 IT
- `01b7a13` Authority + `fullName` / `deathYearHijri`. Поправлены call
  sites `new Authority(...)` в 8 файлах. 3 новых IT (round-trip + 2x
  CHECK violation)
- `42bbad1` Book + 6 полей. Поправлены call sites `new Book(...)` в 18
  файлах. 4 новых IT
- `808be8e` `CitationDetail` record (27 полей) + 9 LEFT JOIN в
  `NodeSourceRepository.findByNodeIdWithLocation`. 5 новых IT
- `7cdfc78` `CitationResponse` + 8 nested ref DTO. `NodeSourceResponse`
  рефакторен (плоские поля → nested citation). `DtoMappers.toCitationResponse`
  + 8 helpers
- `14a5c12` ADR-028 + doc updates (architecture / api-contract /
  glossary / roadmap)

`./mvnw verify`: 425/425 IT pass (+56 vs Сессии 30)

### Решения

- Option A (плоские поля) - rejected: typo-дубли + поиск невозможен
- Option B (1:N book_editions) - rejected: каскад изменений overkill
  для shamela one-edition-per-book. Future migration path сохранён
- Option C (JSONB academic_metadata) - rejected: нет query-able
  индексов, type unsafe
- Выбран middle path - **справочники + расширение Authority + per-book
  скаляры**
- Structured `CitationDetail` вместо string concat - решает слипание
  арабского с латинскими/кириллическими частями. Frontend рендерит
  каждое поле блоком

### Проблемы

- 27 call sites `new Book(...)` и 17 `new Authority(...)` - rewrite
  в 8+18 файлах. Возможен будущий рефактор на builder pattern
- `printed_page` / `part` в `lib_pages` оказались **TEXT** (могут быть
  римскими цифрами / арабскими буквами). `CitationDetail.regionPrintedPage`
  изначально planned Integer → поправлен на String

### Сделано (продолжение - 20.f frontend)

Сессия расширена, 20.f закрыт в той же сессии:

- `23d738d` `feat(frontend): этап 20.f - LibraryCite блочный рендер`
  - `npm run generate-api` обновил types.ts с nested `citation`
  - Backend: добавлено опциональное поле `legacySnapshot` в
    `NodeSourceResponse` (восстановление legacy snapshot для LEGACY
    mode без отката всего рефактора)
  - `NodeCitationsSection.tsx` `LibraryCite` полностью переписан:
    Author / Book title / Muhaqqiq / Publisher · Place · Edition /
    Years / Location / Quote / Deep link - каждый conditional блок
    со своим dir / font / стилем
  - `buildDeepLink` на nested `citation.book.id` / `location.pageId`
    / `pdf.fileId` вместо плоских полей
  - FreeformCite использует `link.legacySnapshot` вместо удалённого
    `link.location`
  - 143/143 frontend tests pass, 40/40 backend controller IT pass,
    bundle 327kB / gzip 103kB (без изменения)
- `bcfc18f` `fix(frontend): 20.f - bidi RTL/LTR для кириллических labels`
  - После playwright smoke увидели bidi-quirk: кириллические labels
    («тахкик:», «(т.774») flip'ались поверх arabic spans
  - Wrap strategy: container divs в `dir="ltr"`, arabic spans inline
    в `dir="rtl"` с unicode-bidi: isolate для location parts

**Playwright smoke** на `/topics/a6617d11.../`, node «Сахаба и саляф не
праздновали Мавлид» с pre-fill через SQL UPDATE (мухаккик السلامة,
publisher Дар Тайба, place Эр-Рияд, edition 2, годы 1420/1999, author
fullName + death_year_hijri 774). Все 15 блоков визуально присутствуют
и читабельны. Screenshot в `/tmp/librarycite-3-card.png`

### Следующий шаг (для Сессии 32)

Оставшиеся подэтапы Этапа 20:

- **20.c** Shamela bibliography parser - regex extraction мухаккика /
  publisher / edition / year из raw `lib_books.description` (там
  лежит bibliography из shamela). Использует `*Repository.findOrCreate(name)`
  для upsert справочников. ~0.5 сессии
- **20.d** Admin BookEditModal - frontend UI для ручного дозаполнения
  academic fields после импорта (когда parser не справился). Search +
  autocomplete по существующим публишерам/местам. ~1 сессия
- **20.e** AddSourceModal расширенная форма - при manual entry для
  sourceType=BOOK запросить полные поля. ~0.5 сессии

**Minor visual polish** для будущего: bidi ordering author name + year
в Author block ещё не идеален (год слева от имени). Low ROI - функционально
работает, читается, оставляю на следующий polish-pass

---

## 2026-05-14 - Сессия 30 (frontend) - user-feedback fixes + 18.h.B1+C1 design polish

Открыта после ручного browser-теста после Сессии 29. Три feedback
пункта, все закрыты

### Сделано

- `5fc87d1` «Цитаты» → «Опора» (مُسْتَنَدٌ - то на что опирается
  тезис), иконка Quote → Anchor. Backend: убран `«, строки X-Y»` из
  computed location SQL JOIN. Display теперь `Т.X стр.Y`
- `ced7e79` 18.h.B1+C1: `CitationsList` разделён на `LibraryCite`
  (3px indigo bar + «Из библиотеки» badge + Перейти к источнику)
  vs `FreeformCite` (slate bg + «Свободная» badge + AlertCircle для
  URL без citation). `NodeDetailsPanel` header получил inline meta-row
  `⚓ N опора (📖 lib · ❝ free)`. `NodeCitationsSection` переключён
  с lazy `onFirstOpen` на eager-load on mount + `onCountsChange`
  callback в parent
- `6d9b6d8` убран `Math.random()` из render (react-hooks/no-impure-function-during-render)
- `22f1be4` иконки в опоре увеличены до читаемого размера 13-14px

### Решения

- **«Опора» вместо «Источники»** - семантический эквивалент
  исламского концепта `мустанад`/`далиль`, не конфликтует с domain
  term `Source`
- **Range убран из display location** - бесполезен в academic
  citation. Остаётся только для технического highlight через
  `?highlight=` query param
- **Lift state up** через `onCountsChange` callback вместо backend
  расширения - state colocation, наружу только агрегаты
- **18.h.A1 (NodeCard footer chips) deferred** - duplicate данные с
  header meta-row, low ROI

### Следующий шаг

Сессия 31 - этап **20.a Academic citation metadata** (ADR-028).
Закрыто в Сессии 31, см. запись выше
