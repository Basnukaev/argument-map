# Журнал работы

Последние сессии. Новые записи - **сверху**

Формат записи описан в `docs/doc-hygiene.md` Принцип 5

**Архив:**
- Сессии 0-21: [`docs/archive/progress-sessions-1-21.md`](archive/progress-sessions-1-21.md)
- Сессии 22-29: [`docs/archive/progress-sessions-22-29.md`](archive/progress-sessions-22-29.md)

---

## 2026-05-16 - Сессия 35 (frontend+backend+docs) - v2 коммиты + doc-долги + 20.c parser

Подобрала uncommitted v2 design migration из Сессии 34, закрыла все
зависшие doc-долги Сессий 32-34, и сделала Этап 20.c (shamela
bibliography parser). Чисто отработавшая сессия в режиме автономии.

### Сделано

**v2 design migration (Сессия 34) разложена в 6 атомарных коммитов:**

- `feat(frontend): v2 design tokens infrastructure` - styles/tokens.css
  + index.css `@theme inline` + themeStore + ThemeEffect + index.html
  FOUC script + Manrope/Source Serif/Amiri шрифты + clsx dep
- `feat(frontend): v2 UI primitives на токенах` - 13 primitives + 2
  новых (Chip/Field) + designTokens.ts
- `feat(frontend): AppHeader + ThemeSwitch + CommandPalette + Bell/Avatar menus`
- `feat(frontend): v2 pages migration + i18n keys` - 6 страниц
  (BookList sort+import, TopicList, CreateTopic two-column, TopicGraph
  AppHeader, BookReader, AdminShamela activity log) + ~25 ключей RU/AR
- `refactor(frontend): graph v2 migration` - 20 файлов графа
  (NodeCard rounded-md, EdgeDetailsPanel solid bg-edge-*-bg,
  CompactMiniMap CSS vars, legend удалена, modals/forms tokens)
- `refactor(frontend): source card + reader на v2 токенах` - 18 файлов
  (QuoteBlock bg-paper, PrimaryButton outline, Collapsible default open,
  reader compose)

**Doc-долги Сессий 32-34 закрыты (один коммит `docs:`):**

- **api-contract.md:** NodeSourceResponse.id (UUID), DELETE
  `/sources/{nodeSourceId}` (breaking path), строка в "Историю изменений"
- **ADR-029 FK variant A** - surrogate UUID id для node_sources, mig 25,
  multi-citation per pair
- **ADR-030 i18n архитектура** - manual dictionary с DictKey union
  literal, useCallback стабилизация, alternatives (i18next отвергнут)
- **ADR-031 v2 design system tokens** - двухслойная token-арх
  (semantics tokens.css + @theme inline bridge), runtime темизация
- **gotchas +5:** React StrictMode duplicate API requests (Сессия 32),
  closure-функции useCallback (Сессия 33), batch-Edit cyrillic verify-grep
  (Сессия 33), Tailwind v4 @theme inline обязателен (Сессия 34),
  mass-replace sed grep audit (Сессия 34)

**Этап 20.c shamela bibliography parser:**

- `ShamelaBibliographyParser` - regex по 5 markers
  (المحقق / حققه ووضع حواشيه / تحقيق / الناشر / الطبعة / عام النشر)
- Heuristic split publisher по " - " для publicationPlace; explicit
  `مكان النشر:` имеет приоритет
- Arabic ordinal dictionary (الأولى=1, ..., العاشرة=10) + fallback на
  arabic-indic digits
- Years: arabic-indic digits перед `هـ` (хиджра) и `م` (григориан)
- `ParsedBibliography` record - все 6 полей nullable, конс. парсер
- Интегрирован в `ShamelaToLibraryMapper.mapBook` через
  `findOrCreate` в `MuhaqqiqRepository`/`PublisherRepository`/
  `PublicationPlaceRepository`. Заменил 6×null в Book record на FK+years
- 11 unit-тестов с реальными фикстурами из dev-БД

### Решения

- **`@theme inline` зафиксирован в ADR-031** как required для v4
  runtime темизации - без него `var()` раскрывается статически на этапе
  сборки. Уровень gotcha + ADR
- **Surrogate UUID id для node_sources** (ADR-029) обоснован vs
  composite PK с positional fields (Option B, Postgres NULL-handling
  хрупкий) и vs отдельная таблица node_source_positions (Option C,
  over-engineering для 1:1)
- **Parser консервативен** - вместо смелого парсинга всех вариантов
  shamela bibliography возвращает null для missing markers. Admin
  BookEditModal (20.d) дополучит UI для ручной правки
- **Bulk-backfill endpoint** для existing books отложен на 20.c
  follow-up - требует `BookRepository.update` + admin endpoint, не
  ложится естественно в один pass с parser

### Проблемы (открытые)

- **Bulk-backfill 20.c follow-up** - 3 dev-книги в БД остались с null
  FK (импорт до 20.c). Чтобы заполнить - либо новый endpoint
  `POST /api/v1/admin/shamela/backfill-bibliography`, либо manual SQL.
  Endpoint - один сервис-метод + один controller-метод + BookRepository
  partial update SQL. ~30 минут работы
- **Heuristic split publisher** по " - " - может слипнуть имя
  издательства с пробелами на конце. Acceptable для MVP, при появлении
  false positive (publisher усечён) - tighten condition (требовать
  пробел или арабскую букву после `-` начала)
- **@tabler/icons-react все еще blocked** через корпоративный proxy
  (ETIMEDOUT). Workaround `svg.lucide:not([stroke-width])` остается

### Дополнительно сделано (после handoff Сессии 35)

В той же сессии, после первого handoff коммита, продолжил работу:

- **Cmd+K → Alt+K migration** (коммиты `3a39ad8`, `cefa404`, `17353fa`,
  `dd4b1ca`). Палитра не открывалась с Cmd+K в Chrome на Win/Linux -
  native accelerator перехватывал. Listener поднят из Header.tsx в
  App.tsx через `paletteStore` (zustand) - работает на любом route
  включая graph page. Capture phase + stopPropagation не освободили
  Ctrl+K. Финальное решение - Alt+K. Gotcha в `gotchas.md` со списком
  зарезервированных/свободных Chrome accelerators
- **20.c follow-up bulk-backfill endpoint** (`59e8414`).
  `POST /api/v1/admin/shamela/backfill-bibliography` через
  `ShamelaBibliographyBackfillService`. Critical parser fix - real БД
  хранит CR character (chr(13)) как separator, не literal `\r` (2 char).
  Regex расширен на alternation. Smoke: 3/3 dev-книг с заполненными FK
  (тафсир получил мухаккика+publisher+place+ed=1+hijri=1431+greg=1999,
  все аналогично)
- **Этап 20.d Admin BookEditModal** (`ea42007` backend + `9c3467d`
  frontend). Backend: `PATCH /api/v1/library/books/{id}` через
  `UpdateBookRequest` (PATCH-семантика null=no change, ""=clear,
  non-empty=findOrCreate). 3 autocomplete endpoints muhaqqiqs/publishers/
  publication-places (ILIKE substring search). Frontend: BookEditModal
  с 6 Field primitive + 3 inline autocomplete (debounce 250ms +
  AbortController cancel). Pencil icon в Card.Cover на /books, кнопка
  «Перечитать metadata» в /admin/shamela. Playwright smoke: тафсир
  Ибн Касира prefilled 6 полей. Verify: backend 425/425, frontend
  143/143, lint clean
- **Этап 19.a Q&A foundation** (`ba5cf8c` backend + `8c75605` frontend
  + ADR-032 в decisions.md). Migration 26 questions table + Question
  domain/repo/service/controller (CRUD под /api/v1/questions). 3
  frontend pages (QuestionListPage с status filter + search /
  CreateQuestionPage с Field + counters / QuestionDetailPage с status
  switcher + delete confirm). Header nav «Q&A» enabled. 29 i18n keys
  RU/AR. Playwright headless подтвердил полный UI CRUD flow.
  Backend verify 425/425

### Следующий шаг (для Сессии 36)

Этапы 20 и 19.a закрыты. Опции на выбор:

**Опция A (рекомендую) - Этап 19.b Q&A source attach** (~1 сессия) -
**настоящая валидация platform pivot**. Migration 27 `question_sources`
(аналог `node_sources` через ADR-027/029) + reuse CitationPicker в
QuestionDetailPage. Если работает - архитектура подтверждена.

**Опция B - Этап 20.e AddSourceModal расширенная** (~0.5 сессии).
При sourceType=BOOK дополнительные academic поля. Reuse autocomplete
через shared `<AcademicMetadataFields>` компонент.

**Опция C - Этап 19.c Answers** (~1 сессия). Answers table + UI add
answer + accepted answer flag.

**Опция D - cleanup**: SourceSearchForm/SourceCreateForm i18n placeholder,
ESLint rule на cyrillic JSX literals, @tabler/icons retry.

Приоритет 2 (альтернатива):

- **Этап 19 Q&A приложение** - валидация платформенной архитектуры
  через второе приложение использующее common Source/Book stack
- **@tabler/icons-react retry** когда сеть позволит → создать
  `src/shared/icons.ts` shim + sed-replace `lucide-react` → `@/shared/icons`

### Инфра на момент Сессии 36 entry

- Postgres :5432 healthy (4+ days uptime), миграции до 25 включительно
- MinIO :9000 healthy
- Backend :9090 + JDWP :5005 (рестарт нужен после parser changes -
  старый процесс на :9090 не подхватит новый classpath)
- Frontend :5173 - после restart Vite должен подобрать v2 changes
  (но кэш .vite может потребовать `rm -rf node_modules/.vite`)
- Все 425 backend tests pass, frontend 143 tests pass, lint 0 errors

---

## 2026-05-15 - Сессия 34 (frontend) - v2 design migration

Большая UI-инициатива от Абдулы: миграция всей фронт-страницы на v2 design
system из `frontend/design-reference/v2/project/handoff/` (5 markdown'ов
с tokens/components/pages + готовый tsx-набор). Этап 20.c parser **не
начат**, перенесён на Сессию 35.

### Архитектурные артефакты (durable)

- **`frontend/src/styles/tokens.css`** (новый) - семантический слой
  CSS variables: ink-scale (0-900), accent-50/100/500/600/700,
  ok/warn/err по 3 ступени, type-abstract / type-empirical /
  edge-supports/refutes/qualifies/responds для графа, surface aliases
  (`--c-bg` / `--c-bg-elevated` / `--c-bg-sunken` / `--c-border` /
  `--c-text` / `--c-text-muted`). `[data-theme="dark"]` block с
  инвертированной ink-scale + ярче accent для контраста на тёмном
- **`src/index.css`** - `@theme inline` bridge: `--color-ink-900 →
  var(--c-ink-900)` чтобы Tailwind v4 автоматически генерировал
  `bg-ink-900`/`text-ink-900`/`border-ink-900` utility-классы. `inline`
  keyword обязателен - без него v4 раскрывает `var()` статически и
  тема не переключается
- **`src/shared/stores/themeStore.ts`** + **`src/shared/components/
  ThemeEffect.tsx`** - zustand store с persist в `localStorage.app.theme`,
  prefers-color-scheme fallback. ThemeEffect синхронизирует
  `<html data-theme="dark">` после mount
- **FOUC inline script** в `index.html` - читает localStorage до React
  mount, ставит `data-theme` сразу - иначе flash light→dark при первой
  загрузке
- **Шрифты:** Manrope (UI) + Source Serif 4 (serif для prose) + Amiri
  (arabic preferred) + Noto Naskh fallback - подключены через Google
  Fonts preconnect
- **Primitives** - `Button` (6 вариантов × 4 размера, backwards-compat),
  `Card` + namespace `Card.Cover/Body/Eyebrow/Title/Meta`, новые `Chip`
  и `Field` (с `Input`/`Textarea`/`Meta`), обновлены `Modal` / `IconButton`
  / `Badge` / `Kbd` / `Toaster` / `Select` / `ContextMenu` / `FormModal`
  на токены
- **Layout** - `Header` (бренд ﷽ + nav + поиск ⌘K + LocaleSwitch +
  ThemeSwitch + BellMenu + AvatarMenu), `CommandPalette` (Cmd+K,
  фильтр + ↑↓Enter), `BellMenu` + `AvatarMenu` (placeholder dropdown'ы
  до бэка)
- **`designTokens.ts` рефакторен** на новые tokens-классы. Edge stroke
  теперь `var(--c-edge-*)` чтобы React Flow переключал цвета в dark

### Сделано

В рабочем дереве (НЕ закоммичено в этой сессии - 1 большой changeset):

- v2 tokens infrastructure (4 новых файла, переписан index.css)
- 9 primitives обновлены / 2 новых (Chip, Field)
- 4 layout-компонента: Header переписан, CommandPalette/BellMenu/
  AvatarMenu/ThemeSwitch новые
- 6 страниц мигрированы: BookListPage (+Импорт Shamela кнопка +
  сортировка dropdown + 5-col grid 2xl), TopicListPage (Card.Body +
  themeable mini-graph), CreateTopicPage (two-column form + Field
  primitive + sticky aside paper-aside), TopicGraphPage (+`<Header />`
  сверху + secondary crumb), BookReaderPage (bg-bg), AdminShamelaPage
  (status pill + Activity Log placeholder с OK/WARN/ERR badges)
- 18+ файлов графа: NodeCard rounded-md + shadow tokens + ring-2 для
  selected, NodeDetailsPanel убран gradient, EdgeDetailsPanel gradient
  → solid `bg-edge-*-bg`, CompactMiniMap на `var(--c-*)` для темизации,
  shift end-[416px] при detail panel, удалена status legend (anti-pattern)
- Source card: цитата на `bg-paper` (тёплый кремовый, не голубой),
  "Перейти к источнику" - **outline** (не filled primary), metadata
  раскрыта по default
- NodeCitationsSection: кнопки "Привести источник / Свободный" из
  side-by-side в vertical stack (текст обрезался)
- `clsx@^2.1.1` добавлен в package.json как explicit dependency
- Mass-replace через sed: 4 волны (borders / bg+text / accent+focus /
  semantic colors) по всему src
- i18n: ~25 новых ключей RU/AR (palette.*, notifications.empty,
  avatar.*, admin.status_*, admin.activity_log, admin.sync_done,
  book.list.sort_*/import_from_shamela)

### Code review через Agent + 20 issues закрыты

Subagent code review нашёл 3 Critical, 10 Important, 7 Minor. **Всё
зафикшено в той же сессии:**

- **C1** ESLint `react-hooks/set-state-in-effect` в CommandPalette →
  container/body split с remount при `open` flip (key-trick idiom)
- **C2** CreateTopicPage tests fail из-за Field `*` marker меняющего
  accessible name → `aria-hidden="true"` + regex `getByLabelText(/^Название/)`
- **C3** CompactMiniMap hex literals (`#c4b5fd` etc) → `var(--c-type-*)`,
  `var(--c-{status}-500)`, `var(--c-edge-*)`, `var(--c-accent-500)`
- **I4** EdgeDetailsPanel `bg-gradient-to-b from-emerald-50/70` →
  solid `bg-edge-*-bg` через `headerBgFor()`
- **I5** sed-leftovers (ring-indigo-400, divide-slate-100, rose,
  shadow-xl, accent-indigo-600) - cleaned по 7 файлам
- **I7-I8** spacing-scale + text-[Xpx] violations (191 occurrences) → 0
- **I9** Modal `"Закрыть"` → `t('common.close')`
- **I10** AdminShamela toast strings → i18n keys с `{placeholder}` interpolation
- **I11** 16 `exhaustive-deps` warnings → 0 (добавлен `t` в deps,
  он stable per memory `feedback_stable_hooks_for_deps`)
- **I12** NodeCard `rounded-xl` → `rounded-md` + `shadow-sh*` tokens
- **I13** Tabler stroke-width override через `:not([stroke-width])` -
  не перебивает explicit `strokeWidth={2.5}` в edges/labels
- **M14-M17** Amiri preload, FOUC inline script, fixed `bg-black/50`
  backdrop (не `ink-900/40` который инвертируется в dark)

### Решения

- **Tailwind v4 `@theme inline`** обязателен для runtime темизации -
  без `inline` v4 раскрывает `var()` статически при сборке и
  `[data-theme="dark"]` не работает. Знание для будущих изменений
  токенов (gotcha-кандидат)
- **NODE_TYPE_TOKENS унификация** - QUESTION/CLAIM/ARGUMENT все
  получили `type-abstract-bg/fg`, EVIDENCE - `type-empirical-*`.
  Per handoff/02-tokens.md это semantic группировка (концепты vs
  наблюдения), не per-тип палитра как в v1
- **MiniMap shift через prop `detailOpen`** вместо абсолютной
  позиции по центру: minimap всегда snap к inline-end, сдвигается на
  416px (panel 400 + gap 16) когда виден detail panel
- **CommandPalette container/body split** - state живёт в body
  который unmount'ится при `open=false`, естественный reset через
  remount. Альтернатива (useEffect + setQuery) ловит ESLint
- **PrimaryButton "Перейти к источнику" outline** (не filled) per
  референс v3 - primary CTA в detail panel это уже статусные кнопки
  узла, переход в библиотеку - secondary
- **Tabler icons workaround** - npm install падает через корпоративный
  proxy (ETIMEDOUT). Глобальный CSS `svg.lucide:not([stroke-width])
  { stroke-width: 1.5 }` визуально приближает lucide к Tabler без
  установки. Когда сеть позволит - заменить через shim-модуль

### Проблемы (открытые)

- **@tabler/icons-react не установлен** - `npm install` падает
  ETIMEDOUT через proxy `66.151.42.7:64526`. curl reachable, но
  npm streaming не доходит. Сейчас визуальное приближение через
  stroke-width override. Полная замена ждёт когда сеть позволит
- **Backlog от Сессии 33 НЕ закрыт** - api-contract.md (DELETE path,
  NodeSourceResponse.id, BookDetailResponse nested), ADR-029
  (FK variant A), ADR-030 (i18n арх.), gotchas (StrictMode duplicate
  requests, useT стабилизация, batch-Edit cyrillic) - оставлено
  на Сессию 35 потому что эта была чисто UI работа
- **Этап 20.c shamela parser НЕ начат** - перенесён на Сессию 35

### Следующий шаг (для Сессии 35)

1. **Закоммитить v2 миграцию** - changeset из этой сессии в working
   tree, около 60 файлов. Разбить на атомарные коммиты по теме:
   `feat(frontend): v2 design tokens + theme switching`,
   `refactor(frontend): mass replace v1 → v2 palette`,
   `feat(frontend): CommandPalette + BellMenu + AvatarMenu`,
   `feat(frontend): BookList sort + import button`,
   `feat(frontend): AdminShamela activity log`,
   `fix(frontend): code-review follow-up Сессии 34`
2. **Doc-долги от Сессии 33** - api-contract.md update, ADR-029/030,
   gotchas (3 шт), плюс новая gotcha про `@theme inline` v4
3. **Этап 20.c shamela bibliography parser** - оригинальный приоритет,
   план в SESSION_START_PROMPT (раздел Текущий приоритет в Сессии 34)
4. **@tabler/icons-react** - попробовать установить заново когда
   proxy успокоится. Если сработает - shim-файл + sed-replace
   `from 'lucide-react'` → `from '@/shared/icons'`

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
