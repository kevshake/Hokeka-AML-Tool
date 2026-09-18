-- W35-1: backfill alerts.psp_id from merchants so the Hibernate tenant filter on alerts
-- does not hide legacy rows that were only scoped via merchant join.
UPDATE alerts a
SET psp_id = m.psp_id
FROM merchants m
WHERE a.psp_id IS NULL
  AND a.merchant_id IS NOT NULL
  AND a.merchant_id = m.merchant_id;

-- Rows tied to a transaction but missing merchant_id / psp_id inherit from the transaction.
UPDATE alerts a
SET psp_id = t.psp_id
FROM transactions t
WHERE a.psp_id IS NULL
  AND a.txn_id IS NOT NULL
  AND a.txn_id = t.txn_id
  AND t.psp_id IS NOT NULL;

COMMENT ON COLUMN alerts.psp_id IS 'Tenant scope for Hibernate pspTenantFilter; backfilled from merchant/transaction in V222.';
