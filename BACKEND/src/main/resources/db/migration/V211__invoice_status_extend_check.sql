-- V211: Extend the invoice status CHECK to cover the states the application actually writes.
--
-- The bank-transfer payment path sets status = 'PENDING_PAYMENT_VERIFICATION' (awaiting admin
-- confirmation of the transfer) and the admin billing path sets 'VOID'. Neither was in the original
-- CHECK (V3), so both writes raised a constraint violation → HTTP 500, making bank-transfer payment
-- and invoice voiding non-functional against Postgres. 'VOID' was already expected by the revenue
-- rollup query (V3: status NOT IN ('CANCELLED','VOID')), confirming it is an intended state.
--
-- Widening the allowed set is safe: every existing row already holds one of the original values.

ALTER TABLE invoices DROP CONSTRAINT IF EXISTS check_invoice_status;

ALTER TABLE invoices ADD CONSTRAINT check_invoice_status
    CHECK (status IN ('DRAFT', 'SENT', 'PAID', 'OVERDUE', 'CANCELLED', 'PARTIALLY_PAID',
                      'PENDING_PAYMENT_VERIFICATION', 'VOID'));
