-- V170: Add psps.branding_theme column mapped by entity/psp/Psp.java.
--
-- Why: Psp.java:101-102 maps @Column(name="branding_theme", length=50) with a
-- default of 'default', but no migration ever created the column (V6/V7 added
-- logo/colors/fonts/button/nav styling, not branding_theme). Under prod/testenv
-- spring.jpa.hibernate.ddl-auto=validate Hibernate schema validation fails at
-- startup ("missing column [branding_theme] in table [psps]") and the app does
-- not boot. This closes that gap.
--
-- Type mirrors the entity field: String length=50 default "default" => VARCHAR(50).
-- Idempotent (IF NOT EXISTS). Existing rows get the 'default' theme.

ALTER TABLE psps
    ADD COLUMN IF NOT EXISTS branding_theme VARCHAR(50) DEFAULT 'default';
