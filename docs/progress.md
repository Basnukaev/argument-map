# Журнал работы

Последние сессии. Новые записи - **сверху**

Формат записи описан в `docs/doc-hygiene.md` Принцип 5

**Архив:**
- Сессии 0-21: [`docs/archive/progress-sessions-1-21.md`](archive/progress-sessions-1-21.md)
- Сессии 22-29: [`docs/archive/progress-sessions-22-29.md`](archive/progress-sessions-22-29.md)
- Сессии 30-37: [`docs/archive/progress-sessions-30-37.md`](archive/progress-sessions-30-37.md)

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
