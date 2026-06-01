# Bug-hunt handoff — 2026-06-01 (Сессия 52)

## Контекст

Multi-agent багоохота (Workflow, 235 агентов, 19 finders + 3-линзовая
adversarial-верификация). Результат: **48 подтверждённых** багов из 72
(24 отброшены панелью скептиков). Полный triage:

- `/tmp/bughunt_confirmed.json` — все 48 подтверждённых (полные поля)
- `/tmp/bughunt_rejected.json` — 24 отброшенных + причины
- `/tmp/bughunt_list.txt` — краткий ранжированный список

Если /tmp очищен — перезапустить Workflow `bug-hunt-discovery`
(скрипт сохранён в session workflows/scripts/).

## ВАЖНО: причина прерывания

Сессия 52 стартовала ДО фикса python3.12-venv. Async-хук
security-guidance (`ensure_agent_sdk.py`) упал на сборке venv и
оставил «висящий» async-future → буферизация вывода Bash/Read
(вывод приходит с задержкой/пачками). venv починен (`apt install
python3.12-venv` + снос битого venv), но **текущая сессия всё ещё
с висящим future**. → Продолжать в СВЕЖЕЙ сессии (буфера не будет).

## Сделано (на диске, НЕ закоммичено, НЕ скомпилировано/протестировано)

Чистый baseline: `c2eafe3` (minimap fix) уже закоммичен.
Все правки ниже — uncommitted, Tier-1 security:

1. **Topic export auth (#0 HIGH)** — DONE
   - `web/controller/TopicExportImportController.java`: +PermissionService,
     +@CurrentUser+role, assertCanRead в export()
2. **Q&A accept/revoke auth (#1 HIGH)** — DONE
   - `qa/service/AnswerService.java`: role-aware overloads acceptAnswer/
     revokeAcceptance + assertQuestionAuthorOrAdmin (mirror QuestionService)
   - `qa/web/controller/AnswerController.java`: оба endpoint'а передают
     @CurrentUser+role
3. **Node vote stats leak (#5 MED)** — DONE
   - `service/NodeVoteService.java`: getStatsForNode(nodeId,userId) overload
     с assertCanRead; bare overload оставлен для post-mutation path
   - `web/controller/NodeVoteController.java`: GET /votes зовёт guarded overload
4. **Node source detach IDOR (#6 MED)** — DONE
   - `repository/NodeSourceRepository.java`: +deleteByIdAndNode(id,nodeId)
   - `service/NodeSourceService.java`: detachById(nodeId, nodeSourceId) scoped
   - `web/controller/NodeSourceController.java`: detach передаёт nodeId

## НЕ сделано (следующие шаги, по приоритету)

### Tier 1 осталось (security):
5. **PDF stream IDOR (#2 HIGH)** — `library/pdf/web/PdfController.java`
   streamPdf+getPdfInfo: прокинуть @CurrentUser+role; `PdfService`
   (НЕТ PermissionService — нужно инжектить) loadBook→assertCanReadBook.
   Проверить других callers openStream/getMetadata.
6. **Library page-write auth (#3 HIGH + #9 MED)**:
   - `BookController.updateFormattedContent` (PATCH /pages/{id}/formatted-content)
     — нет auth вообще
   - `AiEditController.triggerAiEdit` — нет assertCanWriteBook (+ тратит API budget)
   - `PageImageController.uploadPageImage` — нет write-check
   - Все: резолвить parent book через page → assertCanWriteBook.
     BookService: нужен метод page→bookId (проверить getPage/PageDetail).
7. **View-count inflation (#33 LOW)** — BookController:113 (опционально,
   anti-spam уже отложен в Phase 2.b — может быть not-worth-fixing)

### ПОСЛЕ каждого фикса (канон проекта, backend/CLAUDE.md):
- IT-тест: unauthorized→403 (mirror TopicControllerIT permission tests)
- `docs/api-contract.md` — отметить auth требование на endpoint'е
- атомарный коммит per-фикс: `fix(backend): ...`
- В КОНЦЕ Tier-1: один `./mvnw verify` (ловит всё накопившееся)
- ADR? — возможно ADR про «export/pdf/ai-edit/vote-stats закрыты под
  ADR-043 permission model» (расширение)

### Tier 2 (logic/UX/concurrency) — task #7:
- authStore role drop (#14 MED) — `shared/stores/authStore.ts:83`
  readPersistedUser отвергает STUDENT/SCHOLAR → теряется сессия. FRONTEND.
- PDF viewer trio (#11/#12/#13) — `shared/components/reader/PdfViewer.tsx`
  + `BookReaderPage.tsx`. onLoadError collapse / page clamp / deep-link.
- AI-edit + OCR check-then-act (#10/#21/#34) — duplicate paid API calls.
- refresh token rotation atomicity (#4 MED) — `auth/service/AuthService.java:109`
  consume-then-issue (conditional UPDATE rowsAffected==1).
- accepted-answer stuck status (#8 MED) — `qa/service/AnswerService.java`
  deleteAnswer должен revoke если удаляемый == accepted.
- hadith debounce + pagination (#15/#16/#17) — FRONTEND HadithListPage/NarratorListPage.

### Tier 3 (30 low) — см. /tmp/bughunt_list.txt [18..47]

## Инфраструктура (канон — уже изучено)
- Проверки прав в SERVICE-слое. Controller: `@CurrentUser UUID userId` +
  `SecurityContextUtils.currentRoleOrAnonymous()` → service.assertCan*.
- PermissionService: assertCanRead/assertCanWrite (topic),
  assertCanReadBook/assertCanWriteBook/assertIsBookOwner (book).
- Паттерн overload: legacy без auth (internal/IT) + role-aware для REST.
- Task list (TaskCreate) #1-7 заведён.
