# Foundation Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Сжать subdir CLAUDE.md (`backend/` 540→~400, `frontend/` 351→~270) за счёт выноса task-specific ADR-деталей в `backend/docs/` / `frontend/docs/`. Добавить `.claudeignore`, уточнить `.claude/settings.json` deny rules (убрать излишние Read denies, добавить Write/Edit/Bash guards), починить stale references в `.claude/commands/start_conv.md`.

**Architecture:** Pure doc-reorganisation. Никаких изменений в `src/`. Каждый task — атомарный коммит: создать новый topic-named файл в `backend/docs/` или `frontend/docs/` + заменить соответствующую секцию в `CLAUDE.md` 2-3-строчным pointer'ом. Verification через `grep` / `wc -l` / `git diff` после каждого коммита. Финальный smoke check (`./mvnw verify` + frontend lint/build/test) один раз в конце.

**Tech Stack:** Markdown files. Git atomic commits with Conventional Commits format `docs:` / `chore:`. Shell для verification (`grep`, `wc`, `git diff`).

**Spec:** `docs/specs/2026-05-19-foundation-cleanup-design.md` (commit `e7be9d7`)

---

## File Structure

**Создаются (6 новых файлов):**
- `backend/docs/ocr-pipeline.md` — extracted from backend/CLAUDE.md lines 203-224 (OCR ADR-041)
- `backend/docs/ai-editing.md` — extracted from backend/CLAUDE.md lines 225-279 (AI editing ADR-042)
- `backend/docs/hadith-grades.md` — extracted from backend/CLAUDE.md lines 469-497 (Authority.type + миграция 47)
- `backend/docs/auth-security.md` — extracted from backend/CLAUDE.md lines 296-351 (Rate limit ADR-046 + Actuator ADR-048)
- `frontend/docs/auth-integration.md` — extracted from frontend/CLAUDE.md lines 212-241 (Auth Этап 21.b)
- `frontend/docs/permissions-integration.md` — extracted from frontend/CLAUDE.md lines 242-277 (Permissions Этап 22.b)
- `.claudeignore` — новый файл в корне репы

**Модифицируются (5 существующих файлов):**
- `backend/CLAUDE.md` — 4 секции заменяются pointer'ами (Tasks 1-4)
- `frontend/CLAUDE.md` — 3 секции заменяются (Tasks 5-7)
- `.claude/settings.json` — уточнить deny rules (убрать Read denies, добавить Write/Edit/Bash guards) (Task 9)
- `.claude/commands/start_conv.md` — переписать под актуальный SESSION_START_PROMPT (Task 10)
- `docs/progress.md` — handoff запись (Task 11)
- `docs/specs/2026-05-19-foundation-cleanup-design.md` — корректировка acceptance criterion #2 (Task 11)

**Не трогаются:** `src/`, `docs/decisions.md`, `docs/roadmap.md` (sub-project'ы не tracked в roadmap), native subagents, Superpowers plugin, design-reference/

---

## Pointer template (используется во всех Task 1-7)

При замене секции в CLAUDE.md используется единый формат pointer'а:

```markdown
### <Section name with ADR reference>

<Одна-две строки сути — что это и когда нужно.>

**Детали:** `<path/to/new/file.md>` (краткое описание что внутри).
```

Цель — 2-4 строки текста в CLAUDE.md per pointer. Pointer должен дать Claude'у достаточно контекста чтобы понять **нужно ли** читать дальше; если задача не касается этого раздела — pointer пролистывается.

---

## Task 1: Вынести OCR pipeline в `backend/docs/ocr-pipeline.md`

**Files:**
- Create: `backend/docs/ocr-pipeline.md`
- Modify: `backend/CLAUDE.md` (replace lines 203-224 с pointer'ом)

- [ ] **Step 1: Прочитать source content**

Run: `sed -n '203,224p' backend/CLAUDE.md`
Expected: вывод секции `### OCR (ADR-041)` с подсекциями про Tess4j, async pipeline, state machine, graceful degradation, IT тест.

- [ ] **Step 2: Создать `backend/docs/ocr-pipeline.md` с full content**

Use Write tool. Структура:

```markdown
# OCR Pipeline (ADR-041)

Извлечение текста из сканов через Tesseract. Async pipeline,
state machine в `lib_pages.ocr_status`, graceful degradation если
tesseract не установлен.

## Зависимости

- **Tess4j 5.13.0** (Maven dep) — Java JNA wrapper над Tesseract C++
- **Tesseract сам — НЕ Maven artifact**, это system dependency

[... full content из backend/CLAUDE.md lines 203-224 verbatim ...]
```

Скопировать **все** факты из секции дословно: Maven dep, install commands для Debian/macOS, `ocr.tessdata.path` property, async pipeline TaskExecutor config, state machine, graceful degradation, IT тест с `@EnabledIf`.

Добавить только markdown structure (H1, H2 sections — «Зависимости», «Конфигурация», «Async pipeline», «State machine», «Graceful degradation», «Тестирование»). Содержимое — verbatim.

- [ ] **Step 3: Заменить секцию в `backend/CLAUDE.md` pointer'ом**

Use Edit tool. Заменить весь блок lines 203-224 (от `### OCR (ADR-041)` до начала следующей `### AI editing`) на:

```markdown
### OCR (ADR-041)

Pipeline извлечения текста из сканов через Tesseract. Async,
`lib_pages.ocr_status` state machine, graceful degradation если
tesseract не установлен.

**Детали:** `backend/docs/ocr-pipeline.md` (Tess4j config, async
TaskExecutor, state machine PENDING→PROCESSING→DONE/FAILED, IT тест
с `@EnabledIf`).
```

- [ ] **Step 4: Verify diff (нет content loss)**

Run: `git diff backend/CLAUDE.md | grep -c "^-"` — посчитать удалённые строки
Expected: ~22 строки удалено (lines 203-224)

Run: `wc -l backend/docs/ocr-pipeline.md` — посчитать новый файл
Expected: ≥ 25 строк (verbatim content + H1/H2 markdown structure)

Run: `grep -i "tess4j\|tesseract\|ocr_status\|graceful" backend/docs/ocr-pipeline.md | wc -l`
Expected: ≥ 4 матча (ключевые термины не потеряны)

- [ ] **Step 5: Commit**

```bash
git add backend/docs/ocr-pipeline.md backend/CLAUDE.md
git commit -m "docs(backend): вынести OCR pipeline в backend/docs/ocr-pipeline.md

Секция OCR (ADR-041) переведена в отдельный файл по navigability
target из spec'а 2026-05-19-foundation-cleanup-design.md. В CLAUDE.md
остаётся 4-строчный pointer.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Вынести AI editing в `backend/docs/ai-editing.md`

**Files:**
- Create: `backend/docs/ai-editing.md`
- Modify: `backend/CLAUDE.md` (replace lines 225-279 с pointer'ом)

- [ ] **Step 1: Прочитать source content**

Run: `sed -n '225,279p' backend/CLAUDE.md`
Expected: секция `### AI editing (ADR-042, Этап 17.e)` — ~55 строк про Anthropic Claude провайдер, env vars config, async pipeline, retry, state machine, prompt template, graceful degradation, curl example, live IT тест, HTTP-stub тесты.

- [ ] **Step 2: Создать `backend/docs/ai-editing.md`**

Use Write tool. Структура:

```markdown
# AI Editing (ADR-042, Этап 17.e)

LLM расставляет структуру (хадис-боксы, ayah-боксы, decorated headings)
поверх OCR raw text. Без LLM работы платформа продолжает функционировать —
просто `formatted_content` остаётся `null`. AI edit — optional enhancement,
не блокер.

## Provider

Anthropic Claude (`claude-sonnet-4-6`) через raw `java.net.http.HttpClient`
(~100 LOC). Без Anthropic Java SDK — не оправдывает heavy dep для одного
endpoint.

## Configuration через env vars

[... full content lines 225-279 verbatim, divided into H2 sections ...]

## Async pipeline
## Retry
## State machine
## Prompt template
## Graceful degradation
## Curl example
## Live IT тест
## HTTP-уровневые тесты
```

Скопировать **все** факты дословно: env vars (ANTHROPIC_API_KEY, MODEL, MAX_TOKENS, TIMEOUT_SECONDS, BASE_URL), TaskExecutor config (core=2, max=4, queue=50), Resilience4j retry config, state machine в `lib_pages.ai_edit_status` миграция 35, prompt template path, curl example.

- [ ] **Step 3: Заменить секцию в `backend/CLAUDE.md` pointer'ом**

Use Edit tool. Заменить блок lines 225-279 на:

```markdown
### AI editing (ADR-042, Этап 17.e)

LLM расставляет структуру (хадис-боксы, ayah-боксы, headings) поверх
OCR raw text через Anthropic Claude. Optional enhancement — без ключа
платформа работает (formatted_content=null).

**Детали:** `backend/docs/ai-editing.md` (env vars config, async
pipeline `aiEditTaskExecutor`, retry policy Resilience4j, state machine
в `lib_pages.ai_edit_status`, prompt template, graceful degradation,
live IT тест).
```

- [ ] **Step 4: Verify**

Run: `git diff backend/CLAUDE.md | grep -c "^-"`
Expected: ~55 строк удалено

Run: `wc -l backend/docs/ai-editing.md`
Expected: ≥ 55 строк

Run: `grep -i "anthropic\|formatted_content\|ai_edit_status\|resilience4j" backend/docs/ai-editing.md | wc -l`
Expected: ≥ 4 матча

- [ ] **Step 5: Commit**

```bash
git add backend/docs/ai-editing.md backend/CLAUDE.md
git commit -m "docs(backend): вынести AI editing в backend/docs/ai-editing.md

Секция AI editing (ADR-042) переведена в отдельный файл. CLAUDE.md
получает 5-строчный pointer на детальный гайд.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Вынести Hadith grades в `backend/docs/hadith-grades.md`

**Files:**
- Create: `backend/docs/hadith-grades.md`
- Modify: `backend/CLAUDE.md` (replace lines 469-497 с pointer'ом)

- [ ] **Step 1: Прочитать source content**

Run: `sed -n '469,497p' backend/CLAUDE.md`
Expected: секция `### Hadith grades + Authority.type (миграция 47)` — ~29 строк про семантическую роль authority, CHECK constraint, ETL/импорт, backward compat, publishers/muhaqqiqs separate tables.

- [ ] **Step 2: Создать `backend/docs/hadith-grades.md`**

Use Write tool. Структура:

```markdown
# Hadith Grades + Authority.type (миграция 47)

`HadithGradeService.addGrade` валидирует семантическую роль authority —
оценивать хадис (SAHIH/HASAN/DAIF/MAUDU) может только учёный, не
издательство и не тахкик.

## Whitelist в domain.AuthorityType

`SCHOLAR / MUHAQQIQ / PUBLISHER / AUTHOR / OTHER`, default `SCHOLAR`
(БД-уровень).

[... full content lines 469-497 verbatim, divided into H2 sections ...]

## Где enforce
## CHECK constraint
## ETL/импорт
## Backward compat
## Publishers/muhaqqiqs separate tables
```

Скопировать **все** факты дословно: `HadithGradeService.addGrade` validation logic, `InvalidScholarAuthorityException`, CHECK constraint, `AuthorityService.createAuthority` whitelist, ETL поведение `ShamelaAuthorityResolver` + `TopicImportService`, backward compat legacy createAuthority overload, `lib_publishers` + `lib_muhaqqiqs` separate tables.

- [ ] **Step 3: Заменить секцию в `backend/CLAUDE.md` pointer'ом**

Use Edit tool. Заменить блок lines 469-497 на:

```markdown
### Hadith grades + Authority.type (миграция 47)

`HadithGradeService.addGrade` валидирует семантическую роль authority —
оценивать хадис может только `SCHOLAR`. Whitelist в
`domain.AuthorityType`: SCHOLAR / MUHAQQIQ / PUBLISHER / AUTHOR / OTHER.

**Детали:** `backend/docs/hadith-grades.md` (validation logic,
`InvalidScholarAuthorityException`, CHECK constraint, ETL поведение
ShamelaAuthorityResolver, backward compat, `lib_publishers` +
`lib_muhaqqiqs` отдельные таблицы).
```

- [ ] **Step 4: Verify**

Run: `git diff backend/CLAUDE.md | grep -c "^-"`
Expected: ~29 строк удалено

Run: `wc -l backend/docs/hadith-grades.md`
Expected: ≥ 30 строк

Run: `grep -i "scholar\|muhaqqiq\|publisher\|authoritytype" backend/docs/hadith-grades.md | wc -l`
Expected: ≥ 4 матча

- [ ] **Step 5: Commit**

```bash
git add backend/docs/hadith-grades.md backend/CLAUDE.md
git commit -m "docs(backend): вынести Hadith grades в backend/docs/hadith-grades.md

Секция про Authority.type валидацию (миграция 47) переведена в отдельный
файл. CLAUDE.md получает 5-строчный pointer.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Объединить Rate limit + Actuator security в `backend/docs/auth-security.md`

**Files:**
- Create: `backend/docs/auth-security.md`
- Modify: `backend/CLAUDE.md` (replace lines 296-351 с одним pointer'ом)

- [ ] **Step 1: Прочитать source content**

Run: `sed -n '296,351p' backend/CLAUDE.md`
Expected: две подсекции — `#### Rate limiting (ADR-046)` (lines 296-325) и `#### Actuator security (ADR-048)` (lines 326-351). Total ~56 строк.

- [ ] **Step 2: Создать `backend/docs/auth-security.md`**

Use Write tool. Структура — два H2 раздела:

```markdown
# Auth Security: Rate Limiting + Actuator

Spring Security 6 общая конфигурация — в `backend/CLAUDE.md` секция
«Security (ADR-040)». Этот файл — детали по двум **дополнительным**
ADR'ам: rate limit на auth endpoints (ADR-046) и actuator behind
basic auth в prod (ADR-048).

## Rate Limiting (ADR-046)

`/auth/login` и `/auth/register` защищены custom in-memory sliding-window
filter (`RateLimitFilter`).

[... full content lines 296-325 verbatim ...]

## Actuator Security (ADR-048)

`/actuator/**` обрабатывается отдельным `SecurityFilterChain`
(`ActuatorSecurityConfig`, `@Order(1)`, `securityMatcher("/actuator/**")`).

[... full content lines 326-351 verbatim ...]
```

Скопировать **все** факты дословно: `auth.rate-limit.enabled` property, sliding window 1 минута, X-Forwarded-For IP extraction, clock injection, когда расширять; basic auth для actuator, prod/dev profile difference, ACTUATOR_USERNAME/PASSWORD env vars, HTTP security headers mirroring, тесты `@TestPropertySource`.

- [ ] **Step 3: Заменить блок в `backend/CLAUDE.md` одним pointer'ом**

Use Edit tool. Заменить весь блок lines 296-351 (включая оба `####` заголовка) на:

```markdown
### Rate limit + Actuator security (ADR-046 + ADR-048)

Дополнительные security слои: rate limit на `/auth/login` и
`/auth/register` (in-memory sliding window, ADR-046), и actuator
basic auth в prod profile (ADR-048).

**Детали:** `backend/docs/auth-security.md` (RateLimitFilter,
`auth.rate-limit.*` properties, IP extraction; ActuatorSecurityConfig,
prod/dev profile difference, ACTUATOR_USERNAME/PASSWORD env vars).
```

- [ ] **Step 4: Verify**

Run: `git diff backend/CLAUDE.md | grep -c "^-"`
Expected: ~56 строк удалено

Run: `wc -l backend/docs/auth-security.md`
Expected: ≥ 60 строк (содержит обе секции + H1 + intro)

Run: `grep -c "Rate Limiting\|Actuator Security" backend/docs/auth-security.md`
Expected: 2 (один H2 на каждый раздел)

- [ ] **Step 5: Commit**

```bash
git add backend/docs/auth-security.md backend/CLAUDE.md
git commit -m "docs(backend): объединить rate limit + actuator security в backend/docs/auth-security.md

Подсекции ADR-046 (rate limit) и ADR-048 (actuator security) объединены
в один файл по semantic affinity — обе про дополнительные security
слои поверх базовой Spring Security конфигурации. CLAUDE.md получает
один объединённый pointer.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Вынести Auth в `frontend/docs/auth-integration.md`

**Files:**
- Create: `frontend/docs/auth-integration.md`
- Modify: `frontend/CLAUDE.md` (replace lines 212-241 с pointer'ом)

- [ ] **Step 1: Прочитать source content**

Run: `sed -n '212,241p' frontend/CLAUDE.md`
Expected: секция `## Auth (Этап 21.b, ADR-040)` — ~30 строк про useAuthStore, accessToken interceptor, ProtectedRoute, admin-only pages, 401 retry, logout, dev cookies, тестирование компонентов с authStore.

- [ ] **Step 2: Создать `frontend/docs/auth-integration.md`**

Use Write tool. Структура:

```markdown
# Auth Integration (Этап 21.b, ADR-040)

Frontend интеграция с JWT auth — Spring Security 6 + jjwt 0.12.x на
backend, `useAuthStore` (Zustand) + `apiClient` interceptor на
frontend.

## Текущий user

[... full content lines 212-241 verbatim, divided into H2 sections ...]

## accessToken / Interceptor
## ProtectedRoute
## Admin-only pages
## 401 retry behavior
## logout()
## Dev cookies (SameSite=Strict)
## Тестирование компонентов с authStore
```

Скопировать **все** факты дословно: useAuthStore selector, interceptor в `shared/api/client.ts`, ProtectedRoute requireRole, 401 retry с refresh, AvatarMenu logout, SameSite=Strict cookie + Vite proxy, тесты через `useAuthStore.setState({...})`.

- [ ] **Step 3: Заменить секцию в `frontend/CLAUDE.md` pointer'ом**

Use Edit tool. Заменить блок lines 212-241 на:

```markdown
## Auth (Этап 21.b, ADR-040)

JWT auth через `useAuthStore` (Zustand) + `apiClient` interceptor
для `Authorization: Bearer`. Refresh cookie HttpOnly + Vite proxy
для same-origin в dev.

**Детали:** `frontend/docs/auth-integration.md` (useAuthStore selector,
interceptor поведение, ProtectedRoute, 401 retry с refresh, тесты
компонентов с authStore через setState).
```

- [ ] **Step 4: Verify**

Run: `git diff frontend/CLAUDE.md | grep -c "^-"`
Expected: ~30 строк удалено

Run: `wc -l frontend/docs/auth-integration.md`
Expected: ≥ 30 строк

Run: `grep -i "useauthstore\|protectedroute\|interceptor\|refresh" frontend/docs/auth-integration.md | wc -l`
Expected: ≥ 4 матча

- [ ] **Step 5: Commit**

```bash
git add frontend/docs/auth-integration.md frontend/CLAUDE.md
git commit -m "docs(frontend): вынести Auth integration в frontend/docs/auth-integration.md

Секция Auth (Этап 21.b, ADR-040) переведена в отдельный файл. CLAUDE.md
получает 4-строчный pointer.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Вынести Permissions в `frontend/docs/permissions-integration.md`

**Files:**
- Create: `frontend/docs/permissions-integration.md`
- Modify: `frontend/CLAUDE.md` (replace lines 242-277 с pointer'ом)

- [ ] **Step 1: Прочитать source content**

Run: `sed -n '242,277p' frontend/CLAUDE.md`
Expected: секция `## Permissions (Этап 22.b, ADR-043)` — ~36 строк про topic.visibility, isOwner, canWrite optimistic, hiding write actions, VisibilityBadge/RadioGroup, TopicMembersModal, permission errors, тестирование modals.

- [ ] **Step 2: Создать `frontend/docs/permissions-integration.md`**

Use Write tool. Структура:

```markdown
# Permissions Integration (Этап 22.b, ADR-043)

Per-entity authorization frontend layer — backend (ADR-043) источник
истины, frontend показывает visibility/permission UI и hides write
actions optimistically.

## topic.visibility

[... full content lines 242-277 verbatim, H2 sections ...]

## isOwner check
## canWrite optimistic estimate
## Hiding write actions
## VisibilityBadge и VisibilityRadioGroup
## TopicMembersModal
## Permission errors formatting
## Тестирование modals (jsdom polyfill)
```

Скопировать **все** факты дословно: `topic.visibility` selector, `isOwner` = createdBy comparison, optimistic canWrite logic, GraphCanvas/Panels canWrite prop, VisibilityBadge compact prop, TopicMembersModal MVP без user search, `formatPermissionError` helper, HTMLDialogElement polyfill в jsdom + `vi.stubGlobal('confirm')`.

- [ ] **Step 3: Заменить секцию в `frontend/CLAUDE.md` pointer'ом**

Use Edit tool. Заменить блок lines 242-277 на:

```markdown
## Permissions (Этап 22.b, ADR-043)

Per-entity authorization frontend. Backend (ADR-043) — источник
истины, frontend показывает UI и hides write actions optimistically.
canWrite estimate на фронте rough; точная семантика — через GET
/members.

**Детали:** `frontend/docs/permissions-integration.md` (topic.visibility
selector, isOwner check, canWrite optimistic, hiding write actions,
VisibilityBadge/RadioGroup, TopicMembersModal, permission errors через
formatPermissionError, тесты с HTMLDialogElement polyfill).
```

- [ ] **Step 4: Verify**

Run: `git diff frontend/CLAUDE.md | grep -c "^-"`
Expected: ~36 строк удалено

Run: `wc -l frontend/docs/permissions-integration.md`
Expected: ≥ 36 строк

Run: `grep -i "visibility\|isowner\|canwrite\|topicmembers" frontend/docs/permissions-integration.md | wc -l`
Expected: ≥ 4 матча

- [ ] **Step 5: Commit**

```bash
git add frontend/docs/permissions-integration.md frontend/CLAUDE.md
git commit -m "docs(frontend): вынести Permissions integration в frontend/docs/permissions-integration.md

Секция Permissions (Этап 22.b, ADR-043) переведена в отдельный файл.
CLAUDE.md получает 6-строчный pointer.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Заменить RTL/i18n блок в `frontend/CLAUDE.md` ссылкой на i18n-guide.md

**Files:**
- Modify: `frontend/CLAUDE.md` (replace lines 170-193 с короткой ссылкой)

Note: `frontend/docs/i18n-guide.md` уже существует, не создаём новый файл.

- [ ] **Step 1: Verify что i18n-guide.md существует и actual**

Run: `ls -la frontend/docs/i18n-guide.md && head -20 frontend/docs/i18n-guide.md`
Expected: файл существует, начинается с H1 «# i18n Guide» (или подобного)

- [ ] **Step 2: Заменить блок в `frontend/CLAUDE.md` короткой ссылкой**

Use Edit tool. Заменить блок lines 170-193 (полный `### RTL, i18n и арабский текст` блок ~24 строки) на:

```markdown
### RTL, i18n и арабский текст

Локаль UI ≠ язык контента ≠ направление текста — три разных понятия.
UI-строки через `useT()` + словарь `shared/i18n/dictionary.ts`. Tailwind
logical classes (`ms-*`, `me-*`, `text-start`, `border-s`), физические
запрещены. Контент из API — `dir="auto"`, шрифт через `hasArabicScript`.

**Полный гайд:** `frontend/docs/i18n-guide.md` (mixed-content
изоляция через `<bdi>`, локаль-aware даты, иконки навигации по локали,
React Flow граф не зеркалится, naskh шрифт setup).
```

- [ ] **Step 3: Verify**

Run: `git diff frontend/CLAUDE.md | grep -c "^-"`
Expected: ~24 строк удалено

Run: `git diff frontend/CLAUDE.md | grep -c "^+"`
Expected: ~8 строк добавлено

Run: `wc -l frontend/CLAUDE.md`
Expected: ≤ 270 строк (target после tasks 5-7)

- [ ] **Step 4: Commit**

```bash
git add frontend/CLAUDE.md
git commit -m "docs(frontend): заменить RTL блок ссылкой на frontend/docs/i18n-guide.md

i18n-guide.md уже существует и содержит полный гайд по RTL/i18n.
В CLAUDE.md остаётся 8-строчное summary с ключевыми правилами
(logical classes, useT, dir=auto) + pointer на детальный гайд.
Дубликат устранён по Принципу 1 doc-hygiene.md (single source of truth).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: Создать `.claudeignore`

**Files:**
- Create: `.claudeignore` (root of repo)

- [ ] **Step 1: Проверить что файла нет**

Run: `ls -la .claudeignore 2>&1`
Expected: `No such file or directory`

- [ ] **Step 2: Создать `.claudeignore` с deliberate content**

Use Write tool:

```
# Build artifacts
target/
node_modules/
dist/
build/
**/*.log

# Autogenerated - регенерируется через `npm run generate-api`
frontend/src/shared/api/types.ts

# Coverage reports
frontend/coverage/

# IDE
.idea/
.vscode/

# Архив - читать только при поиске исторического контекста
docs/archive/
```

**Намеренно НЕ включено** (для будущей справки):
- `frontend/design-reference/` — Claude **читает** перед UI changes
- `docs/decisions.md` — большой, но grep'ается по запросу

- [ ] **Step 3: Verify content**

Run: `wc -l .claudeignore`
Expected: ≥ 17 строк (с комментариями)

Run: `grep -c "^[^#]" .claudeignore`
Expected: ≥ 10 непустых строк-правил

- [ ] **Step 4: Commit**

```bash
git add .claudeignore
git commit -m "chore: добавить .claudeignore для уменьшения шума в контексте Claude

Игнорируем build artifacts (target/, node_modules/, dist/, build/, *.log),
autogenerated frontend/src/shared/api/types.ts, coverage reports, IDE
конфиги, docs/archive/. Не игнорируем frontend/design-reference/ (Claude
читает перед UI changes) и docs/decisions.md (grep'ается по запросу).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: Уточнить `.claude/settings.json` deny rules

**Files:**
- Modify: `.claude/settings.json`

- [ ] **Step 1: Прочитать current settings.json**

Run: `cat .claude/settings.json`
Expected: JSON с `statusLine`, `permissions.deny` (только `Read(./.env)` и `Read(./.env.*)`), `env`.

- [ ] **Step 2: Заменить permissions.deny массив через Edit**

Use Edit tool. Заменить блок:

```json
  "permissions": {
    "allow": [],
    "deny": [
      "Read(./.env)",
      "Read(./.env.*)"
    ]
  },
```

на:

```json
  "permissions": {
    "allow": [],
    "deny": [
      "Write(./frontend/src/shared/api/types.ts)",
      "Write(./frontend/design-reference/**)",
      "Edit(./frontend/src/shared/api/types.ts)",
      "Edit(./frontend/design-reference/**)",
      "Bash(rm -rf /*)",
      "Bash(git push --force *)"
    ]
  },
```

**Решение по Read denies (2026-05-19, фидбэк Абдулы):**
Полностью убираем Read denies на `.env`, `.env.*`, `*.key`,
`settings.local.json`. Для solo проекта на личной машине они не
дают реальной защиты — Claude running локально, secrets никуда не
уплывают. Зато `.env` часто нужен для debugging (env vars, порт
DB). Threat model для solo dev'а ≠ threat model corp environment.

Остаются только **value-add deterministic guards**:
- Write/Edit deny на autogenerated `types.ts` — защита от случайного
  редактирования вместо `npm run generate-api` regeneration
- Write/Edit deny на `design-reference/**` — это input от Claude
  Design, я не editor
- `Bash(rm -rf /*)` defensive — крайне маловероятен, но cost-free
  guard
- `Bash(git push --force *)` — force push без явной просьбы запрещён
  (см. system prompt safety section)

- [ ] **Step 3: Verify JSON корректен**

Run: `cat .claude/settings.json | jq '.permissions.deny | length'`
Expected: `6`

Run: `cat .claude/settings.json | jq '.statusLine.type'`
Expected: `"command"` (statusLine не сломан)

Run: `cat .claude/settings.json | jq '.env.CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS'`
Expected: `"1"` (env не сломан)

- [ ] **Step 4: Commit**

```bash
git add .claude/settings.json
git commit -m "chore: уточнить .claude/settings.json deny rules

Убраны излишние Read denies (.env, .env.*, *.key,
settings.local.json) - для solo проекта на личной машине они не
дают реальной защиты, Claude running локально и secrets не утекают.
Зато .env часто нужен для debugging.

Оставлены только value-add deterministic guards:
- Write/Edit denies: frontend/src/shared/api/types.ts (autogenerated -
  regenerate через npm run generate-api), frontend/design-reference/**
  (read-only зона, input от Claude Design)
- Bash denies: rm -rf /* (defensive), git push --force * (force push
  без явной просьбы)

Тонкая логика hook'ов будет в Sub-project B.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 10: Переписать `.claude/commands/start_conv.md` под актуальный SESSION_START_PROMPT

**Files:**
- Modify: `.claude/commands/start_conv.md`

- [ ] **Step 1: Прочитать актуальный SESSION_START_PROMPT структуру**

Run: `grep "^##\|^###" docs/SESSION_START_PROMPT.md`
Expected: реальные секции — «Режим работы», «START-OF-SESSION PROTOCOL», «Документация по ходу работы», «Декомпозиция и проверки», «Контрольные точки качества handoff'а», «Текущий приоритет». Никаких «ПРИВЕТСТВИЕ» / «ОТКРЫТО».

- [ ] **Step 2: Verify stale references в текущем start_conv.md**

Run: `grep -n "ПРИВЕТСТВИЕ\|ОТКРЫТО\|feedback_full_autonomy_mode" .claude/commands/start_conv.md`
Expected: 3+ матча (текущий файл содержит stale refs)

- [ ] **Step 3: Перезаписать start_conv.md полностью**

Use Write tool с актуальным content:

```markdown
---
description: Старт новой сессии - читать SESSION_START_PROMPT и продолжить с того места где остановились
---

Это начало новой сессии. Делай в таком порядке:

1. **Прочитай `docs/SESSION_START_PROMPT.md`** - там START-OF-SESSION
   PROTOCOL с порядком чтения и раздел «Текущий приоритет» с next step
2. Пройдись по протоколу:
   - `MEMORY.md` index (подгружается автоматически)
   - `docs/progress.md` - последние 2-3 записи + «Следующий шаг»
   - `docs/roadmap.md` - текущий приоритетный этап
   - Оба `CLAUDE.md` (root + backend/ + frontend/) - уже частично в
     контексте автоматически
3. Проверь инфру:
   - `git log --oneline -15` (свежие коммиты)
   - `docker ps | grep argumentmap-postgres` (БД healthy)
   - `lsof -ti:9090 -ti:5173` (порты)
4. **Дай короткое summary** последнего состояния («Вижу X, продолжаю
   с Y из roadmap»). Если задача ясна - сразу за работу, не жди апрува
   (см. memory `feedback_full_autonomy`)

После старта - **автономный режим**: тактические решения сам, по
логическим границам подэтапов коммит, без вопросов «продолжить?». См.
memory `feedback_full_autonomy` + раздел «Режим работы» в
SESSION_START_PROMPT.

---

**Напоминание handoff (always-on):**

Если задача не влезает в сессию или контекст заполняется - остановись
на логической границе. Перед остановкой:
- запись в `docs/progress.md` (новая Сессия N сверху со «Сделано /
  Решения / Проблемы / Следующий шаг»)
- `docs/roadmap.md` обновлён - `[x]` на закрытых подэтапах
- `docs/SESSION_START_PROMPT.md` «Текущий приоритет» переписан под
  следующую сессию
- если progress.md > 1500 строк - архивировать в
  `docs/archive/progress-sessions-N-M.md`
- финальный handoff коммит `docs: handoff Сессии N - X`
- в «Следующий шаг» в progress.md - так подробно, чтобы новая сессия
  продолжила без вопросов

Лучше 70% задачи с чистым handoff чем 100% с оборванным контекстом.
```

- [ ] **Step 4: Verify stale references устранены**

Run: `grep -n "ПРИВЕТСТВИЕ\|ОТКРЫТО\|feedback_full_autonomy_mode" .claude/commands/start_conv.md`
Expected: пустой output (0 матчей)

Run: `grep -n "feedback_full_autonomy" .claude/commands/start_conv.md`
Expected: 1+ матч (актуальное имя)

- [ ] **Step 5: Commit**

```bash
git add .claude/commands/start_conv.md
git commit -m "docs: переписать .claude/commands/start_conv.md под актуальный SESSION_START_PROMPT

Stale references устранены:
- 'ПРИВЕТСТВИЕ' / 'ОТКРЫТО' секции — таких больше нет в SESSION_START_PROMPT
- 'feedback_full_autonomy_mode.md' — переименован в feedback_full_autonomy.md

Содержимое подтянуто к актуальной структуре SESSION_START_PROMPT (Режим
работы, START-OF-SESSION PROTOCOL, Текущий приоритет). Сохранён always-on
handoff reminder внизу.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 11: Final smoke check + progress.md handoff + spec correction

**Files:**
- Modify: `docs/progress.md` (новая запись Сессии 47 в начало)
- Modify: `docs/specs/2026-05-19-foundation-cleanup-design.md` (criterion #2 update)

- [ ] **Step 1: Verify CLAUDE.md sizes**

Run: `wc -l backend/CLAUDE.md frontend/CLAUDE.md CLAUDE.md`
Expected:
- backend/CLAUDE.md ≤ 410 строк (target ~400)
- frontend/CLAUDE.md ≤ 275 строк (target ~270, slight relax)
- CLAUDE.md (root) ≈ 202 строки (не трогали)

- [ ] **Step 2: Verify все pointer'ы работают (no broken refs)**

Run:
```bash
grep -E "backend/docs/(ocr-pipeline|ai-editing|hadith-grades|auth-security)\.md" backend/CLAUDE.md
grep -E "frontend/docs/(auth-integration|permissions-integration|i18n-guide)\.md" frontend/CLAUDE.md
```
Expected: 4 матча для backend, 3 для frontend. Каждый pointer присутствует.

Run:
```bash
for f in backend/docs/ocr-pipeline.md backend/docs/ai-editing.md backend/docs/hadith-grades.md backend/docs/auth-security.md frontend/docs/auth-integration.md frontend/docs/permissions-integration.md; do
  test -f "$f" && echo "OK: $f" || echo "MISSING: $f"
done
```
Expected: 6 OK строк.

- [ ] **Step 3: Run backend smoke (full verify)**

Run: `cd backend && ./mvnw verify`
Expected: BUILD SUCCESS. Все ~999 тестов проходят (последний baseline из Сессии 46).
Время: ~2-3 минуты в WSL2.

**Если FAIL:** docs-only changes не должны ломать verify. Проверить что мы случайно не задели `application.yml` / `application-*.yml` / других конфигов. Rollback strategy в конце plan'а.

- [ ] **Step 4: Run frontend smoke**

Run: `cd frontend && npm run lint && npm run build && npm run test:run`
Expected: lint passes, build succeeds, все ~571 тестов проходят.
Время: ~1-2 минуты.

**Если FAIL:** docs-only changes не должны ломать TS/Vite. Чаще всего fail после docs-changes — это case где случайно отредактировали JS/TS файл. Rollback в конце.

- [ ] **Step 5: Скорректировать spec — criterion #2 и section 4.5**

Use Edit tool. **Изменение 1:** в `docs/specs/2026-05-19-foundation-cleanup-design.md` заменить:

```markdown
2. `frontend/CLAUDE.md` ≤ 250 строк (с 351)
```

на:

```markdown
2. `frontend/CLAUDE.md` ≤ 270 строк (с 351) — relaxed с initial
   target 250 после фактического измерения; дальнейшее сжатие
   требует consolidation Code review секции (дубликат с backend/CLAUDE.md),
   что выходит за scope Sub-project A. Backlog для future cleanup.
```

**Изменение 2:** в той же spec'е, секции 4.5 («`.claude/settings.json`
deny rules расширение»), заменить блок с 10 deny rules на финальную
версию с 6 rules (без Read denies). По фидбэку Абдулы 2026-05-19 —
Read denies на `.env` / `*.key` / `settings.local.json` излишни для
solo проекта на личной машине, реальной защиты не дают, плюс `.env`
нужен для debugging.

Новая версия 4.5 содержит:

```json
{
  "permissions": {
    "deny": [
      "Write(./frontend/src/shared/api/types.ts)",
      "Write(./frontend/design-reference/**)",
      "Edit(./frontend/src/shared/api/types.ts)",
      "Edit(./frontend/design-reference/**)",
      "Bash(rm -rf /*)",
      "Bash(git push --force *)"
    ]
  }
}
```

С пояснением что Read denies (`.env`, `.env.*`, `*.key`,
`settings.local.json`) удалены — threat model solo dev'а не требует
их, а debugging часто требует чтение `.env`.

- [ ] **Step 6: Добавить запись в `docs/progress.md`**

Use Edit tool. Добавить **в начало** файла (сразу после H1 заголовка) новую запись:

```markdown
## 2026-05-19 - Сессия 47 (Sub-project A: Foundation cleanup)

### Сделано

- `<task1-hash>` `docs(backend): вынести OCR pipeline...` — 22 строки CLAUDE.md → backend/docs/ocr-pipeline.md
- `<task2-hash>` `docs(backend): вынести AI editing...` — 55 строк → backend/docs/ai-editing.md
- `<task3-hash>` `docs(backend): вынести Hadith grades...` — 29 строк → backend/docs/hadith-grades.md
- `<task4-hash>` `docs(backend): объединить rate limit + actuator...` — 56 строк → backend/docs/auth-security.md
- `<task5-hash>` `docs(frontend): вынести Auth integration...` — 30 строк → frontend/docs/auth-integration.md
- `<task6-hash>` `docs(frontend): вынести Permissions...` — 36 строк → frontend/docs/permissions-integration.md
- `<task7-hash>` `docs(frontend): заменить RTL блок ссылкой...` — 24 → 8 строк
- `<task8-hash>` `chore: добавить .claudeignore` — 17 строк правил
- `<task9-hash>` `chore: уточнить .claude/settings.json deny rules` — 6 правил (Write/Edit guards + defensive Bash)
- `<task10-hash>` `docs: переписать .claude/commands/start_conv.md...` — устранены stale refs

### Метрики

- backend/CLAUDE.md: 540 → <FINAL> строк (target ≤ 400)
- frontend/CLAUDE.md: 351 → <FINAL> строк (target ≤ 270, см. spec correction)
- Новых файлов в backend/docs/: 4
- Новых файлов в frontend/docs/: 2 (i18n-guide.md уже существовал)
- .claudeignore: создан, 17 строк
- .claude/settings.json deny rules: 2 (Read .env*) → 6 (Write/Edit guards для autogen+design-reference, defensive Bash)

### Решения

- Acceptance criterion #2 в spec'е relaxed с ≤ 250 на ≤ 270 строк
  после фактического измерения. Дальнейшее сжатие требует
  consolidation Code review секции (дубликат backend/frontend) —
  отложено в backlog как future foundation work
- Approach 1 (flat 1-к-1 mapping) применён к backend/docs/ — matches
  existing pattern (coding-standards, antipatterns, testing-strategy)
- Rate limit (ADR-046) + Actuator security (ADR-048) объединены в
  один файл `auth-security.md` по semantic affinity — обе подсекции
  Security ADR-040, обе про дополнительные security слои

### Следующий шаг

Sub-project B (Hooks setup) — следующий этап Claude Code harness
roadmap из brainstorm 2026-05-19. Brainstorm для B нужен заново
(spec другой scope). Sub-projects D (LSP) и E (periodic review) могут
выполняться параллельно с B.

Backlog для future: consolidation Code review секции между
backend/CLAUDE.md и frontend/CLAUDE.md в один общий гайд.
```

(Заменить `<task1-hash>`...`<task10-hash>` на актуальные SHA из `git log` после каждого commit'а.)

- [ ] **Step 7: Final handoff commit**

```bash
git add docs/progress.md docs/specs/2026-05-19-foundation-cleanup-design.md
git commit -m "docs: handoff Sub-project A (Foundation cleanup) closed

Sub-project A из Claude Code harness setup закрыт. Изменения:
- backend/CLAUDE.md: 540 → <FINAL> строк (target ≤ 400 met)
- frontend/CLAUDE.md: 351 → <FINAL> строк (target ≤ 270 met)
- 6 новых topical файлов в backend/docs/ + frontend/docs/
- .claudeignore создан
- .claude/settings.json deny rules уточнены: 2 → 6 (убраны Read denies, добавлены Write/Edit guards)
- .claude/commands/start_conv.md очищен от stale refs

Smoke checks pass: ./mvnw verify (backend) + npm run lint && build &&
test:run (frontend). Acceptance criterion #2 в spec'е relaxed на ≤ 270
строк после фактического измерения.

Следующий этап: Sub-project B (Hooks setup) — отдельный brainstorm.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Rollback Strategy

Если smoke check fails или какой-то commit ломает структуру:

**Точечный rollback одного коммита:**
```bash
git revert <commit-hash>
```
Создаёт inverse-commit, не теряет историю. Безопасно для published коммитов.

**Полный rollback всех 11 commits Sub-project A:**
```bash
# Identify first commit Sub-project A (после spec commit'а e7be9d7)
SUBPROJECT_START=$(git log --grep="docs(backend): вынести OCR pipeline" --pretty=%H)
# Revert chain
git revert ${SUBPROJECT_START}^..HEAD --no-edit
```

**Если verify fails:**
1. Запустить точечно последний IT который сломался: `./mvnw -Dit.test=ClassNameIT -DfailIfNoTests=false -Dsurefire.skip=true verify`
2. Проверить что docs-only changes не задели конфиги: `git diff e7be9d7 HEAD -- '*.yml' '*.yaml' '*.properties' '*.java' '*.ts' '*.tsx'`
3. Если diff пустой — verify сломался по orthogonal причине (flaky тест, env). Retry verify
4. Если diff не пустой — нашли scope creep, revert relevant commit'ы

**Если frontend lint/build fails:**
1. `cd frontend && npm run lint` — точечно lint только
2. Проверить `git diff e7be9d7 HEAD -- 'frontend/src/**'` — пустой ожидается
3. Если что-то затронуто — revert relevant frontend commit

**НЕ использовать** `git reset --hard` — это destructive и теряет work. Только revert.

---

## Self-Review

**1. Spec coverage:**

| Spec section | Plan task |
|---|---|
| 5.1 Acceptance criterion 1 (backend ≤ 400) | Task 11 Step 1 |
| 5.1 Acceptance criterion 2 (frontend ≤ 250→270) | Task 11 Step 1, Step 5 (spec correction) |
| 5.1 Criterion 3 (pointer 1-к-1) | Tasks 1-7 (каждый Step 3 заменяет section pointer'ом) |
| 5.1 Criterion 4 (4 backend файла) | Tasks 1-4 |
| 5.1 Criterion 5 (2 frontend файла) | Tasks 5-6 |
| 5.1 Criterion 6 (RTL → ссылка) | Task 7 |
| 5.1 Criterion 7 (.claudeignore) | Task 8 |
| 5.1 Criterion 8 (settings.json deny) | Task 9 |
| 5.1 Criterion 9 (start_conv fix) | Task 10 |
| 5.1 Criterion 10 (full smoke) | Task 11 Steps 3-4 |
| Section 6 11 commits | Tasks 1-11 (1-к-1) |
| Section 7 Risk 4 (stale refs) | Task 10 Step 4 |

Покрытие полное.

**2. Placeholder scan:**

- Нет «TBD» / «TODO» / «implement later»
- Нет «similar to Task N» — каждый task имеет explicit content
- Code blocks показывают точные команды и точный markdown content
- `<task1-hash>`...`<task10-hash>` в Task 11 Step 6 — это **намеренные** placeholders для git SHA которые становятся известны **только после commit'а**. Engineer заполняет их при выполнении из `git log --oneline -11`. Это не placeholder failure, а legitimate runtime data.

**3. Type/naming consistency:**

- `auth-security.md` (backend) vs `auth-integration.md` (frontend) — разные имена для разных aspect'ов (backend = security mechanism, frontend = integration с backend auth). Consistent.
- `permissions-integration.md` (frontend) — нет соответствующего permissions.md в backend (детали остаются в CLAUDE.md как cross-cutting). Это explicit decision в spec section 4.1.
- Все pointer'ы используют единый формат из section «Pointer template». Consistent.

Verification passed.

---

## Execution Handoff

**Plan complete and saved to `docs/plans/2026-05-19-foundation-cleanup-plan.md`. Two execution options:**

**1. Subagent-Driven (recommended by skill)** — диспетчирую fresh subagent на каждый task, two-stage review между tasks, fast iteration

**2. Inline Execution** — выполняю tasks в этой сессии через executing-plans, batch execution с checkpoints для review

**Для docs-only работы Inline Execution может быть проще** (нет TDD циклов, риск низкий, контекст уже в моей голове). Но Subagent-Driven даёт независимый review каждого extraction'а, что хорошо для качества.

Выбор за Абдулой.
