-- V134: payment_attempts table
-- Tracks M-Pesa STK Push and bank transfer payment attempts against invoices.

CREATE TABLE IF NOT EXISTS payment_attempts (
    id                         BIGSERIAL PRIMARY KEY,
    invoice_id                 BIGINT NOT NULL,
    psp_id                     BIGINT NOT NULL,
    payment_method             VARCHAR(30) NOT NULL,          -- MPESA, BANK_TRANSFER, CARD
    amount                     NUMERIC(15,2) NOT NULL,
    currency                   VARCHAR(10) NOT NULL DEFAULT 'KES',
    phone_number               VARCHAR(20),                   -- for M-Pesa
    bank_reference             VARCHAR(100),                  -- for bank transfer
    mpesa_checkout_request_id  VARCHAR(100),                  -- from STK push response
    mpesa_merchant_request_id  VARCHAR(100),
    mpesa_transaction_id       VARCHAR(100),                  -- from callback
    status                     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    result_code                VARCHAR(10),
    result_description         TEXT,
    initiated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at               TIMESTAMP WITH TIME ZONE,
    created_at                 TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_payment_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(invoice_id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_psp    FOREIGN KEY (psp_id)     REFERENCES psps(psp_id)         ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_payment_attempts_invoice ON payment_attempts(invoice_id);
CREATE INDEX IF NOT EXISTS idx_payment_attempts_psp     ON payment_attempts(psp_id);
CREATE INDEX IF NOT EXISTS idx_payment_attempts_mpesa   ON payment_attempts(mpesa_checkout_request_id) WHERE mpesa_checkout_request_id IS NOT NULL;
