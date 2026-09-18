-- ============================================================
-- Phase 6 deferred tables — country risk, monthly metrics, SAR
-- templates, BIN ranges, regulator submission attempt log.
-- Migration: V130__deferred_phase6_tables.sql
--
-- Earlier code-fixes assumed these tables but the migrations were
-- deferred. They are now required for go-live since the matching
-- services (CustomerRiskProfilingService, ReportingConsumer,
-- SarContentGenerationService, BinLookupService,
-- RegulatorySubmissionService) all reference them in steady state.
-- ============================================================

-- ------------------------------------------------------------
-- (a) country_risk_scores
-- ISO 3166-1 alpha-2 country code is the natural primary key.
-- The risk_score is a 0-100 integer (FATF tiering); the legacy
-- 0.0-1.0 score used by some callers is computed at read time
-- (tier-based fallback) — see CustomerRiskProfilingService.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS country_risk_scores (
    country_code      CHAR(2)      PRIMARY KEY,
    country_name      VARCHAR(255) NOT NULL,
    risk_score        INTEGER      NOT NULL,
    risk_tier         VARCHAR(16)  NOT NULL,
    fatf_listed       BOOLEAN      NOT NULL DEFAULT FALSE,
    fatf_status       VARCHAR(64),
    last_reviewed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    source            VARCHAR(64)  NOT NULL DEFAULT 'FATF',
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_country_risk_tier ON country_risk_scores(risk_tier);
CREATE INDEX IF NOT EXISTS idx_country_risk_fatf ON country_risk_scores(fatf_listed);

COMMENT ON TABLE country_risk_scores IS
    'Country-level AML/CFT risk scoring. risk_score 0-100 (higher = riskier). risk_tier in (LOW,MEDIUM,HIGH,VERY_HIGH).';

-- FATF blacklist (call for action) — 2026 baseline.
INSERT INTO country_risk_scores (country_code, country_name, risk_score, risk_tier, fatf_listed, fatf_status, source) VALUES
    ('IR', 'Iran',                95, 'VERY_HIGH', TRUE, 'BLACKLIST',     'FATF'),
    ('KP', 'North Korea',          98, 'VERY_HIGH', TRUE, 'BLACKLIST',     'FATF'),
    ('MM', 'Myanmar',              92, 'VERY_HIGH', TRUE, 'BLACKLIST',     'FATF')
ON CONFLICT (country_code) DO NOTHING;

-- FATF grey list (jurisdictions under increased monitoring) — 2026 baseline,
-- aligned with the most recent public lists carried into 2026.
INSERT INTO country_risk_scores (country_code, country_name, risk_score, risk_tier, fatf_listed, fatf_status, source) VALUES
    ('DZ', 'Algeria',                          70, 'HIGH', TRUE, 'GREYLIST', 'FATF'),
    ('BG', 'Bulgaria',                         70, 'HIGH', TRUE, 'GREYLIST', 'FATF'),
    ('BF', 'Burkina Faso',                     75, 'HIGH', TRUE, 'GREYLIST', 'FATF'),
    ('CM', 'Cameroon',                         72, 'HIGH', TRUE, 'GREYLIST', 'FATF'),
    ('HR', 'Croatia',                          65, 'HIGH', TRUE, 'GREYLIST', 'FATF'),
    ('CD', 'Democratic Republic of the Congo', 78, 'HIGH', TRUE, 'GREYLIST', 'FATF'),
    ('HT', 'Haiti',                            80, 'HIGH', TRUE, 'GREYLIST', 'FATF'),
    ('KE', 'Kenya',                            68, 'HIGH', TRUE, 'GREYLIST', 'FATF'),
    ('ML', 'Mali',                             78, 'HIGH', TRUE, 'GREYLIST', 'FATF'),
    ('MC', 'Monaco',                           65, 'HIGH', TRUE, 'GREYLIST', 'FATF'),
    ('MZ', 'Mozambique',                       72, 'HIGH', TRUE, 'GREYLIST', 'FATF'),
    ('NA', 'Namibia',                          70, 'HIGH', TRUE, 'GREYLIST', 'FATF'),
    ('NG', 'Nigeria',                          75, 'HIGH', TRUE, 'GREYLIST', 'FATF'),
    ('PH', 'Philippines',                      72, 'HIGH', TRUE, 'GREYLIST', 'FATF'),
    ('SN', 'Senegal',                          70, 'HIGH', TRUE, 'GREYLIST', 'FATF'),
    ('ZA', 'South Africa',                     68, 'HIGH', TRUE, 'GREYLIST', 'FATF'),
    ('SS', 'South Sudan',                      82, 'HIGH', TRUE, 'GREYLIST', 'FATF'),
    ('SY', 'Syria',                            90, 'VERY_HIGH', TRUE, 'GREYLIST', 'FATF'),
    ('TZ', 'Tanzania',                         70, 'HIGH', TRUE, 'GREYLIST', 'FATF'),
    ('TR', 'Türkiye',                          70, 'HIGH', TRUE, 'GREYLIST', 'FATF'),
    ('VE', 'Venezuela',                        80, 'HIGH', TRUE, 'GREYLIST', 'FATF'),
    ('VN', 'Vietnam',                          68, 'HIGH', TRUE, 'GREYLIST', 'FATF'),
    ('YE', 'Yemen',                            85, 'VERY_HIGH', TRUE, 'GREYLIST', 'FATF')
ON CONFLICT (country_code) DO NOTHING;

-- Baseline LOW-risk seeds for major jurisdictions the platform serves.
INSERT INTO country_risk_scores (country_code, country_name, risk_score, risk_tier, fatf_listed, source) VALUES
    ('US', 'United States',           15, 'LOW', FALSE, 'INTERNAL'),
    ('GB', 'United Kingdom',          15, 'LOW', FALSE, 'INTERNAL'),
    ('CA', 'Canada',                  15, 'LOW', FALSE, 'INTERNAL'),
    ('AU', 'Australia',               15, 'LOW', FALSE, 'INTERNAL'),
    ('NZ', 'New Zealand',             15, 'LOW', FALSE, 'INTERNAL'),
    ('JP', 'Japan',                   18, 'LOW', FALSE, 'INTERNAL'),
    ('SG', 'Singapore',               18, 'LOW', FALSE, 'INTERNAL'),
    ('CH', 'Switzerland',             18, 'LOW', FALSE, 'INTERNAL'),
    -- EU member states (pre-grey-list ones marked LOW; Bulgaria/Croatia handled above).
    ('AT', 'Austria',                 18, 'LOW', FALSE, 'INTERNAL'),
    ('BE', 'Belgium',                 18, 'LOW', FALSE, 'INTERNAL'),
    ('CY', 'Cyprus',                  25, 'LOW', FALSE, 'INTERNAL'),
    ('CZ', 'Czech Republic',          22, 'LOW', FALSE, 'INTERNAL'),
    ('DE', 'Germany',                 15, 'LOW', FALSE, 'INTERNAL'),
    ('DK', 'Denmark',                 15, 'LOW', FALSE, 'INTERNAL'),
    ('EE', 'Estonia',                 22, 'LOW', FALSE, 'INTERNAL'),
    ('ES', 'Spain',                   18, 'LOW', FALSE, 'INTERNAL'),
    ('FI', 'Finland',                 15, 'LOW', FALSE, 'INTERNAL'),
    ('FR', 'France',                  18, 'LOW', FALSE, 'INTERNAL'),
    ('GR', 'Greece',                  25, 'LOW', FALSE, 'INTERNAL'),
    ('HU', 'Hungary',                 25, 'LOW', FALSE, 'INTERNAL'),
    ('IE', 'Ireland',                 18, 'LOW', FALSE, 'INTERNAL'),
    ('IT', 'Italy',                   25, 'LOW', FALSE, 'INTERNAL'),
    ('LT', 'Lithuania',               22, 'LOW', FALSE, 'INTERNAL'),
    ('LU', 'Luxembourg',              22, 'LOW', FALSE, 'INTERNAL'),
    ('LV', 'Latvia',                  22, 'LOW', FALSE, 'INTERNAL'),
    ('MT', 'Malta',                   28, 'LOW', FALSE, 'INTERNAL'),
    ('NL', 'Netherlands',             18, 'LOW', FALSE, 'INTERNAL'),
    ('PL', 'Poland',                  22, 'LOW', FALSE, 'INTERNAL'),
    ('PT', 'Portugal',                22, 'LOW', FALSE, 'INTERNAL'),
    ('RO', 'Romania',                 28, 'LOW', FALSE, 'INTERNAL'),
    ('SE', 'Sweden',                  15, 'LOW', FALSE, 'INTERNAL'),
    ('SI', 'Slovenia',                22, 'LOW', FALSE, 'INTERNAL'),
    ('SK', 'Slovakia',                25, 'LOW', FALSE, 'INTERNAL')
ON CONFLICT (country_code) DO NOTHING;

-- ------------------------------------------------------------
-- (b) monthly_report_metrics
-- Race-safe upsert target for ReportingConsumer (Kafka projector).
-- psp_id is NOT NULL here — when a Kafka event lacks pspId we skip
-- the projection (no synthetic 0). FK ensures we never accumulate
-- metrics for a tenant that has been deleted.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS monthly_report_metrics (
    id           BIGSERIAL     PRIMARY KEY,
    year_month   CHAR(7)       NOT NULL,
    psp_id       BIGINT        NOT NULL REFERENCES psps(psp_id),
    metric_name  VARCHAR(64)   NOT NULL,
    metric_value NUMERIC(20,4) NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (year_month, psp_id, metric_name)
);

CREATE INDEX IF NOT EXISTS idx_mrm_psp ON monthly_report_metrics(psp_id, year_month);

COMMENT ON TABLE monthly_report_metrics IS
    'Per-PSP monthly aggregates projected from Kafka case/decision events. Upserted via ON CONFLICT (year_month, psp_id, metric_name).';

-- ------------------------------------------------------------
-- (c) sar_templates
-- Regulator+jurisdiction+version templates for SAR narrative
-- generation. Body uses Mustache-style {{placeholder}} tokens
-- replaced at render time by SarContentGenerationService.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sar_templates (
    id             BIGSERIAL    PRIMARY KEY,
    regulator      VARCHAR(32)  NOT NULL,
    jurisdiction   CHAR(3)      NOT NULL,
    version        VARCHAR(16)  NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    body_template  TEXT         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (regulator, jurisdiction, version)
);

CREATE INDEX IF NOT EXISTS idx_sar_templates_active
    ON sar_templates(regulator, jurisdiction, active);

COMMENT ON TABLE sar_templates IS
    'SAR narrative templates by regulator+jurisdiction+version. Placeholders use {{placeholder}} syntax.';

-- FINCEN / USA seed (FinCEN SAR XML-shaped narrative section).
INSERT INTO sar_templates (regulator, jurisdiction, version, active, body_template) VALUES
('FINCEN', 'USA', '2024.1', TRUE,
$tpl$<SuspiciousActivityReport xmlns="http://www.fincen.gov/base">
  <Subject>
    <SubjectName>{{customer_name}}</SubjectName>
    <SubjectIdentifier type="merchant">{{merchant_id}}</SubjectIdentifier>
    <SubjectAddressCountry>{{customer_country}}</SubjectAddressCountry>
  </Subject>
  <SuspiciousActivity>
    <ActivityStartDate>{{activity_start_date}}</ActivityStartDate>
    <ActivityEndDate>{{transaction_date}}</ActivityEndDate>
    <TotalAmount>{{total_suspicious_amount}}</TotalAmount>
    <Currency>{{currency}}</Currency>
    <ActivityType>{{suspicious_activity_type}}</ActivityType>
  </SuspiciousActivity>
  <Narrative>
    Case Reference: {{case_reference}}
    Filed By: {{filed_by_name}}
    Filing Institution: {{filing_institution}}

    DESCRIPTION:
    {{case_description}}

    INVESTIGATION FINDINGS:
    {{investigation_findings}}

    TRANSACTION SUMMARY:
    Total Transactions: {{transaction_count}}
    Total Amount: {{total_suspicious_amount}} {{currency}}
    Transaction Period: {{activity_start_date}} to {{transaction_date}}
  </Narrative>
</SuspiciousActivityReport>$tpl$
)
ON CONFLICT (regulator, jurisdiction, version) DO NOTHING;

-- FCA / GBR seed (UK NCA SAR JSON-shaped narrative section).
INSERT INTO sar_templates (regulator, jurisdiction, version, active, body_template) VALUES
('FCA', 'GBR', '2024.1', TRUE,
$tpl${
  "reportType": "SAR",
  "regulator": "FCA",
  "filingInstitution": "{{filing_institution}}",
  "caseReference": "{{case_reference}}",
  "subject": {
    "name": "{{customer_name}}",
    "merchantId": "{{merchant_id}}",
    "country": "{{customer_country}}"
  },
  "activity": {
    "type": "{{suspicious_activity_type}}",
    "startDate": "{{activity_start_date}}",
    "endDate": "{{transaction_date}}",
    "transactionCount": "{{transaction_count}}",
    "totalAmount": "{{total_suspicious_amount}}",
    "currency": "{{currency}}"
  },
  "narrative": "Case {{case_reference}} filed by {{filed_by_name}}. {{case_description}} Investigation findings: {{investigation_findings}}",
  "supportingInformation": "{{investigation_findings}}"
}$tpl$
)
ON CONFLICT (regulator, jurisdiction, version) DO NOTHING;

-- CBK / KEN seed (CBK GDI STR XML-shaped narrative section).
INSERT INTO sar_templates (regulator, jurisdiction, version, active, body_template) VALUES
('CBK', 'KEN', '2024.1', TRUE,
$tpl$<SuspiciousTransactionReport xmlns="http://gdi.centralbank.go.ke/str">
  <ReportingInstitution>{{filing_institution}}</ReportingInstitution>
  <ReportReference>{{case_reference}}</ReportReference>
  <Subject>
    <Name>{{customer_name}}</Name>
    <MerchantId>{{merchant_id}}</MerchantId>
    <Country>{{customer_country}}</Country>
  </Subject>
  <Activity>
    <ActivityType>{{suspicious_activity_type}}</ActivityType>
    <ActivityStart>{{activity_start_date}}</ActivityStart>
    <ActivityEnd>{{transaction_date}}</ActivityEnd>
    <TransactionCount>{{transaction_count}}</TransactionCount>
    <TotalAmount currency="{{currency}}">{{total_suspicious_amount}}</TotalAmount>
  </Activity>
  <Narrative>
    Case Reference: {{case_reference}}
    Filed By: {{filed_by_name}}

    DESCRIPTION:
    {{case_description}}

    INVESTIGATION FINDINGS:
    {{investigation_findings}}
  </Narrative>
</SuspiciousTransactionReport>$tpl$
)
ON CONFLICT (regulator, jurisdiction, version) DO NOTHING;

-- ------------------------------------------------------------
-- (d) bin_ranges
-- BIN-prefix → card metadata. Per the no-mock rule, only
-- rule-based brand mappings are seeded (issuer/country left
-- NULL until a real BIN provider feed is ingested).
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bin_ranges (
    bin_prefix      VARCHAR(8)   PRIMARY KEY,
    issuer          VARCHAR(255),
    issuer_country  CHAR(2),
    card_brand      VARCHAR(32),
    card_type       VARCHAR(16),
    card_class      VARCHAR(32),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_bin_country ON bin_ranges(issuer_country);
CREATE INDEX IF NOT EXISTS idx_bin_brand   ON bin_ranges(card_brand);

COMMENT ON TABLE bin_ranges IS
    'BIN prefix → card metadata. issuer/issuer_country are NULL until populated by a real BIN provider feed; card_brand seed is rule-based.';

-- Rule-based brand seed (issuer/country deliberately NULL — no mock issuers).
INSERT INTO bin_ranges (bin_prefix, card_brand) VALUES
    ('4',    'VISA'),
    ('51',   'MASTERCARD'),
    ('52',   'MASTERCARD'),
    ('53',   'MASTERCARD'),
    ('54',   'MASTERCARD'),
    ('55',   'MASTERCARD'),
    ('2221', 'MASTERCARD'),
    ('2720', 'MASTERCARD'),
    ('34',   'AMEX'),
    ('37',   'AMEX'),
    ('6011', 'DISCOVER'),
    ('65',   'DISCOVER'),
    ('644',  'DISCOVER'),
    ('645',  'DISCOVER'),
    ('646',  'DISCOVER'),
    ('647',  'DISCOVER'),
    ('648',  'DISCOVER'),
    ('649',  'DISCOVER'),
    ('35',   'JCB'),
    ('30',   'DINERS'),
    ('36',   'DINERS'),
    ('38',   'DINERS')
ON CONFLICT (bin_prefix) DO NOTHING;

-- ------------------------------------------------------------
-- (e) regulatory_submission_attempts
-- Append-only attempt log for RegulatorySubmissionService.
-- Used for idempotency (idempotency_key) and audit (request/response
-- bodies, http_status, attempt_no). Retained even if the parent
-- regulatory_submissions row is deleted (CASCADE).
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS regulatory_submission_attempts (
    id              BIGSERIAL     PRIMARY KEY,
    submission_id   BIGINT        NOT NULL REFERENCES regulatory_submissions(id) ON DELETE CASCADE,
    regulator       VARCHAR(16)   NOT NULL,
    idempotency_key VARCHAR(64)   NOT NULL,
    request_body    TEXT,
    response_body   TEXT,
    http_status     INTEGER,
    submitted_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    attempt_no      INTEGER       NOT NULL,
    UNIQUE (submission_id, regulator, attempt_no)
);

CREATE INDEX IF NOT EXISTS idx_rsa_submission ON regulatory_submission_attempts(submission_id);
CREATE INDEX IF NOT EXISTS idx_rsa_idem       ON regulatory_submission_attempts(idempotency_key);

COMMENT ON TABLE regulatory_submission_attempts IS
    'Per-regulator submission attempt log (idempotency + audit). request_body truncated > 64KB with __TRUNCATED__ marker.';

-- ------------------------------------------------------------
-- (f) Widen psps columns that are now AES-256-GCM encrypted via
-- AesGcmStringConverter. base64(IV || GCM(ciphertext+tag)) is
-- ~4/3 of plaintext + 28 bytes overhead, so we round up.
-- ------------------------------------------------------------
ALTER TABLE psps ALTER COLUMN tax_id              TYPE VARCHAR(512);
ALTER TABLE psps ALTER COLUMN registration_number TYPE VARCHAR(512);
ALTER TABLE psps ALTER COLUMN cbk_client_id       TYPE VARCHAR(512);
ALTER TABLE psps ALTER COLUMN cbk_client_secret   TYPE VARCHAR(1024);

