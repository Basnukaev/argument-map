#!/usr/bin/env bash
# SessionStart hook - loads свежий project context в Claude's context window.
# Stdout идёт в Claude's context (feature SessionStart hook'а).
#
# What it loads:
#   1. Last 2 entries из docs/progress.md (по ^## YYYY-MM-DD headers);
#      текущий приоритет = «Следующий шаг» последней записи (входит в #1)
#   2. Active roadmap stage из docs/roadmap.md (не "закрыт")
# (секция «Текущий приоритет» удалена 2026-06-04: SESSION_START_PROMPT
#  выпилен при переходе на OMC, приоритет живёт в progress.md)

set -uo pipefail
# Note: intentional БЕЗ `set -e` - SessionStart hook info-only,
# partial output (если awk fails на одной секции) лучше чем abort.
# Каждая секция guarded `[[ -f ... ]]`.

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

# jq availability hint (один раз при старте сессии)
if ! command -v jq >/dev/null 2>&1; then
  echo "⚠️  jq не установлен. Установи: sudo apt install jq (для PreToolUse/PostToolUse hooks)"
fi

log_decision "SessionStart" "loaded" "progress + roadmap"
exit 0
