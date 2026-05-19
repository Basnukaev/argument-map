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
