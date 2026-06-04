# Bug-hunt handoff — 2026-06-01 (Сессия 52)

## Контекст

Multi-agent багоохота (Workflow, 235 агентов, 19 finders + 3-линзовая
adversarial-верификация). Результат: **48 подтверждённых** багов из 72
(24 отброшены панелью скептиков). Полный triage:

- `/tmp/bughunt_confirmed.json` — все 48 подтверждённых (полные поля)
- `/tmp/bughunt_rejected.json` — 24 отброшенных + причины
- `/tmp/bughunt_list.txt` — краткий ранжированный список

Если /tmp очищен — перезапустить Workflow `bug-hunt-discovery`
(скрипт в session workflows/scripts/).

## СДЕЛАНО И ЗАКОММИЧЕНО (Сессия 52, 9 fix-коммитов + docs)

Все с IT/unit тестами. Бэкенд: 1085 тестов 0 failures (2 errors =
ShamelaApiClientLiveIT live-network, не связано). Фронт: typecheck+lint
зелёные, 620/623 (3 flake в useAiEdit — test pollution, зелёный соло).

| Commit | Что |
|--------|-----|
| c2eafe3 | fix(fe): minimap double-offset при detail-панели (исходный визуальный баг) |
| 8dc88ad | fix(be): **6 authorization-дыр** (ADR-043 gaps): export/PDF-IDOR/accept-answer/page-write/vote-stats-leak/source-detach-IDOR |
| 771ac76 | fix(fe): persisted STUDENT/SCHOLAR сессия терялась на reload (#14) |
| 2cdbfe5 | fix(fe): PDF reader trio — onLoadError collapse / page clamp / deep-link (#11/#12/#13) |
| 1b74e0e | fix(be): AI-edit check-then-act гонка — дублирующие платные API вызовы (#10) |
| 2cf2944 | fix(be): удаление принятого ответа оставляло вопрос ANSWERED без accepted (#8) |
| 819e639 | fix(fe): hadith/narrator debounce + пагинация (#15/#16/#17) |
| 4235731 | fix(be): refresh token rotation не атомарна — concurrent reuse → 2 chain (#4) |
| (docs)  | api-contract.md changelog + gotchas.md (flaky test pollution) |

**Все 4 HIGH security + все MEDIUM security/concurrency/logic закрыты.**

## ОСТАЛОСЬ: Tier-3 (30 low severity)

Из `/tmp/bughunt_list.txt` строки [18..47]. Ни один не критичен;
кандидаты по убыванию ценности:

- #18/#19 AuthService: login timing side-channel (malformed dummy bcrypt
  hash) + disabled-account error leak — security hardening.
- #21/#34 OCR re-trigger concurrent (тот же check-then-act что AI-edit
  #10 — можно применить tryClaim-паттерн к OcrService).
- #26 ShamelaArchiveExtractor: нет cap на размер (decompression bomb).
- #23 AnthropicClient retry на permanent 4xx (множит cost).
- #36/#41 ContextMenu: off-screen near edges + нет keyboard-nav (a11y).
- #29 HadithController stale `bookId` query param после Phase 5 rename.
- #31 QuestionService updateQuestion body="" вместо NULL.
- #32 acceptAnswer на CLOSED вопросе reopens lifecycle.
- #38 TopicListPage post-import refetch теряет sort order.
- #40 Toaster error toasts 'polite' вместо 'assertive' (a11y).
- #44 QuestionDetailPage delete-кнопка видна всем (ownership gating).
- #46/#47 AdminUsersPage createdAt non-locale-aware toLocaleDateString.
- остальные — см. список.

## Канон (изучено, применять для Tier-3)
- Backend permission: проверки в SERVICE-слое через
  PermissionService.assertCan*; controller прокидывает @CurrentUser +
  SecurityContextUtils.currentRoleOrAnonymous(). Legacy overloads без
  auth — для internal callers / IT. Новый helper
  SecurityContextUtils.currentUserIdOrNull() (service-слой без @CurrentUser).
- Concurrency check-then-act: conditional UPDATE (compare-and-set,
  WHERE ... IS DISTINCT FROM / revoked_at IS NULL) + проверка affected
  rows; loser откатывается через @Transactional. Образцы:
  PageRepository.tryClaimAiEditProcessing, AuthService.refresh.
- Frontend debounce/pagination: образец BookListPage (300ms setTimeout
  searchInput→searchQ; Load More аппендит, hasNext gate). НЕ сбрасывать
  в loading на refetch (react-hooks/set-state-in-effect).
- Тесты flaky в полном прогоне → проверять в изоляции (см. gotchas.md
  «Тесты зелёные в изоляции…»).

## Что посмотреть руками (фронт)
- `/topics/:id` даблклик узла → миникарта рядом с панелью (~12px), не улетает.
- Reader PDF (`/books/:id` → PDF): открыть многотомную книгу, сломать
  загрузку одного тома → toolbar/селектор НЕ исчезают; deep-link
  `?pdf=1&pdfPageNumber=N` открывает нужную страницу.
- Hadith/Narrator списки: печатать в поиск (один запрос после паузы),
  Load More подгружает.
- Reload страницы под STUDENT/SCHOLAR юзером → сессия НЕ слетает.
