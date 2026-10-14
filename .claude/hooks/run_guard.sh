#!/bin/sh
# Runs guard_git_scope.py with whichever Python this machine has (`py` on
# Windows, `python3` on macOS/Linux). Fails CLOSED: if no Python is found,
# or the guard crashes or prints nothing, the command is refused rather
# than silently allowed.
dir=$(dirname "$0")
deny() {
    printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"Blocked: %s"}}\n' "$1"
    exit 0
}

if command -v py >/dev/null 2>&1; then
    python="py"
elif python3 -c "import sys" >/dev/null 2>&1; then
    python="python3"
else
    deny "no Python found to run the git safety guard (.claude/hooks/run_guard.sh)."
fi

output=$($python "$dir/guard_git_scope.py") || deny "the git safety guard failed to run."
[ -n "$output" ] || deny "the git safety guard produced no decision."
printf '%s\n' "$output"
