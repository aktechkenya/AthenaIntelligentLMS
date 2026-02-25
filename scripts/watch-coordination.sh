#!/usr/bin/env bash
# watch-coordination.sh
#
# Watches docs/AGENT_COORDINATION.md for changes from either agent.
# Run this in a terminal while both Claude Code sessions are active.
# It git-pulls every 30s and prints a diff whenever the file changes.
#
# Usage:  bash scripts/watch-coordination.sh

set -euo pipefail

REPO=/home/adira/AthenaIntelligentLMS
FILE=docs/AGENT_COORDINATION.md
INTERVAL=30

cd "$REPO"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Agent Coordination Watcher"
echo "  Watching: $FILE"
echo "  Polling every ${INTERVAL}s — Ctrl+C to stop"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

LAST_HASH=$(git log -1 --format="%H" -- "$FILE" 2>/dev/null || echo "none")

while true; do
    sleep "$INTERVAL"

    # Pull latest without touching local changes
    git fetch origin --quiet 2>/dev/null || true

    NEW_HASH=$(git log -1 --format="%H" origin/master -- "$FILE" 2>/dev/null || echo "none")

    if [ "$NEW_HASH" != "$LAST_HASH" ] && [ "$NEW_HASH" != "none" ]; then
        TIMESTAMP=$(date '+%H:%M:%S')
        AUTHOR=$(git log -1 --format="%an" "$NEW_HASH" -- "$FILE" 2>/dev/null || echo "unknown")
        MSG=$(git log -1 --format="%s" "$NEW_HASH" 2>/dev/null || echo "")

        echo ""
        echo "┌─ [$TIMESTAMP] COORDINATION FILE CHANGED ──────────────────"
        echo "│  Commit: ${NEW_HASH:0:8}  Author: $AUTHOR"
        echo "│  Message: $MSG"
        echo "├────────────────────────────────────────────────────────────"
        git diff "$LAST_HASH" "$NEW_HASH" -- "$FILE" 2>/dev/null \
            | grep -E "^\+[^+]|^-[^-]" \
            | sed 's/^+/│  + /; s/^-/│  - /' \
            | head -40
        echo "└────────────────────────────────────────────────────────────"

        # Merge the update locally
        git merge --ff-only origin/master --quiet 2>/dev/null || true
        LAST_HASH="$NEW_HASH"
    fi
done
