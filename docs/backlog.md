# Бэклог

Идеи и задачи без привязки к активному этапу. Не закрытые - в
`docs/roadmap.md`. Закрытые в общем виде - в `docs/progress.md`

Когда задача созревает (становится приоритетной или блокирует
другую) - переезжает в новый Этап в `roadmap.md`

## Фронт - общие улучшения

- [x] **Шрифт title книг в BookListPage** - Сессия 36 подсессия
  typography. Выбран **EB Garamond** через семантический token
  `--font-book-title` для возможности замены без правки JSX. Применён
  к `Card.Title` (non-arabic ветка) с подъёмом size до `text-md` (18px)
  и `tracking-normal`. Подключён в `index.html` к существующему
  Google Fonts preconnect block. Полная цепочка: `tokens.css` →
  `@theme inline` bridge → Tailwind utility `font-book-title` →
  `Card.Title`. Amendment к ADR-031 - см. `docs/decisions.md`
- [x] **Фикс 12 pre-existing test failures** - закрыто в Сессии 36
  через ruflo test-regression-diagnoser subagent. Root cause найдено
  и verified: **Node 24 + undici 7 AbortSignal instanceof bug**
  (nodejs/undici#2596, nodejs/node#56644). Это **внешний bug**, не
  наша проблема. Гипотеза про React 19 + act() отвергнута. Fix -
  monkey-patch `globalThis.fetch` в `frontend/src/test-setup.ts`
  beforeAll() после `server.listen()` чтобы strip `signal` из
  RequestInit. После fix - 142/143 passes (12 регрессий все
  восстановлены, остался 1 unrelated pre-existing fail в
  AddSourceModal.test.tsx про reliability radio). Полный gotcha с
  reproducer + альтернативами + рисками - в `docs/gotchas.md` секция
  «Node 24 + undici 7 - AbortSignal instanceof check».
- [ ] Полнотекстовый поиск (НЕ через Postgres tsvector - см. раздел «Архитектурные решения» ниже)
- [x] **Экспорт графа в PNG / SVG** - закрыто 2026-05-17. Реализовано
  через `html-to-image` + кнопка с popover (PNG/SVG) в `GraphPanels`
  toolbar. Filename `topic-{slug}-{YYYY-MM-DD}.{ext}` через slugify
  с fallback `topic` для cyrillic/arabic titles. fitView + 150ms
  задержка перед snapshot. PDF export НЕ реализован - оставить в
  backlog отдельным пунктом при necessity. Подробно в `docs/progress.md`
- [ ] **PDF export графа** - отдельная задача (jspdf или native
  print-to-PDF), приоритет low - PNG/SVG покрывает основной use case
- [x] **Тёмная тема** - закрыто 2026-05-17. ThemeStore расширен на
  3-option mode (`system`/`light`/`dark`) с computed `effectiveTheme`,
  подписан на `matchMedia('prefers-color-scheme: dark')` для live
  системных смен. ThemeSwitch в Header заменён на dropdown
  Monitor/Sun/Moon, FontSettings synced. Tiptap extensions переключены
  с `@media (prefers-color-scheme: dark)` на `[data-theme='dark']`
  (синхронно с manual override). Hardcoded shadows `rgba(15,23,42,...)`
  в CustomEdge + ReaderModeSwitch заменены на токен `shadow-sh2`.
  graphExport читает `--c-bg` для backgroundColor вместо хардкода
  '#ffffff'. ReactFlow получил `colorMode` prop. FOUC inline script
  в index.html синхронизирован с 3-option логикой. Tests +15
  (themeStore 9, ThemeSwitch 6). UI-guidelines секция «Dark mode»
  с правилами для новых компонентов
- [ ] Локализация (i18n) при появлении второй локали
- [x] **Smart edge routing** - закрыто 2026-05-18 через `elkjs ^0.11.1`.
      Lazy chunk ~440KB gzipped, default остаётся `dagre`. Toggle в
      GraphPanels toolbar (Network icon) переключает между `dagre`
      (sync, лёгкий) и `elk` (async, ORTHOGONAL edge routing вокруг
      узлов). ELK = one-shot re-layout при выборе - после применения
      PATCH'ит posX/posY на бэк, дальше работает как обычные сохранённые
      позиции. Persist в `argmap.layoutAlgorithm` localStorage. i18n
      ключи `layout.*` (ru/ar). Tests +16 (elkLayout 5 + graphLayout
      switch 3 + store 4 + GraphPanels menu 4). Полный отчёт в
      `docs/progress.md` + UI-guidelines дополнен разделом «Layout
      algorithm»
- [x] **Z-index full-stack persistence для узлов** - закрыто 2026-05-18.
      Backend: миграция 40 (`nodes.z_index INTEGER NOT NULL DEFAULT 0` +
      composite index `(topic_id, z_index)`), Node domain получил `int
      zIndex`, NodeRepository - updateZIndex/findMaxZIndex/findMinZIndex,
      NodeService - bringToFront/sendToBack с assertCanWrite,
      2 dedicated endpoint'а `POST /api/v1/nodes/{id}/z-order/bring-to-
      front` и `/send-to-back` (вместо расширения PATCH /nodes/{id} -
      проще API surface). Frontend: buildFlow читает zIndex из DTO,
      bringNodeToFront/sendNodeToBack делают optimistic local update +
      apiPostRaw + refetch, context menu пункты «На передний план» /
      «На задний план» под `canWrite`. Tests: 5 IT (NodeZIndexIT - max+1,
      min-1, 403 nonOwner для обоих action, 404 missing) + 2 frontend
      (MSW). Edges - отдельный пункт ниже, отложен (z-order на edges
      редко важен, локального достаточно)
- [ ] **Z-index persistence для edges** - на узлах закрыто, на edges
      остаётся локальный zRef counter в GraphCanvas. Делать если станет
      критично. Структура аналогичная - миграция, repo.updateZIndex,
      service.bringToFront/sendToBack, REST endpoints

## Responsive / mobile-планшетная адаптация

Фаза 1 (foundation: useIsMobile, Modal, NodeDetailsPanel, Header,
Select) - Сессия 39. Фаза 2 (10 страниц: BookReader drawer, sticky
dvh, PdfViewer toolbar 2-row, list/create padding, AdminShamela
table scroll, CitationPicker tabs, AcademicMetadata 1-col, filter
chips overflow) - Сессия 40. Обе сжаты в roadmap closed-stages

### Фаза 3 - возможные улучшения (когда понадобится)

- [ ] **Hover-only действия имеют tap-альтернативу** - аудит,
  где в проекте есть `group-hover:opacity-100` для кнопок без
  альтернативного tap (например `TopicCard` export button).
  Mobile = touch, hover не работает - кнопки невидимы
- [ ] **Replay design-reference responsive prototypes** - в
  `design-reference/project/responsive.jsx` есть варианты mobile
  navigation которые не имплементированы (bottom-tabs?
  pull-to-refresh?). Cherry-pick если станет нужно
- [ ] **Tablet portrait (768px-1024px)** - sweet spot не покрыт
  явно: `md:` triggers desktop layout, mobile уже стэкается.
  Возможно нужен `md:` mid-density variant между mobile-stack
  и full-desktop

## Будущие фичи (исламский контекст и расширения из дизайн-референса)

В `frontend/design-reference/project/islamic.jsx` и `extras.jsx`
дизайн показывает большое количество секций про работу с
исламскими текстами, sanad-цепочками, multi-grading и пр. Каждая
секция здесь - заготовка под будущий ADR и этап

- [ ] **Source picker для Корана** - таб «Коран» с навигацией по
      сурам, выбор аята, inline-вставка с цитатой и переводом.
      Бэк не готов: нужна интеграция с источниками типа quran.com
      или локальный mushaf-датасет _(SourcePickerQuran)_
- [ ] **Source picker для хадисов** - таб «Хадисы» с 9 сборниками
      (Бухари, Муслим, Тирмизи и т.д.), фильтр по grade
      (sahih/hasan/daif), показ иснада. Потенциальная интеграция
      с sunnah.com _(SourcePickerHadith)_
- [ ] **Source picker для книг** - таб «Книги» с навигацией том /
      страница, интеграция с shamela.ws. Самая большая работа
      из source pickers _(SourcePickerBooks)_
- [ ] **Source detail panel** - параллельная боковая панель
      (800px) с полным содержанием цитируемого источника,
      контекстом и метаданными _(SourceDetailPanel)_
- [ ] **Library overview** - страница `/library` с обзором
      источников темы _(LibraryOverview)_
- [ ] **Inline citations** - формат `[1]` в тексте с popover,
      привязанные к node-source records _(InlineCitations)_
- [ ] **Sanad explorer** - визуализация цепочки передатчиков
      хадиса (8-звенная от Пророка ﷺ до составителя). Каждое
      звено - карточка передатчика (имя / поколение / tier).
      Связи типизированы (`sama'` / `'an'ana` / `haddathana` /
      мункати'). Альтернативные пути. Серьёзная доменная фича -
      потребует расширения доменной модели (новые сущности
      `Rawi`, `Sanad`, `SanadLink`) _(SanadExplorer, SANAD demo
      data)_
- [ ] **Multi-grading хадисов** - один хадис может быть оценён
      несколькими учёными по-разному (Бухари: sahih, Тирмизи:
      hasan). Сейчас `Reliability` - single-value. Расширение на
      M:N таблицу `hadith_grades` (rawi / scholar / grade / source)
      _(MultiGradingSection, SCHOLAR_GRADES demo)_
- [ ] **Bilingual карточки** - двуязычный режим узла
      (EVIDENCE / ARGUMENT с арабским оригиналом + русским
      переводом). Toggle режима оригинал / перевод / оба.
      Требует RTL-поддержки и naskh-шрифтов _(BilingualNodeCard)_
- [ ] **Translator attribution** - при показе перевода аята /
      хадиса - указание переводчика (Кулиев, Sahih International,
      Османов и т.д.). Dropdown переключения переводов
      _(TranslatorSection)_
- [ ] **Tashkeel toggle** - на canvas карточки можно отключить
      огласовки (`harakat`) для краткости. Side-by-side
      сравнение с / без _(TashkeelSection)_
- [ ] **RTL-режим** - для арабского UI: зеркальный layout графа,
      RTL-toolbar, naskh / kufi-шрифты. Большая работа, выделить
      в отдельный этап _(RTLGraphScreen, RTLSection)_
- [ ] **Language switcher (RU / EN / AR)** - в header или
      settings. Идёт в комплекте с i18n и RTL
      _(LanguageSwitcher)_
- [ ] **Settings screen** - язык, выбор арабского шрифта, размер
      текста, тогглы tashkeel / транслит, drag-приоритет
      источников _(SettingsScreen)_
- [ ] **Onboarding** - 4-шаговый чеклист для новой темы
      («создай корневой вопрос», «добавь тезис-ответ» и т.д.) +
      hint-указатели на canvas _(OnboardingChecklist,
      OnboardingHint)_
- [ ] **Topic settings drawer** - 480px drawer над затемнённым
      canvas: title / desc, корневой вопрос (lock), радио
      Private / Shared / Public, метаданные, danger zone
      _(TopicSettingsDrawer)_. Требует расширения Topic на
      бэке полем `visibility` (после auth)
- [ ] **Multi-select с floating action bar** - лассо или
      Shift+click несколько узлов, всплывающая action-bar для
      массовых операций (изменить статус, переместить, удалить,
      экспорт) _(MultiSelectScreen)_
- [ ] **Cross-references drawer** - 600px drawer «узел использован
      в N темах»: группировка по темам, прыжок в граф. Cross-topic
      graph-навигация. Требует backend аггрегата по cross-topic
      ссылкам _(CrossRefDrawer)_
- [ ] **Print preview** - A4-toolbar с тогглами (включить узлы,
      источники, иснады) + полноценная печатная страница темы.
      Граф как SVG, источники в академическом формате
      _(PrintPreviewSection)_

## Бэк - бэклог

- [x] **Пагинация + фильтрация для всех GET-list endpoints** -
      закрыто 2026-05-18. Все 5 endpoints (`/sources`,
      `/authorities`, `/topics`, `/library/books`, `/questions`)
      возвращают `PagedResponse<T>` обёртку. Default `page=0&size=20`,
      max `size=100`. Новые фильтры: sources `?type=&reliability=`,
      authorities `?era=`, topics `?visibility=`, books
      `?authorityId=&publisherId=`. Combination validation
      (reliability только при type=HADITH) → 400 illegal-argument.
      Helper'ы `PagedResponse.of()` + `PageRequest.from()` в
      `web.dto`. Repository паттерн `findPage`+`countFiltered` с
      общим `appendFilters`. Breaking change для frontend
      (raw-array → PagedResponse). 1 page (TopicListPage)
      обновлён smoke; остальные frontend pages - см. ниже
- [ ] Аутентификация (Spring Security + JWT) - см. Этап 21 в
      roadmap
- [ ] Реализация Dung's argumentation framework для продвинутого
      пересчёта статусов
- [x] Импорт / экспорт темы в JSON - закрыт в Сессии 39
      (ADR-037, GET `/topics/{id}/export` + POST `/topics/import`)
- [x] **Голосование за вес аргументов** - закрыто 2026-05-18.
      Миграция 38 `node_votes` (UUID PK, FK CASCADE на nodes+users,
      weight SMALLINT CHECK IN (-1,1), UNIQUE node+user). 3 endpoint
      под `/api/v1/nodes/{id}/vote(s)` (POST upsert / DELETE
      idempotent / GET stats). `NodeResponse` расширен 4 vote-полями
      + bulk-load в graph endpoint (2 SQL на весь граф, не N+1).
      Frontend `VoteWidget` в `NodeCard` для `ARGUMENT`/`EVIDENCE` -
      compact upvote/downvote toggle с optimistic UI. MVP 3-point
      scale {-1, +1}. Permission: vote требует только canReadTopic
      (видишь узел - можешь vote, не write-access). Голоса НЕ
      влияют на StatusCalculation - ортогональный сигнал силы.
      Отложено: 5-point scale {-2..+2}, vote-driven status hints,
      voter-list UI (transparency), агрегаты в `TopicResponse`
- [x] **Frontend pagination для остальных list pages** -
      закрыто 2026-05-18. Обновлены: `BookListPage` (apps/library),
      `QuestionListPage` (apps/qa), `CitationPicker` source-tab.
      `AdminShamelaPage` search endpoint оставлен (raw array,
      не paginated на бэке - admin staging search). Pattern: тот же
      Load More как в `TopicListPage` - state `{items, page,
      hasNext, totalElements}`, кнопка скрыта при активном
      client-side filter/search. `CitationPicker` использует
      `size=100` без Load More (modal layout). `QuestionListPage`
      использует server-side `?status=` через URL param с reset
      page=0 при смене фильтра. Реальная pagination (1/2/3/
      Next/Prev) - upgrade когда станет критичным UX
- [ ] **Cursor-based pagination (если станет нужно)** - сейчас
      offset-based, простая работа для UI. Cursor (created_at +
      id) станет нужен когда: (1) у тем будут миллионы записей -
      OFFSET становится дорогим (`OFFSET 1000000` PG скиппает
      миллион строк), (2) infinite scroll с stable порядком при
      concurrent inserts. До тех пор offset OK

## Tech debt / performance optimization

- [ ] **Shared MinIO Testcontainer для IT suite** - сейчас 7+ IT
      классов (`ObjectStorageServiceIT`, `BucketBootstrap*IT`,
      `OrphanDetection*`, `IntegrityVerification*`, `FileImportServiceIT`,
      `FileImportControllerIT`, `UserUploadProviderIT`,
      `HttpClientPdfFetcherRangeStreamingIT`, `PdfLinksSourceProviderIT`)
      каждый поднимает свой `@Container static MinIOContainer`. Cost:
      ~5-10 сек startup × 9 ITs = 45-90 сек overhead на каждый
      `./mvnw verify` (текущий ~80 сек). Решение: singleton container
      pattern через static init block в общем base class либо
      `withReuse(true)` через Testcontainers reuse mode (требует
      `testcontainers.reuse.enable=true` в `~/.testcontainers.properties`).
      Низкий приоритет - CI не блокирует, локальная разработка
      acceptable. Станет неприятно когда IT'ов вырастет до 20+.
      Reviewer Сессии 37 + 40 дважды flag'нул это как Important
      tech-debt, пока решение «зафиксировать в backlog и не делать
      сейчас» - явное (no scope creep в текущем этапе)

---

## Архитектурные решения для будущих этапов

Большие технические решения которые **не делаем сейчас**, но уже
выбран подход - чтобы при наступлении этапа не передумывать с нуля.

### Полнотекстовый поиск - отдельный сервис Elasticsearch (НЕ Postgres tsvector)

**Решение:** искать через **отдельный Elasticsearch инстанс**, не
через Postgres `tsvector`/GIN. Sync через outbox / CDC / batch
indexer (выбор при наступлении этапа).

**Почему не tsvector:**
- Постгрес не умеет качественно индексировать **арабский** (нет
  встроенного analyzer для арабской морфологии: рут-based stemming,
  diacritics-aware lookup, hamza/yaa нормализация). ICU analyzer
  частично решает - но качество ниже Elasticsearch `arabic`
  analyzer + ICU фильтры
- Search-relevance scoring (TF-IDF, BM25) - в Postgres базовый, в
  Elastic настраиваемый
- Smart features (typo tolerance, fuzzy, synonyms, аббревиатуры,
  морфологические варианты) - в ES out-of-box
- Cross-app search (одновременный поиск по узлам + книгам +
  ответам Q&A) - удобнее federated через ES indices с правами
- Шкала: после Этапа 17 OCR база lib_pages начнёт расти в гигабайты,
  PG GIN index начнёт жрать память shared_buffers

**Что нужно сделать когда дойдём:**
- ADR на выбор search engine (ES vs OpenSearch vs Meilisearch)
- Docker compose сервис
- Outbox pattern или CDC через Debezium для синхронизации
  PG → ES
- Indices: `nodes`, `lib_pages`, `answers`, `qa_questions` (или
  unified `searchable_text` index с типом entity как field)
- Search service на бэке - REST endpoint с filters
- Frontend - global search box в Header (уже есть unified search
  заготовка из Q4 polish design)

### Editor для кастомизации текста книг (перед OCR pipeline Этапа 17)

**Контекст:** перед запуском OCR pipeline (Этап 17 - распознавание
арабских сканов через Tess4j + AI editing) нужен **редактор страницы
книги** с богатой типографикой. Цель - наши книги должны выглядеть
как классические арабские тахкики (научные издания) с:

- хадис/аят в **выделенной рамке** (например розовый/peach background
  как в Beirut-style академических изданиях)
- **marginalia** - комментарии на полях (мелкий текст слева)
- **footnotes** с decorative separator
- **разные уровни заголовков** с орнаментом (текстовое decoration
  типа ◆ ◇ ❖ или CSS borders)
- **красные key terms** или другие color highlights
- **vocalized text** (с tashkeel/harakat) с возможностью toggle
- **page numbers** в декоративных вьюшках
- inline citations + tooltips

**Reference дизайн:** `frontend/design-reference/v2/project/uploads/`
(добавить туда скрин классического тахкика когда будет дизайн-сессия)

**Рекомендуемая библиотека: Tiptap** (https://tiptap.dev)
- На ProseMirror (mature, battle-tested)
- Headless + React 19 совместим
- **Extension API** позволяет добавить custom типы блоков
  (HadithBox, AyahBox, Marginalia, Footnote, Decoration) - то что
  нам надо
- RTL out-of-box, плюс custom CSS для арабского рендеринга
- ~70K weekly downloads, активный maintenance
- MIT license

**Альтернативы:**
- **Lexical** (Meta) - модерн, performant, но менее зрелый
  extension ecosystem
- **Slate.js** - flexible, но требует больше boilerplate для
  custom блоков
- **CKEditor 5** / **TinyMCE** - heavy enterprise, overkill для нас

**Что нужно сделать когда дойдём до Этапа 17:**

1. **ADR на Tiptap + список custom extensions** (HadithBox / AyahBox /
   Marginalia / Footnote / ColorHighlight / Tashkeel toggle / etc)
2. **Дизайн-сессия:** референсы из tahqiq книг + handoff с
   `frontend/design-reference/v2/project/` где макеты для:
   - редактор-режим (admin)
   - viewer-режим (читатель)
   - export-режим (PDF/SVG)
3. **Storage**: контент Page в БД хранить как ProseMirror JSON
   (`jsonb` колонка `lib_pages.formatted_content`) рядом с plain
   `text_content` (для search через ES). Backward compat для уже
   импортированных через PDFBox (просто wrap plain text в paragraph)
4. **OCR/AI workflow:**
   - OCR (Tesseract `ara` model) → raw text
   - AI editing pass (LLM): расставить headings, выделить хадисы,
     добавить footnotes, нормализовать tashkeel
   - Manual review через Tiptap editor с custom toolbar
   - Save formatted JSON + plain text
5. **Reader улучшения** в `BookReaderPage` - parse Tiptap JSON →
   красивый HTML/CSS рендер с naskh шрифтами, ornaments через CSS,
   правильный RTL flow

**Зачем именно сейчас зафиксировать:** если Этап 17 OCR пойдёт без
этого плана - попадёт в plain text storage и потом переделывать
больно. Принять архитектуру **до** того как набьём data.

### Editor improvements (после закрытия ADR-039)

ADR-039 закрыт - 8 custom Tiptap extensions реализованы (HadithBox /
AyahBox / Marginalia / Footnote / ColorHighlight / Tashkeel /
DecoratedHeading / PageNumber). Что осталось доделать в editor stack
по мере дозревания UX:

- **Custom font для tashkeel toggle через font-feature-settings** -
  альтернативный путь без runtime DOM walk: использовать шрифт где
  tashkeel - отдельные ligature glyphs которые можно скрыть через
  `font-feature-settings`. Требует поиска / создания такого font
  asset (большинство free naskh-шрифтов это не поддерживают)
- **Drag-handle для блочных extensions** (HadithBox / AyahBox /
  Marginalia / DecoratedHeading) - сейчас перемещение между
  параграфами через выделение + cut/paste. Tiptap Drag Handle
  extension даст visible handle при hover, удобнее для admin
- **Collaborative editing (Yjs)** - на будущее когда команда
  редакторов >1, чтобы избежать lost-update конфликтов на одной
  странице. Tiptap имеет `@tiptap/extension-collaboration` based
  on Yjs (CRDT). Стек: WebSocket server + Y.Doc per page
- **Slash menu** (`/hadith`, `/ayah`, `/note`) - быстрый ввод
  custom blocks из клавиатуры без mouse в toolbar.
  `@tiptap/extension-mention`-style approach
