# Vision expansion — Сессия 49d (MAX autonomy)

Источник: сообщение Абдулы в начале Сессии 49d (2026-05-20).
Режим: «не останавливайся пока не скажу СТОП», subagents для context
conservation, перед UI changes — `/frontend-design`, screenshots в
WSL paths.

Документ — **зеркало** запросов Абдулы, структурированное по
приоритетам. Не план реализации (для крупных тем будут отдельные
spec'и в этой папке).

---

## 0. Критические баги (фронт + data integrity)

### 0.1 Q&A `[sources ??] is not iterable` (скрин 140915.png)

- **Симптом:** на странице `/qa/{id}` в секции «Источники» красная
  плашка ошибки `[sources ??] is not iterable`.
- **Дополнительно:** `GET /api/v1/questions/{id}/sources` возвращает
  `[]` (пустой массив), но frontend показывает 2 источника в
  «Ответ с источниками». Гипотеза: либо фронт ошибочно потребляет
  endpoint `/sources` для answer когда нужно `/citations`, либо где-то
  `iterable` ожидается, а приходит `undefined`/object.
- **Приоритет:** Critical — JS runtime error.

### 0.2 Audit UI broken (скрин 141039.png)

- **Симптом:** на `/admin/audit` в filter dropdown «Действие» items
  отрисовываются пустыми фиолетовыми блоками (без текста), фильтр
  визуально сломан.
- **Гипотеза:** i18n key missing для action enum values, либо
  `<option>` пропускает label.
- **Приоритет:** Critical (admin UI core).

### 0.3 Alt+K Command Palette — scrollIntoView (повторно)

- **Симптом:** при arrow-down навигации по длинному списку команд
  активный элемент уходит за viewport, scrollbar не двигается.
- **История:** commit `63d434c` Сессии 49 пытался зафиксить через
  `scrollIntoView`. По словам Абдулы регрессировало или не работает в
  крайних случаях.
- **Приоритет:** Important.

### 0.4 Alt+K открывается на login/register (повторно)

- **Симптом:** Alt+K активирует Command Palette на auth pages.
- **История:** commit `be04301` Сессии 49 добавил route guard. По
  словам Абдулы всё ещё триггерится.
- **Гипотеза:** глобальный listener регистрируется до проверки
  pathname, либо PathContext lazy при auth flow.
- **Приоритет:** Important.

---

## 1. UI/UX полировка

### 1.1 Тёмная тема (скрин 173902.png)

- **Симптом:** логотип имеет ярко-жёлто-фиолетовый бейдж, плохо
  читается на тёмном фоне; placeholder обложек книг — ярко-жёлтый,
  выбивается из палитры.
- **Также:** «синий цвет не сочетается с тёмной темой ни в каком
  виде» (вероятно accent-индиго).
- **План:** пересмотр тёмной палитры — мягкие токены вместо
  оригинальных индиго brand-цветов, либо адаптивная palette через
  `prefers-color-scheme` + tokens.css.
- **Приоритет:** Important (visual coherence).

### 1.2 Dropdown hover vs active (скрин 174320.png)

- **Симптом:** в `Select` component hover-state неотличим от
  active-state. Только check mark подсказывает текущий item, hover
  визуально перекрывает selected.
- **Приоритет:** Minor (одна правка contrast в Select.tsx).

### 1.3 Logo always Scheherazade font

- **Запрос:** логотип должен оставаться в шрифте Scheherazade New
  даже когда пользователь меняет шрифт интерфейса.
- **Гипотеза:** настройки font apply'ятся global на body, логотип
  не имеет inline font-family override.
- **Приоритет:** Minor (CSS isolation).

### 1.4 Bottom panel перекрывает zoom controls

- **Симптом:** при выделении узлов/edge popup внизу графа перекрывает
  React Flow Controls (zoom +/−, fit-view).
- **План:** layout — либо z-index re-arrangement, либо bottom-bar
  pushes controls вверх, либо controls перемещаются влево.
- **Приоритет:** Important (UX блок).

### 1.5 «Что такое алгоритмы раскладки» — UX explanation

- **Симптом:** в меню есть выбор алгоритма (ELK / dagre / etc), но
  пользователю непонятно что это и зачем.
- **План:** info tooltip / popover с описанием каждого алгоритма +
  примером картинки.
- **Приоритет:** Minor (documentation in UI).

### 1.6 Edge routing красиво, без слияния в одну точку

- **Симптом:** рёбра графа смешиваются в одной точке (на узле
  «толстый» bunch).
- **История:** commits Сессии 49 (`7050d29` SPLINE, `b1b15f1` bezier
  offset, `fa68ee6` curvature one-time) частично решили overlapping
  pair, но не визуально-«красивый» fan-out из узла.
- **Гипотеза:** нужен ELK layered + sourcePoint/targetPoint adjustment
  по edge type, либо handle distribution algorithm.
- **Приоритет:** Important (graph readability).

---

## 2. Платформенные функции (новые этапы)

### 2.1 Pagination + rating для тем / Q&A / Library

- **Запрос:** «должна быть какая-то пагинация и рейтинг тем чтобы мы
  знали что самое популярное и нужное и актуальное что показывать в
  выдаче, тоже самое для Q&A и библиотеки».
- **Скоп:** добавить поля для «популярности» (views/votes/answers
  count), default ordering = popularity DESC, server-side pagination
  с курсором либо offset (offset существует — добавить sorting).
- **Объём:** 3 параллельных этапа (Topics rating + Q&A rating +
  Library rating). Каждый — миграция + backend column + sorting param
  + frontend ordering selector.
- **Приоритет:** Important (discovery UX).

### 2.2 Library как общая, не личная (collections / favorites)

- **Запрос:** «библиотека это общая тема, не личная у каждого
  пользователя, каждый пользователь потом сможет добавлять какие то
  книги в свои коллекции, избранное».
- **Нынешнее:** `lib_books.visibility = PUBLIC` default + members
  M:N (ADR-043 Amendment). По факту 90% книг — public.
- **Скоп:**
  1. Library — single global catalog. Убрать «Мои/Разделяемые/
     Публичные» tab из BookListPage если все default PUBLIC.
  2. Новая таблица `user_book_collections` (user_id, book_id,
     collection_name, added_at) — favorites + named collections.
  3. UI: «Добавить в коллекцию» из BookCard, страница
     `/library/collections` — мои коллекции, drag-drop книг.
- **Приоритет:** Important.

### 2.3 Полноценный поиск в Shamela / Library

- **Запрос:** «shamela import так хочу чтоб отображались по дефолту
  все книги с пагинацией и поиском как сейчас но его допилить,
  сделать полноценный поиск как в первоисточнике shamela (поиск по
  автору, названию книги, направлению книги в науке и т.п.), такой
  же поиск должен быть и в библиотеке».
- **Скоп:**
  1. Shamela Master — у Shamela есть категории (фикх, тафсир, хадис,
     etc). Backend ETL должен парсить категории в `lib_shamela_categories`.
  2. Search backend — multi-field: `q` по title, `author`,
     `category_id`, `subject` (направление). Fulltext индекс в PG
     либо отдельный Elasticsearch (ADR в backlog говорит Elastic).
  3. UI — расширенный filter в `AdminShamelaPage` + `BookListPage`:
     dropdowns category, multi-select.
- **Приоритет:** Important.

### 2.4 Roles system: admin / scholar / student / user

- **Запрос:** «доступ к админке должен быть только у определённой
  роли, нужно продумать систему ролей и реализовать, админ, учёный,
  студент, обычный пользователь».
- **Нынешнее:** `users.role` CHECK `USER` / `ADMIN` (ADR-040). Roles
  member/editor для per-entity ACL (ADR-043).
- **Скоп:**
  1. ADR — расширить CHECK до `ADMIN / SCHOLAR / STUDENT / USER`.
     Миграция backfill (всем `USER` → `USER`).
  2. Backend — `Role` enum, AuthorizationService, route guards
     (`/admin/*` только ADMIN; некоторые действия — SCHOLAR+).
  3. Frontend — `AdminRoute` уже есть, добавить `ScholarRoute` /
     `StudentRoute` где применимо.
  4. Семантика: SCHOLAR — может добавлять hadith grades, оценки
     иснадов; STUDENT — может комментировать, отвечать в Q&A;
     USER — read-only + ставить vote.
- **Приоритет:** Important (необходим для следующих фич).

### 2.5 Guest view — анонимный доступ

- **Запрос:** «на сайт должна быть возможность войти просто под
  гостем, чтоб посмотреть какие вопросы существуют, ответы, дилеммы,
  книги и т.п.».
- **Нынешнее:** все mutating endpoints требуют auth, GET в dev/test
  permitAll, в prod — authenticated.
- **Скоп:**
  1. Backend — выделить read-only endpoints (`/topics?visibility=PUBLIC`,
     `/library/books?visibility=PUBLIC`, `/questions`, `/answers`),
     сделать их `permitAll` в prod profile.
  2. Frontend — login button становится «Войти / Гость», guest mode
     отсутствие write actions (как у unauthed user).
  3. Topics/Books filtered только PUBLIC. Q&A — все видны (нет
     visibility модели для Q&A — ADR-043 Amendment).
- **Приоритет:** Important.

### 2.6 Hadith Chains Explorer (alminasa.ai style)

- **Запрос:** «отдельная страница должна быть с полной историей
  хадисов и мухаддисов, с иснадами, матнами и т.п. аналог
  функционала с сайта https://alminasa.ai/ но чтоб было наглядно
  видно что кто рассказывал в каких вариациях, вплоть до Пророка
  ﷺ».
- **Скриншоты:** 141314, 141325, 141331.
- **Скоп:** **новое приложение** под `src/apps/hadith/`. ADR-018
  platform pivot rationale прямо здесь окупается.
  1. Domain modeling — `Hadith`, `Narrator`, `Sanad` (chain), `Matn`
     (text), `Grade`, `Source` (book). Backend domain + migrations.
  2. ETL — массовый импорт из открытых баз (sunnah.com API,
     islamhouse, shamela hadith books).
  3. UI — главное: визуализация sanad как граф (используем тот же
     React Flow stack), таблица матнов с вариациями, фильтры.
  4. AI assist — Claude API analysis узлов narrator (когда и где
     передавал, оценка muhaddith'ов).
- **Приоритет:** High (новая платформенная фича, требует
  дизайн + ETL + UI).
- **Объём:** 5-10 сессий минимум.

### 2.7 Observability — logging / metrics / traces

- **Запрос:** «продумать и проработать логирование, метрики, трейсы
  и т.п. — всё что связано с Observability».
- **Нынешнее:** SLF4J logging local, Spring Actuator metrics
  (limited).
- **Скоп:**
  1. Structured logging — Logback JSON encoder, MDC user_id +
     request_id, log retention rotation.
  2. Metrics — Micrometer + Prometheus, key metrics: HTTP latency
     per endpoint, DB query time, JVM heap, queue depth (AI edit,
     OCR pools).
  3. Tracing — OpenTelemetry (auto-instrumentation for Spring,
     manual spans для AI/OCR pipelines).
  4. Frontend — error reporting (Sentry-like), perf marks.
- **Приоритет:** Important (production readiness).
- **Объём:** 2-3 сессий.

---

## 3. Workflow / приоритезация

Порядок выполнения (rough):

1. **0.x Критические баги** — Quick fixes ASAP. Должны быть
   закрыты до больших фич.
2. **1.x UI polish** — параллельные мелкие commits, каждый через
   `/frontend-design` skill.
3. **2.4 Roles** — фундамент для 2.5 (guest), 2.6 (scholar
   capabilities в hadith explorer).
4. **2.1 Rating** + **2.3 Shamela search** — parallel, оба
   touching backend search/sort infra.
5. **2.2 Library collections** — после 2.4 (per-user features).
6. **2.5 Guest** — после 2.4 (proper role model).
7. **2.6 Hadith explorer** — большой scope, нужен отдельный design
   sprint (spec doc в этой же папке).
8. **2.7 Observability** — параллельно с любым из 2.x как infra
   investment.

---

## 4. Workflow rules для этой и subsequent сессий

- **«НЕ ОСТАНАВЛИВАЙСЯ ПОКА НЕ СКАЖУ СТОП»** — MAX autonomy, без
  «продолжить?» / «коммитить?».
- **Subagents для context conservation** — каждая нетривиальная
  задача (исследование + plan) делегируется через `Agent` tool,
  возвращает structured report.
- **При перепутьях — recommended option** + commit (Абдула review'ит
  asynchronously).
- **Перед UI changes** — invoke `frontend-design` skill (per Абдула's
  explicit request в этой сессии).
- **Тестируй через Playwright** — UI самый багованный участок.
  Headless WSL2 после каждого UI commit.
- **Все цели — структурно** в backlog / spec'и (этот документ +
  backlog.md) чтобы будущие сессии не потеряли scope.
- **Атомарные коммиты** — каждое решение = отдельный commit, с
  conventional commit + scope.

---

## 5. Acceptance criteria для этой volna работ

Сессия 49d (и далее continuation) считается успешной если:

- Все 4 critical bug (0.1-0.4) **закрыты** или **escalated** с
  явным reason.
- Минимум 3 UI polish item (1.x) **закрыты**.
- Минимум 2 платформенные фичи (2.x) имеют **spec doc** в этой
  папке (готовые к implementation).
- Минимум 1 платформенная фича (2.x) **начата** (хотя бы miграция
  + backend skeleton).
- Все subsequent сессии могут продолжить через `docs/progress.md`
  «Следующий шаг» без вопросов.
