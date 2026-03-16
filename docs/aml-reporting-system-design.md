# AML Reporting System - Database Design & API Planning

## Overview
Comprehensive AML reporting system for the fraud-detector application covering 85+ reports across 13 categories with filtering, export capabilities, and regulatory submission templates.

---

## 1. Database Schema Design

### 1.1 Core Report Tables

#### reports (Master Report Registry)
```sql
CREATE TABLE reports (
    id BIGSERIAL PRIMARY KEY,
    report_code VARCHAR(50) NOT NULL UNIQUE,       -- e.g., 'SAR_SUMMARY', 'CTR_DAILY'
    report_name VARCHAR(255) NOT NULL,
    report_category VARCHAR(50) NOT NULL,           -- AML_FRAUD, CURRENCY_THRESHOLD, etc.
    description TEXT,
    report_type VARCHAR(20) NOT NULL DEFAULT 'DYNAMIC', -- STATIC, DYNAMIC, REGULATORY
    base_entity VARCHAR(100),                       -- transactions, alerts, cases, etc.
    requires_approval BOOLEAN DEFAULT FALSE,
    regulatory_template VARCHAR(50),                -- FinCEN, FCA, etc.
    retention_days INTEGER DEFAULT 2555,            -- 7 years default
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_reports_category ON reports(report_category);
CREATE INDEX idx_reports_type ON reports(report_type);
CREATE INDEX idx_reports_enabled ON reports(enabled);
```

#### report_definitions (Report Query Definitions)
```sql
CREATE TABLE report_definitions (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES reports(id),
    version INTEGER NOT NULL DEFAULT 1,
    sql_query TEXT NOT NULL,                        -- Base SQL query
    count_query TEXT,                               -- Optimized count query for pagination
    parameters JSONB,                               -- Query parameters definition
    filters JSONB,                                  -- Available filters
    columns JSONB NOT NULL,                         -- Column definitions
    aggregations JSONB,                             -- Aggregation definitions
    group_by_fields JSONB,                          -- Group by fields
    order_by_default VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    created_by BIGINT REFERENCES platform_users(id),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(report_id, version)
);

CREATE INDEX idx_report_defs_report ON report_definitions(report_id);
CREATE INDEX idx_report_defs_active ON report_definitions(is_active);
```

### 1.2 Report Execution & Storage

#### report_executions (Execution Tracking)
```sql
CREATE TABLE report_executions (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES reports(id),
    execution_id VARCHAR(100) UNIQUE NOT NULL,      -- UUID for tracking
    psp_id BIGINT,                                  -- NULL = platform-wide
    triggered_by BIGINT REFERENCES platform_users(id),
    trigger_type VARCHAR(20) NOT NULL,              -- MANUAL, SCHEDULED, API
    
    -- Parameters
    parameters JSONB,                               -- Execution parameters
    date_from TIMESTAMP,
    date_to TIMESTAMP,
    filters_applied JSONB,                          -- Applied filters
    
    -- Status Tracking
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    progress_percent INTEGER DEFAULT 0,
    
    -- Results
    total_records BIGINT,
    file_path VARCHAR(500),                         -- Stored file location
    file_formats JSONB,                             -- ['PDF', 'CSV', 'EXCEL']
    file_sizes JSONB,                               -- Size per format
    
    -- Performance
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    execution_time_ms INTEGER,                      -- Duration in milliseconds
    
    -- Error Handling
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_report_exec_report ON report_executions(report_id);
CREATE INDEX idx_report_exec_psp ON report_executions(psp_id);
CREATE INDEX idx_report_exec_status ON report_executions(status);
CREATE INDEX idx_report_exec_dates ON report_executions(date_from, date_to);
CREATE INDEX idx_report_exec_created ON report_executions(created_at);
```

#### report_results (Cached Report Data)
```sql
CREATE TABLE report_results (
    id BIGSERIAL PRIMARY KEY,
    execution_id BIGINT NOT NULL REFERENCES report_executions(id),
    row_number INTEGER NOT NULL,
    data JSONB NOT NULL,                            -- Row data as JSON
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_report_results_exec ON report_results(execution_id);
CREATE INDEX idx_report_results_row ON report_results(execution_id, row_number);
```

### 1.3 Report Scheduling

#### report_schedules
```sql
CREATE TABLE report_schedules (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES reports(id),
    psp_id BIGINT,                                  -- NULL = platform-wide
    
    -- Schedule Configuration
    schedule_name VARCHAR(255) NOT NULL,
    frequency VARCHAR(20) NOT NULL,                 -- DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY
    cron_expression VARCHAR(100),                   -- Optional custom cron
    
    -- Timing
    time_of_day TIME,                               -- For daily/weekly
    day_of_week INTEGER,                            -- 1-7 for weekly
    day_of_month INTEGER,                           -- 1-31 for monthly
    timezone VARCHAR(50) DEFAULT 'UTC',
    
    -- Parameters
    default_parameters JSONB,
    default_filters JSONB,
    date_range_type VARCHAR(20),                    -- PREVIOUS_DAY, PREVIOUS_WEEK, PREVIOUS_MONTH, CUSTOM
    
    -- Delivery
    email_recipients JSONB,                         -- Array of email addresses
    email_subject VARCHAR(255),
    email_body TEXT,
    
    -- Export Formats
    export_formats JSONB DEFAULT '["PDF", "CSV"]',  -- PDF, CSV, EXCEL
    
    -- Status
    is_active BOOLEAN DEFAULT TRUE,
    last_run_at TIMESTAMP,
    next_run_at TIMESTAMP,
    run_count INTEGER DEFAULT 0,
    fail_count INTEGER DEFAULT 0,
    
    -- Audit
    created_by BIGINT REFERENCES platform_users(id),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_report_sched_report ON report_schedules(report_id);
CREATE INDEX idx_report_sched_psp ON report_schedules(psp_id);
CREATE INDEX idx_report_sched_active ON report_schedules(is_active);
CREATE INDEX idx_report_sched_next ON report_schedules(next_run_at);
```

#### report_schedule_history
```sql
CREATE TABLE report_schedule_history (
    id BIGSERIAL PRIMARY KEY,
    schedule_id BIGINT NOT NULL REFERENCES report_schedules(id),
    execution_id BIGINT REFERENCES report_executions(id),
    scheduled_for TIMESTAMP NOT NULL,
    executed_at TIMESTAMP,
    status VARCHAR(20) NOT NULL,                    -- SUCCESS, FAILED, SKIPPED
    error_message TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_report_sched_hist_schedule ON report_schedule_history(schedule_id);
CREATE INDEX idx_report_sched_hist_status ON report_schedule_history(status);
```

### 1.4 Regulatory Submission Tracking

#### regulatory_submissions
```sql
CREATE TABLE regulatory_submissions (
    id BIGSERIAL PRIMARY KEY,
    submission_reference VARCHAR(100) UNIQUE NOT NULL, -- e.g., SUB-2024-001
    
    -- Report Reference
    report_id BIGINT NOT NULL REFERENCES reports(id),
    execution_id BIGINT REFERENCES report_executions(id),
    
    -- Regulatory Details
    regulator_code VARCHAR(50) NOT NULL,            -- FinCEN, FCA, CBK, etc.
    submission_type VARCHAR(50) NOT NULL,           -- SAR, CTR, STR, etc.
    jurisdiction VARCHAR(50) NOT NULL,              -- US, UK, KE, etc.
    
    -- Filing Information
    filing_period_start DATE NOT NULL,
    filing_period_end DATE NOT NULL,
    filing_deadline DATE,
    
    -- Submission Status
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',    -- DRAFT, PENDING_REVIEW, APPROVED, FILED, REJECTED, AMENDED
    
    -- Approval Workflow
    prepared_by BIGINT REFERENCES platform_users(id),
    prepared_at TIMESTAMP,
    reviewed_by BIGINT REFERENCES platform_users(id),
    reviewed_at TIMESTAMP,
    approved_by BIGINT REFERENCES platform_users(id),
    approved_at TIMESTAMP,
    
    -- Filing
    filed_by BIGINT REFERENCES platform_users(id),
    filed_at TIMESTAMP,
    regulator_reference VARCHAR(100),               -- Reference from regulator
    filing_receipt TEXT,                            -- Receipt/confirmation
    
    -- Rejection/Amendment
    rejection_reason TEXT,
    amended_submission_id BIGINT REFERENCES regulatory_submissions(id),
    amendment_reason TEXT,
    
    -- Data
    submitted_data JSONB,                           -- Summary of submitted data
    attachment_paths JSONB,                         -- File paths
    
    -- PSP
    psp_id BIGINT,
    
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_reg_sub_psp ON regulatory_submissions(psp_id);
CREATE INDEX idx_reg_sub_status ON regulatory_submissions(status);
CREATE INDEX idx_reg_sub_regulator ON regulatory_submissions(regulator_code);
CREATE INDEX idx_reg_sub_type ON regulatory_submissions(submission_type);
CREATE INDEX idx_reg_sub_period ON regulatory_submissions(filing_period_start, filing_period_end);
CREATE INDEX idx_reg_sub_deadline ON regulatory_submissions(filing_deadline);
```

#### regulatory_templates
```sql
CREATE TABLE regulatory_templates (
    id BIGSERIAL PRIMARY KEY,
    template_code VARCHAR(50) UNIQUE NOT NULL,      -- FINCEN_SAR, FINCEN_CTR, FCA_SAR
    template_name VARCHAR(255) NOT NULL,
    regulator_code VARCHAR(50) NOT NULL,
    jurisdiction VARCHAR(50) NOT NULL,
    submission_type VARCHAR(50) NOT NULL,
    
    -- Template Structure
    schema_definition JSONB NOT NULL,               -- Field definitions
    validation_rules JSONB,                         -- Validation rules
    required_fields JSONB,                          -- List of required fields
    
    -- File Generation
    xml_template TEXT,                              -- XML template if applicable
    csv_mapping JSONB,                              -- CSV column mapping
    pdf_template_path VARCHAR(500),
    
    -- Versioning
    version INTEGER DEFAULT 1,
    effective_date DATE,
    expiry_date DATE,
    is_active BOOLEAN DEFAULT TRUE,
    
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
```

### 1.5 Report User Preferences & Favorites

#### report_favorites
```sql
CREATE TABLE report_favorites (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES platform_users(id),
    report_id BIGINT NOT NULL REFERENCES reports(id),
    psp_id BIGINT,
    display_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, report_id, psp_id)
);
```

#### report_saved_filters
```sql
CREATE TABLE report_saved_filters (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES platform_users(id),
    report_id BIGINT NOT NULL REFERENCES reports(id),
    psp_id BIGINT,
    filter_name VARCHAR(255) NOT NULL,
    filter_criteria JSONB NOT NULL,
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
```

### 1.6 Data Quality Tracking

#### data_quality_issues
```sql
CREATE TABLE data_quality_issues (
    id BIGSERIAL PRIMARY KEY,
    issue_type VARCHAR(50) NOT NULL,                -- MISSING_CUSTOMER_DATA, INVALID_ID, etc.
    entity_type VARCHAR(50) NOT NULL,               -- TRANSACTION, CUSTOMER, MERCHANT
    entity_id VARCHAR(100) NOT NULL,
    psp_id BIGINT,
    
    -- Issue Details
    field_name VARCHAR(100),
    expected_format VARCHAR(255),
    actual_value TEXT,
    severity VARCHAR(20) DEFAULT 'WARNING',         -- INFO, WARNING, ERROR, CRITICAL
    
    -- Status
    status VARCHAR(20) DEFAULT 'OPEN',              -- OPEN, RESOLVED, IGNORED
    resolved_by BIGINT REFERENCES platform_users(id),
    resolved_at TIMESTAMP,
    resolution_notes TEXT,
    
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_dq_issues_type ON data_quality_issues(issue_type);
CREATE INDEX idx_dq_issues_entity ON data_quality_issues(entity_type, entity_id);
CREATE INDEX idx_dq_issues_psp ON data_quality_issues(psp_id);
CREATE INDEX idx_dq_issues_status ON data_quality_issues(status);
CREATE INDEX idx_dq_issues_severity ON data_quality_issues(severity);
CREATE INDEX idx_dq_issues_created ON data_quality_issues(created_at);
```

---

## 2. Report Categories & Coverage

### 2.1 AML & Fraud Reports (8)
| Code | Name | Description |
|------|------|-------------|
| SAR_001 | Suspicious Activity Report Summary | Summary of all SARs filed |
| SAR_002 | Suspicious Transaction Report | Detailed suspicious transactions |
| SAR_003 | Suspicious Matter Report | Matters requiring investigation |
| SAR_004 | Attempted Transaction Report | Suspicious attempted transactions |
| SAR_005 | Internal Escalation Report | Cases escalated internally |
| SAR_006 | SAR History Report | Historical SAR filing trends |
| SAR_007 | SAR Aging Report | Aging analysis of pending SARs |
| SAR_008 | SAR Pending Report | SARs pending approval/filing |

### 2.2 Currency & Threshold Reports (6)
| Code | Name | Description |
|------|------|-------------|
| CTR_001 | Currency Transaction Report | CTR filings and tracking |
| CTR_002 | Large Cash Transactions | Large cash deposits/withdrawals |
| CTR_003 | Cash Deposit Monitoring | Cash deposit trends |
| CTR_004 | Cash Withdrawal Monitoring | Cash withdrawal trends |
| CTR_005 | Structuring Detection Report | Potential structuring patterns |
| CTR_006 | Threshold Breach Report | Threshold limit breaches |

### 2.3 Transaction Monitoring Reports (12)
| Code | Name | Description |
|------|------|-------------|
| TXN_001 | Daily Volume Report | Daily transaction volumes |
| TXN_002 | Weekly Volume Report | Weekly transaction volumes |
| TXN_003 | Monthly Volume Report | Monthly transaction volumes |
| TXN_004 | High Value Transactions | High-value transaction analysis |
| TXN_005 | Unusual Pattern Report | Unusual transaction patterns |
| TXN_006 | High Frequency Report | High-frequency transaction analysis |
| TXN_007 | Rapid Movement Report | Rapid fund movement detection |
| TXN_008 | Burst Activity Report | Burst/sudden activity analysis |
| TXN_009 | High Risk Countries Report | Transactions to/from high-risk countries |
| TXN_010 | Sanctioned Jurisdictions Report | Sanctioned jurisdiction activity |
| TXN_011 | Cross Border Flow Report | Cross-border transaction flow |
| TXN_012 | Risk Corridor Report | Risk corridor analysis |

### 2.4 Channel Monitoring Reports (6)
| Code | Name | Description |
|------|------|-------------|
| CHN_001 | ATM Activity Report | ATM transaction monitoring |
| CHN_002 | POS Activity Report | POS transaction monitoring |
| CHN_003 | Online Banking Report | Online banking activity |
| CHN_004 | Mobile Money Report | Mobile money transactions |
| CHN_005 | Agent Banking Report | Agent banking activity |
| CHN_006 | Card Not Present Report | CNP fraud analysis |

### 2.5 Sanctions Screening Reports (4)
| Code | Name | Description |
|------|------|-------------|
| SNC_001 | Screening Hits Report | Sanctions screening hits |
| SNC_002 | True Match Report | Confirmed sanctions matches |
| SNC_003 | False Positive Report | False positive analysis |
| SNC_004 | Cross Border Cash Report | Cross-border cash movements |

### 2.6 Fraud Incident Reports (8)
| Code | Name | Description |
|------|------|-------------|
| FRD_001 | Confirmed Fraud Cases | Confirmed fraud incidents |
| FRD_002 | Suspected Fraud Cases | Suspected fraud incidents |
| FRD_003 | Fraud Loss Report | Actual fraud losses |
| FRD_004 | Attempted Fraud Report | Attempted fraud losses |
| FRD_005 | Card Present Fraud Report | Card-present fraud analysis |
| FRD_006 | Card Not Present Fraud Report | CNP fraud analysis |
| FRD_007 | ATM Fraud Report | ATM fraud incidents |
| FRD_008 | E-commerce Fraud Report | E-commerce fraud analysis |

### 2.7 Alert & Case Management Reports (10)
| Code | Name | Description |
|------|------|-------------|
| ALC_001 | Total Alerts Report | Alert volume summary |
| ALC_002 | Alerts By Rule Report | Alerts grouped by rule |
| ALC_003 | Alerts By Risk Level Report | Alerts by risk classification |
| ALC_004 | False Positive Rate Report | False positive analysis |
| ALC_005 | True Positive Rate Report | True positive analysis |
| ALC_006 | Alert Aging Report | Alert age distribution |
| ALC_007 | Resolution Time Report | Alert resolution metrics |
| ALC_008 | Cases Opened/Closed Report | Case volume trends |
| ALC_009 | Investigator Workload Report | Workload distribution |
| ALC_010 | Alert Disposition Report | Alert disposition analysis |

### 2.8 Rule Engine Reports (6)
| Code | Name | Description |
|------|------|-------------|
| RUL_001 | Rule Trigger Frequency | Rule triggering statistics |
| RUL_002 | Top Effective Rules | Most effective rules |
| RUL_003 | Least Effective Rules | Least effective rules |
| RUL_004 | Rule Precision Metrics | Precision by rule |
| RUL_005 | Rule Recall Metrics | Recall by rule |
| RUL_006 | Rule Coverage Report | Rule coverage analysis |

### 2.9 Risk Scoring & Models Reports (8)
| Code | Name | Description |
|------|------|-------------|
| RSK_001 | Score Distribution Report | Risk score distributions |
| RSK_002 | Score Change History | Score change tracking |
| RSK_003 | Risk Trend Report | Risk trend analysis |
| RSK_004 | High Risk Alerts Report | High-risk customer alerts |
| RSK_005 | Model Accuracy Report | Model accuracy metrics |
| RSK_006 | Model Drift Report | Model drift detection |
| RSK_007 | Feature Importance Report | ML feature importance |
| RSK_008 | Retraining History Report | Model retraining log |

### 2.10 Regulatory Submission Reports (4)
| Code | Name | Description |
|------|------|-------------|
| REG_001 | Filing Log Report | All regulatory filings |
| REG_002 | Late Filing Report | Late filing tracking |
| REG_003 | Rejection Report | Submission rejections |
| REG_004 | Amendment History Report | Filing amendments |

### 2.11 Compliance Management Reports (4)
| Code | Name | Description |
|------|------|-------------|
| CMP_001 | Compliance Breach Report | Compliance breaches |
| CMP_002 | Audit Trail Report | Detailed audit trail |
| CMP_003 | Internal Violations Report | Internal policy violations |
| CMP_004 | AML Violations Report | AML regulation violations |

### 2.12 Data Quality Reports (5)
| Code | Name | Description |
|------|------|-------------|
| DQL_001 | Missing Customer Data Report | Missing customer data analysis |
| DQL_002 | Incomplete Transactions Report | Incomplete transaction data |
| DQL_003 | Invalid ID Report | Invalid identification documents |
| DQL_004 | Duplicate Records Report | Duplicate detection |
| DQL_005 | Data Inconsistencies Report | Data inconsistency analysis |

### 2.13 Chargeback & Dispute Reports (4)
| Code | Name | Description |
|------|------|-------------|
| CHB_001 | Chargeback Monitoring Report | Chargeback trends |
| CHB_002 | Friendly Fraud Report | Friendly fraud analysis |
| CHB_003 | Merchant Dispute Trends Report | Merchant dispute analysis |
| CHB_004 | Chargeback Ratios Report | Chargeback ratio metrics |

---

## 3. API Design

### 3.1 Base Endpoints

```
/api/v1/reports                    # Report management
/api/v1/reports/execute            # Report execution
/api/v1/reports/schedule           # Report scheduling
/api/v1/reports/regulatory         # Regulatory submissions
/api/v1/reports/data-quality       # Data quality reports
```

### 3.2 Report Management APIs

#### Get All Reports
```http
GET /api/v1/reports
```

**Query Parameters:**
```json
{
  "category": "AML_FRAUD",
  "type": "REGULATORY",
  "search": "SAR",
  "enabled": true,
  "page": 0,
  "size": 20
}
```

**Response:**
```json
{
  "content": [
    {
      "id": 1,
      "reportCode": "SAR_001",
      "reportName": "Suspicious Activity Report Summary",
      "reportCategory": "AML_FRAUD",
      "description": "Summary of all SARs filed",
      "reportType": "REGULATORY",
      "baseEntity": "suspicious_activity_reports",
      "requiresApproval": true,
      "regulatoryTemplate": "FinCEN_SAR",
      "enabled": true,
      "favorited": true
    }
  ],
  "totalElements": 85,
  "totalPages": 5,
  "size": 20,
  "number": 0
}
```

#### Get Report Details
```http
GET /api/v1/reports/{reportId}
```

**Response:**
```json
{
  "id": 1,
  "reportCode": "SAR_001",
  "reportName": "Suspicious Activity Report Summary",
  "reportCategory": "AML_FRAUD",
  "description": "Summary of all SARs filed",
  "reportType": "REGULATORY",
  "baseEntity": "suspicious_activity_reports",
  "requiresApproval": true,
  "regulatoryTemplate": "FinCEN_SAR",
  "definition": {
    "version": 1,
    "parameters": [...],
    "filters": [...],
    "columns": [...]
  },
  "enabled": true,
  "createdAt": "2024-01-01T00:00:00Z"
}
```

#### Get Report Categories
```http
GET /api/v1/reports/categories
```

**Response:**
```json
{
  "categories": [
    {
      "code": "AML_FRAUD",
      "name": "AML & Fraud",
      "reportCount": 8,
      "icon": "shield"
    },
    {
      "code": "CURRENCY_THRESHOLD",
      "name": "Currency & Threshold",
      "reportCount": 6,
      "icon": "dollar"
    }
  ]
}
```

### 3.3 Report Execution APIs

#### Execute Report (Async)
```http
POST /api/v1/reports/{reportId}/execute
```

**Request Body:**
```json
{
  "parameters": {
    "dateFrom": "2024-01-01T00:00:00Z",
    "dateTo": "2024-01-31T23:59:59Z",
    "pspId": 123,
    "customerId": null,
    "transactionType": null
  },
  "filters": {
    "status": ["DRAFT", "PENDING"],
    "priority": ["HIGH", "CRITICAL"],
    "amountMin": 10000,
    "amountMax": null
  },
  "options": {
    "exportFormats": ["PDF", "CSV", "EXCEL"],
    "page": 0,
    "size": 100,
    "sortBy": "createdAt",
    "sortDirection": "DESC"
  }
}
```

**Response:**
```json
{
  "executionId": "exec-uuid-123",
  "reportId": 1,
  "status": "PENDING",
  "message": "Report execution queued",
  "estimatedCompletion": "2024-03-13T10:05:00Z",
  "checkStatusUrl": "/api/v1/reports/execute/exec-uuid-123/status"
}
```

#### Get Execution Status
```http
GET /api/v1/reports/execute/{executionId}/status
```

**Response:**
```json
{
  "executionId": "exec-uuid-123",
  "reportId": 1,
  "status": "RUNNING",
  "progressPercent": 45,
  "totalRecords": 1500,
  "processedRecords": 675,
  "startedAt": "2024-03-13T10:00:00Z",
  "estimatedCompletion": "2024-03-13T10:05:00Z"
}
```

#### Get Report Results
```http
GET /api/v1/reports/execute/{executionId}/results
```

**Query Parameters:**
```json
{
  "page": 0,
  "size": 100,
  "sortBy": "createdAt",
  "sortDirection": "DESC"
}
```

**Response:**
```json
{
  "executionId": "exec-uuid-123",
  "reportId": 1,
  "reportName": "Suspicious Activity Report Summary",
  "parameters": { ... },
  "summary": {
    "totalRecords": 1500,
    "generatedAt": "2024-03-13T10:05:00Z",
    "executionTimeMs": 12000
  },
  "columns": [
    { "name": "sarReference", "type": "STRING", "label": "SAR Reference" },
    { "name": "status", "type": "STRING", "label": "Status" },
    { "name": "totalAmount", "type": "CURRENCY", "label": "Total Amount" }
  ],
  "data": [
    { "sarReference": "SAR-2024-001", "status": "FILED", "totalAmount": 50000.00 },
    { "sarReference": "SAR-2024-002", "status": "PENDING", "totalAmount": 125000.00 }
  ],
  "pagination": {
    "page": 0,
    "size": 100,
    "totalElements": 1500,
    "totalPages": 15
  }
}
```

#### Cancel Execution
```http
POST /api/v1/reports/execute/{executionId}/cancel
```

### 3.4 Export APIs

#### Export Report
```http
POST /api/v1/reports/execute/{executionId}/export
```

**Request Body:**
```json
{
  "format": "PDF",
  "template": "standard",
  "options": {
    "includeCoverPage": true,
    "includeSummary": true,
    "pageOrientation": "LANDSCAPE",
    "paperSize": "A4"
  }
}
```

**Response:**
```json
{
  "exportId": "exp-uuid-456",
  "executionId": "exec-uuid-123",
  "format": "PDF",
  "status": "GENERATING",
  "downloadUrl": "/api/v1/reports/export/exp-uuid-456/download",
  "expiresAt": "2024-03-14T10:00:00Z"
}
```

#### Download Export
```http
GET /api/v1/reports/export/{exportId}/download
```

**Response:** Binary file (PDF, CSV, or Excel)

#### Get Available Export Formats
```http
GET /api/v1/reports/{reportId}/export-formats
```

**Response:**
```json
{
  "formats": [
    { "code": "PDF", "name": "PDF Document", "enabled": true },
    { "code": "CSV", "name": "CSV File", "enabled": true },
    { "code": "EXCEL", "name": "Excel Workbook", "enabled": true },
    { "code": "XML", "name": "XML (Regulatory)", "enabled": true, "regulatoryOnly": true }
  ]
}
```

### 3.5 Report Scheduling APIs

#### Create Schedule
```http
POST /api/v1/reports/schedule
```

**Request Body:**
```json
{
  "reportId": 1,
  "scheduleName": "Monthly SAR Summary",
  "frequency": "MONTHLY",
  "dayOfMonth": 1,
  "timeOfDay": "08:00:00",
  "timezone": "UTC",
  "defaultParameters": {
    "dateRangeType": "PREVIOUS_MONTH"
  },
  "emailRecipients": ["compliance@bank.com", "aml@bank.com"],
  "emailSubject": "Monthly SAR Summary Report",
  "emailBody": "Please find attached the monthly SAR summary report.",
  "exportFormats": ["PDF", "CSV"],
  "isActive": true
}
```

#### Get Schedules
```http
GET /api/v1/reports/schedule
```

**Query Parameters:**
```json
{
  "reportId": 1,
  "isActive": true,
  "page": 0,
  "size": 20
}
```

#### Update Schedule
```http
PUT /api/v1/reports/schedule/{scheduleId}
```

#### Delete Schedule
```http
DELETE /api/v1/reports/schedule/{scheduleId}
```

#### Run Schedule Now
```http
POST /api/v1/reports/schedule/{scheduleId}/run-now
```

#### Get Schedule History
```http
GET /api/v1/reports/schedule/{scheduleId}/history
```

### 3.6 Regulatory Submission APIs

#### Create Submission
```http
POST /api/v1/reports/regulatory/submissions
```

**Request Body:**
```json
{
  "reportId": 1,
  "executionId": "exec-uuid-123",
  "regulatorCode": "FinCEN",
  "submissionType": "SAR",
  "jurisdiction": "US",
  "filingPeriodStart": "2024-01-01",
  "filingPeriodEnd": "2024-01-31",
  "filingDeadline": "2024-02-15",
  "pspId": 123
}
```

#### Get Submissions
```http
GET /api/v1/reports/regulatory/submissions
```

**Query Parameters:**
```json
{
  "status": "PENDING_REVIEW",
  "regulatorCode": "FinCEN",
  "filingDeadlineFrom": "2024-01-01",
  "filingDeadlineTo": "2024-03-31",
  "page": 0,
  "size": 20
}
```

#### Get Submission Details
```http
GET /api/v1/reports/regulatory/submissions/{submissionId}
```

#### Update Submission Status
```http
PUT /api/v1/reports/regulatory/submissions/{submissionId}/status
```

**Request Body:**
```json
{
  "status": "APPROVED",
  "notes": "Approved by MLRO",
  "approvedBy": 456
}
```

#### Generate Regulatory File
```http
POST /api/v1/reports/regulatory/submissions/{submissionId}/generate
```

**Request Body:**
```json
{
  "format": "XML",
  "template": "FinCEN_SAR_v1.2"
}
```

#### Get Regulatory Templates
```http
GET /api/v1/reports/regulatory/templates
```

**Query Parameters:**
```json
{
  "regulatorCode": "FinCEN",
  "jurisdiction": "US",
  "isActive": true
}
```

#### Get Late Filings
```http
GET /api/v1/reports/regulatory/late-filings
```

**Response:**
```json
{
  "lateFilings": [
    {
      "submissionId": 123,
      "submissionReference": "SUB-2024-001",
      "regulatorCode": "FinCEN",
      "submissionType": "SAR",
      "filingDeadline": "2024-02-15",
      "daysOverdue": 5,
      "status": "DRAFT"
    }
  ],
  "totalOverdue": 3
}
```

### 3.7 Data Quality APIs

#### Get Data Quality Issues
```http
GET /api/v1/reports/data-quality/issues
```

**Query Parameters:**
```json
{
  "issueType": "MISSING_CUSTOMER_DATA",
  "severity": "ERROR",
  "status": "OPEN",
  "pspId": 123,
  "page": 0,
  "size": 50
}
```

#### Resolve Issue
```http
POST /api/v1/reports/data-quality/issues/{issueId}/resolve
```

**Request Body:**
```json
{
  "resolutionNotes": "Customer data updated",
  "resolvedBy": 456
}
```

#### Get Data Quality Summary
```http
GET /api/v1/reports/data-quality/summary
```

**Response:**
```json
{
  "totalIssues": 150,
  "byType": {
    "MISSING_CUSTOMER_DATA": 45,
    "INVALID_ID": 30,
    "DUPLICATE_RECORDS": 25,
    "INCOMPLETE_TRANSACTIONS": 50
  },
  "bySeverity": {
    "INFO": 20,
    "WARNING": 80,
    "ERROR": 45,
    "CRITICAL": 5
  },
  "trend": {
    "last7Days": -15,
    "last30Days": -35
  }
}
```

### 3.8 User Preferences APIs

#### Favorite Report
```http
POST /api/v1/reports/{reportId}/favorite
```

#### Unfavorite Report
```http
DELETE /api/v1/reports/{reportId}/favorite
```

#### Get Favorite Reports
```http
GET /api/v1/reports/favorites
```

#### Save Filter
```http
POST /api/v1/reports/{reportId}/saved-filters
```

**Request Body:**
```json
{
  "filterName": "High Priority Only",
  "filterCriteria": {
    "priority": ["HIGH", "CRITICAL"],
    "status": ["OPEN"]
  },
  "isDefault": false
}
```

#### Get Saved Filters
```http
GET /api/v1/reports/{reportId}/saved-filters
```

---

## 4. Entity Classes (Java)

### 4.1 Report Entity
```java
@Entity
@Table(name = "reports")
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "report_code", nullable = false, unique = true)
    private String reportCode;
    
    @Column(name = "report_name", nullable = false)
    private String reportName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "report_category", nullable = false)
    private ReportCategory reportCategory;
    
    @Column(name = "description")
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false)
    private ReportType reportType = ReportType.DYNAMIC;
    
    @Column(name = "base_entity")
    private String baseEntity;
    
    @Column(name = "requires_approval")
    private Boolean requiresApproval = false;
    
    @Column(name = "regulatory_template")
    private String regulatoryTemplate;
    
    @Column(name = "retention_days")
    private Integer retentionDays = 2555;
    
    @Column(name = "enabled")
    private Boolean enabled = true;
    
    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL)
    private List<ReportDefinition> definitions;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Getters, setters, builders...
}
```

### 4.2 ReportExecution Entity
```java
@Entity
@Table(name = "report_executions")
public class ReportExecution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;
    
    @Column(name = "execution_id", nullable = false, unique = true)
    private String executionId;
    
    @Column(name = "psp_id")
    private Long pspId;
    
    @Column(name = "triggered_by")
    private Long triggeredBy;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private TriggerType triggerType;
    
    @Type(JsonType.class)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private Map<String, Object> parameters;
    
    @Column(name = "date_from")
    private LocalDateTime dateFrom;
    
    @Column(name = "date_to")
    private LocalDateTime dateTo;
    
    @Type(JsonType.class)
    @Column(name = "filters_applied", columnDefinition = "jsonb")
    private Map<String, Object> filtersApplied;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ExecutionStatus status = ExecutionStatus.PENDING;
    
    @Column(name = "progress_percent")
    private Integer progressPercent = 0;
    
    @Column(name = "total_records")
    private Long totalRecords;
    
    @Column(name = "file_path")
    private String filePath;
    
    @Type(JsonType.class)
    @Column(name = "file_formats", columnDefinition = "jsonb")
    private List<String> fileFormats;
    
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "execution_time_ms")
    private Integer executionTimeMs;
    
    @Column(name = "error_message")
    private String errorMessage;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    // Getters, setters, builders...
}
```

### 4.3 ReportSchedule Entity
```java
@Entity
@Table(name = "report_schedules")
public class ReportSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;
    
    @Column(name = "psp_id")
    private Long pspId;
    
    @Column(name = "schedule_name", nullable = false)
    private String scheduleName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    private ScheduleFrequency frequency;
    
    @Column(name = "cron_expression")
    private String cronExpression;
    
    @Column(name = "time_of_day")
    private LocalTime timeOfDay;
    
    @Column(name = "day_of_week")
    private Integer dayOfWeek;
    
    @Column(name = "day_of_month")
    private Integer dayOfMonth;
    
    @Column(name = "timezone")
    private String timezone = "UTC";
    
    @Type(JsonType.class)
    @Column(name = "default_parameters", columnDefinition = "jsonb")
    private Map<String, Object> defaultParameters;
    
    @Type(JsonType.class)
    @Column(name = "default_filters", columnDefinition = "jsonb")
    private Map<String, Object> defaultFilters;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "date_range_type")
    private DateRangeType dateRangeType;
    
    @Type(JsonType.class)
    @Column(name = "email_recipients", columnDefinition = "jsonb")
    private List<String> emailRecipients;
    
    @Column(name = "email_subject")
    private String emailSubject;
    
    @Column(name = "email_body")
    private String emailBody;
    
    @Type(JsonType.class)
    @Column(name = "export_formats", columnDefinition = "jsonb")
    private List<String> exportFormats = List.of("PDF", "CSV");
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;
    
    @Column(name = "next_run_at")
    private LocalDateTime nextRunAt;
    
    @Column(name = "run_count")
    private Integer runCount = 0;
    
    @Column(name = "created_by")
    private Long createdBy;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    // Getters, setters, builders...
}
```

### 4.4 RegulatorySubmission Entity
```java
@Entity
@Table(name = "regulatory_submissions")
public class RegulatorySubmission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "submission_reference", nullable = false, unique = true)
    private String submissionReference;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;
    
    @Column(name = "execution_id")
    private Long executionId;
    
    @Column(name = "regulator_code", nullable = false)
    private String regulatorCode;
    
    @Column(name = "submission_type", nullable = false)
    private String submissionType;
    
    @Column(name = "jurisdiction", nullable = false)
    private String jurisdiction;
    
    @Column(name = "filing_period_start", nullable = false)
    private LocalDate filingPeriodStart;
    
    @Column(name = "filing_period_end", nullable = false)
    private LocalDate filingPeriodEnd;
    
    @Column(name = "filing_deadline")
    private LocalDate filingDeadline;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SubmissionStatus status = SubmissionStatus.DRAFT;
    
    @Column(name = "prepared_by")
    private Long preparedBy;
    
    @Column(name = "prepared_at")
    private LocalDateTime preparedAt;
    
    @Column(name = "reviewed_by")
    private Long reviewedBy;
    
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
    
    @Column(name = "approved_by")
    private Long approvedBy;
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
    
    @Column(name = "filed_by")
    private Long filedBy;
    
    @Column(name = "filed_at")
    private LocalDateTime filedAt;
    
    @Column(name = "regulator_reference")
    private String regulatorReference;
    
    @Column(name = "rejection_reason")
    private String rejectionReason;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "amended_submission_id")
    private RegulatorySubmission amendedSubmission;
    
    @Column(name = "amendment_reason")
    private String amendmentReason;
    
    @Column(name = "psp_id")
    private Long pspId;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Getters, setters, builders...
}
```

---

## 5. Enums

```java
public enum ReportCategory {
    AML_FRAUD,
    CURRENCY_THRESHOLD,
    TRANSACTION_MONITORING,
    CHANNEL_MONITORING,
    SANCTIONS,
    FRAUD_INCIDENTS,
    ALERT_CASE_MANAGEMENT,
    RULE_ENGINE,
    RISK_SCORING_MODELS,
    REGULATORY_SUBMISSION,
    COMPLIANCE_MANAGEMENT,
    DATA_QUALITY,
    CHARGEBACK_DISPUTE
}

public enum ReportType {
    STATIC,
    DYNAMIC,
    REGULATORY
}

public enum ExecutionStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

public enum TriggerType {
    MANUAL,
    SCHEDULED,
    API
}

public enum ScheduleFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    QUARTERLY,
    YEARLY
}

public enum DateRangeType {
    PREVIOUS_DAY,
    PREVIOUS_WEEK,
    PREVIOUS_MONTH,
    PREVIOUS_QUARTER,
    PREVIOUS_YEAR,
    CUSTOM
}

public enum SubmissionStatus {
    DRAFT,
    PENDING_REVIEW,
    APPROVED,
    FILED,
    REJECTED,
    AMENDED
}
```

---

## 6. Report Query Examples

### 6.1 SAR Summary Report (SAR_001)
```sql
SELECT 
    sar.sar_reference,
    sar.status,
    sar.sar_type,
    sar.jurisdiction,
    sar.suspicious_activity_type,
    sar.total_suspicious_amount,
    sar.filing_deadline,
    sar.filed_at,
    c.case_reference,
    c.status as case_status,
    u.username as created_by,
    sar.created_at,
    sar.updated_at
FROM suspicious_activity_reports sar
LEFT JOIN compliance_cases c ON sar.case_id = c.id
LEFT JOIN platform_users u ON sar.created_by_user_id = u.id
WHERE sar.psp_id = :pspId
  AND sar.created_at BETWEEN :dateFrom AND :dateTo
  AND (:status IS NULL OR sar.status = :status)
  AND (:sarType IS NULL OR sar.sar_type = :sarType)
ORDER BY sar.created_at DESC
```

### 6.2 Daily Volume Report (TXN_001)
```sql
SELECT 
    DATE(t.txn_ts) as transaction_date,
    COUNT(*) as transaction_count,
    SUM(t.amount_cents) / 100.0 as total_amount,
    AVG(t.amount_cents) / 100.0 as avg_amount,
    COUNT(DISTINCT t.merchant_id) as unique_merchants,
    COUNT(DISTINCT t.pan_hash) as unique_cards,
    tf.action_taken,
    COUNT(CASE WHEN tf.score >= 0.7 THEN 1 END) as high_risk_count
FROM transactions t
LEFT JOIN transaction_features tf ON t.txn_id = tf.txn_id
WHERE t.psp_id = :pspId
  AND t.txn_ts BETWEEN :dateFrom AND :dateTo
GROUP BY DATE(t.txn_ts), tf.action_taken
ORDER BY transaction_date DESC
```

### 6.3 Alert Aging Report (ALC_006)
```sql
SELECT 
    a.status,
    a.severity,
    a.disposition,
    COUNT(*) as alert_count,
    AVG(EXTRACT(EPOCH FROM (COALESCE(a.disposed_at, NOW()) - a.created_at)) / 86400) as avg_age_days,
    SUM(CASE WHEN EXTRACT(EPOCH FROM (NOW() - a.created_at)) / 86400 <= 1 THEN 1 ELSE 0 END) as age_0_1_days,
    SUM(CASE WHEN EXTRACT(EPOCH FROM (NOW() - a.created_at)) / 86400 BETWEEN 1 AND 7 THEN 1 ELSE 0 END) as age_1_7_days,
    SUM(CASE WHEN EXTRACT(EPOCH FROM (NOW() - a.created_at)) / 86400 BETWEEN 7 AND 30 THEN 1 ELSE 0 END) as age_7_30_days,
    SUM(CASE WHEN EXTRACT(EPOCH FROM (NOW() - a.created_at)) / 86400 > 30 THEN 1 ELSE 0 END) as age_over_30_days
FROM alerts a
WHERE a.psp_id = :pspId
  AND a.created_at BETWEEN :dateFrom AND :dateTo
GROUP BY a.status, a.severity, a.disposition
ORDER BY alert_count DESC
```

---

## 7. Implementation Phases

### Phase 1: Core Infrastructure (Week 1-2)
1. Create database schema (all tables)
2. Create entity classes and repositories
3. Implement basic report registry management
4. Set up report execution engine

### Phase 2: Report Categories 1-4 (Week 3-4)
1. AML & Fraud reports (8)
2. Currency & Threshold reports (6)
3. Transaction Monitoring reports (12)
4. Channel Monitoring reports (6)

### Phase 3: Report Categories 5-9 (Week 5-6)
1. Sanctions reports (4)
2. Fraud Incident reports (8)
3. Alert & Case Management reports (10)
4. Rule Engine reports (6)
5. Risk Scoring & Models reports (8)

### Phase 4: Report Categories 10-13 + Export (Week 7-8)
1. Regulatory Submission reports (4)
2. Compliance Management reports (4)
3. Data Quality reports (5)
4. Chargeback & Dispute reports (4)
5. Implement PDF/CSV/Excel export

### Phase 5: Scheduling & Regulatory (Week 9-10)
1. Report scheduling system
2. Email delivery
3. Regulatory submission workflows
4. Template generation

---

## 8. Key Design Decisions

1. **Separation of Report Definition and Execution**: Allows versioning and auditing
2. **Async Report Execution**: Large reports run in background to avoid timeouts
3. **JSONB for Flexible Parameters**: Accommodates varying report parameters
4. **PSP Isolation**: All reports respect PSP boundaries
5. **Regulatory Template System**: Separate templates for different regulators
6. **Data Quality Tracking**: Dedicated table for tracking data quality issues
7. **Execution Result Caching**: Report results cached for pagination and re-export
8. **Schedule Pre-calculation**: Next run time pre-calculated for efficiency

---

## 9. Migration Files

Migration files will be created as:
- `V108__reporting_system_schema.sql` - Core tables
- `V109__report_definitions_seed.sql` - Report definitions for all 85+ reports
- `V110__reporting_system_indexes.sql` - Performance indexes
- `V111__regulatory_templates.sql` - Regulatory templates

---

*Document Version: 1.0*
*Last Updated: 2024-03-13*
*Status: Pending Approval*
