# Hooks Setup для Claude Code Harness — design

**Дата:** 2026-05-19
**Статус:** brainstorm complete, awaiting user review
**Sub-project:** B из программы «Claude Code harness setup» (статья
Anthropic «How Claude Code works in large codebases», май 2026)
**Зависит от:** Sub-project A (Foundation cleanup) — closed 2026-05-19
**Следующие sub-project'ы:** C (Skills), D (LSP), E (periodic review)

## 1. Контекст и motivation

Sub-project A создал foundation (compacted CLAUDE.md, `.claudeignore`,
deny rules в settings.json). Сейчас следующий слой harness'а — hooks.

Ключевая мотивация — цитата из статьи Anthropic May 2026:

> Hooks make the setup self-improving. Most teams think of hooks as
> scripts that prevent Claude from doing something wrong, but their
> more valuable use is **continuous improvement**. A stop hook can
> reflect on what happened during a session and propose CLAUDE.md
> updates while the context is fresh. A start hook can load
> team-specific context dynamically.

Currently у нас **deterministic guards** через settings.json deny
(Sub-project A Task 9), но **continuous improvement через hooks**
отсутствует. Hooks закроют этот gap.

## 2. Goals

1. **SessionStart context loading** — Claude получает свежие
   `docs/progress.md` записи + active roadmap stage + текущий
   приоритет автоматически без ручного Read'а
2. **Stop reflection** — если в сессии были git commits но
   `docs/progress.md` не обновлялся → reminder в context. Удержание
   doc-hygiene Принципа 5 без полагания на мою память
3. **Bash safety** — block `--no-verify` (existing проектное
   правило в `feedback_commit_conventions`)
4. **Doc-update reminders** — после Edit DTO/Controller → suggest
   `npm run generate-api`; после Liquibase migration → suggest
   `architecture.md` update

Success criterion: после Sub-project B, типичная сессия моя должна
требовать **меньше manual reading** в начале и **меньше manual
checking** в конце.

## 3. Non-goals (out of scope)

- Sub-project C (project-specific skills) — отдельный sub-project
- Sub-project D (LSP setup) — параллельно но independently
- Subagent-driven hooks (overkill для текущего scope)
- Cross-platform (Mac/Windows native) — bash-only достаточно
  (WSL2 + Linux dev environment)
- Сложная state machine для Stop (multi-step doc check) —
  over-engineering для current need
- Изменения в `backend/CLAUDE.md` / `frontend/CLAUDE.md` (они
  свежие после Sub-project A)
- Hooks для других events (UserPromptSubmit, Notification,
  PreCompact) — нет конкретных use cases сейчас

## 4. Design

### 4.1. Component overview

```
.claude/
├── settings.json            (UPDATE — add `hooks` section)
├── settings.local.json      (untouched, gitignored personal)
├── commands/                (untouched — start_conv.md)
├── helpers/                 (untouched — statusline.cjs)
└── hooks/                   (NEW directory, all bash scripts)
    ├── session-start.sh     SessionStart event
    ├── stop-reminder.sh     Stop event
    ├── pre-bash-guard.sh    PreToolUse(Bash) — block --no-verify
    ├── post-edit-reminder.sh PostToolUse(Edit|Write)
    └── lib/
        └── common.sh         shared helpers (logging, bypass check)
```

### 4.2. SessionStart hook

**Event:** SessionStart fires once при старте Claude Code session.

**Stdout** идёт в Claude's context window — это feature SessionStart
hook'а, не stderr.

**Logic:**
1. Check bypass: `[[ "${CLAUDE_HOOKS_DISABLE:-0}" == "1" ]]` → exit 0
2. Print last 2 entries из `docs/progress.md` (parsing `^## YYYY-MM-DD`
   headers, take первые 2 secchii)
3. Print active stage из `docs/roadmap.md` (всё до первого `^## .*закрыт`
   header — приоритетный этап остаётся развёрнутым)
4. Print «Текущий приоритет» секцию из `docs/SESSION_START_PROMPT.md`
   (от `^## Текущий приоритет` до EOF)

**Exit:** 0 (info-only, не blocking).

**Cost:** ~50ms shell (grep + sed). **Save:** Claude 2-3 Read tool calls
с большими файлами (~5-10 секунд + tokens).

### 4.3. Stop hook (Approach B — conditional structured check)

**Event:** Stop fires **каждый раз** когда Claude заканчивает
response. Critical: performance + idempotency.

**State file:** `/tmp/claude-hooks-session-${CLAUDE_SESSION_ID:-default}.state`
JSON формат:

```json
{
  "session_start_head": "f8677f6",
  "session_start_progress_sha256": "abc123...",
  "last_reminder_at": null
}
```

**Logic:**
1. Check bypass → exit 0
2. Если state file нет:
   - Create с current `git rev-parse HEAD` + `sha256sum docs/progress.md`
   - Exit 0
3. Если есть:
   - Diff current HEAD vs stored → если разные → есть commits
   - Diff current sha256(progress.md) vs stored → если **одинаковые** → progress не обновлялся
4. Conditions: commits есть **И** progress не обновлялся **И** последний
   reminder был >5 минут назад → print reminder в stdout
5. После reminder обновить `last_reminder_at` в state

**Reminder format (stdout):**
```
⚠️  Reminder: с начала сессии было <N> коммитов, но docs/progress.md
не обновлялся. По doc-hygiene.md Принцип 5 — запись в конце сессии.
```

**Exit:** 0 (non-blocking).

**Bypass:** env var + 5-min cooldown между reminders + state file
auto-cleanup при SessionStart (нового сессии).

### 4.4. PreToolUse(Bash) hook

**Event:** Triggers перед Bash tool use. **Matcher** в settings.json:
`Bash`.

**Input:** JSON stdin со схемой:
```json
{"tool_name": "Bash", "tool_input": {"command": "...", "description": "..."}}
```

**Logic:**
1. Check bypass → exit 0
2. Parse command via `jq -r '.tool_input.command'`
3. **Block patterns** (exit 2 + stderr message):
   - `git commit.*--no-verify` → message: `--no-verify обходит pre-commit hooks. См. feedback_commit_conventions.md в memory. Если Абдула explicit запросил — set CLAUDE_HOOKS_DISABLE=1.`

   **Note:** `git push --force` намеренно НЕ дублируется здесь — это
   уже в settings.json deny rules (Sub-project A Task 9). Deny rules
   check'аются ДО hooks по Claude Code security model, поэтому hook
   bever fires для этого pattern. Single source of truth в settings.json.

4. **Warn patterns** (exit 0 + stderr message, не блокирует):
   - `^\./mvnw verify\s*$` (без точечных тестов) → message: `Full verify ~2-3 минуты. См. feedback_no_frequent_builds — запускать только на ключевых этапах.`

**Exit:** 0 (allow) / 2 (block, по spec hooks). stderr message появляется
в Claude's context.

### 4.5. PostToolUse(Edit|Write) hook

**Event:** Triggers после успешного Edit/Write tool use. **Matcher**:
`Edit|Write`.

**Input:** JSON stdin со схемой:
```json
{"tool_name": "Edit", "tool_input": {"file_path": "...", "old_string": "...", "new_string": "..."}}
```

**Logic:**
1. Check bypass → exit 0
2. Parse `file_path`
3. **Pattern matching** на path:
   - `backend/.*/web/dto/.*\.java` или `backend/.*/web/controller/.*\.java`
     → stderr: `DTO/Controller изменён. Регенерация: cd frontend && npm run generate-api`
   - `backend/.*/db/changelog/changes/.*\.xml`
     → stderr: `Liquibase migration изменена. Обнови docs/architecture.md если затронута схема (см. backend/CLAUDE.md «После коммита» чек-лист).`
   - `docs/decisions.md`
     → stderr: `ADR изменён. Добавь pointer в backend/docs/<topic>.md если соответствующий topical файл существует.`
   - `backend/src/main/resources/application.*\.yml`
     → stderr: `application.yml изменён. Restart backend (см. CLAUDE.md «Dev server management»).`

**Exit:** 0 (non-blocking всегда).

### 4.6. Bypass mechanism

Универсальный override через **env var `CLAUDE_HOOKS_DISABLE`**:
- `=1` → все hooks early exit 0
- Любое другое значение или unset → hooks normal behavior

**Use cases для bypass:**
- Bulk automated edit где reminders были бы spam
- Debugging hooks themselves (testing на known-good state)
- Edge case operations где Абдула explicit override

**Где set bypass:**
- В shell: `export CLAUDE_HOOKS_DISABLE=1`
- В Claude Code: один-time через Bash env: `CLAUDE_HOOKS_DISABLE=1 <command>`

### 4.7. Shared library (`hooks/lib/common.sh`)

DRY helper для всех 4 hooks:

```bash
# Bypass check
check_bypass() {
  [[ "${CLAUDE_HOOKS_DISABLE:-0}" == "1" ]] && exit 0
}

# Logging helper
log_decision() {
  local event="$1"
  local decision="$2"
  local reason="$3"
  echo "[$(date -Iseconds)] event=$event decision=$decision reason=$reason" \
    >> "/tmp/claude-hooks-${event}.log"
}

# Stdin JSON parsing (requires jq)
parse_input() {
  jq -r "${1}" <<< "${HOOK_INPUT:-$(cat)}"
}
```

Каждый hook начинается с `source "$(dirname "$0")/lib/common.sh"` + вызов
`check_bypass` first thing.

### 4.8. settings.json hooks section

Дополняется к существующим `permissions.deny` (Sub-project A) и
`statusLine`. **Append**, не overwrite:

```json
{
  "statusLine": { /* existing */ },
  "permissions": { /* existing 6 deny rules */ },
  "env": { /* existing AGENT_TEAMS */ },
  "hooks": {
    "SessionStart": [
      {
        "hooks": [
          {"type": "command", "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/session-start.sh"}
        ]
      }
    ],
    "Stop": [
      {
        "hooks": [
          {"type": "command", "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/stop-reminder.sh"}
        ]
      }
    ],
    "PreToolUse": [
      {
        "matcher": "Bash",
        "hooks": [
          {"type": "command", "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/pre-bash-guard.sh"}
        ]
      }
    ],
    "PostToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [
          {"type": "command", "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/post-edit-reminder.sh"}
        ]
      }
    ]
  }
}
```

### 4.9. Logging

Все hooks логируют в `/tmp/claude-hooks-<event>.log`. Append mode.
Формат: `[ISO-8601 timestamp] event=<name> decision=<silent|reminded|blocked> reason=<brief>`.

`tail -f /tmp/claude-hooks-*.log` для real-time debugging.

Logs **не** в git, **не** в репе — это runtime artifacts.

### 4.10. Dependencies

- `bash` — есть в WSL2 by default
- `jq` — нужен для JSON stdin parsing в PreToolUse / PostToolUse
  hooks. **Graceful degradation:** `lib/common.sh` проверяет
  `command -v jq` в первой строке. Если `jq` отсутствует — все
  hooks тихо `exit 0` (как при bypass). Не блокирует workflow.
  Install: `sudo apt install jq` (WSL2/Debian).
- `git` — есть
- `sha256sum` — есть (coreutils)
- `grep`, `sed`, `cat` — есть

SessionStart hook дополнительно печатает install instruction если
`jq` missing (один раз при старте сессии, в stdout = visible to
Claude как hint).

### 4.11. Manual smoke tests (no unit tests for hooks)

После implementation:

1. **SessionStart smoke:**
   - Запустить `/start_conv` или новую Claude Code session
   - Verify в context Claude'а появилось содержимое последних 2
     progress entries + roadmap stage + текущий приоритет
   - Test bypass: `CLAUDE_HOOKS_DISABLE=1` → verify hooks тихие

2. **Stop smoke:**
   - Touch dummy file, `git add`, `git commit`
   - Trigger Claude response (просить какой-то trivial Read)
   - Verify reminder появился в context
   - Update `docs/progress.md` (touch + commit)
   - Trigger ещё один Claude response
   - Verify reminder исчез
   - Test cooldown: trigger 2+ responses без update — verify only 1 reminder per 5min

3. **PreToolUse(Bash) smoke:**
   - Попытка `git commit --no-verify -m "test"` → verify blocked
   - Попытка `git commit -m "test"` (legit) → verify passed
   - Set `CLAUDE_HOOKS_DISABLE=1` + retry `--no-verify` → verify passed

4. **PostToolUse(Edit) smoke:**
   - Edit dummy `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/Test.java`
     → verify reminder про `npm run generate-api` в stderr
   - Edit `backend/src/main/java/ru/basnukaev/argumentmap/service/Test.java`
     (не DTO/controller) → verify silent (no reminder)
   - Revert dummy edit

## 5. Acceptance criteria

1. `.claude/hooks/` directory создан, 4 scripts + lib/common.sh
2. Все scripts executable (`chmod +x`)
3. `.claude/settings.json` содержит `hooks` секцию с 4 events
4. Bypass работает: `CLAUDE_HOOKS_DISABLE=1` skips все checks
5. Все 4 manual smoke tests pass
6. Logs в `/tmp/claude-hooks-*.log` показывают decision trail
7. `jq` зависимость documented в hooks/README.md (или в lib/common.sh
   как комментарий) + проверка наличия в SessionStart hook
8. **Идемпотентность:** запуск session-start.sh дважды подряд даёт
   тот же output (не side-effecting beyond logging)
9. **Performance:** каждый hook завершается за <200ms на WSL2

## 6. Decomposition into atomic commits

```
1. feat(.claude): hooks lib/common.sh - shared helpers (bypass, log, parse)
2. feat(.claude): SessionStart hook - load progress + roadmap + приоритет в context
3. feat(.claude): Stop hook - conditional reminder при commits без progress.md update
4. feat(.claude): PreToolUse(Bash) hook - block --no-verify и git push --force
5. feat(.claude): PostToolUse(Edit|Write) hook - doc-update reminders
6. chore(.claude): зарегистрировать hooks в settings.json
7. docs: .claude/hooks/README.md с описанием каждого hook'а и bypass
8. docs: handoff Sub-project B (Hooks setup) closed
```

8 atomic commits. Между ними — manual smoke где applicable.

Финальный handoff:
```
8. docs: handoff Sub-project B (Hooks setup) closed
```

С summary в `docs/progress.md` записи Сессии 47 (продолжение) или
новой Сессии 48.

## 7. Risks и open questions

### Risk 1: hooks могут блокировать legit operations
**Mitigation:** comprehensive bypass mechanism (`CLAUDE_HOOKS_DISABLE`)
+ блокирующие patterns ограничены 2 (`--no-verify` и `--force` push).
PostToolUse полностью non-blocking. Stop reminder — info-only, не
блокирует ничего.

### Risk 2: Stop hook overhead на каждом response
**Mitigation:** state file caching (одно `sha256sum` + одно `git rev-parse`
per response). Cooldown 5 min между reminders. Bypass работает.
Performance target <200ms — acceptable для UX.

### Risk 3: hooks не работают в Claude Code GitHub Action / Cloud
**Mitigation:** out of scope — мы работаем локально в WSL2 / Claude
Code CLI. Если когда-то понадобится — adapt.

### Risk 4: `jq` отсутствует на машине
**Mitigation:** SessionStart hook проверяет `command -v jq` и печатает
install instruction если missing. Hooks gracefully degrade (silent
exit 0 если parsing fails).

### Risk 5: Hook script syntax error → broken Claude Code
**Mitigation:** каждый script начинается с `set -e` + `trap`
для graceful failure. Если script syntax invalid — Claude Code не
должен крашиться, hooks просто не fire'ятся. Будем проверять при
implementation.

### Open question 1: Stop hook cooldown duration

5 минут — мой default. Если будет noisy → увеличить до 15. Если
будет miss commits → уменьшить до 2. Tunable в state file logic.

### Open question 2: PreToolUse warn для `./mvnw verify`

Это **warn**, не block. Может быть irritating если запускаю verify
осознанно на этапе закрытия. Если станет noisy — удалить этот warn
pattern (keep только block patterns).

## 8. Что после approval'а этого spec'а

1. Invoke `superpowers:writing-plans` — детальный implementation
   plan с конкретными bash scripts для каждого hook'а
2. Apply plan — 8 атомарных коммитов
3. Manual smoke tests из section 4.11
4. Verify acceptance criteria из section 5
5. Commit handoff
6. Update `docs/progress.md` с записью Sub-project B closed
7. Move to Sub-project C (Skills) или Sub-project D (LSP) или
   Sub-project E (periodic review)

---

**Связано с memory:**
- `feedback_full_autonomy` — autonomous mode применён к этому
  brainstorming, минимум clarifying questions
- `feedback_commit_conventions` — `--no-verify` block hook ENFORCES
  existing memory rule deterministically
- `feedback_no_frequent_builds` — `./mvnw verify` warn hook ENFORCES
  existing memory rule
- `feedback_doc_during_commit` — Stop hook + PostToolUse hooks
  enforce doc-update reminders
- `feedback_session_protocol` — SessionStart hook автоматизирует
  step 1 (Read progress.md + roadmap.md)

**Связано с docs:**
- `docs/doc-hygiene.md` Принцип 5 (progress.md format) — Stop hook
  enforces
- `docs/SESSION_START_PROMPT.md` «Текущий приоритет» — SessionStart
  hook автоматически loads

**Связано со spec'ами:**
- `docs/superpowers/specs/2026-05-19-foundation-cleanup-design.md`
  (Sub-project A) — этот sub-project продолжает roadmap из там
