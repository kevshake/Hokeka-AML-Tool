-- ============================================================
-- V125: Cyber-security incidents, system interruptions,
--       customer complaints, and fraud incidents
--
-- Backs CBK GDI operational reporting:
--   #6  PSP_CYBERSECURITY_INCIDENT_RECORD  (daily)
--   #8  SCH_SY_STABIL_SRVCE_INT           (daily)
--   #5  PSP_CUTOMER_COMPLAINTS             (monthly, day 3)
--   #7  INCIDENTS_DATA (Fraud/Theft/Robbery)(daily)
--
-- alert_id_link  -> alerts(alert_id)   (nullable, traceability)
-- case_id_link   -> compliance_cases(case_id) (nullable, traceability)
-- psp_id is a raw BIGINT NOT NULL (no FK constraint per project rules).
-- ============================================================

-- ---------------------------------------------------------------
-- 1. psp_cyber_incidents  (#6 PSP_CYBERSECURITY_INCIDENT_RECORD)
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS psp_cyber_incidents (
    id                    BIGSERIAL     PRIMARY KEY,
    psp_id                BIGINT        NOT NULL,
    incident_number       VARCHAR(128)  NOT NULL,
    incident_date         TIMESTAMP,
    location_of_attacker  VARCHAR(256),
    incident_mode         VARCHAR(128),
    loss_type             VARCHAR(128),
    details               TEXT,
    action_taken          TEXT,
    resolution_date       TIMESTAMP,
    mitigation_actions    TEXT,
    amount_involved       NUMERIC(18,4),
    amount_lost           NUMERIC(18,4),
    currency              VARCHAR(8),
    created_by            BIGINT,
    created_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cyber_incident_number UNIQUE (incident_number)
);

CREATE INDEX IF NOT EXISTS idx_psp_cyber_incidents_psp_id ON psp_cyber_incidents (psp_id);

COMMENT ON TABLE  psp_cyber_incidents IS 'CBK GDI #6 – Cybersecurity Incident Records (daily)';

-- ---------------------------------------------------------------
-- 2. psp_system_interruptions  (#8 SCH_SY_STABIL_SRVCE_INT)
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS psp_system_interruptions (
    id                               BIGSERIAL     PRIMARY KEY,
    psp_id                           BIGINT        NOT NULL,
    reporting_date                   DATE,
    sub_county_code                  VARCHAR(32),
    system_owner_flag                VARCHAR(16),
    third_party_owned_category       VARCHAR(64),
    third_party_name                 VARCHAR(256),
    product_type                     VARCHAR(64),
    system_unavailability_type_code  VARCHAR(32),
    third_party_system_affected      VARCHAR(256),
    service_interruption_cause_code  VARCHAR(32),
    severity_interruption_code       VARCHAR(32),
    recovery_time_code               VARCHAR(32),
    remedial_status_code             VARCHAR(32),
    system_uptime_percentage         NUMERIC(5,2),
    started_at                       TIMESTAMP,
    resolved_at                      TIMESTAMP,
    created_at                       TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at                       TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_psp_system_interruptions_psp_id ON psp_system_interruptions (psp_id);

COMMENT ON TABLE  psp_system_interruptions IS 'CBK GDI #8 – System Stability / Service Interruptions (daily)';

-- ---------------------------------------------------------------
-- 3. psp_customer_complaints  (#5 PSP_CUTOMER_COMPLAINTS)
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS psp_customer_complaints (
    id                                    BIGSERIAL     PRIMARY KEY,
    psp_id                                BIGINT        NOT NULL,
    complaint_id                          VARCHAR(128)  NOT NULL,
    complaint_code                        VARCHAR(64),
    complainant_gender                    VARCHAR(16),
    complaint_frequency                   INTEGER,
    complainant_name                      VARCHAR(256),
    complainant_age                       INTEGER,
    complainant_contact_number            VARCHAR(64),
    complainant_sub_county_location       VARCHAR(128),
    complainant_education_level           VARCHAR(64),
    others_complainant_details            TEXT,
    agent_id                              VARCHAR(128),
    date_of_occurrence                    DATE,
    date_reported_to_the_institution      DATE,
    date_resolved                         DATE,
    remedial_status                       VARCHAR(64),
    amount_lost                           NUMERIC(18,4),
    amount_recovered                      NUMERIC(18,4),
    created_at                            TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at                            TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_customer_complaint_id UNIQUE (complaint_id)
);

CREATE INDEX IF NOT EXISTS idx_psp_customer_complaints_psp_id ON psp_customer_complaints (psp_id);

COMMENT ON TABLE  psp_customer_complaints IS 'CBK GDI #5 – Customer Complaints & Remedials (monthly, day 3)';

-- ---------------------------------------------------------------
-- 4. psp_fraud_incidents  (#7 INCIDENTS_DATA)
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS psp_fraud_incidents (
    id                   BIGSERIAL     PRIMARY KEY,
    psp_id               BIGINT        NOT NULL,
    reporting_date       DATE,
    sub_county_code      VARCHAR(32),
    sub_fraud_code       VARCHAR(64),
    fraud_category_flag  VARCHAR(16),
    victim_category      VARCHAR(64),
    victim_information   TEXT,
    date_of_occurrence   DATE,
    number_of_incidences INTEGER,
    amount_involved      NUMERIC(18,4),
    amount_lost          NUMERIC(18,4),
    amount_recovered     NUMERIC(18,4),
    action_taken         TEXT,
    recovery_details     TEXT,
    -- Nullable FK links for traceability to alert and case tables
    alert_id_link        BIGINT,
    case_id_link         BIGINT,
    created_at           TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_fraud_incident_alert
        FOREIGN KEY (alert_id_link) REFERENCES alerts (alert_id) ON DELETE SET NULL,
    CONSTRAINT fk_fraud_incident_case
        FOREIGN KEY (case_id_link)  REFERENCES compliance_cases (case_id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_psp_fraud_incidents_psp_id      ON psp_fraud_incidents (psp_id);
CREATE INDEX IF NOT EXISTS idx_psp_fraud_incidents_alert_id    ON psp_fraud_incidents (alert_id_link);
CREATE INDEX IF NOT EXISTS idx_psp_fraud_incidents_case_id     ON psp_fraud_incidents (case_id_link);

COMMENT ON TABLE  psp_fraud_incidents IS 'CBK GDI #7 – Fraud / Theft / Robbery Incidents (daily)';
COMMENT ON COLUMN psp_fraud_incidents.alert_id_link IS 'Nullable link to alerts.alert_id for traceability';
COMMENT ON COLUMN psp_fraud_incidents.case_id_link  IS 'Nullable link to compliance_cases.case_id for traceability';
