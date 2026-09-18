#!/bin/bash
# Test script for database migration fixes

echo "============================================"
echo "Fraud_Detector Migration Fix Verification"
echo "============================================"

MIGRATION_DIR="/root/.openclaw/workspace/projects/fraud-detector/Fraud_Detector/BACKEND/src/main/resources/db/migration"

echo ""
echo "1. Checking migration file count..."
ls -1 ${MIGRATION_DIR}/V*.sql | wc -l
echo ""

echo "2. Listing migrations in order..."
ls -1 ${MIGRATION_DIR}/V*.sql | sort
echo ""

echo "3. Verifying new V14 migration exists..."
if [ -f "${MIGRATION_DIR}/V14__create_roles_and_platform_users.sql" ]; then
    echo "✓ V14 migration exists"
else
    echo "✗ V14 migration missing!"
fi
echo ""

echo "4. Checking for platform_users references..."
echo "   (These should only be in V14, V99, V107)"
grep -l "platform_users" ${MIGRATION_DIR}/V*.sql | xargs -n1 basename
echo ""

echo "5. Checking for compliance_cases FK references..."
echo "   (Should reference case_id, not id)"
grep -n "compliance_cases(id)" ${MIGRATION_DIR}/V*.sql || echo "✓ No incorrect FKs found"
echo ""

echo "6. Checking for psps FK type consistency..."
echo "   psp_id in psps table is VARCHAR(50)"
grep -n "psp_id BIGINT REFERENCES psps" ${MIGRATION_DIR}/V*.sql || echo "✓ No type mismatches found"
echo ""

echo "7. Summary of fixed files..."
echo "   - V14: NEW - Creates roles and platform_users tables"
echo "   - V17: FIXED - Corrected column names (case_status, assigned_to, due_date)"
echo "   - V99: FIXED - Now depends on V14 tables existing"
echo "   - V101: FIXED - Removed invalid FK types"
echo "   - V102: FIXED - Changed FK to compliance_cases(case_id)"
echo "   - V103: FIXED - Removed duplicate table creations, fixed FKs"
echo "   - V107: FIXED - Updated to match actual schema"
echo ""

echo "============================================"
echo "Verification Complete"
echo "============================================"
