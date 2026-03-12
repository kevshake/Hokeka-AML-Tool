#!/bin/bash
# auto-push.sh - Silently commit and push changes to git
# Runs every hour via cron
# Only outputs on errors or when changes are committed/pushed

REPO_DIR="/root/.openclaw/workspace/fraud-detector"
LOG_FILE="/root/.openclaw/workspace/fraud-detector/.auto-push.log"
ERROR_LOG="/root/.openclaw/workspace/fraud-detector/.auto-push-errors.log"

# Change to repo directory
cd "$REPO_DIR" || exit 1

# Check if there are changes to commit
if git diff --quiet && git diff --cached --quiet; then
    # No changes - exit silently
    exit 0
fi

# Add all changes
git add -A

# Commit with timestamp
COMMIT_MSG="Auto-commit: $(date '+%Y-%m-%d %H:%M:%S UTC')"
if ! git commit -m "$COMMIT_MSG" > /dev/null 2>&1; then
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ✗ Commit failed" >> "$ERROR_LOG"
    exit 1
fi

# Log successful commit
echo "[$(date '+%Y-%m-%d %H:%M:%S')] ✓ Committed: $COMMIT_MSG" >> "$LOG_FILE"

# Push to remote (requires credentials)
CURRENT_BRANCH=$(git branch --show-current)
if git push origin "$CURRENT_BRANCH" > /dev/null 2>&1; then
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ✓ Pushed: $COMMIT_MSG" >> "$LOG_FILE"
else
    # Push failed - log error but don't spam
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ⚠ Push failed (credentials needed): $COMMIT_MSG" >> "$ERROR_LOG"
fi
