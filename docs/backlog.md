# Бэклог

Идеи и задачи без привязки к активному этапу. Не закрытые - в
`docs/roadmap.md`. Закрытые в общем виде - в `docs/progress.md`

Когда задача созревает (становится приоритетной или блокирует
другую) - переезжает в новый Этап в `roadmap.md`

> **Сессия 49d vision expansion (2026-05-20):** Абдула задал большой
> список новых целей в начале сессии. Полный structured список — в
> `docs/specs/2026-05-20-vision-expansion-49d.md`. Items
> ниже отражают/ссылаются на этот документ. Большие фичи получают
> отдельные design-specs в `docs/specs/` по мере
> приоритезации.

## Фронт - общие улучшения

**Закрыто (свёрнуто):** Шрифт title книг (EB Garamond, Сессия 36),
фикс 12 pre-existing test failures (Node 24 + undici 7 AbortSignal bug,
Сессия 36, gotcha сохранён), `runDelete` → `DELETE /nodes/bulk` (commit
`9d9cc37`), экспорт графа PNG/SVG (2026-05-17), тёмная тема 3-option
(2026-05-17), smart edge routing elkjs (2026-05-18), z-index persistence
для узлов (миграция 40, 2026-05-18) и для edges (миграция 48, Сессия 47).
Детали в progress.md/git.

- [ ] Полнотекстовый поиск (НЕ через Postgres tsvector - см. раздел «Архитектурные решения» ниже)
- [ ] **PDF export графа** - отдельная задача (jspdf или native
  print-to-PDF), приоритет low - PNG/SVG покрывает основной use case
- [ ] Локализация (i18n) при появлении второй локали

## Граф иснада — turuq «Все пути» (фаза-2 фидбек С63, FB-7)

- [x] **PNG-экспорт turuq: чёрные боксы** ✅ С65 (defensive, не репро-able live).
  Проверено С65: FB-7a `withInlinedCssVars` УЖЕ покрывает turuq-путь (общий
  `exportGraphAsPngHighRes` для single+turuq); реальные branchy-экспорты
  (`cbb31276` 29 узлов/6 веток, `723cd50a` 159 узлов/25 merge) чисты до и после,
  0 near-black в DOM. Edge-strokes — hex-токены, не CSS-vars (merge-коннектор не
  чёрный). Латентный gap (hypothesis a): `transmissionPhrase` whitespace-only
  (`'  '`) был truthy → пустой chip = box-артефакт в PNG. Корпус: 0 whitespace-
  phrases (потому не репро). Fix: `visibleTransmissionPhrase` предикат
  (`utils/sanadEdge.ts`) гейтит blank → chip не рисуется. 5 unit + регрессия.
- [x] **Граф иснада: ELK orthogonal routing** (С64) — `sanadElkLayout.ts` (layered
  DOWN, ORTHOGONAL, edgeNode/edgeEdge spacing) + `SanadCustomEdge.tsx` (рендер ELK
  bend-points через переиспользованный `orthogonalPath.ts`, подпись-формула в
  EdgeLabelRenderer). SanadGraph: async-раскладка (dagre initial → ELK по готовности),
  edge type 'smoothstep'→'sanad'. Верифицировано: single-chain (playwright + PNG),
  fork (real-ELK unit-тесты: разведение ветвей >100px + bend-points), click-highlight
  (data.dimmed), PNG-export (подписи-чипы читаемы, не чёрные боксы). Корпус
  одноцепочечный → branchy кейс покрыт юнит-тестами, не живыми данными.
- [x] **FB-2 detach-× гостю скрыт** (С64) — `onDetach` стал optional во всём дереве
  citations (CitationsList → SourceCard/HadithCite/FreeformCite), передаётся только
  при `canWrite`; гость больше не видит мёртвую кнопку (бэк отдавал 403). +тест
  NodeDetailsPanel «canWrite=false: карточка есть, detach-× скрыт».

## Responsive / mobile-планшетная адаптация

Фаза 1 (foundation: useIsMobile, Modal, NodeDetailsPanel, Header,
Select) - Сессия 39. Фаза 2 (10 страниц: BookReader drawer, sticky
dvh, PdfViewer toolbar 2-row, list/create padding, AdminShamela
table scroll, CitationPicker tabs, AcademicMetadata 1-col, filter
chips overflow) - Сессия 40. Обе сжаты в roadmap closed-stages

### Фаза 3 - возможные улучшения (когда понадобится)

- [x] **Hover-only действия имеют tap-альтернативу** ✅ С66 — аудит всего
  фронта: ровно 2 hover-reveal места. (1) `TopicListPage` export-кнопка карточки
  (`opacity-0 group-hover:opacity-100`) — была невидима на touch (focus уже
  покрывал клавиатуру) → добавлен `pointer-coarse:opacity-100` (Tailwind v4
  `@media (pointer: coarse)`, верифицировано в build-CSS): на устройствах без
  hover кнопка всегда видна. Выбран pointer-coarse, НЕ `useIsMobile` (ширина —
  proxy; pointer-coarse = реальное «нет hover»). (2) `NodeCard` connection-handles
  (`opacity-0 group-hover`) — **оставлены намеренно**: десктоп-first RF-канва
  (не зеркалится), touch-создание рёбер идёт через контекст-меню/AddEdgeModal,
  4 крестика на каждом узле захламили бы граф. BookListPage Download — обычные
  всегда-видимые `<Button>`, не задеты.
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
- [ ] **Source picker для хадисов** - таб «Хадисы» с 12 сборниками
      (Бухари, Муслим, Тирмизи и т.д.), фильтр по статусу/рулингам,
      показ иснада. Данные — собственные `hd_*` (alminasa-снапшот,
      ADR-060) _(SourcePickerHadith)_
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
**Закрыто (свёрнуто):** Source detail panel (`SourceDetailPanel` store,
2026-05-18), Library overview (BookListPage polished, 2026-05-18), Inline
citations (подход A implicit ordinal, 2026-05-18). Детали в progress.md/git.

- [ ] **Sanad explorer** - визуализация цепочки передатчиков
      хадиса (8-звенная от Пророка ﷺ до составителя). Каждое
      звено - карточка передатчика (имя / поколение / tier).
      Связи типизированы (`sama'` / `'an'ana` / `haddathana` /
      мункати'). Альтернативные пути. Серьёзная доменная фича -
      потребует расширения доменной модели (новые сущности
      `Rawi`, `Sanad`, `SanadLink`) _(SanadExplorer, SANAD demo
      data)_
**Закрыто (свёрнуто):** Multi-grading хадисов backend (миграция 43
`hadith_grades`, SAHIH/HASAN/DAIF/MAUDU, 2026-05-18) и UI
(`HadithGradesSection`, 2026-05-18); Translator attribution (миграция 45
`node_translations`, 2026-05-18). Bilingual карточки (миграция 44,
2026-05-18) — **фича позже выпилена Сессией 54 batch 2** (bilingual node
mode + tashkeel удалены, NodeCard всегда рендерит content; не описывать
как живую возможность). Детали в progress.md/git.

- [ ] **Translation editor UI** - admin add/edit modal для добавления
      переводов через UI (сейчас только curl). Modal с polish (translator
      name autocomplete по past entries, language radio, body textarea,
      isDefault checkbox с warning «текущий default потеряет флаг»),
      кнопки + Edit/Delete по carret-menu рядом с dropdown items в
      NodeCard. Low priority - power-users могут через curl до тех пор
- [ ] **Tashkeel toggle** - на canvas карточки можно отключить
      огласовки (`harakat`) для краткости. Side-by-side
      сравнение с / без _(TashkeelSection)_. **Прим.:** прежняя
      tiptap-реализация (Tashkeel extension + `stripTashkeel` util)
      выпилена Сессией 54 batch 2 как junk-дубль — концепт toggle на
      canvas-карточке формально не реализован, не воскрешать как «почти
      готовое».
- [ ] **RTL-режим** - для арабского UI: зеркальный layout графа,
      RTL-toolbar, naskh / kufi-шрифты. Большая работа, выделить
      в отдельный этап _(RTLGraphScreen, RTLSection)_
- [ ] **Language switcher (RU / EN / AR)** - в header или
      settings. Идёт в комплекте с i18n и RTL
      _(LanguageSwitcher)_
**Закрыто (свёрнуто):** Settings screen (язык/шрифт/размер/theme,
`user_preferences` миграция 42 — **тогглы tashkeel/транслит позже
выпилены Сессией 54 batch 2 вместе с user_preferences-вертикалью,
миграция 63**, не описывать как живые); Onboarding floating widget
(2026-05-18); Topic settings drawer (480px, Сессия 37); Multi-select
floating action bar (graphSelectionStore). Детали в progress.md/git.

- [ ] **Cross-references drawer** - 600px drawer «узел использован
      в N темах»: группировка по темам, прыжок в граф. Cross-topic
      graph-навигация. Требует backend аггрегата по cross-topic
      ссылкам _(CrossRefDrawer)_
- [ ] **Print preview** - A4-toolbar с тогглами (включить узлы,
      источники, иснады) + полноценная печатная страница темы.
      Граф как SVG, источники в академическом формате
      _(PrintPreviewSection)_

## Бэк - бэклог

### alminasa — вне-скоуп находки HAR-разбора С59 (контракты сняты, фикстуры в test/resources/alminasa/s59)

- [ ] **chains-links-12 — богатые рёбра сети передатчиков**: готовые
  link-доки {src_id, tgt_id, verb(حدثنا/عن/…), src/tgt_type вкл.
  Prophet, is_singular} — 13k+ рёбер у крупного рави. Богаче наших
  hd_narrator_relations (у тех только counts из top_students).
  Краулить narrator-first/лениво, НЕ в hadith-цикле. Фикстура
  chains-links-12-scholars.json.
- [x] **narrator-commentary-12 — джарх-ва-тадиль цитаты о рави** ✅ Сессия 61
  (2026-06-16, ADR-061, миграция 76): таблица hd_narrator_commentaries +
  секция «Оценки учёных о передатчике» на карточке рави. Live: 29 546 цитат
  (re-map 7 789 рави, 0 ошибок). План
  `docs/plans/2026-06-16-alminasa-narrator-commentary.md`. **Known-tradeoff
  (MINOR-1 review):** `AlminasaEsClient.fetchNarratorCommentaries` батчит 25
  рави × ES `size=500` — у очень плодовитого рави цитаты могут переполнить
  size (warn-лог, доки не теряются: re-crawl батча). Консистентно с
  fetchCommentaries/fetchAmbiguous; поднять `dependent-fetch-size` если в live
  появятся overflow-warn'ы по narrator-commentary-12.
- [ ] **references — каталог корпуса alminasa** (86 книг: type/status/
  progress оцифровки). Витрина «что есть в корпусе»; можно статическим
  seed. Низкий приоритет. Фикстура references.json.
- [ ] **Расширенный профиль рави из narrators-12**: краулер сейчас
  кладёт raw целиком — но book_titles[]/top_students[]/top_scholars[]
  уже мапятся; досмотреть остальные поля (extended_full_name и пр.)
  при следующем заходе на риджаль.
- [x] **Подсветка гариб-слов в матне** ✅ Сессия 61 (commit 1d4f5e3):
  `HighlightedMatn.tsx` + `highlightGharib.ts` (normalizeArabic зеркалит
  бэковый ArabicTextNormalizer, пословный матчинг + фразы-reference ~5%),
  click-поповер толкование+словарь. 10 unit + 2 HadithDetailPage теста,
  playwright 44 слова. metadata.referenceId использован как ключ.
- [ ] **Admin-форма «Оценка учёного» (hadith_grades)** ⚠️ ЭСКАЛИРОВАНО
  (Сессия 61, инвестигация в git/progress): механизм `hadith_grades`
  (миграция 43, `POST /api/v1/sources/{id}/grades`, таблица + enum
  `HadithGradeValue`, FK на `sources`+`authorities`) **НЕ сведён** с
  alminasa `hd_hadiths`. Detail-секция «Оценки учёных» читает
  `hd_hadiths.metadata.grades` jsonb (freeform scholar/grade/note,
  **READ-ONLY**, нет POST). id-mismatch (`sources.id` vs `hd_hadiths.id`) +
  schema-mismatch: даже при совпадении id форма писала бы в таблицу, которую
  detail НИКОГДА не читает → секция осталась бы пустой. **Нужен ADR Абдулы:**
  (A) POST в `hd_hadiths.metadata.grades` jsonb (просто, но freeform, без
  authorities-FK/enum/дедупа); (B) связать `hd_hadiths`↔`sources` + detail
  читает из `hadith_grades` JOIN (дороже, настоящая модель: authorities, enum,
  дедуп, permission SCHOLAR); (C) новая таблица `hd_hadith_grades(hadith_id
  FK, authority_id, grade, citation, note)` + endpoint + чтение в detail.
  Код НЕ писан (guard: архитектура → стоп).
- [x] **Pre-existing lint errors (2)** — закрыто С64 (lint 0 ошибок): (1)
  `HadithDetailPage` `preserve-manual-memoization` — `detail?.fullTextAr`
  захойстен в const `fullTextAr` (deps без optional-chaining → компилятор
  сохраняет мемоизацию), поведение идентично; (2) `AdminHadithImportPage`
  `set-state-in-effect` — точечный `eslint-disable-next-line` на legitimate
  mount-fetch (setState async после await, как в других fetch-эффектах проекта).
  tsc 0, 58 затронутых тестов зелёные.


- [ ] **Связаться с alminasa.ai (مركز تميز) до массового краулинга** —
      продуктовое решение Абдулы (идея из консультации 2026-06-04).
      Два академических проекта в одной нише: написать им — возможно
      дадут официальный дамп/доступ или благословение со ссылкой на
      источник. Технически краулер и так вежливый (1 стр/с, ответы
      CDN-кэшированы) и атрибуция фиксируется в метаданных (спека
      Сессии 56 §G), но официальный контакт снимает юр./этический
      вопрос целиком. До ответа — не запускать полный обход 12
      сборников (dev-краулинг отдельных страниц для отладки — ок).

**Закрыто (свёрнуто):** Isnad persistence-on-import (иснад
персиститься в `hd_sanads`/`hd_narrators`/`hd_sanad_narrators`,
дедуп нарраторов по normalized-name, идемпотентный delete-recreate) —
закрыто 2026-06-03, ADR-059 amendment, Сессия 55 Фаза 9; детали в
progress.md/git.

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

**Закрыто (свёрнуто):** Пагинация+фильтрация всех GET-list endpoints
(`PagedResponse<T>`, 2026-05-18); Dung's argumentation framework
(миграция 41, status_algorithm MVP|DUNG_GROUNDED, Сессия 38, ADR-044);
Frontend UI переключения status-algorithm (radio в TopicSettingsDrawer,
commit `7990b13`); Импорт/экспорт темы в JSON (Сессия 39, ADR-037);
Голосование за вес аргументов (миграция 38 `node_votes`, 2026-05-18 —
**позже отменено ADR-053, голоса перенесены node→topic, миграция 60 DROP
node_votes**); Frontend pagination остальных list pages (Load More,
2026-05-18). Детали в progress.md/git.

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

~~Секция сжата Планом 4 (Сессия 57, 2026-06-04)~~: sunnah-ETL удалён
(ADR-060) — пункты про `SunnahToHadithMapper`/`SunnahDumpReader`/
`sn_staging_*`/AdminSunnahPage/полный корпус sunnah сняты как мёртвые.
Единственный переживший (генерик, не sunnah):

- [ ] **Расширенные формы хамзы/алифа** (Minor, DEFER per YAGNI): `ArabicTextNormalizer` —
  NFKC + текущие folds покрывают обычный текст и presentation forms. **Проверено С64
  (NFKC-расчёт):** U+0672/0673 (alef wavy hamza above/below) NFKC НЕ декомпозирует —
  проходят verbatim (ٲ/ٳ); U+0675/0676/0677 → base + U+0674 (HIGH HAMZA остаётся как
  буква). Т.е. реально НЕ нормализуются (аудит С64 ошибочно пометил DONE — поправлено).
  Когда понадобится: switch-кейсы U+0672/0673/0675→ا, U+0676→و, +снять U+0674 (как ء).
  Это архаичные/тюркские формы — в стандартном арабском матне хадиса не встречаются,
  поэтому держим отложенным до появления в реальных alminasa-данных.

### Code-review findings (Сессия 52, 2026-06-01) — ADR-043 sweep gaps

Из code-review fix-волны (4 reviewer-агента). Реальные, но out-of-scope
для закрытых 6 authz-дыр — тот же системный паттерн «эндпоинт не зовёт
permission-модель», который надо домести чтобы sweep был полным:

**Закрыто (свёрнуто):** NodeSourceController topic-authz (commit
5f27689, Сессия 52); Q&A citation controllers unscoped detachById
(commit 5f27689, завершает ADR-043 sweep); AI-edit stuck-PROCESSING
liveness (Сессия 55 Фаза 12). Детали в progress.md/git.

- [x] **Thesis `إعداد:` author-loss** — закрыто С64 (без схемы): `ParsedBibliography`
  получил nullable-поле `thesisPreparer`, парсер ловит `إعداد`/`اعداد`/`من إعداد`
  (как существующий `إشراف`-маркер), а `ShamelaToLibraryMapper.resolveAuthority`
  использует preparer как fallback автора, когда structured `author_id` пуст —
  имя резолвится в обычную `Authority(type=AUTHOR)` тем же путём (новый
  `ShamelaAuthorityResolver.resolveByName`). Без новой колонки. Backfill-сервис
  НЕ трогали (риск перезаписать ручные правки — отдельный вопрос). Тесты: parser
  19/19 (+iʿdād-кейсы), mapper IT 16/16 (+resolvesThesisPreparerAsAuthor).
- [x] **Repository round-trip IT для thesis-колонок** — закрыто (аудит С64):
  `BookRepositoryIT.save_withThesisMetadata_roundTrip` (+null-fields +
  updateThesisMetadata) сохраняют/читают thesis_* через Testcontainers Postgres.
- [x] **HadithListPage test asymmetry** — закрыто (аудит С64): общие хуки
  `usePagedList`/`usePagedSearch` извлечены (commit ae41c98) + покрыты тестами
  (`usePagedList.test.tsx`, `usePagedSearch.test.tsx`), `HadithListPage.test.tsx` есть.
- [x] **Load More stale-append race** — закрыто (аудит С64): append-`loadMore`
  живёт только в `usePagedSearch` с generation-guard (`:198`) + AbortController
  (`:157`); list-страницы перешли на `usePagedList` (REPLACE+guard). Commit 43bb89f.

### Bug-hunt Tier-3 (Сессия 52, 2026-06-01) — 30 low-severity

Из multi-agent багоохоты (235 агентов, 48 подтверждённых; HIGH security +
medium закрыты в Сессии 52, см. `docs/audits/2026-06-01-bug-hunt-handoff.md`).
Остаток — low severity, ни один не критичен. Канон фиксов — в handoff'е.

**Закрыто (свёрнуто):** ~26 из ~30 пунктов закрыты — security hardening
(AuthService login timing side-channel; disabled-account leak;
ShamelaArchiveExtractor decompression bomb), concurrency (OCR re-trigger
claim — фича OCR позже выпилена ADR-057/Сессия 55; ShamelaAuthorityResolver
UNIQUE-guard миграция 66; AnthropicClient transient-only retry), logic
(OcrService NULL→FAILED — удалён вместе с OCR ADR-057; updateOcrStatus
COALESCE — тоже OCR-смежный исторический факт; ShamelaChapterMapper cycle
Фаза 10; ShamelaBibliographyParser word-count Фаза 10; QuestionService
body→NULL; acceptAnswer-CLOSED 409; HadithController stale bookId commit
94309dc; getDetail O(sanads×links) Фаза 10; TopicListPage post-import sort;
useViewTracking dedup), accessibility/UX (ContextMenu clamp; Toaster
assertive; QuestionDetailPage delete-gating Фаза 11; AnswersSection busyIds
Фаза 11; QuestionListPage Load More Фаза 11; AdminUsersPage locale-date
Фаза 11). Канон фиксов — `docs/audits/2026-06-01-bug-hunt-handoff.md`,
детали в progress.md/git.

Остаются живыми:
- [x] **View-count inflation** — закрыто С64: in-memory sliding-window дедуп по
      `(clientIp, bookId)` (окно 30 мин, `BookViewDedupService`, зеркалит
      RateLimitFilter) — повтор той же пары = no-op. Эндпоинт `POST /views`
      сделан публичным в prod (счётчик публичен, контент не раскрывается), дедуп
      = анти-инфляция. AtomicInteger callCount, XFF-trust note. Тесты unit 9 + IT
      (BookServiceIT 3 + GuestAccessProdProfileIT аноним→204).
- [x] **MinimapCard drag/clamp** — закрыто (commit `a890327`): `BOUNDS_PAD`
      симметрично расширяет bounds, drag/click клампятся к `padMinX/padMinY`
      (центрированный origin). Подтверждено С64.
- [x] **PageView citation highlight** — закрыто (commit `1473bb9`, аудит С64):
      флаг `richTextReady` (`PageView.tsx:109`) гейтит highlight-эффект
      (`:152`, `if (hasFormattedContent && !richTextReady) return;`), сбрасывается
      на смену страницы — async-гонка на AI-edited страницах закрыта.
- [~] **PdfViewer initial page suffix-range / HttpClientPdfFetcher**
      negative Content-Length при upstream 206 без Content-Length.
      Content-Length-половина закрыта Сессия 55 Фаза 12: деривация длины
      вынесена в `deriveContentLength`/`deriveEndInclusive` с guard'ом
      (206 без Content-Length → длина из Content-Range или unknown `-1`,
      никогда негатив); controller не выставляет `Content-Length` при `-1`.
      Suffix-range половина отдельная/намеренная (PdfController отклоняет
      suffix `bytes=-N` per ADR-023 amendment — PDF.js их не шлёт).
- [x] **PageImageService S3-put-before-DB** — закрыто С64 (ADR-066): root cause —
      это единственный blob-writer, байпасивший каталог (`put` вместо
      `putAndRegister`), из-за чего janitor ложно флажил каждый page-image.
      Фикс: `putAndRegister(..., SCAN)` (тип уже в enum+CHECK, без миграции) →
      blob регистрируется в `library_files`, janitor authoritative, residual =
      восстановимый невидимый orphan. PageImageServiceIT 9 + OrphanDetectionJanitorIT 7.

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
- [x] **Z-index renormalization для long-running тем** - max+1 / min-1
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
  `Integer.MAX_VALUE/MIN_VALUE`. **Recovery path реализован С64:** `POST
  /api/v1/topics/{id}/renormalize-zindex` (компактизация z_index узлов+рёбер
  в 0..N сохраняя порядок, в одной транзакции; assertCanWrite owner/EDITOR/ADMIN;
  TopicControllerIT 5 кейсов + live-смоук). Overflow-гарды + recovery — оба есть.
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

- [x] **Flaky test: `bulkActions.test.tsx` (d3-drag + jsdom)** — закрыто
      (аудит С64): `test-setup.ts:25-47` мокает `d3-drag` (no-op chainable +
      no-op dragDisable/dragEnable), `bulkActions.test.tsx` зелёный 5/5. Полный
      прогон С64 — 887/887, флака нет.

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
  - **Закрыто (свёрнуто):** `react-hooks/set-state-in-effect` lint
    (RESOLVED Сессия 51, lint green 0/0) и
    `BookRepositoryIT.findAll_orderByCreatedAt` флак (исправлено Сессией 53
    — test-pollution, ассерт своих книг как подпоследовательности). Детали в
    progress.md/git.
  - **Narrator identity duplication в seed**: один и тот же человек (Суфьян
    ибн Уяйна, Малик) — отдельные `hd_narrators` записи per-hadith (разные
    UUID). Для dev-seed ок; реальный ETL должен дедуплицировать по identity
    (name_ar_normalized + era), иначе `/narrators/{id}/transmitted` покажет
    неполный корпус раввия. Учесть в Phase 5 (ETL `NarratorMapper`).
  - **[x] Smoke-тесты для graph-chrome** — закрыто (аудит С64): есть
    `ZoomControls.test.tsx`, `MinimapCard.test.tsx`, `HelpShortcuts.test.tsx`
    (все в `apps/argument-map/components/graph/`), зелёные.
  - **[x] v2→v3 token alias cleanup** (Minor) — закрыто С64 (commit a2ebafa):
    `Badge`/`BookListPage`/`EdgeDetailsPanel`/`edgeRules` мигрированы на v3-имена
    (`bg-status-ok-bg`, `bg-node-thesis`, `text-edge-attacks` и т.д., ground truth —
    `designTokens.ts EDGE_TYPE_TOKENS`); удалены `edge-*`/`type-*` alias-блоки в
    `index.css` + `tokens.css`. Чистый rename (значения идентичны), tsc 0, build OK,
    879 тестов. Широкий backward-compat слой (`accent-*`/`ink-*`/сотни consumers)
    оставлен намеренно — вне scope этого минорного пункта.
  - **[x] NodeDetailsPanel «Опора» тесты** — закрыто/устарело (аудит С64):
    `NodeDetailsPanel.test.tsx` зелёный 28/28 (3 «падающих» subtests более не
    воспроизводятся; MSW «unhandled request» stderr — нефатальный шум).
  - **Системная flakiness полного прогона (НЕ исправлено, требует выделенной
    работы):** корень — IT-классы делят один Testcontainers Postgres (context-
    cache), часть коммитит данные, часть ассертит «все строки». Каждый full
    `verify` краснит 1 случайный тест-«жертву» (зелёный в изоляции).
    Известные жертвы: `PdfControllerIT.streamPdf_withoutRange` (MinIO/timing),
    ранее `BookRepositoryIT` (исправлен). Durable fix — изоляция (per-class
    truncation либо `@Transactional` на коммитящих). Объём — отдельная
    тест-гигиена, вне Phase 5. До тех пор: упавший в full прогоне класс
    прогнать в изоляции прежде чем считать регрессией.

- [x] **GraphCanvas lastNodesRef comment fragility** (audit M-6) — УЖЕ закрыто:
      комментарий в `handleNodeContextMenu` (ныне ~L458-464) объясняет и почему
      ref сознательно вне deps useCallback, и несёт РЕГРЕССИЯ-ГАРД («превратишь
      ref в state → добавь в deps»). Ровно то, что просил аудит. Подтверждено С64.

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

**Закрыто (свёрнуто):** Rate limiting `/auth/login`+`/auth/register`
(in-memory sliding-window, 2026-05-19, ADR-046); Actuator endpoints
behind auth в prod (ADR-048); Refresh token rotation (миграция 46,
ADR-047); RefreshTokenCleanupJanitor (`@Scheduled` daily, 2026-05-19);
Edge z-order persistence (миграция 48, Сессия 47 — дубликат пункта выше).
Детали в progress.md/git.

- [x] **CreateQuestionPage raw-HTML render без sanitize** (audit M-4) — закрыто:
      `CreateQuestionPage.tsx:133` уже оборачивает `t('qa.create.hint_body')` в
      `sanitizePageHtml` (DOMPurify reader-path), ровно как предлагал аудит.
      Подтверждено С64.

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

### Editor для кастомизации текста книг — реализовано / частично отменено

**Закрыто (свёрнуто):** Tiptap-редактор страницы книги с богатой
типографикой РЕАЛИЗОВАН (ADR-039, Этап 17.0 — 8 custom extensions:
HadithBox / AyahBox / Marginalia / Footnote / ColorHighlight / Tashkeel /
DecoratedHeading / PageNumber). Хранение `lib_pages.formatted_content`
jsonb — миграция 33. **OCR-часть плана отменена** (Tesseract выпилен
ADR-057 / миграция 68, Сессия 55 — шаг «OCR ara → raw text» мёртв).
Источник истины по editor-стеку — ADR-039 + roadmap Этап 17; остаток
editor-доработок — в секции ниже.

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

## ✅ Turuq-граф: легенда «Цепи передачи» в режиме «Все пути» (найдено С60, ЗАКРЫТО С64)

Обе половины закрыты: (1) badge «основная»-шум скрыт — `primaryBadgeMeaningful`
в SanadGraph рисует ярлык только когда основная цепь ровно одна (не в turuq);
(2) `collectionRu`/`collectionAr` теперь заполняются в
`SanadGraphService.buildTuruqGraph` (commit `ec0521b`, аудит С64) — легенда
подписывает цепи сборником, а не дублирует «основная».

## Нумерованная пагинация × client-side фильтр = недостижимые страницы (ревью С62, I-1/I-2)

После C20 (нумерованная пагинация) на TopicListPage и BookListPage при активном
**client-side** фильтре пагинация скрывается — фильтр применяется только к 20
элементам текущей страницы:
- **TopicListPage**: текстовый поиск client-side (бэк `/topics` не знает `?q=`)
  → находит только темы на ТЕКУЩЕЙ странице.
- **BookListPage**: visibility-фильтр (MINE/SHARED/PUBLIC) client-side → при
  активном чипе пагинация скрыта, «мои» книги со стр.2+ недостижимы.
Унаследовано от Load-More, но нумерованная пагинация усиливает иллюзию «данных
больше нет» (номера обещают полноту). **Решение:** server-side фильтры (`?q=`
для тем, `?visibility=` для книг) — тогда фильтр+пагинация композятся. До тех
пор НЕ прятать пагинацию молча, а плашка «фильтр применён к текущей странице»
либо дизейбл с тултипом. Продуктовое решение Абдулы.

## usePagedList: мёртвая проверка issuedPage в stale-guard ✅ ЗАКРЫТО (С65, уже убрано)

Проверено С65: `usePagedList.ts` stale-guard использует ТОЛЬКО
`issuedGeneration !== generationRef.current` (ветка `issuedPage` отсутствует —
убрана ранее). tsc 0, usePagedList 8/8, lint 0. Пункт устарел.
