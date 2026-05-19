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
