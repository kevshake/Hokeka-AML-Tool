INSERT INTO billing_rates (psp_id, service_type, pricing_model, base_rate, currency,
                           effective_from, is_active, description)
SELECT NULL, sku.service_type, 'PER_REQUEST', sku.rate, 'USD', CURRENT_DATE, TRUE, sku.description
FROM (VALUES
    ('WALLET_SCREENING', CAST(0.0800 AS NUMERIC), 'Virtual-asset wallet screening'),
    ('VASP_SCREENING', CAST(2.5000 AS NUMERIC), 'Virtual asset service provider screening'),
    ('EDD_SCREENING', CAST(15.0000 AS NUMERIC), 'Enhanced due diligence screening'),
    ('TRAVEL_RULE_TRANSFER', CAST(0.1200 AS NUMERIC), 'Travel Rule transfer processing')
) sku(service_type, rate, description)
WHERE NOT EXISTS (
    SELECT 1 FROM billing_rates existing
    WHERE existing.psp_id IS NULL AND existing.service_type = sku.service_type
);
