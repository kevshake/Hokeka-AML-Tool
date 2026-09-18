ALTER TABLE asset_accounts
    ADD COLUMN product_domain VARCHAR(48);

UPDATE asset_accounts
SET product_domain = CASE account_type
    WHEN 'BANK_ACCOUNT' THEN 'BANKING'
    WHEN 'BROKERAGE' THEN 'SECURITIES_AML'
    WHEN 'MOBILE_MONEY' THEN 'E_MONEY_MOBILE_MONEY'
    WHEN 'PREPAID_VALUE' THEN 'PREPAID_CLOSED_LOOP'
    WHEN 'CRYPTO_WALLET' THEN 'VIRTUAL_ASSET'
    WHEN 'EXCHANGE_ACCOUNT' THEN 'VIRTUAL_ASSET'
    ELSE 'E_MONEY_MOBILE_MONEY'
END
WHERE product_domain IS NULL;

ALTER TABLE asset_accounts
    ALTER COLUMN product_domain SET NOT NULL;

ALTER TABLE multi_asset_transactions
    ADD COLUMN product_domain VARCHAR(48),
    ADD COLUMN source_product_domain VARCHAR(48),
    ADD COLUMN destination_product_domain VARCHAR(48);

UPDATE multi_asset_transactions
SET product_domain = CASE asset_class
    WHEN 'BANKING' THEN 'BANKING'
    WHEN 'SECURITIES' THEN 'SECURITIES_AML'
    WHEN 'E_MONEY' THEN 'E_MONEY_MOBILE_MONEY'
    WHEN 'CRYPTO' THEN 'VIRTUAL_ASSET'
END
WHERE product_domain IS NULL;

UPDATE multi_asset_transactions
SET source_product_domain = COALESCE(source_product_domain, product_domain),
    destination_product_domain = COALESCE(destination_product_domain, product_domain)
WHERE source_product_domain IS NULL OR destination_product_domain IS NULL;

ALTER TABLE multi_asset_transactions
    ALTER COLUMN product_domain SET NOT NULL;

ALTER TABLE multi_asset_risk_signals
    ADD COLUMN signal_type VARCHAR(32),
    ADD COLUMN product_domain VARCHAR(48);

UPDATE multi_asset_risk_signals signal
SET signal_type = CASE
        WHEN signal.signal_code = 'SECURITIES_MATCHED_ORDER' THEN 'MARKET_ABUSE'
        WHEN signal.signal_code LIKE 'CRYPTO_HIGH_RISK_%'
          OR signal.signal_code LIKE 'CRYPTO_ELEVATED_%'
          OR signal.signal_code = 'CRYPTO_CROSS_CHAIN_TRANSFER' THEN 'CRYPTO_EXPOSURE'
        ELSE 'AML'
    END,
    product_domain = transaction.product_domain
FROM multi_asset_transactions transaction
WHERE transaction.id = signal.transaction_id
  AND (signal.signal_type IS NULL OR signal.product_domain IS NULL);

ALTER TABLE multi_asset_risk_signals
    ALTER COLUMN signal_type SET NOT NULL,
    ALTER COLUMN product_domain SET NOT NULL;

CREATE INDEX idx_asset_account_product_domain
    ON asset_accounts(psp_id, product_domain, customer_id);
CREATE INDEX idx_multi_asset_transaction_domain_time
    ON multi_asset_transactions(psp_id, product_domain, executed_at DESC);
CREATE INDEX idx_multi_asset_signal_taxonomy
    ON multi_asset_risk_signals(psp_id, signal_type, product_domain, created_at DESC);

COMMENT ON COLUMN multi_asset_risk_signals.signal_type IS
    'Financial-crime discipline: AML, MARKET_ABUSE, SANCTIONS, FRAUD, CYBER, or CRYPTO_EXPOSURE';
COMMENT ON COLUMN multi_asset_transactions.product_domain IS
    'Legally and operationally distinct product domain within the broad asset class';
