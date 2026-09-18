-- V210: Link usage rows to the invoice that consumed them.
--
-- Enables the billing cycle to bill each unit of consumption exactly once: generateMonthlyInvoice
-- reads only rows with invoice_id IS NULL and stamps them with the new invoice's id, so a re-run of
-- the monthly cycle (or an overlapping period) can never double-bill. Idempotency of the invoice
-- itself is enforced in application code via (psp_id, billing_period_start) lookup; a hard UNIQUE
-- index is intentionally NOT added here because a legacy database may already hold duplicate manual
-- invoices for a period, and a failing CREATE UNIQUE INDEX would abort the whole migration run.

ALTER TABLE api_usage_logs ADD COLUMN IF NOT EXISTS invoice_id BIGINT;

-- Fast lookup of the rows consumed by a given invoice.
CREATE INDEX IF NOT EXISTS idx_api_usage_invoice
    ON api_usage_logs(invoice_id);

-- Partial index for the hot path in invoice generation: un-invoiced billable rows for a PSP window.
CREATE INDEX IF NOT EXISTS idx_api_usage_uninvoiced
    ON api_usage_logs(psp_id, request_timestamp)
    WHERE invoice_id IS NULL;
