# Бэклог

Идеи и задачи без привязки к активному этапу. Не закрытые - в
`docs/roadmap.md`. Закрытые в общем виде - в `docs/progress.md`

Когда задача созревает (становится приоритетной или блокирует
другую) - переезжает в новый Этап в `roadmap.md`

> **Сессия 49d vision expansion (2026-05-20):** Абдула задал большой
> список новых целей в начале сессии. Полный structured список — в
> `docs/superpowers/specs/2026-05-20-vision-expansion-49d.md`. Items
> ниже отражают/ссылаются на этот документ. Большие фичи получают
> отдельные design-specs в `docs/superpowers/specs/` по мере
> приоритезации.

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
  через test-regression-diagnoser subagent. Root cause найдено
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
- [x] **Frontend migration `runDelete` → `DELETE /api/v1/nodes/bulk`** -
      закрыто 2026-05-20 (commit `9d9cc37`). `runDelete` в
      `GraphCanvas.tsx` использует единый bulk endpoint с
      `{nodeIds: [...]}` payload, новый `apiDeleteWithBody` helper в
      `shared/api/client.ts`. Single HTTP roundtrip + atomic
      BULK_DELETE audit row. `skippedRootIds` обрабатывается gracefully.
      2 новых теста в `bulkActions.test.tsx`
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
- [x] **Z-index persistence для edges** - закрыто 2026-05-19 в Сессии
      47 (Tech debt task #1). Mirror Node.zIndex pattern: миграция 48
      (edges.z_index INTEGER NOT NULL DEFAULT 0), Edge.zIndex field,
      EdgeRepository updateZIndex/findMaxZIndex/findMinZIndex (через
      JOIN nodes для topicId), EdgeService bringToFront/sendToBack +
      assertCanWrite, POST endpoints `/api/v1/edges/{id}/z-order/*`,
      frontend useGraphZOrder switched от ephemeral counter к API
      (optimistic + onRefetch sync). EdgeServiceIT 20→25, EdgeZIndexIT
      6 tests. EdgeResponse.zIndex field

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
- [ ] **FILE_ONLY bbox-citation CREATION (roadmap 25.f, region
      selection)** — `CitationPicker` сейчас цитирует только
      text-страницы (`bookState.pages`); для FILE_ONLY книг
      (archive.org сканы, 0 текстовых страниц) нужен PDF-режим
      выбора: показать PDF-страницу + нарисовать bbox
      (react-image-crop) → создать citation с pdf-локацией
      `{fileId, pageNumber, bbox}`. **DISPLAY-сторона** (подсветка
      bbox при переходе по deep-link `?bbox=x,y,width,height` —
      overlay поверх PDF-страницы в `PdfViewer`) **сделана в
      Сессии 55**; остаётся CREATION (рисование/выбор области)
      _(CitationPickerPdfRegion)_.
      **⚠️ АРХИТЕКТУРНЫЙ БЛОКЕР (найдено Сессией 55):** `CitationRequest.pdfFileId`
      — это UUID FK на `library_files(file_id)`. Но archive.org FILE_ONLY книги
      хранят PDF в `metadata.pdf_links`, а НЕ в `library_files` (там только
      USER_UPLOAD). → для archive.org книг нет `pdfFileId`, pdf-локационную
      цитату создать нельзя. Нужно **решение по модели** прежде чем делать UI:
      либо (а) расширить citation-модель ссылаться на pdf_links по `fileIndex`
      (новая колонка/режим в node_sources + CHECK), либо (б) регистрировать
      archive.org тома в `library_files` при импорте. Это design-задача (нужен
      выбор Абдулы), не быстрый фронт-фикс. Плюс сама UX рисования bbox требует
      визуальной итерации (playwright env-blocked). Поэтому отложено осознанно.
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

- [ ] **Связаться с alminasa.ai (مركز تميز) до массового краулинга** —
      продуктовое решение Абдулы (идея из консультации 2026-06-04).
      Два академических проекта в одной нише: написать им — возможно
      дадут официальный дамп/доступ или благословение со ссылкой на
      источник. Технически краулер и так вежливый (1 стр/с, ответы
      CDN-кэшированы) и атрибуция фиксируется в метаданных (спека
      Сессии 56 §G), но официальный контакт снимает юр./этический
      вопрос целиком. До ответа — не запускать полный обход 12
      сборников (dev-краулинг отдельных страниц для отладки — ок).

- [x] **Isnad persistence-on-import** — закрыто 2026-06-03 (ADR-059
      amendment). Иснад теперь ПЕРСИСТИТСЯ на импорте в
      `hd_sanads`/`hd_narrators`/`hd_sanad_narrators` (single-import —
      default ON; bulk — opt-in `?extractIsnad=true`). Дедуп нарраторов
      по normalized-name (`ArabicTextNormalizer` +
      `NarratorRepository.findByNameArNormalized`), один передатчик =
      одна строка `hd_narrators`, переиспользуемая между хадисами/цепями.
      Идемпотентность повторного импорта — delete-recreate per hadith
      (`SanadRepository.deleteByHadithId`). Реальный `/hadith`-explorer
      (`SanadGraphService.buildGraph`) теперь показывает граф для
      импортированных хадисов. `IsnadPersistenceService` +
      `IsnadPersistenceServiceTest`/`IsnadPersistenceIT`.

- [ ] **Rijal narrator dedup + bio enrichment** (follow-up к ADR-059
      amendment). Дедуп по normalized-name — MVP, несовершенен:
      **омонимы** (разные исторические личности с одинаковой
      нормализованной формой) ложно сольются, а **вариативность
      написания** (الحميدي / عبد الله بن الزبير الحميدي — это один
      передатчик) наоборот раздвоит. Шаг: настоящая rijal-резолюция через
      авторитетный справочник передатчиков (alminasa / иной) — маппинг
      имени на каноничную личность + обогащение био (даты рождения/смерти
      по хиджре, надёжность, поколение, kunya/laqab). Сейчас узлы
      импортированного иснада несут только арабское имя.

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
- [x] **Frontend UI для переключения status-algorithm** - **уже сделано
      ранее** в commit `7990b13` (2026-05-18). Section «Алгоритм статусов»
      в `TopicSettingsDrawer.tsx` с radio cards MVP/DUNG_GROUNDED,
      `handleAlgorithmChange` шлёт PATCH `/api/v1/topics/{id}/status-algorithm`.
      Gated через `canManage` для read-only users. 12 tests в
      `TopicSettingsDrawer.test.tsx`. Backlog запись была stale,
      cleared в Сессии 49
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

### Code-review findings (Сессия 55, 2026-06-03) — deferred Minor

- [ ] **migration 69 (content_kind) HAS_FILE предикат** использует
  `jsonb_array_length` без `jsonb_typeof(...)='array'` guard — латентная
  хрупкость (не сработала, все live-данные = array). Нельзя править
  применённый changeset (checksum); затянуть
  `jsonb_typeof(...)='array' AND ...` при следующем касании файла /
  в новой миграции если понадобится.

### Code-review findings (Сессия 53, 2026-06-01) — Phase 5 ETL шаг 2 deferred Minor

Из multi-agent review (5 измерений → adversarial verify, 0 Critical, все 6
Important + большинство Minor закрыты в сессии). Сознательно отложенные Minor:

- [ ] **Concurrent-run idempotency** (Minor): `SunnahToHadithMapper` —
  find-then-insert (TOCTOU). Для admin-triggered single import безопасно
  (UNIQUE-констрейнт ловит), но параллельные прогоны одного сборника дали бы
  сырой constraint-violation вместо чистого skip. Если появится параллельный
  импорт: advisory lock по slug либо `INSERT ... ON CONFLICT DO NOTHING`.
- [ ] **Расширенные формы хамзы/алифа** (Minor): NFKC + текущие folds
  покрывают обычный текст и presentation forms; редкие U+0672/0673/0675/0676/
  0677 (alef/waw with wavy hamza) пройдут verbatim. Добавить в switch если
  встретятся в реальном дампе.
- [ ] **hadith→book/chapter referential gap** (Minor, by design): в
  `sn_staging_hadith` `book_number`/`chapter_id` — мягкие атрибуты без FK
  (hasBooks/hasChapters опциональны). Опционально: mapper логирует/считает
  хадисы с (book,chapter), не находящими staging-родителя.

**Шаг 2.d (SunnahDumpReader, Сессия 53):**
- [ ] **Admin REST-триггер импорта sunnah** + прод-config MySQL-DataSource
  (`SunnahDumpProperties` url/user/pass/enabled + conditional bean). Нужно
  чтобы запустить импорт против реального дампа вне тестов. AdminShamelaPage-
  стиль, под bulk-policy gate (превью staging до commit).
- [ ] **`SunnahDumpReader.readChapters` INNER JOIN** по `arabicBookID`: в
  реальном дампе `BookData.arabicBookID` nullable → главы книги с
  `arabicBookID IS NULL` отбросятся (главы теряются, хадисы — нет). Для
  Бухари+Муслим arabicBookID заполнен. Fix при расширении объёма: LEFT JOIN
  + fallback резолва book_number, либо валидация.
- [ ] **Whole-collection in-memory List** в reader/import: `readHadiths`
  материализует весь сборник в память (~7.5k строк для Сахихайн — ОК). При
  расширении за пилот / API-источнике — пересмотреть на Stream/пагинацию.
- [ ] **grade-парсинг dump**: `arabicgrade1`/`englishgrade1` кладутся как
  `[{graded_by:"", grade: текст}]`. Грейдер ("Darussalam" из "Sahih
  (Darussalam)") не извлекается. Улучшить при необходимости.

**Шаг 2.e (прод-обвязка + реальный пилот, Сессия 53):**
- [x] **HTML/markup в тексте дампа** — **исправлено Сессией 53** (под-проект #1):
  оказалось, что разметка (HTML `<p>`, quran-якоря `<A href=openquran>`, footnote
  `<c_qNN>`) есть и в арабском, и в английском, и текла в `normalized_matn`
  (поиск). `SunnahTextCleaner` срезает её в reader для bodyAr И bodyEn;
  перечистка 98 импортированных через delete+reimport. + previewMatn
  (диакритизированный) на карточке + редизайн HadithListPage.
- [ ] **Frontend AdminSunnahPage** (AdminShamelaPage-стиль): кнопки
  «превью каталога» + «импорт сборника» поверх `/api/v1/admin/sunnah/*`.
  Типы уже в `types.ts` (generate-api). Чтобы Абдула триггерил импорт без curl.
- [ ] **Полный корпус**: репо-sample `00-samplegitdb.sql` = только 100
  хадисов Бухари (muslim/др. — только метаданные сборников). Полный корпус —
  через `SunnahApiClient` (шаг 4) либо полный дамп от sunnah.com.

### Code-review findings (Сессия 52, 2026-06-01) — ADR-043 sweep gaps

Из code-review fix-волны (4 reviewer-агента). Реальные, но out-of-scope
для закрытых 6 authz-дыр — тот же системный паттерн «эндпоинт не зовёт
permission-модель», который надо домести чтобы sweep был полным:

- [x] **NodeSourceController без topic-authz** — закрыто (commit 5f27689,
  Сессия 52). attach/detach → assertCanWrite на тему узла, list →
  assertCanRead. @CurrentUser прокинут. detach остаётся node-scoped.
- [x] **Q&A citation controllers — unscoped detachById** — закрыто
  (commit 5f27689). create/detach → автор вопроса/ответа или ADMIN; detach
  стал question/answer-scoped (deleteByIdAndQuestion/deleteByIdAndAnswer,
  404 при mismatch). GET без guard (open discussion). Завершает ADR-043 sweep.
- [x] **AI-edit stuck-PROCESSING liveness** — закрыто Сессия 55 Фаза 12:
  `tryClaimAiEditProcessing` теперь выигрывает также при stale PROCESSING
  (`OR ai_edit_started_at IS NULL OR ai_edit_started_at < now() -
  make_interval(mins => ?)`), интервал из `ai.edit.processing-timeout-minutes`
  (default 10). 3 IT-кейса (fresh-non-proc wins / fresh-proc loses /
  stale-proc wins + started_at refreshed).
- [ ] **Thesis `إعداد:` author-loss** — partial-parse прячет raw
  description (guard `!hasStructuredMetadata`); у диссертаций автор иногда
  только в `إعداد:` и не резолвится в shamela authorId → теряется. Либо
  парсить إعداد в authority/thesis, либо показывать raw когда есть
  непокрытые structured-полями строки.
- [ ] **Repository round-trip IT для thesis-колонок** — есть только parser
  unit-тесты; нет IT что `save()`/`updateThesisMetadata` реально
  персистят/читают thesis_* через Postgres (защита от reorder/typo).
- [ ] **HadithListPage test asymmetry** — debounce+пагинация добавлены в
  HadithListPage и NarratorListPage идентично, но тесты только у Narrator.
  Кандидат на извлечение общего `usePagedSearch` хука + один тест.
- [ ] **Load More stale-append race** — `loadMore` без AbortController:
  смена query во время in-flight page-N аппендит stale items на свежий
  page-0. Тот же паттерн что в BookListPage. Fix: query-ref guard в .then.

### Bug-hunt Tier-3 (Сессия 52, 2026-06-01) — 30 low-severity

Из multi-agent багоохоты (235 агентов, 48 подтверждённых; HIGH security +
medium закрыты в Сессии 52, см. `docs/superpowers/audits/2026-06-01-bug-hunt-handoff.md`).
Остаток — low severity, ни один не критичен. Канон фиксов — в handoff'е.

Security hardening:
- [x] **AuthService login timing side-channel** — malformed dummy bcrypt
      hash в timing-protection path → email-enumeration по времени ответа
      остаётся. `AuthService.java:72`. **Закрыто:** заменён на валидный
      `DUMMY_BCRYPT_HASH` (malformed → KDF не запускался); not-found И
      null-password пути теперь прогоняют bcrypt против него (constant-time).
      Tests: `AuthServiceDummyHashTest` (2 unit) + `AuthServiceIT.login_notFoundWrongAndDisabled_returnSameGenericMessage`.
- [x] **Disabled-account login leak** — отдельное error-сообщение +
      проверка после password check → утечка валидности credentials.
      `AuthService.java:83`. **Закрыто:** disabled → тот же generic
      `INVALID_CREDENTIALS_MESSAGE` что и wrong-password (не различаем
      enabled/disabled). Tests: `AuthControllerIT.POST_login_disabledAccount_returnsSameGeneric401AsWrongPassword`
      (тот же type+detail) + service-level выше.
- [x] **ShamelaArchiveExtractor decompression bomb** — нет per-entry /
      total size cap → возможно disk exhaustion. `ShamelaArchiveExtractor.java:59`.
      **Закрыто:** per-entry (2 ГБ) + total (8 ГБ) + entry-count (10k) caps,
      считаем фактически записанные байты (не доверяем `getSize`), partial-файл
      удаляется при abort. Лимиты инжектятся через конструктор для тестов.
      Tests: 4 новых в `ShamelaArchiveExtractorTest` (per-entry / total / count /
      within-limits passes).
- [ ] **View-count inflation** — `POST /books/{id}/views` unauthenticated +
      unbounded (anti-spam dedup отложен Phase 2.b). `BookController.java:113`.

Concurrency (применить tryClaim-паттерн как в AI-edit #10 / refresh #4):
- [x] **OCR re-trigger concurrent** — нет check-then-act guard → duplicate
      OCR, last-write-wins. `OcrController.java:73` + `OcrService.java:127`.
      **Закрыто:** атомарный claim `PageRepository.tryClaimOcrProcessing`
      (зеркало `tryClaimAiEditProcessing`) — conditional UPDATE
      `SET ocr_status=PROCESSING WHERE ocr_status IS DISTINCT FROM PROCESSING`,
      возвращает rows==1 только winner'у. `OcrService.recognize` после
      precondition'ов вызывает claim; loser выходит не запуская Tesseract.
      Tests: `OcrServiceConcurrencyIT` (2: already-PROCESSING → bail без
      перезаписи; no-image → FAILED до claim) + `PageRepositoryIT`
      (first wins / second loses).
- [x] **ShamelaAuthorityResolver find-then-insert** — без uniqueness guard
      → дубли authorities при concurrent import. `ShamelaAuthorityResolver.java:69`.
      **Закрыто:** миграция 66 — UNIQUE index на `authorities(name)`
      (натуральный ключ дедупликации; заменил non-unique idx, dedup
      существующих перед constraint с repoint живых FK sources/hadith_grades/
      lib_books). `AuthorityRepository.saveIgnoreConflict` —
      `INSERT ... ON CONFLICT (name) DO NOTHING` + re-select → возвращает
      каноническую строку при проигрыше гонки. Resolver (resolve +
      resolveAnonymous) использует его вместо `save`. Tests:
      `AuthorityRepositoryIT` (3: idempotent re-insert, UNIQUE violation на
      прямом save, new insert) + `ShamelaToLibraryMapperIT` (2: resolve/
      anonymous дважды → один row).
- [x] **AnthropicClient retry на permanent 4xx** — multiplies cost +
      stall FAILED signal. `AnthropicClient.java:133`.
      **Закрыто:** retry-политика через `retry-exception-predicate`
      (`AnthropicTransientFailurePredicate`) вместо `retry-exceptions`:
      retry ТОЛЬКО на transient (IOException, statusCode 0=connection/timeout,
      429, 5xx); permanent 4xx (400/401/403/404) и невалидный LLM JSON
      (mapped 200) НЕ повторяются. `application.yml`
      `resilience4j.retry.instances.anthropicApi`. Test:
      `AnthropicTransientFailurePredicateTest` (7: 5xx/429/IO/timeout → retry;
      400/401/403/404/409/422/200 → no retry).

Logic:
- ~~[ ] **OcrService NULL→FAILED**~~ — **(удалено в Сессии 55, ADR-057 — OCR выпилен)**
- [x] **updateOcrStatus COALESCE** preserves stale ocr_completed_at при
      re-run DONE/FAILED. `PageRepository.java:205`. **Закрыто** заодно с #1:
      `tryClaimOcrProcessing` при claim PROCESSING явно ставит
      `ocr_completed_at = NULL` (не COALESCE) — re-OCR ранее завершённой
      страницы больше не показывает stale «завершено» пока статус PROCESSING.
      Test: `PageRepositoryIT.tryClaimOcrProcessing_clearsStaleCompletedAt`.
- [x] **ShamelaChapterMapper** silently drops главы в parent-ref cycle
      (no error/log). `ShamelaChapterMapper.java:67`. **Закрыто (Сессия 55
      Фаза 10):** после BFS детектим непрозвавшиеся title (застрявшие в
      цикле A→B/B→A или самореференции) → `log.warn` с id/title + причиной,
      привязываем к root как fallback (книга импортируется целиком, потеря
      наблюдаема). Test: `ShamelaChapterMapperTest` (4: чистое дерево без warn,
      2-node цикл, non-existent parent = orphan-as-root без warn,
      самореференция).
- [x] **ShamelaBibliographyParser** dash-split mis-routes publisher →
      publication place. `ShamelaBibliographyParser.java:95`. **Закрыто
      (Сессия 55 Фаза 10):** брутальный char-length-ratio guard
      (`candidate.length() < publisher.length()/2 + 1`) молча НЕ резал
      короткого издателя с длинным топонимом («دار طيبة - المملكة العربية
      السعودية»: 24 ≥ 18) — место издания оставалось приклеенным к publisher.
      Заменён на word-count guard (топоним ≤5 слов даже когда длинный в
      символах). Минимальная правка, 12 (→15) существующих фикстур зелёные.
      Test: `ShamelaBibliographyParserTest` +2 (короткий издатель + длинная
      страна разделяются; длинная клауза >5 слов НЕ режется).
- [x] **QuestionService updateQuestion** body="" вместо NULL (contra
      документированной clear-to-null семантики). `QuestionService.java:156`.
      **Закрыто:** blank body теперь очищается в `NULL` (не `""`) через новый
      `clearBody` флаг в `QuestionRepository.update(...)` 5-арг overload
      (4-арг сохранён для internal). Tests: `QuestionServiceIT` (4 новых:
      empty/whitespace → NULL в БД, null = no-change, non-blank = trimmed).
- [x] **acceptAnswer на CLOSED вопросе** silently reopens lifecycle →
      ANSWERED. `AnswerService.java:194`. **Закрыто:** выбрана семантика
      **reject** (CLOSED — терминальное модераторское состояние; принятие
      обходило бы модерацию). Новый `QuestionClosedException` → `409
      question-closed`. Guard в `acceptAnswer` legacy overload (role-overload
      делегирует). api-contract обновлён. Tests: 2 новых в `AnswerServiceIT`
      (не reopen + role-overload тоже blocked).
- [x] **HadithController stale `bookId` query param** после Phase 5
      collection rename. `HadithController.java:62`. **Уже исправлено** ранее
      commit `94309dc` (под-проект #1.B): `bookId` query param переименован в
      `collectionId` (repository + controller + frontend + api-contract все на
      `collectionId`). Сейчас в hadith-контроллерах нет ни одного stale
      `bookId` `@RequestParam` (grep подтвердил). Кода/тестов менять не нужно —
      `HadithControllerIT` (7) зелёный.
- [x] **getDetail O(sanads×links)** per-sanad linear scan narrator links.
      `HadithController.java:101`. **Закрыто (Сессия 55 Фаза 10):** вложенный
      `allLinks.stream().filter(sanadId==)` на каждый sanad заменён на
      `groupingBy(SanadNarrator::sanadId)` ОДИН раз + lookup O(1). Внутри
      группы defensive-sort по position (bulk-query уже `ORDER BY sanad_id,
      position`, поведение идентичное). Чистый perf-рефакторинг. Test:
      `HadithControllerGetDetailTest` (multi-sanad × multi-narrator —
      группировка не протекает между sanad'ами, порядок position сохранён) +
      `HadithControllerIT` getDetail (7) зелёный.
- [x] **TopicListPage post-import refetch** теряет active sort order.
      `TopicListPage.tsx:155`. Закрыто Tier-3 batch: миграция на
      `usePagedSearch` (`buildUrl` замыкает `sort`, `deps:[sort,refreshKey]`)
      гарантирует что post-import refetch (bump `refreshKey`) несёт
      активный sort. Тест: смена sort→popular + импорт → refetch URL
      сохраняет `sort=popular`.
- [ ] **MinimapCard drag/clamp** использует content bounds без padding →
      drag snaps inconsistently. `MinimapCard.tsx:317`.
- [x] **useViewTracking** marks view sent до resolve POST → failed first
      POST не retry. `useViewTracking.ts:29`. Закрыто Tier-3 batch:
      dedup-флаг `sessionStorage` ставится теперь ТОЛЬКО в `.then()`
      успешного POST (был до запроса). Упавший POST не блокирует retry
      на следующем визите. Cancelled-guard на unmount. Тесты: failed
      first POST → нет флага → retry на следующем mount; success → флаг.

Accessibility / UX:
- [x] **ContextMenu off-screen** near canvas edges (нет viewport clamp).
      `ContextMenu.tsx:52,54`. Закрыто Tier-3 batch: `useLayoutEffect`
      измеряет меню после mount и зажимает позицию через чистую
      `clampMenuPosition()` (вынесена в `contextMenuPosition.ts`) — сдвиг
      внутрь viewport при overflow у правого/нижнего края, минимум =
      margin (8px). Тесты на clamp-логику. **Keyboard-nav (arrow keys)
      не сделан** — остаётся отдельным улучшением.
- [x] **Toaster error toasts** 'polite' вместо 'assertive' aria-live.
      `Toaster.tsx:74`. Закрыто Tier-3 batch: per-toast aria-live —
      error/warning → `assertive` + `role=alert`, info/success → `polite`
      + `role=status`. aria-live снят с обёртки (иначе перебивал бы
      per-toast assertive). Тесты на aria-live/role по типу.
- [x] **QuestionDetailPage delete-кнопка** видна всем (нет ownership
      gating, inconsistent с answer-level). `QuestionDetailPage.tsx:248`.
      Закрыто Сессия 55 Фаза 11: overflow-меню (смена статуса + удаление)
      теперь gated через `useAuthStore` — `user.id === question.askedBy ||
      role === 'ADMIN'` (mirror HadithGradesSection/AnswerCard). DTO имеет
      `askedBy` → полный author+ADMIN gating. Тесты: author/admin видят,
      non-author/anon — нет.
- [x] **AnswersSection** single busyAnswerId mishandles concurrent
      accept/delete разных ответов. `AnswersSection.tsx:59`. Закрыто
      Сессия 55 Фаза 11: `busyAnswerId: string|null` → `ReadonlySet<string>`
      (markBusy/clearBusy с immutable `new Set(prev)`); accept/revoke/delete
      разных ответов теперь независимы. Тест: конкурентный accept A+B держит
      обе кнопки disabled (старый единый флаг сбрасывал A при старте B).
- [ ] **PageView citation highlight** может теряться на AI-edited страницах
      (async render race). `PageView.tsx:148`.
- [x] **QuestionListPage Load More** использует label-строку как error
      fallback message. `QuestionListPage.tsx:126`. Закрыто Сессия 55
      Фаза 11: `fallbackError` был `t('qa.list.subtitle')` («вопросов в
      обсуждении») → новый i18n-ключ `qa.list.load_failed` (ru/ar).
      Тест: сбой Load More показывает осмысленную ошибку, не подзаголовок.
- [x] **AdminUsersPage createdAt** non-locale-aware toLocaleDateString.
      `AdminUsersPage.tsx:182`. Закрыто Сессия 55 Фаза 11: `new
      Date().toLocaleDateString()` → `useFormatDate()` `full` style в
      `<bdi dir="ltr">` (mirror AdminAuditPage). Тест: дата по локали ru.
- [~] **PdfViewer initial page suffix-range / HttpClientPdfFetcher**
      negative Content-Length при upstream 206 без Content-Length.
      Content-Length-половина закрыта Сессия 55 Фаза 12: деривация длины
      вынесена в `deriveContentLength`/`deriveEndInclusive` с guard'ом
      (206 без Content-Length → длина из Content-Range или unknown `-1`,
      никогда негатив); controller не выставляет `Content-Length` при `-1`.
      Suffix-range половина отдельная/намеренная (PdfController отклоняет
      suffix `bytes=-N` per ADR-023 amendment — PDF.js их не шлёт).
- [ ] **PageImageService S3-put-before-DB** в @Transactional → rollback
      оставляет orphan scan (или включить OrphanDetectionJanitor в prod).
      `PageImageService.java:125`.

- [x] **AuditEntityType / UserRole single source of truth** - закрыто
      2026-05-19 (Сессия 47 Tech debt task #3). `@Schema(allowableValues)`
      на DTO fields (added в `9ca073a` Сессия 46) + frontend regenerate
      `types.ts` после Сессии 47 backend restart → literal unions для
      `entityType` (12 values incl. NODE_TRANSLATION), `action` (7),
      `role` (USER/ADMIN/MEMBER/EDITOR). `AdminAuditPage` uses generated
      type через `NonNullable<components['schemas']['AuditLogResponse']['entityType']>`
      + `satisfies EntityType[]` compile-time check (commit `8245b77`)
- [x] **Authority.type column для HadithGrade scholar validation** -
  закрыто 2026-05-19. Реализовано Вариант A: миграция 47 добавила
  `authorities.type VARCHAR(20) NOT NULL DEFAULT 'SCHOLAR'` с CHECK
  whitelist `SCHOLAR/MUHAQQIQ/PUBLISHER/AUTHOR/OTHER` + индекс.
  Backfill всех existing rows как SCHOLAR (publishers и muhaqqiqs
  живут в отдельных таблицах ADR-028, дублей нет). `Authority` record
  расширен полем `type`, `AuthorityType` constants class для
  whitelist + `isValid()`. `HadithGradeService.addGrade` теперь
  валидирует resolved scholar.type==SCHOLAR - попытка с PUBLISHER/
  MUHAQQIQ/AUTHOR/OTHER → 400 `invalid-scholar-authority`. Новая
  ошибка 400 `invalid-authority-type` при создании. `ShamelaAuthorityResolver`
  явно ставит `AUTHOR` (книжный контекст), `TopicImportService`
  оставляет null (default SCHOLAR через БД, старые экспорты не
  несут type-семантику). Total backend tests 998/998 pass
- [x] **Audit log для удалённых тем через /audit/topics/{id}** -
  закрыто 2026-05-19. Проверка факта: `audit_log` НЕ имеет FK на
  `entity_id` (миграция 39 - только plain UUID), поэтому при удалении
  темы CASCADE затирает nodes/edges/topic_members, но audit_log rows
  preserved. Реализовано: special case в `AuditLogController` -
  если topic deleted, count audit rows: 0 → 404 topic-not-found
  (тема никогда не существовала), >0 + не-ADMIN → 403
  `forbidden-deleted-topic-audit`, >0 + ADMIN → возвращаем preserved
  audit (compliance forensics). Симметрично для книг
  (`forbidden-deleted-book-audit`). Reviewer flag round 3 #6
- [ ] **Z-index renormalization для long-running тем** - max+1 / min-1
  pattern на 32-bit int даёт практически безграничное space (2.1B
  операций bring-to-front пока не уйдёт в overflow), но теоретически
  уплывёт на edge cases (бот-driven автоматизация, многолетние
  collaborative темы). Renormalize (compact all z_index в continuous
  integer sequence 1, 2, 3, ...) при достижении большого spread
  (e.g. abs(max) > 1_000_000). Pattern из CAD/diagramming tools.
  Low priority - real-world spread <100 у большинства тем.
  **Update Сессия 49b (2026-05-20):** added overflow guards в
  `NodeService.bringToFront/sendToBack` + `EdgeService.bringToFront/sendToBack`
  (commit `8b82892`) - throws `IllegalStateException` при достижении
  `Integer.MAX_VALUE/MIN_VALUE`. Recovery path (admin `POST /api/v1/topics/{id}/renormalize-zindex`)
  не реализован - оставлено в этом backlog item, overflow rare
- [ ] **Edge.topic_id денормализация (ADR-level decision)** - сейчас
  `EdgeService.bringToFront/sendToBack` и `deleteEdge` loadят edge +
  from-node для получения topicId (2 queries per call). Если store
  `topic_id` directly на edges table - устраняет JOIN nodes path,
  consistency через FK или trigger. Schema change + миграция.
  Future, не сейчас. Backlog from Сессии 49b audit follow-up

- [x] **Shared MinIO Testcontainer для IT suite** - закрыто 2026-05-19
      (Сессия 46). `SharedMinioContainer` singleton с static `INSTANCE`
      создаётся один раз на JVM fork, 9 IT мигрированы (ObjectStorageServiceIT,
      ObjectStorageHealthIndicatorIT, IntegrityVerificationJobIT,
      OrphanDetectionJanitorIT, UserUploadProviderIT, PdfLinksSourceProviderIT,
      FileImportServiceIT, PageImageServiceIT, OcrServiceIT,
      FileImportControllerIT). Экономия 45-90 сек на verify-прогоне.
      Test isolation - явный empty bucket'а перед delete в
      ObjectStorageHealthIndicatorIT (shared container накапливает
      versions от других IT с versioning). Reviewer round 5+6 flag
      закрыт

- [x] **BookSummaryResponse.createdBy для accurate «Мои» filter в
      Library overview** - сейчас фильтрация книг текущего user'а в
      Library overview через approximation `visibility === 'PRIVATE'`
      (works in practice т.к. RBAC: privata = owner-only). Hrupkij:
      если кто-то расширит visibility model или owner поделится своей
      книгой как SHARED - approximation сломается. Fix: добавить
      `createdBy: UUID` в `BookSummaryResponse` (full sync с
      `BookResponse`) + frontend фильтрует строго `book.createdBy ===
      currentUser.id`. Reviewer round 4 #8. Закрыто 2026-05-19:
      `BookSummaryResponse.createdBy` (mapper заполняет из
      `Book.createdBy`), `BookControllerIT.getBooks_returnsCreatedBy`,
      `BookListPage` фильтр «Мои» теперь strict
      `book.createdBy === currentUser.id` (если currentUser=null -
      пустой список)

- [x] **PATCH /api/v1/topics/{id} для title/description editing** -
      сейчас readonly в `TopicSettingsDrawer`. Нет REST endpoint для
      переименования темы (visibility patch есть, но title нет).
      User'у приходится создавать новую тему вместо переименования.
      Fix: PATCH endpoint + form в settings drawer + IT тесты на
      audit log для UPDATE с FieldDiff(title, description). Reviewer
      round 4 #10. Закрыто Сессией 2026-05-19: `UpdateTopicRequest`
      (PATCH-семантика null=no change), `TopicService.updateTopic`
      (assertCanWrite, audit FieldDiff только по изменившимся полям),
      `PATCH /api/v1/topics/{id}` controller, 13 IT (happy/partial/
      no-op/permission/404) + 6 REST IT в `TopicControllerIT`. Frontend:
      editable form в metadata-секции `TopicSettingsDrawer` (Save
      disabled пока нет изменений / валидация title), i18n RU+AR, 5
      Vitest кейсов (controlled input, save disabled, success PATCH с
      только changed полями, оба поля в body, 403 toast)

- [x] **Bulk audit log consolidation - single BULK_DELETE / BULK_STATUS
      action с entityIds[]** (закрыто 2026-05-19, backend часть) -
      `AuditAction.BULK_DELETE` + `BULK_UPDATE` константы (зарезервированы),
      `AuditLogService.logBulkDelete(childEntityType, parentType, parentId,
      actor, entityIds, sharedContext)` helper - один audit row с
      `{childEntityType, entityIds[], count, snapshots}` в changes JSON,
      `entity_id = parentId` (NOT NULL constraint + bulk row концептуально
      событие на parent'е). `NodeService.bulkDeleteNodes(nodeIds, userId,
      role)` - single-topic ограничение, корневые в `skippedRootIds` без
      fail'а, один пересчёт статусов на topic, audit пишется только если
      хоть один узел реально удалён. `DELETE /api/v1/nodes/bulk` endpoint
      с `BulkDeleteNodesRequest(nodeIds: max 100)` + `BulkDeleteResponse(
      deletedIds, skippedRootIds)`. 7 IT в `NodeServiceIT` (one audit row /
      filters root / non-writer 403 / cross-topic 400 / non-existent 404 /
      empty 400 / only-root no-op). Frontend migration на новый endpoint -
      next step (другая задача, runDelete в `GraphCanvas.tsx`).
      Bulk update/status change для других сущностей (edges/answers) -
      по запросу, пока only nodes имеют bulk delete UX

- [x] **NodeTranslationService DRY: extract `promoteToDefault` helper**
      (закрыто 2026-05-19) - извлечён private helper
      `promoteToDefault(nodeId, candidateTranslationId)` -
      инкапсулирует atomic switch default-флага через
      `translationRepository.setDefault`. Все три mutation-сайта
      (`addTranslation`, `setDefault`, `removeTranslation`) ходят
      через helper - один источник истины для default-switching.
      Decision «кого promote'ить» (новый перевод vs oldest remaining)
      остаётся на caller'е. 20 IT NodeTranslationServiceIT pass,
      public API не изменился

- [x] **Frontend UX consistency: window.confirm → unified pattern**
      (audit 2026-05-20 M-1) — закрыто 2026-05-31. Выбран **styled
      `ConfirmDialog`** (не toast-undo): member removal / grade delete /
      answer delete — действия без естественного undo, для них modal
      с явным подтверждением честнее. Реализован promise-based
      `askConfirm(opts): Promise<boolean>` (`shared/stores/confirmStore.ts`,
      императивный API как у `toast.*`) + глобальный host `ConfirmDialog`
      в App.tsx. Все 5 callsite'ов мигрированы с `window.confirm` на
      `await askConfirm({ message, danger })`. Node-delete остаётся на
      toast+Undo (там undo осмыслен). 6 тестов (confirmStore 3 +
      ConfirmDialog 3), 3 component-теста переведены с `vi.stubGlobal
      ('confirm')` на `vi.mock(confirmStore)`. `common.confirm` +
      `common.confirm_title` i18n RU+AR

- [ ] **Flaky test: `bulkActions.test.tsx` (d3-drag + jsdom)** — обнаружено
      2026-05-31. В полном параллельном прогоне (`vitest run`) 3 теста
      падают с unhandled-ошибками из `d3-drag/nodrag.js` (`document` style
      mutation на `selectstart`/`pointerdown`, которую jsdom не реализует).
      **В изоляции файл проходит 5/5** — это flakiness (async-ошибка teardown
      одного теста прилетает в другой под параллельной нагрузкой), не
      реальная регрессия. Фикс: подавить эти unhandled-ошибки в `test-setup.ts`
      (filter по d3-drag stack) либо изолировать lasso-тесты. Non-blocking,
      pre-existing на master. Объём ~1ч + итерации (flakiness
      недетерминирована)

- [ ] **Hadith Explorer — follow-ups из code-review Сессии 50** (3 parallel
      reviewers: backend / frontend / domain-accuracy). Critical: 0. Закрыто
      в сессии: `bg-bg-sunken`→`bg-sunken` (visual bug), 2 фактические ошибки
      matn (Бухари №6689 не «без إنّما»; Муслим №1907 matn был обрезан),
      role-precedence COLLECTOR>COMPANION задокументирована, Тамим место
      смерти уточнено. **Отложено (намеренно):**
  - **Prod guest-access**: `GET /api/v1/hadith/**` permitAll только в dev/test
    profile; в prod `anyRequest().authenticated()` закроет гостевой просмотр
    (spec §4.3 / vision §2.5). **Pre-existing** (вся платформа dev/test-only
    per ADR-040 transitional; Phase 1.f hadith endpoints имели тот же gap).
    Закрыть в рамках prod-hardening этапа (ADR-040): добавить
    `requestMatchers(GET, "/api/v1/hadith/**").permitAll()` вне dev-ветки.
  - ✅ **`react-hooks/set-state-in-effect` lint** — **RESOLVED** Сессия 51.
    Все 6 сайтов (`SanadGraph`, `NarratorDetailPage`, `TopicListPage`,
    `QuestionListPage`, `AdminUsersPage`, `LibraryCollectionsPage`) — это
    намеренный loading/reset-переход при смене dep. Применён justified
    `eslint-disable-next-line` + «почему»-комментарий на каждом сайте —
    тот же idiom что в каноническом `useApiQuery` (он делает то же для
    loading-перехода). Заодно убраны 2 устаревших unused-disable
    (`useApiQuery` L57, `useOnboardingProgress` L146 — после guard'а правило
    не флагует) + добавлен missing dep `setEdges` в `useAutoLayout`.
    **Lint теперь green (0/0).** Опционально-future: реальная консолидация
    в один `AsyncState`-хук или route-`key` remount (не блокер, idiom
    задокументирован).
  - **Narrator identity duplication в seed**: один и тот же человек (Суфьян
    ибн Уяйна, Малик) — отдельные `hd_narrators` записи per-hadith (разные
    UUID). Для dev-seed ок; реальный ETL должен дедуплицировать по identity
    (name_ar_normalized + era), иначе `/narrators/{id}/transmitted` покажет
    неполный корпус раввия. Учесть в Phase 5 (ETL `NarratorMapper`).
  - **Smoke-тесты для нового graph-chrome** (code-review Сессии 51, Reviewer 1
    рекомендация): `ZoomControls` (preset open/Escape/disabled-at-limits),
    `MinimapCard` (collapse↔expand, click-to-jump → onViewportChange, drag),
    `HelpShortcuts` (hover/click open, pin stopPropagation). Остальной graph/
    хорошо покрыт — это gap. Не блокер (build/tsc/lint green, токены проверены
    визуально).
  - **v2→v3 token alias cleanup** (code-review Сессии 51, Minor): новый chrome
    использует `accent-*` алиасы вместо `brand-*`; `Badge`/`BookListPage`/
    `EdgeDetailsPanel`/`edgeRules` ещё на старых `type-*`/`edge-*-bg` именах.
    Всё резолвится через alias-блок (cross-cutting sweep подтвердил 0 unstyled),
    финальный шаг — мигрировать на v3 имена и удалить alias-блок в
    `index.css`/`tokens.css`.
  - **NodeDetailsPanel «Опора» тесты падают** (pre-existing, НЕ регрессия
    Сессии 51 — файл не тронут): 3 subtests citations/sources (MSW handler
    `/sources` + изменённый формат label, семья 49d QA-sources). Флак вместе
    с `bulkActions` d3-drag. Триаж отдельно.
  - [x] **BookRepositoryIT.findAll_orderByCreatedAt флак** — **исправлено
    Сессией 53**. Реальная причина оказалась НЕ tie-break, а **test
    pollution**: `findAll(null,null)` возвращает все книги, а другой
    IT-класс, коммитящий `lib_books` в shared Testcontainers Postgres,
    «загрязняет» таблицу. Fix: ассерт порядка СВОИХ книг как
    подпоследовательности (устойчиво к посторонним строкам). Класс
    `@Transactional`, так что свои строки откатываются — протекали чужие.
  - **Системная flakiness полного прогона (НЕ исправлено, требует выделенной
    работы):** корень — IT-классы делят один Testcontainers Postgres (context-
    cache), часть коммитит данные, часть ассертит «все строки». Каждый full
    `verify` краснит 1 случайный тест-«жертву» (зелёный в изоляции).
    Известные жертвы: `PdfControllerIT.streamPdf_withoutRange` (MinIO/timing),
    ранее `BookRepositoryIT` (исправлен). Durable fix — изоляция (per-class
    truncation либо `@Transactional` на коммитящих). Объём — отдельная
    тест-гигиена, вне Phase 5. До тех пор: упавший в full прогоне класс
    прогнать в изоляции прежде чем считать регрессией.

- [ ] **GraphCanvas lastNodesRef comment fragility** (audit M-6) -
      callback `handleNodeContextMenu` читает `lastNodesRef.current`
      и не включает ref в deps (правильно для mutable ref). Комментарий
      line 404 объясняет lastNodesRef vs `nodes` closure capture, но
      не объясняет почему ref пропущена в deps array. Будет regress
      если кто-то превратит ref в state. Quick comment-only fix

- [~] **Dark theme palette overhaul** (vision 49d Section 1.1) — **core
      адресован Сессией 51** token-миграцией v2→v3 (`a907218`): indigo accent
      заменён на purple-violet brand (hue 270, oklch), retuned per-theme
      (`[data-theme='dark']` brand-500/600 brightened). Indigo «не сочетается»
      — закрыто. **Остаётся проверить глазами:** placeholder обложек книг
      (ярко-жёлтые) и logo bg в dark — если ещё конфликтуют, точечный tweak.
      Проверить при manual browser pass.

- [ ] **Edge routing distribution через handles** (vision 49d Section
      1.6) - когда из одного узла идёт 4+ рёбер, они merge в одну точку
      на одном handle. Сейчас SPLINE routing (Сессия 49 commit 7050d29)
      + bezier offset для overlapping pairs (b1b15f1) решает 2-ребро
      case, но не fan-out из одного узла. План: distribute edges по
      4 handles (top/right/bottom/left) в зависимости от relative
      position connected узлов, либо ELK ports support для multi-edge
      distribution. Требует investigation subagent

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
- [x] **Actuator endpoints behind auth в prod** - закрыто 2026-05-19
      (ADR-048). Отдельный `SecurityFilterChain` (`ActuatorSecurityConfig`,
      `@Order(1)`, `securityMatcher("/actuator/**")`). В prod profile -
      basic auth для всего кроме `/actuator/health`, `/actuator/health/**`,
      `/actuator/info` (LB liveness/readiness + CI/CD deploy verification).
      In-memory ACTUATOR user из env `ACTUATOR_USERNAME` /
      `ACTUATOR_PASSWORD`, fail-fast при пустых значениях в prod.
      Локальный AuthenticationManager с DelegatingPasswordEncoder (не
      конфликтует с глобальным BCrypt). HTTP security headers
      (HSTS/CSP/Referrer/Permissions) mirror'ятся в actuator chain.
      Dev/test - permitAll как раньше. 5 IT в ActuatorSecurityProdProfileIT
      (health/info public + circuitbreakers 401/200/wrong)
- [x] **Refresh token rotation** (Important Cross-cutting #4) -
      реализован 2026-05-19 (ADR-047). Single-use refresh с tracking в
      `refresh_tokens` таблице, SHA-256 hex hashing, steal detection
      через revoke-all-by-user при reuse. Миграция 46 + RefreshToken
      domain + RefreshTokenRepository + AuthService rotation logic +
      AuthServiceRotationIT (8 IT) + adapt существующих AuthControllerIT.

- [x] **RefreshTokenCleanupJanitor** - закрыто 2026-05-19.
      `@Component @ConditionalOnProperty(refresh-token.cleanup.enabled=true)`
      + `@Scheduled` cron daily 02:30 (после AuditLogRetention 02:00 и
      до orphan janitor 03:00). `RefreshTokenRepository.deleteOlderThan(cutoff)`
      DELETE'ит revoked где `revoked_at < cutoff` И expired активные
      `expires_at < cutoff` (hard DELETE без soft-delete - history
      редко нужна, backup-based recovery). `RefreshTokenCleanupProperties`
      с validation retentionDays >= 7. Default `enabled=false` (dev/test
      работают без janitor'а), в prod через `REFRESH_TOKEN_CLEANUP_ENABLED=true`.
      Default retention 30 дней - balance forensics window (refresh TTL
      7 дней + 3x запас) и table hygiene. 5 IT
      (RefreshTokenCleanupJanitorIT через Testcontainers): revoked старше/
      внутри retention, expired never-used, active valid, count returned

- [x] **Edge z-order persistence** - закрыто 2026-05-19 в Сессии 47
      (Tech debt task #1, 6 commits). Mirror Node.zIndex pattern:
      миграция 48 (`edges.z_index`), `Edge.zIndex` field, EdgeRepository
      `updateZIndex/findMaxZIndex/findMinZIndex`, EdgeService
      `bringToFront/sendToBack` с permission check, POST endpoints
      `/api/v1/edges/{id}/z-order/{bring-to-front,send-to-back}`,
      frontend `useGraphZOrder` switched от ephemeral counter к API
      с optimistic + onRefetch sync. EdgeServiceIT 20→25, EdgeZIndexIT
      6 tests

- [ ] **CreateQuestionPage raw-HTML render без sanitize**
      (audit 2026-05-20 M-4) - `CreateQuestionPage.tsx:132` рендерит
      `t('qa.create.hint_body')` через React raw-HTML escape hatch без
      DOMPurify wrap. Risk теоретический - dictionary controlled by team.
      Но прецедент нежелательный: если i18n loading перейдёт на remote
      backend, эта точка станет реальной XSS дырой. Fix: либо разбить
      на структурированный `<p>{t(..._p1)}<br/>{t(..._p2)}</p>`, либо
      обернуть `sanitizePageHtml` (используется в reader path).

- [ ] **AdminShamelaPage placeholder strings hardcoded RU**
      (audit M-3) - `AdminShamelaPage.tsx:629-633` пять mock log lines
      литералы на русском (`'sync-master: ничего нового...'`). Heading
      i18n'd, но строки логов нет. Placeholder исчезнет когда backend
      log endpoint появится - но до тех пор при locale=en/ar mixed
      rendering. Fix: TODO comment либо migration в dictionary
      ru/ar/en временные ключи

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
