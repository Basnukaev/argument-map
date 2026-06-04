# Claude Code Hooks для argument-map

Sub-project B из Claude Code harness setup (статья Anthropic May 2026
«How Claude Code works in large codebases»). См. design spec:
`docs/specs/2026-05-19-hooks-setup-design.md`.

## Hooks overview

| Event | Script | Purpose |
|---|---|---|
| SessionStart | `session-start.sh` | Load свежие progress.md (2 entries) + active roadmap + текущий приоритет в context |
| Stop | `stop-reminder.sh` | Conditional reminder если в сессии были commits но progress.md не обновлялся |
| PreToolUse(Bash) | `pre-bash-guard.sh` | Block `git commit --no-verify`. Warn для `./mvnw verify` |
| PostToolUse(Edit\|Write) | `post-edit-reminder.sh` | Doc-update reminders для DTO/Controller/migration/ADR/application.yml |

## Bypass

Set env var в shell от которого стартует Claude Code:
```bash
export CLAUDE_HOOKS_DISABLE=1
claude
```

Или один-time для current shell:
```bash
CLAUDE_HOOKS_DISABLE=1 claude
```

Все hooks early `exit 0` при detected bypass.

**Важно — limitation для inline bash commands:**

`CLAUDE_HOOKS_DISABLE=1 <command>` **внутри** Claude Code Bash tool
**не bypass'ит** PreToolUse/PostToolUse hooks. Причина: hook process
inherits Claude Code's environment (без inline env prefix), не
environment команды которую Claude собирается выполнить.

Корректные способы bypass'нуть PreToolUse/PostToolUse:
1. **Restart Claude Code** с `export CLAUDE_HOOKS_DISABLE=1` в shell
2. **Set в `.claude/settings.json`** под `env` key (persistent для всех
   sessions):
   ```json
   "env": { "CLAUDE_HOOKS_DISABLE": "1" }
   ```
3. **SessionStart и Stop hooks** работают с inline prefix т.к. они
   fire независимо от tool commands

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

## Smoke test priority (новая сессия)

**Перед relying на hooks в production workflow — verify в новой Claude
Code session:**

1. **`$CLAUDE_PROJECT_DIR` expansion** в settings.json hook commands —
   Claude Code должен expand variable в actual project path. Если NOT
   expanded → все hooks silently fail (script not found). Test:
   запустить тривиальный edit или bash и check `/tmp/claude-hooks-*.log`
   — если log пуст после tool use → expansion broken
2. Затем — manual smoke tests из «Manual smoke tests» секции выше

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
