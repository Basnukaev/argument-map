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
- [x] **Source detail panel** - закрыто 2026-05-18. 800px параллельная
      боковая панель (fullscreen на mobile) с полным содержанием
      цитируемого источника. Архитектура: `useSourceDetailPanelStore`
      Zustand (current + isOpen, openWith/close), `SourceDetailPanel`
      mount'ится один раз в App.tsx, открывается из любой точки через
      store. Sections: Metadata (type/title/authority с deathYearHijri/
      citation/reliability), Quote (выделенный blockquote accent-500),
      Context (dashed border), Full Reading (кнопка → /books/{bookId}).
      Integration: SourceCardHeader title опционально clickable
      (`onTitleClick`), прокинуто через SourceCard в NodeCitations/
      QuestionCitations/AnswerCitationsSection. InlineCitationMarker
      popover добавил «Открыть подробнее» ссылку для full panel.
      i18n: 14 ключей `source_detail.*` + 1 `inline_citation.open_detail`
      (RU+AR). 411 frontend tests pass _(SourceDetailPanel)_
- [x] **Library overview** - закрыто 2026-05-18. Extend BookListPage
      (/books) до polished overview. Hero (eyebrow + title + description +
      total count), debounced search 300ms через server-side `?q=`,
      visibility filter chips (Все / Мои / Разделяемые / Публичные) -
      client-side поверх загруженной страницы (backend не поддерживает
      ?visibility=). AuthorityFilter component - autocomplete dropdown
      по `/api/v1/authorities?q=` с debounced search + click-to-select +
      era-suffix + outside-click close. Sort переключён на
      latest/alphabetical (более natural для read-mode). EmptyState
      illustrated с круговой иконкой + описательный текст + CTA на
      /admin/shamela. Cards layout оставлен existing (Card.Cover с
      stable color по bookId + Card.Body с type/lang/visibility badges).
      Routing - оставлен `/books` (per ADR-022 frontend reorg);
      «/library» в спеке - conceptual. i18n: 28 ключей
      `library.overview.*` (RU + AR). 5 новых tests
      (BookListPage.test.tsx: empty state, cards, search debounce, filter
      chips toggle, Load More append). 430 → 435 frontend tests pass.
      Отложено: «Мои» через BookSummary.createdBy (backend не отдаёт);
      server-side sort через ?sort=field,DESC; PDF-only filter
      _(LibraryOverview)_
- [x] **Inline citations** - закрыто 2026-05-18. Подход A (implicit
      ordinal) - `[N]` маркеры в `node.content` mapping'ятся по 1-based
      ordinal на `node_sources` (порядок `created_at ASC`). Backend:
      `NodeResponse` расширен `inlineCitations: InlineCitationRef[]`,
      bulk-load один SQL на весь граф через
      `NodeSourceRepository.findInlineCitationsForNodes` (JOIN sources
      + lib_books). Frontend: `parseInlineCitations` utility + 2
      компонента (`InlineCitationMarker` с click-popover,
      `InlineCitationBody` wrapper) интегрированы в `NodeCard` body
      section и `NodeContentEditor` view-режим. Popover показывает
      title / quote / citation / reliability (HADITH only). Dead-маркеры
      для unknown ordinal'а - grey стиль, tooltip «Источник не найден».
      Без миграции БД - чисто request-level. Tests +29 (15 parser, 8
      marker, 6 body, 5 backend IT). Отложено: Tiptap
      `formatted_content` integration, deep-link на book page,
      explicit `[#sourceId]` для robustness при reorder source'ов
      _(InlineCitations)_
- [ ] **Sanad explorer** - визуализация цепочки передатчиков
      хадиса (8-звенная от Пророка ﷺ до составителя). Каждое
      звено - карточка передатчика (имя / поколение / tier).
      Связи типизированы (`sama'` / `'an'ana` / `haddathana` /
      мункати'). Альтернативные пути. Серьёзная доменная фича -
      потребует расширения доменной модели (новые сущности
      `Rawi`, `Sanad`, `SanadLink`) _(SanadExplorer, SANAD demo
      data)_
- [x] **Multi-grading хадисов** - закрыто 2026-05-18 (backend). Миграция 43
      `hadith_grades` (source × scholar × grade с UNIQUE constraint),
      whitelist `SAHIH/HASAN/DAIF/MAUDU` (добавлен `MAUDU` отдельно от
      legacy `Reliability` enum - не трогаем `Source.reliability`
      single-value). 4 REST endpoint под `/api/v1/sources` (POST/GET
      `{sourceId}/grades`, PATCH/DELETE `grades/{gradeId}`). Permission:
      author либо ADMIN на mutating. GET возвращает denormalized scholar
      info через JOIN на authorities (один SQL без N+1). 23 IT (14 service
      + 9 controller). Frontend UI - отдельная задача ниже
- [x] **Multi-grading UI** - закрыто 2026-05-18 (frontend). Новый
      `HadithGradesSection` в `frontend/src/shared/components/citation/
      sourceCard/` - collapsible под Collapsible-metadata в `SourceCard`
      (виден только при `sourceType=HADITH`), плюс отдельной секцией в
      `SourceDetailPanel` между Context и FullReading. List grades с
      scholar name + deathYearHijri + color-coded badge (SAHIH emerald
      / HASAN blue / DAIF orange / MAUDU rose), gradeCitation italic,
      comment expandable truncate. Edit/Delete только для createdBy
      или ADMIN. Modal с scholar autocomplete по `/api/v1/authorities?q=...`,
      4-state grade radio, citation/comment fields. Локализованные
      ошибки 400 invalid-hadith-grade / 409 hadith-grade-duplicate /
      403 forbidden-hadith-grade-write. i18n hadith.grades.* (34 ключа
      RU + AR). 5 vitest + manual smoke - 430 tests pass
- [x] **Bilingual карточки** - двуязычный режим узла. Закрыто
      2026-05-18. Миграция 44 + поля Node.translation /
      translationLang / originalLang. NodeCard рендерит в 3 режимах
      (original / translation / both) с toggle в card header
      (Languages icon). PreferencesStore ключ `bilingualMode`,
      Settings секция «Двуязычный режим узлов». Один перевод на узел -
      multi-translation см. Translator attribution
- [x] **Translator attribution** - закрыто 2026-05-18. Миграция 45 -
      новая таблица `node_translations` (node × translator × language)
      заменяет single-translation 1:1 модель миграции 44. Backend:
      NodeTranslation domain + repository (bulk findByNodeIds для GET
      /topics/{id}/graph - один SQL на весь граф), NodeTranslationService
      с canWriteTopic guards + atomic default-swap + auto-promote oldest
      при удалении default. 5 endpoint под /api/v1/nodes: POST/GET
      /{id}/translations, PATCH/DELETE /translations/{id}, отдельный
      POST .../default для atomic switch. NodeResponse breaking change -
      убраны translation/translationLang, добавлено translations[].
      Permission: canWriteTopic. Unique constraint через partial indexes
      (NULL и not-NULL translator_name). Спецсемантика: первый перевод
      узла всегда default. 19 service IT + 10 controller IT (всё в
      pass). Frontend: NodeCard читает data.translations[], dropdown
      показывается при >1 переводов (translator name + language +
      ★ default badge), single translation - label под секцией перевода.
      Type regen + 7 vitest pass. i18n node.translations.* (RU+AR).
      Backlog «Translation editor UI» (admin add/edit modal) отложен в
      backlog ниже как low-value MVP - power-users могут через curl,
      обычный flow добавит позже _(TranslatorSection)_
- [ ] **Translation editor UI** - admin add/edit modal для добавления
      переводов через UI (сейчас только curl). Modal с polish (translator
      name autocomplete по past entries, language radio, body textarea,
      isDefault checkbox с warning «текущий default потеряет флаг»),
      кнопки + Edit/Delete по carret-menu рядом с dropdown items в
      NodeCard. Low priority - power-users могут через curl до тех пор
- [ ] **Tashkeel toggle** - на canvas карточки можно отключить
      огласовки (`harakat`) для краткости. Side-by-side
      сравнение с / без _(TashkeelSection)_
- [ ] **RTL-режим** - для арабского UI: зеркальный layout графа,
      RTL-toolbar, naskh / kufi-шрифты. Большая работа, выделить
      в отдельный этап _(RTLGraphScreen, RTLSection)_
- [ ] **Language switcher (RU / EN / AR)** - в header или
      settings. Идёт в комплекте с i18n и RTL
      _(LanguageSwitcher)_
- [x] **Settings screen** - язык (RU/AR/EN), выбор арабского шрифта
      (Naskh/Kufi/Tahoma), размер текста (small/medium/large/xl),
      тогглы tashkeel/транслит, theme. Persist на бэке через
      `user_preferences` (миграция 42) + localStorage cache для FOUC.
      Drag-приоритет источников остался в backlog (отдельная work)
- [x] **Onboarding** - закрыто 2026-05-18. Floating widget bottom-end
      (320px) с 4-шаговым чеклистом: `create_topic`, `add_root_question`,
      `add_claim_node`, `attach_source`. Detection через single fetch
      (GET /topics + GET /topics/{id}/graph для первой owned темы).
      Steps clickable - navigate на relevant page. Collapsible mini
      state, dismiss X с persist в localStorage `onboarding_dismissed`.
      Auto-dismiss + celebration toast при completed=4. 18 i18n keys
      `onboarding.*` (RU+AR). Tests +11 (7 hook + 4 component).
      Отложено: hint-указатели на canvas (`OnboardingHint`) с
      bouncing arrow icon для active step - сложность low value,
      MVP без них
- [x] **Topic settings drawer** - 480px end-side drawer над dimmed
      canvas с consolidated settings (Сессия 37). Sections: root
      question (read-only, lock + hint) / visibility radio
      (PRIVATE/SHARED/PUBLIC) / members compact preview top-3 +
      expand link в TopicMembersModal (только SHARED) / status
      algorithm radio (MVP vs DUNG_GROUNDED) / audit log link для
      ADMIN / danger zone с typing topic name confirmation для
      DELETE. Mobile fullscreen overlay. Заменили inline visibility/
      members controls на единый gear IconButton в crumb-bar.
      41 i18n key `topic.settings.*` (RU + AR). 7 тестов
- [x] **Multi-select с floating action bar** - Shift+click либо
      Meta (⌘) добавляет узел к выделению, lasso через drag по pane.
      FloatingActionBar (bottom-center pill, dark slate-900 с indigo-
      акцентным счётчиком) появляется при >0 выделенных. Actions:
      Удалить (переиспользует existing runDelete с root-filter +
      Undo toast) / Изменить статус (popup STANDING/DISPUTED/REFUTED,
      Promise.allSettled c partial-failure handling) / Снять (=Esc).
      Bulk delete и единичный Del работают через единый runDelete
      handler. graphSelectionStore (Zustand Set<string>) - источник
      истины для bar и handlers. 13 i18n keys `bulk_actions.*`
      (RU + AR). 16 тестов (store 7 + bar 9). Backlog: bulk-move +
      bulk-export отложены как low-value extensions
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
- [x] **Реализация Dung's argumentation framework** - закрыто
      2026-05-18 (Сессия 38, ADR-044). Миграция 41 + `topics.status_algorithm
      VARCHAR(20) CHECK MVP|DUNG_GROUNDED` (default MVP), `DungFrameworkService.
      computeGroundedLabelling(nodes, edges)` - iterative grounded labelling
      по attack-edges (REFUTES + INVALIDATES). Mapping IN→STANDING/OUT→REFUTED/
      UNDEC→DISPUTED. `StatusCalculationService` диспатчит MVP / DUNG_GROUNDED
      по `topic.statusAlgorithm`. Новый endpoint `PATCH /api/v1/topics/{id}/
      status-algorithm` (owner only, side-effect recalc). 15 unit + 8 IT.
      **Frontend toggle** оставлен в backlog отдельным пунктом ниже -
      пока доступно только через curl. Preferred/stable extensions и
      bipolar argumentation отвергнуты в ADR-044
- [ ] **Frontend UI для переключения status-algorithm** - бэкенд готов
      (PATCH endpoint работает), фронту нужен toggle в TopicMetaPanel /
      TopicSettingsModal для owner'а. Сейчас power-user'ы могут через curl,
      но обычным пользователям нужен GUI
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

- [ ] **AuditEntityType / UserRole single source of truth** - сейчас BE
  константы String + FE whitelist в `dictionary/types.ts`. Расхождение
  возможно при добавлении новых типов (BE добавит, FE не узнает пока
  не запустит против реального API). Fix: BE enum (Java) + OpenAPI
  generation выдаёт union literal в `types.ts` автоматически. Альтернатива -
  явный shared enum в api-contract spec. Low priority пока number
  entity types <15 (сейчас 7: TOPIC/NODE/EDGE/BOOK/QUESTION/ANSWER/
  TOPIC_MEMBER). Reviewer flag round 3 #2
- [ ] **Authority.type column для HadithGrade scholar validation** -
  сейчас `HadithGradeService.addGrade` принимает любой UUID authority
  как scholar, даже если это PUBLISHER или MUHAQQIQ. Семантически
  неверно: оценивать хадис как «sahih» может только muhaddith, не
  издательство. Fix: добавить `authorities.type` column (whitelist
  SCHOLAR / MUHAQQIQ / PUBLISHER / AUTHOR / другие) + валидация в
  HadithGradeService.addGrade при resolve scholar. Альтернатива - принять
  flat namespace authorities и записать как explicit design decision
  в ADR (правда тогда нужны соглашения типа suffix в name «Bukhari
  (muhaddith)» для разрешения disambiguation в UI). Reviewer flag
  round 3 #4
- [ ] **Audit log для удалённых тем недоступен через /audit/topics/{id}** -
  `permissionService.assertCanWrite(topicId)` бросает 404
  topic-not-found если тема удалена (CASCADE на topics → удалены и
  все child audit). Для compliance scenario (кто/когда удалил тему)
  admin может использовать `GET /audit/admin?entityType=TOPIC&entityId=`,
  но usability так себе: нужно знать UUID удалённой темы. Fix: special
  case в AuditLogController - если topic deleted, всё равно вернуть
  audit history (без assertCanWrite). Либо explicit
  `TopicAlreadyDeletedException` → 410 Gone с link на admin endpoint.
  Reviewer flag round 3 #6
- [ ] **Z-index renormalization для long-running тем** - max+1 / min-1
  pattern на 32-bit int даёт практически безграничное space (2.1B
  операций bring-to-front пока не уйдёт в overflow), но теоретически
  уплывёт на edge cases (бот-driven автоматизация, многолетние
  collaborative темы). Renormalize (compact all z_index в continuous
  integer sequence 1, 2, 3, ...) при достижении большого spread
  (e.g. abs(max) > 1_000_000). Pattern из CAD/diagramming tools.
  Low priority - real-world spread <100 у большинства тем

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

- [ ] **BookSummaryResponse.createdBy для accurate «Мои» filter в
      Library overview** - сейчас фильтрация книг текущего user'а в
      Library overview через approximation `visibility === 'PRIVATE'`
      (works in practice т.к. RBAC: privata = owner-only). Hrupkij:
      если кто-то расширит visibility model или owner поделится своей
      книгой как SHARED - approximation сломается. Fix: добавить
      `createdBy: UUID` в `BookSummaryResponse` (full sync с
      `BookResponse`) + frontend фильтрует строго `book.createdBy ===
      currentUser.id`. Reviewer round 4 #8

- [ ] **PATCH /api/v1/topics/{id} для title/description editing** -
      сейчас readonly в `TopicSettingsDrawer`. Нет REST endpoint для
      переименования темы (visibility patch есть, но title нет).
      User'у приходится создавать новую тему вместо переименования.
      Fix: PATCH endpoint + form в settings drawer + IT тесты на
      audit log для UPDATE с FieldDiff(title, description). Reviewer
      round 4 #10

- [ ] **Bulk audit log consolidation - single BULK_DELETE / BULK_STATUS
      action с entityIds[]** - сейчас каждый bulk delete/status change
      создаёт N rows в audit_log (один per entity). При bulk operation
      на 50 узлах - 50 audit rows. Сложно прочитать в admin UI:
      «удалил 50 узлов» воспринимается как 50 не связанных событий.
      Fix: новый action `BULK_DELETE` / `BULK_UPDATE` с массивом
      `entityIds` в changes JSON. Сейчас acceptable т.к. audit
      admin UI отложен. Reviewer round 4 recommendation

- [ ] **NodeTranslationService DRY: extract `promoteToDefault` helper** -
      сейчас в `addTranslation` (через `setDefault`) и
      `removeTranslation` (через oldest + setDefault) логика «сменить
      default-перевод узла» дублируется. После rounds 4 fix #2 уже
      используется setDefault как atomic helper, но оставшийся
      duplicate - выбор кого promote'ить (новый перевод vs oldest
      remaining). Fix: private helper `promoteToDefault(nodeId,
      candidateId)` - один вход для default-switching. Reviewer round
      4 recommendation #2

## Security backlog

Cross-cutting security improvements flagged code review round 5. Не
делаем в текущем этапе (scope-creep на handoff) - закрываем отдельным
security-focused этапом

- [x] **Rate limiting на /auth/login + /auth/register** - закрыто
      2026-05-19 (ADR-046). Custom in-memory sliding-window filter
      перед JWT в SecurityFilterChain, применяется только к 2 path.
      ConcurrentHashMap per (IP, endpoint), sliding window 1 минута,
      lockout 15 мин при превышении. Default limits 5/min login,
      3/min register. Property `auth.rate-limit.enabled=false` по
      умолчанию, prod opt-in через `AUTH_RATE_LIMIT_ENABLED=true`.
      Whitelist `127.0.0.1` + `::1` для CI/smoke. IP extraction:
      X-Forwarded-For (first) > X-Real-IP > remoteAddr с
      port-stripping (защита от обхода `127.0.0.1:9999`). Clock
      injected (AuthClockConfig) - тесты deterministic без
      Thread.sleep. Lazy cleanup stale entries каждые 256 calls -
      защита от memory leak при random-IP atак. Direct 429 + Retry-After
      + ProblemDetails JSON без exception unwinding (economy CPU на
      hot path). 20 новых тестов (8 IT + 9 unit + 3 misc).
      Migration to Redis/Hazelcast - при scale-out (см. ADR-046
      trigger to revisit). Bucket4j / Resilience4j / gateway-level
      отвергнуты как overkill для single-instance MVP
- [ ] **Actuator endpoints behind auth в prod** (Crit Cross-cutting #7) -
      сейчас `/actuator/**` permitAll во всех profiles. Эндпоинты
      circuit breakers / health details / info содержат версию backend,
      DB connection state, registered beans - reconnaissance leak для
      attacker. Liveness/readiness probes (`/actuator/health`,
      `/actuator/info`) остаются public для load balancer. Fix: в prod
      profile - basic auth (`spring.security.user.name/password` env),
      либо restrict через network layer / API gateway (LB allows только
      internal cidrs для actuator routes). Reviewer round 5 Crit
      Cross-cutting #7
- [x] **Refresh token rotation** (Important Cross-cutting #4) -
      реализован 2026-05-19 (ADR-047). Single-use refresh с tracking в
      `refresh_tokens` таблице, SHA-256 hex hashing, steal detection
      через revoke-all-by-user при reuse. Миграция 46 + RefreshToken
      domain + RefreshTokenRepository + AuthService rotation logic +
      AuthServiceRotationIT (8 IT) + adapt существующих AuthControllerIT.

- [ ] **RefreshTokenCleanupJanitor** - daily cron DELETE revoked старше
      30 дней + expired never used. Pattern уже есть в
      `AuditLogRetentionJanitor` (`@ConditionalOnProperty` +
      `@Scheduled`, retention property, AuditLogRepository.deleteOlderThan).
      Replicate как `RefreshTokenCleanupJanitor` с config
      `refresh-token.cleanup.{enabled,retention-days,cron}`,
      `RefreshTokenRepository.deleteOlderThan(cutoff)`. Без это
      таблица растёт линейно от login activity (миллионы revoked rows
      через год). ADR-047 рекомендует запускать после Этапа 25.c

- [ ] **Edge z-order persistence** - mirror Node.zIndex (миграция 40,
      NodeContextMenu bringToFront/sendToBack). Сейчас edge z-order
      ephemeral (`zRef` counter в GraphCanvas), теряется на refetch.
      Pattern уже есть - apply к edges: миграция `edges.z_index INT
      NOT NULL DEFAULT 0`, EdgeRepository.updateZIndex /
      findMax/findMin, EdgeService.bringToFront/sendToBack с
      assertCanWrite, REST endpoints `POST /edges/{id}/z-order/{bring-to-front,
      send-to-back}`. Frontend buildFlow читает edge.zIndex, optimistic
      update в context menu. Low priority - z-order на edges редко
      важен пользователю. Reviewer round 5 Bonus #7

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
