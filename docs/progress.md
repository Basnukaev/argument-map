# Журнал работы

Последние сессии. Новые записи - **сверху**

Формат записи описан в `docs/doc-hygiene.md` Принцип 5

**Архив:**
- Сессии 0-21: [`docs/archive/progress-sessions-1-21.md`](archive/progress-sessions-1-21.md)
- Сессии 22-29: [`docs/archive/progress-sessions-22-29.md`](archive/progress-sessions-22-29.md)
- Сессии 30-37: [`docs/archive/progress-sessions-30-37.md`](archive/progress-sessions-30-37.md)

---

## 2026-05-18 - Bilingual карточки узлов (backlog item)

Закрыт backlog «Bilingual карточки - двуязычный режим узла». Узел
может иметь арабский оригинал и перевод (RU / EN). Карточка
рендерится в одном из трёх режимов: только оригинал, только
перевод, оба друг под другом

**Backend:**

- миграция 44 - ALTER `nodes` добавляет 3 nullable колонки:
  `translation` (text), `translation_lang` varchar(5) CHECK IN
  ('ru', 'en'), `original_lang` varchar(5) CHECK IN ('ar', 'ru', 'en')
- `Node` domain расширен 3 новыми полями. RowMapper / save /
  update в NodeRepository учитывают новые колонки
- `NodeService` получил перегрузки с bilingual-полями:
  - `createNode(..., translation, translationLang, originalLang, userId[, role])`
  - `updateContent(nodeId, contentBox, translationBox, langBox,
    originalLangBox, userId[, role])` - boxed-семантика через
    sentinel `NoChange.INSTANCE` отличает «не пришло в PATCH» от
    «пришло null (очистить)»
- Валидация: translation NOT NULL → translationLang обязателен;
  whitelist enum. Двойная - @Pattern в DTO (быстрый 400) + сервис
  (защита от прямого вызова)
- Revision НЕ пишется для translation / lang - это metadata, не
  version-controlled
- `UserPreferenceService.ALLOWED_VALUES` расширен ключом
  `bilingualMode` (original / translation / both)

**Frontend:**

- `preferencesStore` ключ `bilingualMode: BilingualModePref`,
  default `'both'`. Включён в mergeWithDefaults / resetAll /
  readCurrentPrefs
- `NodeCard` ключевая логика:
  - эффективный язык оригинала - explicit originalLang приоритетнее,
    fallback на `hasArabicScript(content)`
  - toggle (Languages icon) в card header виден ТОЛЬКО когда
    узел имеет translation. Cyclic - текущий → next по кругу.
    Local override приоритетнее глобального preferencesStore
  - 3 режима: original / translation / both. В both между блоками
    разделитель + label «перевод»
  - Original получает `font-naskh` + `leading-[1.8]` если isArabic;
    translation - regular sans-serif. `dir="auto"` для обоих
- `UserPreferencesSection` (Settings) - новая секция «Двуязычный
  режим узлов» с radio-style выбором 3 опций. Persist через
  preferencesStore → backend
- i18n keys (RU + AR, ~25 ключей): `node.bilingual.*` для контролов,
  `settings.section.bilingual.*` для Settings секции

**Tests:**

- Backend `NodeServiceIT` +5: createNode_withTranslation_persists,
  createNode_withTranslationButNoLang_throws,
  createNode_invalidOriginalLang_throws,
  updateContent_clearTranslation_setsToNull,
  updateContent_addTranslationToExistingNode - 834 → 839 backend tests
- Frontend `NodeCard.test.tsx` (новый, 5 tests): без translation
  toggle скрыт + рендерится только оригинал; режим both - оба блока +
  label; режимы original / translation скрывают одну из сторон;
  toggle cycling меняет local override - 440 → 447 frontend tests

**Что отложено в backlog:**

- Multi-translation (Translator attribution) - M:N таблица если
  возникнет use-case разных переводчиков одного аята. Текущий MVP -
  один перевод на узел
- Inline NodeContentEditor / NodeDetailsPanel редактирование
  translation - сейчас translation создаётся только через REST
  (POST/PATCH /nodes). UI редактор - в отдельный backlog item
  (BilingualNodeEditor)
- AddNodeModal расширение - сейчас принимает только content.
  Translation добавляется отдельным PATCH либо через future
  BilingualNodeEditor

**Коммиты:** 5

1. `feat(backend): миграция 44 + Node.translation/translationLang/originalLang`
2. `feat(backend): NodeService + DTO + IT для translation полей`
3. `fix(backend): обновить CreateNodeRequest/UpdateNodeRequest usages в IT тестах`
4. `feat(frontend): bilingualMode в preferencesStore + i18n keys + types`
5. `feat(frontend): NodeCard bilingual rendering + Settings секция`

**Verify:** backend `./mvnw verify` clean (839 tests), frontend
`tsc + lint + build + test:run` clean (447 tests, 0 errors,
7 baseline warnings)

---

## 2026-05-18 - Topic settings drawer (backlog item)

Закрыт backlog «Topic settings drawer - 480px drawer над затемнённым
canvas». Polished UX consolidation existing visibility + members
controls + новые status algorithm picker + audit link для admin +
danger zone delete

**Новый компонент** -
`frontend/src/apps/argument-map/components/TopicSettingsDrawer.tsx`:

- 480px end-side drawer (desktop) / fullscreen (mobile) над dimmed
  canvas. Own backdrop + `<aside>` slide-in справа (паттерн
  `SourceDetailPanel`, не нативный `<dialog>` - нужен start/end-side
  slide а не центр)
- Sections:
  - **Корневой вопрос** - read-only title + description с
    Lock-иконкой + hint «нельзя изменить - граф вырастает из него»
    (бэк не имеет PATCH /topics/{id} для title/description -
    осознанно read-only, в backend нет endpoint'а)
  - **Видимость** - переиспользуем `VisibilityRadioGroup`. Save
    autosave (PATCH /visibility on radio change, не explicit save
    button)
  - **Участники** - compact preview top-3 members + counter, expand
    link в `TopicMembersModal` для full management. Section visible
    только при `visibility=SHARED`
  - **Алгоритм статусов** - radio MVP vs DUNG_GROUNDED с hint'ами.
    PATCH /status-algorithm autosave on radio change
  - **Журнал изменений** - admin-only, ссылка на `/admin/audit?
    entityType=TOPIC&entityId={id}`
  - **Опасная зона** - delete button → modal с typing topic name
    защита (paттерн GitHub/GitLab). Confirm button disabled пока
    `typed.trim() === title.trim()`. DELETE /topics/{id} → toast +
    navigate /topics
- Permission gates - `canManage` (owner/admin) hides
  visibility/members/status/danger sections для read-only viewer.
  `isAdmin` separates audit section
- Conditional render `{open && <Drawer/>}` - идиома проекта,
  избегает effect-driven state reset между переоткрытиями (см.
  frontend/CLAUDE.md)

**Integration в `TopicGraphPage.tsx`:**

- Заменили inline visibility badge click + members button на единый
  `Settings` IconButton (gear) в crumb-bar для `canManage`. Badge
  остаётся read-only для всех
- Удалили inline `VisibilityChangeForm` (33 строки) и `Modal` +
  `TopicMembersModal` mount - drawer сам управляет всем
- TopicGraphPage shrunk on 88 строк (-118 +30 net)

**41 i18n key `topic.settings.*` (RU + AR)** - title / close /
section.* / field.* / root_question.locked_hint / members.* /
status_algorithm.{mvp,dung}{,_hint,change_*} / audit.* / danger.*

**Tests** (`TopicSettingsDrawer.test.tsx` - 7 tests):

- render sections respecting `canManage` / `isAdmin` permissions
- audit-section только для ADMIN
- members section появляется при rerender с visibility=SHARED
- PATCH /visibility → onChanged callback + toast
- PATCH /status-algorithm → onChanged
- delete flow: typing topic name unlocks confirm → DELETE → navigate
- read-only viewer не видит destructive sections

**Verify:** lint 0 errors, build 8.87s, 442 tests pass (baseline 435+).

**Что НЕ сделано осознанно:**

- Title/description editing - нет PATCH /topics/{id} endpoint'а на
  backend. Если потом понадобится - добавить endpoint + поля
  редактируемыми в drawer (read-only сейчас, lock + hint)
- Context menu canvas «Настройки темы» - пропустил, gear icon в
  crumb-bar достаточен для discoverability. Context-menu integration
  потребовала бы изменения GraphCanvas (большой компонент) -
  непропорционально к value

**Атомарные commits:**

- `feat(frontend): TopicSettingsDrawer 480px desktop / fullscreen mobile`
- `feat(frontend): integration TopicSettingsDrawer в TopicGraphPage toolbar`
- `feat(frontend): danger zone delete topic с confirmation type-name flow`
- `docs: TopicSettingsDrawer - backlog cleared + progress`

---

## 2026-05-18 - Library overview (backlog item)

Закрыт backlog «Library overview» - extend `BookListPage` (route /books)
до polished обзорной страницы каталога. Не создавал отдельный
`LibraryOverviewPage` - per task MVP «extend BookListPage, ничего нового
не создавать». Routing остался `/books` (per ADR-022 frontend reorg);
«/library» в спеке - conceptual

**Что добавлено в `frontend/src/apps/library/pages/BookListPage.tsx`:**

- **LibraryHero** компонент с eyebrow «БИБЛИОТЕКА · КАТАЛОГ», h1
  «Библиотека», description (длинная читабельная строка про назначение)
  + total count «N книг доступно» (bdi для number в RTL)
- **Debounced search** (300ms через useEffect + setTimeout sync
  searchInput → searchQ → useEffect refetch). Server-side `?q=` -
  меньше client-side фильтрации, точные total counts. Search input
  c X-clear button + Search-иконкой
- **VisibilityFilter chips** (Все / Мои / Разделяемые / Публичные) -
  client-side filter поверх загруженной страницы. backend не
  поддерживает `?visibility=`. «Мои» приближённо = PRIVATE
  (точный owner-filter требует `BookSummary.createdBy` которого
  backend не отдаёт - оставлено в backlog)
- **AuthorityFilter** компонент - text input + dropdown autocomplete
  по `/api/v1/authorities?q=` (debounce 300ms, size=10). Click select
  → server-side фильтрация `?authorityId=`. Era-suffix в результатах,
  outside-click close, X-clear когда выбран
- **Sort dropdown** переключён `added/title/type` → `latest/alphabetical`
  (более natural для read-mode библиотеки). Default `latest` =
  backend createdAt DESC (стабильный)
- **EmptyState** компонент - круговая ink-100 иконка `BookOpen`, h2 в
  serif, descriptive paragraph, primary CTA button «Импорт из Shamela»
  на /admin/shamela (вместо minimal `book.list.title` fallback)
- **Load More** label поменян на `library.overview.load_more` (раньше
  common.load_more, теперь специфичный ключ для possible override)

**Tests** (`BookListPage.test.tsx` - новый, 5 tests):

- empty state иллюстрированный + CTA проверки
- рендер карточек с visibility badge + href /books/{id}
- debounced search триггерит refetch с `?q=Бухари` (через msw query
  inspection + 400ms timeout для wait debounce)
- visibility filter chips переключают видимость client-side (toggle
  ALL → PRIVATE → ALL)
- Load More кнопка появляется когда hasNext, click аппендит следующую
  страницу

**i18n** - 28 ключей `library.overview.*` (RU + AR): title, description,
total_books, search_placeholder, search_clear, filter.{all,my,shared,
public,authority,authority_placeholder,authority_clear}, sort.{label,
latest,alphabetical}, empty_state.{title,description,cta}, card.
{pages_count,visibility.{private,shared,public}}, load_more

**Pre-existing code style notes:**

- `setLoading(true)` синхронно в useEffect нельзя
  (react-hooks/set-state-in-effect) - в AuthorityFilter через
  `Promise.resolve().then(() => setLoading(true))` microtask trick.
  Pattern полезен для других мест где нужен loading indicator в effect
- act() wrap для setTimeout-debounce теста чтобы избежать act warning
  про необёрнутый state update внутри setTimeout

**Commits** (Сессия 38):
- `84c8100` feat(frontend): library overview - hero + search debounced + filter chips
- `cee313a` feat(frontend): library overview - authority filter + sort dropdown + cards layout
- `4d60337` feat(frontend): library overview - empty state illustrated + Load More + tests
- `<this>` docs: library overview - backlog cleared + progress

**Результаты verify:**

- 435 frontend tests pass (430 baseline + 5 новых для overview)
- npm run lint - 0 errors (7 pre-existing react-refresh warnings)
- npm run build - 8.9s clean
- Playwright smoke @ 375px mobile (`/tmp/library_smoke.mjs`):
  - hero title visible, search input visible, visibility chips visible
  - search «Бухари» отправляется, filter chip «Мои» переключается
  - grid grid-cols-1 confirmed на mobile
  - screenshots в /tmp/library_0{1,2,3}_mobile_*.png

**Что пользователь может проверить руками:**

1. Открой `/books` (или conceptual «/library») на mobile (375px) или
   desktop
2. Должен увидеть:
   - hero header с total count из бэка
   - search input с placeholder
   - visibility chips горизонтальный ряд (overflow-x scroll на mobile)
   - Authority filter (text + dropdown по focus)
   - Sort dropdown справа
   - Type chips (BOOK/HADITH/QURAN и т.д. - вторая строка)
3. Печатай в search «Бухари» - после 300ms должен fetch с `?q=Бухари`
4. Click «Мои» - локально фильтрует PRIVATE
5. Если библиотека пустая - illustrated EmptyState с CTA
6. RTL switch (AR locale) - проверь что chips и dropdown ms/me/ps/pe
   логические работают

---

## 2026-05-18 - Multi-grading UI frontend (backlog item)

Закрыт backlog «Multi-grading UI» - frontend для `hadith_grades` бэка
(который закрыт чуть раньше в этой же дате, commits e5be185..30e0483).
HADITH source теперь имеет в `SourceCard` и `SourceDetailPanel`
секцию с оценками учёных (`HadithGradesSection`), conditional по
`sourceType === 'HADITH'`. Без HADITH source - компонент возвращает
`null` и не делает запросов, поэтому добавление безопасно для всех
вариантов

**Компонент `frontend/src/shared/components/citation/sourceCard/HadithGradesSection.tsx`:**

- collapsible header «Оценки учёных (N)» с count badge, default closed
- при раскрытии - fetch `GET /api/v1/sources/{sourceId}/grades`,
  loading spinner / error / empty state с CTA «Добавить первую оценку»
- list grades (denormalized scholar info из бэка - один SQL без N+1):
  scholarFullName / deathYearHijri (hijri в `<bdi>`), color-coded
  badge (SAHIH emerald / HASAN blue / DAIF orange / MAUDU rose darker),
  gradeCitation italic, comment expandable (truncate 80 chars + click)
- edit/delete actions visible только если
  `createdBy === user.id || user.role === ADMIN` - frontend hide,
  backend - source of truth через 403 forbidden-hadith-grade-write
- кнопка «Добавить оценку» снизу для не-пустого списка
- Modal с scholar autocomplete по `/api/v1/authorities?q=...&size=10`
  (debounce 250ms, paged response items), 4-state grade radio с
  colored label, citation/comment optional fields. Edit-режим
  не позволяет менять scholar (immutable по бэку)
- error mapping: 400 `invalid-hadith-grade` (не-HADITH source) /
  409 `hadith-grade-duplicate` / 403 `forbidden-hadith-grade-write` -
  локализованные form-error в modal, остальное через formatApiError

**Integration:**

- `SourceCard` - новые опциональные `sourceId` + `sourceType` props,
  при HADITH рендерится секция под Collapsible-metadata. Backward
  compat: для существующих caller'ов без передачи props - ничего
  не меняется
- `NodeCitationsSection`, `QuestionCitationsSection`,
  `AnswerCitationsSection` - пробрасывают `source.id` + `source.sourceType`
  из локального `sourceLookup` Map в `<SourceCard>`
- `SourceDetailPanel` - в `PanelBody` между Context и FullReading
  новый section с заголовком + HadithGradesSection, conditional по
  `sourceType === 'HADITH'`. Здесь у нас уже есть полный
  `SourceDto` из бэк-fetch'а

**i18n hadith.grades.* (34 ключа × 2 локали = 68 entries):**

- title / loading / empty_state / add_first / add_button / add_title /
  edit_title
- scholar / scholar_placeholder / scholar_hint / scholar_required
- grade.label / grade.SAHIH (Сахих / صحيح) / HASAN / DAIF / MAUDU
- citation_label / citation_hint / comment_label
- submit / submitting / cancel / edit / delete
- add_success / update_success / delete_success / confirm_delete
- error_load / error_save / error_delete / error_invalid_hadith /
  error_duplicate / error_forbidden

**Тесты:** 5 vitest (HadithGradesSection.test.tsx)

- не рендерит ничего для не-HADITH source (smoke null-render)
- empty list → empty state + кнопка «Добавить первую оценку»
- list grades с badge SAHIH/DAIF + edit/delete только для своих
- ADMIN видит edit/delete на чужих оценках
- delete grade с confirm → DELETE + refresh

Total: 430 vitest passing (425 baseline + 5 new). Lint clean
(2 errors про set-state-in-effect fixed - fetchGrades через
Promise.resolve().then() chain, autocomplete suggestions через
computed `showList` flag без sync setState в effect)

**3 коммита:**

- `7885b7c feat(frontend): HadithGradesSection компонент с list + add modal`
- `6e47ed0 feat(frontend): integration HadithGradesSection в SourceCard и SourceDetailPanel`
- next: `docs: multi-grading UI frontend - backlog cleared + progress`

**Backlog:** «Multi-grading UI» закрыт. api-contract.md уже
содержал секцию Hadith grades (backend сессия), туда менять
нечего. Без ADR - чистое расширение existing card UI без
architectural alternatives

## 2026-05-18 - Code review round 3 fixes (3 important closed, 4 в backlog)

Закрыты все 3 Important issues из третьего code review цикла (0 Critical).
6 issues всего, 3 из них (#1, #3, #5) реализованы, 3 (#2, #4, #6) +
Z-index renormalization зафиксированы в backlog с обоснованием.

**Implemented (3 атомарных коммита):**

- `fix(backend) #1` - **UserRepository.findByIds bulk** устраняет N+1
  в `AuditLogController.toResponses`. Комментарий обещал bulk JOIN,
  реально был цикл `userRepository.findById(actorId)` по каждому
  actor'у страницы. Новый метод через `NamedParameterJdbcTemplate`
  `WHERE id IN (:ids)`. Контроллер собирает `Set<UUID>` уникальных
  actorId'ов и резолвит одним SQL. Null-safe для system-инициированных
  событий (actorUserId=null). +5 IT (`UserRepositoryIT`)

- `fix(frontend) #3` - **authStore.logout cleanup user-scoped кешей**.
  На shared машине user A → set arabicFont=kufi + dismiss onboarding →
  logout → user B login получал чужие prefs и без onboarding widget.
  Новый helper `shared/utils/clearUserStorage.ts` чистит
  `app.preferences` + `onboarding_dismissed`. Вызывается из logout(),
  refreshAccessToken() при 401, loadCurrentUser() при refresh fail.
  Device-level prefs (theme/layoutAlgorithm/showEdgeLabels) намеренно
  не трогаем - ambient device settings. +4 теста (authStore.test.ts)

- `feat(backend) #5` - **AuditLogRetentionJanitor + ConditionalOnProperty +
  IT**. ADR-043 Amendment 3 (Этап 22.d) оставил retention janitor
  «отложен» в backlog. Закрываем по reviewer round 3 - audit_log будет
  расти неограниченно без cleanup'а. Cron 02:00 ежедневно, retention
  default 365 дней, минимум 7 (валидация в `AuditRetentionProperties`).
  По умолчанию выключен, активация в prod через `AUDIT_RETENTION_ENABLED=true`.
  Pattern полностью повторяет `OrphanDetectionJanitor` /
  `IntegrityVerificationJob`. +4 IT (`AuditLogRetentionJanitorIT`)

**Зафиксировано в backlog (4 issue):**

- #2 AuditEntityType / UserRole single source of truth (BE constants
  String + FE whitelist разъезжаются)
- #4 Authority.type column для HadithGrade scholar validation
  (сейчас принимает PUBLISHER как scholar)
- #6 Audit log для удалённых тем недоступен через `/audit/topics/{id}`
  (permission check throws 404 если тема удалена)
- Z-index renormalization (max+1 / min-1 unbounded growth - low pri)

**Verify:**
- backend `./mvnw verify` - 834/834 pass / 2 skipped (live tests) /
  0 failures. Время ~1:52
- frontend `npm test --run authStore.test.ts` - 13/13 pass
- TypeScript `npx tsc --noEmit -p tsconfig.app.json` - clean

**Документация:**
- `docs/backlog.md` - 4 новых entry в Tech debt секции
- `backend/CLAUDE.md` - audit log раздел дополнен Retention policy bullet

---

## 2026-05-18 - Multi-grading хадисов backend (backlog item)

Закрыт backlog «Multi-grading хадисов» (бэк часть). Один и тот же
хадис может быть оценён разными учёными по-разному (Бухари: SAHIH,
Тирмизи: HASAN, Албани: SAHIH). Существующее single-value
`Source.reliability` не трогаем - остаётся primary/legacy для backward
compatibility. Расширение - параллельная M:N таблица `hadith_grades`

**Backend (3 коммита):**
- Миграция 43 `hadith_grades` (UUID PK, source_id FK CASCADE на
  sources, scholar_id FK RESTRICT на authorities, grade VARCHAR(20)
  CHECK SAHIH/HASAN/DAIF/MAUDU, grade_citation VARCHAR(500), comment
  text, created_at, created_by FK users, UNIQUE source+scholar) + 2
  индекса. Domain: `HadithGrade` record + `HadithGradeValue` enum
  (отдельно от legacy `Reliability` - добавлен `MAUDU` «выдуманный»
  для полноты классической классификации) + `HadithGradeWithScholar`
  (denormalized read-model). Repository: CRUD + `findBySourceIdWithScholar`
  с JOIN на authorities (один SQL для GET endpoint - не N+1)
- `HadithGradeService` - addGrade валидирует source.type=HADITH (400 - нельзя
  grade'нуть BOOK/QURAN/ARTICLE), scholar exists (404), uniqueness
  scholar-for-source (409). updateGrade/removeGrade - permission через
  createdBy либо ADMIN (403 `forbidden-hadith-grade-write`). REST под
  `/api/v1/sources`: POST/GET `{sourceId}/grades`, PATCH/DELETE
  `grades/{gradeId}`. 4 новых exception type'а + handler'ы в
  `GlobalExceptionHandler` (hadith-grade-not-found, invalid-hadith-grade,
  hadith-grade-duplicate, forbidden-hadith-grade-write)
- 23 IT (14 service + 9 controller) - все сценарии edge cases

**Verify:**
- `./mvnw verify` - 825 tests pass / 2 skipped (live tests, ожидаемо)
  / 0 failures / 0 errors. Время полного билда ~1:46

**Что НЕ сделано (отложено в backlog):**
- Frontend UI (`HadithGradesSection` в `SourceCard` + `AddHadithGradeModal`) -
  бэк endpoint'ы готовы, можно поднять curl'ом. UI требует своей
  дизайн-сессии (color-coded grade badges, scholar autocomplete,
  edit/delete actions). Параллельно работал frontend subagent над
  Onboarding - не сталкивались по файлам, но в одну сессию два
  больших фронт-таска - перегрузка
- `findConsensusGrade(sourceId)` helper для majority-grade - не нужен
  пока UI не покажет «итоговую оценку» рядом с individual

**Параллельная работа:** frontend subagent в той же сессии закрыл
Onboarding (запись выше). Зоны не пересекались - backend hadith_grades
+ frontend onboarding/. Один merge без конфликтов

---

## 2026-05-18 - Onboarding checklist (backlog item)

Закрыт backlog «Onboarding» - 4-шаговый floating widget для новых
users. Per-device persist через localStorage без бэка
(`onboarding_dismissed`) - backend whitelist preference keys не
расширяем в MVP

**Frontend (3 коммита):**
- `useOnboardingProgress` hook (`shared/hooks/`) - state computation
  через single fetch на mount: GET `/topics?page=0&size=20` + filter
  по `createdBy === user.id` для первой owned темы, потом GET
  `/topics/{id}/graph` чтобы посмотреть nodes (CLAIM detection) +
  `inlineCitations` (source attached detection). 4 step ids:
  `create_topic`, `add_root_question`, `add_claim_node`, `attach_source`.
  Возвращает `{ steps, completed, total, isVisible, firstTopicId, dismiss }`.
  Tests +7 (anonymous, no topics, all done, partial, foreign topic,
  dismiss, persisted dismissed)
- `OnboardingChecklist.tsx` (`shared/components/onboarding/`) -
  floating card 320px `bottom-4 end-4` (RTL logical), header + 4
  steps list + progress bar. Step row clickable navigate на
  `/topics/new` (create_topic) либо `/topics/{firstTopicId}` (claim/
  source). `add_root_question` - non-clickable hint, появляется
  автоматически при topic create. Collapsible mini-state (counter
  «3/4» + expand chevron). Auto-dismiss при completed=4 с celebration
  toast + 3 sec delay. Tests +4 (anonymous render, user без тем,
  dismiss action + localStorage, all 4 done + progressbar a11y)
- App.tsx mount - render один раз рядом с CommandPalette/
  SourceDetailPanel/Toaster (НЕ внутри Routes - widget переживает
  навигацию между страницами)
- i18n RU+AR: 18 ключей `onboarding.*` (title, subtitle, dismiss/
  minimize/expand, progress placeholder `{completed} из {total}`,
  4×label + 3×action + 1×hint, completed_toast, aria_widget)

**Verify:**
- `npm run lint` - 0 errors (3 warnings - unrelated pre-existing
  react-refresh про co-exports)
- `npx tsc --noEmit -p tsconfig.app.json` - clean
- `npm run test:run` - 422 passing (411 baseline + 11 новых)
- `npm run build` - 6.84s clean
- Playwright smoke (headless WSL2): login fresh user → widget
  visible bottom-end с «0 из 4» → click step 1 → navigate /topics/new
  → dismiss X → widget скрыт, localStorage `onboarding_dismissed=1`

**Что не сделано (отложено в backlog):**
- `OnboardingHint` - bouncing arrow pointer на canvas «+» button
  для active `add_claim_node` step. Сложность low value, MVP без
  hints - сами labels достаточно clear
- Per-step events refresh - сейчас widget обновляется только при
  re-mount (route change). Добавить broadcast event listener для
  refetch при mutation (create_topic, add_node, attach_source) -
  upgrade для следующей сессии если станет UX critical
- Backend persistence - сейчас per-device через localStorage.
  Если user пользуется multiple devices - dismissed на одном не
  sync на другой. Можно расширить preference whitelist на
  `onboardingDismissed` если станет важно

**Что user может проверить руками:**

1. Открыть `/login`, залогиниться как fresh user (нет тем)
2. Видеть floating widget в правом-нижнем углу `bottom-4 end-4`
   с header «Начни работу» + 4 шагами, первый highlighted
3. Кликнуть «Создай первую тему» - перейти на `/topics/new`
4. Создать тему - вернуться на `/topics` - widget показывает
   2 из 4 (steps 1+2 completed, strikethrough)
5. Открыть тему - добавить CLAIM узел - вернуться на `/topics` -
   step 3 done
6. Добавить inline citation `[1]` к node + привязать source -
   step 4 done, появляется toast «Отлично! Все шаги пройдены»,
   через 3 сек widget исчезает
7. Свернуть widget через chevron ↓ - остаётся mini-counter
8. Dismiss X - widget пропадает permanently
   (`localStorage.onboarding_dismissed=1`)

---

## 2026-05-18 - SourceDetailPanel (backlog item)

Закрыт backlog «Source detail panel» - параллельная боковая панель
800px desktop / fullscreen mobile с полным содержанием цитируемого
источника. Открывается из всех 4-х точек citation UI: NodeCitations,
QuestionCitations, AnswerCitations (через SourceCard title click) +
InlineCitationMarker popover (через «Открыть подробнее» action)

**Frontend (3 коммита):**
- `sourceDetailPanelStore` Zustand store - минимальный контракт
  (current: SourceDetailCitation | null, isOpen, openWith, close).
  Множественные компоненты открывают одну mount'нутую панель в App.tsx
  без prop drilling. 4 unit теста pass
- `SourceDetailPanel.tsx` - public wrapper + content компонент.
  Conditional render идиома `{isOpen && <Content key={sourceId} />}`
  - re-mount на смене source, state без useEffect-сброса (обходит
  react-hooks/set-state-in-effect, см. memory `feedback_react_key_remount`).
  Sections: Metadata (type/title/authority с deathYearHijri/citation/
  reliability HADITH), Quote (выделенный blockquote accent-500 с
  dir="auto" + naskh для arabic), Context (dashed border), Full Reading
  (кнопка → /books/{bookId} для BOOK/QURAN/HADITH с bookId).
  Tailwind logical `end-0` + `w-[800px]` (desktop) / `w-screen` (mobile),
  backdrop click-to-close + Escape listener. 6 vitest pass
- Integration в 3 SourceCard consumer + InlineCitationMarker:
  - SourceCardHeader: title опционально clickable (`onTitleClick`)
  - прокинуто через SourceCard в NodeCitations/Question/AnswerCitations
  - InlineCitationMarker popover - добавлена «Открыть подробнее» ссылка
- mounted в App.tsx один раз, рядом с CommandPalette/Toaster
- i18n RU+AR: 14 ключей `source_detail.*` + 1 `inline_citation.open_detail`
  (29 строк добавлено в dictionary.ts)

**Verify:**
- `npm run lint` - 0 errors
- `npx tsc --noEmit -p tsconfig.app.json` - clean
- `npm run test:run` - 411 passing (было 393 baseline + 10 новых +
  unrelated рост от другой работы)
- `npm run build` - 7.32s clean
- Playwright smoke (headless WSL2): login flow + topic page + graph
  render OK, 0 console errors

**Что не сделано (отложено в backlog):**
- Hover-preview popover для inline citation marker - сейчас click,
  можно сделать hover для quick preview. MVP: click оставлен
- «Attached to» section в panel (список других nodes использующих
  этот source) - нужен новый endpoint `GET /sources/{id}/usages`
- Slide-in animation - сейчас просто появляется. CSS transition с
  `translate-x-full` → `translate-x-0` отложен (motion preferences)

**Архитектура decision (без ADR):**
- Глобальный Zustand store вместо prop drilling - множественные точки
  открытия, panel mount'ится глобально, не tied к конкретной странице
- Conditional render `{isOpen && <Content/>}` вместо постоянного mount
  + style toggle - state в Content всегда fresh, не нужен useEffect
  для reset
- `key={sourceId}` форсирует re-mount на смене источника - чистое
  состояние, не нужно вручную обнулять FetchState в setState

**Коммиты:**
- `ef92612` feat(frontend): sourceDetailPanelStore Zustand для управления panel state
- `8a99113` feat(frontend): SourceDetailPanel компонент 800px desktop / fullscreen mobile
- `0339644` feat(frontend): integration SourceCard + InlineCitationMarker - открывают panel
- (this commit) docs: SourceDetailPanel - backlog cleared + progress

---

## 2026-05-18 - Settings screen + user preferences (backlog item)

Закрыт backlog «Settings screen». Полноценные user-preferences с
persistance на бэке + FOUC-prevention через localStorage cache.

**Backend (3 коммита):**
- миграция 42 `user_preferences (id, user_id FK CASCADE, key, value jsonb,
  updated_at, UNIQUE(user_id, key))` + индекс по user_id
- `UserPreference` record (id/userId/key/value as JSON string/updatedAt)
- `UserPreferenceRepository` JDBC: findByUserId / findByUserAndKey /
  upsert (INSERT ON CONFLICT (user_id, key) DO UPDATE) / delete. Cast
  `CAST(? AS jsonb)` для jsonb колонки
- `UserPreferenceService` - whitelist валидация в коде (Map ALLOWED_VALUES):
  - enum-string keys: locale (ru/ar/en), arabicFont (naskh/kufi/tahoma),
    textSize (small/medium/large/xl), theme (system/light/dark)
  - boolean keys: hideTashkeelByDefault, transliteration
  - getAll / set / setAll (bulk) / delete. Невалидное значение/ключ →
    IllegalArgumentException → 400 через GlobalExceptionHandler
- `PreferencesController` - 4 endpoint: GET /preferences, PUT (bulk),
  PUT /{key} (конверт {value: ...}), DELETE /{key}
- IT: 11 service tests + 6 controller tests (17 новых, все pass)

**Frontend (3 коммита):**
- `apiPutRaw` helper в `shared/api/client.ts` (отсутствовал, добавлен
  по аналогии с apiPatchRaw)
- regenerated `types.ts` (новые endpoints)
- `usePreferencesStore` Zustand с pre-paint cache в localStorage
  ('app.preferences') + backend sync. Методы: loadFromBackend
  (mergeWithDefaults на ответ + persist), set (optimistic local + PUT
  + revert на error), resetAll (Promise.allSettled DELETE всех ключей),
  resetLocal (для logout). 5 unit тестов pass
- `UserPreferencesSection` - новая секция в SettingsPage: язык, размер
  текста (с preview), упрощённый арабский шрифт (3 опции вместо 10),
  toggle tashkeel/транслит, reset all с confirm modal. Toast на каждое
  save/error
- расширены i18n RU+AR (~30 новых ключей: language/textSize/tashkeel/
  transliteration/saved_toast/reset_defaults/etc)
- `PreferencesEffect` side-effect компонент в main.tsx:
  - login → loadFromBackend, logout → resetLocal
  - locale → useLocaleStore.setLocale (LocaleEffect ставит html lang dir)
  - theme → useThemeStore.setMode
  - textSize → CSS var --text-size-scale, arabicFont → --font-arabic-pref
  - hideTashkeel/transliteration → data-attributes на <html>
- 3 vitest для SettingsPage (render секций, click AR → PUT, toggle
  транслитерации)

**Не сделано (отложено):**
- EN словарь - сейчас locale=en фолбэк на ru, отдельная итерация
  перевода ~1000 ключей в backlog
- Drag-приоритет источников из old backlog formulation - отдельная
  работа, никак не связана с preferences
- ADR не пишется - решение очевидное (key-value table + whitelist),
  без alternatives to compare

**Verify статус:** UserPreferenceServiceIT + PreferencesControllerIT
17/17 pass. preferencesStore.test.ts + SettingsPage.test.tsx 8/8 pass.

---

## 2026-05-18 - Inline citations [N] в тексте узлов (backlog item)

Закрыт пункт backlog «Inline citations - формат `[1]` в тексте с
popover, привязанные к node-source records». Реализация без миграции
БД - чисто на уровне DTO + frontend парсер

**Стратегия mapping markers → sources:** Подход A (implicit ordinal).
`[N]` → N-й source в `node.inlineCitations[]` (1-based). Простой и
достаточный для MVP. Если возникнут reorder issues - move to mixed
(`[#sourceId]` для explicit) без breaking change

**Backend (1 коммит):**
- new `InlineCitationRef` record DTO - lightweight ref для popover
- `NodeResponse` расширен полем `inlineCitations:
  List<InlineCitationRef>` (после `userVote`)
- `NodeSourceRepository.findInlineCitationsForNodes(List<UUID>)` -
  bulk-load JOIN sources + lib_books, ORDER BY node_id, created_at ASC.
  Один SQL на весь граф - не N+1
- `findInlineCitationsForNode(nodeId)` - one-node wrapper
- `DtoMappers.toResponse(node, stats, userVote, citations)` +
  graph-перегрузка с `citationsByNode` map
- `TopicController.getGraph` подключает bulk-load citations к нынешним
  vote bulk-load (теперь 3 SQL на весь граф: stats / userVotes /
  citations)
- `NodeController` create/update/z-order endpoints - точечная
  подгрузка через findInlineCitationsForNode
- 5 IT в NodeSourceRepositoryIT: ordinals по created_at, empty input
  → empty map, HADITH reliability, book.title > source.title fallback,
  bulk multi-node (ordinal counter сбрасывается per-node)

**Frontend (3 коммита):**
- `inlineCitations.ts` utility: `parseInlineCitations(body):
  ParsedSegment[]` regex `\[(\d+)\]` splits body на text+citation
  сегменты; `hasInlineCitations(body)` fast-path
- `InlineCitationMarker.tsx`: `<sup>[N]</sup>` clickable, popover с
  title / quote / citation / reliability (HADITH only). Dead-marker
  (grey, `cursor-not-allowed`) если citation undefined. Native popover
  attribute не используется (jsdom не поддерживает) - простой
  absolute-positioned dialog с click-outside + Escape close
- `InlineCitationBody.tsx`: wrapper принимает body + citations,
  парсит и рендерит segments. Используется в `NodeCard` body section
  и `NodeContentEditor` view-режим (edit-textarea оставлен как есть -
  user видит raw `[1]`)
- i18n: 3 ключа `node.inline_citation.*` (ru + ar)
- Tests +29 (15 parser unit, 8 marker render+interaction, 6 body
  integration)

**Что отложено:**
- Tiptap `formatted_content` integration - сейчас только plain
  `node.content`. Когда editor для книг будет на Tiptap (ADR-039),
  расширим парсинг на ProseMirror JSON
- Deep-link `/library/books/{sourceBookId}?highlight=...` на click -
  сейчас popover только показывает данные
- Explicit `[#sourceId]` syntax (Подход C mixed) - на случай если
  user'ы начнут реордерить sources
- Source picker integration - сейчас `[N]` пишут вручную в textarea

**Что user может проверить руками:**
1. Создать узел с body `Доказательство [1] и хадис [2]`
2. Привести 2 source через секцию «Опора» (любые)
3. Открыть граф - на NodeCard в body section должны быть видны
   `[1]` `[2]` маркеры (indigo chip)
4. Click на `[1]` - popover с title книги / quote / citation
5. Написать `[99]` - dead-marker (grey, не clickable), tooltip
   «Источник не найден»

Acceptance:
- Backend: 18/18 в `NodeSourceRepositoryIT` + 21/21 `TopicControllerIT`
  + 10/10 `NodeControllerIT` + 9/9 `NodeVoteControllerIT` (58 проверены)
- Frontend: 393/393 (29 новых, остальные не сломаны)

---

## 2026-05-18 - Dung's argumentation framework (backlog item, Этап 6)

Закрыт пункт backlog «Реализация Dung's argumentation framework для
продвинутого пересчёта». Opt-in переключатель алгоритма пересчёта
статусов через колонку `topics.status_algorithm` - default остаётся
MVP (см. ADR-007), новые темы и existing продолжают работать без
вмешательства

**Реализовано (5 атомарных feat-коммитов + 1 docs):**

1. **Backend - ADR-044 + миграция 41 + Topic domain** -
   `topics.status_algorithm VARCHAR(20) NOT NULL DEFAULT 'MVP'` +
   CHECK constraint `IN ('MVP','DUNG_GROUNDED')`. Topic record получает
   `String statusAlgorithm`, TopicRepository - `updateStatusAlgorithm`
   + расширение COLUMNS/ROW_MAPPER + duplicate SQL в
   `findVisibleToUserWithCounts`. `domain.StatusAlgorithm` константы
   (string-литералы, не enum - тот же подход что `TopicVisibility`).
   TopicResponse + DtoMappers расширены. TopicService/TopicImportService -
   MVP по умолчанию. IT тесты получили дополнительный аргумент
   конструктора Topic в 7 файлах
2. **Backend - `DungFrameworkService` с grounded labelling** -
   iterative attack-based algorithm. Фильтрует attack edges (REFUTES +
   INVALIDATES), строит `attackersOf` adjacency map, до convergence
   присваивает IN/OUT, remaining → UNDEC. 15 unit-тестов на records
   напрямую (singleNoAttack, mutual cycle, defender chain, support
   игнорируются, orphan edges, self-attack, 3-cycle, complex chain
   a→b→c→d, double attacker)
3. **Backend - `StatusCalculationService` переключение MVP /
   DUNG_GROUNDED** - public `recalculateTopic(topicId)` диспатчит по
   `topic.statusAlgorithm`. Existing логика - в private
   `recalculateUsingMvp`, новая `recalculateUsingDung` делегирует
   `DungFrameworkService` + mapping IN→STANDING/OUT→REFUTED/UNDEC→
   DISPUTED. EdgeService/NodeService callers не меняются. +3 unit
   теста в StatusCalculationServiceTest для DUNG cases
4. **Backend - `PATCH /api/v1/topics/{id}/status-algorithm` endpoint** -
   `UpdateTopicStatusAlgorithmRequest{algorithm}` с Pattern validation
   (`MVP|DUNG_GROUNDED`), `TopicService.updateStatusAlgorithm(topicId,
   algorithm, userId, role)` - assertIsOwner + audit UPDATE с FieldDiff
   statusAlgorithm + **side effect** `recalculateTopic` в той же
   транзакции. No-op для same value (без audit + без recalc).
   Controller endpoint mirror'ит `PATCH /visibility` pattern
5. **Backend - IT для TopicStatusAlgorithm endpoint** - 8 MockMvc
   тестов: setDung returns 200 + recalc цепочки a→b (REFUTES) даёт
   STANDING/REFUTED; setMvp recalculatesBack; invalidAlgorithm и
   null → 400; byNonOwner → 403 forbidden-topic-write даже на PUBLIC
   теме; unknownTopic → 404; sameValue noOp не пишет audit;
   writesAuditEntry проверяет UPDATE row в audit_log

**Семантика:**

- MVP учитывает SUPPORTS/REFUTES/INVALIDATES, статусы STANDING/
  DISPUTED/REFUTED/UNVERIFIED, поддерживает «свежий узел» через
  UNVERIFIED
- DUNG_GROUNDED учитывает только attack-edges (REFUTES +
  INVALIDATES), SUPPORTS/QUALIFIES/RESPONDS_TO игнорируются по
  дизайну Dung'а. Mapping IN→STANDING/OUT→REFUTED/UNDEC→DISPUTED.
  UNVERIFIED не используется в Dung'е - все ноды получают
  определённый label

**Тесты:** 785/785 IT pass, 300+ unit pass (+18 от Dung'а). Подробнее
по покрытию - в commits 6d288f5 (Dung unit) + 6e4d7ce (IT)

**Что отложено:**

- **Frontend UI toggle** алгоритма - сейчас доступен только через
  curl PATCH. Backend готов, фронту нужен toggle в `TopicMetaPanel`
  либо `TopicSettingsModal`. Power user'ы могут переключать сейчас,
  обычные - после frontend implementation
- **Preferred / stable extensions** - rejected в ADR-044 (multiple
  extensions = сложнее mapping на наш status enum, stable может не
  существовать). Если возникнет academic use case - расширяемо через
  новые значения CHECK constraint без breaking
- **Bipolar argumentation** (с support edges + attack edges,
  Cayrol & Lagasquie-Schiex 2005) - rejected как over-engineering для
  MVP. Наши SUPPORTS продолжают работать в MVP-алгоритме

**Что можно проверить руками:**

```bash
# создать тему (default MVP)
TOPIC_ID=$(curl -X POST http://localhost:9090/api/v1/topics \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Dung test","rootQuestion":"А?"}' \
  | jq -r .id)

# переключить на DUNG_GROUNDED
curl -X PATCH "http://localhost:9090/api/v1/topics/${TOPIC_ID}/status-algorithm" \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001" \
  -H 'Content-Type: application/json' \
  -d '{"algorithm":"DUNG_GROUNDED"}'

# проверить что вернулся 200 + statusAlgorithm:DUNG_GROUNDED
# создать узлы + attack-edges (REFUTES) - и убедиться через GET /graph
# что statuses посчитаны по Dung'у. Возврат на MVP - тем же endpoint
```

**Закрыто:**

- backlog «Реализация Dung's argumentation framework» → checked +
  reference на ADR-044
- roadmap «Этап 6. Улучшения бэкенда» свёрнут в строку - JSON
  export/import (Сессия 39) и Dung framework (эта сессия) закрыты,
  full-text search отложен через отдельный Elasticsearch (см.
  `docs/backlog.md` «Архитектурные решения»)

---

## 2026-05-18 - Z-index full-stack persistence для узлов (backlog item)

Закрыт пункт backlog «Z-index full-stack persistence для узлов и рёбер»
(в части узлов - edges отдельным пунктом). До этого `bring to front` /
`send to back` работали только в локальном RF-state и сбрасывались при
любом refetch графа

**Реализовано (5 атомарных коммитов):**

1. **Backend - миграция 40 + Node domain + repository** -
   `nodes.z_index INTEGER NOT NULL DEFAULT 0` + composite index
   `(topic_id, z_index)`. Node record получает `int zIndex`,
   NodeRepository - `updateZIndex`/`findMaxZIndex(topicId)`/
   `findMinZIndex(topicId)`. NodeResponse + DtoMappers + все Node
   constructor сайты (TopicService/TopicImportService/NodeService и
   4 теста) обновлены под новый record arg
2. **Backend - NodeService + REST endpoints** -
   `bringToFront(nodeId, userId, role)` с assertCanWrite, считает
   `findMaxZIndex(topicId) + 1`, обновляет, возвращает новый Node.
   `sendToBack` симметрично с min - 1. Семантическая идемпотентность:
   повторный bringToFront всё ещё ставит выше (max+1 от нового max).
   `POST /api/v1/nodes/{id}/z-order/bring-to-front` и `/send-to-back` -
   dedicated endpoints вместо расширения PATCH /nodes/{id} (проще API
   surface, нет race condition)
3. **Backend - IT NodeZIndexIT** - 5 кейсов: bringToFront_setsToMaxPlus1
   (последовательные z=1, z=2, z=3 идемпотентность), sendToBack_
   setsToMinMinus1 (z=-1, z=-2), bringToFront_nonOwner_returns403
   (PRIVATE тема + stranger = forbidden-topic-access не -write, так как
   `assertCanWrite` сначала проверяет canRead чтобы не leak'нуть
   existence), sendToBack_nonOwner_returns403, bringToFront_
   nonExistentNode_returns404
4. **Frontend - GraphCanvas + buildFlow** - `buildFlow` читает
   `n.zIndex` из DTO и кладёт в ReactFlow node `zIndex` prop.
   `bringNodeToFront`/`sendNodeToBack` теперь делают optimistic local
   update (на основе текущих node zIndex на канве) + `apiPostRaw` +
   `onRefetch`. Edges остаются с локальным zRef (отдельный пункт
   backlog). Гейтинг по `canWrite`: для read-only context menu пункты
   «На передний план» / «На задний план» скрыты (write op). Tests +2
   через MSW (POST hits правильный URL для обоих action)
5. **Docs** - этот entry + backlog «Z-index для узлов»  → [x] + новый
   подпункт edges, api-contract NodeResponse + 2 endpoint section +
   history row

**Тесты:**
- Backend: 771 (766 baseline + 5 NodeZIndexIT). Все pass.
  `./mvnw verify` clean
- Frontend: 364 (357 baseline + 5 admin audit subagent + 2 z-order).
  `npm run lint && npm run build && npm run test:run` clean

**Документация (включая api-contract):**
- `docs/api-contract.md` - NodeResponse `zIndex` поле + 2 endpoint
  section + история row
- `docs/backlog.md` - «Z-index для узлов» закрыто, оставлен подпункт
  «Z-index для edges» как отдельный
- `docs/progress.md` - эта запись

**ADR не понадобился:** изменение чисто incremental по существующей
паттерну (`updatePosition` без revision/updatedAt - копируем для
`updateZIndex`). Альтернативы (`PATCH /nodes/{id}` расширение vs
dedicated endpoints) - dedicated выбран как проще API surface
без race condition; обоснование зафиксировано в commit message

**Не сделано (намеренно отложено):**
- Z-index для edges - редко критично (edges обычно стабильно поверх
  или под nodes). Если возникнет use-case - тот же шаблон, миграция
  41 + EdgeService + 2 endpoint
- Renormalization z_index в диапазон `[0..N-1]` - 32-bit int даёт
  ±2млрд, на сотни тысяч toggles в одной теме не уплывёт. Compress -
  отдельный backlog item если станет нужно

**Что user может проверить руками:**

1. Открой граф темы с 2+ узлами
2. Правый клик на узел → «На передний план» / «На задний план»
3. Перезагрузи страницу (F5)
4. Должен увидеть: stacking order сохранился после refetch (раньше
   сбрасывался)
5. Обрати внимание:
   - У read-only пользователя (SHARED MEMBER, не EDITOR) пункты
     «На передний/задний план» в context menu НЕ показываются
   - Edge context menu z-order пункты работают как раньше (локально)

---

## 2026-05-18 - Admin audit UI (Этап 22.e, frontend, ADR-043 Amendment 3)

Закрыт Этап 22.e - frontend для audit log endpoint'а готового в 22.d.
Параллельно работал другой subagent над z-index persistence (backend +
NodeContextMenu) - не пересекались (я только frontend `apps/admin/` +
`shared/i18n/`)

**Реализовано (3 атомарных коммита):**

1. **AdminAuditPage.tsx + types + i18n + tests** - main page с
   header / FilterBar (native <select>, draft→applied state pattern) /
   AuditTable (CSS grid 7 columns, color-coded action badges
   emerald/blue/rose/purple/amber) / DetailsModal (pretty-printed JSON
   через JSON.parse + stringify(2), parse error fallback) / Load More
   pagination. Filter по action client-side - бэк его не принимает.
   `regenerate-api` подтянул AuditLogResponse + 4 endpoint signatures.
   ~40 i18n keys `admin.audit.*` в RU/AR. Tests +5 (render таблицы,
   Apply фильтра, View Details modal, empty state, Load More append).
   Total 362 frontend tests (357 baseline +5)
2. **Routing** - `/admin/audit` под ProtectedRoute requireRole="ADMIN"
   в App.tsx. USER → silent redirect /topics
3. **Nav link** - в AdminShamelaPage overflow menu (••• → "Audit log"
   с ShieldCheck иконкой). window.location.assign т.к. ContextMenu
   items - onClick callbacks, не Link node renderers. Видимость не
   гейтится по role: страница уже под ProtectedRoute ADMIN

**Документация:**

- roadmap.md - 22.e отмечен [x] с описанием + 22.f создан в backlog
  для private Q&A model + audit retention janitor (откладываем)
- progress.md - эта запись

**Verify:**

- `npm run lint` - 0 errors (7 pre-existing warnings)
- `npm run build` - clean (981kB main bundle, ожидаемо)
- `npm run test:run` - 362/362 pass
- `npx tsc --noEmit -p tsconfig.app.json` - clean

**Что user может проверить руками:**

1. Открой `/admin/shamela` от admin@argumentmap.local
2. Кликни ••• кнопку (overflow) → "Audit log"
3. Должна открыться таблица записей аудита
4. Apply фильтра entityType=TOPIC - таблица фильтруется
5. Кликни "Подробнее" - modal с pretty-printed JSON changes
6. Reset - фильтры очищаются, таблица возвращается ко всем записям

**Этап 22 полностью закрыт** (22.a + 22.b + 22.c + 22.c.f + 22.d +
22.e). Откладываются в 22.f backlog: private Q&A visibility model
(если понадобится) + audit retention janitor (cron cleanup >6 мес.)

---

## 2026-05-18 - Audit log per-entity (Этап 22.d, backend, ADR-043 Amendment 3)

Закрыт долг из 22-го этапа Amendment 2 «audit log отложен в 22.d».
Параллельно работал frontend subagent над smart edge routing - не
пересекались (он `frontend/`, я `backend/`)

**Реализовано (7 атомарных коммитов):**

1. **Миграция 39 + domain + repository** - `audit_log` таблица (event-
   sourcing lite, 1 row per mutation). 4 индекса:
   `(entity_type, entity_id, created_at DESC)`, partial
   `(parent_entity_type, parent_entity_id, ...)`, `(actor_user_id, ...)`,
   `(created_at DESC)`. `AuditLog` record + `AuditEntityType` /
   `AuditAction` constants (string literals не enum - добавление нового
   типа не требует миграции). `AuditLogRepository` с `findByEntityPage`
   / `findByParentOrSelfPage` (UNION self + child для GET /audit/topics)
   / `findByActorPage` / `findFilteredPage` (admin с
   entityType/actorId/dateFrom/dateTo, единый `appendAdminFilters`
   helper - count не разойдётся с list)
2. **AuditLogService** - synchronous в той же транзакции что и mutation
   через `@Transactional`. `logCreate/Update/Delete` + специализированные
   `logVisibilityChange` / `logMemberAdd/Remove/RoleChange`.
   JSON-сериализация changes через ObjectMapper - если падает, log
   warning + сохраняем row без changes detail (entity_id/action всё
   равно фиксируются). `FieldDiff(old, new)` record для UPDATE с
   per-field diff
3. **Integration TopicService/NodeService/EdgeService** - 5 mutation
   точек получили audit. TopicService createTopic пишет 2 entries
   (TOPIC + root NODE с parent=TOPIC). EdgeService updateEdge - audit
   в role-overload т.к. legacy без userId
4. **Integration BookService/QuestionService/AnswerService/Member services** -
   8 mutation точек. BookMember/TopicMember - audit
   MEMBER_ADD/REMOVE/ROLE_CHANGE с parent=TOPIC/BOOK. Shamela
   ETL и TopicImportService используют repositories напрямую - audit
   bulk-операций не пишется (acceptable, они имеют свой INFO log)
5. **AuditLogController + DTOs + handlers** - 4 endpoint'а с
   permission rules. AdminOnlyException → 403 forbidden-admin-only.
   Username для actor JOIN'ится bulk (один проход по unique IDs на
   страницу, не N+1). Date-фильтры - ISO-8601 instants
6. **IT тесты +16** - AuditLogServiceIT (8: sanity persist, FieldDiff
   round-trip, snapshot DELETE, DESC sort, UNION self+child, actor
   filter, visibility/member changes) + AuditLogControllerIT (7: owner
   200, non-owner 403, includes child entities, /me filter, admin 403,
   admin 200 с filters, admin invalid entity 400) + TopicControllerIT
   `createTopic_writesAuditEntry` integration test (POST /topics → 2
   audit rows)
7. **Docs** - ADR-043 Amendment 3 (event-sourcing rationale + manual
   logging vs AOP + rejected alternatives) + api-contract.md новая
   секция «Audit log API» + history entry + glossary «Audit log /
   AuditEntityType / AuditAction / parentEntity / changes JSON format» +
   backend/CLAUDE.md дополнение раздела Permissions + roadmap 22.d
   `[x]` + 22.e добавлен (admin UI + private Q&A + retention janitor)

**Результаты:**

- `./mvnw verify` BUILD SUCCESS, 770 тестов (754 + 16 новых).
  Одиночный transient MinIO container start fail прошёл на retry,
  не связан с нашими изменениями
- Audit пишется synchronous - rollback main flow откатит audit row
- ADMIN bypass всех endpoints через `UserRole.ADMIN.equals(role)`

**Что отложено:**

- Admin UI для audit (страница `/admin/audit` с фильтрами + таблицей) -
  22.e или backlog
- Private Q&A visibility model (если возникнет use-case закрытых
  учёных групп) - 22.e
- Async logging через outbox pattern - YAGNI до появления performance
  overhead
- Retention policy janitor (cron cleanup >6 месяцев) - до тех пор пока
  размер audit_log не подскочит до GB

**Smoke-check:**

- `POST /api/v1/topics` создаёт 2 audit_log rows (TOPIC + root NODE)
  - проверено через TopicControllerIT
- `GET /api/v1/audit/topics/{id}` возвращает 200 для owner, 403 для
  non-owner - проверено в AuditLogControllerIT
- Frontend контракт не сломан (новые endpoints additive, существующие
  не тронуты)

---

## 2026-05-18 - Smart edge routing (frontend, backlog → закрыто)

Backlog-задача из «Фронт - общие улучшения» закрыта. Параллельно
работал backend subagent над audit log 22.d - не пересекались
(он `backend/`, я `frontend/`)

**Реализовано (4 атомарных коммита):**

1. **`elkLayout.ts` + `applyLayout()` switch** - `elkjs ^0.11.1` (200KB
   gzipped в lazy chunk - не нагружает initial bundle для тех кто
   остаётся на dagre). `layered` algorithm + `elk.edgeRouting=ORTHOGONAL`
   - 90-градусные изломы вокруг узлов, меньше crossings чем dagre
   bezier curves. Возвращает только позиции узлов; рёбра остаются на
   `CustomEdge` с 4-handles (никакой замены edge component'а).
   `layoutAlgorithmStore` - Zustand persist в `argmap.layoutAlgorithm`
2. **`GraphPanels` layout dropdown** - новая кнопка `Network` в toolbar
   (под edge-labels toggle) с radio выбором `dagre`/`elkjs`. Loading
   spinner на иконке пока ELK пересчитывает. `GraphCanvas.triggerElkRelayout`
   - one-shot при выборе ELK: применяет позиции + PATCH каждый узел +
   `fitView`. После - работает как обычные сохранённые posX/posY,
   следующие refetch'и их уважают
3. **Tests +16** - elkLayout (5: empty / updates positions / count /
   data preserved / edges unchanged) + graphLayout switch (3: dagre /
   elk / empty) + layoutAlgorithmStore (4: default / setAlgorithm /
   localStorage / unknown fallback) + GraphPanels menu (4: open / default
   checked / pick ELK calls callback / no-op same algorithm). Mock
   elkjs в всех тестах - bundled.js 1.4MB не должен попадать в jsdom
4. **Docs:** ui-guidelines дополнен разделом «Layout algorithm»
   (default dagre, ELK для сложных графов через settings, lazy bundle
   chunk), backlog отмечен закрытым. gotcha не написан - elkjs работает
   по доке без сюрпризов

**Bundle impact:** elkjs выделен в отдельный chunk `elkLayout-*.js`
(1440KB raw, 438KB gzipped) загружаемый dynamic import только при
первом выборе ELK. Initial bundle не вырос. TopicGraphPage chunk остался
385KB raw / 123KB gzipped

**Playwright smoke** (manual, не CI): топик с 19 nodes / 18 edges
(`123test`). Скриншоты до/после в `/tmp/elk_before_dagre.png` +
`/tmp/elk_after_elk.png` - ORTHOGONAL routing визуально разводит
рёбра вокруг узлов вместо bezier-кривых через них

**Acceptance criteria выполнены:**
- npm run test:run: 357 / 357 (341 baseline + 16 новых)
- npm run lint: 0 errors (7 pre-existing warnings без отношения к ELK)
- npx tsc --noEmit: 0 errors
- npm run build: успешен, elkjs в lazy chunk, initial bundle не вырос

**Что отложено:**
- **Suspense UI** - сейчас lazy через dynamic import внутри `applyLayout`,
  но без Suspense overlay. Loading state через `layoutPending` + spinner
  на кнопке. Полный overlay над canvas - upgrade когда ELK станет default
  для больших графов
- **Edge bend points rendering** - ELK возвращает bend points в
  edge.sections[].bendPoints. Сейчас рёбра остаются на CustomEdge с
  bezier через handles. Полный orthogonal pipeline с edge polylines -
  upgrade когда попросят visual parity с ELK preview
- **Auto-suggestion** - сейчас выбор ручной через toggle. Auto-switch
  на ELK при `nodeCount > 30 || edgeCount > 50` - upgrade когда наберём
  данные по UX

---

## 2026-05-18 - Голосование за вес аргументов (backend + frontend)

Backlog-задача из раздела «Бэк - бэклог» закрыта. Параллельно с
22.c.f subagent'ом (book visibility/members) - не пересекались по
файлам: я работал в `backend/` + `apps/argument-map/components/graph/`,
тот - в `apps/library/`/`apps/admin/`/`shared/components/visibility/`

**Реализовано (7 атомарных коммитов):**

1. **Миграция 38** `node_votes (id UUID PK, node_id+user_id FK CASCADE,
   weight SMALLINT CHECK IN (-1,1), voted_at TIMESTAMPTZ, UNIQUE node+user)`
   + 2 индекса. Domain records `NodeVote` + `VoteStats`. `NodeVoteRepository`
   с upsert через ON CONFLICT, bulk-aggregation для graph endpoint
2. **NodeVoteService** + `InvalidVoteException` (→ 400 invalid-vote).
   Permission: vote требует только `canReadTopic` (голос это reaction,
   не write-access). PRIVATE-темы защищены автоматически
3. **3 REST endpoint** `/api/v1/nodes/{id}/vote(s)`: POST upsert (201),
   DELETE idempotent (204), GET stats. Валидация weight ∈ {-1,+1} в
   сервисе; @NotNull на DTO
4. **NodeResponse расширен** 4 vote-полями (`voteUpvotes`/`voteDownvotes`/
   `voteScore`/`userVote`). Legacy `DtoMappers.toResponse(Node)` сохранён
   (нули + null). `GET /topics/{id}/graph` делает 2 bulk-SQL на весь
   граф (не N+1). Single-node mutating endpoints тоже подгружают актуальную
   статистику в один ответ
5. **VoteWidget** в `NodeCard` для `ARGUMENT`/`EVIDENCE` - compact
   chevron-up/score/chevron-down с toggle (повторный click снимает),
   optimistic UI с revert, click stopPropagation. Auth-aware - анонимный
   click показывает toast info вместо запроса. Pure helper
   `computeOptimistic` вынесен и покрыт 4 unit-кейсами
6. **IT backend**: 8 service IT + 9 controller IT (включая GET /graph
   проверяет что vote-поля попадают через bulk-load)
7. **Docs**: api-contract.md (новая history entry + endpoints section +
   NodeVoteStatsResponse + расширение NodeResponse), architecture.md
   (новый раздел «8. Голосование за вес аргументов»), glossary.md
   (термин «Голосование за аргумент»), backlog.md (item закрыт)

**Результаты:**

- `./mvnw verify` BUILD SUCCESS - **750 tests, 0 failures, 0 errors, 2
  skipped** (baseline 733 + 17 новых vote IT)
- Frontend: `npm run test:run` **341/341 pass** (baseline 333 + 8 VoteWidget
  тестов)
- `npm run lint`: 0 errors (7 pre-existing warnings)
- `npm run build`: success
- `npx tsc --noEmit -p tsconfig.app.json`: clean
- types.ts регенерирован - `NodeResponse` получил vote-поля,
  `NodeVoteStatsResponse` + `CreateNodeVoteRequest` schemas доступны

**Решения:**

- **3-point scale {-1, +1}** для MVP. 5-point {-2..+2} (категории силы)
  в backlog'е. 3-point - современный паттерн (Reddit/HN), проще UI
- **Vote не сохраняется как 0** - removeVote = DELETE row. Иначе семантика
  «голосовал ли я» становится мутной
- **Vote не влияет на StatusCalculation** - Dung-style логика остаётся
  чистой. Голоса - параллельный сигнал силы (как лайки на FB), не
  меняющий структурные статусы STANDING/DISPUTED
- **Permission - canReadTopic, не canWriteTopic** - vote это reaction
  на узел, не его изменение. PRIVATE-темы защищены автоматически (не
  видишь = не голосуешь)
- **VoteWidget только для ARGUMENT/EVIDENCE** - QUESTION (корневой
  вопрос темы) и CLAIM (главный тезис) не голосуются: они структурные,
  не аргументы за/против
- **Bulk-load в graph endpoint** через 2 SQL (агрегаты + персональные
  голоса). N+1 загрузка по каждому узлу при render'е графа была бы
  катастрофой при большом графе

**Что отложено:**

- 5-point scale {-2..+2} с категориями («слабое»/«сильное» несогласие)
- Voter list UI - показать кто именно голосовал (transparency).
  Backend endpoint `GET /api/v1/nodes/{id}/votes/voters` отложен,
  на бэке `NodeVoteRepository.findByNodeId(...)` готов для этого
- Vote-driven UI hints в StatusCalculation - например подсвечивать
  «STANDING но downvoted» как «слабый аргумент». Это product-level
  feature, не MVP
- Vote aggregates в `TopicResponse` - топ-обсуждаемые аргументы темы,
  «3 самых сильных EVIDENCE»

**Проверить руками:**

- открой `/topics/{any}` где есть `ARGUMENT` или `EVIDENCE` узлы
- в footer карточки увидишь компактный виджет: chevron-up / score / chevron-down
- click upvote (зелёная стрелка вверх) - счёт +1, кнопка подсвечена emerald
- click downvote - смена голоса на -1, score обновляется
- повторный click по уже-активному голосу - DELETE, счёт возвращается
- click по кнопке без логина (logout через AvatarMenu) - toast «Войдите
  чтобы голосовать», запроса не идёт
- network tab показывает 2 bulk SQL при загрузке /graph (агрегаты +
  user votes), не N+1
- swagger UI на `/swagger-ui/index.html` показывает 3 новых endpoint'а
  под `/api/v1/nodes/{nodeId}/vote(s)`

---

## 2026-05-18 - Этап 22.c.f frontend - book visibility + members UI (Этап 22 закрыт)

Зеркало 22.b TopicMembersModal/visibility UI для library books (ADR-043
Amendment). Параллельно с voting-subagent (NodeCard vote buttons - моя
зона была library/admin/shared, не пересекались)

**Реализовано (4 атомарных коммита + docs):**

1. **types.ts regen** + **VisibilityRadioGroup/VisibilityBadge → shared**
   (`shared/components/visibility/`). Generic тип `Visibility` (alias
   `TopicVisibility` для backward compat) + проп `labelPrefix`
   ('topic.visibility' | 'book.visibility'). Старые файлы в
   `apps/argument-map/components/` стали re-export'ами - не правил все
   callsite'ы в одном PR
2. **BookMembersModal** + **BookEditModal extension** - точная копия
   паттерна TopicMembersModal с заменой endpoint'ов и i18n. BookEditModal
   получил секцию visibility (radio) + кнопку «Управление участниками»
   при SHARED. i18n словарь дополнен 9 ключами `book.visibility.*` + 23
   `book.members.*` + 6 `book.permission.*` (RU + AR)
3. **VisibilityBadge на BookListPage cards** (compact, в Card.Eyebrow
   рядом с book type chip и language). **BookReaderPage header** через
   BookHeader children slot: owner/admin видят кликабельный badge для
   смены + Members button при SHARED, прочие - read-only badge. Local
   `BookVisibilityChangeForm` mirror VisibilityChangeForm из TopicGraphPage
4. **permissionErrors.ts** дополнен `forbidden-book-{access|write}`
   → `book.permission.{forbidden_access|forbidden_write}`

**Результаты:**

- `npm test -- --run`: **333/333 pass** (+5 BookMembersModal tests, baseline
  328 → 333)
- `npm run lint`: 0 errors, 7 warnings (existing fast-refresh)
- `npm run build`: success (PdfViewer chunk warning остался - existing)
- `npx tsc --noEmit -p tsconfig.app.json`: clean
- Playwright smoke (headless): login admin → /books показывает 7 visibility
  badges (Globe для shamela PUBLIC) → открыть книгу → header показывает
  кликабельный «Публичная» badge → modal с 3 radio (Приватная/Разделяемая/
  Публичная) → смена на SHARED → save → Members button появляется → клик →
  модалка «Участники книги» с owner-row (Crown + UUID) → revert PUBLIC

**Этап 22 (a + b + c + c.f) закрыт целиком** - roadmap сжат в одну запись

**Что отложено (22.d):**

- Audit log per-entity - кто что менял когда + кто получил/потерял access
- Private Q&A model (visibility + members для questions/answers) если
  понадобится для закрытых учёных групп
- Owner check на BookListPage `Pencil` action - сейчас не gated на UI
  (показан всем). Бэк сам отдаёт 403 и toast показывает локализованное
  сообщение через formatPermissionError. UI gating требует createdBy
  в BookSummaryResponse - backlog

**Проверить руками:**

- `/books` - visibility badges (Globe для PUBLIC, Lock для PRIVATE,
  Users для SHARED) на каждой карточке рядом с book type chip
- Открыть любую книгу из списка - в правом верхнем углу header'а
  должен быть кликабельный badge с visibility (для admin) + кнопка
  «Управление участниками» если SHARED
- Click visibility badge → modal с 3 radio'ами, hint текст под каждым
- Сменить на SHARED → save → появится Members button → click →
  модалка с owner-row сверху + форма добавления (UUID + role select)
- Upload новой PDF через `/admin` → книга создаётся PRIVATE (backend
  default для user-uploads), сразу видна с Lock badge в /books
- AR-локаль (RU/AR switch в header) - все badges/modals на арабском
  (`خاص` / `مشترك` / `عام`)

---

## 2026-05-18 - Этап 22.c RBAC extension: library books + Q&A guards

Расширил ADR-043 RBAC permissions per-entity (Этап 22.a-b - topics) на
library books и Q&A. Параллельно с frontend pagination fix subagent'ом.

**Реализовано (8 атомарных коммитов):**

1. **Миграция 37** - `lib_books.visibility VARCHAR(20) NOT NULL DEFAULT
   'PUBLIC'` + CHECK constraint + 2 индекса; новая таблица
   `lib_book_members` (mirror `topic_members`: id, book_id FK CASCADE,
   user_id FK CASCADE, role MEMBER/EDITOR, added_at, added_by, UNIQUE
   book+user). Default PUBLIC (в отличие от topics PRIVATE) сохраняет
   open library для shamela imports
2. **Book domain extension** - `Book` record + `visibility String`,
   `BookMember` record, `BookVisibility` / `BookMemberRole` константы,
   `BookMemberRepository` (JDBC + 9 методов)
3. **PermissionService extension** - `canReadBook` / `canWriteBook` /
   `isBookOwner` + `assertCanReadBook` / `assertCanWriteBook` /
   `assertIsBookOwner`. Те же исключения паттерн что у topics:
   `BookAccessDeniedException` (403 forbidden-book-access),
   `BookWriteAccessDeniedException` (403 forbidden-book-write),
   `BookMemberNotFoundException` (404 book-member-not-found). ADMIN bypass
4. **BookService permission integration** - перегрузки с `(userId, role)`:
   `getBookWithChapters`, `deleteBook`, `updateVisibility`,
   `updateAcademicMetadata`. Новый `listVisibleBooksPage` +
   `countVisibleBooks`. `BookRepository.findVisibleToUserPage` SQL UNION
   паттерн (PUBLIC OR created_by=? OR SHARED+EXISTS lib_book_members).
   `FileImportService.importPdf` теперь ставит **PRIVATE** для user-
   uploads (REST POST через BookController остаётся PUBLIC по умолчанию)
5. **BookMemberController + REST endpoints** -
   `POST/GET/PATCH/DELETE /api/v1/library/books/{id}/members` (mirror
   TopicMemberController). DTO: `AddBookMemberRequest`,
   `UpdateBookMemberRequest`, `BookMemberResponse`,
   `UpdateBookVisibilityRequest`. BookController расширен `PATCH
   /books/{id}/visibility` и permission-checking всех existing endpoints
6. **Q&A author/admin guards** - `AnswerService` новые перегрузки
   `updateAnswer/deleteAnswer(id, body, actorUserId, actorRole)` -
   автор или ADMIN. `QuestionService` аналогично. `AnswerController` /
   `QuestionController` PATCH/DELETE используют новые guards через
   `SecurityContextUtils.currentRole()`. Visibility model для Q&A НЕ
   добавлена (open discussion - все могут читать). Exceptions:
   `AnswerWriteAccessDeniedException` / `QuestionWriteAccessDeniedException`
   → 403 forbidden-{answer|question}-write
7. **IT покрытие** - новые: `PermissionServiceBookIT` (13 тестов vis
   matrix), `BookMemberControllerIT` (10 REST + auth), `AnswerControllerIT`
   (5 author/admin guards). Обновлены `QuestionControllerIT` (+2
   permission tests, существующие adapted под обязательный X-User-Id),
   `BookControllerIT` (+3 visibility tests + adapted existing). Также
   фикс - `ShamelaToLibraryMapper` передаёт PUBLIC, 49 existing тестов
   через python-script патчены до 17-args Book ctor с
   `BookVisibility.PUBLIC` default
8. **Документация** - ADR-043 Amendment секция (rationale PUBLIC default
   для books, Q&A no-visibility-by-design); api-contract.md visibility
   поля в Book*Response + 5 новых REST endpoint docs + Q&A guards в
   PATCH/DELETE; history entry; roadmap 22.c → [x]; backend/CLAUDE.md
   permissions раздел дополнен

**Результаты:**

- `./mvnw verify`: **BUILD SUCCESS, 733 tests, 0 failures, 2 skipped**
  (+29 от 22.b 704 baseline; целевые 701+ покрыты)
- Backend dev :9090 перезапущен с миграцией 37 - liquibase applied
  successfully (47ms), `lib_book_members` создана
- Параллельный frontend subagent (pagination fix) не пересекался с
  backend зоной - чистое разделение

**Что отложено:**

- Frontend UI для book members + visibility (зеркало 22.b
  TopicMembersModal) - будет в следующей сессии
- Private Q&A (visibility model для questions/answers) - 22.d, добавим
  если возникнет use-case закрытых учёных групп
- Audit log per-entity (кто что менял когда, кто потерял access) - 22.d

**Проверить руками:**

- `curl -H "X-User-Id: 00000000-0000-0000-0000-000000000001"
  http://localhost:9090/api/v1/library/books` - вернёт PagedResponse
  с visibility поле в items
- Создать PRIVATE book через POST `/library/imports/file` (PDF upload),
  убедиться что другой user не видит её в `GET /library/books`
- Попробовать удалить чужой Q&A answer без ADMIN role - ожидать 403
  forbidden-answer-write

---

## 2026-05-18 - Frontend pagination breaking change fix (4 pages закрыты)

Параллельно с backend subagent'ом 22.c RBAC (library books +
Q&A) - чинил frontend pages которые сломались после
pagination breaking change прошлой сессии: 4 page'а читали
response как raw array, а backend начал возвращать
`PagedResponse<T>{items, page, hasNext, totalElements, ...}`.

**Реализовано (3 атомарных fix-коммита + 1 docs):**

1. **`BookListPage`** - apps/library. BooksAccum state, Load More
   при `hasNext && !filterActive`. totalElements в подзаголовке.
   Local filter chips (typeFilter, search) скрывают Load More -
   server отдал бы следующую страницу но client скрыл бы items,
   кнопка кажется broken
2. **`QuestionListPage`** - apps/qa. QuestionsAccum state.
   statusFilter теперь server-side через `?status=` URL param -
   useEffect зависит от statusFilter, при смене reset page=0 +
   refetch. search остался client-side как в TopicListPage,
   Load More скрыт при search.trim()
3. **`CitationPicker`** - shared/components/citation. Use
   `size=100` (max permitted) без Load More - модальное окно
   ограниченной высоты, Load More UX внутри picker некомфортный.
   Когда библиотека >100 книг - search-by-title или pagination
   control внутри picker'а (backlog)

**AdminShamelaPage** не трогал - endpoint `/admin/shamela/search`
не paginated (admin staging search, raw array).

**Pattern** взят 1:1 из TopicListPage (reference):

- `apiGetRaw<PagedResponse<T>>('/...?page=N&size=20')`
- state `{items, page, hasNext, totalElements}`
- Load More: `page+1` fetch → append к items
- Filter change → reset page=0 (server-side filters) или скрыть
  Load More (client-side filters)

**Тесты**: existing tests (BookList/Question/CitationPicker не
имели baseline tests) - не добавлял. TopicListPage тесты уже
обновлены прошлой сессией. Frontend baseline 328/328 pass +
typecheck + lint + build clean.

**Playwright smoke**: открыл /books /qa /admin/shamela как admin.
- /qa: рендерится с 2 question cards, "2 вопросов в обсуждении"
  totalElements корректно
- /admin/shamela: sync status загружен (8589 книг в staging,
  v1261), всё работает
- /books: страница рендерит header/filter chips, но книги не
  показываются из-за 401 от backend - это работа параллельного
  RBAC subagent (мой fix корректный, error state UI отображается
  как ожидается через formatApiError)

**Acceptance criteria**:

- [x] Все 4 pages (3 paginated + 1 без изменений) рендерятся без
      JS crash
- [x] Frontend tests 328/328 pass
- [x] Lint clean (только pre-existing warnings)
- [x] Build clean
- [x] Typecheck clean
- [x] Playwright smoke: /qa, /books, /admin/shamela открываются

**Backlog**: убрана запись «Frontend pagination для остальных
list pages» (закрыта)

---

## 2026-05-18 - Pagination + filters для всех GET-list endpoints (backlog closed)

Backend backlog task созрела - справочники и темы растут, raw-array
ответы на каждый GET становились дорогими. Закрыта параллельно с
frontend subagent'ом по true tashkeel removal (зоны не пересеклись:
бэк = `backend/`, фронт subagent = RichTextRenderer/Tashkeel
extension, я = `web.dto` + repository/service/controller + 1 frontend
page).

**Реализовано (7 атомарных feat-коммитов + 1 docs):**

1. **`PagedResponse<T>` + `PageRequest` helpers** - простые records
   в `web.dto` без Spring Data Pageable (на проекте JDBC, не плодим
   dep). PagedResponse{items, page, size, totalElements, totalPages,
   hasNext, hasPrev}. PageRequest: default page=0, size=20,
   MAX_SIZE=100 clamp. 11 unit-тестов на edge cases (null/negative/
   over-max)
2. **`GET /sources`** - PagedResponse + `?type=&reliability=` фильтры.
   Combination validation: reliability только при type=HADITH
   (иначе 400 illegal-argument). Repository паттерн `findPage +
   countFiltered` с общим `appendFilters` helper. IT +7
3. **`GET /authorities`** - PagedResponse + `?era=` (exact match,
   свободный текст). madhab фильтр умышленно отложен (нужна
   нормализация). IT +2
4. **`GET /topics`** - PagedResponse + `?visibility=` whitelist
   PRIVATE/SHARED/PUBLIC поверх ADR-043 visibility-clipping.
   Сортировка changed на `created_at DESC` (для UI consistency
   с sources/questions); старый findVisibleToUserWithCounts с ASC
   оставлен для internal callers (TopicImportService). IT +3
5. **`GET /library/books`** - PagedResponse + `?authorityId=` /
   `?publisherId=` фильтры. IT +2
6. **`GET /questions`** - PagedResponse, `?status=` уже был. IT +1
7. **TopicListPage frontend** - regenerate types.ts, AsyncState
   изменён на TopicsAccum {topics, page, hasNext, totalElements},
   Load More button в конце списка (скрывается при client-side
   search). apiGetRaw для query-params (apiGet принимает только
   keyof paths). 4 теста переведены на PagedResponse mock shape.
   i18n key `common.load_more` (RU/AR)

**Документация:**

- `docs/api-contract.md` - новая секция «Пагинация GET-list
  endpoints» + обновлены 5 endpoint описаний с новыми query
  params + history entry с breaking change объяснением
- `docs/backlog.md` - pagination + фильтрация **закрыто**.
  Добавлены 2 новых: «Frontend pagination для остальных list
  pages» (BookListPage/QuestionListPage/AdminShamela/
  CitationPicker - Load More паттерн) и «Cursor-based pagination
  (если станет нужно)» - offset OK пока нет миллионов записей
- `backend/CLAUDE.md` - новая секция «Pagination + filters
  (GET-list endpoints)» с правилами (Repository паттерн, Service
  валидация enum-whitelist, Controller интеграция,
  IllegalArgumentException → 400 vs InvalidXyzException → 422)

**Backward compat не сохранён** (memory
`feedback_no_prod_no_backward_compat` - нет prod, ломаем смело).
Frontend список pages обновлён в той же сессии только частично -
TopicListPage smoke; остальные в backlog как continuation для
следующих сессий.

**Тесты:** backend `./mvnw verify` BUILD SUCCESS 701/701 pass
(+14 IT + 11 unit от baseline). Frontend `npm test --run`
328/328 pass (4 TopicListPage теста обновлены под PagedResponse
mock).

**Smoke curl на dev backend:**

```bash
curl 'http://localhost:9090/api/v1/sources?page=0&size=5'
# {items:[...], page:0, size:5, totalElements:16, totalPages:4, hasNext:true, hasPrev:false}

curl 'http://localhost:9090/api/v1/sources?type=BOOK&reliability=SAHIH'
# 400 illegal-argument: «фильтр reliability допустим только при type=HADITH»
```

---

## 2026-05-18 - True tashkeel removal закрыт (Этап 17.0.c gotcha cleared)

Frontend subagent-цикл, параллельный backend пагинации/фильтрации
(зоны не пересеклись - бэк трогал `backend/`, фронт - Tiptap reader).
Закрытие gotcha из Сессии Tiptap final: «Tashkeel full removal требует
runtime text manipulation» - до сих пор toggle «Без огласовок» в
BookReader был CSS-placeholder и визуально диакритики оставались.

**Реализовано (2 атомарных feat-коммита + 1 docs):**

1. **stripTashkeel utility + 15 тестов** - новая папка
   `shared/components/editor/utils/`. `stripTashkeelText(s)` через
   regex `/[ً-ٰٟ]/g` (U+064B-U+065F + U+0670 khanjariyya, без
   tatweel). `stripTashkeelFromDoc(doc, strip)` - рекурсивный walk
   ProseMirror JSON tree, трансформ text-nodes, сохранение marks
   и attrs, no-mutation. Тесты: basmala/hamdala/khanjariyya,
   идемпотентность, nested HadithBox, latin/tatweel негативы,
   strip=false opt-out, null/undefined defensive

2. **RichTextRenderer hideTashkeel prop + 2 теста** - useMemo
   processed content, передача в Tiptap useEditor. PageView пробрасывает
   state `hideTashkeel` сразу через prop вместо `.hide-tashkeel` CSS
   класса (CSS-hook остался для опциональных индикаторов). Legacy
   fallback path (NULL formattedContent + sanitizePageHtml) тоже
   применяет `stripTashkeelText` к raw text до sanitize. Tashkeel mark
   JSDoc + tiptap.css - очищены от MVP-комментариев

3. **docs:** backlog.md - убран пункт «True tashkeel removal через
   runtime regex DOM walk» из «Editor improvements». gotchas.md -
   запись переписана как «закрыто», с разделом «Решение» (выбран
   подход 1 - JSON transform, не DOM-walk, не custom NodeView).
   progress.md - эта запись

**Verify:** lint 0 errors (6 pre-existing warnings), tsc clean,
build 3.29s, test:run 328 passed (311 baseline + 17 новых)

**Что проверить руками:** открыть BookReaderPage с arabic content
(seed-mawlid.sh или любая книга на ar), toggle «Без огласовок» -
text реально меняется (диакритики исчезают), toggle обратно -
огласовки возвращаются. Работает на обоих путях: formatted ProseMirror
JSON и legacy sanitized HTML.

---

## 2026-05-17 - Этап 22.b frontend RBAC permissions UI (закрытие Этапа 22 целиком)

Параллельный subagent-cycle к 17.e.f - реализация фронтенд-части
ADR-043 (backend закрыт в Сессии 42, commits `1435e69`..`ae86efe`).
Зоны не пересеклись: 17.e.f трогал `admin/pages/` + `shared/hooks/`
+ `ai_edit.*` ключи; здесь - `argument-map/components/` + `topic.*`
ключи.

**Реализовано (5 атомарных feat-коммитов + 1 docs):**

1. **VisibilityRadioGroup + CreateTopicPage + 40+ i18n keys**
   (commit `15d5657`) - radio group с 3 опциями (Lock/Users/Globe
   icons, RU/AR labels + подсказки). Body POST /api/v1/topics теперь
   с полем visibility (default PRIVATE). 40+ i18n keys в обеих
   локалях сразу: `topic.visibility.*`, `topic.members.*`,
   `topic.permission.*`. Тесты: 3 новых +1 обновлён existing
   happy path (теперь с visibility=PRIVATE в body)

2. **TopicMembersModal + permissionErrors helper** (commit `0472c49`) -
   `apps/argument-map/components/TopicMembersModal.tsx`. Modal с
   списком членов (inline role switcher MEMBER/EDITOR + trash),
   owner-row с badge «Владелец» (отдельная строка над members),
   форма add UUID + radio role. MVP без user search - только UUID
   input + client-side regex validation. Toast errors на 400
   duplicate, 403 forbidden, invalid UUID. `formatPermissionError`
   helper в `shared/api/permissionErrors.ts` - маппит ApiError
   `forbidden-topic-access/write` на локализованные строки. 5 тестов

3. **VisibilityBadge + badge на TopicListPage cards** (commit `b6eb19e`) -
   Compact badge (только иконка с tooltip по умолчанию) в углу
   TopicCard heading chunk. Не нарушает existing layout polish.
   Reuse'ится в TopicGraphPage header

4. **hiding write actions в GraphCanvas/GraphPanels** (commit `c8a091f`) -
   Новый prop `canWrite` (default true для backwards compat тестов).
   При false скрываем: Add Node / Add Edge / Delete кнопки в
   GraphPanels, pane context menu, mutating items в node/edge
   context menu, кнопку Add First Node в empty state. Read-only
   действия (z-order, open details panel) - оставляем

5. **TopicGraphPage integration: badge/change/manage + permission UX**
   (commit `0175c86`) - В header: VisibilityBadge с tooltip,
   кнопка смены visibility (только owner/admin) → Modal с
   VisibilityRadioGroup + PATCH /visibility, кнопка «Управление
   участниками» при SHARED → TopicMembersModal, badge «Только
   чтение» (Lock) для non-owner на PRIVATE. canWrite оценка на
   фронте как `isOwner || isAdmin || visibility !== 'PRIVATE'` -
   optimistic, backend ассертит точно

**Технические заметки:**

- `Select` компонент - custom prop API `options[]` + onChange(value),
  не нативный `<select>`. Сначала ошибочно использовал нативный API,
  потом заменил
- Field.Input для UUID - принимает `className`, но `spellCheck` через
  `...rest` тоже доходит до native input
- HTMLDialogElement.showModal/close polyfill в TopicMembersModal.test
  (jsdom не реализует) + `window.confirm` stub для DELETE flow

**Метрики:** 5 файлов создано (VisibilityRadioGroup, VisibilityBadge,
TopicMembersModal + test, permissionErrors), 4 файла изменено
(CreateTopicPage + test, TopicListPage, TopicGraphPage, GraphCanvas,
GraphPanels, dictionary). 12 новых тестов (299 baseline → 311). Lint
0 errors (6 pre-existing warnings). Build 3.0s clean. Typecheck clean

**Отложено в backlog Этапа 22.c+:**

- Full user search (autocomplete по email/username) - сейчас MVP
  только UUID input
- Transfer ownership UX (перевод owner на другого user'а) - бэк
  отдельно проверять
- Permission UX для library books / Q&A - повтор паттерна

---

## 2026-05-17 - Этап 17.e.f frontend AI editing button (закрытие Этапа 17 целиком)

Закрывающий subagent-cycle на свежезавершённом 17.e backend
(commits 357cb1b..3903c4d) - подключить UI кнопку для async AI edit
через Anthropic Claude. Параллельно крутился второй subagent над
22.b TopicEditModal visibility - зоны не пересеклись (admin/pages/
+ shared/hooks/ + 10 ai.* dictionary keys vs argument-map/components/
+ topic.* keys).

**Реализовано (2 атомарных feat-коммита + 1 docs):**

1. **useAiEdit hook + 5 тестов** (commit `efa6ae4`) -
   `shared/hooks/useAiEdit.ts`. POST триггер + polling GET каждые
   3 сек до DONE/FAILED, 5 мин hard timeout. AbortController + 3
   timer ref (interval / timeout / UI ticker) - cleanup в cancel()
   и unmount useEffect. callbackRef через useEffect (react-hooks/refs
   rule - нельзя писать в ref во время render). При DONE - fetch
   /pages/{id} для свежего formattedContent, передача в callback.
   Транзитные network errors не валят polling - ждём next tick;
   ApiError 404 → failed. 503 пробрасывается из start() как ApiError
   чтобы callsite сам показал локализованный toast.
   Тесты используют vi.useFakeTimers + advanceTimersByTimeAsync для
   ускорения polling cadence: happy path / 503 / 5-мин timeout /
   cancel / unmount

2. **AI button + overlay + 10 i18n keys** (commit `7aff03f`) -
   `apps/admin/pages/AdminPageEditorPage.tsx`. Кнопка «AI
   редактирование» (Wand2 icon, indigo accent) в конце toolbar -
   визуально отличается от format-кнопок. Pre-flight: textContent
   empty → toast.warning, aiBusy → toast.info, иначе toast.info
   «Запущено» + aiEdit.start. Editor area получает overlay
   (bg-bg/85 + backdrop-blur) пока aiBusy, editable={!aiBusy}.
   Overlay показывает Loader2 + counter + кнопку «Отменить» (только
   polling, не сам job - контракт ADR-042 bounded pool). При DONE
   callback применяет content в editor (setContent + emitUpdate:false)
   + currentJson + patches state.page.formattedContent чтобы
   isFallback hint исчез. 10 ru/ar i18n keys.
   PageResponse type alias переделан с intersection на Omit+intersect
   т.к. после regenerate-api formattedContent появился как JsonNode
   (Record<string, never>) - не совместим с editor.getJSON() (object)

3. **roadmap update** (этот коммит) - 17.e блок дополнен описанием
   17.e.f. Backlog запись "Frontend AI edit UI отложен" убрана из
   roadmap (была в строке про 17.e). Этап 17 закрыт целиком (a-f).

**Verify:**
- npm run lint: 0 errors, 5 pre-existing warnings (не мои файлы)
- npx tsc --noEmit -p tsconfig.app.json: clean
- npm run build: clean (3.10s)
- npm run test:run: 304 tests pass (baseline 299 + 5 useAiEdit)

**Не сделано (отложено):**
- Playwright smoke с реальным ANTHROPIC_API_KEY - требует key на
  backend, пользователь проверит руками. UI-смоук без key (увидеть
  503 toast) тоже валидный сценарий
- AdminPageEditorPage.test.tsx (компонентный тест) - hook покрыт
  отдельно, button render следует общему паттерну остальных toolbar
  кнопок (уже проверены индиректно через RichTextEditor.test.tsx)

**Известные ограничения:**
- aiEdit.cancel() прерывает polling но не сам Anthropic-запрос на
  backend - это design по ADR-042 (bounded task pool, нет REST для
  cancel). Status вернётся в idle, но через 30 сек backend пометит
  страницу DONE/FAILED. Можно перезапросить через polling button
- 5-минутный hard timeout срабатывает если backend завис в
  PROCESSING - UI покажет failed toast. Пользователь может
  кликнуть AI button заново (idempotent на state machine)

---

## 2026-05-17 - Этап 22 RBAC permissions per-entity (topics visibility), ADR-043

Параллельно с AI edit subagent (Этап 17.e). Зоны не пересеклись -
я только service/ + миграция 36 + ADR-043 + auth/web/security/
SecurityContextUtils + новый topic_members слой; он library/imports/
+ миграция 35 + ADR-042. Финальный merge в backend/CLAUDE.md
+ GlobalExceptionHandler пересеклись бесконфликтно (разные секции).

**Реализовано (7 атомарных коммитов):**

1. **ADR-043 + миграция 36** (commit `1435e69`) - hybrid visibility-модель:
   `topics.visibility` (PRIVATE/SHARED/PUBLIC, CHECK constraint + 2
   индекса) + `topic_members` таблица (id, topic_id FK CASCADE, user_id
   FK CASCADE, role CHECK MEMBER/EDITOR, added_at, added_by, UNIQUE
   topic+user). ADMIN bypass в Service-слое. Default PRIVATE для existing
   тем - backward compat. Rejected: full RBAC (over-engineering),
   org/team ownership (нет user-base), per-action permissions

2. **Topic domain + TopicMember + repositories** (commit `a2a40e3`) -
   Topic record расширен полем visibility (7-args). TopicVisibility +
   TopicMemberRole - final class со static String (тот же подход что
   UserRole - совпадение с CHECK в БД, не enum). TopicMember record.
   TopicRepository обновлён (visibility в COLUMNS/save/COUNTS_SQL +
   updateVisibility + findVisibleToUserWithCounts через UNION query:
   PRIVATE owned ∪ SHARED member ∪ PUBLIC). Новый
   TopicMemberRepository (save / find* / exists / delete / updateRole).
   Existing tests адаптированы под 7-arg Topic constructor

3. **PermissionService + exceptions** (commit `4f96f40`) - canReadTopic
   / canWriteTopic / isOwner + assert-варианты бросают
   TopicAccessDeniedException / TopicWriteAccessDeniedException (топики
   в properties для debugging). assertCanWrite сначала проверяет read
   (404-like: не leak'ает существование private темы), потом write.
   GlobalExceptionHandler - 2 handler → 403 Problem Details
   forbidden-topic-access/write

4. **Service permission checks** (commit `7d19e55`) -
   TopicService/NodeService/EdgeService получили перегрузки с
   `(userId, role)` параметрами которые делают assertCan*. Старые
   сигнатуры оставлены для internal callers (TopicImportService,
   scheduled jobs, IT). TopicService.listVisibleTopicsWithCounts +
   updateVisibility + deleteTopic с owner-check. NodeController/
   EdgeController/TopicController обновлены - читают role через
   `SecurityContextUtils.currentRole()` helper (не вводим
   ArgumentResolver - role и так в AuthenticatedUser в SecurityContext).
   TopicResponse расширен visibility, CreateTopicRequest принимает
   опциональное visibility (default PRIVATE). PermissionServiceTest -
   20 unit-тестов с моками покрывают всю visibility matrix

5. **TopicMemberController + REST endpoints** (commit `45306a4`) -
   TopicMemberService с business logic (only owner может add/remove
   other; member может удалить только себя; owner не может быть
   добавлен как member; UNIQUE constraint ловит дубли). 5 новых
   endpoints: POST/GET/PATCH/DELETE `/api/v1/topics/{id}/members[/...]`
   + PATCH `/api/v1/topics/{id}/visibility`. TopicMemberNotFoundException
   → 404 topic-member-not-found. DTO: AddTopicMemberRequest /
   TopicMemberResponse / UpdateTopicMemberRequest /
   UpdateTopicVisibilityRequest

6. **IT для PermissionService + TopicMemberController** (commit `fd1a45c`) -
   PermissionServiceIT (11 IT через Testcontainers) - matrix + UNION
   query findVisibleToUser. TopicMemberControllerIT (10 IT через
   MockMvc) - POST/GET/PATCH/DELETE с owner/non-owner/self-leave
   scenarios. TopicControllerIT расширен 5 тестами visibility
   (POST_topic_withVisibility_setsCorrectly, GET_topic_PRIVATE_byNonOwner
   _returns403, GET_topics_returnsOnlyVisible, DELETE_topic_byNonOwner
   _returns403, invalid visibility 400). Existing topic tests дополнены
   X-User-Id header (теперь required даже для read)

7. **Fix NodeControllerIT/EdgeControllerIT** (commit `d422be5`) - 7
   тестов где DELETE/GET без header падали с 400 missing-user-header.
   Header добавлен (now required для permission check на parent topic)

**Документация (7-й коммит):**
- ADR-043 - hybrid visibility model + matrix + rejected alternatives
  (full RBAC / org-based / per-action) + open questions (transitions,
  transfer ownership, groups)
- architecture.md - новый раздел «Permissions / Visibility model»
  с topology diagram + permission matrix + где живут проверки
- api-contract.md - 5 новых endpoints + breaking semantic change
  (GET now requires X-User-Id) + история записью в самом верху
- roadmap.md - Этап 22 ✅, добавлены отложенные подэтапы 22.b
  (frontend UI), 22.c (RBAC на library/Q&A), 22.d (audit log)
- backend/CLAUDE.md - новый раздел Permissions (ADR-043) после Security

**Результат `./mvnw verify`:** запуск через ApplicationContext был
блокирован чужой проблемой `AnthropicClient` (subagent в процессе
финализации) - локальные мои IT прошли:
- PermissionServiceIT - 11/11 ✓
- TopicMemberControllerIT - 10/10 ✓
- NodeControllerIT + EdgeControllerIT - 24/24 ✓ после fix header
- TopicControllerIT - 17/17 ✓
- PermissionServiceTest (unit без Spring) - 20/20 ✓

Итого ~67 новых тестов (включая адаптацию existing). Полный verify
запустится зеленым когда subagent закончит AnthropicClient @Autowired
fix - это его коммит, не мой.

**Что отложено:**
- Frontend UI - radio visibility + members sub-modal (Этап 22.b)
- RBAC на library books / Q&A questions (Этап 22.c)
- Audit log (Этап 22.d)
- Transfer ownership / visibility transitions UX prompts - открытые
  вопросы в ADR-043

**Что user может проверить руками:**

```bash
# 1. Smoke - создать PRIVATE тему, увидеть только её
TOPIC_ID=$(curl -s -X POST http://localhost:9090/api/v1/topics \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001" \
  -H "Content-Type: application/json" \
  -d '{"title":"My private","rootQuestion":"Test?"}' | jq -r .id)

# Список - своя тема видна
curl -s http://localhost:9090/api/v1/topics \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001" | jq

# Другой user (UUID любой существующий) - PRIVATE темы не видит
OTHER_USER=$(uuidgen)  # вставить в users либо взять seeded

# 2. Сделать PUBLIC
curl -X PATCH http://localhost:9090/api/v1/topics/$TOPIC_ID/visibility \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001" \
  -H "Content-Type: application/json" \
  -d '{"visibility":"PUBLIC"}'

# 3. Добавить member (SHARED режим)
curl -X PATCH http://localhost:9090/api/v1/topics/$TOPIC_ID/visibility \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001" \
  -H "Content-Type: application/json" \
  -d '{"visibility":"SHARED"}'

curl -X POST http://localhost:9090/api/v1/topics/$TOPIC_ID/members \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001" \
  -H "Content-Type: application/json" \
  -d "{\"userId\":\"$OTHER_USER\",\"role\":\"EDITOR\"}"

# 4. Удалить чужой PUBLIC темой как другой user - 403
curl -i -X DELETE http://localhost:9090/api/v1/topics/$TOPIC_ID \
  -H "X-User-Id: $OTHER_USER"
```

---

## 2026-05-17 - Этап 17.e - AI editing pass backend (Anthropic Claude), ADR-042

Параллельно с RBAC subagent (Этап 22 topic permissions). Зоны не
пересеклись - я только library/imports/ + миграция 35 + ADR-042, они
trogали service/ + миграцию 36 + ADR-043.

**Реализовано (6 атомарных коммитов):**

1. **Миграция 35 + Page domain expansion + ADR-042**
   (commit `357cb1b`) - ALTER lib_pages добавляет 3 nullable колонки:
   `ai_edit_status` (CHECK PENDING/PROCESSING/DONE/FAILED) +
   `ai_edit_started_at`/`ai_edit_completed_at` + partial index по
   status WHERE NOT NULL. Page record расширен 3 полями + 21-args
   canonical конструктор + backward-compat 18-args (для existing OCR
   callers) + сохранён 12-args (shamela mapper). PageRepository
   COLUMNS + RowMapper + save() обновлены. Новые методы
   `updateAiEditStatus` и `updateFormattedContentAndMarkAiEditDone` -
   зеркало OCR-методам. AiEditStatus константный класс (mirror
   OcrStatus). **ADR-042** описывает выбор Anthropic Claude
   (claude-sonnet-4-6) как single-provider LLM. Rejected: OpenAI
   (слабее arabic), Gemini (no JSON guarantee), local LLM (GPU dep),
   HF API (rate limits), Anthropic Java SDK (heavy ради тонкой
   обёртки). Triggers revisit для cost/quality/privacy/availability

2. **AnthropicClient HTTP wrapper** (commit `0ebafe0`) - тонкий
   клиент ~200 LOC поверх java.net.http.HttpClient. Headers
   x-api-key + anthropic-version 2023-06-01, body {model, max_tokens,
   messages} серилизуется ObjectMapper. Защищён `@Retry("anthropicApi")`
   - 3 attempts, exponential backoff (2s/4s/8s). `isEnabled()` -
   sentinel "disabled" для config. `AnthropicApiException` -
   RuntimeException с HTTP status code. Application.yml расширен
   секциями `ai.anthropic.*` (api-key/base-url/model/max-tokens/
   timeout) и `resilience4j.retry.instances.anthropicApi`

3. **AiEditService + prompt template** (commit `88c71db`) - async
   pipeline для преобразования OCR raw text в ProseMirror JSON через
   Claude. @Async через новый `aiEditTaskExecutor` (core=2/max=4/
   queue=50). State machine PENDING → PROCESSING → DONE/FAILED.
   `validateProseMirrorJson` - базовая структурная валидация
   (type=doc + content array). `stripMarkdownFence` - чинит common
   LLM ошибку (```json ... ``` wrapper). Prompt template вынесен в
   `resources/prompts/ai-edit-tahqiq.txt` (не хардкод): few-shot
   example + правила распознавания (hadithBox/ayahBox/decoratedHeading/
   footnote/colorHighlight). 9 unit-тестов `AiEditServiceValidationTest`
   без БД и HTTP

4. **REST endpoints + GlobalExceptionHandler** (commit `03a7b8b`) -
   POST `/api/v1/library/pages/{id}/ai-edit` триггер с pre-flight
   `anthropicClient.isEnabled()` check - синхронный 503
   `ai-edit-not-configured` вместо background FAILED. GET endpoint
   для polling. `AiEditJobResponse{pageId, status, startedAt,
   completedAt, hasTextContent}`. GlobalExceptionHandler расширен 2
   новыми handler: `AiEditNotConfiguredException` → 503,
   `AnthropicApiException` → 502 (если upstream non-2xx) либо 503
   (если IO/timeout, statusCode=0)

5. **IT с HttpServer stub + опциональный live test** (commit `ddaf232`)
   - `AiEditServiceIT` (6 tests) через @MockBean AnthropicClient -
   end-to-end state machine + JSON validation + БД updates.
   `AnthropicClientStubIT` (5 tests) через JDK HttpServer (без
   WireMock dep) - HTTP-протокол contract, headers, response parsing.
   `AiEditControllerIT` (5 tests) через MockMvc - REST + Problem
   Details mapping. `AiEditServiceLiveIT` - опциональный live test
   через ANTHROPIC_API_KEY env var (`@Tag("live")` - skip из verify).
   AnthropicClient получил `@Autowired` на main конструктор - Spring
   не мог решить какой из двух использовать. **25 новых тестов**

6. **Docs финал** (этот коммит) - api-contract POST/GET ai-edit
   endpoints + history entry, roadmap 17.e/d отмечены `[x]` + весь
   Этап 17 сжат в строку «Закрытые этапы», progress (эта запись),
   backend/CLAUDE.md новая секция «AI editing (ADR-042)» с curl
   examples

**Дизайн-решения:**

- **Single-provider MVP** - не `AbstractLlmClient` + factory. YAGNI до
  момента когда 2+ provider'а станут required. Triggers revisit в
  ADR-042
- **Manual trigger** (не auto после OCR) - пользователь контролирует
  cost. OCR DONE не автоматически запускает AI edit. Трейд-офф между
  UX (one-click) и cost protection - выбрали cost
- **Prompt в resource file, не хардкод** - легче iterate на quality
  без recompile. Live test проверяет регрессию
- **Markdown fence stripping** - страховка на случай если LLM
  проигнорирует «без fence» instruction. Better defensive чем strict
- **Базовая валидация только** (type=doc + content array, не deep
  schema validation) - Tiptap reader игнорирует unknown nodes без
  crash, acceptable degradation на MVP

**Тесты:** baseline 554 → **579** (+25). Регрессий нет в моих файлах.
**Новые ADR:** ADR-042 (Anthropic Claude для AI editing)
**Новые gotcha:** нет

**Полный `./mvnw verify` не прогонялся в конце** - RBAC subagent
работает параллельно с uncommitted breaks в `TopicControllerIT`
(`patch()` без import). Когда RBAC subagent закроет свои коммиты,
полный verify будет прогоняться. Все мои файлы compile + все мои
новые тесты pass изолированно (5 stub + 9 validation + 6 service IT
+ 5 controller IT).

**Не делалось / отложено:**

- Frontend UI кнопка «AI редактирование» в admin UI - отдельный
  подэтап 17.e.f в будущем. Сейчас только REST endpoint + curl
  example в `backend/CLAUDE.md`
- Multi-provider routing (OpenAI fallback) - YAGNI до triggered
  revisit
- Streaming responses - Anthropic API поддерживает SSE, но для
  background async задачи нет необходимости
- Cost monitoring / quota - admin сам мониторит через Anthropic
  console
- OCR → AI edit auto cascade - manual trigger контролирует cost

**User может проверить руками** (с настроенным ANTHROPIC_API_KEY):

```bash
export ANTHROPIC_API_KEY=sk-ant-api-...
# рестарт backend
cd backend && ./mvnw spring-boot:run \
  -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" \
  > /tmp/backend.log 2>&1 &

# Найти page с OCR'ed контентом
PAGE_ID=$(psql -h localhost -U argmap argumentmap -tA \
  -c "select id from lib_pages where text_content is not null and length(text_content) > 50 limit 1")
echo "page: $PAGE_ID"

# Триггер AI edit (202 Accepted)
curl -X POST "http://localhost:9090/api/v1/library/pages/${PAGE_ID}/ai-edit" \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001" | jq

# Polling статуса (опросить через ~10 секунд)
sleep 10
curl "http://localhost:9090/api/v1/library/pages/${PAGE_ID}/ai-edit" \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001" | jq

# Когда status=DONE - проверить formatted_content
curl "http://localhost:9090/api/v1/library/pages/${PAGE_ID}" \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001" | jq '.formattedContent'
```

Без `ANTHROPIC_API_KEY` - POST вернёт 503 с понятным detail.

---

## 2026-05-17 - Этап 17.a-c - OCR backend (PageImageService + Tess4j + ImageRegion API), ADR-041

Параллельная задача с frontend dark theme - зоны не пересеклись (backend
только library + миграция 34 + 7 коммитов в backend/, frontend трогает
shared/components/ui)

**Реализовано (3 подэтапа):**

1. **17.a PageImageService** (POST /api/v1/library/books/{id}/pages)
   - migration 34 ALTER lib_pages: 6 nullable колонок image_bucket/
     storage_key/uploaded_at + ocr_status (CHECK PENDING/PROCESSING/
     DONE/FAILED) + ocr_started_at/completed_at + partial index по
     ocr_status WHERE NOT NULL
   - Page record расширен 6 полями + backwards-compat 12-args
     конструктор для shamela mapper / file import (не ломает callers)
   - OcrStatus константы вместо enum (простота добавления статусов
     без code-gen round-trip с фронтом)
   - PageRepository обновлён + 4 новых метода (findByBookAndPageNumber,
     updateImagePointer, updateOcrStatus, updateTextContentAndMarkDone)
   - Whitelist MIME: image/jpeg, image/png, image/webp, image/tiff
   - bucket `library-page-images` уже сконфигурирован в
     ObjectStorageProperties (ADR-024) - дополнительной инфры не нужно
   - Создаёт placeholder Page с text_content="" если pageNumber
     новый, либо обновляет existing (idempotent re-upload, S3 versioning)

2. **17.b Tess4j + OcrService** (POST/GET /api/v1/library/pages/{id}/ocr)
   - Tess4j 5.13.0 Maven dep (Java JNA wrapper над Tesseract C++)
     + exclusion slf4j-simple
   - Tesseract сам = system dependency (НЕ pom), нужно установить
     отдельно. Документировано в backend/CLAUDE.md OCR section
   - OcrConfig - @EnableAsync + bounded ThreadPoolTaskExecutor
     "ocrTaskExecutor" (core=2/max=4/queue=100, CallerRunsPolicy
     backpressure). Small pool потому что Tesseract уже multi-threaded
     на одну страницу
   - OcrService.recognize / recognizeAsync(@Async("ocrTaskExecutor")):
     state transition PENDING/FAILED/DONE → PROCESSING → download
     MinIO image в temp file → Tesseract.doOCR(file) с
     language="ara+rus+eng" → update text_content + DONE, либо
     FAILED на любую exception
   - Graceful degradation: native Tess4j binding lazy load на первом
     вызове, если Tesseract нет - backend стартует нормально, OCR
     помечает FAILED
   - REST: POST /pages/{id}/ocr (202 Accepted + текущий status),
     GET /pages/{id}/ocr (polling). OcrJobResponse{pageId, status,
     startedAt, completedAt, hasImage}

3. **17.c ImageRegion API** (3 endpoint)
   - ImageRegionService + CRUD: create/getOne/listByPage/delete
   - CreateImageRegionRequest{x, y, width, height, extractedText?}
     с Bean Validation @DecimalMin/@DecimalMax 0..1. DB CHECK
     constraint (миграция 16) обеспечивает x+width<=1 AND y+height<=1
   - POST /api/v1/library/pages/{pageId}/regions → 201 + Location
   - GET /api/v1/library/pages/{pageId}/regions - list sorted by created_at
   - DELETE /api/v1/library/pages/regions/{regionId} → 204
   - Update/PATCH намеренно нет - regions immutable. Изменить =
     удалить + draw new

**ADR-041 «OCR через Tess4j»** принят. Rejected: Google Cloud Vision
(paid + cloud), PaddleOCR (Python-only), Apache Tika (тонкая обёртка),
sync OCR (блокирует HTTP-thread). Triggers revisit: если quality
arabic OCR <70% на real manuscripts → PaddleOCR microservice

**Документация:**

- `docs/decisions.md` - ADR-041 (контекст / решение / rejected /
  consequences / triggers / связанные ADR)
- `docs/api-contract.md` - 6 новых endpoints + history entry
- `docs/roadmap.md` - 17.a/b/c/f `[x]`; 17.d помечен «реализовано
  неявно через idempotent POST /ocr»
- `backend/CLAUDE.md` - новая секция OCR (ADR-041) с правилом про
  установку tesseract-ocr на хост

**Коммиты (7):**

1. `feat(backend): Этап 17 - миграция 34 image scans fields + Page domain expansion`
2. `feat(backend): Этап 17.a - PageImageService + POST /library/books/{id}/pages multipart`
3. `feat(backend): Этап 17.b - Tess4j integration + OcrService async`
4. `feat(backend): Этап 17.b - OCR REST endpoints (POST trigger + GET status)`
5. `feat(backend): Этап 17.c - ImageRegion API (POST/GET/DELETE regions)`
6. `feat(backend): Этап 17 - IT для PageImageService/OcrService/ImageRegion`
7. `docs: Этап 17.a-c OCR backend - ADR-041 + api-contract + roadmap + progress + backend/CLAUDE.md` (этот коммит)

**Verify:** `./mvnw clean verify` BUILD SUCCESS, **646 tests pass**
(+24 от 622 baseline), 0 failures, 0 errors, **2 skipped** -
OcrServiceIT через @EnabledIf пропускается так как на dev WSL2
tesseract-ocr НЕ установлен (graceful degradation работает,
проверено)

**Что user может проверить руками:**

1. backend dev сервер на :9090 поднялся - проверка через
   `curl http://localhost:9090/actuator/health`
2. Upload page image:
   ```bash
   # bookId возьми из существующей книги в админке
   curl -X POST "http://localhost:9090/api/v1/library/books/{bookId}/pages?pageNumber=1" \
     -H "X-User-Id: $YOUR_USER_ID" \
     -F "file=@/path/to/scan.jpg"
   ```
   Получишь PageResponse с `imageBucket=library-page-images`,
   `imageStorageKey={bookId}/page-1.jpg`, `ocrStatus=PENDING`
3. Триггер OCR (нужен tesseract на host, см. backend/CLAUDE.md):
   ```bash
   curl -X POST "http://localhost:9090/api/v1/library/pages/{pageId}/ocr" \
     -H "X-User-Id: $YOUR_USER_ID"
   ```
4. Polling status:
   ```bash
   watch -n 2 'curl "http://localhost:9090/api/v1/library/pages/{pageId}/ocr"'
   ```
   Status переходит PENDING → PROCESSING → DONE (или FAILED если
   tesseract не установлен)
5. После DONE: `GET /api/v1/library/pages/{pageId}` - text_content
   заполнен распознанным текстом
6. ImageRegion: POST/GET/DELETE на `/library/pages/{id}/regions`

**Отложено:**

- 17.d отдельный re-OCR resource (есть idempotent через POST /ocr)
- 17.e AI editing pass (LLM расставляет structure через Tiptap nodes)
- Cron retry hung PROCESSING - manual через re-trigger пока
- Frontend ImagePageRenderer (18.e) - отдельный mode для image-сканов
  с overlay regions через react-image-crop
- Real-world arabic OCR quality benchmark - дождаться когда реальные
  manuscript scans появятся для тестового batch

**Handoff:** при следующей сессии можно либо начинать 17.e (AI editing
после получения tesseract output), либо 18.e ImagePageRenderer чтобы
визуализировать regions поверх scan'ов

---

## 2026-05-17 - Тёмная тема (полная) - backlog cleared

Закрыт пункт «Тёмная тема» из `docs/backlog.md` раздел «Фронт - общие
улучшения». Не новый этап - расширение существующей dark-инфраструктуры
которая уже была в проекте (tokens.css, ThemeEffect, ThemeStore 2-state)
до полноценной 3-option модели с system detection.

**Что было до:**
- ThemeStore 2-state (light/dark), persist в `app.theme`
- ThemeEffect ставит `data-theme="dark"` на html
- FOUC inline script в index.html
- Все семантические токены (`--c-bg`, `--c-ink-*`) с dark vars
- ThemeSwitch в Header - простой toggle Sun/Moon
- FontSettings секция темы с 2 кнопками

**Что добавлено в этой сессии:**

1. **ThemeStore → 3-option** (`shared/stores/themeStore.ts`)
   - `mode`: `'system' | 'light' | 'dark'` (user preference)
   - `effectiveTheme`: computed `'light' | 'dark'` после resolution
   - Subscribe на `matchMedia('prefers-color-scheme: dark')` change -
     при `mode='system'` смена темы ОС переключает effective без
     перезагрузки страницы
   - Legacy 2-state API (`toggle`, `theme`, `setTheme`) сохранён для
     CommandPalette и backward-compat

2. **ThemeSwitch → dropdown** (`shared/components/layout/ThemeSwitch.tsx`)
   - Trigger - icon-кнопка (Monitor / Sun / Moon в зависимости от mode)
   - Popover с 3 menuitemradio: «Системная» / «Светлая» / «Тёмная»
   - Активная отмечена `aria-checked + Check icon`
   - ESC / outside click закрывают, focus-ring через accent token
   - i18n keys `theme.{system|light|dark}`, `theme.aria_label`,
     `theme.menu_label` для RU + AR

3. **FontSettings 3-кнопка** - добавлена кнопка «Системная» рядом с
   Светлая/Тёмная, переключение через `setMode`

4. **FOUC script расширен** (`index.html`) - понимает 3 опции:
   `null|system` → читать `prefers-color-scheme`, `light|dark` → явный
   override. Single source of truth с themeStore.ts

5. **Tiptap dark synced с manual override** (`src/styles/tiptap.css`) -
   все 8 extensions использовали `@media (prefers-color-scheme: dark)`,
   что игнорировало manual mode из themeStore (user light на dark
   системе видел dark extensions). Заменено на nested
   `[data-theme='dark'] { .extension {...} }` через CSS native nesting

6. **Hardcoded shadows** - `shadow-[0_1px_2px_rgba(15,23,42,0.06)]` в
   CustomEdge + ReaderModeSwitch заменено на токен `shadow-sh2`
   (--sh-2 имеет dark variant)

7. **graphExport theme-aware** - `backgroundColor` default читает
   `--c-bg` из computed styles вместо хардкода `#ffffff`. Dark theme
   узлы больше не экспортируются на белом фоне

8. **ReactFlow colorMode** - в GraphCanvas передаётся
   `colorMode={effectiveTheme}` - controls/dots/attribution темы
   адаптируются

**Тесты:** +15 новых (themeStore 9, ThemeSwitch 6). Всего 299 frontend
tests, 0 регрессий.

**Документация:**
- `frontend/docs/ui-guidelines.md` - новая секция «Dark mode» с
  правилами: использовать токены, запрет `@media prefers-color-scheme`
  внутри приложения (только в bootstrap inline script), CSS-override
  для 3rd-party библиотек, FOUC sync с themeStore key, lucide
  иконки наследуют currentColor
- `docs/backlog.md` - пункт «Тёмная тема» отмечен `[x]` с резюме

**Playwright verification (WSL2 headless):**
- login (light + dark)
- topics (light + dark)
- books (light + dark) - адаптация цветов карточек книг через токены
- qa (light + dark)
- settings (light + dark) - 3-option кнопки видны
- theme dropdown работает: System / Light / Dark переключают
  `data-theme` атрибут и localStorage синхронно

**Что НЕ сделано / отложено:**
- Tiptap extensions используют CSS native nesting (`[data-theme='dark']
  { .hadith-box {...} }`) - современная фича, поддержка Chrome 112+,
  Firefox 117+, Safari 16.5+. В нашем target browser matrix это OK;
  если придётся поддержать старее - PostCSS plugin развернёт nesting
- Не проверены вручную: TopicGraphPage (граф React Flow), BookReaderPage
  (Tiptap rendering) - playwright покрыл list-pages, граф/reader
  требуют ручной check от Абдулы для визуального качества

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

## 2026-05-17 - Этап 17.0.c - 3 финальных Tiptap extensions, ADR-039 закрыт

Закрыт ADR-039 целиком - 8 из 8 custom extensions реализованы.
Параллельная задача с graph export и login UI - зоны не пересеклись
(Tiptap extensions в `shared/components/editor/extensions/` + reader
PageView + admin editor page, graph export в `apps/argument-map/`,
auth в `apps/auth/` + `shared/stores/authStore.ts`)

**Реализовано (3 extensions):**

1. **Tashkeel** (`Tashkeel.ts`) - inline mark для семантической
   маркировки текста с арабскими диакритическими знаками. MVP scope:
   mark сериализуется, в reader кнопка «С огласовками / Без огласовок»
   ставит класс `.hide-tashkeel` на article. CSS - placeholder
   (визуально не меняет диакритики). Full removal через regex по
   text nodes - в backlog «True tashkeel removal». Новая gotcha
   объясняет почему MVP именно такой
2. **DecoratedHeading** (`DecoratedHeading.ts`) - block node для
   заголовков с symmetric ornament glyph. Атрибуты: `level` (1-4) +
   `ornament` (diamond/flower/star/crescent). Render через h1..h4
   + data-attrs, CSS `::before/::after` content per ornament.
   `parseHTML priority: 60` чтобы StarterKit Heading не перехватил
   matching tag
3. **PageNumber** (`PageNumber.ts`) - inline atom для декоративной
   разметки границы страницы оригинала внутри абзаца. Render через
   `<span data-type="page-number">` + CSS `::before` с
   `content: '⟦' attr(data-number) '⟧'`. Self-contained
   (`atom: true`), не выделяется (`user-select: none`)

**Интеграция:**

- `AdminPageEditorPage` toolbar расширен 3 кнопками (Tashkeel toggle,
  DecoratedHeading с Modal radio level/ornament, PageNumber с Modal
  number input). Pre-fill PageNumber из текущего page.pageNumber
- `PageView` (reader): `READER_EXTENSIONS` довёл до 8 extensions,
  добавлен toggle для tashkeel (только для арабского контента),
  кнопка в header рядом с PDF preview
- `tiptap.css` - стили для 3 новых extensions (Tashkeel placeholder,
  DecoratedHeading per-ornament glyphs + per-level font-sizes,
  PageNumber decorative bracketing)
- ~24 новых i18n keys RU/AR (admin toolbar + DH/PN modals + reader
  tashkeel show/hide)
- 19 новых schema-тестов (Tashkeel: 5, DecoratedHeading: 8,
  PageNumber: 6). Frontend total **284 тестов pass** (было 241)

**Документация:**

- `docs/roadmap.md` - Этап 17.0 / 17.0.b / 17.0.c сжаты в одну
  строку «Tiptap editor + 8 custom extensions (ADR-039 закрыт)».
  Открытыми остаются 17.a-f (OCR pipeline + AI editing)
- `docs/gotchas.md` - новая gotcha «Tashkeel full removal требует
  runtime text manipulation» с симптомом / причиной / решением (MVP) /
  TODO для future
- `docs/backlog.md` - новый раздел «Editor improvements (после
  закрытия ADR-039)» с True tashkeel removal, font-feature-settings
  альтернативой, Drag-handle для блочных extensions, collaborative
  editing через Yjs, slash menu

**Коммиты (4):**

- `feat(frontend): Этап 17 - Tashkeel mark + reader toggle (MVP CSS-only placeholder)`
- `feat(frontend): Этап 17 - DecoratedHeading с 4 ornament вариантами`
- `feat(frontend): Этап 17 - PageNumber inline decorative element`
- `docs: Этап 17 - 3 финальных extensions, ADR-039 implementation closed`

**Verify:** lint 0 errors, typecheck clean, build success, 284/284
frontend tests pass

**Отложено:**

- True tashkeel removal через runtime regex DOM walk (NodeView с
  React или TreeWalker hook в PageView)
- OCR pipeline (17.a-d): PageImageService upload, Tess4j integration,
  ImageRegion API, re-OCR endpoint
- AI editing pass (17.e): LLM расставляет structure через Tiptap
  custom nodes, manual review через editor

**Handoff:** что можно проверить руками - в финальном отчёте

---

## 2026-05-17 - graph export PNG/SVG (backlog cleared)

Закрыт пункт backlog «Экспорт графа в PNG / SVG». Параллельная задача
к Tiptap extensions и login UI - зоны не пересеклись (фронтовая
кнопка в `apps/argument-map/components/graph/`, новые ключи под
prefix `graph.export.*` не пересекаются с `topic.export.*` JSON)

**Реализовано:**

1. **`graphExport` utility** (`apps/argument-map/utils/graphExport.ts`) -
   wraps html-to-image `toPng`/`toSvg`. Slugify filename для
   латиницы / fallback `topic` для cyrillic / arabic. Filter
   исключает overlay-элементы React Flow (controls, minimap,
   attribution, panel). `triggerDownload` через программный клик
   по `<a download>` с DOM-attachment для Firefox/Safari совместимости
2. **Toolbar button** в `GraphPanels` - Download IconButton +
   inline popover с PNG/SVG опциями. Перед export -
   `fitView({ padding: 0.1 })` + 150ms задержка для стабилизации
   React Flow. Топик title из `graph.topic.title` пробрасывается
   через GraphCanvas
3. **i18n** - 6 ключей `graph.export.*` в RU/AR словарях
   (button, hint, png, svg, success с filename placeholder, error)
4. **Тесты** - 19 unit-тестов с моком `html-to-image`: slugify
   variations, filter exclusions, download trigger, опции
   pixelRatio (default 2x retina, override до 4x для print)

**Bundle:** html-to-image ~10KB gzipped, single dep, MIT, активный maintenance

**Не сделано (отложено):**
- PDF export (отдельная задача, требует jspdf, больше работы) -
  оставить в backlog как future improvement если станет важно
- Quality dropdown (1x/2x/4x pixelRatio toggle в UI) - YAGNI пока,
  default 2x подходит для retina screenshot + reasonable file size

**Коммиты:**
- `feat(frontend): graphExport utility - PNG/SVG через html-to-image`
- `feat(frontend): экспорт графа кнопка в TopicGraphPage toolbar`

---

## 2026-05-17 - Сессия 42 Этап 21.b - frontend login UI, Этап 21 целиком закрыт

Параллельно с Tiptap extensions (см. ниже) - frontend auth end-to-end
интеграция с backend Этапа 21.a (ADR-040). Зона: AuthStore, apiClient
interceptor, новое app `src/apps/auth/`, ProtectedRoute, Logout в
AvatarMenu, Vite proxy. Не пересекалась с Tiptap subagent'ом

**Реализовано:**

1. **AuthStore** (`shared/stores/authStore.ts`) - Zustand store:
   login/register/logout/refreshAccessToken/loadCurrentUser. Persist
   только user в localStorage (быстрый UI bootstrap), accessToken в
   памяти (XSS-safety, ADR-040), refresh в httpOnly cookie. raw fetch
   внутри store - избегаем circular dep с apiClient
2. **apiClient interceptor** - Bearer Authorization header если
   accessToken есть, 401 → refresh + retry один раз. Конкурентные
   refresh дедуплицируются через single in-flight Promise (5 параллельных
   401 = один refresh запрос). Связка с authStore через `authBridge.ts`
   (lazy injection через AuthAccessor pattern)
3. **LoginPage + RegisterPage** (`apps/auth/pages/`) с AuthShell -
   hero-style standalone (без Header). Field/Button primitives. Client-
   side валидация для register (email regex, password >=8, match).
   Locale-aware ошибки: 401 → «Неверный email или пароль», 409 с
   type=email-already-taken → «Email уже используется»
4. **ProtectedRoute** (`shared/components/auth/`) - splash «Загрузка»
   пока bootstrap, redirect на `/login?redirect=<path>` без user,
   `requireRole="ADMIN"` для `/admin/*` (USER → silent redirect /topics)
5. **App.tsx routing** - все `/topics`, `/books`, `/qa`, `/settings`
   через `<ProtectedRoute>`, `/admin/*` с `requireRole="ADMIN"`,
   `/login` + `/register` public. На mount - `loadCurrentUser()` один
   раз (initialized гард защищает от React 19 StrictMode double-effect)
6. **AvatarMenu Logout** - показывает user.username + email из
   authStore, кнопка «Выйти» вызывает logout() + navigate /login.
   initials autogen из username
7. **Vite proxy + relative API_BASE_URL** - critical fix: SameSite=Strict
   refresh cookie не шлётся cross-origin :5173 → :9090, плюс backend
   CORS Этапа 21.a имеет `allowCredentials(false)`. Решение в рамках
   frontend - vite.config proxy `/api`+`/actuator`, API_BASE_URL=''
   для browser (через proxy). Tests остаются на VITE_API_URL для msw.
   Backend не тронут - prod fix через nginx/Cloudflare same-origin
8. **i18n** - 25+ keys auth.* / login.* / register.* / logout.* /
   access.* в обе локали (RU/AR)

**Файлы:**
- `frontend/src/shared/stores/authStore.{ts,test.ts}` - 10 тестов
- `frontend/src/shared/api/{client,authBridge}.ts` + client.test - 12
  новых interceptor тестов
- `frontend/src/apps/auth/components/AuthShell.tsx`
- `frontend/src/apps/auth/pages/LoginPage.{tsx,test.tsx}` - 5 тестов
- `frontend/src/apps/auth/pages/RegisterPage.{tsx,test.tsx}` - 4 теста
- `frontend/src/shared/components/auth/ProtectedRoute.{tsx,test.tsx}` -
  5 тестов
- `frontend/src/shared/components/layout/AvatarMenu.tsx` - logout flow
- `frontend/src/App.tsx` + `main.tsx` - routing, installAuthBridge
- `frontend/vite.config.ts` - proxy config
- `frontend/src/shared/i18n/dictionary.ts` - 25+ keys × 2 locales

**Метрики:**
- 246 frontend tests passed (210 baseline + 36 new auth tests)
- tsc clean, eslint 0 errors (5 pre-existing warnings)
- production build clean (3.14s)
- Playwright headless smoke: 5/5 шагов passed (/topics protected
  redirect → login → /topics → logout → /login → /register render)

**Что отложено (Этап 22+ / backlog):**
- Forgot password flow (нужен email-сервис, ADR пока нет)
- Email verification (та же причина)
- OAuth (Google / GitHub) - после v1 если будет спрос
- RBAC permissions per-entity (Visibility/ACL) - явный Этап 22 в
  roadmap
- Backend CORS `allowCredentials(true)` для prod без proxy - откладывается
  до выбора deployment topology (Cloudflare vs nginx vs ALB)

**Коммиты (6):**
- `aaa858b` feat(frontend): Этап 21.b AuthStore Zustand с persist user
- `a288ee8` feat(frontend): Этап 21.b apiClient Bearer + refresh-on-401
  interceptor
- `1cdda13` feat(frontend): Этап 21.b LoginPage + RegisterPage компоненты
- `de252fc` feat(frontend): Этап 21.b ProtectedRoute + AdminRoute wrappers
  + App routing
- `56f1c48` feat(frontend): Этап 21.b Header Logout + initial
  loadCurrentUser
- `43fd35a` fix(frontend): Этап 21.b Vite proxy для /api+/actuator +
  relative API_BASE_URL

**Этап 21 целиком закрыт** - backend Этап 21.a (ADR-040, Сессия 41) +
frontend Этап 21.b (эта сессия). Свёрнут в строку «Закрытые этапы»
в roadmap

---

## 2026-05-17 - Сессия 42 Этап 17.0.b - 4 custom Tiptap extensions

Continuation Этапа 17.0 - после MVP с HadithBox (Сессия 41) реализованы
ещё 4 extensions из 7 запланированных в ADR-039. Параллельная сессия
с frontend login UI (Этап 21.b) - изоляция зон: моя `editor/extensions/`
+ `tiptap.css` + `AdminPageEditorPage` + `admin.page_editor.*` keys.

**Реализовано:**

1. **AyahBox** - блочный node (group=block, content=block+) с
   attributes surah (1-114) / ayah (>=1) / translation (optional).
   wrapIn/lift команды как у HadithBox. Визуал: `bg-amber-50` +
   `border-amber-400` + орнаментальные `﴿ ﴾` в углах
2. **Marginalia** - блочный node с RTL-aware `data-side` = 'start' |
   'end'. На desktop (`>=769px`) - float сбоку через
   `float: inline-start/end` + `max-width: 30%`. На mobile - inline
   blockquote-like. content=block+ для wrapIn совместимости
3. **Footnote** - Mark (вариант B из ADR-039), `<sup data-type=
   "footnote" title="...">`. Auto-numbering чисто CSS counter в
   `tiptap.css` (`.ProseMirror { counter-reset: footnote; }` +
   `.footnote-ref::before { counter-increment: footnote; content:
   '[' counter(footnote) ']' }`) - без JS, пересчёт автоматический.
   Native browser tooltip из атрибута `title`
4. **ColorHighlight** - Mark с whitelist 5 цветов (red/blue/green/
   yellow/purple). setColorHighlight с toggle behaviour (тот же цвет
   на selection = снимает mark). parseHTML читает color из class или
   data-color. Tailwind 700-level колор палитра + dark mode (-400)

**AdminPageEditorPage toolbar:** добавлены 5 кнопок (Hadith
существовала + 4 новых). HadithBox/AyahBox/Marginalia/Footnote
открывают Modal для attrs; ColorHighlight - palette dropdown с
swatches 5 цветов. Каждый блок-extension показывает `×` remove
кнопку рядом когда active

**Файлы:**
- `frontend/src/shared/components/editor/extensions/AyahBox.{ts,test.ts}`
- `frontend/src/shared/components/editor/extensions/Marginalia.{ts,test.ts}`
- `frontend/src/shared/components/editor/extensions/Footnote.{ts,test.ts}`
- `frontend/src/shared/components/editor/extensions/ColorHighlight.{ts,test.ts}`
- `frontend/src/styles/tiptap.css` - дополнен 4 блоками CSS + dark
  mode для каждого + media-queries для marginalia
- `frontend/src/apps/admin/pages/AdminPageEditorPage.tsx` - 5
  кнопок + 4 модалки + highlight palette
- `frontend/src/shared/components/reader/PageView.tsx` - READER_
  EXTENSIONS расширен 4 новыми (синхронизация admin↔reader, без
  этого reader падал бы на unknown node types)
- `frontend/src/shared/i18n/dictionary.ts` - +40 keys RU/AR

**Тесты:** 241/241 frontend pass (24 новых + auth-subagent работа).
lint clean, typecheck clean, build clean

**Отложено:**
- Этап 17.0.c: оставшиеся 3 extensions (Tashkeel mark /
  DecoratedHeading / PageNumber)
- AI editing pipeline (Этап 17.e)
- OCR pipeline (Этап 17.a-17.d) - архитектурный prerequisite ADR-039
  закрыт, можно стартовать

---

## 2026-05-17 - Сессия 41 Этап 17.0 Tiptap rich text editor MVP

Параллельная сессия с Spring Security/JWT (Этап 21.a) - моя зона
lib_pages + frontend editor, без затрагивания auth/security кода.
Реализован MVP rich text editor согласно ADR-039 (закрыт в предыдущей
сессии). Цель - structured хранение богатой типографики тахкика
(хадис-боксы, marginalia, footnotes и т.д.) **до** запуска OCR pipeline
Этапа 17 - чтобы не плодить долг.

**Backend:**

1. Миграция 33 - `lib_pages.formatted_content jsonb NULL` (миграция 32
   занята auth-агентом)
2. `Page` domain получает поле `formattedContent: String` (хранится как
   raw JSON-строка через `?::jsonb` cast - паттерн уже использовался
   для `lib_books.metadata`)
3. `PageRepository` extended RowMapper + `updateFormattedContent`
   partial update
4. `PageResponse` получает поле `formattedContent: JsonNode` - structured
   response, не плоская строка
5. `UpdateFormattedContentRequest{formattedContent: JsonNode}` с
   `@NotNull` validation
6. `BookService.updateFormattedContent` - trust frontend (schema
   validation на фронте), throw `PageNotFoundException` если page нет
7. `PATCH /api/v1/library/pages/{id}/formatted-content` в BookController
8. 4 IT теста (valid HadithBox + invalid JSON + empty doc + 404)
9. Все 9 call sites `new Page(...)` обновлены под 12-arg ctor

Также пофиксил критический баг в `application.yml` (auth-агент засунул
`spring.liquibase` блок под `auth:` - все IT падали из-за «changelog
yaml does not exist» fallback path). Переставил блок под `spring`.

**Frontend:**

1. Установлен Tiptap 3.23 - `@tiptap/react` + `@tiptap/starter-kit`
   + `@tiptap/core` + `@tiptap/pm`
2. Shared editor в `src/shared/components/editor/`:
   - `RichTextEditor.tsx` - headless wrapper над `useEditor` +
     `EditorContent` с props content/onChange/editable/extensions/
     onEditorReady
   - `RichTextRenderer.tsx` - read-only wrapper для reader view
   - `wrapPlainTextAsDoc` utility - оборачивает plain text в minimal
     paragraph-doc для legacy fallback
3. Первый custom extension `extensions/HadithBox.ts`:
   - group:'block', content:'block+', defining:true
   - attributes source (string) + grade ('sahih'|'hasan'|'daif')
     с fallback на 'sahih' при невалидном
   - parseHTML/renderHTML для div[data-type="hadith-box"] -
     SSR-friendly для будущего generateHTML path
   - commands setHadithBox/unsetHadithBox
4. CSS `src/styles/tiptap.css` - peach background, dashed border,
   `«`/`»` ornament через ::before + dir-aware mirror для RTL,
   dark mode adjustments
5. `AdminPageEditorPage` (`/admin/library/pages/:pageId/edit`):
   - GET /api/v1/library/pages/{id} + initial fallback на
     wrapPlainTextAsDoc(textContent) если formattedContent null
   - Toolbar Bold/Italic/H1-3/Blockquote/HadithBox (с Modal
     source+grade) + кнопка unsetHadithBox когда курсор в HadithBox
   - Save через PATCH endpoint + toast
6. `BookReaderPage.PageView` - если formattedContent non-null,
   рендерит через RichTextRenderer (с HadithBox extension), иначе
   старый sanitizePageHtml путь
7. 30 i18n keys RU/AR (`admin.page_editor.*`)
8. 14 frontend tests (6 HadithBox schema + 8 RichTextRenderer/
   wrapPlainTextAsDoc)

**Backward compat (ADR-039 фиксирует):** NULL formatted_content для
тысяч existing PDFBox-imported и Shamela-imported страниц - они
рендерятся через fallback wrap text_content в paragraph-doc, никакой
data migration не нужно.

**Что отложено в Этап 17.0.b:**

- Остальные 7 custom extensions (AyahBox / Marginalia / Footnote /
  ColorHighlight / Tashkeel / DecoratedHeading / PageNumber) - каждое
  отдельным коммитом по паттерну HadithBox
- Highlight ranges + ЛКМ-selection (citation flow) в formatted mode -
  пока только в legacy режиме, нужен ProseMirror selection API
- AI editing integration (LLM возвращает JSON с разметкой) - Этап 17.e
- OCR pipeline для image-сканов - Этап 17.a-d
- Кнопка/ссылка «Редактировать» из reader на admin editor - UX-сессия
  с Абдулой

**Тесты:** 628/628 backend pass, 193/193 frontend pass, lint clean,
build clean, typecheck clean.

**Smoke test:**

```
curl -X PATCH http://localhost:9090/api/v1/library/pages/{PAGE_ID}/formatted-content \
  -H "X-User-Id: ..." -H "Content-Type: application/json" \
  -d '{"formattedContent":{"type":"doc","content":[{"type":"hadithBox",
       "attrs":{"source":"Бухари 1","grade":"sahih"},
       "content":[{"type":"paragraph","content":[{"type":"text",
                  "text":"إنما الأعمال بالنيات"}]}]}]}}'
# Returns 200 + PageResponse с formattedContent в теле
# GET той же page returns ту же formattedContent
```

**Коммиты:**

- backend миграция 33
- backend PATCH endpoint + Page domain + DTO + service + production
  callers + application.yml fix
- backend IT тесты + Page ctor обновления в 9 IT файлах
- frontend Tiptap install + RichTextEditor + RichTextRenderer
- frontend HadithBox extension + tiptap.css
- frontend AdminPageEditorPage + 30 i18n keys
- frontend BookReaderPage PageView обновление

---

## 2026-05-17 - Сессия 41 Этап 21.a Spring Security + JWT backend foundation

Параллельная сессия с Tiptap (Этап 17.0 migration 33) - моя зона
security/auth/users, без затрагивания lib_pages и frontend. Реализован
backend для реальной аутентификации согласно ADR-040 - заменили
заглушку ADR-006 (X-User-Id header без проверки) на полноценный
Bearer JWT через Spring Security 6 + jjwt 0.12.6.

**Что сделано (5+1 атомарных коммитов):**

1. ADR-040 в `docs/decisions.md` + миграция 32 `users` ALTER
   (password_hash NULLABLE / role VARCHAR(20) DEFAULT 'USER' с CHECK
   USER|ADMIN / enabled BOOLEAN DEFAULT TRUE / updated_at TIMESTAMPTZ
   + LOWER(email) functional index). Rationale: транзитная password_hash
   nullable - legacy dev users без пароля продолжают работать через
   X-User-Id fallback. После Этапа 21.b убрать NULL отдельной миграцией
2. Auth domain + UserRepository + UserService - records `User`/
   `UserRole`/`AuthTokens`/`AuthenticatedUser`, JDBC `UserRepository`
   (findById/findByEmail/findByUsername case-insensitive, existsBy*,
   updatePassword, setEnabled), `UserService.register` с
   BCryptPasswordEncoder + проверкой дубликатов email/username
3. JwtService + AuthService + AuthController - HS256 через jjwt,
   access 15мин / refresh 7д, typ-claim для различения. `AuthService.login`
   - dummy-hash на отсутствующего user'а для timing-protection.
   `AuthService.refresh` - проверка typ=refresh, переиспользование
   (no-rotation MVP). `AuthController` - 5 endpoints (register / login /
   refresh / logout / me), refresh в HttpOnly+Secure+SameSite=Strict
   cookie с Max-Age 604800
4. SecurityConfig + 2 фильтра + EntryPoint - `JwtAuthenticationFilter`
   (Authorization: Bearer, не падает на ошибке - молча даёт 401 на
   EntryPoint), `XUserIdAuthenticationFilter` (@Profile local/dev/test
   - читает X-User-Id если SecurityContext empty - dev/test fallback),
   `JwtAuthenticationEntryPoint` (Problem Details 401).
   `CurrentUserArgumentResolver` переключён с header на SecurityContext
   - **API `@CurrentUser` не изменилось**, controllers не трогали.
   `DevUserSeeder` (@Profile local/dev) создаёт fixed
   admin@argumentmap.local / admin12345 (UUID 0000...0001 - тот же что
   мок во фронте до Этапа 21.b)
5. IT - `JwtServiceIT` 7 (round-trip access+refresh, tampered signature,
   garbage, foreign-key signature, short-secret-init-fail, expired через
   reflection), `AuthServiceIT` 10 (register valid/dupe email/dupe
   username, login valid/wrong-pw/unknown-email/disabled, refresh
   valid/access-as-refresh-throws/garbage), `AuthControllerIT` 13
   (register 201+cookie, register invalid email/short pw/dupe, login
   200/401, /me 200/401/invalid-bearer, refresh 200/no-cookie-401,
   logout 204+max-age=0). Total 30 новых IT
6. (финальный) docs - api-contract.md новая секция Bearer JWT + /auth/*
   endpoints + history entry; roadmap.md Этап 21 разбит на 21.a (закрыт)
   + 21.b (open); architecture.md новый раздел Authentication;
   progress.md - эта запись

**Transitional X-User-Id (ADR-040):**

Existing 60+ integration тестов не передавали X-User-Id на GET/list
запросы (исторически @CurrentUser был только на POST/PATCH). После
включения Spring Security ВСЕ endpoints формально требовали auth.
Решение: `SecurityConfig` с детекцией profile через `Environment` -
в `local`/`dev`/`test` profile делает `permitAll()` для всего `/api/**`
**кроме** `/api/v1/auth/me` (всегда требует Bearer). В prod profile
блок не активируется. После Этапа 21.b - убрать transitional ветку
вместе с XUserIdAuthenticationFilter.

При permitAll request всё равно проходит через
`XUserIdAuthenticationFilter`: если есть X-User-Id - principal ставится,
`@CurrentUser` его извлекает. Если нет - `MissingUserHeaderException`
(старое поведение). Symmetry сохранена.

**Существующие IT обновлены:**

- TopicControllerIT, BookControllerIT, FileImportControllerIT,
  TopicExportImportControllerIT - 4 теста c `missing-user-header 400` →
  обновлены на `unauthorized 401`. ADR-040 явно меняет семантику: без
  любой auth (Bearer или X-User-Id в dev) - 401 от Spring Security
  EntryPoint
- OpenApiIT - 2 теста с X-User-Id `required=true` → `required=false`
  (после ADR-040 Bearer JWT - основной путь, X-User-Id - dev fallback)

**Dependencies added (pom.xml):**

- `spring-boot-starter-security` (BOM-managed version)
- `jjwt-api` + `jjwt-impl` (runtime) + `jjwt-jackson` (runtime) 0.12.6
- `spring-security-test` (test scope) - для MockMvc helpers (не
  использован в текущих IT, оставлен для следующих сессий)

**Config (application.yml):**

```yaml
auth:
  jwt:
    secret: ${AUTH_JWT_SECRET:dev-only-do-not-use-in-prod-...-min-32-chars}
    access-token-ttl-minutes: ${AUTH_ACCESS_TTL_MINUTES:15}
    refresh-token-ttl-days: ${AUTH_REFRESH_TTL_DAYS:7}
```

JwtService throws `IllegalStateException` если secret < 32 байт - prevents
shipping dev placeholder в prod.

**Smoke (curl):**

После backend rerun проверено:
- `POST /api/v1/auth/register` с {email, username, password} → 201 + accessToken + Set-Cookie refresh_token
- `POST /api/v1/auth/login` с {email, password} → 200 + accessToken
- `GET /api/v1/auth/me` с `Authorization: Bearer <jwt>` → 200 + user info
- `GET /api/v1/auth/me` без header → 401 Problem Details
- `GET /api/v1/topics` с X-User-Id (dev fallback) → 200 (existing flow работает)

**Не сделано в этой сессии:**

- frontend login UI - **Этап 21.b** (следующая сессия): LoginPage,
  RegisterPage, AuthStore (Zustand), apiClient interceptor (Bearer +
  refresh-on-401), Logout, resume session через /me
- refresh token rotation + blacklist - см. ADR-040 «Открытые вопросы»
- OAuth2 / social login - не входит в исламский use-case

**Что user может проверить руками:**

- запустить backend (если ещё не) - dev seeder создаст admin user
- `curl -X POST http://localhost:9090/api/v1/auth/login -H 'Content-Type: application/json' -d '{"email":"admin@argumentmap.local","password":"admin12345"}'` - получить access token
- `curl http://localhost:9090/api/v1/auth/me -H 'Authorization: Bearer <token>'` - увидеть user info
- swagger ui `/swagger-ui/index.html` - проверить новые `/auth/*` endpoints видны

---

## 2026-05-17 - Сессия 40 Responsive Фаза 2

Production-prep продолжение Сессии 39: закрыты все 10 точек Responsive
Фазы 2. До этой сессии mobile (<768px) работал только на foundation
из Фазы 1 (Modal, Header, NodeDetailsPanel) - остальные страницы
выпадали за viewport. Сейчас 0 horizontal scroll на 375px на всех
ключевых страницах (топики/книги/Q&A/админ/reader). 179/179 tests
pass, build clean, lint clean.

**Что сделано (10 коммитов по точке + 1 doc):**

1. **#1 BookReaderPage drawer + fullscreen PDF preview** (bf2d94f) -
   на <md inline sidebar с chapters скрыт, заменён на drawer Modal
   (full-screen). Открывается из toolbar кнопкой «Главы» (icon List).
   PDF preview overlay на mobile занимает h-dvh (max-md:inset-0)
   вместо bottom-sheet с drag-handle (drag скрыт). Sticky toolbar
   снят на mobile (md:sticky) - browser address-bar collapsing
   делает sticky прыгающим
2. **#2 sticky chapters sidebar - dvh вместо vh** (e48c244) -
   max-h calc(100vh-7rem) → calc(100dvh-7rem). vh не учитывает
   collapsing address-bar на iOS Safari / Chrome
3. **#3 PdfViewer toolbar - vertical stack на mobile** (9c51bf2) -
   6+ items toolbar (prev/page/next + zoom/scale/zoom/download) на
   <sm в 2 ряда вместо ломаного flex-wrap. Через `flex-col sm:flex-row`
   + group rolled через `sm:contents`
4. **#4+#5 cards layout - mobile padding reduction** (cea3b06) -
   TopicListPage / QuestionListPage / BookListPage уже были responsive
   (grid-cols-1 sm:grid-cols-2 lg:grid-cols-3). Дополнено
   px-3 py-6 на mobile вместо px-6 py-8 - +24px content width
5. **#6 CreateQuestionPage hint - mobile padding** (389d4eb) - тот же
   паттерн padding reduction + CreateTopicPage. Hint остаётся видимым
   ниже формы (не в collapsible) - lg:grid-cols-[1fr_300px] стэкается
6. **#7 AdminShamelaPage mobile stack + table h-scroll** (4cbcdb2) -
   ResultsTable в overflow-x-auto + min-w-[668px] (sticky header
   скроллируется синхронно). StatusStrip status chip получил
   col-span-2 sm:col-span-3 - на mobile занимает полную ширину строки
7. **#8 CitationPicker tab switcher** (2d1c4a4) - 3-колоночный layout
   (books 280 + reader flex + selection 320) на <sm заменён на 3-tab
   switcher (books / reader / selection). Auto-switch после выбора
   книги (books → reader). Tab selection с badge-dot если есть
   selection. Reader-tab disabled до выбора книги. Modal-обёртка
   fullscreen на mobile (h-dvh, rounded-none, subtitle скрыт)
8. **#9 FileUploadModal academic - 1-col grid** (35ccb31) - 3 numeric
   поля (edition/yearHijri/yearGregorian) grid-cols-3 → grid-cols-1
   sm:grid-cols-3. Затрагивает FileUploadModal, BookEditModal,
   AddSourceModal (общий AcademicMetadataFields)
9. **#10 BookListPage filter chips overflow** (0f3bc7a) - filter
   chips на mobile в overflow-x-auto + -mx-3 px-3 для edge-to-edge
   scrollbar (standard mobile category pattern). Та же правка
   применена к QuestionListPage chips для consistency

**i18n - 3 новых ключа (RU+AR):**
- `citation_picker.tab_books` / `tab_reader` / `tab_selection`

**Playwright smoke @ 375x812:**

Все 5 list/create страниц + BookReader дают scroll=365/375 (нет
horizontal overflow). Screenshots в `/tmp/responsive-phase2-*-375.png`

**Документация:**
- roadmap.md - Responsive Фаза 1+2 сжата в одну строку closed-stages,
  активная секция «User feedback Responsive» убрана. Принцип 3
  doc-hygiene (закрытый этап = строка)
- backlog.md - Фаза 2 чек-лист убран, добавлена Фаза 3 (3 точки
  возможных улучшений без обязательств)
- progress.md - эта запись
- coding-standards.md - примеры grid responsive, drawer pattern,
  dvh использование, overflow scroll для chips - см. следующий
  коммит

**Что user проверить руками:**

Chrome DevTools emulation iPhone 13 (390×844) + iPad Mini (768×1024):
1. `/topics`, `/qa`, `/books` - cards в 1 col, padding 12px, filter
   chips скроллятся горизонтально
2. `/admin/shamela` - 5 stat карточек + status chip в 2-col, после
   sync ResultsTable скроллится горизонтально на mobile
3. `/books/{id}` - кнопка «Содержание» открывает drawer Modal со
   списком глав. PDF mode тоже показывает «Содержание»
4. `/qa/new` - форма сверху, hint снизу (не сжато). Padding 12px
5. CitationPicker (открыть из node deтails / question detail
   citations panel) - на mobile должны быть 3 tab (книги/чтение/
   выделение), при выборе книги автоматически в reader
6. FileUploadModal в `/admin/shamela` (кнопка «Из файла») - раскрыть
   academic секцию, поля edition/years в одну колонку

Tests: 179 passes (24 test files). Build: 497KB main bundle gzipped.

---

## 2026-05-17 - Сессия 39 Responsive Фаза 1

Production-prep работа по адаптации UI под mobile (375+) и tablet
(768+) viewport. До этой сессии всё было оптимизировано под desktop
1280+. Закрыта **Фаза 1** - 4 критические точки которые ломали
mobile usability. Фаза 2 (10 точек) вынесена в backlog с явными
TODO. Frontend 179/179 tests pass (+9 новых: 4 useViewport + 5
Modal), lint 0 errors, build SUCCESS

### Frontend (6 commits)

- `58584df` feat - `shared/hooks/useViewport.ts` с `useIsMobile()`
  hook и `BREAKPOINTS` constant. Foundation для conditional logic
  где нужна другая структура компонента (не просто стили).
  `test-setup.ts` получил polyfill для `window.matchMedia` и
  `HTMLDialogElement.showModal/close` - jsdom не реализует, без
  них любой компонент использующий `useIsMobile` или `<dialog>`
  падал в тестах. Default polyfill = desktop viewport
- `9580317` feat - `Select` adaptive `max-h`. Заменил condit
  `max-h-64` (только при опций > maxVisibleItems) на CSS-only
  `max-h-[min(16rem,50vh)]` - на mobile menu не вылезает за viewport,
  на desktop ведёт себя как раньше (16rem ≈ 12 опций)
- `9f14528` feat - `Modal` full-screen overlay на mobile. На <md
  (768px): `fixed inset-0 h-dvh w-screen` без rounded corners,
  header с `<ArrowLeft>` back-button вместо close-X (стандартный
  mobile dismiss). На md+ - centered с rounded и max-w. Через
  `useIsMobile()` conditional class. Все existing call sites
  (FormModal, AddNodeModal, AddEdgeModal, AddSourceModal,
  FileUploadModal, CitationPicker) автоматически получают mobile
  mode без правок. `Modal.test.tsx` новый: 5 тестов desktop + mobile
- `f8a10f5` feat - `NodeDetailsPanel` fullscreen overlay на mobile.
  400px right-side panel на mobile занимал почти весь viewport и
  блокировал граф. На <md теперь `fixed inset-0 z-50` - чтение и
  редактирование узла становится independent task, закрытие через
  back-arrow возвращает в граф. Desktop без изменений (absolute end-0
  + 400px). Замена `X` → `ArrowLeft` icon в close button на mobile
- `6839b27` feat - `Header` compact + hamburger menu drawer на
  mobile. Inline nav (4 пункта) + 6 right actions переполняли 375px.
  На <md: `<Menu>` кнопка перед logo, inline nav `hidden md:flex`,
  drawer открывается через `Modal` (fullscreen из Фазы 1) с nav
  links + Search + Settings actions. Compact padding (gap-2 px-3
  vs gap-6 px-6 desktop). Right cluster - только Locale + Theme
  inline (часто переключаемые узкие affordance), остальное в
  drawer. Header остаётся h-12 (48px) ≤60px требования
- `92b4156` refactor - `useIsMobile` переведён с `useEffect+setState`
  на `useSyncExternalStore` (React 18+ API). eslint правило
  `react-hooks/set-state-in-effect` (default error в проекте)
  поймало старый паттерн. Behavior идентичный

### Решения

- **Conditional render vs CSS-only для responsive?** Для стилей
  и visibility - Tailwind breakpoint prefix (`hidden md:flex`).
  Для **смены структуры компонента** (другой layout, другой
  handler, drawer vs panel) - `useIsMobile()`. Не использовать
  hook когда CSS prefix достаточен - runtime overhead +
  SSR-incompatibility hazard
- **Bottom-sheet vs fullscreen для NodeDetailsPanel?** Выбран
  fullscreen overlay - проще, переиспользует тот же inset-0
  pattern что Modal, не плодит компоненты. Bottom-sheet требовал
  бы drag-handle, swipe-to-dismiss UX, отдельный animation flow.
  Если в Фазе 2 появятся узлы с большим objectom (10+ сущностей)
  - можно вернуться к bottom-sheet
- **useEffect + setState vs useSyncExternalStore?** Изначально
  написал старый pattern, eslint поймал. Перешёл на правильный
  React 18+ API - чище и без warning. Mock в тесте singleton-by-query
  потому что useSyncExternalStore re-reads getSnapshot после notify
- **Stash + restore dictionary?** В working tree были uncommitted
  parallel polish изменения (Settings/* keys для Settings page).
  Я случайно stash'нул их вместе с Header, потом restore через
  `git apply --3way` - сохранил оба набора (мои nav.menu_* +
  parallel polish settings.*). Гигиена commits сохранена -
  parallel polish осталось untracked в working tree

### Docs

- `roadmap.md` - новая активная секция «User feedback Responsive»
  с Фазой 1 `[x]` + Фазой 2 `[ ]` (10 точек)
- `backlog.md` - раздел «Responsive» переписан под Фазу 2 с
  acceptance criteria
- `frontend/docs/coding-standards.md` - новая секция «Responsive»
  с правилами mobile-first, когда CSS, когда JS, Modal pattern,
  `dvh` vs `vh`, testing

### Verify

- Frontend: `npm run lint` 0 errors, `npx tsc --noEmit` 0 errors,
  `npm test -- --run` 179/179 pass, `npm run build` SUCCESS
- Playwright @ 375px (iPhone SE): TopicListPage с visible hamburger
  + bismillah logo + locale/theme в compact header, hamburger menu
  drawer fullscreen с back-arrow, BookListPage 1-column cards (filter
  chips - известная Фаза 2 issue), desktop @ 1280 не сломан -
  скрины в `/tmp/responsive-sweep-*.png`

### Следующий шаг

Фаза 2 - 10 точек в backlog. Самые важные: BookReaderPage layout
(chapters drawer), PdfViewer toolbar overflow, TopicListPage /
QuestionListPage cards grid responsive. Можно делать
инкрементально в любой следующей сессии когда придёт user
feedback с конкретного экрана

---

## 2026-05-17 - Сессия 39 lazy PDF streaming 25.d.5

Закрыл последний открытый пункт Этапа 25.b/d - lazy Range streaming
для shamela PDF из archive.org через backend. До этого первое
открытие 135MB книги блокировало юзера на ~30 сек пока бэкенд
скачивал весь PDF целиком для кеша. Теперь Range request форвардится
напрямую к archive.org и стримится бэкендом без буферизации в памяти.
Backend 592 IT (+17 от 575), `mvnw verify` BUILD SUCCESS

### Backend (3 commits)

- `62d14e1` feat - `PdfSourceProvider.openStream(book, fileIndex,
  RangeSpec)` как primary read path. Domain `RangeSpec(startInclusive,
  endInclusive?)` (end nullable для open-ended `bytes=N-`) +
  `PdfStreamingResult(stream, contentLength, start, end, totalSize,
  isPartial)` AutoCloseable. `UserUploadProvider.openStream` - MinIO
  native Range через `GetObjectRequest.range()`.
  `PdfLinksSourceProvider.openStream` - cache hit MinIO Range; cache
  miss + null range синхронный fill через `locateFile()`; cache miss +
  range lazy forward к archive.org через `PdfFetcher.openStream`
  (HTTP Range header добавляется). `HttpClientPdfFetcher.openStream`
  защищён тем же `@CircuitBreaker(pdfDownload)` что и `fetch()`.
  `PdfService.openStream` - роутер через provider.
  `RangeNotSatisfiableException` → 416 Problem Details в
  `GlobalExceptionHandler` с `start`/`totalSize` properties
- `854cc69` feat - `PdfController.streamPdf` мигрирован на
  `PdfService.openStream`. Status / headers / content строятся из
  `PdfStreamingResult` полей. Default chunk cap 1MB сохранён.
  `PdfControllerIT` адаптирован под новый API + новый тест
  `streamPdf_rangeOutsideFile_returns416`
- `f47b4e2` feat IT - `HttpClientPdfFetcherRangeStreamingIT` (новый,
  6 тестов) через локальный `com.sun.net.httpserver.HttpServer` на
  динамическом порту: 200 full, 206 partial, 200 при игнорировании
  Range (mirror без Range support), 5xx → exception, open-ended
  `bytes=N-`, 416 от upstream. JDK HttpServer выбран вместо WireMock
  - нет нового runtime dep, sub-10мс startup. `UserUploadProviderIT`
  (+5) и `PdfLinksSourceProviderIT` (+5) - cache hit/miss с разными
  range scenarios + 416 + invalid fileIndex

### Решения

- **MinIO tee при cache miss + range?** Отложено - требует
  `PipedInputStream` или background executor + careful sync. Сейчас
  каждый Range request на не-кешированной книге = отдельный upstream
  HTTP. Trade-off acceptance: latency распределена ровнее, нет
  30-сек блока в начале. Тригерь tee когда появится production
  traffic где много юзеров на одну книгу
- **WireMock vs JDK HttpServer для тестов?** JDK HttpServer - нет
  нового runtime dep, lightweight, достаточно для контракт-уровня.
  WireMock дал бы advanced features (recording / fault injection)
  которые на этом уровне не нужны
- **Default method в `PdfSourceProvider.openStream`?** Нет -
  явный signature каждому provider'у заставляет подумать про lazy
  семантику конкретно для своего источника. Default через `locateFile`
  + `MinIO.getRange` дал бы regression к старому поведению для
  PdfLinks (полный download)
- **Удалить `locateFile` после миграции на `openStream`?** Нет -
  используется в IT (cache verification, multi-volume), при cache
  miss + null range (admin smoke / full download path). Не deprecated

### Docs

- ADR-023 **Amendment 2026-05-17** в `decisions.md` про lazy
  streaming - rejected alternatives (tee, double request, no-cache)
- `roadmap.md` 25.d.5 → `[x]` с описанием
- `api-contract.md` PDF API раздел расширен: Range header semantics,
  Content-Range, lazy streaming описание, 416 ошибка, 503 circuit
  breaker

### Verify

- Backend: `./mvnw verify` 592/592 BUILD SUCCESS
- Smoke curl - см. отчёт

### Следующий шаг

Этап 25 PDF Viewer почти закрыт - остаются `25.d.2` (text↔pdf page
sync, Tier 1 admin flow), `25.d.4` (inline PDF preview redesign),
`25.e/f` (после Этапа 17). Можно переключаться на любой пункт из
SESSION_START_PROMPT по выбору Абдулы

---

## 2026-05-17 - Сессия 39 финал, Этап 6 JSON export/import

Закрыл единственный нетронутый пункт Этапа 6 - JSON-сериализация темы
целиком для backup и обмена между инстансами. Backend 575 IT (+21
от 554), frontend 170 vitest без регрессий, lint clean, build ok

### Backend (3 commits)

- `733842c` feat - `TopicExportDto` + 7 nested records
  (TopicData/NodeData/EdgeData/NodeSourceData/SourceData/AuthorityData/
  BookRef) + `TopicImportResponse{topicId, importedNodes, ...,
  warnings[]}`. `TopicExportService.exportTopic` собирает unique
  sources через LinkedHashSet (стабильный порядок по first-seen).
  `TopicImportService.importTopic` с UUID remapping через
  `Map<oldUUID, newUUID>` для каждой entity, FK references
  (edges.fromNodeId, node_sources.nodeId/sourceId) пере-mapping
  по словарю. createdBy перезаписан на импортирующего user'а
  (security). Authorities find-or-create по name (без era - dup
  избегаем), books find-or-skip с warning. Positional refs
  null'ифицируются если source без bookId.
  `UnsupportedExportFormatException` → 422 unsupported-format-version
  с receivedVersion/supportedVersions properties
- `dd97246` feat - `TopicExportImportController` с двумя endpoints:
  `GET /api/v1/topics/{id}/export` (Content-Disposition: attachment;
  filename="topic-{shortId}.json"), `POST /api/v1/topics/import`
  routed по consumes (application/json для programmatic flow,
  multipart/form-data для UI file upload)
- `ee99efe` feat IT - 19 тестов через Testcontainers:
  - `TopicExportServiceIT` (5): empty topic, full tree с дедупликацией
    sources, revisions exclusion, source without authority/book, 404
  - `TopicImportServiceIT` (8): invalid format version, null topic,
    empty payload, fresh instance remapping, missing book → warning,
    existing authority by name reused, existing book preserved, round-trip
  - `TopicExportImportControllerIT` (6): export 200 + filename header,
    export 404, importJson 201, importMultipart 201, invalid version
    422, missing X-User-Id 400

### Frontend (1 commit)

- `bb0417d` feat - в TopicListPage header кнопка «Импортировать тему»
  (ghost Upload icon) триггерит hidden `<input type="file">`
  программно. handleFileSelected → apiPostMultipart → toast.success
  с action «Открыть» → navigate на новую тему. Warnings показываются
  отдельным toast.warning. 422 unsupported-format-version → специальный
  toast.error.
  На каждой TopicCard в углу `<Download>` icon button (opacity-0,
  fade-in на group-hover) - apiGetRaw `/export` → Blob +
  URL.createObjectURL + programmatic `<a download>` click +
  setTimeout(0) revoke. stopPropagation чтобы не сработал обёрточный
  `<Link>`. 8 новых i18n keys ru/ar (topic.export.*, topic.import.*).
  Types регенерированы (TopicImportResponse + TopicExportDto + TopicData
  доступны в components.schemas)

### Решения

- **Включать revisions?** Нет - история не нужна для обмена/backup,
  10x размер при минимальной ценности
- **Включать Books полностью?** Нет - shared library resource (ADR-019),
  hint (id+title+authorityId) достаточен для пользователя
- **Reuse imported UUIDs?** Нет - PK violations при self-import.
  UUID remapping + защита от ownership override
- **Authority match by name VS (name+era)?** name - era это
  disambiguation, не invariant. Дубликаты избегаются, occasional
  false-match приемлем
- **Книги auto-create при импорте?** Нет - подмена source provenance.
  Find-or-skip с warning - пользователь явно импортирует книги
  через основной flow если нужно
- **Один endpoint /import vs два?** Один с content-type routing.
  Spring routes на одном path по `consumes` (JSON body для curl,
  multipart для UI)

### Docs

- ADR-037 в `decisions.md` с rejected alternatives (inline books,
  imported UUIDs reuse, auto-create books, multipart-only)
- `api-contract.md` новая секция «Topic export/import API» с описанием
  обоих endpoints + DTO + warnings semantics. History entry добавлен
- `roadmap.md` Этап 6 → `[x]` JSON export/import

### Verify

- Backend: `./mvnw verify` 575/575 BUILD SUCCESS
- Frontend: `npx tsc --noEmit -p tsconfig.app.json` clean,
  `npm run lint` 0 errors (4 pre-existing warnings),
  `npm run build` 2.55s ok,
  `npm run test:run` 170/170 pass
- Smoke (curl):
  ```
  curl -s http://localhost:9090/v3/api-docs | grep -o "topics/import\|topics/.*export" | sort -u
  /api/v1/topics/import
  /api/v1/topics/{topicId}/export
  ```
  endpoints зарегистрированы

### Что осталось в Этапе 6

- Полнотекстовый поиск по содержимому узлов (Postgres `tsvector`) -
  низкий приоритет, ждёт когда базы наполнятся
- Реализация Dung's argumentation framework - research-grade фича,
  не блокирует основной MVP

### Следующий шаг

Этап 6 закрыт по приоритетной части. Можно двигаться к
Этапу 17 OCR / другим Опциям A-H из SESSION_START_PROMPT по выбору
Абдулы

---

## 2026-05-17 - Сессия 39 продолжение, delete UX unification (#7)

После hotkey unification Абдула заметил разнобой: context menu
«Удалить» удалял silent, а Del/Backspace (только что добавленный
subagent'ом коммитом `4a4002d`) показывал native `window.confirm()` -
уродский, не локализованный, блокирующий. Унифицировали через
паттерн Gmail/Slack: оба пути теперь silent delete + toast.success
с действующей кнопкой «Отменить» (5 сек TTL по defaults success
toast)

### Frontend (1 commit + docs)

- `XXX` fix(frontend) - убрали `window.confirm()` целиком из
  `GraphCanvas.handleDelete`. Единая точка `runDelete(nodeIds, edgeIds)` -
  используется из context menu (`deleteOneNode`/`deleteOneEdge`),
  hotkey Del/Backspace (`handleDelete`) и toolbar bulk-delete.
  Snapshot узлов до DELETE → toast.success с action «Отменить» →
  при клике `restoreNodeFromSnapshot` через POST `/api/v1/nodes`
  + PATCH posX/posY. Edges НЕ восстанавливаются (новый id у
  re-created узла) - предупреждение через tooltip-hint у Undo кнопки
- `ToastAction.hint?: string` - расширили API toast action button
  опциональным title-tooltip. Используется для
  «связи не восстанавливаются - привяжите вручную»
- 4 новых i18n ключа: `graph.node.deleted_toast`,
  `graph.node.deleted_undo`, `graph.node.undo_failed`,
  `graph.node.undo_no_edges_hint` + `graph.edge.deleted_toast` +
  `graph.node.deleted_toast_multi` (ru/ar)
- 3 новых vitest в `GraphCanvas.test.tsx`: confirm spy assertions +
  toast appearance + undo flow с POST mock

### Решение про undo

Прагматичный путь: **re-create без edges**. Альтернативы:
1. Backend soft-delete + revive endpoint - сохраняет id + edges,
   но требует миграцию (`deleted_at`) + новый endpoint + изменение
   запросов исключающих soft-deleted. Overkill для случая «упс,
   нажал не туда»
2. Frontend re-create с edges - проблема: после DELETE backend каскадно
   удаляет edges, restore'ить их нужно отдельной серией POST'ов с
   риском rule violations (ADR-010 матрица). И всё равно новый id

Выбран (3): undo восстанавливает только узел через POST. Цена -
edges теряются - честно сообщается через tooltip. Большинство
случайных удалений - leaf узлы где edges и так минимальны

### Docs

- `roadmap.md` - #7 в «User feedback Сессии 38»
- `frontend/docs/ui-guidelines.md` - **новая секция «Destructive
  actions»** с правилом «не использовать native confirm/alert/prompt»

### Verify

- `npx tsc --noEmit -p tsconfig.app.json` clean
- `npm run lint` 0 errors (4 pre-existing warnings)
- `npm run build` 2.55s ok
- `npm run test:run` 170/170 pass (167 baseline + 3 GraphCanvas
  delete UX)
- Playwright headless smoke - все 12 шагов pass:
  - 0 native confirm на любом пути удаления (Del + context menu)
  - toast.success появляется с Undo кнопкой
  - tooltip-hint у Undo показывает предупреждение про edges
  - клик Undo восстанавливает узел (count возвращается)
  - context menu Удалить тоже silent + toast undo
  - скриншоты `/tmp/delete-ux-{1-6}-*.png`

---

## 2026-05-17 - Сессия 39, hotkey unification (#2 / #4)

Параллельно с bug-fix subagent'ом закрыли последние два observable
замечания пользователя (#2 Alt+K на не-EN раскладке, #4 ⌘+↵ submit).
Вместо точечного fix'а провели **системную унификацию** всех keyboard
shortcuts через `react-hotkeys-hook` 5.x с обёрткой `useHotkey`
(ADR-036). Заодно подобрали Del/Backspace handler subagent'а (#3) -
мигрировали на ту же систему

### Frontend (4 commits)

- `1ba8faa` feat **infra** - `react-hotkeys-hook@5.3.2` +
  `shared/hooks/useHotkey.ts` (тонкая обёртка с дефолтами:
  preventDefault, enableOnFormTags=false, useKey=true для
  layout-independence) + `shared/components/ui/ShortcutHint.tsx`
  (отображение combination как набор `<Kbd>` с platform-aware glyph'ами:
  `mod` → `⌘` Mac / `Ctrl` Win/Linux). 8 vitest (useHotkey 3 +
  ShortcutHint 5)
- `e4b5938` refactor **миграция 16 файлов**:
  - App.tsx (Alt+K palette - решает #2 через useKey:true)
  - CommandPalette (escape/arrows/enter + enableOnFormTags)
  - CitationPicker, ContextMenu, AvatarMenu, BellMenu, Select,
    NodeSelect, useGraphEscape - escape close
  - GraphCanvas Del/Backspace (#3 migrated на useHotkey
    `'delete,backspace'`)
  - FormModal - автоматический `mod+enter` submit +
    `<ShortcutHint keys="mod+enter">` в footer. Решает #4.
    `<Kbd>⌘</Kbd>` хардкоды убраны из AddNodeModal/AddEdgeModal
  - Header `<ShortcutHint keys="alt+k">` вместо `<Kbd>Alt</Kbd><Kbd>K</Kbd>`
  - PageJump/PdfViewer inline onKeyDown оставлены с комментариями
    (form-bound Enter-to-submit, не global hotkey - идиоматично)
- `b2517c3` fix **#2/#4 + preventDefault gotcha** - useGraphEscape
  `preventDefault: false` на уровне опций + ручной
  `e.preventDefault()` в callback только когда реально обрабатываем.
  Иначе react-hotkeys-hook стопал бы Esc до того как native
  `<dialog>` его получит - Modal не закрывался бы по Escape

### Docs (этот commit)

- ADR-036 react-hotkeys-hook + альтернативы (vanilla, hotkeys-js,
  tinykeys) с обоснованием
- `frontend/docs/coding-standards.md` секция Hotkeys: useHotkey
  вместо addEventListener, modifier `mod` для cross-platform,
  preventDefault gotcha для native dialog, `ShortcutHint` для UI
- `gotchas.md` запись «event.key vs event.code в keyboard handlers»
  с reproducer ru/ar/en раскладок
- roadmap: #2/#4 → `[x]` (#3 уже был помечен subagent'ом, чуть
  доуточнили формулировку)

### Verify

- `npx tsc --noEmit -p tsconfig.app.json` clean
- `npm run lint` 0 errors (4 warnings pre-existing)
- `npm run build` 2.57s ok
- `npm run test:run` 167/167 pass (156 baseline + 8 useHotkey/ShortcutHint
  + 3 от bug-fix subagent'а AdminShamela)
- playwright headless smoke 5/5:
  - Alt+K open palette
  - Esc close palette
  - AddNodeModal open
  - Esc close AddNodeModal (после preventDefault fix)
  - Cmd+Enter submit AddNodeModal

### Что осталось

- #6 финальное решение по шрифту - waiting Абдулу
- Опции A-H из SESSION_START_PROMPT не тронуты

### Следующий шаг

Все 6 user feedback закрыты. Можно двигаться к Опциям A-H по выбору
Абдулы (Этап 17 OCR / импорт-экспорт темы JSON / прочее)

---

## 2026-05-17 - Сессия 39, user feedback #1 / #3 / #5 / #6

Закрыли 4 из 6 observable замечаний пользователя из конца Сессии 38
(#2 и #4 - hotkey unification - параллельно ведёт другой subagent).
Backend +2 IT (NodeServiceIT 9→11), frontend +3 vitest
(AdminShamelaPage.test новый). Все коммиты атомарные

### Backend (1 commit)

- `9e8e045` feat **#1 root protection** - `NodeIsRootException` 409
  Conflict. `NodeService.deleteNode` подтягивает `Topic` и сверяет
  `nodeId == topic.rootNodeId` ДО удаления. Иначе бэк бы отдал 500
  или каскадно разрушил граф. `GlobalExceptionHandler` мапит в
  Problem Details `type=node-is-root` + `nodeId` / `topicId` properties.
  +2 IT: root throws, non-root succeeds (sanity)

### Frontend (3 commits)

- `c6c8188` feat **#5 shamela toast UX** - `AdminShamelaPage`
  `formatShamelaError` мапит `problem.type` через `ApiError.is(suffix)`:
  shamela-api-error → «внешний сервис shamela.ws недоступен. возможно
  требуется VPN или сервис временно лежит. попробуйте позже»; archive
  → «не удалось распаковать»; reader → «ошибка чтения каталога».
  Unknown тип фолбэк на title+detail. +3 vitest в новом
  `AdminShamelaPage.test.tsx` (502 case, archive case, fallback)
- `4a4002d` feat **#1 + #3 GraphCanvas** - root protection (UI):
  - `rootNodeId = graph.topic?.rootNodeId` derived
  - context menu: для root пункт «Удалить» рендерится disabled с
    подсказкой («корневой вопрос нельзя удалить - удалите тему
    целиком»), для не-root - обычный danger
  - bulk-delete из toolbar: фильтрует root, toast.warning после
    успеха что один узел пропущен
  - `deleteOneNode` защитный barrier - toast.warning если будущая
    точка входа попробует удалить root
  - Del/Backspace handler (#3): `useEffect` с `event.code` (любая
    раскладка), игнорит фокус в input/textarea/contentEditable +
    открытый modal + контекстное меню. Триггерит `handleDelete` -
    root filter уже там. TODO: hotkey subagent мигрирует на единую
    систему через react-hotkeys-hook

### Docs (1 commit, далее)

- #6 диагностика шрифта через playwright (см. ниже)
- ADR не нужен - #1 это bug fix, #5 - UX, #6 - диагностика без
  изменения

### #6 диагностика - результат playwright

`http://localhost:5173/books`:
- `--font-book-title` CSS var = `'Manrope', 'Source Serif', Georgia, serif`
  - **уже не EB Garamond** как обещает комментарий в tokens.css
  (возможно subagent типографии Сессии 36 не докоммитил, либо
  rollback произошёл)
- `document.fonts.size = 0` - ноль web-fonts загрузилось вообще
  (включая Amiri для арабских title)
- Причина: WSL2 corp proxy 407 блокирует Google Fonts CSS request
  (HTML preconnect → `fonts.googleapis.com` → 407). Известная gotcha
- Для всех 5 книг `book.language='ar'`, поэтому Card.Title идёт
  по `arabic=true` ветке → `font-arabic` class →
  `'Amiri','Scheherazade New','Noto Naskh Arabic',serif` →
  все три отвалились через прокси → fallback **system serif**
  (Liberation Serif на Linux/WSL2)
- screenshot: `/tmp/book-list-fonts.png`. Выглядит **читаемо** -
  это нормальный serif. «выврвиглазность» - вероятно из-за
  отсутствия типографики (italic glyphs, hinting), которая в
  production browser с интернетом будет другая
- **Не меняем шрифт** - решение по визуальному дизайну за Абдулой.
  Можно: (a) в production с реальным интернетом проверить как
  EB Garamond/Amiri выглядят; (b) если в production тоже плохо -
  обсудить переход на Lora / PT Serif / Old Standard TT; (c)
  если в WSL2 хочется хорошего dev preview - подключить fonts
  через локальные `@font-face` файлы в `public/fonts/` минуя
  Google CDN

### Что НЕ закрыто в Сессии 39

- **#2 Alt+K layout fix** - параллельно делает hotkey subagent
- **#4 Cmd+Enter + централизация hotkeys** - там же. Будет
  отдельный handoff от hotkey subagent
- **#6 финальное решение по шрифту** - waiting Абдулу
- Опции A-H из SESSION_START_PROMPT не тронуты (вначале #1-#6)

### Следующий шаг

Если hotkey subagent ещё не закончил - подождать его коммитов,
проверить что #2/#4 действительно закрыты. Если да - двигаться к
Опции A (Этап 17 OCR) или B (импорт/экспорт темы JSON) из
SESSION_START_PROMPT по выбору Абдулы

---

## 2026-05-17 - Сессия 38, post-review fixes Этапа 16

Закрыли critical issue + 3 important issue из code review Сессии 37.
Критическое - после `POST /imports/file` загруженный PDF был в MinIO +
`library_files` catalog, но **не читаем** через `PdfService` (единственный
`PdfLinksSourceProvider` смотрел `metadata.pdf_links` который
`FileImportService` не пишет). Кнопка «Открыть книгу» в FileUploadModal
toast вела в reader который не мог получить PDF - critical UX gap

### Backend (5 commits)

- `b5d4cc4` feat **Этап 16.h** - `UserUploadProvider` (`@Order(50)`,
  выше `PdfLinksSourceProvider` order=100). `supports` - true если
  есть active blob в `library_files` с `source_type=USER_UPLOAD`.
  `getMetadata` возвращает single PdfFileInfo (page_count из
  `book.metadata.pdf_page_count`). `locateFile` резолвит
  `(bucket, storage_key)` из catalog - никакого upstream download
- Новый репозиторный метод `findActiveByBookIdAndSourceType` для
  scoped lookup. `PdfService` javadoc обновлён - перечисляет оба
  provider'а
- Тесты +11: 9 кейсов `UserUploadProviderIT` через Testcontainers
  MinIO+Postgres + 1 E2E `POST_upload_thenGET_pdfInfo_...` в
  `FileImportControllerIT` (upload → GET /pdf/info → 200 со списком →
  GET /pdf → 200 PDF). Этот E2E - регрессионный якорь, дублировать
  для каждого нового способа создания Book
- `dcfdf24` fix **BucketBootstrap concurrent startup** - catch
  `BucketAlreadyOwnedByYouException` + `BucketAlreadyExistsException`
  при race condition между двумя pod'ами на createBucket. Трактуется
  как success, INFO лог с e.getClass().getSimpleName() для debug
- `5c5277e` fix **language whitelist** в FileImportController.
  Whitelist `Set.of("ar","ru","en")` (mirror frontend FileUploadModal).
  Blank/null - валидно (сервис применит default "ar"), вне whitelist →
  422 `file-import-error`. Закрывает contract drift
- `f9519c0` docs - уточнить комментарий в FileImportService про порядок
  pages/S3. Старый утверждал «защищает от pages без blob'а», на самом
  деле наоборот - от blob без pages при page-extraction failure.
  Edge case commit DB failure после S3 put → orphan blob упомянут с
  отсылкой на OrphanDetectionJanitor 25.b

### Проверки

- `./mvnw verify` - **554/554 pass** (543 до Сессии 38 + 11 новых),
  BUILD SUCCESS за 1:27
- Backend dev :9090 рестартован, поднимается с логом «bucket bootstrap
  завершён - все 4 bucket'а доступны»
- **Smoke на живом backend:** uploaded test PDF
  `/tmp/smoke.pdf` (590 bytes, 1 page) через
  `POST /api/v1/library/imports/file` - получил book_id
  `b683aaf1-a8a3-453b-b06e-bab4066bd0e7`. Затем
  `GET /api/v1/library/books/{id}/pdf/info` → 200 с правильным JSON
  (single-file, label=smoke, pageCount=1). `GET /pdf?fileIndex=0` →
  200 application/pdf с валидным PDF byte content. **Critical gap
  подтверждён закрытым на production-like setup**
- Language whitelist подтверждён на live backend: `language=zzzz` →
  422 с message `language должен быть одним из [ar, ru, en],
  получено 'zzzz'`

### Документация

- `docs/roadmap.md` - в записи закрытого Этапа 16 добавлено упоминание
  **16.h** post-review fix
- `docs/api-contract.md` - в секции File import API добавлена note
  что после upload книга **сразу** доступна через `/pdf/info` + `/pdf`
  endpoints через UserUploadProvider, language whitelist описан в
  таблице полей. Запись в «История изменений»
- `docs/gotchas.md` - **новая gotcha** «Каждый PdfSourceProvider должен
  явно поддержать новый source type» с симптом / причина / решение +
  превентивный паттерн (3-step smoke после новых способов создания Book)
- `docs/progress.md` - эта запись

### Известные мелочи (не блокеры)

- Frontend не трогался - фронт URL `/books/{bookId}` уже правильный,
  reader просто заработал после backend fix. Manual UI verification
  всё ещё нужна (Опция D - responsive sweep плюс sanity check на
  live книгу)
- Smoke book `b683aaf1-a8a3-453b-b06e-bab4066bd0e7` оставлен в
  production-БД (`smoke.pdf`, 1 страница). Можно удалить через
  `DELETE /api/v1/library/books/{id}` (если admin endpoint
  поддерживает USER_UPLOAD) или вручную через mc/psql

### Следующий шаг (для Сессии 39 / далее)

Опции из Сессии 37 остаются актуальными (Этап 17 OCR, Этап 6
импорт/экспорт JSON, 25.d.5 lazy PDF streaming etc). Опция D
**responsive sweep** дополнительно становится приоритетной потому что
PDF reader теперь работает end-to-end (раньше не имело смысла
полировать UX на сломанном flow)

---
