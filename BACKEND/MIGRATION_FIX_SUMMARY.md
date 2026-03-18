# Fraud_Detector Database Migration Fix Summary

## Root Cause Analysis

### Issue 1: Missing Core Tables (Critical)
**Files:** V99__Dummy_Credentials.sql, V107__sample_data.sql
- `platform_users` table is referenced but NEVER created
- `roles` table is referenced but NEVER created
- These tables appear to be from a legacy schema that was never migrated

### Issue 2: Wrong Primary Key References (Critical)
**Files:** V102__add_score_fields_to_case_alerts.sql, V103__fix_case_queues_schema.sql
- `compliance_cases` table has `case_id` as primary key (created in V2)
- These migrations reference `compliance_cases(id)` which doesn't exist

### Issue 3: Duplicate Table Creation (Critical)
**File:** V103__fix_case_queues_schema.sql
- Creates `case_queues` table again (already defined in V8)
- Creates `case_activities` table again (already defined in V8)

### Issue 4: Type Mismatches in Foreign Keys (High)
**File:** V101__runtime_errors_tracking.sql
- `psps.psp_id` is VARCHAR(50) but referenced as BIGINT

**File:** V103__fix_case_queues_schema.sql
- `case_queues.psp_id` defined as VARCHAR(50) but should match `psps.psp_id`
- Creates duplicate case_activities with foreign key to wrong column

### Issue 5: Schema Drift in Sample Data (Medium)
**File:** V107__sample_data.sql
- References columns in `merchants`, `transactions`, `alerts`, `compliance_cases` that don't exist
- References `platform_users` table that doesn't exist

## Fix Strategy

1. **Create Missing Core Tables** (New Migration V14)
   - Create `roles` table
   - Create `platform_users` table with proper FK to roles

2. **Fix V101** - runtime_errors_tracking
   - Fix psp_id type (VARCHAR not BIGINT)
   - Remove user_id FK if causing issues

3. **Fix V102** - case_alerts
   - Change FK from `compliance_cases(id)` to `compliance_cases(case_id)`

4. **Fix V103** - case_queues_schema
   - Remove duplicate table creations
   - Fix FK references to use `case_id` not `id`
   - Fix psp_id type consistency

5. **Fix V99** - Dummy_Credentials
   - Depends on roles and platform_users tables (V14)
   - Fix any column mismatches

6. **Fix V107** - sample_data
   - Fix column references to match actual schema
   - Add proper NULL handling

## Migration Order Fix

The migrations must be reordered/rebased so that:
- V14: Create roles and platform_users tables (BEFORE V99)
- V99: Depends on V14
- V101-V103: Fixed FK references
- V107: Fixed column references

## Files to Modify

1. `V14__create_roles_and_platform_users.sql` (NEW - must run before V99)
2. `V101__runtime_errors_tracking.sql` (FIXED)
3. `V102__add_score_fields_to_case_alerts.sql` (FIXED)
4. `V103__fix_case_queues_schema.sql` (FIXED)
5. `V99__Dummy_Credentials.sql` (FIXED)
6. `V107__sample_data.sql` (FIXED)

## Testing Checklist

- [ ] Fresh database migration runs without errors
- [ ] All tables created with correct columns
- [ ] All foreign keys properly reference existing columns
- [ ] Sample data inserts successfully
- [ ] Backend application starts without Hibernate errors
