# Design spec: Rating + sorting для Topics / Q&A / Library listings

**Дата:** 2026-05-20
**Автор:** Абдула + brainstorming
**Статус:** approved, ожидает implementation plan
**Источник запроса:** `docs/specs/2026-05-20-vision-expansion-49d.md`
Section 2.1
**Связанные ADR (existing):** ADR-030 (NodeVote ±1, только nodes),
ADR-032 (Q&A model без vote system), ADR-043 (visibility/members),
ADR-046 (rate-limit filter pattern - переиспользуем для view anti-spam)
**Связанные ADR (будут созданы):** ADR-051 (popularity ranking
стратегия), ADR-052 (view-tracking + anti-spam), ADR-053 (Q&A vote
system, если внедряем)

---

## Контекст

Три listings (`GET /topics`, `GET /questions`, `GET /library/books`)
возвращают `PagedResponse<T>` с хардкодом `ORDER BY created_at DESC`
(см. backend/CLAUDE.md «Pagination»). Пользователь не видит «что
популярное и актуальное» - только хронологию. При росте до 200+ тем
discovery ломается: первые 20 - старые черновики.

NodeVote (ADR-030) уже есть - но это per-node вес аргумента,
не per-entity popularity. Q&A и Library никаких vote/view полей не имеют.
`acceptedAnswerId` - единственный сигнал «решено». `answerCount` -
не хранится.

**Не путать с NodeVote:** NodeVote - вес узла внутри графа темы.
Rating в этом spec - per-entity score для list discovery. Они
ортогональны (тема может иметь 1000 upvote'нутых узлов и 0 view'ов
на саму тему).

## Цель

1. Popularity-based sorting в трёх listings (`?sort=`) с
   backward-compat default `recent`.
2. 4 phase'ы, каждая ship'абельна независимо: Phase 1 даёт UI selector
   с derived метриками, Phase 2-3 - view/vote tracking, Phase 4 -
   tuning composite score на реальной data.
3. Не блокирует другие streams (rating - изолированный feature area).

## Не входит

- Полнотекстовый поиск (vision spec 2.3 - отдельный этап).
- Trending / activity feed (производная от rating, defer).
- Cursor-based pagination (offset достаточен до 10k+ rows - backlog).
- Per-user personalized ranking (over-engineering).
- Book votes (см. Q5 - citation_count - сильнее сигнал).

## 1. Current state inventory

### 1.1 Listings endpoints

| Endpoint | Controller | Repository |
|---|---|---|
| `GET /api/v1/topics` | `TopicController.list` | `TopicRepository.findVisibleToUserPage` (+ `findAllPage` для ADMIN) |
| `GET /api/v1/questions` | `QuestionController.list` | `QuestionRepository.findPage` |
| `GET /api/v1/library/books` | `BookController.list` | `BookRepository.findVisibleToUserPage` |

Все три возвращают `PagedResponse<T>`, сортируют `ORDER BY created_at DESC` хардкодом.

### 1.2 Frontend list pages

- `frontend/src/apps/argument-map/pages/TopicListPage.tsx` - sort state отсутствует
- `frontend/src/apps/qa/pages/QuestionListPage.tsx` - sort state отсутствует
- `frontend/src/apps/library/pages/BookListPage.tsx` - уже имеет client-side `sortBy: 'latest' | 'alphabetical'` (комментарий: «Server-side sort через ?sort= - в backlog»). Становится template для двух других после backend-overhaul.

### 1.3 Existing signals для популярности

- **Topics:** `node_count` + `edge_count` (LEFT JOIN, уже в TopicWithCounts); sum `node_votes.weight` ALL узлов темы доступен через JOIN.
- **Q&A:** `acceptedAnswerId` (boolean), `updated_at` (last activity), COUNT(answers) через subquery.
- **Library:** только `created_at`; citation_count через JOIN `node_sources` + `answer_sources`. `collection_count` появится с vision 2.2.

## 2. Popularity semantics

### 2.1 Topics

| Signal | Source | Phase | Weight (P4) |
|---|---|---|---|
| `vote_score` | denormalized SUM(node_votes.weight всех узлов) | 1 | 0.3 |
| `node_count` | existing COUNT | 1 | 0.2 |
| `view_count` | `topics.view_count` (new col) | 2 | 0.3 |
| `recency` | NOW() - created_at, linear decay | 1 | 0.2 |

«node_count = есть содержание, vote_score = качество аргументов,
view_count = discovery interest».

### 2.2 Q&A questions

| Signal | Source | Phase | Weight (P4) |
|---|---|---|---|
| `answer_count` | denormalized COL | 1 | 0.3 |
| `accepted_bonus` | flat +50 если `accepted_answer_id IS NOT NULL` | 1 | n/a |
| `view_count` | `questions.view_count` (new col) | 2 | 0.3 |
| `vote_score` | sum `question_votes.weight` (new table, Phase 3) | 3 | 0.2 |
| `recency` | NOW() - updated_at (last activity, не created_at) | 1 | 0.2 |

### 2.3 Library books

| Signal | Source | Phase | Weight (P4) |
|---|---|---|---|
| `citation_count` | denormalized COUNT `node_sources` + `answer_sources` | 1 | 0.4 |
| `view_count` | `lib_books.view_count` (new col) | 2 | 0.4 |
| `recency` | created_at, longer decay window (90d) | 1 | 0.1 |
| `collection_count` | `user_book_collections` (vision 2.2) | 4 | 0.1 |

Recency весит меньше т.к. библиотека академическая - классика
остаётся популярна.

## 3. Schema migrations

### 3.1 Phase 1 - минимальные columns

**Миграция 49 (`20260520-49-topic-popularity-columns.xml`):**

- `ALTER TABLE topics ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0`
- `ALTER TABLE topics ADD COLUMN vote_score INTEGER NOT NULL DEFAULT 0`
- Composite index `idx_topics_popularity ON topics ((vote_score + view_count/10), created_at DESC)`
- Backfill SQL внутри changeset:
  `UPDATE topics t SET vote_score = COALESCE((SELECT SUM(nv.weight) FROM node_votes nv JOIN nodes n ON n.id = nv.node_id WHERE n.topic_id = t.id), 0)`
- `<rollback>`: DROP INDEX + DROP COLUMNS

**Миграция 50 (`20260520-50-question-popularity-columns.xml`):**

- `view_count BIGINT DEFAULT 0`, `answer_count INTEGER DEFAULT 0`
- Index `idx_questions_popularity`
- Backfill: `UPDATE questions q SET answer_count = (SELECT COUNT(*) FROM answers WHERE question_id = q.id)`

**Миграция 51 (`20260520-51-book-popularity-columns.xml`):**

- `view_count BIGINT DEFAULT 0`, `citation_count INTEGER DEFAULT 0`
- Index `idx_books_popularity`
- Backfill: `UPDATE lib_books b SET citation_count = COALESCE((SELECT COUNT(*) FROM node_sources WHERE book_id = b.id), 0) + COALESCE((SELECT COUNT(*) FROM answer_sources WHERE book_id = b.id), 0)`

Backfill - один SQL в той же миграции (после ADD COLUMN). Приемлемо
при текущих объёмах (<10k topics). Если вырастет до миллионов -
вынести в отдельный one-off job (backlog).

### 3.2 Denormalization trade-off

**Решение:** **denormalize counters в row**, event-sync через mutating
services.

**Pro:** sort через PG index O(log N) на каждый GET list (vs O(N)
aggregate каждый раз). При 1000 list-req/час - 0 extra query'ов
vs 1000 SUM-аггрегатов.

**Cost:** sync logic в 5-7 mutating services. Защита от drift -
nightly `PopularityReconcileJanitor` (см. 3.4) + IT-тест
«создать vote → counter +1».

### 3.3 Phase 2 - View tracking

**Миграция 52 (`20260520-52-entity-views.xml`):**

```sql
CREATE TABLE entity_views (
    id UUID PRIMARY KEY,
    entity_type VARCHAR(20) NOT NULL,  -- TOPIC | QUESTION | BOOK
    entity_id UUID NOT NULL,
    user_id UUID,        -- nullable, anonymous
    session_id VARCHAR(64),
    viewed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT entity_views_type_check CHECK (entity_type IN ('TOPIC','QUESTION','BOOK'))
);
CREATE INDEX idx_entity_views_entity ON entity_views (entity_type, entity_id, viewed_at);
```

Hot-path: NO sync write на каждый view. Async `@Async
ViewTrackingService.recordView()` создаёт row + `UPDATE ... SET view_count = view_count + 1`.

**Anti-spam:** 1 view per (user/session, entity) per 30 min.
In-memory sliding window (mirror RateLimitFilter pattern, ADR-046).

### 3.4 Phase 3 - Q&A vote system

**Миграция 53 (`20260520-53-question-votes.xml`):**

```sql
CREATE TABLE question_votes (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    weight INTEGER NOT NULL,
    voted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT question_votes_weight_check CHECK (weight IN (-1, 1)),
    CONSTRAINT question_votes_unique UNIQUE (question_id, user_id)
);
```

Структура mirrors `node_votes` (ADR-030). + `ALTER TABLE questions ADD COLUMN vote_score INTEGER DEFAULT 0`.

### 3.5 PopularityReconcileJanitor (Phase 1 stub, Phase 4 enable)

`service/`, `@ConditionalOnProperty(popularity.reconcile.enabled=true)`,
cron `02:30` (после audit-log retention 02:00). Default off; в prod
включается в Phase 4. Запускает три backfill SQL (как в Phase 1).
Safety-net от drift.

## 4. REST extensions

### 4.1 `?sort=` query param

Все три list endpoint'а принимают `?sort={recent|popular|alphabetical}`:

- `recent` (**default - backward-compat**) - `ORDER BY created_at DESC`
  (или `updated_at DESC` для Q&A).
- `popular` - `ORDER BY <popularity-formula> DESC, created_at DESC`.
- `alphabetical` - `ORDER BY LOWER(title) ASC, created_at DESC`
  (locale-aware collation для unicode/арабского).

Invalid sort → 400 `invalid-sort-key`.

**Default `recent` (не `popular`):**
1. Backward-compat для existing frontend (не передаёт `sort=`).
2. После Phase 1 у всех existing rows score=0 → случайный порядок.
3. Frontend default свободно меняется в Phase 4 (product decision,
   тривиальный change).

### 4.2 Popularity formula в SQL

**Topics:**
```sql
ORDER BY (
    vote_score
    + view_count / 10
    + node_count * 2
    + EXTRACT(EPOCH FROM (created_at - NOW())) / 86400 / 30
) DESC, created_at DESC
```

**Questions:**
```sql
ORDER BY (
    answer_count * 10
    + view_count / 50
    + CASE WHEN accepted_answer_id IS NOT NULL THEN 50 ELSE 0 END
    + vote_score * 5
    + EXTRACT(EPOCH FROM (updated_at - NOW())) / 86400 / 30
) DESC, updated_at DESC
```

**Books:**
```sql
ORDER BY (
    citation_count * 10
    + view_count / 50
    + EXTRACT(EPOCH FROM (created_at - NOW())) / 86400 / 90
) DESC, created_at DESC
```

Веса - stub'ы. Финализация - Phase 4 по реальной data distribution.

### 4.3 View tracking endpoints (Phase 2)

- `POST /api/v1/topics/{id}/views` - body пустой, 204
- `POST /api/v1/questions/{id}/views`
- `POST /api/v1/library/books/{id}/views`

**Альтернатива (отвергнута):** автотрек на GET через middleware. Минус -
GET идемпотентен по REST контракту, side effect break'нет caching.
Frontend explicit POST на mount detail page - cleaner contract.

**Auth:** не требуется. userId если есть, иначе `X-Session-Id`
header (frontend генерирует UUID в localStorage).

### 4.4 Vote endpoints (Phase 3)

Mirror NodeVoteController:
- `POST /api/v1/questions/{id}/vote` body `{weight: -1 | 1}`, auth
- `DELETE /api/v1/questions/{id}/vote`
- `GET /api/v1/questions/{id}/votes` - VoteStats aggregate

Idempotent: повторный POST с тем же weight = no-op; opposite weight = update.

### 4.5 api-contract.md updates

Новый раздел «Sorting и popularity»:
- `?sort` allowed values + default `recent`
- Formula description
- View tracking endpoints (Phase 2)
- Vote endpoints для Q&A (Phase 3)
- problem-detail `invalid-sort-key`

### 4.6 OpenAPI / generated types

После Phase 1 backend: `npm run generate-api`. В `TopicResponse`,
`QuestionResponse`, `BookSummaryResponse` добавляются `viewCount`,
`voteScore`, `answerCount`, `citationCount` - frontend отрисовывает
metric badges.

## 5. Frontend changes

### 5.1 SortSelect dropdown

Reusable `<SortSelect value={sort} onChange={setSort} />` в
`shared/components/ui/`. На каждой list page:

```tsx
type SortKey = 'recent' | 'popular' | 'alphabetical';
const [sort, setSort] = useState<SortKey>(() =>
  (searchParams.get('sort') as SortKey) || 'recent'
);
// useEffect refetch при изменении sort (mirror statusFilter в QuestionListPage)
```

URL deep-link: `?sort=popular&page=0&size=20`.

### 5.2 Metric badges на cards

Phase 1+:
- TopicCard: badge `vote_score` если > 0 (текущие node/edge counters остаются)
- QuestionCard: badge `answer_count` + acceptedAnswerId (уже есть)
- BookCard: badge `citation_count` если > 0

Phase 2+: eye icon + `view_count` если > 0.

Conditional render: `{count > 0 && <Badge>...</Badge>}` - не шумим
свежие entity с «0 views, 0 votes». Locale-aware plurals через
`useT()` («1 ответ» / «2 ответа» / «5 ответов»).

### 5.3 BookListPage migration

Удалить client-side JS sort в `BookListPage.tsx:88-92`. UI selector
тот же, отправляет `?sort=` server-side при изменении.

### 5.4 useViewTracking hook (Phase 2)

`shared/hooks/useViewTracking.ts`:

```tsx
useEffect(() => {
  if (!entityId) return;
  const key = `viewed:${type}:${entityId}`;
  if (sessionStorage.getItem(key)) return;
  sessionStorage.setItem(key, '1');  // mirror backend 30min window
  apiPost(`/api/v1/${path}/${entityId}/views`, {}).catch(() => {});
}, [entityId, type, path]);
```

Integration в `TopicGraphPage`, `QuestionDetailsPage`, `BookReaderPage`.
Fire-and-forget, ошибку логируем, не показываем.

### 5.5 QuestionVoteWidget (Phase 3)

Mirror existing NodeVoteButton: up/down arrows + score. Optimistic
update: click → setState immediately + POST → revert на 4xx.

## 6. Test plan

**Backend IT (~30 новых):**
- Phase 1 (~12): `Topic/Question/BookRepositoryIT` × `findPage_sortRecent/Popular/Alphabetical` + `*ControllerIT` × `sortParam_passed/invalidSort_returns400` + `PopularityBackfillIT`
- Phase 2 (~10): `EntityViewsRepositoryIT` (insert/increment/dedupe), `ViewTrackingServiceIT` (anti-spam, session anonymous), 3× `*ViewControllerIT`
- Phase 3 (~8): `QuestionVoteRepositoryIT`/`ServiceIT`/`ControllerIT` (mirror NodeVote pattern)

**Frontend Vitest (~12 новых):**
- `SortSelect`, `TopicListPage`/`QuestionListPage`/`BookListPage` (sort + badges), Phase 2 `useViewTracking`, Phase 3 `QuestionVoteWidget` (optimistic + revert)

**Manual playwright smoke** после каждой Phase: sort dropdown меняет URL+order; answered Q&A выше unanswered при `popular`; view counter инкрементируется при mount detail page; vote виджет обновляет score optimistic.

## 7. Rollout plan (rough hours)

### Phase 1 - sort UI + derived metrics (~20 ч)

| Подэтап | Часы |
|---|---|
| 1.a | Миграции 49/50/51 + columns/indexes + backfill SQL + reconcile janitor stub | 2 |
| 1.b | Repository sort param + popularity formula × 3 | 3 |
| 1.c | Controllers `?sort=` + validation + 400 | 1 |
| 1.d | NodeVoteService sync `topics.vote_score` | 2 |
| 1.e | AnswerService sync `questions.answer_count` | 1.5 |
| 1.f | NodeSourceService/AnswerCitationService sync `lib_books.citation_count` | 2 |
| 1.g | Backend IT (12) | 3 |
| 1.h | Frontend SortSelect + 3 list pages + metric badges | 4 |
| 1.i | Frontend Vitest + api-contract.md + ADR-051 | 2 |

### Phase 2 - view tracking (~9.5 ч)

| Подэтап | Часы |
|---|---|
| 2.a | Миграция 52 + EntityViewsRepository | 1.5 |
| 2.b | ViewTrackingService (async + anti-spam) | 3 |
| 2.c | 3 POST endpoints + IT | 2 |
| 2.d | Frontend useViewTracking + 3 detail pages | 2 |
| 2.e | ADR-052 + api-contract.md | 1 |

### Phase 3 - Q&A votes (~9 ч)

| Подэтап | Часы |
|---|---|
| 3.a | Миграция 53 + QuestionVoteRepository | 1.5 |
| 3.b | QuestionVoteService + sync vote_score | 2 |
| 3.c | QuestionVoteController + endpoints + IT | 2 |
| 3.d | Frontend QuestionVoteWidget | 2.5 |
| 3.e | ADR-053 + api-contract.md | 1 |

### Phase 4 - tuning (~3.5 ч)

| Подэтап | Часы |
|---|---|
| 4.a | Production data analysis (read-only) | 1 |
| 4.b | Adjust weights в SQL (constants change) | 0.5 |
| 4.c | Frontend default sort `popular` (один commit) | 0.5 |
| 4.d | Enable PopularityReconcileJanitor в prod | 0.5 |
| 4.e | Code review + handoff | 1 |

**Grand total:** ~42 ч = ~5-6 сессий. Каждая Phase ship'абельна.
Phase 1 - must, Phase 2-3 - should, Phase 4 - nice-to-have tuning.

## 8. Acceptance criteria

**Phase 1:**
- [ ] Миграции 49/50/51 применены + backfill корректен (spot-check 5 rows)
- [ ] `?sort={recent|popular|alphabetical}` × 3 endpoint'а; invalid → 400 `invalid-sort-key`; default остаётся `recent`
- [ ] sync `topics.vote_score` (NodeVoteService), `questions.answer_count` (AnswerService), `lib_books.citation_count` (citation services)
- [ ] Frontend SortSelect × 3 страницы, URL deep-link, metric badges условный render
- [ ] Backend IT 12 + Vitest 6 зелёные; api-contract.md + ADR-051

**Phase 2:**
- [ ] Миграция 52 применена; POST `.../views` записывает row + инкрементирует counter; anti-spam dedupe 30min (user/session); anonymous через X-Session-Id
- [ ] Frontend useViewTracking один POST per mount; view_count badge в listings
- [ ] IT 10 + Vitest 3 зелёные; ADR-052

**Phase 3:**
- [ ] Миграция 53 применена; POST/DELETE `/questions/{id}/vote` работает с auth; `questions.vote_score` синхронизирован
- [ ] Optimistic frontend update + revert на error
- [ ] IT 8 + Vitest 3 зелёные; ADR-053

**Phase 4:**
- [ ] Веса formula adjusted; default frontend sort = `popular`; PopularityReconcileJanitor включён в prod; backlog item «ре-проверить через 2 недели»

## 9. Risks / open questions

**Q1: Denormalize vs derived.** Denormalize (см. 3.2). Cost - 5-7
sync points + reconcile cron + IT-тест.

**Q2: View tracking - GET middleware vs POST.** POST explicit (4.3).
GET идемпотентен, side effect break'нет caching contract. Frontend POST
на mount detail page - cleaner contract.

**Q3: Anonymous view tracking - dedupe без user_id.** `X-Session-Id`
header (UUID в localStorage). Spoofable, но accept'абельно для view
counter (не security boundary). Cookie альтернатива - extra config.
При abuse - rate-limit IP-level (ADR-046 pattern).

**Q4: Default sort - API vs UI.** API default = `recent` навсегда
(backward-compat). Frontend default = `popular` после Phase 4 (UX
decision, тривиальный change).

**Q5: Book votes.** Defer / не делаем. `citation_count` - сильнее
сигнал «книга реально используется», чем «лайкнул не читая».
Если explicit запрос будет - отдельный ADR.

**Q6: Decay - linear vs exponential.** Phase 1: linear. Phase 4
потенциально: exponential (`exp(-age_days / half_life)`, PG `exp()`
есть). Решение по data distribution.

**Q7: Sort × visibility filters.** Sort применяется внутри filter
result (WHERE visibility → ORDER BY sort). Index не covers visibility -
filter+sort план. Если slow - composite index `(visibility, popularity_expr)`
backlog optimization.

**Q8: BookListPage MINE filter vs server sort.** MINE filter в
backlog → server-side (ADR-043 Amendment). Пока - sort работает, MINE
visible only на загруженной странице (acceptable for MVP).

**Q9: AuditLog для view tracking?** Нет. High-volume, low-value
individually. Если privacy compliance потребует - добавим, не Phase 1-3.

**Q10: Metric badge при score = 0?** Не показывать. `{count > 0 && <Badge/>}`.
Visual noise иначе - все свежие entity с «0 votes».

## 10. Decomposition (для implementation plan)

Подэтапы перечислены в таблицах Section 7 (`1.a` … `4.e`). Каждый
подэтап - один атомарный commit с своими IT. Естественные handoff
точки: после 1.i (rating MVP in prod), после 2.e (view tracking live),
после 3.e (votes live). Phase 4 - cleanup pass когда есть production
data для analyze.
