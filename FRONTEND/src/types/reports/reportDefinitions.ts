/**
 * AML Reports Center - Report Definitions
 * 85+ reports across 13 categories
 */

export type ReportType = "Regulatory" | "Operational" | "Compliance" | "Analytical";
export type ReportStatus = "draft" | "scheduled" | "generating" | "completed" | "failed";
export type ExportFormat = "PDF" | "CSV" | "Excel";

export interface ReportParameter {
  name: string;
  label: string;
  type: "date" | "daterange" | "select" | "multiselect" | "text" | "number" | "checkbox" | "currency";
  required: boolean;
  defaultValue?: string | number | boolean | string[];
  options?: { value: string; label: string }[];
  placeholder?: string;
  min?: number;
  max?: number;
}

export interface ReportCategory {
  id: string;
  name: string;
  icon: string;
  description: string;
  reportCount: number;
}

export interface ReportDefinition {
  id: string;
  /** Numeric reports.id used by scheduling endpoints. */
  databaseId?: number;
  /** DB report_code (e.g. "SAR_001"). Equal to `id` for DB-sourced reports. */
  code: string;
  name: string;
  description: string;
  category: string;
  type: ReportType;
  parameters: ReportParameter[];
  supportsChart: boolean;
  supportsExport: ExportFormat[];
  defaultSchedule?: ScheduleConfig;
  tags: string[];
}

export interface ScheduleConfig {
  frequency: "daily" | "weekly" | "monthly" | "quarterly" | "yearly";
  dayOfWeek?: number; // 0-6
  dayOfMonth?: number; // 1-31
  time?: string; // HH:mm
  timezone: string;
  recipients: string[];
  formats: ExportFormat[];
}

export interface ReportInstance {
  id: string;
  executionId?: string;
  reportId: string;
  reportCode?: string;
  reportName: string;
  status: ReportStatus;
  parameters: Record<string, unknown>;
  createdAt: string;
  completedAt?: string;
  fileUrl?: string;
  fileSize?: number;
  format: ExportFormat;
  errorMessage?: string;
  createdBy: string;
}

// ==================== 13 CATEGORIES ====================

export const REPORT_CATEGORIES: ReportCategory[] = [
  {
    id: "aml-fraud",
    name: "AML & Fraud Reporting",
    icon: "ShieldAlert",
    description: "Core AML and fraud detection reports",
    reportCount: 8
  },
  {
    id: "currency-threshold",
    name: "Currency & Threshold",
    icon: "DollarSign",
    description: "Currency transaction and threshold monitoring",
    reportCount: 6
  },
  {
    id: "transaction-monitoring",
    name: "Transaction Monitoring",
    icon: "Activity",
    description: "Transaction analysis and monitoring",
    reportCount: 12
  },
  {
    id: "channel-monitoring",
    name: "Channel Monitoring",
    icon: "Monitor",
    description: "Channel and platform monitoring",
    reportCount: 6
  },
  {
    id: "sanctions",
    name: "Sanctions Reports",
    icon: "Ban",
    description: "Sanctions screening and compliance",
    reportCount: 4
  },
  {
    id: "fraud-incidents",
    name: "Fraud Incident Reports",
    icon: "AlertTriangle",
    description: "Fraud incident tracking and analysis",
    reportCount: 8
  },
  {
    id: "alert-case",
    name: "Alert & Case Management",
    icon: "FolderOpen",
    description: "Alert and case management reports",
    reportCount: 10
  },
  {
    id: "rule-engine",
    name: "Rule Engine Performance",
    icon: "Settings",
    description: "Rule engine metrics and performance",
    reportCount: 6
  },
  {
    id: "risk-scoring",
    name: "Risk Scoring & Models",
    icon: "BarChart3",
    description: "Risk scoring and model performance",
    reportCount: 8
  },
  {
    id: "regulatory-submission",
    name: "Regulatory Submission",
    icon: "FileText",
    description: "Regulatory filing and submission",
    reportCount: 6
  },
  {
    id: "compliance-management",
    name: "Compliance Management",
    icon: "CheckCircle",
    description: "Compliance program management",
    reportCount: 4
  },
  {
    id: "data-quality",
    name: "Data Quality",
    icon: "Database",
    description: "Data quality and integrity reports",
    reportCount: 5
  },
  {
    id: "chargeback-dispute",
    name: "Chargeback & Dispute",
    icon: "CreditCard",
    description: "Chargeback and dispute management",
    reportCount: 4
  },
  {
    id: "cbk-reporting",
    name: "CBK Reporting",
    icon: "AccountBalance",
    description: "Central Bank of Kenya regulatory reports",
    reportCount: 6
  }
];

// ==================== DB-DRIVEN CATALOG MAPPING ====================
//
// The live report catalog is sourced from the database `reports` table via the
// backend ReportDefinitionDTO (see features/api/reportQueries.ts -> useReportDefinitions).
// The hardcoded REPORT_DEFINITIONS array was removed; the helpers
// below map a DTO onto the ReportDefinition shape this UI renders.

/** Shape of a single ReportDefinitionDTO as returned by GET /reports/definitions. */
export interface ReportDefinitionDTO {
  id?: number;
  reportCode: string;
  reportName: string;
  /** Backend ReportCategory enum, serialized as its name e.g. "AML_FRAUD". */
  reportCategory: string;
  categoryDisplayName?: string;
  description?: string;
  /** Backend ReportType enum: REGULATORY | DYNAMIC | ANALYTICAL. */
  reportType?: string;
  baseEntity?: string;
  requiresApproval?: boolean;
  enabled?: boolean;
  currentVersion?: number;
  parameters?: Array<Record<string, unknown>> | null;
  filters?: Array<Record<string, unknown>> | null;
  columns?: Array<Record<string, unknown>> | null;
}

/**
 * Map of the backend ReportCategory enum -> the friendly category id this UI uses.
 * Covers all 13 enum values present in the DB. `cbk-reporting` has no DB enum value
 * (CBK reports live under REGULATORY_SUBMISSION) — it stays in REPORT_CATEGORIES purely
 * to drive the CBK submission panel in the sidebar.
 */
export const REPORT_CATEGORY_ID_BY_ENUM: Record<string, string> = {
  AML_FRAUD: "aml-fraud",
  CURRENCY_THRESHOLD: "currency-threshold",
  TRANSACTION_MONITORING: "transaction-monitoring",
  CHANNEL_MONITORING: "channel-monitoring",
  SANCTIONS: "sanctions",
  FRAUD_INCIDENTS: "fraud-incidents",
  ALERT_CASE_MANAGEMENT: "alert-case",
  RULE_ENGINE: "rule-engine",
  RISK_SCORING_MODELS: "risk-scoring",
  REGULATORY_SUBMISSION: "regulatory-submission",
  COMPLIANCE_MANAGEMENT: "compliance-management",
  DATA_QUALITY: "data-quality",
  CHARGEBACK_DISPUTE: "chargeback-dispute",
  CBK: "cbk-reporting",
};

/**
 * Map a backend ReportType enum to the UI ReportType.
 * DB enum is REGULATORY | DYNAMIC | ANALYTICAL; the UI also models Operational/Compliance
 * for icon/colour purposes, so DYNAMIC (the bulk of the catalog) is shown as "Operational".
 */
const mapReportType = (reportType?: string): ReportType => {
  switch ((reportType || "").toUpperCase()) {
    case "REGULATORY":
      return "Regulatory";
    case "ANALYTICAL":
      return "Analytical";
    case "DYNAMIC":
    default:
      return "Operational";
  }
};

/** Map a backend parameter `type` (e.g. "DATETIME", "LONG") to a UI ReportParameter type. */
const mapParameterType = (type?: string): ReportParameter["type"] => {
  switch ((type || "").toUpperCase()) {
    case "DATE":
    case "DATETIME":
    case "TIMESTAMP":
      return "date";
    case "LONG":
    case "INTEGER":
    case "INT":
    case "DECIMAL":
    case "DOUBLE":
    case "NUMBER":
      return "number";
    case "BOOLEAN":
    case "BOOL":
      return "checkbox";
    case "STRING":
    case "TEXT":
    default:
      return "text";
  }
};

/** Humanise a parameter name, e.g. "dateFrom" -> "Date From". */
const humaniseName = (name: string): string =>
  name
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replace(/[_-]+/g, " ")
    .replace(/\b\w/g, (c) => c.toUpperCase())
    .trim();

/** Map a single DB parameter map onto the UI ReportParameter shape. */
const mapDbParameter = (raw: Record<string, unknown>): ReportParameter => {
  const name = String(raw.name ?? "");
  const label = raw.label ? String(raw.label) : humaniseName(name);
  const defaultValue = raw.defaultValue as ReportParameter["defaultValue"];
  return {
    name,
    label,
    type: mapParameterType(raw.type as string | undefined),
    required: Boolean(raw.required),
    ...(defaultValue !== undefined && defaultValue !== null ? { defaultValue } : {}),
  };
};

/**
 * Map a backend ReportDefinitionDTO onto the ReportDefinition shape this UI renders.
 * Only ~5 reports carry parameters; the rest get an empty parameter list.
 */
export const mapDtoToReportDefinition = (dto: ReportDefinitionDTO): ReportDefinition => {
  const category =
    REPORT_CATEGORY_ID_BY_ENUM[(dto.reportCategory || "").toUpperCase()] ??
    (dto.reportCategory || "").toLowerCase().replace(/_/g, "-");

  const parameters = Array.isArray(dto.parameters)
    ? dto.parameters.filter((p) => p && p.name).map(mapDbParameter)
    : [];

  return {
    id: dto.reportCode,
    databaseId: dto.id,
    code: dto.reportCode,
    name: dto.reportName,
    description: dto.description ?? "",
    category,
    type: mapReportType(dto.reportType),
    parameters,
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: [],
  };
};

