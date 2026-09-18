ALTER TABLE travel_rule_transfers
    ADD COLUMN IF NOT EXISTS originator_verification_reference VARCHAR(500),
    ADD COLUMN IF NOT EXISTS originator_verified_by VARCHAR(160),
    ADD COLUMN IF NOT EXISTS originator_verified_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS beneficiary_verification_reference VARCHAR(500),
    ADD COLUMN IF NOT EXISTS beneficiary_verified_by VARCHAR(160),
    ADD COLUMN IF NOT EXISTS beneficiary_verified_at TIMESTAMP;

COMMENT ON COLUMN multi_asset_transactions.originator_name IS
    'Legacy field retained for schema compatibility; new writes use encrypted travel_rule_transfers.encrypted_payload';
COMMENT ON COLUMN multi_asset_transactions.originator_account IS
    'Legacy field retained for schema compatibility; new writes use encrypted travel_rule_transfers.encrypted_payload';
COMMENT ON COLUMN multi_asset_transactions.beneficiary_name IS
    'Legacy field retained for schema compatibility; new writes use encrypted travel_rule_transfers.encrypted_payload';
COMMENT ON COLUMN multi_asset_transactions.beneficiary_account IS
    'Legacy field retained for schema compatibility; new writes use encrypted travel_rule_transfers.encrypted_payload';

CREATE OR REPLACE FUNCTION seed_ke_virtual_asset_policy_for_psp()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO travel_rule_jurisdiction_policies (
        psp_id, jurisdiction, policy_code, threshold_usd, applies_to_all_transfers,
        required_fields, verify_originator, verify_beneficiary, accepted_protocols,
        retention_years, effective_from, legal_reference
    ) VALUES (
        NEW.psp_id, 'KE', 'KE_VASP_2025_BASELINE', 0, TRUE,
        '["originatorName","originatorAccount","originatorAddress","originatorCountry","beneficiaryName","beneficiaryAccount","beneficiaryVasp"]'::jsonb,
        TRUE, TRUE, '["TRISA","OPENVASP","SYGNA","NOTABENE","CUSTOM_API"]'::jsonb,
        7, TIMESTAMP '2025-11-04 00:00:00',
        'Virtual Asset Service Providers Act, 2025; POCAMLA and applicable regulations'
    ) ON CONFLICT (psp_id, policy_code) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_seed_ke_virtual_asset_policy ON psps;
CREATE TRIGGER trg_seed_ke_virtual_asset_policy
    AFTER INSERT ON psps
    FOR EACH ROW EXECUTE FUNCTION seed_ke_virtual_asset_policy_for_psp();
