# Журнал работы

Последние сессии. Новые записи - **сверху**

Формат записи описан в `docs/doc-hygiene.md` Принцип 5

**Архив:**
- Сессии 0-21: [`docs/archive/progress-sessions-1-21.md`](archive/progress-sessions-1-21.md)
- Сессии 22-29: [`docs/archive/progress-sessions-22-29.md`](archive/progress-sessions-22-29.md)

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
