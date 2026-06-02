# Журнал работы

Последние сессии. Новые записи - **сверху**

Формат записи описан в `docs/doc-hygiene.md` Принцип 5

**Архив:** `docs/archive/progress-sessions-{1-21,22-29,30-37,38-45,46-52}.md`
(сессии ≤52). Здесь — 53+.

<!-- NEWEST-ENTRY-ANCHOR -->

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
shamela guard) + 6 ручных багов + код-ревью + флак — **ЗАКРЫТЫ**. Остаётся
ОПЦИОНАЛЬНОЕ/отложенное: IsnadExtraction (AI, контент — Абдула отложил); полный
in-place рендеринг hadith-сборника как книги (сейчас редирект достаточен); shamela
`category.sqlite` sync (зависит от живого shamela.ws); визуальная playwright-проверка
(env-blocked, Chromium отсутствует). **БД пуста — наполнять через /admin tools.**

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
`docs/superpowers/specs/2026-06-02-preprod-ux-overhaul.md` (источник истины) +
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

