# Стартовый промпт для новой сессии Claude Code

Этот файл - **стабильный** контекст начала любой сессии. Обновляется
только раздел «Текущий приоритет» (в конце документа). Остальное -
постоянное

Правила эволюции этого файла - в `docs/doc-hygiene.md` Принцип 6.
Если файл вырос за 400 строк - вылавливай дубли с CLAUDE.md /
progress.md / roadmap.md и выкидывай

---

## Режим работы

**Полная автономия + ruflo-first.** Двигаемся в сторону полного
перехода на ruflo way работы со всех сторон (subagents, memory,
session continuity, tasks, learning, code review). Strategic direction
зафиксирован в `mcp__ruflo__memory_retrieve namespace=argument-map
key=strategic-direction-ruflo-migration`.

Правила автономии и subagent usage:
- `mcp__ruflo__memory_retrieve namespace=argument-map key=autonomy-mode`
  - текущий snapshot правил с semantic recall (ruflo-first variant
  v2 от 16.05). Subagents - **ВПРЕДЬ через ruflo**: для long-running
  implementation tasks `mcp__ruflo__hive-mind_spawn` или
  `mcp__ruflo__swarm_init`+`agent_spawn`, для quick research можно
  нативный Agent subagent_type=Explore
- `mcp__ruflo__autopilot_status` - состояние long-horizon resumption
  (включён 16.05, maxIterations=200, timeoutMinutes=720)
- `mcp__ruflo__agentdb_pattern-search` - cross-session architectural
  patterns (например ADR-033 «параллельная иерархия» сохранён как
  `type=architectural-decision`)

Новая сессия первым делом делает `mcp__ruflo__memory_retrieve`
для `autonomy-mode` + `strategic-direction-ruflo-migration` перед
чтением остальной документации.

---

## START-OF-SESSION PROTOCOL

Перед первым ответом в новой сессии **выполни**:

### 1. Прочитай в таком порядке

1. **Ruflo memory** - `mcp__ruflo__memory_retrieve namespace=argument-map
   key=autonomy-mode` - правила автономии. Ранее были в `feedback_full_
   autonomy_mode.md`, теперь живут в ruflo с semantic recall между
   сессиями. И `key=argument-map-19b-completion-state` для последнего
   completion snapshot
2. **`CLAUDE.md`** (корень) - стэк, команды, layout, навигация по
   документации - уже в твоём контексте при старте
3. **`docs/progress.md`** - последние 2-3 записи + «Следующий шаг»
4. **`docs/roadmap.md`** - текущий приоритетный этап. Закрытые
   этапы свёрнуты в одну строку, активные имеют чек-лист
5. **«Текущий приоритет»** ниже в этом файле - что Абдула или
   предыдущая сессия зафиксировали как next step

### 2. По мере работы читай по запросу

- `docs/decisions.md` - если задача в принципиальной области
  (миграция, API contract, новый домен). Полный файл большой -
  читай по grep'у, не целиком
- `docs/gotchas.md` - перед миграцией / тонким Spring/JDBC кодом /
  фронтом с React Flow или RTL
- `docs/architecture.md` + `architecture-platform.md` - перед
  новой доменной сущностью или изменением core flow
- `docs/api-contract.md` - перед изменением REST endpoint или
  добавлением поля DTO
- `docs/glossary.md` - когда встретится незнакомый доменный термин
- `docs/backlog.md` - если рассматриваешь добавить новую идею
- `frontend/design-reference/` - **до** UI-изменений (см. memory
  `feedback_design_reference_check.md`)

### 3. Memory и feedback

Два слоя памяти после миграции 16.05:

- **Ruflo memory** (primary) - `mcp__ruflo__memory_retrieve` /
  `mcp__ruflo__memory_search_unified` / `mcp__ruflo__agentdb_pattern-
  search` с `namespace=argument-map`. Sub-cross-session recall через
  HNSW vector store. Сюда переехал autonomy mode + architectural patterns
- **Локальная файловая memory** (legacy) - в
  `~/.claude/projects/-mnt-c-my-folders-projects-argument-map/memory/`.
  Прочитай `MEMORY.md` index при старте - остались feedback'и про
  decision authority, WSL-only, не-частые-билды, React key-trick,
  RTL/наshк, design-reference check, playwright UI verification,
  no bulk shamela parsing, no backward compat. Постепенно мигрировать
  в ruflo при касании

### 4. Проверь актуальное состояние инфры

- `git log --oneline -15` - свежие коммиты
- `docker ps | grep argumentmap-postgres` - БД healthy
- `lsof -ti:9090 -ti:5173` - что-то на портах
- Backend / frontend сам запускай по необходимости (см. CLAUDE.md
  раздел «Команды»). Не жди инструкций

### 5. Скажи Абдуле краткое summary

«вижу - последний раз X, продолжаю с Y из roadmap». Если задача
ясна - сразу за работу, не жди апрува

---

## Документация по ходу работы

После **каждого** `feat`/`fix` коммита проверь чек-лист (детали - в
`backend/CLAUDE.md` или `frontend/CLAUDE.md` секция «После коммита»):

| Что произошло | Что обновить |
|---|---|
| Закрыт пункт roadmap | `roadmap.md` `[x]` |
| Закрыт целый этап | `roadmap.md` - сжать в строку (см. `doc-hygiene.md` Принцип 3) |
| Принято решение между альтернативами | новый ADR в `decisions.md` |
| Миграция БД / новая колонка | ADR + `architecture.md` |
| Новый REST endpoint / поле DTO | `api-contract.md` |
| Поймал баг который может повториться | `gotchas.md` |
| Новое доменное понятие | `glossary.md` |
| Reorg структуры (пути / пакеты) | синхронизация всех мест (см. `doc-hygiene.md` Принцип 8) |

ADR / gotcha / api-contract пишутся **сразу**, не в конце сессии

---

## Декомпозиция и проверки

### Декомпозиция

- Задача больше 1-2 файлов → подэтапы X.a / X.b / X.c
- Между подэтапами - прогон проверок и коммит. Не один большой
- Каждый подэтап имеет внятную границу

### Когда запускать билды/тесты

**Не на каждом чихе**. Полный прогон делается **по факту**:

- В конце завершённой логической фазы
- Перед коммитом если в фазе были средние/крупные изменения
- Когда есть конкретный сигнал что что-то могло сломаться

Команды:
- Фронт: `npm run lint && npm run build && npm run test:run`
- Бэк: `./mvnw verify`
- Smoke через curl с `X-User-Id` после прохождения тестов

См. memory `feedback_no_frequent_builds.md`

---

## Контрольные точки качества handoff'а

При закрытии сессии новая сессия должна получить:

1. **Что закрыто** - запись в `progress.md` без переписывания git log
2. **Что открыто и в каком приоритете** - раздел «Текущий приоритет»
   ниже в этом файле + чек-лист в `roadmap.md`
3. **Контекст последних решений** - ADR-N или ссылка на новые
   gotcha если они были
4. **Текущая инфра** - порты / UUID / тестовая тема (если изменились)
5. **Ключевые файлы** - если в текущей задаче трогаешь редкие части
   репы и они без этой подсказки сложно найти

В конце сессии **обязательно**:

- запись в `progress.md` по формату (см. `doc-hygiene.md` Принцип 5)
- `roadmap.md` обновлён - закрытые подэтапы `[x]`, закрытые целиком
  этапы сжаты в строку
- «Текущий приоритет» ниже **переписан** под следующую сессию
- если изменилась структура / пути - синхронизация согласно
  `doc-hygiene.md` Принцип 8
- `progress.md` > 1500 строк? - архивировать в
  `docs/archive/progress-sessions-N-M.md`

---

## Текущий приоритет

> **Этот раздел обновляется каждой сессией**. Всё выше - стабильное

**Сессия 38 - 4 backend commits, code review fixes Сессии 37 закрыты
end-to-end** (1 critical + 3 important). Главное - **Этап 16.h
UserUploadProvider** теперь uploaded PDF читается через `/pdf/info` +
`/pdf` endpoints (раньше 404). Полный лог в `docs/progress.md` запись
«2026-05-17 - Сессия 38, post-review fixes Этапа 16».

**Сессия 37 (предыдущая)** - 12+ коммитов, 4 subagents, Этап 16 PDF
upload end-to-end + 25.b RetryStrategy.

### Закрыто в Сессии 38

- **Этап 16.h UserUploadProvider** (commit `b5d4cc4`) - critical issue
  code review. Новый PdfSourceProvider (@Order=50) для USER_UPLOAD
  blob'ов, GET `/pdf/info` и `/pdf` теперь работают для uploaded книг.
  +11 IT (UserUploadProviderIT + E2E в FileImportControllerIT).
  Smoke на dev: real upload + curl → 200 со streaming PDF
- **BucketBootstrap idempotent** (commit `dcfdf24`) - catch
  `BucketAlreadyOwnedByYouException` + `BucketAlreadyExistsException`
  при concurrent startup двух pod'ов
- **language whitelist** (commit `5c5277e`) - `Set.of("ar","ru","en")`
  в FileImportController, mirror frontend
- **FileImportService комментарий** (commit `f9519c0`) - уточнено что
  порядок «save pages → put S3» защищает от blob без pages, не наоборот

### Закрыто в Сессии 37

- **Этап 16 PDF upload end-to-end** (commits `37edb5d`/`da0da7a`/
  `e224650`/`72a3f3e` backend + `a696c51`/`3e94d84`/`863de6b`
  frontend + `945d4b9`/`9dbfac4`/`85a9093` academic extension 16.g):
  PDFBox 3.x page-by-page extraction, `POST /api/v1/library/imports/file`
  multipart до 50MB, MinIO `library-user-uploads` bucket с
  BucketBootstrap, admin FileUploadModal на `/admin/shamela`,
  collapsible academic секция через shared AcademicMetadataFields
  из 20.e. **ADR-035** PDFBox vs Tika. EPUB отложен (нет UX-кейса)
- **25.b operational hardening полностью закрыт** (последний пункт)
  - commit `096f119` - RetryStrategy API вместо deprecated
  RetryPolicy в S3ClientConfig. `AwsRetryStrategy.standardRetryStrategy()`,
  `maxAttempts = numRetries + 1` (новый API считает initial attempt).
  ADR-024 Amendment
- **F-2 cleanup** (commit `1d8d361`) - unrelated AddSourceModal
  reliability radio test починен (искал английский label
  "/reliability/", после i18n стал "Степень достоверности")
- **F-1 cleanup** - package.json/lock в корне уже в .gitignore с
  Сессии 36, физические файлы нужны ruflo MCP

### Опции для Сессии 39 (по приоритету)

**Опция A - Этап 17 OCR pipeline** (продолжение 16) - для
scanned-PDF где `text_content=""` (subagent D зафиксировал что
check constraint `lib_pages_content_present` проверяет только NULL,
не emptiness). Tess4j + arabic `ara` training data + async через
`@Async` + admin re-OCR endpoint. Релевантно сейчас потому что
свежее знание про PDF processing. ~2 сессии backend+frontend

**Опция B - Импорт/экспорт темы в JSON** (Этап 6 backend) - serializer
Topic + nodes + edges + node_sources + question_sources +
answer_sources в JSON. Endpoints `POST /api/v1/topics/{id}/export`
+ `POST /api/v1/topics/import`. Frontend кнопки в TopicListPage.
Полезно для backup и обмена темами. **Не блокируется ничем
внешним**. ~1 сессия

**Опция C - 25.d.5 Lazy PDF streaming через backend** - сейчас
`PdfLinksSourceProvider.downloadFile` качает **весь PDF** на бэк
перед отдачей frontend. Лучше форвардить Range-request frontend →
archive.org → отдавать chunks. Performance + memory улучшение.
Связано с ADR-023. ~1-2 сессии

**Опция D - Responsive/mobile sweep** - первый pass по существующим
pages для tablet/mobile (BookReaderPage, AdminShamelaPage с
FileUploadModal, CitationPicker, NodeDetailsPanel). Подготовка к
production. ~1 сессия. См. `docs/backlog.md` раздел Responsive

**Опция E - Полнотекстовый поиск Postgres tsvector** (Этап 6) -
GIN индекс на `nodes.body` + `lib_pages.text_content` + REST
endpoint. Актуально когда контента станет много (после Этапа 17
OCR будет много text content в БД)

**Опция F - Source picker для Корана / Хадисов** (B/C из Сессии 37
опций) - таб «Коран» / «Хадисы» в CitationPicker. **Зависит от
внешнего API** (quran.com / sunnah.com) - может блокироваться

**Опция G - NodeCard footer chips (18.h.A1)** - deferred low value
«duplicate данные с header meta-row». 30 мин closeout. Решить -
закрыть в roadmap или вообще удалить пункт

**Опция H - Этап 21 Spring Security + JWT** - реальная
аутентификация. Большая работа (>2 сессий)

### Инфра на момент Сессии 39 entry

- Postgres :5432 healthy, миграции **до 31 включительно** applied
  (без новых в Сессии 37)
- MinIO :9000 healthy + 4 bucket'а инициализированы через
  BucketBootstrap (включая новый `library-user-uploads` для PDF
  upload). Versioning enabled
- Backend :9090 + JDWP :5005 - перезапущен с новым classpath
  (PDFBox 3.0.5 + FileImportService + RetryStrategy API +
  BucketBootstrap). Стартует с логом
  «bucket bootstrap завершён - все 4 bucket'а доступны»
- Frontend :5173 - может потребовать `rm -rf node_modules/.vite`
  после массовых изменений (types.ts регенерация после 16.b/16.g)
- **554 backend tests** (543 после Сессии 37 + 11 от Этапа 16.h:
  9 UserUploadProviderIT + 2 в FileImportControllerIT (E2E + language))
- **156 frontend tests** (147 baseline + 5 от 16.f + 4 от 16.g = 156)
- **0 failing** (унрелейтед AddSourceModal reliability fail починен)

### Реальные artefacts в production-БД для smoke

- Test question `3796f633-1822-45fa-87e1-6337a603b6f1` с
  attached citation (19.b validation)
- Test answer `8872e584-ca2e-4b33-95ce-f3e545df55bb` со status
  ANSWERED + attached citation (19.c + 19.d validation)
- Source `132d75cc-cf4e-4d24-beb3-a4859ba0b776` (тафсир Ибн Касира)
  reused между 3 entity types (физический proof ADR-018)
- **Книги загруженные через FileImportController** - проверить
  через `psql ... -c "SELECT id, title, page_count FROM lib_books
  WHERE bucket = 'library-user-uploads' ORDER BY created_at DESC
  LIMIT 5"` либо `mc ls local/library-user-uploads/` (если пользовался
  upload UI вживую)

### Ruflo memory keys (Сессия 36-37) для load в новой сессии

```
mcp__ruflo__memory_retrieve namespace=argument-map key=...
```

Priority (читать в порядке):
1. `autonomy-mode` - правила автономии (ruflo-first v2)
2. `ruflo-max-utilization-rule` - всегда использовать ruflo на максимум
3. `strategic-direction-ruflo-migration` - long-term goal
4. `session-36-final-snapshot` - снэпшот Сессии 36
5. `e2e-platform-validation-3-entities` - production E2E proof
6. `test-regression-fix-plan` - Node 24 undici bug verdict

Architectural patterns в agentdb_pattern-store:
- `mcp__ruflo__agentdb_pattern-search query="параллельная иерархия"`
  → ADR-033 pattern с confidence=0.95 (validated 3 times)
- `mcp__ruflo__agentdb_pattern-search query="academic metadata 2-step"`
  → ADR-028 + 20.e + 16.g pattern (BookEditModal / AddSourceModal /
  FileUploadModal все используют shared AcademicMetadataFields,
  validated 3 times)

### Auto memory путь

Memory **перенесена** в новый путь
`~/.claude/projects/-home-basnukaev-projects-argument-map/memory/`
(было `-mnt-c-my-folders-projects-argument-map`). 20 файлов
скопированы, старый путь оставлен как backup. Auto memory harness
в новой сессии должен подхватить новый путь автоматически (CWD
hash совпадает с `/home/basnukaev/projects/argument-map/`).

Если в Сессии 38 убедимся что новая память работает -
`rm -rf ~/.claude/projects/-mnt-c-my-folders-projects-argument-map/`
можно удалить старый (включая `.jsonl` transcript'ы старых сессий)

### Известные мелочи (не блокеры) для Сессии 38

- **progress.md = 1552 строки** - превышает 1500 порог из
  `doc-hygiene.md` Принцип 5. Стоит архивировать Сессии 30-36 в
  `docs/archive/progress-sessions-30-36.md` в начале Сессии 38
  (Сессии 22-29 уже в архиве). ~10 минут работы
- **jsdom + node 24 не парсит multipart FormData** - известная
  гочa, в FileUploadModal.test тесты для multipart используют
  mock `globalThis.fetch` напрямую (зафиксировано в комменте теста)
- **PDFBox text_content="" для scanned-PDF** проходит через
  CHECK constraint `lib_pages_content_present` (проверяет NULL не
  emptiness) - это потенциальная гочa для Этапа 17 OCR pipeline,
  фиксируем что OCR будет seed'ить эти пустые text_content
- **Bean Validation для @RequestParam не настроена** - subagent
  D.g обошёл через ручную range-валидацию в controller. Если в
  будущем добавится много multipart endpoints - стоит настроить
  глобально через `@Validated` + handler `HandlerMethodValidationException`
- **playwright WSL2 не загружает Google Fonts** через corp proxy 407
  - визуальная проверка шрифтов только в реальном браузере

