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
