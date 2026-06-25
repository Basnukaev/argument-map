# Журнал работы

Последние сессии. Новые записи - **сверху**

Формат записи описан в `docs/doc-hygiene.md` Принцип 5

**Архив:** `docs/archive/progress-sessions-{1-21,22-29,30-37,38-45,46-52}.md`
(сессии ≤52). Здесь — 53+.

<!-- NEWEST-ENTRY-ANCHOR -->

## 2026-06-25 - Сессия 66 - backlog-автопилот: курация 5.b, mobile-аудит, ADR-067 PDF_LINK (бэкенд)

Продолжение автопилота после С65. **Развилка деплоя:** разобрался, что
argument-map НЕ задеплоен нигде (прода нет), а remblo — отдельный продукт со
своим бэкапом только его БД; «P0-2 backup (в remblo)» = «когда со-задеплоим на
тот же VPS», это first-time prod bring-up = ADR-уровень. Абдула: **деплой —
в последнюю очередь**, пока backlog-задачи. (memory `project_session_state`,
`project_remblo_deploy_setup`.)

**Сделано (6 коммитов):**
1. `5dd7a5c` **курация 5.b** — `ExplanationDto.authorDeathYear` теперь surface'ится
   (`toExplanationDto` терял уже собранное `hd_explanations.author_death_year`);
   `ExplanationsList` рисует «ум. {year} г.х.», `CurationFieldsPanel` получил
   текущее значение (был latent `value:null`). Component-тест. Каверза: dev-корпус
   = 0 explanations с author_death_year (приходит только через curation-override).
2. `0b3ba6a` **mobile hover-аудит** — весь фронт = 2 hover-reveal места; export-кнопка
   `TopicListPage` получила `pointer-coarse:opacity-100` (touch-видимость, верифиц.
   в build-CSS `@media (pointer:coarse)`); NodeCard handles оставлены (десктоп-first RF).
3. `4928fa7` **hadith_grades** — аудит (архитектор-агент) показал: backlog-пункт
   STALE, фича отгружена end-to-end в С62 (ADR-062 Вариант B, мост hd_hadiths↔sources).
   Закрыт как устаревший. Reimport-safe уже (mapper сохраняет source_id).
4. `4660d61` **ADR-067 PDF_LINK бэкенд** (главное) — FILE_ONLY (archive.org-сканы)
   нельзя было цитировать: PDF в `metadata.pdf_links`, не в `library_files` (та
   требует blob). Решение (архитектор-агент, выбран из 2): 5-й режим citation
   PDF_LINK по `(pdf_file_index,page,bbox)`, НЕ регистрировать archive.org в
   library_files. Миграция 80 (+pdf_file_index в 3 таблицы, 5-ветковый
   взаимоисключающий CHECK), CitationMode/Request/3 домена/3 сервиса (валидация
   index<files().size() через getMetadata без скачивания)/3 репо/PdfRef.fileIndex,
   85 IT. **Независимое ревью = APPROVE, 0 Crit/0 Imp, 4 Minor** (truth-table CHECK).
5. `70e3b50` **ревью-Minor #1** — DB-CHECK тесты для question/answer (был только node).
   Остальные 3 Minor + pre-existing баг (невалидный bbox→500 вместо 400: PdfBbox
   валидирует в compact-конструкторе → Jackson, не @Valid) — в backlog с обоснованием.

**Процесс:** 2 параллельных архитектор-агента (read-only) для развилок →
синтез в ADR/решение → executor (opus) для бэкенд-реализации → независимое
code-ревью (BASE/HEAD) → закрыть Minor #1 / backlog остальное. Делегирование
сберегло контекст в длинной сессии.

**FILE_ONLY PDF_LINK фронт — СДЕЛАН В ЭТОЙ ЖЕ СЕССИИ** (`6e27153b`): рестарт
бэка на новом коде + `npm run generate-api`; `CitationPickerPdfRegion`
(react-pdf + pointer-drag rect → нормализ. bbox, селектор тома, навигация
страниц); `CitationPicker` FILE_ONLY-ветка вместо заглушки (lazy+Suspense);
deep-link builders ×3 +`&fileIndex=`; `BookReaderPage` читает `?fileIndex=` →
`PdfViewer.initialFileIndex`; pdfRegion.ts чистые хелперы; i18n RU+AR; tsc 0,
926 тестов. **Live-смоук на стенде:** POST region-цитаты (Том 1/стр.5/bbox) →
201 mode=PDF_LINK + fileIndex round-trip → DELETE 204.

**Фидбэк Абдулы по ручному тесту → 4 фикса:** (1) `0e992f7b` — region-цитаты
рисовались как «Свободная» без локации/навигации: `isLibraryMode` не включал
'PDF_LINK' (→ FreeformCite) + SourceCard брал локатор из `c.location` (у pdf-
режимов null, локация в `c.pdf`). Фикс: 'PDF_LINK' в isLibraryMode + локатор из
`c.pdf` («стр./Том/▢ область») — чинит и старые FK-PDF; Q/A наследуют.
(2) `a7dade53` — список опор при нескольких источниках нечитаем → выбор Абдулы:
группировка по типу (Хадисы/Книги/Свободные + counts) + компактные
раскрывающиеся строки (`CompactRow`, SourceCard не тронут → Q/A не задеты).
(3) `be67269f` — в КНИГИ-строке было «(книга)» вместо названия: `pickLatinTitle`
прячет арабские title; свёрнутая строка теперь берёт реальное `source.title||
book.title` (dir=auto, font-naskh). (4) `390677eb` — pre-existing: невалидное
тело citation-запроса → 500 вместо 400 (`PdfBbox` в compact-конструкторе →
HttpMessageNotReadableException не ловился) → добавлен handler → 400
`malformed-request-body` (no-leak).

**Следующий шаг — РУЧНАЯ визуальная проверка Абдулы (всё рабочее, нужны глаза):**
(a) FILE_ONLY-цитата: тема→узел→«Привести»→FILE_ONLY-книга → PDF + селектор тома +
рисование прямоугольника → «Привести»; затем в списке опор цитата под «Книги» с
«стр./Том/▢ область» + «Перейти к источнику» → клик → подсветка совпала с нарисованным.
(b) Сгруппированный список опор: узел с разнотипными опорами → секции
Хадисы/Книги/Свободные, компактные строки, раскрытие по клику — читаемо. react-pdf
headless ограничен → именно ручная. Кривизна рисования/RTL/плотности — точечная итерация.
**Backlog-автопилот (С66):** быстрые/средние non-gated пункты вычерпаны.
**КУРАЦИЯ 5.b ЗАКРЫТА ЦЕЛИКОМ (A+B+review-fix) — ЭПИК КУРАЦИИ ПОЛНОСТЬЮ ЗАВЕРШЁН:**
- 5.b-A (`57acbfec`): overlay рави в графе иснада (SanadGraphService.applyNarrators
  на read-пути, NarratorData +overriddenFields, reveal-gated) + правка рави в
  NarratorPanel. IT SanadGraphNarratorOverlayIT.
- 5.b-B (`a8ffb1de`): transmission_phrase через СИНТЕТИЧЕСКИЙ стабильный ключ
  `entity_id=hadith_id, field_name='transmission_phrase@'+position` (зеркало Фазы 6 —
  `sanad_id` нестабилен на реимпорте). Выделенный PATCH `/hadith/sanad-narrators/
  transmission-phrase` (ADMIN), generic-эндпоинт reject, edit-only. ADR-065 amendment.
  HEADLINE IT TransmissionPhraseOverlayIT: правка переживает реимпорт. Независимое
  ревью = 0 Crit / 1 Imp / 5 Minor.
- Review-fix (`ead55734`): Imp — правка формулы ребра загейчена на `viewMode==='main'`
  (`SanadGraph.edgesEditable`): в turuq merged-графе hadithId ребра неоднозначен →
  правка ушла бы не в тот хадис. 5 Minor → backlog (нит/латентные).

**Следующий шаг — backlog почти вычерпан non-gated. Остаток = крупные фичи (нужна
спека/решение):** source-pickers (Коран/Хадисы/Книги),
нит-хвосты ревью-Minor #2/#3/#4. Деплой — в последнюю очередь (решение Абдулы).

## 2026-06-24 - Сессия 65 - ЭПИК КУРАЦИИ ЗАКРЫТ (фазы 0-6) + Tiptap RTL/polish

Автопилот: «посмотри прогресс/беклог, делай всё, на перепутье — best practice».
Приоритет из С64 был однозначен — **эпик курации данных** (P0-1 + FB-5), спека
`docs/specs/2026-06-18-data-curation-overlay.md` (фазы 0-6, ADR-065 заготовлен).
Фаза 0 (P0-1a merge-страховка перевода) была закрыта в С64-cont. Эта сессия
закрыла **ВЕСЬ ЭПИК (фазы 1-6) + 4 review-чекпоинта** (пилот / hide-show /
сателлиты / C9-overlay — ВСЕ APPROVE) + **2 Tiptap-фикса** (RTL-рендеринг +
AI-кнопка/ayah-box polish — баги Абдулы по скринам). ~22 коммита. После «далее» пилот
(1-3) продолжен Фазами 4-5; затем по скринам Абдулы — Tiptap-Arabic-фикс.

### Что построено (механизм)
Overlay-таблица `hd_field_overrides` поверх импортного корпуса. Импорт alminasa
hd_* **не трогаем** — правки/скрытия живут в overlay и накладываются **на
ЧТЕНИИ** (`OverrideApplyService` в `findById/findPage` хадиса/рави). → правка
переживает delete-recreate реимпорта (это и есть решение P0-1). Тот же паттерн,
что `hadith_grades` (ADR-062), но generic над любым полем.

- **Фаза 1** (`a5f7524`): миграция 78 (UNIQUE(table,id,field) + CHECK whitelist
  8 таблиц + payload-CHECK + FK users); `OverrideEntity` (Java enum, mirror
  CHECK), `FieldOverride` record (+`__record__`), `CurationWhitelist` (§5 —
  первоисточник `normalized_matn`/`full_text_ar`/`text_ar`/commentary `comments`
  СОЗНАТЕЛЬНО вне editable), `OverrideRepository` (upsert ON CONFLICT + batch
  findByEntity). ADR-065 в decisions.md. 15 тестов.
- **Фаза 2** (`23ffe05`): `OverrideApplyService`+`OverrideSet` (каст-помощники
  §3.4: битый int→WARN+base; hidden/null→null). **Репозиторный fold**:
  display-методы (`findById/findPage/findByIds/findBySourceIds/
  findByNarratorIdPage`) → EFFECTIVE; import/dedup (`findByExternalId/
  findByCollectionIdAndPrimaryNumber/findByNameArNormalized`) → RAW. Это
  load-bearing инвариант: правка НЕ может утечь обратно в импорт-write-path.
  12 тестов.
- **Фаза 3.a** (`43b101d`): generic `PUT/DELETE/GET /api/v1/admin/curation/
  overrides` (ADMIN-only), `CurationOverrideService` (whitelist+enum+reason+
  existence валидация, двойной аудит ADR-043 в той же tx), `CurationException`
  (единый параметризованный, 7 type-slug), api-contract.md. 11-кейс IT.
- **Review-fixes** (`7248aa6`): из независимого ревью (APPROVE, 0 Crit/Imp,
  8 Minor) закрыты дешёвые — isNull в audit-diff, base-vs-effective facet
  javadoc, stale-комменты, applyBool forward-note, name_ar позиционный тест.
- **Фаза 3.b** (`b17714e`): frontend `EditableField` (ADMIN inline-edit, паттерн
  C9; non-admin → plain) в HadithDetailPage (status/authenticity/hadith_type/
  chapter_ar/sub_chapter_ar) + NarratorDetailPage (reliability_grade/tabaqa/
  grade_text/kunya/laqab). i18n ru+ar. types.ts регенерирован. 7+46 тестов.
- **Фаза 4.a** (`8a63ba2`): record-level hide/reveal. `OverrideApplyService.
  applyRecordHide(table, records, idOf, reveal, mapper)` — читатель/гость:
  скрытая запись ВЫРЕЗАНА; ADMIN (по роли via SecurityContextUtils): приходит с
  `hiddenByAdmin`+`hideReason` (reveal §4.3). HadithController.getDetail
  (rulings/explanations) + NarratorController.getOne (commentaries). DTO +id/
  hiddenByAdmin/hideReason (additive). matns/sanads record-hide → Фаза 5.
  CurationHideIT. api-contract.
- **Фаза 4 review-fix** (`8dc6916`): из ревью (APPROVE, 0 Crit/Imp, 4 Minor) —
  +граница STUDENT-cut (reveal ТОЛЬКО ADMIN, не hasAtLeast), List.copyOf,
  commentary hideReason-ассерт.
- **Фаза 4.b** (`6cec418`): frontend `HideToggle` (EyeOff «Скрыть»→reason-модалка
  →PUT __record__; Eye «Показать»→DELETE) в RulingsList/ExplanationsList/
  NarratorCommentaryList; скрытая запись — затемнена+пилюля. i18n ru+ar. 5+51.
- **Фаза 5.a** (`28ac6fb` + review-fix `1de93a4`): field-edit + hide на
  сателлитах. 5 чистых static `apply(<Satellite>, OverrideSet)` (HadithRuling/
  Explanation/NarratorCommentary/Matn/Sanad — правят §5-editable, первоисточник
  passthrough) + `applyAndHide` (field-edit + record-hide одним батч-load).
  matns/sanads теперь EFFECTIVE+record-hide (были raw). MatnDto/SanadDto
  +hiddenByAdmin/hideReason. CurationSatelliteFieldEditIT 10. Review APPROVE
  (позиц. корректность 5 рекордов verified field-by-field); удалён мёртвый
  applyRecordHide.
- **Фаза 5.b** (`4c7bf40`): frontend `CurationFieldsPanel` (ADMIN-only сетка
  EditableField) в RulingsList/ExplanationsList/NarratorCommentaryList +
  MatnVariations (поля + HideToggle matns). i18n field.* ru+ar. 139+4 теста.
  **Отложено:** sanad-UI (только в RF-графе, SanadSummaryDto без reveal-полей)
  + hd_sanad_narrators.transmission_phrase + ExplanationDto.author_death_year
  не surface'ится.
- **Tiptap-RTL-фикс** (`510d982`): арабский текст всегда RTL-корректен
  НЕЗАВИСИМО от языка UI. Баг: блоки наследовали base-direction от `<html dir>`
  (по локали UI) → в RU(LTR) пунктуация уезжала. Фикс: `BlockDir`-extension
  `dir="auto"` на каждый блок (parseHTML null → JSON не меняется) + dir=auto на
  кастом-нодах + `unicode-bidi: isolate` на ayah/hadith-box. Playwright RU+AR
  verified. tsc/lint 0, vitest 904.
- **Фаза 6** (`1d5017c` + review-fix `6a9e2eb`): **C9-перевод матна → overlay,
  ЭПИК ЗАКРЫТ.** Ключ СТАБИЛЬНЫЙ `(hadith_id, is_primary)` — синтетические
  `primary_text_ru/en` на entity_id=hadith_id (НЕ matn.id, он меняется на
  реимпорте). migration 79 (data-migration text_ru/en примарных матнов →
  overlay; idempotent NOT EXISTS; non-destructive — колонки НЕ зануляет;
  на dev мигрировал 3 перевода). `applyMatns`/`applyWithPrimaryTranslation`
  (СОЗНАТЕЛЬНО не через applyAndHide — его empty-set early-return съел бы
  hadith-keyed перевод). C9 `editTranslation` пишет overlay @Transactional+
  audit (primary→hadith-key, non-primary→matn.id). P0-1a снят
  (`findPrimaryByHadithId` удалён, AlminasaHadithMapper не переносит перевод).
  Review APPROVE 0 Crit/Imp; guard primary_text_* на generic-эндпоинте.
- **Tiptap-polish** (`f5405ab`): AI-кнопка из бледной → solid indigo;
  ayah/hadith-box ornament'ы — корень: `﴿﴾` (U+FD3F/FD3E) НЕ в UI-шрифте →
  тофу `(`; fix (font-verified): `font-family: var(--font-ar)` на pseudo +
  перенос с углов на inline-края; hadith `«»`→ rose accent-bar + `◆`.
- **Reading-font фикс** (`19517f0`): «Арабский шрифт» не менял текст книги,
  «Шрифт интерфейса» — менял. Корень: FontPairEffect писал алиас `--font-arabic`,
  контент читает базу `--font-ar`; формат-контент шёл через `.prose`/--font-serif
  (unlayered бил `font-naskh`). Fix: контрол пишет `--font-ar`; reader-контент →
  `.prose-arabic`. Декаплинг verified (computed font-family). 15 тестов.
- **turuq-PNG gate** (`9305246`): чёрные боксы — не репро (FB-7a уже покрывал);
  defensive `visibleTransmissionPhrase` гейтит whitespace-only chip'ы. + закрыт
  стейл-пункт usePagedList M-1 (issuedPage уже убран). Остаток бэклога —
  gated/ADR/эскалации/эпики (Абдуле выбрать направление).

### Review (4 чекпоинта — ВСЕ APPROVE)
**Пилот (Фазы 1-3):** независимый code-reviewer (Opus) — **APPROVE, 0 Critical,
0 Important, 8 Minor.** Опасные инварианты подтверждены: нет утечки override в
импорт, позиционная корректность record-конструкторов, SQL-инъекция в
assertEntityExists невозможна (table = всегда один из 8 hardcoded enum-литералов),
первоисточник непробиваем, RBAC полон, аудит атомарен, нет N+1.
**Hide/show (Фаза 4):** независимый review — **APPROVE, 0 Critical, 0 Important,
4 Minor.** Security-крус «не-ADMIN leak» опровергнут: роль из аутентифицированного
principal (не форжабельна в prod), точное равенство `ADMIN`, cut-ветка вырезает
запись целиком (не null-маска), `hideReason` только в reveal-ветке, scope только
detail (list не отдаёт сателлиты). Дешёвые Minor закрыты; отложено: effective-facet
JOIN → §10 backlog; applyBool strictness → Фаза 5.
**Сателлиты (Фаза 5):** независимый review — **APPROVE, 0 Critical, 0 Important,
2 Minor.** Главный риск — позиционная корректность 5 `apply(<Satellite>)`
record-конструкторов — проверен field-by-field против domain-records + whitelist
+ Liquibase-схемы (нет swap page/volume, первоисточник text_ar/comments
passthrough). 2 Minor (мёртвый applyRecordHide + висячий javadoc) закрыты в
`1de93a4`.
**C9-overlay (Фаза 6):** независимый review — **APPROVE, 0 Critical, 0 Important,
2 Minor + 2 Open (low).** Два высших риска — data-migration (idempotent NOT
EXISTS / no-ADMIN-safe / non-destructive — колонки не зануляет / scoped rollback)
и apply-корректность (решение НЕ через applyAndHide verified — иначе empty-set
early-return съел бы hadith-keyed перевод) — выдержали adversarial. Minor #1
(guard primary_text_* на generic-эндпоинте) закрыт `6a9e2eb`; остальное
документировано (§10).

### Верификация
- Backend: каждая фаза — таргетный прогон зелёный; регрессия (Hadith/Narrator
  Controller IT, AlminasaMapper/SchemaRepository IT, SanadGraphService) зелёная.
- **Live-смоук** на :9090: PUT authenticity SAHIH→HASAN → `GET /detail` отдаёт
  EFFECTIVE HASAN → DELETE → откат к SAHIH; dev-таблица overrides очищена (0).
- Frontend: tsc 0, lint clean, 7+46 тестов; Playwright headless — detail
  рендерится без краша (аноним: бейджи plain, карандашей нет, матн цел).

### Инфра-стейт
- **Backend перезапускался дважды** (после Фазы 3.a и после Фазы 4.a — новые
  DTO/endpoint для generate-api). Сейчас на свежем master, :9090, JDWP :5005,
  полный env ai.env+proxy. Миграция 78 применена. **Перед `generate-api`
  бэкенд надо рестартить с новым кодом** (он отдаёт live OpenAPI).
- Dev-Postgres: 2 ADMIN-юзера (`...001` И `...002` — оба ADMIN!), hd_field_
  overrides пуста (live-смоуки подчищены). Dev-логин admin@argumentmap.local/
  admin12345. **psql:** `docker exec argumentmap-postgres psql -U argmap -d
  argumentmap` (юзер argmap, НЕ postgres).
- Frontend :5173 жив (HMR подхватил Фазы 3.b/4.b).

### Известное / отложено
- **Effective-facet**: `findPage` фильтрует authenticity/status по БАЗОВОМУ слою
  (apply после fetch) — override в фасет-счётчике не виден. §10, JOIN-проход в
  backlog (решение Абдулы: учитывать effective).
- **name_ar normalized-sync**: правка `name_ar` через overlay не пересчитывает
  `name_ar_normalized` (search по базовому) — overrides редки, отложено.
- **field-level hide на сателлитах** (напр. ruler_name рулинга): сателлиты ещё
  не в field-apply → field-hide пока без эффекта. Привязано к Фазе 5.
- 12 Minor из 2 ревью — все либо закрыты, либо привязаны к Фазе 5.

### Следующий шаг — ЭПИК КУРАЦИИ ЗАКРЫТ; остаток в backlog
Эпик курации (фазы 0-6) завершён. Открытых блокеров нет. **Хвосты (backlog,
не блокеры):**
- **Фаза 5.b:** (1) `hd_sanad_narrators.transmission_phrase` (композ. ключ
  `entity_id=sanad_id`, `field_name='transmission_phrase@{position}'`, спека §5);
  (2) sanad field-edit/hide UI — sanad только в RF-графе, `SanadSummaryDto` без
  `hiddenByAdmin`/`hideReason` (добавить + место под контролы вне графа);
  (3) `ExplanationDto.author_death_year` не surface'ится (правка пишется, «—» на
  показе — добавить в DTO или убрать из панели); (4) applyBool strictness.
- **Из ревью Фазы 6 (Minor/Open, документированы):** AI-`translate` cache-check
  по базовой колонке (после C9-overlay правки non-force translate может зря
  дёрнуть LLM — override всё равно побеждает на чтении); cleanup-миграция
  зануления `hd_matns.text_ru/en` (сейчас держим в обоих местах — безопасно).
- **§10 backlog:** effective-facet JOIN (фасет-фильтр authenticity по
  override-значению), фильтрация по всем версиям данных.
- **Прод-готовность (PROD-READINESS-AUDIT.md, ещё открыто):** бэкап/restore БД,
  env-плейсхолдеры DB-кредов, member-list анониму (P1-4 ADR-064).

Новое направление — спросить Абдулу (курация дала инструменты; контент/прод/
новая фича).

**Ручная проверка (Абдуле) — залогинься ADMIN:**
1. **Tiptap (2 фикса):** `/admin/library/pages/{id}/edit` в RU UI — арабская
   пунктуация на месте; **AI-кнопка** теперь solid indigo (видна); **ayah-box**
   с золотыми `﴿ ﴾` по краям (не `(` в углу), **hadith-box** с rose-баром+`◆`;
   переключи AR UI + dark — без регресса. И ридер книги. RTL глазами.
2. **Курация (весь эпик):** hadith/narrator detail — карандаши у бейджей/полей;
   вкладки «Вердикты»/«Шарх»/«Иляль»/«Гариб» + «Параллельные тексты» (матны) —
   «Правка полей» + `EyeOff` «Скрыть» (reason→пилюля); «Оценки учёных» рави.
   **Перевод матна** (C9 «Перевод RU/EN») правится и теперь живёт в overlay
   (переживёт реимпорт). Выйди из ADMIN — ни карандашей, ни «Скрыть», скрытые
   записи не приходят. RTL-вёрстка сетки «Правка полей».

## 2026-06-24 - Сессия 64 (cont.) - автопилот бэклога (OMC-оркестрация)

После графа-иснада Абдула: `/autopilot ... где перепутье — решай сам, best
practice`. OMC переподключён → оркестрация через worktree-executor'ы + architect
+ code-reviewer. **Доведены до конца все autopilot-safe пункты бэклога**, форки
решены мной; крупные фичи (Source pickers, RTL, full-text, hadith_grades,
FILE_ONLY citation, narrator dedup) НЕ трогал — это многосессионные эпики с ADR.

### Сделано (исполнители-executor'ы в worktree, я интегрировал cherry-pick'ом)
- **#6 thesis `إعداد`** (`9510114`) — автор диссертации не теряется (parser +
  thesisPreparer + resolveByName fallback, без схемы). Parser 19 + mapper IT 16.
- **#10 lint 0** (`9e88e5a`) — memo-hoist (preserve-manual-memoization) +
  eslint-disable mount-fetch. lint 0, 58 тестов.
- **#16 z-index renormalize** (`5a1ee9a`+types `c53cf1d`) — `POST /topics/{id}/
  renormalize-zindex` (компакт 0..N, assertCanWrite). 5 IT + live-смоук (0,0,0→0,1,2,
  non-writer 403). types.ts регенерирован.
- **#3 view-count дедуп** (`fd54389`) — in-memory sliding-window (clientIp,bookId);
  **форк решён:** счётчик публичен → POST /views permitAll в prod (`7a0089e`),
  дедуп=анти-инфляция. 9 unit + IT + GuestAccessProdProfileIT аноним→204.
- **#5 PageImage→SCAN** (`6342bbc`, ADR-066) — architect-дизайн: root cause =
  единственный blob-writer мимо каталога; `putAndRegister(SCAN)` (без схемы) →
  janitor authoritative. 16 IT.

### Валидация (autopilot Phase 4)
Code-reviewer: **APPROVE**, 0 Critical, 1 Important (pre-existing view-auth — закрыт
форк-решением), 6 Minor (callCount→AtomicInteger, XFF-trust note, инвариант
eviction — закрыты в `7a0089e`; остальные no-fix). Integration-прогон 79 backend-
тестов + 19 (prod-guest+dedup) зелёные. ADR-065 коллизия (зарезервирован под
курацию) → PageImage переименован в ADR-066.

### ЭПИК КУРАЦИИ: Фаза 0 СДЕЛАНА (`f7b6fb5`); далее Фаза 1 (свежей сессией)
**Фаза 0 (P0-1a) закрыта:** реимпорт alminasa больше не теряет ручной перевод
матна — `AlminasaHadithMapper.mapHadith` читает primary-матн ДО delete-recreate
(`MatnRepository.findPrimaryByHadithId`) и переносит `text_ru/en` в новую строку.
AlminasaMapperIT 8/8. Снять после Фазы 6 (перевод уедет в overlay).
**СЛЕДУЮЩИЙ ШАГ = Фаза 1** (свежей сессией, по `docs/specs/
2026-06-18-data-curation-overlay.md` §Фаза 1): миграция `20260618-78-hd-field-
overrides` + `FieldOverride`/`OverrideRepository` + `CurationWhitelist` + ADR-065
в decisions.md. Затем Фазы 2-6 (apply-слой, пилот, hide/show, сателлиты, миграция
C9-перевода в overlay).

Главный приоритет — эпик курации данных (P0-1+FB-5), `docs/specs/
2026-06-18-data-curation-overlay.md`, ADR-065 (зарезервирован). Остаток бэклога
после автопилота — только gated/эпики: #9 migration-guard (immutable), Source
pickers / RTL / full-text / hadith_grades / FILE_ONLY-citation / narrator-dedup
(каждый — отдельный ADR+спека). Инфра: backend перезапущен на свежем master
(:9090, JDWP :5005), dev-логин admin@argumentmap.local/admin12345.

## 2026-06-23 - Сессия 64 - ELK-граф иснада + автопилот/сверка бэклога

Продолжение С63. Абдула: «продолжи фикс графа через elk», затем «на автопилоте все
таски из беклога». Сделано 6 коммитов + сверка бэклога с кодом.

### Граф иснада: ELK orthogonal routing (headline, `26d0054`)
Замена dagre+smoothstep на ELK layered (Проблемы 1/2/3 Абдулы из С63):
- `sanadElkLayout.ts` — layered DOWN, ORTHOGONAL, `edgeNodeBetweenLayers`=40 (зазор
  ребро↔карточка, Проблема 1), `edgeEdgeBetweenLayers`=22 (развод параллельных, П.2).
  Проще argument-map: БЕЗ инверсии direction (sanad source=ранний рави уже = ELK DOWN),
  БЕЗ layerConstraint, БЕЗ radial.
- `SanadCustomEdge.tsx` — рисует ELK bend-points через переиспользованный
  `orthogonalPath.ts` (огибание карточек), подпись-формула в EdgeLabelRenderer на
  середину сегмента (`pickLabelPosition`, не на узле — П.3).
- `SanadGraph` — async-раскладка (dagre мгновенный fallback → ELK по готовности,
  `cancelled`-guard), edge type 'smoothstep'→'sanad', dim подписей через `data.dimmed`
  (сохраняет click-highlight `fcb6aaf`).
- Верифицировано: single-chain (playwright + PNG — подписи-чипы читаемы, не чёрные),
  fork (real-ELK юнит-тесты: ветви >100px + bend-points), 122/122 hadith-тестов.
  Корпус строго одноцепочечный (каждый хадис=1 sanad) → branchy покрыт юнит-тестами.

### Автопилот бэклога: 3 фикса + сверка с кодом
- `8cb9d33` usePagedList M-1: убрана мёртвая `issuedPage`-ветка stale-guard.
- `0e39d56` FB-2: detach-× опоры скрыт от гостя (`onDetach` optional во всём
  citations-дереве) + тест. **Закрывает guest-view.**
- `a2ebafa` (форк worktree, cherry-pick) v2→v3 token cleanup: Badge/BookListPage/
  EdgeDetailsPanel/edgeRules на v3-имена, удалены `edge-*`/`type-*` alias-блоки;
  визуально подтверждено (playwright: SUPPORTS зелёный, RESPONDS_TO серый).
- `32d1fd8` **сверка бэклога**: subagent-аудит 16 пунктов bug-hunt/audit/code-review
  → **11 фактически закрытых в С52–С63 отмечены [x]** с evidence (Load-More race,
  PageView highlight, thesis IT, bulkActions flak, NodeDetailsPanel «Опора»,
  graph-chrome smoke, turuq-легенда, MinimapCard clamp, CreateQuestionPage sanitize,
  GraphCanvas comment, HadithListPage asymmetry). Аудит ошибочно пометил «формы
  хамзы U+0672+» как DONE — поправлено (NFKC их НЕ нормализует, проверено расчётом;
  остаётся отложенным YAGNI).

### Граф иснада — фидбек Абдулы, 4 пункта (`b898a96`)
После ELK-свитча Абдула прогнал граф руками (реальный turuq `5f0809fa`, 37 узлов /
12 цепей — наконец нашёлся branchy-кейс): (1) карточка передатчика не открывалась
в fullscreen — рендерилась родителем СНАРУЖИ fullscreen-элемента → перенёс рендер
ВНУТРЬ `SanadGraph.containerRef` (controlled-проп `selectedNarrator`); (2) редизайн:
панель-«бандура» во всю высоту → компактная плавающая карточка 330px в углу;
(3) карточка по ДВОЙНОМУ клику (одиночный = подсветка). RF `onNodeDoubleClick` на
кастом-узлах не срабатывает + `onNodeClick` на dblclick зовётся раз → нативный
dblclick (capture) + hit-тест через `screenToFlowPosition` (DOM-таргет =
`.react-flow__pane`, слой узлов pointer-events:none). Подсказка управления — в hint
+ секция «Управление» в легенде; (4) прямые 90°-углы рёбер вместо скруглённых +
`collectChainThroughEdge` (клик ребра подсвечивает ТОЛЬКО его цепь: вверх от source,
вниз от target — без сестринских веток madar'а). Playwright-верифицировано.

### СЛЕДУЮЩИЙ ШАГ — без изменений: ЭПИК КУРАЦИИ (фазы 0→6)
Эпик курации данных (P0-1 + FB-5) из С63 **остаётся главным приоритетом** (Абдула:
делать в отдельной сессии). Спека: `docs/specs/2026-06-18-data-curation-overlay.md`,
фазы 0→6 (см. запись С63 ниже). Граф-фикс С64 был отдельным треком.

Остаток открытого бэклога (после сверки) — **genuinely open, но gated/deferred**:
- view-count dedup (gated: anti-spam стратегия + новая таблица = решение Абдулы);
- PageImageService S3-put-before-DB (prod-hardening; янитор сверяет library_files,
  куда page-images не пишутся → сейчас log-only);
- thesis `إعداد` author-loss (мелкое, но shamela book-ETL = деприоритизирован);
- migration-69 guard (immutable changeset, только будущей миграцией);
- 2 lint-ошибки (флаг «не трогать наобум», React-Compiler memo/effect риск);
- z-index renormalize endpoint (low: overflow-гарды уже есть).

### Инфра-стейт (без изменений с С63)
Backend+frontend на :9090/:5173, docker postgres+minio. graphify глобально.
Тест-данные: PUBLIC-темы `59ef9415` (Сигареты, 3 узла/2 ребра), `ceb9f28a`;
одноцеп. хадис `b81d260c` (11 узлов, граф иснада). Dev-логин
admin@argumentmap.local/admin12345. Полный прогон С64: 887/887 фронт-тестов,
lint 2 pre-existing ошибки (не мои).

## 2026-06-23 - Сессия 63 - Фаза-2 фидбек Абдулы (7 пунктов) + дизайн курации данных

После handoff С62 Абдула дал 7 пунктов фидбека (ручной тест) + запрос на прод-фазу.
Спека фидбека: `docs/specs/2026-06-18-phase2-feedback.md`. Quick-wins исполнены +
большой архитектурный трек «курация данных» спроектирован.

### Quick-wins (8 коммитов)
- **FB-2 guest-UI гейтинг** (недоделка guest-view ADR-064): ч.1 «Админ» из нав+палитры
  (`hasRoleAtLeast ADMIN` в Header/CommandPalette); ч.2 edit-контента + add-цитат в
  NodeDetailsPanel-секциях (`canWrite` TopicGraphPage→GraphCanvas→панель→
  NodeContentEditor/NodeCitationsSection). Pane/node контекст-меню уже гейтились.
  Follow-up: detach × (backlog).
- **FB-4a** AR-клавиатура: алфавит → стандартная физ. раскладка (Arabic 101, dir=ltr).
- **FB-6** ADMIN перегенерация перевода (force=true) в MatnTranslateControls.
- **FB-7a** PNG edge-метки: html-to-image не резолвил CSS-vars в клоне → чёрные боксы;
  `withInlinedCssVars` копирует `--*` инлайн на время toPng. Метки формул читаемы.
- **FB-7b** turuq-граф «Все пути»: nodesep 56→88, ranksep 84→100 (узлы не наседают,
  одноцеп. режим не затронут); глубокое распутывание — backlog.

### Курация данных (P0-1 аудита + FB-5) — СПРОЕКТИРОВАНА, готова к билду
Абдула выбрал **overlay-таблицу** `hd_field_overrides`. Детальная спека:
`docs/specs/2026-06-18-data-curation-overlay.md` (801 стр, ADR-065 draft, фазы 0-6).
Решения Абдулы (§10): matn-перевод ключуется `(hadith_id, is_primary)`; фасет-фильтр
по **effective** (override-applied) значениям, не базовым; commentary verbatim = только
скрытие записи. Механизм: импорт пишет hd_* как есть → `OverrideApplyService` на
`findById/findPage` накладывает override+hidden на чтении; import-путь
(`findByExternalId`) overrides НЕ применяет. Whitelist: правимо=метаданные/
классификации (authenticity 2228 NULL, рави reliability/tabaqa), запрещено=
первоисточник (full_text_ar/normalized/text_ar/commentary).

### СЛЕДУЮЩИЙ ШАГ — ЭПИК КУРАЦИИ (фазы 0→6) по `data-curation-overlay.md`
1. **Фаза 0 (P0-1a, быстро):** спасти перевод от реимпорта — `AlminasaHadithMapper.
   insertMatn` upsert по природному ключу с сохранением text_ru/en (вместо
   delete-recreate с новым UUID). Минимальная страховка до overlay.
2. **Фаза 1:** миграция `20260618-78-hd-field-overrides` + repo + ADR-065 в decisions.md.
3. **Фаза 2:** `OverrideApplyService` (apply на доменных records, батч-load, каст text→тип).
4. **Фазы 3-6:** пилот (hadiths+narrators) → hide/show → сателлиты → миграция C9-перевода.
Параллельно/после: прочие P0/P1 аудита (бэкап БД, env DB-креды, member-list анониму P1-4).

### Остаток фазы-2 (нужны спеки/решения Абдулы)
- **FB-1** student AI-format + docx/pdf экспорт (название вкладки 🟡, ADR+спека).
- **FB-3** связь QA↔граф (модель дискуссии 🟡, ADR).
- **FB-4b** подсветка участка совпадения поиска (бэк matched-field+ranges, спека).

### Инфра-стейт
Backend+frontend на :9090/:5173. graphify установлен глобально (uv tool, бар `graphify`).
Тест-данные: PUBLIC-темы `59ef9415` (Сигареты, 3 узла), `ceb9f28a` (Тест); turuq-хадис
`89c76e3f` (10 цепей, 38 узлов), одноцеп. `b81d260c` (9 рави). Dev-логин
admin@argumentmap.local/admin12345.

## 2026-06-18 - Сессия 62 - Hadith Explorer UX-фидбек (22 пункта) + автопилот очереди + прод-аудит

Продолжение С61-автопилота. Абдула прогнал Hadith Explorer руками → **22 пункта**
UX-фидбека (карточка рави, detail хадиса, список). Тимлид-триаж → спека
`docs/specs/2026-06-17-hadith-explorer-ux-feedback.md` (волны 0-5) → исполнение
параллельными opus-исполнителями + мой verify-гейт + playwright на каждом UI-коммите
+ независимый review рисковых коммитов. Форки D1-D4 решены Абдулой (2-осевая
таксономия, слить вкладки, править перевод, полный guest-view, нумерованная пагинация).

### Сделано (волны 0-5, ~18 атомарных коммитов)
- **#4 оценки учёных** — мост hd_hadiths↔sources (ADR-062 Option B, lazy source_id).
- **B2/B3/B4 карточка рави** — label:value «поля-карточки» (дизайн Field Layout Вар.1),
  i18n status-чипов, дедуп тройного эпитета (tabaqa=null сподвижникам, на re-map).
- **C6/C16 гариб** — поповер скролл+контраст+clamp + тримминг пунктуации.
- **C8/C19/C21/D1 таксономия** — миграция 77 `authenticity` (SAHIH/HASAN/DAIF/MAUDU,
  эвристика по рулингам), 2-осевые фасеты, hadith_type i18n (ADR-063).
- **C7/C10/C18 detail** — вкладки-switcher + слияние Текст/Иснад + гариб в огласованный.
- **C11/C12/C13/C15 граф** — легенда скролл+highlight+сворачивание, зум 400%, fullscreen
  (тема-фон не чёрный), empty-state такхриджа. **C14a (запутанность) отложен** — все
  31999 хадисов одноцепочечные, путать нечего (диагноз в спеке).
- **A1/49.G guest-view** — permitAll GET в prod + RBAC service-фильтр (ADR-064);
  побочно закрыт pre-existing IDOR (BookService.getPage) + NPE (GlobalExceptionHandler).
- **C20 пагинация** — нумерованная cross-app (usePagedList + Pagination, deep-link ?page=).
- **B5 AR-клавиатура** — попап в SearchInput (хадисы/рави/книги).
- **C9 правка перевода** — PATCH /matns/{id}/translation (ADMIN, бэк IT 8/8) + edit-UI (фронт).
- **C14b PNG-экспорт** иснад-графа high-res (graphExport → shared, ADR-022).
- **C17 diff** параллельных текстов (siblingDiff LCS + normalizeArabic). **Очередь
  Абдулы (guest/пагинация/AR/перевод/PNG/diff) — ЗАКРЫТА полностью.**

### Независимый review (opus) — поймал 1 Critical
guest-view/C9-бэк/C20. **C-1:** permitAll-глоб `/library/pages/**` открыл анониму ещё
2 под-ресурса (`/regions`, `/ai-edit`) без read-guard → анон-IDOR на метадату приватных
книг. **Зафикшено** (assertCanReadBook + регресс GuestAccessProdProfileIT). I-1/I-2
(client-фильтр прячет пагинацию) + M-1 → backlog.

### Прод-аудит (запрос Абдулы, отдельный агент → `PROD-READINESS-AUDIT.md` в корне)
Главная боль подтверждена кодом: **реимпорт молча затирает ВСЁ** (mapHadith
delete-recreate matns/sanads/rulings — перевод исчезает с новым matnId; рави — все 20
колонок; защиты manually_edited/lock/overrides НЕТ нигде). **P0:** защита правок от
реимпорта, бэкап/restore БД (нет вообще), env-плейсхолдеры DB-кредов. **P1:** manual-edit
эндпоинты hd_*, data-health вью, закрыть member-list анониму.

### Находки/гочи
- **playwright ловит то, что мок скрывает (2×):** C20 deep-link сброс (firstRun-ref ×
  StrictMode-double-invoke → gotcha записан) + C14b PNG-кроп (getNodesBounds на
  недомеренных узлах → width=0, 96px-полоска). Невидимы юнит-тестам/tsc, вскрыты только
  живым прогоном + проверкой артефакта (размеры PNG).
- types.ts дрейфовал (фичи шли на ручных типах) — регенерён (b49a7e9).

### Инфра-стейт
Backend перезапущен с новым кодом (guest-view+C9+C-1), JDWP :5005, порт 9090. Корпус:
**31999 хадисов (ВСЕ одноцепочечные)**, 7789 рави, 28035 с crossref-siblings, 1 матн с
переводом. Dev-вход admin@argumentmap.local/admin12345. Хадисы для проверок: `22687c64`
(149 siblings — diff), `b81d260c` (9-рави цепь — PNG/граф/вкладки).

### Следующий шаг — ПРОД-ФАЗА (Абдула запросил после очереди), по `PROD-READINESS-AUDIT.md`
1. **P0-1a (быстрый, начать с него):** перестать терять перевод при реимпорте —
   `AlminasaHadithMapper.insertMatn` (стр.305) не должен delete-recreate matn с
   переводом; upsert по природному ключу (hadith_id + printed_number) с сохранением
   text_ru/text_en. Минимальный фикс острейшего случая, без архитектурного решения.
2. **P0-1 (архитектура, нужен ADR):** общая защита ручных правок от реимпорта —
   overlay-таблица `hd_field_overrides` vs lock-колонки vs merge (3 варианта в аудите
   §3.3). Разблокирует P1-1 (manual-edit эндпоинты — иначе правки затрутся).
3. **P0-2/P0-3:** бэкап/restore БД (ранбук в remblo) + env-плейсхолдеры DB-кредов
   (`application.yml:371`, сейчас захардкожены localhost/argmap/argmap).
4. **P1-4 (security, дёшево):** закрыть member-list анониму (`assertIsMemberOrAdmin` на
   `/topics|books/{id}/members`) — тот же открытый вопрос ADR-064.
Затем P1: manual-edit эндпоинты hd_* (authenticity/рави) + data-health вью + admin-UI.
Tiptap-редактор (ADR-039) готов — Абдула хотел «пощупать»: `/admin/library/pages/{id}/edit`.

## 2026-06-16 - Сессия 61 - narrator-commentary + автопилот по беклогу (13 задач)

Автопилот-марафон: дочинить narrator-commentary, затем автономно пройти
беклог, в конце сводка ручных проверок. Координатор-модель: я + параллельные
исполнители-агенты (правки файлов на диске = надёжно), независимый review +
мой verify-гейт на каждом коммите.

### narrator-commentary — джарх/таʿдиль о рави (ADR-061, миграция 76)
Клон Плана 8 со сдвигом ключа джойна **хадис→рави** (`hd_narrators.external_id`).
ES `narrator-commentary-12` (32 848 доков) → `hd_narrator_commentaries`
(live **29 546**, re-map 7 789 рави 0 ошибок) → секция «Оценки учёных о
передатчике» на карточке рави. Backend X.a–X.d (opus-исполнитель) + frontend
X.e + live backfill + playwright (Абу Хурайра, Ибн Хаджар «الصحابي الجليل»).
Review **APPROVE 0C/0I** + 5 Minor (MINOR-1 silent-truncate → backlog).
План: `docs/plans/2026-06-16-alminasa-narrator-commentary.md`.

### Корпус: НЕ был пуст (коррекция С60-handoff)
С60 писал «БД пустая», реальность: краул `hadith-12` был **PAUSED на 32k/82.6k**
(не пустой), а علل/غريب НЕ re-map'нуты. С61: backfill علл/غريب (2 289 commentary
+ 4 168 ambiguous staged) → re-map хадисов → `hd_explanations` **GHARIB 62 808
+ ILAL 1 967 + SHARH 25 978**. Корпус восстановлен к фиделити С59.

### Беклог-автопилот (13 закрыто)
**Сделано кодом:** #2 turuq-легенда (collectionRu/Ar из hd_collections, ярлык
«основная» только при 1 primary-цепи), #3 гариб-подсветка (HighlightedMatn +
highlightGharib normalizeArabic), #5 Load More race (BookListPage→usePagedSearch
generation-guard), #6 MinimapCard clamp (padMinX центрирование), #7 PageView
highlight (Tiptap onEditorReady-сигнал готовности DOM), #8 CreateQuestionPage
sanitize-wrap, #10 GraphCanvas comment (M-6), #12 thesis round-trip IT (5),
#13 graph-chrome smoke (44).
**Stale (устранено ранее, не делалось):** #9 (mock-логи AdminShamela удалены
Фазой 7), #11 (usePagedSearch хук уже существовал — применён к BookListPage =
#5), #14 (d3-drag мок в test-setup.ts с С51 → bulkActions 5/5 в full-run).
**#4 ЭСКАЛИРОВАНО (архитектура, код НЕ писан):** `hadith_grades` (миграция 43,
POST по `sources.id`, таблица+enum) НЕ сведён с alminasa `hd_hadiths`
(detail читает `metadata.grades` jsonb freeform, read-only). id+schema mismatch
→ ADR Абдулы, 3 варианта в backlog.

### Гочи/находки
- **`ai.env` + `./mvnw verify` = ложный фейл:** засорсил ai.env (для SHAMELA-
  прокси бэка) → `DEEPSEEK_API_KEY` протёк в `HadithTranslationNotConfiguredIT`
  (ждёт «AI не настроен → 503») → 1/1355 фейл. Чистый env в изоляции → зелёный.
  **Не сорсить ai.env перед verify.**
- **OMC-агенты съедают финальное текстовое сообщение** (хук «standing by») →
  велеть писать результат в файл (`/tmp/*-done.md`); имплементацию (правки на
  диске) это не трогает, верифицирую сам.
- **Pre-existing lint (2, не С61):** `AdminHadithImportPage.tsx:108`
  set-state-in-effect (Плана 5) + `HadithDetailPage.tsx:214` React-Compiler
  preserve-manual-memoization (С59). Baseline красный с С56 (был зелёный С51).

### Верификация
- Backend `./mvnw verify`: **1354/1355** (1 = env-артефакт выше, изолированно
  зелёный); narrator-commentary IT все зелёные на чистой схеме (76 changesets).
- Frontend: build ✓, **814/814 тестов** ✓, lint 2 pre-existing errors.
- narrator-commentary review APPROVE 0C/0I.

### Инфра-стейт
- Корпус: `hd_hadiths` 31 999, `hd_narrators` 7 648, `hd_narrator_commentaries`
  **29 546**, `hd_explanations` ~90.7k. Краул `hadith-12` PAUSED 32k/82.6k
  (можно дотянуть). Миграции через **76**.
- Backend :9090 (narrator-commentary код + миграция 76, JDWP :5005), frontend
  :5173. dev-логин `admin@argumentmap.local / admin12345`.

### Следующий шаг
1. **Решение по #4** — мост `hadith_grades` ↔ alminasa `hd_hadiths` (ADR, 3
   варианта A/B/C в backlog: jsonb-POST / связать с sources / новая таблица).
2. Pre-existing lint (2 ошибки) — focused follow-up (не трогать наобум).
3. Опц.: дотянуть краул `hadith-12` 32k→82.6k.
4. Дальше по выбору: **49.B** rating+pagination / **49.D** observability (спеки
   готовы), либо HAR-находки (chains-links-12 богатые рёбра сети, references-
   каталог 86 книг, narrator-commentary расширенный профиль).
5. Письмо alminasa (вежливость) — backlog.

## 2026-06-06 - Сессия 60 - чистая БД: смок установки с нуля + фикс флаки-логаута по F5

По просьбе Абдулы **полная очистка данных** под смок чистой установки:
`docker compose down -v` (Postgres + MinIO volumes), бакеты пересозданы
init-контейнером.

### Смок чистой установки — зелёный
- **Все 75 Liquibase-миграций накатились с нуля без ошибок** (главная
  ценность прогона — целостность цепочки миграций).
- API: topics/books/qa/hadiths/narrators — 200 с корректными пустыми
  PagedResponse; crawl/backfill чекпоинты IDLE.
- UI (playwright headless): логин + 6 экранов — пустые состояния с CTA,
  админ-импорт с каталогом сборников по нулям.

### 🐛 Найден и закрыт: флаки-логаут по F5 (`847d257`)
Смок выявил до **трёх** конкурентных `POST /auth/refresh` на одну
перезагрузку: single-flight жил только в interceptor'е `client.ts`,
а bootstrap (`loadCurrentUser`) звал refresh стора напрямую. Ротация
(ADR-047) валидирует только первый запрос — проигравший 401 стирал
сессию победителя → случайный выброс на /login. Pre-existing, не
следствие чистки. Фикс: single-flight на уровень `authStore`
(модульный `refreshInFlight`), +2 теста (конкурентные → 1 POST,
последовательные → честные отдельные ротации), gotcha. После фикса:
5/5 перезагрузок — ровно один refresh=200.

### Верификация
- vitest **757/757** (было 756 + 1 новый).
- Изолированный перегон `ObjectStorageServiceIT` — **17/17 зелёный**
  (свежее подтверждение: 17 errors в s59-verify-логе = TC-флака
  context-кеша, `Connection refused` к порту умершего контейнера).

### Инфра-стейт (ИЗМЕНИЛСЯ!)
- **dev-БД ПУСТАЯ** — корпус 33k снесён вместе с volume. Тестовый
  логин pw-admin-57@test.local пропал (был данными).
- Живой dev-логин: `admin@argumentmap.local / admin12345`
  (auto-seed DevUserSeeder, профиль local).
- Серверы: backend :9090 (DeepSeek env + JDWP :5005), frontend :5173.

### Следующий шаг
По команде Абдулы — перекраул корпуса (админка → краул → backfill →
маппинг, ~1-2 ч фоном). Дальше по выбору: 49.B rating+pagination /
49.D observability (спеки готовы) либо backlog-находки HAR
(джарх-цитаты о рави). Письмо alminasa — в backlog.

## 2026-06-06 - Сессия 59 - ВСЕ юзер-гейты сняты: HAR→وкладки علل/غريب (План 8), DeepSeek live, хвост фидбека

Абдула принёс: свежие HAR с кликами по вкладкам علل/غريب (+бонусные
almuradji3/arruvat), DeepSeek-ключ, 2 UX-замечания. Автономный марафон
~12 коммитов. **alminasa-трек теперь закрыт ПОЛНОСТЬЮ — гейтов не
осталось.**

### DeepSeek live (гейт «ждёт ключ» снят)
Ключ в `~/.config/argument-map/ai.env` (600, вне гита); бэк стартует с
`AI_PROVIDER=deepseek + DEEPSEEK_API_KEY + AI_HTTP_PROXY=$HTTPS_PROXY`
(прямое соединение режется — корп-прокси обязателен, gotcha С55).
**Перевод живьём работает**: 594-1 → RU «Совершенство веры верующего —
в его благом нраве» (2.3с), EN тоже; cached=true на повторе.

### HAR-разбор (гейт «ждёт HAR» снят)
Субагент разобрал 3 HAR: علل = `hadith-commentary-12` (джойн
commentary.narrations[] ⊇ hadith_id), غريب = `ambiguous-12` (словарные
статьи; ids из hadith-doc `ambiguous[]` — **УЖЕ в нашем staging raw у
16 784 хадисов** → перекраул не нужен). Бонус-находки → backlog:
chains-links-12 (рёбра сети с глаголами передачи), narrator-commentary-12
(джарх-цитаты о рави), references (каталог корпуса). Фикстуры —
`backend/src/test/resources/alminasa/s59/`.

### План 8 — вкладки علل/غريب (`docs/plans/2026-06-06-alminasa-ilal-gharib.md`)
Критика плана ДО исполнения: REVISE → C1 (курсору backfill негде жить
в чекпоинте → one-shot in-memory keyset, коарс-прогресс, свой executor),
C2 (точный GIN-SQL `narrations @> ?::jsonb`, bind Jackson-массивом),
M1 (батчинг per-index), M3 (ключ GHARIB hadith×ambiguous_id×reference_id),
M4 (text=commentary_text). Реализация: миграция 75
(am_staging_commentary GIN + am_staging_ambiguous),
AlminasaDependentsBackfillService + REST backfill/{start,pause,status},
insertExplanations ILAL/GHARIB (SHARH выживает re-map),
ExplanationDto.reference, UI: три секции شروح/علل/غريب
(гариб-карточка: СЛОВО-заголовок + словарь·автор).
**Live: backfill 33k за 17 мин (2 350 commentary + 4 210 ambiguous,
0 ошибок) → re-map 33 299/33 300 → hd_explanations: 65 280 GHARIB +
2 018 ILAL + 26 871 SHARH.** API-проверка: 184-1 — 4 гариба (أَبْعَدَ،
الْمَذْهَبَ × 2 словаря), 146-2 — иляль Даракутни.

### Хвост фидбека С58
«في الإسناد» и при клике из ГРАФА (map externalId→textForm из
parseIsnadHtml); «Неизвестно (маджхуль)» → «Без оценки» (маджхуль —
джарх-термин, у нас отсутствие записи); sibling-matns endpoint + секция
«Параллельные тексты» (ответ на «где вариации»: вариации alminasa =
матны параллельных передач).

### Review (объединённый С58-59, диапазон a67f9c7..HEAD)
**APPROVE: 0 Critical / 0 Important / 5 Minor** — 3 закрыты (фантомный
RUNNING чекпоинта при TaskRejected, мёртвая переменная, unmount-гарды),
2 приняты с обоснованием (SAHABI без нормализации, index-key с
комментарием). Verify 1326/1326 (+17 ObjectStorageServiceIT —
TC-flake, изолированный перегон 17/17 зелёный). vitest 76/76 hadith.

### Известное
- 594-2472 — единственный фейл re-map (пустой matn_with_tashkeel в
  источнике, честный skip).
- Иляль-покрытие разреженное по природе данных (~2k доков на корпус).
- Подсветка гариб-слов прямо в матне — backlog (metadata.reference
  уже несёт слово и позицию).

### Следующий шаг
alminasa-трек завершён целиком (Планы 1-8, все гейты сняты). Дальше:
49.B rating+pagination / 49.D observability (спеки готовы) либо
backlog-находки HAR (jарх-цитаты о рави — красивая следующая фича
для карточки передатчика). Письмо alminasa (вежливость/официальный
доступ) — по-прежнему рекомендовано, пункт в backlog.

## 2026-06-05/06 - Сессия 58 - фидбек Абдулы по Explorer: 2 live-бага + граф «Все пути» + UX

Абдула прогнал Explorer на собственноручно накрауленных **33k хадисах**
(юзер-гейт массового обхода снят им самим) и дал 5 пунктов фидбека.
Автономный фикс-марафон: ~7 коммитов.

### Live-баги (вскрыты полным корпусом, фикстуры их не ловили)
1. **Писцовые аббревиатуры формул**: `ثنا/ثني/أنا/نا/أنبأ` не были в
   словаре парсера → `transmission_phrase NULL` у половины звеньев
   Мустадрака. Фикс: аббревиатуры канонизируются к полным формам
   (`ثنا`→`حدثنا`) — стрелки графа единообразны.
2. **Абу Хурайра «маджхуль»**: level `الصحابي الجليل` ≠ строгое `صحابي`.
   Фикс: contains-детекция корня `صحاب` в level + startsWith в gradeText
   (519 сподвижников распознано). Gotcha записана.

### Новая фича — объединённый граф طرق (идея Абдулы из спеки §E)
- `GET /hadiths/{id}/turuq-graph`: граф ВСЕХ путей — главный хадис +
  resolved-crossref сиблинги; narrator-узлы дедуп по UUID, рёбра с
  агрегированным sanadCount.
- **Version-узлы** (role=VERSION, VersionInfo: сборник/№/превью матна) в
  конце каждой цепи — и в обычном графе (цепь больше не обрывается в
  пустоту); клик → переход к той передаче.
- Фронт: тогл «Основная цепь | Все пути (N)» с lazy-фетчем.

### UX-фиксы по фидбеку
- Вердикты: self-бейдж скрыт; «на параллельную передачу» → ссылка с
  именем сборника (RulingDto + relatedHadithId/relatedCollectionNameRu).
- Такхридж: «Муснад Ахмада · №8944» вместо сырых `121-8622 ["8944"]`
  (CrossrefDto → numbers[] + имена сборника; note выпилен).
- Пустые «Оценки учёных» (ручные оценки платформы — НЕ дубль вердиктов)
  и одиночные «Вариации» скрыты вместе с пунктами навигации.
- Панель рави: подпись «كما ورد في الإسناد» — форма имени из текста
  (закрывает путаницу الفاكهي vs الخزاعي — это одно лицо, id 22973).
- HadithDetailResponse + externalId.

### Ре-маппинг (лечение данных)
importNarrators 7 921/7 921 (~1 мин) + importHadiths 33 299/33 300
(1 честный фейл: 594-2472 пустой матн). Resolve: **355 894 crossref-FK
+ 28 549 relation-FK**. Цепь 594-1 верифицирована SQL-ом: позиции 5-7
теперь `حدثنا`. UUID стабильны (идемпотентный upsert).

### Верификация
Playwright headless на 594-1: 9/9 чеков (тогл, version-узлы, скрытые
секции, имена в такхридже, ссылки в вердиктах, «في الإسناد», turuq-граф
с Ибн Хиббаном/Тирмизи). Полный verify + vitest 747/747 + tsc.
Скриншоты /tmp/s58-*.png.

### Следующий шаг
Юзер-гейты остатка: HAR вкладок علل/غريب; AI-ключ для live-перевода.
Параллельные кандидаты: 49.B rating+pagination, 49.D observability.
Перф resolveNarratorRelations на полном корпусе оказался ок (28.5k за
секунды). Следить за payload detail-эндпоинта на хадисах с многими
шархами (M4-caveat Плана 6).

## 2026-06-04/05 - Сессия 57 - alminasa Планы 3-7: маппер, выпил legacy, админка, Explorer, AI-перевод

Полностью автономный марафон `/autopilot` (MAX-режим): закрыт ВЕСЬ
оставшийся alminasa-трек ADR-060 — Планы 3→7 последовательно, каждый по
циклу «план-док → критик → исполнение субагентами → тесты → review →
фиксы». ~35 коммитов. Для каждого плана критик-ревью ДО исполнения
ловило реальные дефекты дизайна (off-by-one семантики формул,
lock-on-failure launcher'а, mappedCount без source-фильтра и др.).

### Сделано

**План 3 — маппер staging→hd_*** (`docs/plans/2026-06-04-alminasa-mapper.md`):
- `AlminasaIsnadParser` — детерминированный парс rawy-тегов из
  `full_text_ar`; семантика формул «сегмент ПОСЛЕ тега» (= собственная
  речь рави о получении; критик C1 поймал off-by-one исходного дизайна),
  реверс → position 0 = сподвижник, формулы без сдвигов; эталонный
  вектор 146-1 залочен IT + round-trip `SanadGraphService.buildGraph`.
- `AlminasaNarratorMapper` (upsert по external_id, grade→enum c verbatim
  в grade_text, relations из top_students/scholars) +
  `AlminasaHadithMapper` (транзакционный порядок resolve→delete-сателлиты→
  insert; рулинги = union embedded+index per-док с дедупом; статус
  сахихайн→CANONICAL; пре-чек коллизии (collection, primary_number));
  `AlminasaImportService` (per-док tx, failure isolation, resolve
  crossref-FK SQL-ом + relations в Java по нормализованным именам),
  dry-run с setRollbackOnly. 51 тест; review 0C/1I (закрыт).
**План 4 — выпил legacy** (`…-legacy-removal.md`): `hadith/sunnah/**`
  (41 файл) + `hadith/isnad/**` (AI-иснад, ADR-059 → SUPERSEDED),
  миграция 74 DROP `sn_staging_*`, AdminSunnahPage + 82 i18n-ключа,
  `/admin/sunnah/*` из api-contract, regen types (-389 строк); roadmap
  49.C split + backlog-чистка. `buildGraph`/`SanadGraph` живы.
**План 5 — AdminHadithImportPage** (`…-admin-import-page.md`): 5 admin-
  endpoints (catalog: mappedCount ТОЛЬКО по external_source='alminasa';
  import/status с live processedSoFar; async launcher — CAS IDLE→RUNNING
  до submit + finally-гарантия выхода (критик C2: иначе transient-фейл
  навечно лочит 409), прямой executor.execute вместо @Async
  (self-invocation мимо прокси — вскрыто latch-тестом); dry-run
  404/422). Страница: краулер start/pause + каталог 12 сборников +
  статус импорта + dry-run превью цепи; импорт-кнопки disabled при
  crawl RUNNING. 14 IT + 7 MSW.
**План 6 — Hadith Explorer на alminasa** (`…-frontend-explorer.md`):
  detail +8 полей / narrator +6 / sanad-graph +externalId;
  `parseIsnadHtml` (без dangerouslySetInnerHTML) + кликабельный иснад
  (lifted graph-фетч, единая панель, клик = данные из графа без
  доп. фетча); секции вердиктов (provenance «на параллельную передачу»),
  шарха (collapsible), такхриджа (resolved→Link), изданий; NarratorPanel:
  tabaqa??generation, gradeText??reliabilityComment.
**План 7 — AI-перевод** (`…-ai-translation.md`): POST
  /matns/{id}/translate (кэш text_ru/text_en + cached-флаг, force=ADMIN,
  isEnabled→503, сервис БЕЗ @Transactional — LLM 5-15с вне tx, два
  UPDATE по lang, guard пустого ответа→502); кнопки RU/EN у hero-матна
  и вариаций (без дубля для primary). 10 IT со стабом LlmClient.

### Live-верификация (playwright headless + дев-краул в рамках гейта)
Дев-краул 1 страницы (100 хадисов / 315 рави / шархи / рулинги, pause
на границе) → UI-прогон админки: каталог 12 сборников со staged-counts,
импорт рави → маппинг всех → **вскрыт live-баг: kunya/laqab живых доков
> varchar(120)** (2 хадиса падали) → фикс truncate + re-run → 100/100,
26 crossref-FK + 916 relation-FK срезолвлено. Dry-run 146-1 через UI.
Explorer: detail 146-1 — тип مرفوع, кликабельный иснад (клик открыл
панель الحميدي), 8 вердиктов с provenance-подписями, 3 шарха, такхридж
20 мест, 2 издания. Скриншоты /tmp/p5-*.png, /tmp/p6-*.png.
**Очистка по гейту:** alminasa-строки hd_* удалены, staging TRUNCATE,
чекпоинт IDLE (статус-smoke подтверждён).

### Верификация (финал)
Backend `./mvnw verify` **1318/1318 BUILD SUCCESS** (на границе П4 был
flake HttpClientPdfFetcherCircuitBreakerIT — изолированный перегон
зелёный, документированный TC-cache flake). Frontend **vitest 737/737**
(116 файлов), tsc + eslint чисто, build success. types.ts регенерирован
3 раза (только аддитивные/удаления по планам).

### Code-review (независимые, по workflow)
П3: 0 Critical / 1 Important (bookIdFilter семантика — закрыт early-break).
П4: lite-verify, 2 гэпа (stale ADR-индекс, i18n alminasa-карточки) — закрыты.
П5-7 (объединённый): **0 Critical / 0 Important / 5 Minor** — 4 закрыты
(@NotNull lang + IT, дубль перевод-контролов, guard пустого LLM-ответа,
index-key комментарий), 1 принят (unmount-guard, React 19 безвреден).

### Решения/доки
ADR не потребовались (всё в рамках ADR-058/060); ADR-059 помечен
SUPERSEDED. Gotchas +2 (setRollbackOnly невидим из @Transactional-теста;
не-NFC combining-знаки в арабских литералах). api-contract: +6 endpoints
(Import Admin API, translate), -секция /admin/sunnah. Architecture:
маппинг-пайплайн.

### Проблемы/known
- kunya/laqab >120 в live-данных — закрыт truncate'ом (4be4b7c), полный
  текст остаётся в staging raw.
- `.omc/state` мусор от хуков субагентов появлялся внутри backend/src —
  вычищен, в git не попадал.
- Релейшны передатчиков: FK-резолв best-effort (короткие формы имён),
  916 на 100 хадисов — лучше ожиданий, но полнота не гарантирована.

### ЖДЁТ АБДУЛУ (юзер-гейты, без них трек дальше не двигается)
1. **Письмо alminasa (مركز تميز)** → разрешение на массовый обход
   12 сборников (backlog «Связаться с alminasa.ai»). После ответа:
   `/admin/hadith-import` → Старт краулера → (часы, resumable) →
   «Импорт рави» → «Маппинг всех сборников».
2. **Свежий HAR с кликами по вкладкам علل (иляль) и غريب (гариб)** на
   alminasa.ai — без него вкладки НЕ реализованы (контракты ES-индексов
   `hadith-commentary-12`/`ambiguous-12` неизвестны; ничего не выдумано).
3. **AI-ключ для live-перевода** (`--ai.provider=... --ai.api-key=...`,
   за прокси `--ai.http.proxy=...`): проверить POST translate живьём
   (сейчас только стаб-тесты; без ключа кнопки дают 503-тост).
4. **Ручные UI-проверки** (накопленные + новые):
   - НОВОЕ `/admin/hadith-import`: дев-краул Старт→Пауза (гейт: 1-2
     страницы!), импорт рави, маппинг сборника, прогресс-бары, dry-run
     `146-1` (после краула) — RTL-выравнивание, tooltip на disabled
     кнопках при RUNNING-краулере.
   - НОВОЕ `/hadith` Explorer (нужны данные — после дев-краула+импорта):
     detail хадиса — клик по рави В ТЕКСТЕ иснада И в React Flow графе
     (обе точки должны открывать ОДНУ панель; RF-клик headless не
     проверяется надёжно), collapsible шарха, такхридж-переходы,
     кнопки перевода (503-тост без ключа).
   - Накопленное с С55 (playwright env-blocked тогда): archive.org
     FILE_ONLY ридер, content_kind кнопки, bbox-подсветка,
     DeepSeek-метаданные.
   - Тест-логин для проверок: `pw-admin-57@test.local` /
     `Pw-Admin-57!pass` (ADMIN, создан этой сессией).

### Инфра-стейт (на конец Сессии 57)
Docker postgres+minio up; backend :9090 + JDWP :5005 запущен (код
Планов 5-7); frontend :5173 запущен; миграции через **74** (74 — DROP
sn_staging_*); staging/hd_* alminasa-данные ОЧИЩЕНЫ, чекпоинт IDLE;
sunnah-mysql больше не нужен совсем. Smoke:
`curl -s -H "X-User-Id: 00000000-0000-0000-0000-000000000001"
http://localhost:9090/api/v1/admin/alminasa/import/status` → IDLE.

### Следующий шаг
Трек alminasa кодово ЗАВЕРШЁН — дальше только юзер-гейты (блок «ЖДЁТ
АБДУЛУ» выше). Параллельные кандидаты: 49.B rating+pagination или 49.D
observability (спеки готовы); либо после ответа alminasa — массовый
обход + наблюдение за перф (resolveNarratorRelations OFFSET-caveat,
батчинг маппера если живой прогон 82k покажет боль).

## 2026-06-03/04 - Сессия 56 - alminasa единственный источник: спека + Планы 1-2

Реализация разворота ADR-060 (alminasa.ai = единственный источник хадисов).
Сессия шла в два захода: первый заход (03.06) закрыл спеку + План 1 и **упал**
(API-ошибки клиента) ровно на границе после doc-коммита Плана 1 — крах ничего
не потерял (атомарные коммиты). Второй заход (04.06) — План 2 целиком,
subagent-driven (имплементер + spec-ревью + quality-ревью на каждую группу задач).

### Сделано

**Спека + План 1 (заход 1, 03.06):** дизайн-спека
`docs/specs/2026-06-03-alminasa-hadith-source-design.md` (HAR-анализ,
архитектура краулер→staging→map, модель данных, фазовый план 1-7); план 1
`2026-06-03-alminasa-hadith-ingestion-schema.md`; **миграции 70-71** (alminasa-колонки
hd_hadiths/hd_narrators: external_id/type/chapter/full_text/tabaqa/grade_text;
5 новых таблиц hd_hadith_editions/hd_rulings/hd_explanations/hd_hadith_crossrefs/
hd_narrator_relations); расширенные Hadith/Narrator records + репозитории с
findByExternalId; 5 доменных records + репозитории; round-trip IT
(AlminasaSchemaRepositoryIT); **ADR-060** + glossary/architecture. 13 коммитов.

**План 2 (заход 2, 04.06):** `2026-06-04-alminasa-crawler-staging.md` — ES-клиент +
resumable краулер → staging. 15 коммитов:
- **HAR-разбор субагентом** → уточнения спеки: путь индекса `es-prod-euw1-{index}-read`;
  сайт пагинирует from+size (упёрся бы в ES-лимит 10k) → у нас `search_after` по
  `hadith_serial_id`; `narrators[].id` — строка; у narrator-дока id только в ES `_id`;
  вкладки علل/غريب — отдельные индексы, контракты не сняты (отложено до Плана 6).
  Фикстуры из HAR → `backend/src/test/resources/alminasa/`.
- **Миграция 72**: `am_staging_hadith/narrator/explanation/ruling` (raw jsonb NOT NULL
  forward-compat, природные PK, UNIQUE(hadith_serial_id) сторожит search_after) +
  `am_crawl_checkpoint` (generic по index_name, status CHECK).
- **AlminasaRows** (JsonNode→Row, fail-fast на битых доках, trim) + 4 staging-DAO +
  чекпоинт-DAO с resume-семантикой (upsertRunning(reset), advance=АБСОЛЮТНЫЙ счётчик).
- **AlminasaEsClient**: 4 запроса (страница хадисов search_after + terms-батчи
  narrators/explanations/rulings), Origin/Referer, опц. корп-прокси (Authenticator —
  безопасен, серверного Authorization нет), @Retry(alminasaApi) + transient-предикат
  (0/429/5xx; interrupt=-1 и 4xx НЕ ретраим), empty-collection short-circuit,
  warn при переполнении dependent-fetch-size.
- **AlminasaCrawlService**: «hadith-first» resumable цикл — страница хадисов →
  зависимые по id страницы (только проверенные HAR'ом формы запросов, нет
  сортировочных требований к зависимым индексам); чекпоинт на границе КАЖДОЙ
  страницы; pause на границе; stale-takeover (10 мин); single-thread executor
  (queue=0+AbortPolicy = реальный guard сериализации); БЕЗ @Transactional на цикле.
- **Admin REST** `/api/v1/admin/alminasa/crawl/{start,pause,status}` (202/200/200,
  409 alminasa-crawl-already-running, ADMIN-only как sunnah); race
  «stale-takeover при живом воркере» → TaskRejectedException → честный 409
  (чекпоинт не трогаем — живой воркер сам продолжает heartbeat).
- **Доки**: api-contract (3 endpoint'а + DTO), gotchas (ES-прокси: контракт и
  ловушки), architecture (pipeline краулера), regen types.ts (только добавления).
- **31 новый тест**: AlminasaRowsTest(5) + AmStagingDaoIT(4) + predicate(3) +
  StubIT(7) + RetryIT(1, лок regрессии self-invocation Сессии 55) +
  CrawlServiceIT(5: full/resume/pause/conflict/stale) + ControllerIT(6, вкл.
  детерминированный тест race живого воркера через latch).

### Решения
- ADR-060 (заход 1). План 2: `_msearch` не нужен (одиночные `_search` c terms);
  alminasa.enabled default-on (публичный прокси, секретов нет); чекпоинт generic
  без collection-измерения (глобальный hadith-first обход, прогресс по сборникам
  План 5 возьмёт из staging group by book_id); fetched_count = абсолютный count(*)
  (replay страницы не раздувает прогресс).
- **Продукт (Абдула, из консультации с веб-Клодом):** до массового обхода написать
  в مركز تميز (alminasa) про официальный доступ — пункт в backlog; полный обход
  12 сборников ГЕЙТИТСЯ этим пунктом. Memory `feedback_hadith_source_strategy`
  дополнена.

### Code-review (per-группа + финальный)
Каждая группа: spec-ревью + quality-ревью. 3 фикс-итерации по ревью: (1) предикат
без негативных тестов + empty-collection guards; (2) fetched_count дрейф при replay +
лживый javadoc 409 + total_hits каждую страницу; (3) race stale-takeover→500 →
TaskRejectedException→409 + детерминированный IT. Финальный ревью всего диапазона:
**0 Critical, 0 Important, Ready to merge**; единственный named residual —
queryability `terms.id` на narrators-индексе непроверяема стабом (live-smoke,
гейтится backlog-пунктом).

### Верификация (финал)
Backend `./mvnw verify` → **BUILD SUCCESS, 1324/1324**. Frontend **vitest 720/720**
(114 файлов), tsc clean, types.ts — только добавления. Live-smoke краулера НЕ гонялся
(см. backlog-гейт); admin-endpoints проверены IT + ручная проверка ниже.

### Хвост сессии — live-инцидент первого dev-краулинга + хотфикс (10b26b2)
Абдула прогнал dev-краул → `DuplicateKeyException` на первой странице. Systematic
debugging: **`hadith_serial_id` — номер ВНУТРИ сборника, не глобальный** (живой зонд:
12 доков с serial=1, по одному на сборник; заодно получен полный book-id map всех 12
сборников для Плана 3: 19=Муватта, 121=Ахмад, 137=Дарими, 146=Бухари, 158=Муслим,
173=Ибн Маджа, 184=Абу Дауд, 195=Тирмизи, 319=Насаи, 345=Ибн Хузайма, 454=Ибн Хиббан,
594=Мустадрак). UNIQUE-индекс миграции 72 сработал задуманной канарейкой (упал ДО
записи строк). Хотфикс TDD: миграция 73 (UNIQUE снят + checkpoint.last_sort_id),
**составной search_after [serial, hadith_id]** (живым зондом подтверждён), фикстура
из реальных live-доков. **Live-верификация:** 300 хадисов / 719 рави / 2069 рулингов
застейджены, serial=1..3 → по 12 сборников каждый, pause на границе, `terms.id` к
narrators подтверждён (закрыт residual финального ревью). Dev-данные очищены,
чекпоинт IDLE. gotcha + api-contract (lastSortId) + types regen — в том же коммите.

### Проблемы/known
- ~~`terms.id` к narrators-12 — live-предположение~~ **подтверждён живьём** (см. хвост).
- Вкладки علل/غريب: контракты НЕ в HAR — перед Планом 6 снять свежий HAR с кликами.
- Полный обход 12 сборников — ТОЛЬКО после ответа alminasa (backlog).

### Следующий шаг
**План 3 — маппер staging→hd_*** (процесс: спека → план в `docs/plans/`,
оркестрация через OMC `/ralplan`): детерминированный парс
иснада из `full_text_ar` по `<a class=rawy id=N>` (порядок = narrators[], реверс в
position 0 = Пророк ﷺ), upsert по external_id в hd_* (Plan 1 колонки/таблицы готовы),
cross-refs из raw_narrations, рулинги/шархи/relations, book-id→slug map (146=البخاري,
остальные из book_name при краулинге), unit-тесты на реальном hadith-HTML из фикстур.
После него План 4 (выпил sunnah ETL + AI-иснад) → 5 (админка) → 6-7 (фронт, AI-перевод).

### Перенесено из SESSION_START_PROMPT (файл выпилен при переходе на OMC, 2026-06-04)

**Дополнительно в очереди (после Планов):** 🖐️ ручные проверки UI (накоплено
с Сессии 55, playwright env-blocked): archive.org FILE_ONLY ридер, content_kind
кнопки, bbox-подсветка, DeepSeek-метаданные; плюс admin alminasa endpoints
(smoke ниже).

**Инфра-стейт (на конец Сессии 56):** Docker (postgres+minio) up; backend :9090
+ JDWP :5005 (команда — CLAUDE.md); frontend :5173; psql роль `argmap`;
миграции через **73** (73 — составной курсор, хвост-хотфикс). sunnah-mysql
`:3307` нужен только для sunnah-legacy (до Плана 4). AI-фичи: `--ai.provider=...` + ключ аргументом (НЕ в репо), за
корп-прокси `--ai.http.proxy=...`. Admin для curl:
`00000000-0000-0000-0000-000000000001`. HAR'ы в gitignore; полные сэмплы
ответов alminasa — `/tmp/alminasa-fixtures/` (если /tmp пережил ребут) либо
пере-извлечь из HAR в корне. Smoke alminasa:
`curl -s -H "X-User-Id: 00000000-0000-0000-0000-000000000001" http://localhost:9090/api/v1/admin/alminasa/crawl/status`
→ `{"status":"IDLE",...}`. Дев-данные: fmhji (FILE_ONLY, 4 файла); bukhari
hd_* 1 хадис (sunnah-legacy).

**Известные мелочи (не блокеры):** jsdom+node24 не парсит multipart FormData
(mock fetch в FileUploadModal.test); node24+undici AbortSignal workaround в
test-setup.ts (gotchas); playwright WSL2 не грузит Google Fonts через
corp-proxy (шрифты проверять в реальном браузере).

## 2026-06-02/03 - Сессия 55 - крупный автономный overhaul (7 фаз + code-review)

Запрос Абдулы: 10 пунктов + скриншоты `img*.png` + HAR (archive.org/alminasa).
Полностью автономный марафон. Спека `docs/specs/2026-06-02-session-55-overhaul.md`.
Карта кода — multi-agent workflow (6 агентов). ~14 коммитов.

### Сделано (7 фаз)
1. **Выпил Tesseract OCR** (ADR-057, migration 68 drop `lib_pages.ocr_*`). Удалены
   OcrService/Config/Controller/Status/JobResponse + Tess4j + frontend-OCR. `@EnableAsync`
   перенесён OcrConfig→AiEditConfig. Сохранён AiEditService + image-scan upload (субстрат
   для будущего AI-распознавания).
2. **Swappable LLM** (ADR-058): пакет `ai/` — `LlmClient` интерфейс + AnthropicLlmClient/
   OpenAiCompatibleLlmClient/DeepSeekLlmClient через `@ConditionalOnProperty(ai.provider)`,
   retry-инстанс `llmApi`. `BookMetadataExtractionService` (LLM→био-поля, graceful fallback).
3. **content_kind** (migration 69) — НОВАЯ ось доступности TEXT_ONLY/TEXT_AND_FILE/
   FILE_ONLY, ортогональна `book_type` (жанр). Импортёры выставляют через updateContentKind.
   Frontend: ридер по типу (FILE_ONLY→PDF сразу, TEXT_ONLY→без PDF).
4. **archive.org overhaul** (ADR-056 amend): FILE_ONLY, drop `_text` OCR-варианты (это и
   была абракадабра), HTML-стрип описания, AI-метаданные (regex fallback), лок формы после
   импорта. Закрыло баги обложки-как-тома, OCR-интерливинга, сырого HTML, дефолта-на-cover.
5. **Reader**: bbox-подсветка при PDF-цитате (display; creation=roadmap 25.f), 0-page guard.
6. **Hadith**: `availableHadith` честный счётчик (дамп = bukhari-only сэмпл!), независимый
   скролл превью-панели, alminasa-карточка переформулирована.
7. **AI-иснад** (ADR-059): `IsnadExtractionService` (LLM парсит цепочку из матна) +
   in-memory `SanadGraphResponse` (reuse Hadith Explorer viz) + `POST /admin/sunnah/
   extract-isnad` + кнопка «Извлечь иснад (ИИ)» в превью.
8. **Иснад persistence-on-import** (Фаза 9, ADR-059 amend): `IsnadPersistenceService` —
   на импорте (single default-on, bulk `?extractIsnad=true`) извлечённая цепочка пишется
   в hd_sanads/hd_narrators/hd_sanad_narrators (дедуп нарраторов по нормализованному
   имени — MVP, возможен false-merge гомонимов; idempotent delete-recreate). Теперь
   `/hadith` explorer показывает иснад импортированных хадисов (не только preview). +39 тестов.

### Бонус — Tier-3 фиксы из бэклога (Фазы 10-12, после основного scope)
- **Фаза 10 (backend):** ShamelaChapterMapper cycle-detect+log (главы не пропадают молча),
  ShamelaBibliographyParser word-count guard (страна не вклеивается в издателя),
  HadithController.getDetail O(sanads×links)→groupingBy.
- **Фаза 11 (frontend):** QuestionDetailPage delete-gating (author+ADMIN), AnswersSection
  per-answer busyIds, QuestionListPage proper load-error, AdminUsersPage locale-aware дата.
- **Фаза 12 (backend):** AI-edit liveness-escape (stale PROCESSING re-claim, property
  `ai.edit.processing-timeout-minutes`), HttpClientPdfFetcher negative Content-Length guard.
- Итого ~9 Tier-3 пунктов бэклога закрыты. backend BUILD SUCCESS, frontend 716 тестов.

### Хвост сессии — DeepSeek-прокси + сворачивание хадисов (по запросу Абдулы)
- **LLM за корп-прокси** (`ai.http.proxy`): WSL2 blackhole-DNS api.deepseek.com→127.0.0.1 +
  обязательный authenticated HTTPS_PROXY. Диагностика через standalone-репро: builder
  `Authenticator` (как shamela) проходит прокси-407, НО JDK вырезает серверный
  `Authorization: Bearer` → 401. Фикс: превентивный `Proxy-Authorization: Basic` +
  `jdk.httpclient.allowRestrictedHeaders` (static-блок), прокси ТОЛЬКО на LLM-HttpClient.
  `ai/LlmHttpClients`, gotcha. **DeepSeek заработал живьём** (метаданные archive.org ИИ —
  лучше regex: split издатель/место). Ключ передан аргументом, НЕ в репо.
- **UI-фиксы:** убраны OCR-упоминания в admin-карточках (archive.org «+OCR»→«PDF»,
  PDF-upload без обещания авто-извлечения); CitationPicker не виснет на FILE_ONLY
  (честное «только PDF» вместо вечного спиннера). Отложено (косметика): ровные чипы,
  z-index FloatingActionBar/модалка, header overlap; AI-vision метаданных по front-matter PDF.
- **Хадисы свёрнуты:** субагент enrich+grounding успел частично отредактировать
  (extract-isnad endpoint, grounding) → **откатил** (`git checkout`), чтобы Абдула
  переделал с чистой базы. РАЗВОРОТ стратегии (memory `feedback_hadith_source_strategy`):
  **alminasa = единственный источник** (Сессия 56), AI-isnad-from-matn (ADR-059) → legacy.

### Решения
- ADR-057 (OCR removed), ADR-058 (swappable LLM), ADR-059 (AI-иснад), ADR-056 amendment
  (archive.org FILE_ONLY). content_kind vs book_type — две ортогональные оси.
- **Стратегия источников хадисов**: sunnah дамп primary, иснад AI из матна (без внешней
  зависимости), alminasa.ai = проприетарный ES → НЕ скрейпим, оставлен как будущее
  обогащение риджаль-данными.

### Code-review (multi-agent, 5 измерений + adversarial verify)
14 raw → 11 confirmed, **0 Critical, 1 Important, 10 Minor**. Important + 9 Minor закрыты:
- **Important:** `@Retry` перестал применяться к AI-edit (single-arg `complete` = default-метод
  без @Retry → self-invocation мимо proxy). Фикс: AiEditService зовёт two-arg `complete(null,
  prompt)`, default-метод удалён, +`LlmClientRetryIT` (503/503/200 через proxy → 3 запроса).
- **Minor:** IsnadExtractionRequest.number Integer→String («1a»), bbox wrong-volume guard,
  dead i18n/union, 5 doc-фиксов (ADR-058/057/041 статусы, api-contract OCR, spec divergences).
- **Отложено:** migration 69 jsonb guard (нельзя править applied changeset; backlog).

### Верификация (финал)
Backend `./mvnw verify` → **BUILD SUCCESS** (см. progress). Frontend: tsc clean, eslint
**0 проблем** (убран pre-existing unused-disable), build ✓, **vitest 708/708** (109 файлов).
Live-smoke: archive.org re-import fmhji → FILE_ONLY, 4 файла (no `_text`), описание plain-text;
extract-isnad → `{llmEnabled:false}` (graceful без ключа).

### Проблемы/known
- AI-фичи (метаданные книг, иснад) работают только с реальным LLM-ключом (`ai.provider`
  + `*_API_KEY` env); без ключа graceful (regex fallback / `llmEnabled:false`).
- Дамп sunnah = только bukhari (100 строк); полный корпус — контент-ops.
- **Весь UI требует ручной проверки** (playwright env-blocked, нет Chromium).

### Следующий шаг
1. **🔴 Переделка хадисов под alminasa** (Сессия 56) — alminasa = единственный источник
   (memory `feedback_hadith_source_strategy`); дизайн-спека + alminasa-парсер (staging→map).
   AI-isnad-from-matn (ADR-059) становится legacy.
2. **🖐️ Ручная проверка UI** overhaul'а + DeepSeek-метаданные (ключ поднят).
3. **bbox-citation CREATION** (roadmap 25.f) — архитектурный блокер (pdfFileId↔library_files),
   нужно решение по модели. Display готов.
4. **Косметика:** ровные чипы admin, z-index модалки, header overlap; AI-vision метаданных
   по front-matter PDF (Абдула просил — титул/выходные данные несут ISBN/тома/издателя).

## 2026-06-02 - Сессия 54 (батч 5) - archive.org PDF-импорт MVP (новый инструмент)

Запрос Абдулы: админ-инструмент импорта книг из archive.org по URL (parser +
preview + gap-aware enrichment + выбор обложки/томов). Brainstorm→спека
(`docs/specs/2026-06-02-archive-org-pdf-import-design.md`, одобрена)
→ реализация 2 субагентами (backend+frontend). ADR-056, migration 67. ~3 коммита.

### Сделано (MVP)
- **Backend** (ADR-056): `ArchiveOrgClient` (metadata API, extractIdentifier, CB) +
  `ArchiveOrgMetadataMapper` (provenance archive_org/missing + авто-группировка
  cover/volumes original+OCR, устойчиво к вариативности) + `ArchiveOrgImportService`
  (preview no-write + import: lib_books + pdf_links **dual-variant object-form** +
  cover_url + академ.поля + lazy PDF + test-mode N страниц + идемпотентность по
  metadata.archive_org_id) + `ArchiveOrgAdminController` (ADMIN-only). migration 67
  cover_url. PdfLinksSourceProvider backward-compat (legacy string + object form).
  +45 IT, реальные фикстуры.
- **Frontend**: `AdminArchiveOrgPage` (/admin/archive-org) URL→preview с gap-бейджами
  (зелёный из источника / жёлтый «нет, заполни») + raw arabic description + список
  томов (original/OCR/«только скан») + cover-picker + test-mode тумблер → импорт.
  Dashboard-карточка. +5 тестов (27 pass).
- **Live-smoke прошёл**: `/admin/archive-org/preview?url=...fmhji` → реальные
  метаданные, title+language=archive_org, остальное=missing (как задумано).

### Следующий шаг (итерации archive.org)
**СДЕЛАНО (батч 6):** ✅ обложки — `coverUrl` в Book/BookResponse/Summary/Detail +
рендер `<img>` на карточке (fallback letter-avatar при null/404) + thumbnail в
reader-шапке; ✅ парсинг arabic `description` (`ArchiveOrgDescriptionParser`) —
author/publisher/год(hijri+greg)/тома/издание из текста → gap-поля стали
`archive_org` (fmhji парсит всё). migration 67.
**ОСТАЁТСЯ (итерации, в тестовой эксплуатации):** полное фоновое извлечение всех
томов (+Tesseract scan-only — сейчас sync за `extractText`/`testModePages`);
volume-dropdown в reader; eager-download UI; relabel/reassign томов в preview
(нужен `ImportRequest.fileMapping` — backend); place/muhaqqiq split из description;
provenance-enrichment как общий паттерн для shamela/sunnah/alminasa. Детали — спека §10.

## 2026-06-02 - Сессия 54 (батч 4) - «го дальше»: hd_collections UI + shamela guard + review + зелёный CI

Продолжение по бэклогу после батча 3. ~7 коммитов.

### Сделано
1. **hd_collections UI** (мост #3 виден end-to-end): книга `HADITH_COLLECTION` в
   библиотеке → бейдж «Сборник хадисов» + клик резолвит коллекцию (`by-book`) →
   `/hadith?collectionId=` (реальный контент, обходит «тонкую книгу»).
   HadithListPage читает `?collectionId=`. **Проверено живьём.**
2. **shamela-admin ADMIN-guard**: все 7 endpoints ADMIN-only (mirror Sunnah).
   **Живьём: admin 200 / non-admin 403.** Security-гэп закрыт.
3. **Multi-agent code review батчей 2-4** (Workflow, 19 агентов, 6 измерений +
   adversarial verify): 13 raw → **8 confirmed, 0 Critical**. Закрыты: Important
   (migration 65 rollback падал по FK при удалении system-user-owner lib_books →
   DELETE lib_books первым; md5sum обнулён для recompute) + 7 Minor (system-user
   ON CONFLICT→WHERE NOT EXISTS, ShamelaBookDao javadoc, vote-service javadoc
   422→400, VoteWidget мёртвый eslint-disable, BookListPage aria-label с title,
   4 orphaned dict-ключа).
4. **Регрессия TypeChip-теста починена**: chip-padding фикс (батч 3) сменил
   h-5/h-6 на padding-based, но TypeChip.test ассертил старое (проскочил т.к.
   гонялся scope'ом src/apps/argument-map, а тест в shared/). Обновлены ассерты.
5. **d3-drag jsdom-флак убран → ПОЛНОСТЬЮ ЗЕЛЁНЫЙ `npm test`**: bulkActions «2
   failed/10 errors» в full-suite (корень: user-event MouseEvent view=null →
   React-Flow d3-drag nodrag(null)). Фикс: мок d3-drag в test-setup + inline
   @xyflow/* в vite.config. Full suite: 105 файлов / 665 тестов / **0 failed**.

### Верификация (КУМУЛЯТИВНО, финал)
- Backend `./mvnw verify` → **BUILD SUCCESS** (полный сьют).
- Frontend `npx vitest run` → **665 pass / 0 failed / 0 errors** (стабильно 4×) +
  build ✓ + tsc ✓ + lint 0 err. **CI полностью зелёный впервые за сессию.**

### Следующий шаг
Все явные запросы Абдулы (3 батча) + бэклог (answer_votes, hd_collections backend+UI,
shamela guard) + 6 ручных багов + 2 код-ревью (batch1 + batches2-4, 0 Critical) +
d3-флак + **14 Tier-3 пунктов бэклога** (security: login timing/disabled-account/
decompression-bomb; correctness: acceptAnswer-CLOSED/body-clear/updateQuestion;
concurrency: OCR atomic-claim/authority-race migration 66+ADR-055/AnthropicClient
transient-retry; UX: ContextMenu clamp/Toaster assertive/useViewTracking/sort) —
**ЗАКРЫТЫ**. CI полностью зелёный (backend BUILD SUCCESS, frontend 678/0/0).

Остаётся НИЗКОПРИОРИТЕТНОЕ Tier-3 (shamela chapter parent-cycle, bibliography
dash-split, getDetail O(sanads×links) perf, OcrService NULL→FAILED — нужен новый
статус+миграция+фронт) + ОТЛОЖЕННОЕ Абдулой: IsnadExtraction (AI, контент); полный
in-place рендер hadith-сборника как книги (редирект достаточен); shamela
`category.sqlite` sync (живой shamela.ws). **Наибольшая ценность сейчас —
ВИЗУАЛЬНАЯ проверка руками** (playwright env-blocked, нет Chromium): весь
UX-overhaul + content-tooling. **БД пуста — наполнять через /admin tools.**

## 2026-06-02 - Сессия 54 (батч 3) - баги из ручного тестирования Абдулы (6 фиксов)

Абдула прогнал руками → список багов со скринами. Все 6 закрыты (~5 коммитов).

1. **Обновление статусов узлов не работало** («Не удалось обновить ни один узел»):
   PATCH /nodes/{id} не принимал `status` (только content/pos/lang) → 400. Добавлен
   `status` в UpdateNodeRequest + `NodeService.updateStatus` (assertCanWrite, валидация
   enum, audit). Персистит для узлов без влияющих рёбер (StatusCalc MVP пересчитывает
   только при изменении рёбер). **Проверено живьём: 200 + узел STANDING.**
2. **Импорт хадисов «не настроен»** (503): backend терял `SUNNAH_DUMP_*` env при
   рестарте (fork spring-boot:run не наследовал env). Фикс — запускать с
   **`-Dspring-boot.run.arguments="--sunnah.dump.enabled=true --sunnah.dump.url=... ..."`**
   (детерминированно, не зависит от env-наследования). **Проверено: /collections +
   /preview → 200.**
3. **PDF shamela не грузился** (`pdf_links.root отсутствует`): code gap — shamela-native
   `pdf_links` без `root` (относит. пути) не резолвились. Фикс: резолв против shamela
   CDN. +IT.
4. **Shamela sync `category.sqlite отсутствует`**: environmental/upstream (структура
   архива/сеть). Сделан рекурсивный fallback + actionable ошибка + gotcha (не хак).
5. **Миникарта**: синий прямоугольник вьюпорта при zoom-out наезжал на header/footer
   → overflow-hidden на области карты.
6. **Ребро RESPONDS_TO**: иконка ↩ (влево, против хода) путала направление → ↳ (вперёд).
   **+ чип типа узла**: текст касался границ → padding + зазор до контента.

**Проблемы/known:** shamela-admin endpoints без ADMIN-guard (security-backlog, см. батч 2);
shamela sync category.sqlite зависит от живого shamela.ws (env). bulkActions d3-шум.

**Инфра ВАЖНО:** backend запускать с `-Dspring-boot.run.arguments="--sunnah.dump.*"`
(НЕ env — fork их теряет). См. SESSION_START «Инфра». DB чистая, сидер opt-in.

## 2026-06-02 - Сессия 54 (батч 2) - баг-фиксы + чистка мусора + answer_votes + DB cleanup

**Продолжение Сессии 54** (тот же автономный заход). Абдула дал второй батч:
«продолжай по бэклогу, голоса на ответы и hd_collections» + список багов/чисток.
~13 коммитов поверх 27 из батча 1.

### Сделано

1. **fix vote-баг (КРИТ):** клик по голосу на карточке → разлогин + навигация.
   Корень: VoteWidget внутри React-Router `<Link>` делал только `stopPropagation`
   → onClick Link'а не срабатывал → не вызывался его preventDefault → браузер
   делал НАТИВНУЮ навигацию по `<a href>` = **full page reload** (отсюда И
   переход, И «разлогин»). Fix: `e.preventDefault()` в кнопках/контейнере.
2. **Выпилен весь user-preferences вертикаль** (мусор/dead): backend
   PreferencesController/Service/Repo/domain + 2 IT + **migration 63 DROP
   user_preferences**; frontend preferencesStore + PreferencesEffect +
   UserPreferencesSection. Это убрало: textSize (мёртвый `--text-size-scale`,
   дублировал рабочий UI-scale), arabicFont-3opt (мёртвый `--font-arabic-pref`
   дубль 10-опционного), tashkeel/transliteration/bilingual (junk).
3. **bilingual node mode + tashkeel удалены полностью** (frontend): NodeCard
   всегда рендерит content; Tashkeel tiptap-extension + stripTashkeel util +
   reader/editor кнопки убраны. (NodeResponse.translations оставлен в API —
   фронт игнорит, без regenerate-ripple.)
4. **Фикс title-weight для арабского** (#5): `--title-weight` применяется к
   арабским заголовкам (fixed-weight шрифты → браузер снапит 400/700, bold↔normal
   работает; документировано).
5. **Импорт-страницы показывают контент по умолчанию** (#9/#10): backend
   `GET /admin/shamela/books` (paged, q-опционален) — AdminShamelaPage теперь
   листает весь каталог по умолчанию + пагинация + фильтр, **убрана кнопка
   «импорт из файла»** (PDF только на /admin). AdminSunnahPage — автовыбор
   первого сборника → список хадисов виден сразу (был blank-until-filter).
6. **Голосование за ответы** (#1, бэклог): migration 64 `answer_votes` (зеркало
   question_votes) + AnswerVote стек + POST/DELETE/GET /api/v1/answers/{id}/vote
   + AnswerResponse +voteScore/userVote. Frontend: обобщённый `VoteWidget` на
   карточках ответов.
7. **DB cleanup (#11):** truncate всего user-facing контента + sunnah-staging +
   audit (topics/nodes/qa/lib_books+5915 pages/hd_*/sources/authorities/votes),
   удалены 35 тестовых юзеров + 86 refresh_tokens. **Сохранены:** admin-юзер
   (`00000000-...-0001`), схема, **shamela master-каталог lib_shamela_book=8589**
   (источник импорта). `DevHadithSeeder` → **opt-in** (`dev.seed-hadith=true`,
   по умолчанию выкл) чтобы чистая БД не пере-наполнялась при рестарте.

### Решения

- Весь preferences-вертикаль = мусор/dead → удалён целиком, не латали по полю.
  Memory `feedback_no_junk_settings`.
- DB cleanup: сохранить shamela-каталог (import source, нужен для наполнения) +
  admin + схему; сидер хадисов opt-out.
- vote внутри Link: `preventDefault` обязателен (не только stopPropagation).

### Проблемы / known

- ~~**shamela-admin endpoints без role-check** («на MVP») — pre-existing
  security-гэп (Sunnah-admin наоборот ADMIN-only).~~ **ЗАКРЫТО 2026-06-02:**
  все 7 shamela-admin endpoint теперь ADMIN-only (`requireAdmin()` mirror Sunnah;
  non-admin→403 forbidden-admin-only, anonymous на map-book→401). Consistency
  с Sunnah-admin восстановлена.
- bulkActions d3-drag uncaught-шум (pre-existing, тесты проходят).

### Следующий шаг

**hd_collections ↔ библиотечный «Сборник хадисов» (#2, под-проект #3) — BACKEND
МОСТ СДЕЛАН** (ADR-054, migration 65): `hd_collections.book_id → lib_books` +
ленивый `BookCollectionBridgeService` (создаёт lib_books HADITH_COLLECTION при
импорте, system-user owner 0002) + двусторонние линки (`CollectionResponse.bookId`
+ `GET /hadith/collections/by-book/{bookId}`). 49 IT.
**hd_collections UI — СДЕЛАНО** (батч 4 «го дальше»): обошёл проблему «тонкой
книги» через **редирект** — книга `HADITH_COLLECTION` в библиотеке получает бейдж
«Сборник хадисов» и при клике резолвит коллекцию (`by-book`) → ведёт в
`/hadith?collectionId=` (реальный контент, не пустой BookReader). HadithListPage
читает `?collectionId=` из URL. Мост #3 теперь видим end-to-end.
**ADMIN-guard на shamela-admin — СДЕЛАНО** (батч 4): все 7 endpoints ADMIN-only
(mirror Sunnah), проверено живьём (admin 200 / non-admin 403). Security-гэп закрыт.
**Остаток (опц., backlog):** полный in-place рендеринг hadith-сборника как книги
в BookReader (листать хадисы вместо lib_pages) — сейчас решено редиректом, что
достаточно; визуальная playwright-проверка (env-blocked); IsnadExtraction (AI,
контент, отложено); shamela category.sqlite (зависит от живого shamela.ws).

## 2026-06-02 - Сессия 54 - Крупный предпрод UX-overhaul + content-tooling (8 фаз, 17 коммитов)

**Автономный режим (ultracode).** Абдула дал большой product-брифинг («доведи до
предпрода, подготовь инструменты для ручного наполнения контентом») с 13 болями и
ушёл на часы, доверив все решения. Бриф зафиксирован в спеке
`docs/specs/2026-06-02-preprod-ux-overhaul.md` (источник истины) +
memory `project_session_54_preprod` + 3 feedback-memory. Сделано **широким
параллелизмом субагентов** (backend ∥ frontend на disjoint-доменах; frontend
сериализуется на общем dictionary.ts/master-changelog). Коммиты `1102d27..HEAD`.

### Сделано (по фазам брифа)

- **Фаза 1 — #2.B hadith→node citation (handoff-scope, ЗАКРЫТ).** Backend:
  `HadithRepository.findBySourceIds` (reverse IN-lookup) + nullable nested
  `HadithRef` в `NodeSourceResponse` (hadithId/primaryNumber/collectionName/
  previewMatn/status), enrichment в `NodeSourceController.list`. Frontend:
  `HadithPickerModal` (usePagedSearch over /hadith/hadiths) + 3-я кнопка
  «Прикрепить хадис» в `NodeCitationsSection` + рендер `HadithCite` перед
  FreeformCite. generate-api. +IT (8) +тесты.
- **Фаза 2 — SWR-кэш данных (бриф #2,#3).** Новый `queryCache` (in-memory Map
  по URL) + stale-while-revalidate в `useApiQuery` и `usePagedSearch` (кэширует
  весь накопленный Load-More список под page-0 ключом). Возврат на страницу —
  мгновенно, фоновая ревалидация. Корень жалобы «навигация тормозит». +10 тестов.
  Alt+K (#3): убран `backdrop-blur` с overlay (блюрил весь граф на каждом
  открытии) — мгновенно.
- **Фаза 3a — карточки библиотеки (#9).** Theme-aware палитра обложек
  (--cover-1..5, muted в dark) вместо foreground-токенов (прыгали к ~87% в dark
  → glare). Equal-height (Card.Title clamp + flex). #12: `SourceDetailPanel`
  стартует под header (top-12) — не сливается с шапкой.
- **Фаза 3b — голосование node→topic (#13, ADR в decisions.md).** Backend:
  удалён весь node-vote стек + migration 60 DROP node_votes + vote-поля убраны
  из NodeResponse; добавлен topic-vote стек (migration 61 topic_votes,
  TopicVote/Repo/Service/Controller, POST/DELETE/GET /topics/{id}/vote,
  TopicResponse +voteScore/userVote bulk-load). Frontend: удалён VoteWidget с
  узлов, новый `TopicVoteWidget` на карточках TopicListPage + шапке TopicGraphPage.
- **Фаза 4 — единый ListControls (#7,#10).** shared `ListToolbar`/`FilterChips`/
  `SortSelect`/`SearchInput`/`LoadMoreButton` применены на 4 списках. Topics+Q&A
  мигрированы на usePagedSearch (SWR-кэш + серверный поиск Q&A). Легенда статусов
  хадиса. +21 компонент-тест.
- **Фаза 5 — redesign чтения хадиса (#6).** 4 секции (Текст hero / полноэкранный
  иснад 70vh / Оценки панель / Вариации сворачиваемые) + sticky in-page nav
  (IntersectionObserver). detail на useApiQuery (SWR).
- **Фаза 6 — settings drawer + масштаб + reader fonts (#4,#11).** Settings как
  slide-over (шестерёнка/Alt+,/palette — не уводит со страницы). UI-scale store
  (compact 0.9 / standard 1.0 / comfortable 1.1, **дефолт compact ≈−10%**,
  откат «Стандартный (базовый)» в один клик). ReaderFontControls «Aa» в
  BookReader (шрифт live на тексте). +15 тестов.
- **Фаза 7 — overhaul админки + Sunnah import-preview (#1,#5, ЦЕНТРАЛЬНЫЙ).**
  Backend: 3 ADMIN endpoint'а — browse дампа, **DRY-RUN preview** (rollback-only
  транзакция: реальный код импорта → читаем uncommitted hd_* → setRollbackOnly,
  0 загрязнения), single-import по номеру. Frontend: `AdminDashboardPage`
  (карточки-возможности с «что получится», PDF promoted, alminasa.ai-заглушка),
  `AdminSunnahPage` (browse → preview как-будет-в-нашем-формате → импорт по
  одному), убран бесполезный лог из AdminShamelaPage. +8 IT +5 тестов.
- **Фаза 8 — redesign Q&A (#8).** QuestionDetailPage читаемая центр-колонка +
  статус-бейдж с тултипом + действия в kebab; accepted-answer пришпилен с лентой;
  composer-карточка; карточки списка equal-height. shared statusTokens/
  QuestionStatusBadge/OverflowMenu.
- **Бонус — голосование за вопросы** (завершение #13, после code review):
  migration 62 `question_votes` (зеркало topic_votes) + qa-стек + POST/DELETE/GET
  /questions/{id}/vote (open discussion, без visibility) + QuestionResponse
  +voteScore/userVote. Frontend: TopicVoteWidget обобщён в shared `VoteWidget`
  (entity-agnostic voteUrl, DRY), на карточках QuestionListPage + шапке
  QuestionDetailPage. +28 IT, generate-api.

### Решения

- **ADR (decisions.md): голосование перенесено node→topic** — узлы курируемые,
  голос за тему = сигнал популярности сообщества.
- **Dry-run preview через rollback-only транзакцию** (Фаза 7) — гарантированно
  точный preview (тот же код что импорт), 0 загрязнения БД. Отвергнут pure-mapping
  (риск расхождения с реальным маппером).
- **UI-scale дефолт compact 0.9** — Абдула явно хочет компактнее; откат на 100%
  обязателен и реализован.
- Параллелизм: backend ∥ frontend; frontend строго по одному (общий dictionary.ts
  + master-changelog — узкие места, не параллелятся; worktree не спасает frontend
  т.к. node_modules не копируется).

### Проблемы / known

- ✅ **3 pre-existing fail** `NodeDetailsPanel.test.tsx > секция Опора` —
  **ПОЧИНЕНЫ** (был не timing, а stale-mock: NodeCitationsSection читает
  /sources+/authorities как PagedResponse `.items`, а моки отдавали сырые
  массивы после миграции endpoints на PagedResponse). Fix: helper `paged()`.
  NodeDetailsPanel 25/25. Остаётся только d3-drag uncaught-шум в
  `bulkActions.test.tsx` (React Flow teardown в jsdom — не test-failure, тесты
  файла проходят; pre-existing, отдельная тест-гигиена).
- **Осиротевший `git stash@{0}`** (WIP on master @ e1802b6, BookListPage+
  dictionary) — избыточен (закоммиченный BookListPage уже на ListControls).
  НЕ дропнут (создан не мной). Абдуле: `git stash show -p stash@{0}`, при
  ненадобности `git stash drop`.
- Lint: 1 pre-existing warning (unused eslint-disable в BookReaderPage:174).
- **Визуальная playwright-проверка НЕ прогнана** — структурно всё зелёное
  (backend BUILD SUCCESS, frontend build ✓ / tsc ✓ / 686 тестов pass), но
  редизайны и глобальный масштаб 0.9 нужно глянуть глазами (см. «Что посмотреть»).

### Верификация

- Backend: **`./mvnw verify` → BUILD SUCCESS** (полный сьют, 0 реальных failures).
- Frontend: `npm run build` ✓, `eslint` 0 errors, `tsc` ✓, vitest **686 pass /
  3 pre-existing fail**.
- **Multi-agent code review (Workflow, 27 агентов, 6 измерений + adversarial
  verify): 21 raw → 17 confirmed, 0 Critical.** Закрыты обе Important (TopicVoteWidget
  props-clobber, usePagedSearch loadingMore залипал) + 12 Minor (Sunnah browse
  ORDER BY, queryCache cap, focus-trap, i18n, orphaned keys, доки, +6 authz-IT).
  Backlog: Sunnah offset-overflow
  (repo-wide PageRequest), bulkActions d3-flake.

### Следующий шаг

**Все 8 фаз брифа + Alt+K + #12 закрыты.** Остаток / на будущее:
1. **Визуально проверить руками** все редизайны (особенно глобальный масштаб 0.9
   — если мелко, переключить «Стандартный (базовый)» в настройках) + прогнать
   playwright-smoke когда удобно.
2. ✅ 3 `NodeDetailsPanel секция Опора` падения починены (stale PagedResponse
   моки). Остаётся опц.: d3-drag uncaught-шум в bulkActions.test.tsx (teardown).
3. ✅ Голосование на **вопросах** добавлено (Фаза 3b+, migration 62 question_votes
   + обобщённый shared `VoteWidget`). Осталось опц.: answer_votes (голос за
   отдельный ответ — accept-answer уже сигналит лучший, поэтому низкий приоритет).
4. Опц.: alminasa.ai import-tool (заглушка-карточка в админке готова).
5. Очистить `git stash@{0}` если не нужен.
6. ✅ Code review проведён (multi-agent, 0 Critical, Important+Minor закрыты) —
   см. «Верификация» выше.

## 2026-06-01 - Сессия 53 - Phase 5 ETL sunnah.com шаг 2 (2.a-2.e) + РЕАЛЬНЫЙ ПИЛОТ Бухари

**Автономный режим (ultracode).** Цель из handoff'а Сессии 52: Phase 5 ETL
sunnah.com шаг 2. Закрыт **весь шаг 2 (2.a-2.e)**: конвейер дамп → `sn_staging_*`
→ mapper → `hd_*`, прогнан **против реального дампа** (98 хадисов Бухари
импортированы). ~10 коммитов (`2b24e76..HEAD`), 2 multi-agent review-волны
(0 Critical обе) + de-flake BookRepositoryIT.

### Сделано

**Ключевая декомпозиция.** `SunnahDataSource` как интерфейс ПЕРЕД любым
конкретным reader'ом → staging-схема, DAO, mapper и все 27 тестов
зафиксированы **независимо** от нерешённой развилки «как читать дамп». Поэтому
шаги 2.a-2.c сделаны автономно без новых зависимостей и без сети.

**1. Шаг 2.a — migration 59 `sn_staging_*` (`2b24e76`, ADR-051).** Четыре
staging-таблицы (collection/book/chapter/hadith), зеркало логической модели
sunnah.com (spec §5). Отличия от shamela staging: естественные составные PK
(идемпотентный `ON CONFLICT` upsert), денормализация языков в `*_ar`/`*_en`,
`raw` jsonb forward-compat, без `deleted_at` tombstone (dump = полный snapshot).

**2. Шаг 2.b — `SunnahDataSource` + DTO + DAO (`2b24e76`).** Интерфейс
источника (dump + API → одни staging, единый mapper; гранулярность по
сборнику). 4 staging DTO-records + 4 DAO (упрощённый idiom
`batchUpdate(sql, List<Object[]>)` — без shamela-boilerplate, `imported_at`
через DB-default). `SunnahStagingDaoIT` 12 тестов.

**3. Шаг 2.c — `SunnahToHadithMapper` + `ArabicTextNormalizer` (`141b0b7`).**
Маппер staging → hd_collections/hd_hadiths/hd_matns: текст ar/en, grades
`[{graded_by}]` → metadata `[{scholar,grade}]` (контракт parseGrades),
структура книга/глава → matn.metadata, status=VARIANT (импорт не выдаётся за
канон), идемпотентность по `(collection_id, primary_number)`. Нормализатор
арабского (NFKC + снятие диакритики + сведение алиф/йа/та-марбута/хамза) —
вычисляется, а не вбивается руками. 6 mapper-IT + 9 normalizer-unit.

**4. Multi-agent code review (Workflow, 41 агент, `be8bdf0`).** 5 измерений
→ adversarial verify: 36 raw → 25 confirmed, **0 Critical**, 11 false-positive
отброшено. Все 6 Important + большинство Minor закрыты. Production-багов НЕ
было — гэпы в покрытии (вакуумные тесты enrichment book/chapter), доках,
hardening. Фиксы: parseNumber только ASCII (иначе коллизия idempotency с
арабо-индийскими цифрами), NFKC в normalizer, +18 тестов, architecture-platform/
glossary/ADR-051 docs.

**5. Шаг 2.d — `SunnahDumpReader` + end-to-end импорт (`a7443a4`+`29ba54a`,
ADR-052).** Изучена РЕАЛЬНАЯ схема дампа (`db/00-samplegitdb.sql`, 7 таблиц) —
денормализованная, ≠ логической spec.v1.yml: `HadithTable` консолидирует ar+en+
grade, книга/глава inline; `ChapterData` хранит bookID (не bookNumber); babID
ДРОБНЫЙ (1.1, 22.10). **Из-за дробного babID исправлен migration 59:
`chapter_id` integer → varchar** (иначе 1.0/1.1 схлопнулись бы), ripple в
DTO/DAO/mapper/тесты. `SunnahDumpReader` (JDBC MySQL → DTO, канонизация babID,
JOIN ChapterData→BookData) + `SunnahImportService` (source→staging→mapper,
bulk-policy gate, source как параметр). pom += `mysql-connector-j` +
`testcontainers:mysql`. **Первый dual-container IT** (Postgres+MySQL) — end-to-end
дамп→hd_*. Reader IT 4 + import IT 3.

**6. Вторая review-волна (Workflow, 3 измерения, `29ba54a`).** 17 raw → 7
confirmed, **0 Critical**, 10 отброшено. Все test-quality: fixture был
недостаточно discriminating (1 книга где bookID==bookNumber → JOIN не доказан;
mutation select bookID прошёл бы). Усилен fixture (книга bookID 2.0 ≠ bookNumber
5; orphan-хадис; muslim hasbooks='no') + тесты доказывают JOIN end-to-end +
ветки b==null/ch==null/no-grades + matn idempotency.

**7. Шаг 2.e — прод-обвязка + РЕАЛЬНЫЙ ПИЛОТ (`adb76b2`).** Просьба Абдулы:
«сделай всё сам в плане дампа, без ручной работы». Сделано:
- `SunnahDumpProperties` + `SunnahDumpConfig` (@ConditionalOnProperty bean
  MySQL DataSource + reader, изолирован от Postgres) + `SunnahAdminController`
  (ADMIN-only, bulk-policy gate: GET /collections превью, POST /import/{coll}) +
  503 `sunnah-dump-not-configured`. Controller IT 3, api-contract + generate-api.
- **Реальный прогон:** поднял MySQL (docker, `db/00-samplegitdb.sql` —
  13 collections / 100 hadiths Бухари), backend с `SUNNAH_DUMP_*` env →
  `POST /import/bukhari` → **inserted=98** skippedExisting=2 (курируемые №1/№8
  побеждают). hd_hadiths 101, matn ar/en + book/chapter enrichment корректны,
  detail/list API отдают. Реальные данные видны в Hadith Explorer.

**8. De-flake BookRepositoryIT.** Рекуррентный флак full-прогона — оказался
test pollution (другой класс коммитит lib_books), не tie-break. Fix:
subsequence-ассерт. Системная flakiness (PdfControllerIT и др.) — в backlog,
отдельная тест-гигиена.

**9. ПОД-ПРОЕКТ #1 — инструмент просмотра/дебага хадисов (pivot Абдулы:
«контент в последнюю очередь, нужны инструменты для заполнения/просмотра/
дебага»).** Страница хадисов была непригодна (стена текста + сырая разметка
sunnah). Спека `2026-06-01-hadith-viewing-tool-design.md` (brainstorm →
approved). Сделано (4 коммита):
- **`SunnahTextCleaner`** (`adb…`): срезает inline-разметку (HTML, quran-якоря
  `<A href=openquran>`, footnote `<c_qNN>`), decode entities, в reader. Реальная
  перечистка: delete 98 + reimport → 0 matns с markup (хадис №32 теперь чистый).
- **`GET /hadith/collections`** (chip-фильтр + реальный hadithCount) + **sort**
  (number/alphabetical-арабский/recent) + **previewMatn** (диакритизированный
  text_ar первичного matn, batch-load — красивее folded normalizedMatn).
- **Редизайн `HadithListPage`**: чипы-сборники + sort + одна колонка, чистые
  арабские карточки (naskh/RTL). 3 component-теста, lint 0err, build green.
- ⚠️ Playwright visual НЕ прогнан (MCP chromium missing + skill не
  зарегистрирован) — но страница ЖИВАЯ на :5173 с реальными данными.

### Решения

- **ADR-051** staging-схема sn_staging_* (+ дополнение про mapper-слой).
- **ADR-052** (step 2.d): дамп sunnah.com читаем через **MySQL-драйвер +
  Testcontainers** (решение Абдулы) — `mysql-connector-j` runtime +
  `testcontainers:mysql`. Отвергнуто: конвертация в SQLite, API-first.
  Реализовано в этой же сессии.
- **migration 59 `chapter_id` integer → varchar** — из-за дробного babID
  реального дампа (1.1, 22.10). Миграция была unreleased (дев-бэк на 58),
  потому правлена in place, а не alter-миграцией.

### Проблемы / known

- Системная flakiness full-прогона: IT-классы делят Testcontainers Postgres,
  каждый `verify` краснит 1 случайную «жертву» (зелёная в изоляции).
  `BookRepositoryIT` исправлен (subsequence-ассерт); `PdfControllerIT` и др. —
  в backlog (отдельная тест-гигиена). **0 реальных failures**, ~1137 тестов.
- ⚠️ **migration 59 правлена in place** (chapter_id varchar) — дев-БД была на
  58, конфликта checksum нет; теперь backend применил 59 при рестарте.

### Следующий шаг

**Phase 5 шаг 2 закрыт (2.a-2.e) + под-проект #1 (просмотр хадисов) сделан.**
Пивот Абдулы: контент — в последнюю очередь, **строим инструменты**. Очередь
под-проектов (спека `2026-06-01-hadith-viewing-tool-design.md` §5 — out of scope #1):

1. **Под-проект #1 — просмотр/дебаг хадисов: ✅ СДЕЛАН** (чистка текста +
   чипы-сборники + sort + чистые арабские карточки). Остаток (опц.): английский
   текст тоже содержит HTML — `SunnahTextCleaner` уже применяется и к bodyEn,
   так что чисто; диакритизация на карточке — через previewMatn, сделано.
2. **Под-проект #2 — линковка хадисов в узлы тем** (спека
   `2026-06-01-hadith-node-citation-design.md`). **#2.A backend ✅** (`84a565e`):
   `HadithCitationService` (ensure-source через мост `Hadith.sourceId` +
   attach) + `POST /nodes/{id}/hadith-citations` + IT (4). **← #2.B — порядок:**
   **(a) backend-обогащение (НЕ сделано):** `HadithRepository.findBySourceIds`
   + обогатить `GET /nodes/{id}/sources` для HADITH полями `hadithId`/
   `previewMatn`/`collectionName`/`primaryNumber` в `NodeSourceResponse` (без
   них фронт не нарисует) + IT; **(b)** рестарт backend → `generate-api`;
   **(c) frontend:** новая `HadithPickerModal` (НЕ reuse full-page
   `HadithListPage` — у неё нет onSelect; переиспользовать хук `usePagedSearch`
   + `GET /hadith/hadiths`), `onSelect(hadithId)`; **(d)** рендер хадис-опоры
   в `NodeCitationsSection.tsx` (3-я кнопка «прикрепить хадис» + ветка рендера
   по `hadithId` перед FreeformCite). Детали — SESSION_START «next».
3. **Под-проект #3 — примирение `hd_collections` ↔ библиотечный «Сборник
   хадисов»** (book_type=HADITH): два представления одного сборника. Архитектура.
4. **Frontend AdminSunnahPage** (опц.): кнопки превью+импорт поверх
   `/api/v1/admin/sunnah/*` (типы в types.ts) — триггерить импорт без curl.
5. **Под-проект #4 / Phase 5 step 3 `IsnadExtraction`** (= контент, Абдула
   отложил): matn+isnad блоб → AI (ADR-042) → hd_sanads + trust-level. step 4
   `SunnahApiClient` + полный корпус (sample-дамп = только 100 хадисов Бухари).

Источник за интерфейсом `SunnahDataSource` (dump сейчас, API позже одной
реализацией, mapper/контроллер не двигаются).

**Инфра пилота (важно для воспроизводимости):**
- Контейнер `sunnah-mysql` на :3307 (`db/00-samplegitdb.sql` загружен,
  root/root, БД `sunnah`). Дамп-файл — `/tmp/sunnah.sql` (re-fetch:
  `curl -sL raw.githubusercontent.com/sunnah-com/api/master/db/00-samplegitdb.sql`).
- Backend сейчас запущен с `SUNNAH_DUMP_ENABLED=true SUNNAH_DUMP_URL=
  'jdbc:mysql://localhost:3307/sunnah?allowPublicKeyRetrieval=true&useSSL=false'
  SUNNAH_DUMP_USERNAME=root SUNNAH_DUMP_PASSWORD=root` + JDWP :5005. Без этих
  env импорт-endpoint → 503 (by design).
- Дев-Postgres теперь: 101 hd_hadiths (3 сид + 98 импорт Бухари VARIANT).
- Admin user для curl: `00000000-0000-0000-0000-000000000001`.

**Ручная проверка (от Абдулы, висит с Сессии 52):** hadith/narrator списки
(debounce+Load More), dark-theme primary Button hover, thesis-книга 15, минимап
при detail-панели. **Новое:** Бухари в Hadith Explorer теперь 101 хадис с
реальным текстом — глянуть как выглядит (en содержит HTML — см. follow-up #1).

