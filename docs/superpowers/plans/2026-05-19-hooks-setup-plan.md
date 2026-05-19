# Hooks Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Создать 4 Claude Code hooks (SessionStart, Stop, PreToolUse(Bash), PostToolUse(Edit|Write)) с shared lib + bypass mechanism + logging для self-improving harness согласно статье Anthropic May 2026.

**Architecture:** 4 bash scripts в `.claude/hooks/` + shared `lib/common.sh` (bypass check, jq check, logging). Registration в `.claude/settings.json` hooks section. Stop hook использует state file + 5min cooldown для conditional reminder. jq graceful degradation. Manual smoke tests deferred до new Claude Code session (hooks settings применяются при restart).

**Tech Stack:** Bash + jq (parsing JSON stdin). Claude Code hooks contract (`$CLAUDE_PROJECT_DIR`, `$CLAUDE_SESSION_ID`, exit 0/2 semantics).

**Spec:** `docs/superpowers/specs/2026-05-19-hooks-setup-design.md` (commit `e4eed41`)

---

## File Structure

**Создаются (7 новых файлов):**
- `.claude/hooks/lib/common.sh` — shared helpers (bypass, jq check, logging)
- `.claude/hooks/session-start.sh` — SessionStart event handler
- `.claude/hooks/stop-reminder.sh` — Stop event handler
- `.claude/hooks/pre-bash-guard.sh` — PreToolUse(Bash) handler
- `.claude/hooks/post-edit-reminder.sh` — PostToolUse(Edit|Write) handler
- `.claude/hooks/README.md` — описание hooks + bypass + smoke tests
- (нет 7-го файла; README — это и есть docs)

**Модифицируются (2 существующих файла):**
- `.claude/settings.json` — добавить `hooks` секцию (append к existing statusLine + permissions + env)
- `docs/progress.md` — handoff запись Sub-project B closed

**Не трогаются:** все backend/, frontend/, docs/ (кроме progress.md), settings.local.json, существующие commands/start_conv.md и helpers/statusline.cjs.

---

## Task 1: Shared library `.claude/hooks/lib/common.sh`

**Files:**
- Create: `.claude/hooks/lib/common.sh`

- [ ] **Step 1: Создать directory структуру**

Run:
```bash
mkdir -p /home/basnukaev/projects/argument-map/.claude/hooks/lib
```

Expected: directories созданы без ошибок.

- [ ] **Step 2: Создать `lib/common.sh`**

Use Write tool. Content:

```bash
#!/usr/bin/env bash
# Shared helpers для Claude Code hooks в проекте argument-map.
# Sourced в начале каждого hook script'а через:
#   source "$(dirname "$0")/lib/common.sh"

# Bypass via env var CLAUDE_HOOKS_DISABLE=1
# Используется для bulk automated edits / debugging.
check_bypass() {
  if [[ "${CLAUDE_HOOKS_DISABLE:-0}" == "1" ]]; then
    exit 0
  fi
}

# jq graceful degradation - если нет jq, hooks тихо skip
# (вместо crash). PreToolUse / PostToolUse требуют jq для parsing stdin JSON.
check_jq() {
  if ! command -v jq >/dev/null 2>&1; then
    exit 0
  fi
}

# Logging helper - append в /tmp/claude-hooks-<event>.log
# Формат: [ISO-8601 timestamp] event=<name> decision=<silent|reminded|blocked|...> reason=<brief>
log_decision() {
  local event="$1"
  local decision="$2"
  local reason="${3:-}"
  local logfile="/tmp/claude-hooks-${event}.log"
  echo "[$(date -Iseconds)] event=$event decision=$decision reason=${reason}" >> "$logfile"
}
```

- [ ] **Step 3: Syntax check**

Run:
```bash
bash -n /home/basnukaev/projects/argument-map/.claude/hooks/lib/common.sh
echo "EXIT=$?"
```

Expected: `EXIT=0` (no syntax errors).

- [ ] **Step 4: Commit**

```bash
cd /home/basnukaev/projects/argument-map
git add .claude/hooks/lib/common.sh
git commit -m "feat(.claude): hooks lib/common.sh - shared helpers (bypass, jq check, log)

DRY helpers для всех 4 hooks Sub-project B:
- check_bypass: CLAUDE_HOOKS_DISABLE=1 env var skip
- check_jq: graceful degradation если jq missing
- log_decision: structured logging в /tmp/claude-hooks-<event>.log

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: SessionStart hook `.claude/hooks/session-start.sh`

**Files:**
- Create: `.claude/hooks/session-start.sh`

- [ ] **Step 1: Создать `session-start.sh`**

Use Write tool. Content:

```bash
#!/usr/bin/env bash
# SessionStart hook - loads свежий project context в Claude's context window.
# Stdout идёт в Claude's context (feature SessionStart hook'а).
#
# What it loads:
#   1. Last 2 entries из docs/progress.md (по ^## YYYY-MM-DD headers)
#   2. Active roadmap stage из docs/roadmap.md (не "закрыт")
#   3. «Текущий приоритет» секция из docs/SESSION_START_PROMPT.md

set -uo pipefail

source "$(dirname "$0")/lib/common.sh"
check_bypass

# Project root - либо $CLAUDE_PROJECT_DIR (если задано Claude Code), либо pwd
cd "${CLAUDE_PROJECT_DIR:-$(pwd)}"

# 1. Last 2 progress entries
if [[ -f docs/progress.md ]]; then
  echo "=== Last 2 progress entries ==="
  awk '
    /^## [0-9]{4}-[0-9]{2}-[0-9]{2}/ {
      count++
      if (count > 2) exit
    }
    count >= 1 && count <= 2 { print }
  ' docs/progress.md
  echo
fi

# 2. Active roadmap stage (не закрытый)
if [[ -f docs/roadmap.md ]]; then
  echo "=== Active roadmap stage ==="
  awk '
    /^## / {
      if ($0 ~ /закрыт/) {
        in_section=0
      } else {
        in_section=1
      }
    }
    in_section { print }
  ' docs/roadmap.md | head -50
  echo
fi

# 3. Текущий приоритет
if [[ -f docs/SESSION_START_PROMPT.md ]]; then
  echo "=== Текущий приоритет ==="
  awk '/^## Текущий приоритет/{flag=1} flag' docs/SESSION_START_PROMPT.md | head -50
  echo
fi

# jq availability hint (один раз при старте сессии)
if ! command -v jq >/dev/null 2>&1; then
  echo "⚠️  jq не установлен. Установи: sudo apt install jq (для PreToolUse/PostToolUse hooks)"
fi

log_decision "SessionStart" "loaded" "progress + roadmap + приоритет"
exit 0
```

- [ ] **Step 2: chmod +x**

```bash
chmod +x /home/basnukaev/projects/argument-map/.claude/hooks/session-start.sh
```

- [ ] **Step 3: Syntax check**

```bash
bash -n /home/basnukaev/projects/argument-map/.claude/hooks/session-start.sh
echo "EXIT=$?"
```

Expected: `EXIT=0`.

- [ ] **Step 4: Dry-run test (run script manually, observe output)**

```bash
cd /home/basnukaev/projects/argument-map && ./.claude/hooks/session-start.sh | head -60
```

Expected: видим Last 2 progress entries (Сессии 47 и 46), active roadmap, «Текущий приоритет» секцию. Никаких ошибок stderr.

- [ ] **Step 5: Commit**

```bash
git add .claude/hooks/session-start.sh
git commit -m "feat(.claude): SessionStart hook - load progress + roadmap + приоритет в context

Stdout идёт в Claude's context window — автоматическая подгрузка
свежих 2 progress entries, active roadmap stage и Текущего
приоритета. Save 2-3 Read tool calls в начале сессии.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Stop hook `.claude/hooks/stop-reminder.sh`

**Files:**
- Create: `.claude/hooks/stop-reminder.sh`

- [ ] **Step 1: Создать `stop-reminder.sh`**

Use Write tool. Content:

```bash
#!/usr/bin/env bash
# Stop hook - conditional reminder когда в сессии были commits но
# docs/progress.md не обновлялся.
#
# Approach B (см. spec section 3): state file + 5-min cooldown.
# Idempotent, не noisy.

set -uo pipefail

source "$(dirname "$0")/lib/common.sh"
check_bypass

cd "${CLAUDE_PROJECT_DIR:-$(pwd)}"

STATE_FILE="/tmp/claude-hooks-session-${CLAUDE_SESSION_ID:-default}.state"
COOLDOWN_SECONDS=300  # 5 min между reminders

current_head=$(git rev-parse HEAD 2>/dev/null || echo "no-git")
current_progress_sha=$(sha256sum docs/progress.md 2>/dev/null | cut -d' ' -f1 || echo "no-file")
now=$(date +%s)

# Initialize state at first response in session
if [[ ! -f "$STATE_FILE" ]]; then
  cat > "$STATE_FILE" <<EOF
session_start_head=$current_head
session_start_progress_sha=$current_progress_sha
last_reminder_at=0
EOF
  log_decision "Stop" "init" "first response in session"
  exit 0
fi

# Read state
# shellcheck disable=SC1090
source "$STATE_FILE"

# Conditions
commits_present=false
[[ "$current_head" != "$session_start_head" ]] && commits_present=true

progress_unchanged=false
[[ "$current_progress_sha" == "$session_start_progress_sha" ]] && progress_unchanged=true

cooldown_passed=true
if [[ "${last_reminder_at:-0}" -gt 0 ]] && [[ $((now - last_reminder_at)) -lt $COOLDOWN_SECONDS ]]; then
  cooldown_passed=false
fi

if [[ "$commits_present" == "true" ]] && [[ "$progress_unchanged" == "true" ]] && [[ "$cooldown_passed" == "true" ]]; then
  commit_count=$(git rev-list "${session_start_head}..HEAD" --count 2>/dev/null || echo "?")
  echo "⚠️  Reminder: с начала сессии было $commit_count коммитов, но docs/progress.md не обновлялся. По doc-hygiene.md Принцип 5 — запись в конце сессии."

  # Update last_reminder_at в state file (replace line)
  sed -i "s/^last_reminder_at=.*/last_reminder_at=$now/" "$STATE_FILE"

  log_decision "Stop" "reminded" "commits=$commit_count progress_unchanged=true"
else
  log_decision "Stop" "silent" "commits=$commits_present progress_unchanged=$progress_unchanged cooldown=$cooldown_passed"
fi

exit 0
```

- [ ] **Step 2: chmod +x**

```bash
chmod +x /home/basnukaev/projects/argument-map/.claude/hooks/stop-reminder.sh
```

- [ ] **Step 3: Syntax check**

```bash
bash -n /home/basnukaev/projects/argument-map/.claude/hooks/stop-reminder.sh
echo "EXIT=$?"
```

Expected: `EXIT=0`.

- [ ] **Step 4: Dry-run test (state file behavior)**

```bash
# Cleanup any old state
rm -f /tmp/claude-hooks-session-*.state /tmp/claude-hooks-Stop.log

# First run — should init silently
cd /home/basnukaev/projects/argument-map && ./.claude/hooks/stop-reminder.sh
echo "EXIT=$? (init silent)"

# Verify state file created
test -f /tmp/claude-hooks-session-*.state && echo "state file: OK"

# Second run без commits — should silent
./.claude/hooks/stop-reminder.sh
echo "EXIT=$? (silent, no changes)"

# Verify log shows decisions
tail -3 /tmp/claude-hooks-Stop.log
```

Expected: первый run — `EXIT=0`, init log. Второй run — `EXIT=0`, silent log (commits=false, progress_unchanged=true, cooldown=true → all conditions for silent).

- [ ] **Step 5: Commit**

```bash
git add .claude/hooks/stop-reminder.sh
git commit -m "feat(.claude): Stop hook - conditional reminder при commits без progress.md update

Approach B (см. spec): state file + 5-min cooldown. Reminder печатается
только когда (а) в сессии были commits И (б) progress.md не обновлялся
И (в) >5 мин с последнего reminder.

Idempotent, не noisy. State file в /tmp/claude-hooks-session-<id>.state.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: PreToolUse(Bash) hook `.claude/hooks/pre-bash-guard.sh`

**Files:**
- Create: `.claude/hooks/pre-bash-guard.sh`

- [ ] **Step 1: Создать `pre-bash-guard.sh`**

Use Write tool. Content:

```bash
#!/usr/bin/env bash
# PreToolUse(Bash) hook - guards для destructive / heavy commands.
#
# Block patterns (exit 2):
#   - git commit --no-verify (обходит pre-commit hooks)
#
# Warn patterns (exit 0 + stderr):
#   - ./mvnw verify (full, без точечных тестов) - reminder про cadence
#
# Note: git push --force намеренно НЕ дублируется здесь - это в
# settings.json permissions.deny (Sub-project A Task 9), которые
# check'аются до hooks. Single source of truth там.

set -uo pipefail

source "$(dirname "$0")/lib/common.sh"
check_bypass
check_jq

# Parse stdin JSON
input=$(cat)
command=$(echo "$input" | jq -r '.tool_input.command // ""')

# Block patterns
if [[ "$command" =~ git[[:space:]]+commit.*--no-verify ]]; then
  echo "❌ --no-verify обходит pre-commit hooks. См. feedback_commit_conventions.md в memory." >&2
  echo "Если Абдула explicit запросил — set CLAUDE_HOOKS_DISABLE=1 для этой команды." >&2
  log_decision "PreToolUse" "blocked" "--no-verify pattern in: ${command:0:80}"
  exit 2
fi

# Warn patterns (non-blocking)
if [[ "$command" =~ ^\./mvnw[[:space:]]+verify[[:space:]]*$ ]]; then
  echo "⚠️  Full ./mvnw verify ~2-3 минуты. См. feedback_no_frequent_builds — запускать только на ключевых этапах." >&2
  log_decision "PreToolUse" "warned" "full verify: $command"
  exit 0
fi

log_decision "PreToolUse" "passed" "command: ${command:0:80}"
exit 0
```

- [ ] **Step 2: chmod +x**

```bash
chmod +x /home/basnukaev/projects/argument-map/.claude/hooks/pre-bash-guard.sh
```

- [ ] **Step 3: Syntax check**

```bash
bash -n /home/basnukaev/projects/argument-map/.claude/hooks/pre-bash-guard.sh
echo "EXIT=$?"
```

Expected: `EXIT=0`.

- [ ] **Step 4: Dry-run test (block pattern)**

```bash
# Test block pattern
echo '{"tool_name":"Bash","tool_input":{"command":"git commit -m test --no-verify","description":""}}' \
  | /home/basnukaev/projects/argument-map/.claude/hooks/pre-bash-guard.sh
echo "EXIT=$? (expect 2)"

# Test legit command
echo '{"tool_name":"Bash","tool_input":{"command":"git status","description":""}}' \
  | /home/basnukaev/projects/argument-map/.claude/hooks/pre-bash-guard.sh
echo "EXIT=$? (expect 0)"

# Test warn pattern
echo '{"tool_name":"Bash","tool_input":{"command":"./mvnw verify","description":""}}' \
  | /home/basnukaev/projects/argument-map/.claude/hooks/pre-bash-guard.sh
echo "EXIT=$? (expect 0, but stderr has warn)"

# Test bypass
CLAUDE_HOOKS_DISABLE=1 echo '{"tool_name":"Bash","tool_input":{"command":"git commit -m test --no-verify"}}' \
  | CLAUDE_HOOKS_DISABLE=1 /home/basnukaev/projects/argument-map/.claude/hooks/pre-bash-guard.sh
echo "EXIT=$? (expect 0 via bypass)"
```

Expected: first command exits 2 (blocked), second exits 0, third exits 0 with stderr warn, fourth exits 0 (bypass).

- [ ] **Step 5: Commit**

```bash
git add .claude/hooks/pre-bash-guard.sh
git commit -m "feat(.claude): PreToolUse(Bash) hook - block --no-verify, warn про full verify

Block patterns (exit 2):
- git commit --no-verify (обходит pre-commit hooks)

Warn patterns (exit 0 + stderr):
- ./mvnw verify (full, без точечных тестов)

git push --force НЕ дублируется - это в settings.json deny rules
(Sub-project A), single source of truth там.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: PostToolUse(Edit|Write) hook `.claude/hooks/post-edit-reminder.sh`

**Files:**
- Create: `.claude/hooks/post-edit-reminder.sh`

- [ ] **Step 1: Создать `post-edit-reminder.sh`**

Use Write tool. Content:

```bash
#!/usr/bin/env bash
# PostToolUse(Edit|Write) hook - doc-update reminders для специфичных
# file patterns.
#
# Patterns (non-blocking, stderr reminders):
#   - backend/.../web/dto/*.java или web/controller/*.java
#     → reminder про npm run generate-api
#   - backend/.../db/changelog/changes/*.xml
#     → reminder про architecture.md
#   - docs/decisions.md
#     → reminder про pointer в backend/docs/<topic>.md
#   - backend/src/main/resources/application*.yml
#     → reminder про restart backend

set -uo pipefail

source "$(dirname "$0")/lib/common.sh"
check_bypass
check_jq

input=$(cat)
file_path=$(echo "$input" | jq -r '.tool_input.file_path // ""')

remind() {
  echo "💡 $1" >&2
}

case "$file_path" in
  *backend/*/web/dto/*.java|*backend/*/web/controller/*.java)
    remind "DTO/Controller изменён. Регенерация: cd frontend && npm run generate-api"
    log_decision "PostToolUse" "reminded" "DTO/Controller: $file_path"
    ;;
  *backend/*/db/changelog/changes/*.xml)
    remind "Liquibase migration изменена. Обнови docs/architecture.md если затронута схема (см. backend/CLAUDE.md «После коммита» чек-лист)."
    log_decision "PostToolUse" "reminded" "migration: $file_path"
    ;;
  *docs/decisions.md)
    remind "ADR изменён. Добавь pointer в backend/docs/<topic>.md если соответствующий topical файл существует."
    log_decision "PostToolUse" "reminded" "ADR: $file_path"
    ;;
  *backend/src/main/resources/application*.yml|*backend/src/main/resources/application*.yaml)
    remind "application.yml изменён. Restart backend (см. CLAUDE.md «Dev server management»)."
    log_decision "PostToolUse" "reminded" "config: $file_path"
    ;;
  *)
    log_decision "PostToolUse" "silent" "no pattern match: $file_path"
    ;;
esac

exit 0
```

- [ ] **Step 2: chmod +x**

```bash
chmod +x /home/basnukaev/projects/argument-map/.claude/hooks/post-edit-reminder.sh
```

- [ ] **Step 3: Syntax check**

```bash
bash -n /home/basnukaev/projects/argument-map/.claude/hooks/post-edit-reminder.sh
echo "EXIT=$?"
```

Expected: `EXIT=0`.

- [ ] **Step 4: Dry-run test (pattern matching)**

```bash
# Test DTO pattern
echo '{"tool_name":"Edit","tool_input":{"file_path":"/proj/backend/src/main/java/ru/basnukaev/argumentmap/web/dto/TopicRequest.java"}}' \
  | /home/basnukaev/projects/argument-map/.claude/hooks/post-edit-reminder.sh 2>&1
echo "EXIT=$? (expect 0 + reminder)"

# Test silent (non-matching)
echo '{"tool_name":"Edit","tool_input":{"file_path":"/proj/README.md"}}' \
  | /home/basnukaev/projects/argument-map/.claude/hooks/post-edit-reminder.sh 2>&1
echo "EXIT=$? (expect 0 + silent)"

# Verify log
tail -3 /tmp/claude-hooks-PostToolUse.log
```

Expected: DTO test prints reminder, silent test no output. Log shows both decisions.

- [ ] **Step 5: Commit**

```bash
git add .claude/hooks/post-edit-reminder.sh
git commit -m "feat(.claude): PostToolUse(Edit|Write) hook - doc-update reminders

Pattern-based reminders на stderr (non-blocking):
- DTO/Controller → npm run generate-api
- Liquibase migration → architecture.md update
- ADR → pointer в backend/docs/<topic>.md
- application.yml → restart backend

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Зарегистрировать hooks в `.claude/settings.json`

**Files:**
- Modify: `.claude/settings.json`

- [ ] **Step 1: Прочитать current settings.json**

```bash
cat /home/basnukaev/projects/argument-map/.claude/settings.json
```

Expected: видим existing keys `statusLine`, `permissions` (6 deny rules), `env` (1 var). Нет `hooks` key.

- [ ] **Step 2: Add hooks section**

Use Edit tool. Заменить блок:

```json
  "env": {
    "CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS": "1"
  }
}
```

на:

```json
  "env": {
    "CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS": "1"
  },
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

- [ ] **Step 3: Validate JSON**

```bash
cat /home/basnukaev/projects/argument-map/.claude/settings.json | jq '.'
echo "EXIT=$? (expect 0 - valid JSON)"
```

Expected: pretty-printed JSON без ошибок, `EXIT=0`.

- [ ] **Step 4: Validate hooks section structure**

```bash
cat /home/basnukaev/projects/argument-map/.claude/settings.json | \
  jq '.hooks | keys' | xargs echo "Hook events:"
cat /home/basnukaev/projects/argument-map/.claude/settings.json | \
  jq '.permissions.deny | length' | xargs echo "Deny rules (untouched):"
```

Expected: `Hook events: [ "PostToolUse", "PreToolUse", "SessionStart", "Stop" ]` (alphabetical), `Deny rules (untouched): 6`.

- [ ] **Step 5: Commit**

```bash
git add .claude/settings.json
git commit -m "chore(.claude): зарегистрировать 4 hooks в settings.json

Append hooks section к existing statusLine + permissions (6 deny
rules от Sub-project A) + env. Hooks:
- SessionStart → session-start.sh
- Stop → stop-reminder.sh
- PreToolUse (matcher: Bash) → pre-bash-guard.sh
- PostToolUse (matcher: Edit|Write) → post-edit-reminder.sh

Все scripts ссылаются через \$CLAUDE_PROJECT_DIR/.claude/hooks/<name>.sh.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: README в `.claude/hooks/README.md`

**Files:**
- Create: `.claude/hooks/README.md`

- [ ] **Step 1: Создать README**

Use Write tool. Content:

```markdown
# Claude Code Hooks для argument-map

Sub-project B из Claude Code harness setup (статья Anthropic May 2026
«How Claude Code works in large codebases»). См. design spec:
`docs/superpowers/specs/2026-05-19-hooks-setup-design.md`.

## Hooks overview

| Event | Script | Purpose |
|---|---|---|
| SessionStart | `session-start.sh` | Load свежие progress.md (2 entries) + active roadmap + текущий приоритет в context |
| Stop | `stop-reminder.sh` | Conditional reminder если в сессии были commits но progress.md не обновлялся |
| PreToolUse(Bash) | `pre-bash-guard.sh` | Block `git commit --no-verify`. Warn для `./mvnw verify` |
| PostToolUse(Edit\|Write) | `post-edit-reminder.sh` | Doc-update reminders для DTO/Controller/migration/ADR/application.yml |

## Bypass

Set env var:
```bash
export CLAUDE_HOOKS_DISABLE=1
```

Или для single command:
```bash
CLAUDE_HOOKS_DISABLE=1 <command>
```

Все hooks early `exit 0` при detected bypass.

## Dependencies

- `bash` — есть в WSL2 by default
- `jq` — required для PreToolUse / PostToolUse (parsing JSON stdin).
  **Graceful degradation:** если `jq` missing, эти hooks тихо `exit 0`
  (не блокируют workflow). Install: `sudo apt install jq`
- `git`, `sha256sum`, `awk`, `sed`, `grep` — есть в coreutils

## Logging

Каждый hook логирует в `/tmp/claude-hooks-<event>.log`. Формат:
```
[ISO-8601 timestamp] event=<name> decision=<silent|reminded|blocked|...> reason=<brief>
```

Real-time debugging:
```bash
tail -f /tmp/claude-hooks-*.log
```

Logs **не** в git — runtime artifacts.

## Manual smoke tests

Hooks нельзя unit-tested — Claude Code не имеет dedicated test framework
для hooks. После implementation запустить **в новой Claude Code session**
(текущая session использует cached settings.json):

### SessionStart smoke
1. Restart Claude Code или запустить `/start_conv` slash command
2. Verify в context Claude'а появилось:
   - Last 2 progress entries (Сессии 47 и 46)
   - Active roadmap stage
   - «Текущий приоритет» секция
3. Test bypass: `CLAUDE_HOOKS_DISABLE=1 claude` → verify hooks тихие

### Stop smoke
1. Touch dummy file, `git add`, `git commit -m "test"`
2. Trigger Claude response (просить trivial Read)
3. Verify reminder появился в context
4. Update `docs/progress.md` (touch + commit)
5. Trigger ещё один Claude response
6. Verify reminder исчез
7. Test cooldown: trigger 2+ responses без update — verify only 1 reminder per 5min

### PreToolUse(Bash) smoke
1. Попытка `git commit --no-verify -m "test"` через Claude → verify blocked + stderr message
2. Попытка `git commit -m "test"` (legit) → verify passed
3. `CLAUDE_HOOKS_DISABLE=1` + retry `--no-verify` → verify passed

### PostToolUse(Edit|Write) smoke
1. Edit dummy `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/Test.java`
   → verify reminder про `npm run generate-api` в stderr
2. Edit `backend/src/main/java/ru/basnukaev/argumentmap/service/Test.java`
   (не DTO/controller) → verify silent
3. Revert dummy edit

## State file (Stop hook)

`/tmp/claude-hooks-session-${CLAUDE_SESSION_ID:-default}.state` — Bash
key=value pairs:
- `session_start_head` — git HEAD at first response in session
- `session_start_progress_sha` — sha256 of docs/progress.md at first response
- `last_reminder_at` — Unix timestamp of last printed reminder (для cooldown)

Cleanup: state files auto-orphan при new session (но не удаляются
автоматически). Опциональный cleanup: `rm /tmp/claude-hooks-session-*.state`.

## Edge cases / known limitations

- Hooks применяются только при **restart** Claude Code session
  (settings.json кэшируется в memory). Inline test не возможен в той же
  session где hooks добавлены
- `CLAUDE_SESSION_ID` может быть не set в некоторых Claude Code версиях
  → fallback на `"default"` → state file shared между sessions (acceptable
  trade-off)
- Stop hook fires per response — если Claude long task с 50+ responses,
  потенциально 50+ hook invocations. Cooldown 5min ограничивает spam
- jq missing → PreToolUse и PostToolUse silent skip. SessionStart hook
  печатает install instruction
- Hook scripts read-only с точки зрения source files. Mутируют только
  `/tmp/claude-hooks-*` (logs + state)
```

- [ ] **Step 2: Commit**

```bash
git add .claude/hooks/README.md
git commit -m "docs(.claude): README для hooks с overview + bypass + smoke tests

Описание 4 hooks, bypass mechanism (CLAUDE_HOOKS_DISABLE), dependencies
(jq graceful degradation), logging (/tmp/claude-hooks-<event>.log), manual
smoke tests для каждого hook'а, state file format для Stop hook, edge
cases / known limitations.

Smoke tests требуют restart Claude Code session т.к. settings.json
cached в memory активной session.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: Handoff Sub-project B closed

**Files:**
- Modify: `docs/progress.md`

- [ ] **Step 1: Прочитать current progress.md head**

```bash
head -20 /home/basnukaev/projects/argument-map/docs/progress.md
```

Expected: видим header + ссылки на archive + `---` separator + начало Сессии 47 запись.

- [ ] **Step 2: Add entry в начало записей**

Use Edit tool. Найти Сессии 47 запись (created в Sub-project A handoff) и заменить её start:

Заменить:
```
## 2026-05-19 - Сессия 47 - Claude Code harness Sub-project A: Foundation cleanup
```

на:
```
## 2026-05-19 - Сессия 47 - Claude Code harness Sub-projects A + B (Foundation cleanup + Hooks setup)

### Sub-project B (Hooks setup) - closed

Spec + plan + 7 атомарных execution коммитов + handoff:

- `e4eed41` `docs: spec для Sub-project B (Hooks setup) Claude Code harness` — brainstorm spec с 4 hooks design (Approach B для Stop hook)
- `<plan-hash>` `docs: implementation plan для Sub-project B` — detailed plan на 8 атомарных tasks
- `<task1-hash>` `feat(.claude): hooks lib/common.sh` — shared helpers (bypass, jq check, log_decision)
- `<task2-hash>` `feat(.claude): SessionStart hook` — load progress + roadmap + приоритет в context
- `<task3-hash>` `feat(.claude): Stop hook` — conditional reminder (state file + 5min cooldown)
- `<task4-hash>` `feat(.claude): PreToolUse(Bash) hook` — block --no-verify, warn ./mvnw verify
- `<task5-hash>` `feat(.claude): PostToolUse(Edit|Write) hook` — doc-update reminders (4 patterns)
- `<task6-hash>` `chore(.claude): зарегистрировать hooks в settings.json` — добавлено hooks секция
- `<task7-hash>` `docs(.claude): README для hooks` — overview + bypass + smoke tests + state file format

### Решения Sub-project B

- Approach B для Stop hook (conditional state file vs Approach C UserPromptSubmit) — immediate feedback важнее performance saving
- 5-min cooldown между Stop reminders — balanced между timely и noisy
- 4 PostToolUse patterns: DTO/Controller, Liquibase, ADR, application.yml. Не покрыто: frontend изменения, hooks themselves (избежать meta-loop)
- jq graceful degradation вместо hard requirement — hooks работают без jq (тихо exit 0) если PreToolUse/PostToolUse content зависим от parsing
- git push --force НЕ дублируется в PreToolUse hook — это в settings.json deny rules (Sub-project A), single source of truth
- Manual smoke tests deferred до новой Claude Code session (settings.json cached в memory активной session)

### Известные ограничения

- Hooks fire только при restart Claude Code session (settings.json cached). Текущая session не получает benefit
- CLAUDE_SESSION_ID может быть unset → state file shared между sessions (acceptable trade-off)
- Stop hook fires per response — потенциальный overhead на long sessions. 5-min cooldown ограничивает

### Sub-project A (Foundation cleanup) - closed

```

(Сохраняется существующая запись Sub-project A ниже.)

- [ ] **Step 3: Verify**

```bash
head -50 /home/basnukaev/projects/argument-map/docs/progress.md
```

Expected: видим обновлённый header «Сессия 47 - Sub-projects A + B», секцию «Sub-project B (Hooks setup) - closed», followed by «Sub-project A (Foundation cleanup) - closed» (existing).

- [ ] **Step 4: Replace task hashes с актуальными SHA**

```bash
cd /home/basnukaev/projects/argument-map && git log --oneline -10
```

Note hashes для последних 8 commits (Task 1-7 + plan commit). Replace placeholders `<plan-hash>`, `<task1-hash>`...`<task7-hash>` в progress.md через Edit tool.

- [ ] **Step 5: Final handoff commit**

```bash
git add docs/progress.md
git commit -m "docs: handoff Sub-project B (Hooks setup) closed

Sub-project B из Claude Code harness setup закрыт. Изменения:
- 4 hooks в .claude/hooks/ (SessionStart, Stop, PreToolUse(Bash),
  PostToolUse(Edit|Write))
- Shared lib/common.sh с bypass/jq/log helpers
- README с smoke test plan
- Hooks зарегистрированы в .claude/settings.json
- Bypass: CLAUDE_HOOKS_DISABLE=1 env var

Manual smoke tests deferred до новой Claude Code session
(settings.json cached в memory). После restart - smoke test
4 scenarios из .claude/hooks/README.md.

Следующий этап: Sub-project C (Skills) либо D (LSP) либо E
(periodic review) - можно параллельно.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review

**1. Spec coverage:**

| Spec section / criterion | Plan task |
|---|---|
| 4.1 Component overview (4 hooks + lib) | Tasks 1-5 |
| 4.2 SessionStart logic | Task 2 |
| 4.3 Stop logic (Approach B + state file + cooldown) | Task 3 |
| 4.4 PreToolUse(Bash) logic | Task 4 |
| 4.5 PostToolUse(Edit\|Write) logic | Task 5 |
| 4.6 Bypass mechanism (env var) | Task 1 (`check_bypass`) + used в всех hooks |
| 4.7 Shared library | Task 1 |
| 4.8 settings.json hooks section | Task 6 |
| 4.9 Logging | Task 1 (`log_decision`) + used в всех hooks |
| 4.10 Dependencies (jq graceful) | Task 1 (`check_jq`) + Task 2 (SessionStart install hint) |
| 4.11 Manual smoke tests | Task 7 (README docs the plan; actual tests deferred к new session) |
| Section 5 Acceptance criteria 1-9 | Все criteria покрыты Tasks 1-8 |
| Section 6 8 commits decomposition | Tasks 1-8 (1-к-1) |

Покрытие полное.

**2. Placeholder scan:**

- Нет «TBD» / «TODO» / «fill in details»
- `<task1-hash>`...`<task7-hash>` + `<plan-hash>` в Task 8 Step 2 — это **намеренные** placeholders для git SHA которые становятся известны **только после commit'а**. Engineer заполняет их при Step 4 из `git log --oneline -10`. Это legitimate runtime data, не placeholder failure.

**3. Type / naming consistency:**

- `check_bypass`, `check_jq`, `log_decision` — defined в Task 1, used везде. Consistent.
- Event names в log_decision (SessionStart, Stop, PreToolUse, PostToolUse) — matched settings.json hooks keys. Consistent.
- Path `$CLAUDE_PROJECT_DIR/.claude/hooks/<name>.sh` — consistent в Tasks 6 settings.json и в actual file paths Tasks 1-5.

Verification passed.

---

## Execution Handoff

По MAX autonomy mode (memory `feedback_full_autonomy` второй level), skip execution choice question. **Выбираю Inline Execution** automatically — invoke `superpowers:executing-plans` skill сразу после этого plan'а.

Reasoning для Inline (а не Subagent-Driven):
- Pure docs/config работа, контекст у меня в голове
- Bash scripts короткие (~30-50 LOC каждый), не requires subagent-level isolation
- Хороший trade-off speed vs review safety — atomic коммиты сами по себе обеспечивают rollback
