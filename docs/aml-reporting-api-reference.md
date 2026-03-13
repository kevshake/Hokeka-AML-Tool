# AML Reporting System - API Reference

## Base URL
```
/api/v1/reports
```

## Authentication
All endpoints require session-based authentication. Include session cookie with each request.

---

## 1. Report Management Endpoints

### 1.1 Get All Reports
Retrieves a paginated list of all available reports.

```http
GET /api/v1/reports
```

**Query Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| category | String | No | Filter by report category (e.g., AML_FRAUD) |
| type | String | No | Filter by report type (STATIC, DYNAMIC, REGULATORY) |
| search | String | No | Search in report name/code |
| enabled | Boolean | No | Filter by enabled status |
| page | Integer | No | Page number (default: 0) |
| size | Integer | No | Page size (default: 20, max: 100) |

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": 1,
      "reportCode": "SAR_001",
      "reportName": "Suspicious Activity Report Summary",
      "reportCategory": "AML_FRAUD",
      "reportCategoryDisplay": "AML & Fraud",
      "description": "Summary of all SARs filed with status and aging",
      "reportType": "REGULATORY",
      "baseEntity": "suspicious_activity_reports",
      "requiresApproval": true,
      "regulatoryTemplate": "FinCEN_SAR",
      "enabled": true,
      "favorited": true,
      "lastExecutedAt": "2024-03-12T10:00:00Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": {
      "sorted": true,
      "unsorted": false
    }
  },
  "totalElements": 85,
  "totalPages": 5,
  "last": false,
  "first": true
}
```

### 1.2 Get Report Categories
Retrieves all report categories with counts.

```http
GET /api/v1/reports/categories
```

**Response (200 OK):**
```json
{
  "categories": [
    {
      "code": "AML_FRAUD",
      "name": "AML & Fraud",
      "description": "Reports related to suspicious activity and fraud detection",
      "reportCount": 8,
      "icon": "shield",
      "color": "#dc3545"
    },
    {
      "code": "CURRENCY_THRESHOLD",
      "name": "Currency & Threshold",
      "description": "Currency transaction and threshold monitoring",
      "reportCount": 6,
      "icon": "dollar",
      "color": "#28a745"
    }
  ]
}
```

### 1.3 Get Report Details
Retrieves detailed information about a specific report.

```http
GET /api/v1/reports/{reportId}
```

**Path Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| reportId | Long | Yes | Report ID |

**Response (200 OK):**
```json
{
  "id": 1,
  "reportCode": "SAR_001",
  "reportName": "Suspicious Activity Report Summary",
  "reportCategory": "AML_FRAUD",
  "description": "Summary of all SARs filed with status and aging",
  "reportType": "REGULATORY",
  "baseEntity": "suspicious_activity_reports",
  "requiresApproval": true,
  "regulatoryTemplate": "FinCEN_SAR",
  "retentionDays": 2555,
  "enabled": true,
  "definition": {
    "id": 1,
    "version": 1,
    "parameters": [
      {
        "name": "pspId",
        "type": "LONG",
        "required": true,
        "description": "PSP ID for filtering"
      },
      {
        "name": "dateFrom",
        "type": "DATETIME",
        "required": true,
        "description": "Start date"
      },
      {
        "name": "dateTo",
        "type": "DATETIME",
        "required": true,
        "description": "End date"
      },
      {
        "name": "status",
        "type": "STRING",
        "required": false,
        "description": "SAR status filter"
      }
    ],
    "filters": [
      {
        "field": "status",
        "type": "ENUM",
        "options": ["DRAFT", "PENDING_REVIEW", "APPROVED", "FILED", "REJECTED"]
      },
      {
        "field": "sarType",
        "type": "ENUM",
        "options": ["INITIAL", "CONTINUING", "CORRECTED"]
      },
      {
        "field": "jurisdiction",
        "type": "STRING"
      },
      {
        "field": "amountMin",
        "type": "DECIMAL"
      },
      {
        "field": "amountMax",
        "type": "DECIMAL"
      }
    ],
    "columns": [
      {
        "name": "sar_reference",
        "type": "STRING",
        "label": "SAR Reference",
        "sortable": true,
        "filterable": true
      },
      {
        "name": "status",
        "type": "STRING",
        "label": "Status",
        "sortable": true,
        "filterable": true
      },
      {
        "name": "total_suspicious_amount",
        "type": "CURRENCY",
        "label": "Total Amount",
        "sortable": true,
        "filterable": true
      }
    ],
    "isActive": true
  },
  "createdAt": "2024-01-01T00:00:00Z",
  "updatedAt": "2024-01-01T00:00:00Z"
}
```

### 1.4 Get Report Preview
Gets a preview of report data (limited rows) without full execution.

```http
POST /api/v1/reports/{reportId}/preview
```

**Request Body:**
```json
{
  "parameters": {
    "dateFrom": "2024-01-01T00:00:00Z",
    "dateTo": "2024-01-31T23:59:59Z",
    "pspId": 123
  },
  "filters": {
    "status": ["DRAFT", "PENDING"]
  },
  "limit": 10
}
```

**Response (200 OK):**
```json
{
  "reportId": 1,
  "reportName": "Suspicious Activity Report Summary",
  "columns": [...],
  "data": [...],
  "totalRecords": 1500,
  "previewLimit": 10
}
```

---

## 2. Report Execution Endpoints

### 2.1 Execute Report
Queues a report for execution (async operation).

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

**Response (202 Accepted):**
```json
{
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "reportId": 1,
  "reportName": "Suspicious Activity Report Summary",
  "status": "PENDING",
  "message": "Report execution queued successfully",
  "estimatedCompletion": "2024-03-13T10:05:00Z",
  "checkStatusUrl": "/api/v1/reports/execute/550e8400-e29b-41d4-a716-446655440000/status",
  "resultsUrl": "/api/v1/reports/execute/550e8400-e29b-41d4-a716-446655440000/results"
}
```

### 2.2 Get Execution Status
Retrieves the current status of a report execution.

```http
GET /api/v1/reports/execute/{executionId}/status
```

**Response (200 OK):**
```json
{
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "reportId": 1,
  "reportName": "Suspicious Activity Report Summary",
  "status": "RUNNING",
  "progressPercent": 45,
  "totalRecords": 1500,
  "processedRecords": 675,
  "currentPhase": "Aggregating data",
  "startedAt": "2024-03-13T10:00:00Z",
  "estimatedCompletion": "2024-03-13T10:05:00Z",
  "elapsedTimeMs": 45000
}
```

### 2.3 Get Execution Results
Retrieves the results of a completed report execution.

```http
GET /api/v1/reports/execute/{executionId}/results
```

**Query Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| page | Integer | No | Page number (default: 0) |
| size | Integer | No | Page size (default: 100) |
| sortBy | String | No | Sort column |
| sortDirection | String | No | ASC or DESC |

**Response (200 OK):**
```json
{
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "reportId": 1,
  "reportName": "Suspicious Activity Report Summary",
  "reportCode": "SAR_001",
  "parameters": {
    "dateFrom": "2024-01-01T00:00:00Z",
    "dateTo": "2024-01-31T23:59:59Z",
    "pspId": 123
  },
  "filtersApplied": {
    "status": ["DRAFT", "PENDING"]
  },
  "summary": {
    "totalRecords": 1500,
    "generatedAt": "2024-03-13T10:05:00Z",
    "executionTimeMs": 12000,
    "generatedBy": "john.doe@bank.com"
  },
  "columns": [
    {
      "name": "sar_reference",
      "type": "STRING",
      "label": "SAR Reference",
      "sortable": true,
      "filterable": true
    },
    {
      "name": "status",
      "type": "STRING",
      "label": "Status",
      "sortable": true,
      "filterable": true
    },
    {
      "name": "total_suspicious_amount",
      "type": "CURRENCY",
      "label": "Total Amount",
      "currency": "USD",
      "sortable": true,
      "filterable": true
    }
  ],
  "data": [
    {
      "sar_reference": "SAR-2024-001",
      "status": "FILED",
      "sar_type": "INITIAL",
      "jurisdiction": "US",
      "suspicious_activity_type": "Structuring",
      "total_suspicious_amount": 50000.00,
      "filing_deadline": "2024-02-15",
      "filed_at": "2024-01-20T10:30:00Z",
      "case_reference": "CASE-2024-0001",
      "created_by": "analyst@bank.com",
      "created_at": "2024-01-15T09:00:00Z",
      "days_open": 5
    }
  ],
  "pagination": {
    "page": 0,
    "size": 100,
    "totalElements": 1500,
    "totalPages": 15,
    "first": true,
    "last": false
  },
  "exports": {
    "PDF": {
      "url": "/api/v1/reports/export/exp-001/download",
      "size": 2457600,
      "expiresAt": "2024-03-14T10:00:00Z"
    },
    "CSV": {
      "url": "/api/v1/reports/export/exp-002/download",
      "size": 512000,
      "expiresAt": "2024-03-14T10:00:00Z"
    }
  }
}
```

### 2.4 Cancel Execution
Cancels a running or pending report execution.

```http
POST /api/v1/reports/execute/{executionId}/cancel
```

**Response (200 OK):**
```json
{
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "CANCELLED",
  "message": "Execution cancelled successfully"
}
```

### 2.5 Retry Execution
Retries a failed report execution.

```http
POST /api/v1/reports/execute/{executionId}/retry
```

**Response (202 Accepted):**
```json
{
  "executionId": "550e8400-e29b-41d4-a716-446655440001",
  "reportId": 1,
  "status": "PENDING",
  "message": "Report execution queued for retry",
  "previousExecutionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### 2.6 List My Executions
Lists report executions for the current user.

```http
GET /api/v1/reports/executions/my
```

**Query Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| reportId | Long | No | Filter by report |
| status | String | No | Filter by status |
| dateFrom | DateTime | No | Filter by date range |
| dateTo | DateTime | No | Filter by date range |
| page | Integer | No | Page number |
| size | Integer | No | Page size |

**Response (200 OK):**
```json
{
  "content": [
    {
      "executionId": "550e8400-e29b-41d4-a716-446655440000",
      "reportId": 1,
      "reportName": "Suspicious Activity Report Summary",
      "reportCode": "SAR_001",
      "status": "COMPLETED",
      "totalRecords": 1500,
      "dateFrom": "2024-01-01T00:00:00Z",
      "dateTo": "2024-01-31T23:59:59Z",
      "startedAt": "2024-03-13T10:00:00Z",
      "completedAt": "2024-03-13T10:05:00Z",
      "executionTimeMs": 12000,
      "availableFormats": ["PDF", "CSV", "EXCEL"]
    }
  ],
  "totalElements": 25,
  "totalPages": 3
}
```

---

## 3. Export Endpoints

### 3.1 Export Report
Generates an export file for a completed report execution.

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
    "includeCharts": true,
    "pageOrientation": "LANDSCAPE",
    "paperSize": "A4",
    "headerText": "Confidential - AML Department",
    "footerText": "Page {page} of {totalPages}"
  }
}
```

**Supported Formats:** PDF, CSV, EXCEL, XML (regulatory)

**Response (202 Accepted):**
```json
{
  "exportId": "660e8400-e29b-41d4-a716-446655440000",
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "format": "PDF",
  "status": "GENERATING",
  "progressPercent": 0,
  "downloadUrl": "/api/v1/reports/export/660e8400-e29b-41d4-a716-446655440000/download",
  "expiresAt": "2024-03-14T10:00:00Z"
}
```

### 3.2 Get Export Status
Checks the status of an export generation.

```http
GET /api/v1/reports/export/{exportId}/status
```

**Response (200 OK):**
```json
{
  "exportId": "660e8400-e29b-41d4-a716-446655440000",
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "format": "PDF",
  "status": "COMPLETED",
  "progressPercent": 100,
  "fileSize": 2457600,
  "downloadUrl": "/api/v1/reports/export/660e8400-e29b-41d4-a716-446655440000/download",
  "expiresAt": "2024-03-14T10:00:00Z",
  "generatedAt": "2024-03-13T10:06:00Z"
}
```

### 3.3 Download Export
Downloads the generated export file.

```http
GET /api/v1/reports/export/{exportId}/download
```

**Response:** Binary file (Content-Type: application/pdf, text/csv, or application/vnd.openxmlformats-officedocument.spreadsheetml.sheet)

### 3.4 Get Available Export Formats
Gets available export formats for a report.

```http
GET /api/v1/reports/{reportId}/export-formats
```

**Response (200 OK):**
```json
{
  "formats": [
    {
      "code": "PDF",
      "name": "PDF Document",
      "description": "Formatted PDF report with charts",
      "enabled": true,
      "contentType": "application/pdf",
      "fileExtension": "pdf",
      "maxRows": null
    },
    {
      "code": "CSV",
      "name": "CSV File",
      "description": "Raw data in CSV format",
      "enabled": true,
      "contentType": "text/csv",
      "fileExtension": "csv",
      "maxRows": null
    },
    {
      "code": "EXCEL",
      "name": "Excel Workbook",
      "description": "Formatted Excel with multiple sheets",
      "enabled": true,
      "contentType": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "fileExtension": "xlsx",
      "maxRows": 1048576
    },
    {
      "code": "XML",
      "name": "XML (Regulatory)",
      "description": "Regulatory XML format",
      "enabled": true,
      "regulatoryOnly": true,
      "contentType": "application/xml",
      "fileExtension": "xml",
      "maxRows": null
    }
  ]
}
```

---

## 4. Report Scheduling Endpoints

### 4.1 Create Schedule
Creates a new scheduled report.

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
  "timezone": "America/New_York",
  "defaultParameters": {
    "dateRangeType": "PREVIOUS_MONTH"
  },
  "defaultFilters": {
    "status": ["FILED"]
  },
  "emailRecipients": ["compliance@bank.com", "aml@bank.com"],
  "emailSubject": "Monthly SAR Summary Report - {{period}}",
  "emailBody": "Please find attached the monthly SAR summary report for {{period}}.",
  "exportFormats": ["PDF", "CSV"],
  "isActive": true
}
```

**Response (201 Created):**
```json
{
  "id": 1,
  "reportId": 1,
  "reportName": "Suspicious Activity Report Summary",
  "scheduleName": "Monthly SAR Summary",
  "frequency": "MONTHLY",
  "dayOfMonth": 1,
  "timeOfDay": "08:00:00",
  "timezone": "America/New_York",
  "nextRunAt": "2024-04-01T08:00:00Z",
  "isActive": true,
  "createdAt": "2024-03-13T10:00:00Z"
}
```

### 4.2 Get Schedules
Retrieves all scheduled reports.

```http
GET /api/v1/reports/schedule
```

**Query Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| reportId | Long | No | Filter by report |
| isActive | Boolean | No | Filter by active status |
| page | Integer | No | Page number |
| size | Integer | No | Page size |

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": 1,
      "reportId": 1,
      "reportName": "Suspicious Activity Report Summary",
      "reportCode": "SAR_001",
      "scheduleName": "Monthly SAR Summary",
      "frequency": "MONTHLY",
      "nextRunAt": "2024-04-01T08:00:00Z",
      "lastRunAt": "2024-03-01T08:00:00Z",
      "isActive": true,
      "runCount": 12,
      "failCount": 0
    }
  ],
  "totalElements": 5,
  "totalPages": 1
}
```

### 4.3 Get Schedule Details
Retrieves detailed information about a schedule.

```http
GET /api/v1/reports/schedule/{scheduleId}
```

### 4.4 Update Schedule
Updates an existing schedule.

```http
PUT /api/v1/reports/schedule/{scheduleId}
```

### 4.5 Delete Schedule
Deletes a schedule.

```http
DELETE /api/v1/reports/schedule/{scheduleId}
```

### 4.6 Run Schedule Now
Manually triggers a scheduled report to run immediately.

```http
POST /api/v1/reports/schedule/{scheduleId}/run-now
```

**Response (202 Accepted):**
```json
{
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "Scheduled report triggered for immediate execution",
  "checkStatusUrl": "/api/v1/reports/execute/550e8400-e29b-41d4-a716-446655440000/status"
}
```

### 4.7 Get Schedule History
Retrieves execution history for a schedule.

```http
GET /api/v1/reports/schedule/{scheduleId}/history
```

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": 1,
      "scheduleId": 1,
      "executionId": "550e8400-e29b-41d4-a716-446655440000",
      "scheduledFor": "2024-03-01T08:00:00Z",
      "executedAt": "2024-03-01T08:00:05Z",
      "status": "SUCCESS",
      "recordsGenerated": 150
    }
  ],
  "totalElements": 12
}
```

### 4.8 Pause Schedule
Pauses an active schedule.

```http
POST /api/v1/reports/schedule/{scheduleId}/pause
```

### 4.9 Resume Schedule
Resumes a paused schedule.

```http
POST /api/v1/reports/schedule/{scheduleId}/resume
```

---

## 5. Regulatory Submission Endpoints

### 5.1 Create Submission
Creates a new regulatory submission.

```http
POST /api/v1/reports/regulatory/submissions
```

**Request Body:**
```json
{
  "reportId": 1,
  "executionId": 123,
  "regulatorCode": "FinCEN",
  "submissionType": "SAR",
  "jurisdiction": "US",
  "filingPeriodStart": "2024-01-01",
  "filingPeriodEnd": "2024-01-31",
  "filingDeadline": "2024-02-15",
  "pspId": 123
}
```

**Response (201 Created):**
```json
{
  "id": 1,
  "submissionReference": "SUB-2024-001",
  "reportId": 1,
  "regulatorCode": "FinCEN",
  "submissionType": "SAR",
  "jurisdiction": "US",
  "filingPeriodStart": "2024-01-01",
  "filingPeriodEnd": "2024-01-31",
  "filingDeadline": "2024-02-15",
  "status": "DRAFT",
  "daysUntilDeadline": 30,
  "createdAt": "2024-03-13T10:00:00Z"
}
```

### 5.2 Get Submissions
Retrieves regulatory submissions.

```http
GET /api/v1/reports/regulatory/submissions
```

**Query Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| status | String | No | Filter by status |
| regulatorCode | String | No | Filter by regulator |
| filingDeadlineFrom | Date | No | Filter by deadline range |
| filingDeadlineTo | Date | No | Filter by deadline range |
| isLate | Boolean | No | Filter late filings |
| page | Integer | No | Page number |
| size | Integer | No | Page size |

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": 1,
      "submissionReference": "SUB-2024-001",
      "regulatorCode": "FinCEN",
      "submissionType": "SAR",
      "jurisdiction": "US",
      "filingPeriodStart": "2024-01-01",
      "filingPeriodEnd": "2024-01-31",
      "filingDeadline": "2024-02-15",
      "status": "APPROVED",
      "isLateFiling": false,
      "daysUntilDeadline": 5,
      "preparedBy": "analyst@bank.com",
      "preparedAt": "2024-02-10T10:00:00Z",
      "approvedBy": "mlro@bank.com",
      "approvedAt": "2024-02-12T14:00:00Z"
    }
  ],
  "totalElements": 25
}
```

### 5.3 Get Submission Details
Retrieves detailed submission information.

```http
GET /api/v1/reports/regulatory/submissions/{submissionId}
```

### 5.4 Update Submission Status
Updates the status of a submission.

```http
PUT /api/v1/reports/regulatory/submissions/{submissionId}/status
```

**Request Body:**
```json
{
  "status": "APPROVED",
  "notes": "Approved for filing - MLRO review complete",
  "userId": 456
}
```

### 5.5 Generate Regulatory File
Generates the regulatory filing document.

```http
POST /api/v1/reports/regulatory/submissions/{submissionId}/generate
```

**Request Body:**
```json
{
  "format": "XML",
  "template": "FinCEN_SAR_v1.2",
  "validateOnly": false
}
```

**Response (200 OK):**
```json
{
  "submissionId": 1,
  "format": "XML",
  "fileUrl": "/api/v1/reports/regulatory/submissions/1/file",
  "validationResult": {
    "valid": true,
    "errors": [],
    "warnings": []
  }
}
```

### 5.6 File Submission
Marks a submission as filed with the regulator.

```http
POST /api/v1/reports/regulatory/submissions/{submissionId}/file
```

**Request Body:**
```json
{
  "filedBy": 456,
  "filedAt": "2024-02-14T10:00:00Z",
  "regulatorReference": "FINCEN-2024-123456",
  "filingReceipt": "Receipt confirmation...",
  "attachments": ["path/to/file1.xml"]
}
```

### 5.7 Create Amendment
Creates an amended submission.

```http
POST /api/v1/reports/regulatory/submissions/{submissionId}/amend
```

**Request Body:**
```json
{
  "amendmentReason": "Correction to transaction amount",
  "changes": {
    "totalAmount": 75000.00
  }
}
```

### 5.8 Get Late Filings
Retrieves all late or at-risk filings.

```http
GET /api/v1/reports/regulatory/late-filings
```

**Response (200 OK):**
```json
{
  "overdue": [
    {
      "submissionId": 5,
      "submissionReference": "SUB-2024-005",
      "regulatorCode": "FinCEN",
      "submissionType": "SAR",
      "filingDeadline": "2024-02-15",
      "daysOverdue": 5,
      "status": "DRAFT",
      "priority": "CRITICAL"
    }
  ],
  "atRisk": [
    {
      "submissionId": 6,
      "submissionReference": "SUB-2024-006",
      "regulatorCode": "FinCEN",
      "filingDeadline": "2024-03-20",
      "daysRemaining": 2,
      "status": "PENDING_REVIEW"
    }
  ],
  "totalOverdue": 1,
  "totalAtRisk": 3
}
```

### 5.9 Get Regulatory Templates
Retrieves available regulatory templates.

```http
GET /api/v1/reports/regulatory/templates
```

**Response (200 OK):**
```json
{
  "templates": [
    {
      "id": 1,
      "templateCode": "FinCEN_SAR",
      "templateName": "FinCEN Suspicious Activity Report",
      "regulatorCode": "FinCEN",
      "jurisdiction": "US",
      "submissionType": "SAR",
      "version": 1,
      "effectiveDate": "2024-01-01",
      "isActive": true
    }
  ]
}
```

---

## 6. Data Quality Endpoints

### 6.1 Get Data Quality Issues
Retrieves data quality issues.

```http
GET /api/v1/reports/data-quality/issues
```

**Query Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| issueType | String | No | Filter by issue type |
| severity | String | No | Filter by severity |
| status | String | No | Filter by status |
| entityType | String | No | Filter by entity type |
| page | Integer | No | Page number |
| size | Integer | No | Page size |

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": 1,
      "issueType": "MISSING_CUSTOMER_DATA",
      "entityType": "CUSTOMER",
      "entityId": "CUST-001",
      "fieldName": "address",
      "expectedFormat": "Full address with postal code",
      "actualValue": null,
      "severity": "WARNING",
      "status": "OPEN",
      "createdAt": "2024-03-13T10:00:00Z"
    }
  ],
  "totalElements": 150
}
```

### 6.2 Resolve Issue
Marks a data quality issue as resolved.

```http
POST /api/v1/reports/data-quality/issues/{issueId}/resolve
```

**Request Body:**
```json
{
  "resolutionNotes": "Customer data updated with complete address",
  "userId": 456
}
```

### 6.3 Get Data Quality Summary
Retrieves summary statistics for data quality.

```http
GET /api/v1/reports/data-quality/summary
```

**Response (200 OK):**
```json
{
  "totalIssues": 150,
  "openIssues": 125,
  "resolvedIssues": 25,
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
  "byStatus": {
    "OPEN": 125,
    "RESOLVED": 25
  },
  "trend": {
    "last7Days": -15,
    "last30Days": -35
  },
  "lastScanAt": "2024-03-13T06:00:00Z"
}
```

### 6.4 Run Data Quality Scan
Triggers a new data quality scan.

```http
POST /api/v1/reports/data-quality/scan
```

**Request Body:**
```json
{
  "entityTypes": ["CUSTOMER", "TRANSACTION", "MERCHANT"],
  "issueTypes": ["MISSING_CUSTOMER_DATA", "INVALID_ID"],
  "pspId": 123
}
```

---

## 7. User Preference Endpoints

### 7.1 Favorite Report
Adds a report to user's favorites.

```http
POST /api/v1/reports/{reportId}/favorite
```

**Response (201 Created):**
```json
{
  "reportId": 1,
  "favorited": true,
  "favoritesCount": 5
}
```

### 7.2 Unfavorite Report
Removes a report from user's favorites.

```http
DELETE /api/v1/reports/{reportId}/favorite
```

### 7.3 Get Favorite Reports
Retrieves user's favorite reports.

```http
GET /api/v1/reports/favorites
```

**Response (200 OK):**
```json
{
  "reports": [
    {
      "id": 1,
      "reportCode": "SAR_001",
      "reportName": "Suspicious Activity Report Summary",
      "reportCategory": "AML_FRAUD",
      "displayOrder": 1
    }
  ]
}
```

### 7.4 Save Filter
Saves a filter configuration for a report.

```http
POST /api/v1/reports/{reportId}/saved-filters
```

**Request Body:**
```json
{
  "filterName": "High Priority SARs",
  "filterCriteria": {
    "priority": ["HIGH", "CRITICAL"],
    "status": ["DRAFT", "PENDING_REVIEW"]
  },
  "isDefault": false
}
```

### 7.5 Get Saved Filters
Retrieves saved filters for a report.

```http
GET /api/v1/reports/{reportId}/saved-filters
```

### 7.6 Delete Saved Filter
Deletes a saved filter.

```http
DELETE /api/v1/reports/saved-filters/{filterId}
```

---

## 8. Error Responses

All errors follow this format:

```json
{
  "timestamp": "2024-03-13T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid date range: dateFrom must be before dateTo",
  "path": "/api/v1/reports/1/execute",
  "errorCode": "INVALID_DATE_RANGE"
}
```

### Common Error Codes
| Code | HTTP Status | Description |
|------|-------------|-------------|
| REPORT_NOT_FOUND | 404 | Report does not exist |
| EXECUTION_NOT_FOUND | 404 | Execution does not exist |
| INVALID_PARAMETERS | 400 | Missing or invalid parameters |
| INVALID_DATE_RANGE | 400 | Date range is invalid |
| EXECUTION_FAILED | 500 | Report execution failed |
| EXPORT_FAILED | 500 | Export generation failed |
| UNAUTHORIZED | 403 | User not authorized |
| RATE_LIMITED | 429 | Too many requests |

---

## 9. Rate Limits

- **Report Execution**: 10 requests per minute per user
- **Export Generation**: 5 requests per minute per user
- **Data Export**: 100 MB per hour per user
- **Scheduled Reports**: 50 schedules per PSP

---

*API Version: 1.0*
*Last Updated: 2024-03-13*
