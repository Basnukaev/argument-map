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
