#!/bin/bash
# auto-push.sh - Automatically commit and push changes to git
# Runs every 5 minutes via cron
# NOTE: Requires git credentials to be configured for push to work

REPO_DIR="/root/.openclaw/workspace/fraud-detector"
LOG_FILE="/root/.openclaw/workspace/fraud-detector/.auto-push.log"

# Change to repo directory
cd "$REPO_DIR" || exit 1

# Check if there are changes to commit
if git diff --quiet && git diff --cached --quiet; then
    # No changes
    exit 0
fi

# Add all changes
git add -A

# Commit with timestamp
COMMIT_MSG="Auto-commit: $(date '+%Y-%m-%d %H:%M:%S UTC')"
git commit -m "$COMMIT_MSG" >> "$LOG_FILE" 2>&1

# Push to remote (requires credentials)
if git push origin $(git branch --show-current) >> "$LOG_FILE" 2>&1; then
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ✓ Pushed: $COMMIT_MSG" >> "$LOG_FILE"
else
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ⚠ Committed locally (push failed - check credentials): $COMMIT_MSG" >> "$LOG_FILE"
fi
