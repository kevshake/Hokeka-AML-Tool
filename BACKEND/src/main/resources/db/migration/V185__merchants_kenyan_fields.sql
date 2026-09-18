-- V185: Add merchants.kra_pin and merchants.cr12_number columns mapped by
-- entity/merchant/Merchant.java (Phase 29 "Kenyan Specific Fields").
--
-- VERSION PLACEMENT: intentionally gapped above your active feature frontier
-- (you are appending V171..V178 for the virtual-asset / wallet-screening / VASP
--  build-out, and twice took the next number while this orthogonal merchants fix
--  was drafted — V175 then V178). Flyway allows gaps and applies in version order,
--  so V185 is safe and avoids further collision. If your sequence ever reaches
--  V185, renumber this file to the next free version — it has no dependency on
--  order beyond "after V2 created `merchants`".
--
-- Why: Merchant.java maps two real persistent columns —
--   line 46-47:  @Column(name = "kra_pin",     length = 50)  private String kraPin;
--   line 49-50:  @Column(name = "cr12_number", length = 100) private String cr12Number;
-- with getters/setters and builder support (they are NOT @Transient). No prior
-- migration ever created these columns (V2 creates `merchants`; later migrations
-- add other fields, but never kra_pin/cr12_number). Under prod/testenv
-- spring.jpa.hibernate.ddl-auto=validate, Hibernate schema validation fails at
-- startup ("missing column [kra_pin] in table [merchants]") and the app does not
-- boot. This closes that gap — same boot-fatal class as V162/V165/V170.
--
-- Types mirror the entity fields exactly:
--   kraPin     String length=50  => VARCHAR(50)   (Kenya Revenue Authority PIN)
--   cr12Number String length=100 => VARCHAR(100)  (CR12 company ownership cert ref)
-- Merchant is NOT @Audited (no Envers), so no merchants_aud columns are required.
-- Idempotent (IF NOT EXISTS). Existing rows get NULL (both fields are nullable).

ALTER TABLE merchants
    ADD COLUMN IF NOT EXISTS kra_pin VARCHAR(50);

ALTER TABLE merchants
    ADD COLUMN IF NOT EXISTS cr12_number VARCHAR(100);
