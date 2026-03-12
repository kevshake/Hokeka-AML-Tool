#!/bin/bash
# monthly-report.sh - Generate monthly auto-push activity report
# Run: ./monthly-report.sh [YYYY-MM]

REPO_DIR="/root/.openclaw/workspace/fraud-detector"
LOG_FILE="$REPO_DIR/.auto-push.log"
ERROR_LOG="$REPO_DIR/.auto-push-errors.log"

MONTH="${1:-$(date '+%Y-%m')}"

echo "========================================"
echo "Fraud Detector Auto-Push Report"
echo "Month: $MONTH"
echo "Generated: $(date '+%Y-%m-%d %H:%M:%S UTC')"
echo "========================================"
echo ""

if [ -f "$LOG_FILE" ]; then
    COMMITS=$(grep "$MONTH" "$LOG_FILE" | wc -l)
    PUSHES=$(grep "$MONTH" "$LOG_FILE" | grep "Pushed" | wc -l)
    echo "Commits: $COMMITS"
    echo "Successful pushes: $PUSHES"
    echo ""
    echo "Recent activity:"
    grep "$MONTH" "$LOG_FILE" | tail -20
else
    echo "No activity log found."
fi

if [ -f "$ERROR_LOG" ]; then
    ERRORS=$(grep "$MONTH" "$ERROR_LOG" | wc -l)
    if [ "$ERRORS" -gt 0 ]; then
        echo ""
        echo "⚠️  ERRORS: $ERRORS"
        echo ""
        grep "$MONTH" "$ERROR_LOG"
    else
        echo ""
        echo "✓ No errors this month."
    fi
else
    echo ""
    echo "✓ No error log found."
fi
