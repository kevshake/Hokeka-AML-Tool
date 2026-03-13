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
  frequency: "once" | "hourly" | "daily" | "weekly" | "monthly" | "quarterly" | "yearly";
  dayOfWeek?: number; // 0-6
  dayOfMonth?: number; // 1-31
  time?: string; // HH:mm
  timezone: string;
  recipients: string[];
  formats: ExportFormat[];
}

export interface ReportInstance {
  id: string;
  reportId: string;
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
    reportCount: 4
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
  }
];

// ==================== 85+ REPORT DEFINITIONS ====================

export const REPORT_DEFINITIONS: ReportDefinition[] = [
  // ===== 1. AML & Fraud Reporting (8 reports) =====
  {
    id: "aml-suspicious-activity",
    name: "Suspicious Activity Report (SAR)",
    description: "Comprehensive SAR filing report with narrative and supporting evidence",
    category: "aml-fraud",
    type: "Regulatory",
    parameters: [
      { name: "startDate", label: "Start Date", type: "date", required: true },
      { name: "endDate", label: "End Date", type: "date", required: true },
      { name: "status", label: "Status", type: "select", required: false, options: [{ value: "all", label: "All" }, { value: "draft", label: "Draft" }, { value: "filed", label: "Filed" }, { value: "acknowledged", label: "Acknowledged" }] },
      { name: "priority", label: "Priority Level", type: "multiselect", required: false, options: [{ value: "low", label: "Low" }, { value: "medium", label: "Medium" }, { value: "high", label: "High" }, { value: "critical", label: "Critical" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["regulatory", "fincen", "compliance"]
  },
  {
    id: "aml-customer-risk-profile",
    name: "Customer Risk Profile Analysis",
    description: "Detailed customer risk profiles with scoring and segmentation",
    category: "aml-fraud",
    type: "Analytical",
    parameters: [
      { name: "riskLevel", label: "Risk Level", type: "multiselect", required: false, options: [{ value: "low", label: "Low" }, { value: "medium", label: "Medium" }, { value: "high", label: "High" }, { value: "extreme", label: "Extreme" }] },
      { name: "customerType", label: "Customer Type", type: "multiselect", required: false, options: [{ value: "individual", label: "Individual" }, { value: "business", label: "Business" }, { value: "pep", label: "PEP" }, { value: "ngo", label: "NGO" }] },
      { name: "reviewDate", label: "Last Review Date", type: "daterange", required: false }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["kyc", "risk", "profiling"]
  },
  {
    id: "aml-high-risk-transactions",
    name: "High-Risk Transaction Summary",
    description: "Summary of transactions flagged as high risk",
    category: "aml-fraud",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Date Range", type: "daterange", required: true },
      { name: "minAmount", label: "Minimum Amount", type: "currency", required: false },
      { name: "riskScore", label: "Min Risk Score", type: "number", required: false, min: 0, max: 100 }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["high-risk", "transactions", "monitoring"]
  },
  {
    id: "aml-pep-screening",
    name: "PEP Screening Results",
    description: "Politically Exposed Persons screening outcomes",
    category: "aml-fraud",
    type: "Compliance",
    parameters: [
      { name: "screeningDate", label: "Screening Date Range", type: "daterange", required: true },
      { name: "matchStatus", label: "Match Status", type: "multiselect", required: false, options: [{ value: "confirmed", label: "Confirmed Match" }, { value: "possible", label: "Possible Match" }, { value: "false", label: "False Positive" }] },
      { name: "listSource", label: "List Source", type: "multiselect", required: false, options: [{ value: "pep", label: "PEP Lists" }, { value: "sanctions", label: "Sanctions" }, { value: "adverse", label: "Adverse Media" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["pep", "screening", "kyc"]
  },
  {
    id: "aml-edd-review",
    name: "Enhanced Due Diligence (EDD) Review",
    description: "EDD cases requiring enhanced monitoring",
    category: "aml-fraud",
    type: "Compliance",
    parameters: [
      { name: "eddTrigger", label: "EDD Trigger", type: "multiselect", required: false, options: [{ value: "high-risk", label: "High Risk Score" }, { value: "unusual", label: "Unusual Activity" }, { value: "pep", label: "PEP Connection" }, { value: "sanctions", label: "Sanctions Hit" }] },
      { name: "reviewStatus", label: "Review Status", type: "select", required: false, options: [{ value: "all", label: "All" }, { value: "pending", label: "Pending" }, { value: "completed", label: "Completed" }] }
    ],
    supportsChart: false,
    supportsExport: ["PDF", "Excel"],
    tags: ["edd", "due-diligence", "compliance"]
  },
  {
    id: "aml-structuring-analysis",
    name: "Structuring Analysis Report",
    description: "Potential structuring and layering activity detection",
    category: "aml-fraud",
    type: "Analytical",
    parameters: [
      { name: "dateRange", label: "Analysis Period", type: "daterange", required: true },
      { name: "threshold", label: "Structuring Threshold", type: "currency", required: true, defaultValue: 10000 },
      { name: "timeWindow", label: "Time Window (hours)", type: "number", required: false, defaultValue: 24, min: 1, max: 168 }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["structuring", "layering", "detection"]
  },
  {
    id: "aml-typologies",
    name: "AML Typologies Report",
    description: "Money laundering typologies and pattern analysis",
    category: "aml-fraud",
    type: "Analytical",
    parameters: [
      { name: "typology", label: "Typology Type", type: "multiselect", required: false, options: [{ value: "smurfing", label: "Smurfing" }, { value: "trade", label: "Trade-Based" }, { value: "correspondent", label: "Correspondent Banking" }, { value: "casino", label: "Casino/Gaming" }, { value: "crypto", label: "Virtual Currency" }, { value: "real-estate", label: "Real Estate" }] },
      { name: "dateRange", label: "Period", type: "daterange", required: true }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["typologies", "patterns", "analysis"]
  },
  {
    id: "aml-fraud-trends",
    name: "Fraud Trends Analysis",
    description: "Emerging fraud patterns and trend analysis",
    category: "aml-fraud",
    type: "Analytical",
    parameters: [
      { name: "dateRange", label: "Trend Period", type: "daterange", required: true },
      { name: "granularity", label: "Granularity", type: "select", required: false, options: [{ value: "daily", label: "Daily" }, { value: "weekly", label: "Weekly" }, { value: "monthly", label: "Monthly" }] },
      { name: "fraudType", label: "Fraud Type", type: "multiselect", required: false, options: [{ value: "identity", label: "Identity Theft" }, { value: "card", label: "Card Fraud" }, { value: "account", label: "Account Takeover" }, { value: "app", label: "Application Fraud" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["trends", "fraud", "analytics"]
  },

  // ===== 2. Currency & Threshold (6 reports) =====
  {
    id: "ctr-currency-transaction",
    name: "Currency Transaction Report (CTR)",
    description: "FinCEN CTR filings and summaries",
    category: "currency-threshold",
    type: "Regulatory",
    parameters: [
      { name: "dateRange", label: "Filing Period", type: "daterange", required: true },
      { name: "threshold", label: "Threshold Amount", type: "currency", required: true, defaultValue: 10000 },
      { name: "filingStatus", label: "Filing Status", type: "select", required: false, options: [{ value: "all", label: "All" }, { value: "pending", label: "Pending" }, { value: "filed", label: "Filed" }, { value: "amended", label: "Amended" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["ctr", "fincen", "regulatory"]
  },
  {
    id: "ltr-large-transaction",
    name: "Large Transaction Report",
    description: "Transactions exceeding specified thresholds",
    category: "currency-threshold",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "minAmount", label: "Minimum Amount", type: "currency", required: true, defaultValue: 5000 },
      { name: "transactionType", label: "Transaction Type", type: "multiselect", required: false, options: [{ value: "cash-in", label: "Cash In" }, { value: "cash-out", label: "Cash Out" }, { value: "wire", label: "Wire Transfer" }, { value: "ach", label: "ACH" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["threshold", "large-transactions", "monitoring"]
  },
  {
    id: "aggregated-currency",
    name: "Aggregated Currency Report",
    description: "Aggregated multiple transactions by customer",
    category: "currency-threshold",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Aggregation Period", type: "daterange", required: true },
      { name: "aggregationWindow", label: "Window (days)", type: "number", required: true, defaultValue: 1, min: 1, max: 30 },
      { name: "threshold", label: "Aggregation Threshold", type: "currency", required: true, defaultValue: 10000 }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["aggregation", "currency", "monitoring"]
  },
  {
    id: "cash-intensive-business",
    name: "Cash Intensive Business Report",
    description: "Monitoring of cash-intensive business activities",
    category: "currency-threshold",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "businessType", label: "Business Type", type: "multiselect", required: false, options: [{ value: "msb", label: "MSB" }, { value: "casino", label: "Casino" }, { value: "dealer", label: "Car Dealer" }, { value: "jewelry", label: "Jewelry" }, { value: "real-estate", label: "Real Estate" }] },
      { name: "minCashVolume", label: "Min Cash Volume", type: "currency", required: false }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["cash", "business", "monitoring"]
  },
  {
    id: "funds-transfer-report",
    name: "Funds Transfer Report",
    description: "Wire and electronic funds transfer monitoring",
    category: "currency-threshold",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "transferType", label: "Transfer Type", type: "multiselect", required: false, options: [{ value: "incoming", label: "Incoming" }, { value: "outgoing", label: "Outgoing" }, { value: "domestic", label: "Domestic" }, { value: "international", label: "International" }] },
      { name: "minAmount", label: "Minimum Amount", type: "currency", required: false }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["wire", "transfer", "monitoring"]
  },
  {
    id: "monetary-instrument-log",
    name: "Monetary Instrument Log",
    description: "Cashier's checks, money orders, and traveler's checks",
    category: "currency-threshold",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "instrumentType", label: "Instrument Type", type: "multiselect", required: false, options: [{ value: "cashier", label: "Cashier's Check" }, { value: "money-order", label: "Money Order" }, { value: "travelers", label: "Traveler's Check" }] },
      { name: "serialNumber", label: "Serial Number", type: "text", required: false }
    ],
    supportsChart: false,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["instruments", "logs", "monitoring"]
  },

  // ===== 3. Transaction Monitoring (12 reports) =====
  {
    id: "tm-velocity-analysis",
    name: "Velocity Analysis Report",
    description: "Transaction velocity patterns and anomalies",
    category: "transaction-monitoring",
    type: "Analytical",
    parameters: [
      { name: "dateRange", label: "Analysis Period", type: "daterange", required: true },
      { name: "velocityType", label: "Velocity Type", type: "multiselect", required: false, options: [{ value: "count", label: "Transaction Count" }, { value: "amount", label: "Transaction Amount" }, { value: "frequency", label: "Frequency" }] },
      { name: "comparisonPeriod", label: "Compare to Previous", type: "select", required: false, options: [{ value: "none", label: "None" }, { value: "7d", label: "7 Days" }, { value: "30d", label: "30 Days" }, { value: "90d", label: "90 Days" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["velocity", "patterns", "analysis"]
  },
  {
    id: "tm-amount-threshold",
    name: "Amount Threshold Breaches",
    description: "Transactions exceeding defined thresholds",
    category: "transaction-monitoring",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "thresholdType", label: "Threshold Type", type: "select", required: false, options: [{ value: "daily", label: "Daily" }, { value: "weekly", label: "Weekly" }, { value: "monthly", label: "Monthly" }, { value: "single", label: "Single Transaction" }] },
      { name: "alertLevel", label: "Alert Level", type: "multiselect", required: false, options: [{ value: "low", label: "Low" }, { value: "medium", label: "Medium" }, { value: "high", label: "High" }, { value: "critical", label: "Critical" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["threshold", "breaches", "monitoring"]
  },
  {
    id: "tm-pattern-deviation",
    name: "Pattern Deviation Analysis",
    description: "Deviations from customer historical patterns",
    category: "transaction-monitoring",
    type: "Analytical",
    parameters: [
      { name: "dateRange", label: "Analysis Period", type: "daterange", required: true },
      { name: "deviationType", label: "Deviation Type", type: "multiselect", required: false, options: [{ value: "amount", label: "Amount" }, { value: "frequency", label: "Frequency" }, { value: "location", label: "Location" }, { value: "device", label: "Device" }, { value: "merchant", label: "Merchant" }] },
      { name: "sensitivity", label: "Sensitivity", type: "select", required: false, options: [{ value: "low", label: "Low" }, { value: "medium", label: "Medium" }, { value: "high", label: "High" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["patterns", "deviation", "analysis"]
  },
  {
    id: "tm-round-amount",
    name: "Round Amount Analysis",
    description: "Detection of round-amount transactions (potential structuring)",
    category: "transaction-monitoring",
    type: "Analytical",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "roundness", label: "Roundness Level", type: "select", required: false, options: [{ value: "hundreds", label: "Hundreds" }, { value: "thousands", label: "Thousands" }, { value: "ten-thousands", label: "Ten Thousands" }] },
      { name: "minAmount", label: "Minimum Amount", type: "currency", required: false }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["round-amount", "structuring", "analysis"]
  },
  {
    id: "tm-rapid-movement",
    name: "Rapid Movement Detection",
    description: "Quick successive transactions (velocity)",
    category: "transaction-monitoring",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "timeWindow", label: "Time Window (minutes)", type: "number", required: true, defaultValue: 60, min: 1, max: 1440 },
      { name: "minTransactions", label: "Min Transactions", type: "number", required: true, defaultValue: 3, min: 2 }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["rapid", "velocity", "detection"]
  },
  {
    id: "tm-cross-border",
    name: "Cross-Border Transaction Summary",
    description: "International transaction monitoring",
    category: "transaction-monitoring",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "countries", label: "Countries", type: "multiselect", required: false, options: [{ value: "high-risk", label: "High Risk Jurisdictions" }, { value: "sanctioned", label: "Sanctioned Countries" }, { value: "all", label: "All Countries" }] },
      { name: "correspondent", label: "Include Correspondent", type: "checkbox", required: false, defaultValue: true }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["cross-border", "international", "monitoring"]
  },
  {
    id: "tm-atm-analysis",
    name: "ATM Transaction Analysis",
    description: "ATM usage patterns and anomalies",
    category: "transaction-monitoring",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "analysisType", label: "Analysis Type", type: "select", required: false, options: [{ value: "location", label: "Location" }, { value: "time", label: "Time of Day" }, { value: "amount", label: "Amount Distribution" }, { value: "frequency", label: "Frequency" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["atm", "analysis", "monitoring"]
  },
  {
    id: "tm-merchant-analysis",
    name: "Merchant Transaction Analysis",
    description: "Merchant category and volume analysis",
    category: "transaction-monitoring",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "mcc", label: "Merchant Category (MCC)", type: "multiselect", required: false, options: [{ value: "5993", label: "5993 - Financial Services" }, { value: "6051", label: "6051 - Quasi Cash" }, { value: "7995", label: "7995 - Gambling" }, { value: "6012", label: "6012 - Financial Institutions" }] },
      { name: "highRiskOnly", label: "High Risk Only", type: "checkbox", required: false, defaultValue: false }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["merchant", "mcc", "analysis"]
  },
  {
    id: "tm-weekend-holiday",
    name: "Weekend/Holiday Activity",
    description: "Unusual weekend and holiday transaction patterns",
    category: "transaction-monitoring",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "includeWeekends", label: "Include Weekends", type: "checkbox", required: false, defaultValue: true },
      { name: "includeHolidays", label: "Include Holidays", type: "checkbox", required: false, defaultValue: true }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["weekend", "holiday", "patterns"]
  },
  {
    id: "tm-late-night",
    name: "Late Night/Early Morning Activity",
    description: "Transactions outside normal business hours",
    category: "transaction-monitoring",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "startTime", label: "Start Time", type: "text", required: true, defaultValue: "22:00" },
      { name: "endTime", label: "End Time", type: "text", required: true, defaultValue: "06:00" }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["late-night", "time-based", "monitoring"]
  },
  {
    id: "tm-geographic-concentration",
    name: "Geographic Concentration Analysis",
    description: "Transaction clustering by geography",
    category: "transaction-monitoring",
    type: "Analytical",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "granularity", label: "Geographic Level", type: "select", required: false, options: [{ value: "country", label: "Country" }, { value: "state", label: "State/Province" }, { value: "city", label: "City" }, { value: "zip", label: "ZIP/Postal Code" }] },
      { name: "minConcentration", label: "Min Concentration %", type: "number", required: false, min: 0, max: 100 }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["geographic", "concentration", "analysis"]
  },
  {
    id: "tm-beneficial-ownership",
    name: "Beneficial Ownership Verification",
    description: "BO verification status and discrepancies",
    category: "transaction-monitoring",
    type: "Compliance",
    parameters: [
      { name: "verificationStatus", label: "Status", type: "multiselect", required: false, options: [{ value: "verified", label: "Verified" }, { value: "pending", label: "Pending" }, { value: "failed", label: "Failed" }, { value: "expired", label: "Expired" }] },
      { name: "entityType", label: "Entity Type", type: "multiselect", required: false, options: [{ value: "corporation", label: "Corporation" }, { value: "llc", label: "LLC" }, { value: "partnership", label: "Partnership" }, { value: "trust", label: "Trust" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "Excel"],
    tags: ["beneficial-ownership", "verification", "compliance"]
  },

  // ===== 4. Channel Monitoring (6 reports) =====
  {
    id: "channel-online-banking",
    name: "Online Banking Activity Report",
    description: "Online banking channel analysis",
    category: "channel-monitoring",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "activityType", label: "Activity Type", type: "multiselect", required: false, options: [{ value: "login", label: "Login" }, { value: "transfer", label: "Transfer" }, { value: "billpay", label: "Bill Pay" }, { value: "wire", label: "Wire" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["online", "banking", "channel"]
  },
  {
    id: "channel-mobile-app",
    name: "Mobile App Activity Report",
    description: "Mobile application usage analysis",
    category: "channel-monitoring",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "platform", label: "Platform", type: "multiselect", required: false, options: [{ value: "ios", label: "iOS" }, { value: "android", label: "Android" }] },
      { name: "appVersion", label: "App Version", type: "text", required: false }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["mobile", "app", "channel"]
  },
  {
    id: "channel-device-fingerprint",
    name: "Device Fingerprint Analysis",
    description: "Device recognition and anomaly detection",
    category: "channel-monitoring",
    type: "Analytical",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "anomalyType", label: "Anomaly Type", type: "multiselect", required: false, options: [{ value: "new-device", label: "New Device" }, { value: "location", label: "Location Change" }, { value: "multiple", label: "Multiple Devices" }, { value: "emulator", label: "Emulator/Simulator" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["device", "fingerprint", "analysis"]
  },
  {
    id: "channel-ip-analysis",
    name: "IP Address Analysis",
    description: "IP geolocation and risk assessment",
    category: "channel-monitoring",
    type: "Analytical",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "riskLevel", label: "Risk Level", type: "multiselect", required: false, options: [{ value: "low", label: "Low" }, { value: "medium", label: "Medium" }, { value: "high", label: "High" }, { value: "tor", label: "Tor Exit Node" }, { value: "proxy", label: "Proxy/VPN" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["ip", "geolocation", "analysis"]
  },
  {
    id: "channel-session-anomalies",
    name: "Session Anomalies Report",
    description: "Unusual session patterns and behaviors",
    category: "channel-monitoring",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "anomalyType", label: "Anomaly Type", type: "multiselect", required: false, options: [{ value: "duration", label: "Unusual Duration" }, { value: "multiple", label: "Concurrent Sessions" }, { value: "velocity", label: "Session Velocity" }, { value: "logout", label: "No Proper Logout" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["session", "anomalies", "monitoring"]
  },
  {
    id: "channel-api-monitoring",
    name: "API Transaction Monitoring",
    description: "API channel transaction analysis",
    category: "channel-monitoring",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "endpoint", label: "API Endpoint", type: "multiselect", required: false, options: [{ value: "payments", label: "Payments" }, { value: "transfers", label: "Transfers" }, { value: "auth", label: "Authentication" }] },
      { name: "responseCode", label: "Response Code", type: "text", required: false }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["api", "monitoring", "channel"]
  },

  // ===== 5. Sanctions Reports (4 reports) =====
  {
    id: "sanctions-real-time",
    name: "Real-Time Sanctions Screening",
    description: "Live sanctions screening results",
    category: "sanctions",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "listType", label: "List Type", type: "multiselect", required: false, options: [{ value: "ofac", label: "OFAC SDN" }, { value: "un", label: "UN Consolidated" }, { value: "eu", label: "EU Consolidated" }, { value: "hmt", label: "UK HMT" }] },
      { name: "matchType", label: "Match Type", type: "multiselect", required: false, options: [{ value: "name", label: "Name" }, { value: "address", label: "Address" }, { value: "country", label: "Country" }, { value: "id", label: "ID Number" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["sanctions", "screening", "real-time"]
  },
  {
    id: "sanctions-false-positives",
    name: "False Positive Analysis",
    description: "Sanctions screening false positive trends",
    category: "sanctions",
    type: "Analytical",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "resolution", label: "Resolution", type: "multiselect", required: false, options: [{ value: "true", label: "True Hit" }, { value: "false", label: "False Positive" }, { value: "pending", label: "Pending Review" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["sanctions", "false-positives", "analysis"]
  },
  {
    id: "sanctions-name-screening",
    name: "Name Screening Summary",
    description: "Customer and transaction name screening",
    category: "sanctions",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "screeningType", label: "Screening Type", type: "multiselect", required: false, options: [{ value: "onboarding", label: "Onboarding" }, { value: "transaction", label: "Transaction" }, { value: "periodic", label: "Periodic Review" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["sanctions", "name-screening", "summary"]
  },
  {
    id: "sanctions-list-updates",
    name: "Sanctions List Update Log",
    description: "Audit of sanctions list updates and re-screening",
    category: "sanctions",
    type: "Compliance",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "listSource", label: "List Source", type: "multiselect", required: false, options: [{ value: "ofac", label: "OFAC" }, { value: "un", label: "UN" }, { value: "eu", label: "EU" }, { value: "hm-treasury", label: "HM Treasury" }] }
    ],
    supportsChart: false,
    supportsExport: ["PDF", "Excel"],
    tags: ["sanctions", "list-updates", "audit"]
  },

  // ===== 6. Fraud Incident Reports (8 reports) =====
  {
    id: "fraud-incident-summary",
    name: "Fraud Incident Summary",
    description: "Overview of all fraud incidents",
    category: "fraud-incidents",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "fraudType", label: "Fraud Type", type: "multiselect", required: false, options: [{ value: "identity", label: "Identity Theft" }, { value: "card", label: "Card Fraud" }, { value: "account", label: "Account Takeover" }, { value: "app", label: "Application Fraud" }, { value: "wire", label: "Wire Fraud" }] },
      { name: "status", label: "Status", type: "multiselect", required: false, options: [{ value: "open", label: "Open" }, { value: "investigating", label: "Investigating" }, { value: "resolved", label: "Resolved" }, { value: "closed", label: "Closed" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["fraud", "incidents", "summary"]
  },
  {
    id: "fraud-card-compromise",
    name: "Card Compromise Report",
    description: "Compromised card tracking and analysis",
    category: "fraud-incidents",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "compromiseType", label: "Compromise Type", type: "multiselect", required: false, options: [{ value: "skimming", label: "Skimming" }, { value: "data-breach", label: "Data Breach" }, { value: "phishing", label: "Phishing" }, { value: "merchant", label: "Merchant Compromise" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["fraud", "card", "compromise"]
  },
  {
    id: "fraud-identity-theft",
    name: "Identity Theft Analysis",
    description: "Identity theft incidents and patterns",
    category: "fraud-incidents",
    type: "Analytical",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "theftVector", label: "Theft Vector", type: "multiselect", required: false, options: [{ value: "synthetic", label: "Synthetic Identity" }, { value: "stolen", label: "Stolen Identity" }, { value: "account", label: "Account Takeover" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["fraud", "identity", "theft"]
  },
  {
    id: "fraud-account-takeover",
    name: "Account Takeover Report",
    description: "ATO incidents and prevention metrics",
    category: "fraud-incidents",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "vector", label: "Attack Vector", type: "multiselect", required: false, options: [{ value: "credential", label: "Credential Stuffing" }, { value: "phishing", label: "Phishing" }, { value: "malware", label: "Malware" }, { value: "sim", label: "SIM Swap" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["fraud", "account-takeover", "ato"]
  },
  {
    id: "fraud-mule-accounts",
    name: "Mule Account Detection",
    description: "Identified mule accounts and networks",
    category: "fraud-incidents",
    type: "Analytical",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "muleType", label: "Mule Type", type: "multiselect", required: false, options: [{ value: "willing", label: "Willing Participant" }, { value: "unwitting", label: "Unwitting Victim" }, { value: "synthetic", label: "Synthetic Identity" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["fraud", "mule", "accounts"]
  },
  {
    id: "fraud-merchant-fraud",
    name: "Merchant Fraud Report",
    description: "Merchant-related fraud incidents",
    category: "fraud-incidents",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "fraudScheme", label: "Fraud Scheme", type: "multiselect", required: false, options: [{ value: "bustout", label: "Bust-Out" }, { value: "transaction", label: "Transaction Laundering" }, { value: "identity", label: "Identity Swap" }, { value: "friendly", label: "Friendly Fraud" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["fraud", "merchant", "report"]
  },
  {
    id: "fraud-social-engineering",
    name: "Social Engineering Incidents",
    description: "Social engineering attack documentation",
    category: "fraud-incidents",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "attackType", label: "Attack Type", type: "multiselect", required: false, options: [{ value: "phishing", label: "Phishing" }, { value: "vishing", label: "Vishing" }, { value: "smishing", label: "Smishing" }, { value: "pretexting", label: "Pretexting" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["fraud", "social-engineering", "incidents"]
  },
  {
    id: "fraud-insider-threats",
    name: "Insider Threat Summary",
    description: "Internal fraud and unauthorized access",
    category: "fraud-incidents",
    type: "Compliance",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "threatType", label: "Threat Type", type: "multiselect", required: false, options: [{ value: "data-theft", label: "Data Theft" }, { value: "unauthorized", label: "Unauthorized Access" }, { value: "embezzlement", label: "Embezzlement" }, { value: "sabotage", label: "Sabotage" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "Excel"],
    tags: ["fraud", "insider", "threats"]
  },

  // ===== 7. Alert & Case Management (10 reports) =====
  {
    id: "alert-performance",
    name: "Alert Performance Metrics",
    description: "Alert generation and handling statistics",
    category: "alert-case",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "metric", label: "Metric", type: "multiselect", required: false, options: [{ value: "volume", label: "Volume" }, { value: "ttr", label: "Time to Review" }, { value: "escalation", label: "Escalation Rate" }, { value: "false-positive", label: "False Positive Rate" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["alerts", "performance", "metrics"]
  },
  {
    id: "alert-queue-status",
    name: "Alert Queue Status",
    description: "Current alert queue status and backlog",
    category: "alert-case",
    type: "Operational",
    parameters: [
      { name: "queue", label: "Queue", type: "multiselect", required: false, options: [{ value: "new", label: "New Alerts" }, { value: "assigned", label: "Assigned" }, { value: "escalated", label: "Escalated" }, { value: "pending", label: "Pending Info" }] },
      { name: "priority", label: "Priority", type: "multiselect", required: false, options: [{ value: "low", label: "Low" }, { value: "medium", label: "Medium" }, { value: "high", label: "High" }, { value: "critical", label: "Critical" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["alerts", "queue", "status"]
  },
  {
    id: "case-workload-analysis",
    name: "Case Workload Analysis",
    description: "Analyst case load distribution",
    category: "alert-case",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "analyst", label: "Analyst", type: "select", required: false, options: [] }, // Populated dynamically
      { name: "groupBy", label: "Group By", type: "select", required: false, options: [{ value: "analyst", label: "Analyst" }, { value: "team", label: "Team" }, { value: "priority", label: "Priority" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["cases", "workload", "analysis"]
  },
  {
    id: "case-resolution-time",
    name: "Case Resolution Time Analysis",
    description: "Time to resolve cases by type and priority",
    category: "alert-case",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "caseType", label: "Case Type", type: "multiselect", required: false, options: [{ value: "aml", label: "AML Investigation" }, { value: "fraud", label: "Fraud Investigation" }, { value: "sanctions", label: "Sanctions Review" }] },
      { name: "sla", label: "Include SLA Breaches", type: "checkbox", required: false, defaultValue: true }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["cases", "resolution", "time"]
  },
  {
    id: "case-quality-review",
    name: "Case Quality Review",
    description: "Quality assessment of completed cases",
    category: "alert-case",
    type: "Compliance",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "reviewType", label: "Review Type", type: "select", required: false, options: [{ value: "random", label: "Random Sample" }, { value: "high-value", label: "High Value" }, { value: "escalated", label: "Escalated" }] },
      { name: "minScore", label: "Min Quality Score", type: "number", required: false, min: 0, max: 100 }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "Excel"],
    tags: ["cases", "quality", "review"]
  },
  {
    id: "escalation-analysis",
    name: "Escalation Analysis",
    description: "Escalation patterns and outcomes",
    category: "alert-case",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "escalationReason", label: "Reason", type: "multiselect", required: false, options: [{ value: "complexity", label: "Complexity" }, { value: "value", label: "High Value" }, { value: "regulatory", label: "Regulatory" }, { value: "jurisdiction", label: "Jurisdiction" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["escalation", "analysis", "cases"]
  },
  {
    id: "sar-conversion-rate",
    name: "SAR Conversion Rate",
    description: "Cases converted to SAR filings",
    category: "alert-case",
    type: "Regulatory",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "initialSource", label: "Initial Source", type: "multiselect", required: false, options: [{ value: "alert", label: "Alert" }, { value: "manual", label: "Manual Review" }, { value: "system", label: "System Detection" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["sar", "conversion", "rate"]
  },
  {
    id: "alert-trend-analysis",
    name: "Alert Trend Analysis",
    description: "Alert volume trends over time",
    category: "alert-case",
    type: "Analytical",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "granularity", label: "Granularity", type: "select", required: false, options: [{ value: "daily", label: "Daily" }, { value: "weekly", label: "Weekly" }, { value: "monthly", label: "Monthly" }] },
      { name: "ruleType", label: "Rule Type", type: "multiselect", required: false, options: [{ value: "velocity", label: "Velocity" }, { value: "amount", label: "Amount" }, { value: "pattern", label: "Pattern" }, { value: "sanctions", label: "Sanctions" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["alerts", "trends", "analysis"]
  },
  {
    id: "analyst-productivity",
    name: "Analyst Productivity Report",
    description: "Individual analyst performance metrics",
    category: "alert-case",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "analyst", label: "Analyst", type: "select", required: false, options: [] }, // Populated dynamically
      { name: "metrics", label: "Metrics", type: "multiselect", required: false, options: [{ value: "cases", label: "Cases Closed" }, { value: "quality", label: "Quality Score" }, { value: "time", label: "Avg Handle Time" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["analyst", "productivity", "performance"]
  },
  {
    id: "case-aging-report",
    name: "Case Aging Report",
    description: "Cases by age and status",
    category: "alert-case",
    type: "Operational",
    parameters: [
      { name: "asOfDate", label: "As of Date", type: "date", required: true },
      { name: "ageBuckets", label: "Age Buckets", type: "multiselect", required: false, options: [{ value: "0-7", label: "0-7 Days" }, { value: "8-14", label: "8-14 Days" }, { value: "15-30", label: "15-30 Days" }, { value: "30+", label: "30+ Days" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["cases", "aging", "backlog"]
  },

  // ===== 8. Rule Engine Performance (6 reports) =====
  {
    id: "rule-hit-rate",
    name: "Rule Hit Rate Analysis",
    description: "Rule triggering frequency and effectiveness",
    category: "rule-engine",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "ruleType", label: "Rule Type", type: "multiselect", required: false, options: [{ value: "velocity", label: "Velocity" }, { value: "amount", label: "Amount" }, { value: "pattern", label: "Pattern" }, { value: "sanctions", label: "Sanctions" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["rules", "hit-rate", "performance"]
  },
  {
    id: "rule-false-positive",
    name: "Rule False Positive Rate",
    description: "False positive analysis by rule",
    category: "rule-engine",
    type: "Analytical",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "threshold", label: "FP Rate Threshold %", type: "number", required: false, min: 0, max: 100, defaultValue: 50 }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["rules", "false-positives", "analysis"]
  },
  {
    id: "rule-performance-trend",
    name: "Rule Performance Trend",
    description: "Rule effectiveness over time",
    category: "rule-engine",
    type: "Analytical",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "granularity", label: "Granularity", type: "select", required: false, options: [{ value: "daily", label: "Daily" }, { value: "weekly", label: "Weekly" }, { value: "monthly", label: "Monthly" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["rules", "trends", "performance"]
  },
  {
    id: "rule-overlap-analysis",
    name: "Rule Overlap Analysis",
    description: "Multiple rules triggering on same transactions",
    category: "rule-engine",
    type: "Analytical",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "minRules", label: "Min Overlapping Rules", type: "number", required: false, defaultValue: 2, min: 2 }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["rules", "overlap", "analysis"]
  },
  {
    id: "rule-tuning-recommendations",
    name: "Rule Tuning Recommendations",
    description: "Suggested threshold adjustments",
    category: "rule-engine",
    type: "Analytical",
    parameters: [
      { name: "dateRange", label: "Analysis Period", type: "daterange", required: true },
      { name: "targetFP", label: "Target FP Rate %", type: "number", required: false, min: 0, max: 100, defaultValue: 10 }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "Excel"],
    tags: ["rules", "tuning", "recommendations"]
  },
  {
    id: "rule-execution-time",
    name: "Rule Execution Time Report",
    description: "Performance and latency metrics",
    category: "rule-engine",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "metric", label: "Metric", type: "select", required: false, options: [{ value: "avg", label: "Average" }, { value: "p95", label: "95th Percentile" }, { value: "p99", label: "99th Percentile" }, { value: "max", label: "Maximum" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["rules", "performance", "execution"]
  },

  // ===== 9. Risk Scoring & Models (8 reports) =====
  {
    id: "risk-score-distribution",
    name: "Risk Score Distribution",
    description: "Customer risk score breakdown",
    category: "risk-scoring",
    type: "Analytical",
    parameters: [
      { name: "asOfDate", label: "As of Date", type: "date", required: true },
      { name: "customerType", label: "Customer Type", type: "multiselect", required: false, options: [{ value: "individual", label: "Individual" }, { value: "business", label: "Business" }, { value: "pep", label: "PEP" }] },
      { name: "scoreRange", label: "Score Range", type: "select", required: false, options: [{ value: "all", label: "All" }, { value: "0-25", label: "0-25 (Low)" }, { value: "26-50", label: "26-50 (Medium)" }, { value: "51-75", label: "51-75 (High)" }, { value: "76-100", label: "76-100 (Extreme)" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["risk", "scoring", "distribution"]
  },
  {
    id: "risk-model-performance",
    name: "Risk Model Performance",
    description: "Model accuracy and validation metrics",
    category: "risk-scoring",
    type: "Analytical",
    parameters: [
      { name: "dateRange", label: "Validation Period", type: "daterange", required: true },
      { name: "model", label: "Model", type: "select", required: false, options: [{ value: "aml", label: "AML Risk" }, { value: "fraud", label: "Fraud Risk" }, { value: "credit", label: "Credit Risk" }] },
      { name: "metrics", label: "Metrics", type: "multiselect", required: false, options: [{ value: "auc", label: "AUC-ROC" }, { value: "precision", label: "Precision" }, { value: "recall", label: "Recall" }, { value: "f1", label: "F1 Score" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "Excel"],
    tags: ["risk", "model", "performance"]
  },
  {
    id: "risk-score-migration",
    name: "Risk Score Migration Analysis",
    description: "Risk score changes over time",
    category: "risk-scoring",
    type: "Analytical",
    parameters: [
      { name: "fromDate", label: "From Date", type: "date", required: true },
      { name: "toDate", label: "To Date", type: "date", required: true },
      { name: "migrationType", label: "Migration Type", type: "multiselect", required: false, options: [{ value: "upgrade", label: "Risk Upgrade" }, { value: "downgrade", label: "Risk Downgrade" }, { value: "stable", label: "Stable" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["risk", "migration", "analysis"]
  },
  {
    id: "risk-factor-breakdown",
    name: "Risk Factor Breakdown",
    description: "Contributing risk factors analysis",
    category: "risk-scoring",
    type: "Analytical",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "factorType", label: "Factor Type", type: "multiselect", required: false, options: [{ value: "geographic", label: "Geographic" }, { value: "behavioral", label: "Behavioral" }, { value: "product", label: "Product" }, { value: "customer", label: "Customer" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["risk", "factors", "breakdown"]
  },
  {
    id: "model-drift-detection",
    name: "Model Drift Detection",
    description: "Model performance degradation alerts",
    category: "risk-scoring",
    type: "Analytical",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "driftType", label: "Drift Type", type: "multiselect", required: false, options: [{ value: "concept", label: "Concept Drift" }, { value: "data", label: "Data Drift" }, { value: "prediction", label: "Prediction Drift" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "Excel"],
    tags: ["model", "drift", "detection"]
  },
  {
    id: "risk-concentration",
    name: "Risk Concentration Analysis",
    description: "Risk concentration by segment",
    category: "risk-scoring",
    type: "Analytical",
    parameters: [
      { name: "asOfDate", label: "As of Date", type: "date", required: true },
      { name: "dimension", label: "Dimension", type: "select", required: false, options: [{ value: "product", label: "Product" }, { value: "geography", label: "Geography" }, { value: "customer", label: "Customer Segment" }, { value: "channel", label: "Channel" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["risk", "concentration", "analysis"]
  },
  {
    id: "high-risk-customer",
    name: "High Risk Customer Report",
    description: "Current high-risk customer population",
    category: "risk-scoring",
    type: "Operational",
    parameters: [
      { name: "asOfDate", label: "As of Date", type: "date", required: true },
      { name: "minScore", label: "Min Risk Score", type: "number", required: false, defaultValue: 75, min: 0, max: 100 },
      { name: "includeHistory", label: "Include History", type: "checkbox", required: false, defaultValue: false }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["risk", "high-risk", "customers"]
  },
  {
    id: "risk-scoring-backtesting",
    name: "Risk Scoring Backtesting",
    description: "Historical model performance validation",
    category: "risk-scoring",
    type: "Analytical",
    parameters: [
      { name: "dateRange", label: "Backtest Period", type: "daterange", required: true },
      { name: "model", label: "Model Version", type: "select", required: false, options: [{ value: "v1", label: "Version 1.0" }, { value: "v2", label: "Version 2.0" }, { value: "current", label: "Current" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "Excel"],
    tags: ["risk", "backtesting", "validation"]
  },

  // ===== 10. Regulatory Submission (4 reports) =====
  {
    id: "reg-fincen-sar",
    name: "FinCEN SAR Filing Report",
    description: "SAR filing status and tracking",
    category: "regulatory-submission",
    type: "Regulatory",
    parameters: [
      { name: "dateRange", label: "Filing Period", type: "daterange", required: true },
      { name: "status", label: "Status", type: "multiselect", required: false, options: [{ value: "draft", label: "Draft" }, { value: "filed", label: "Filed" }, { value: "acknowledged", label: "Acknowledged" }, { value: "corrected", label: "Corrected" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "Excel"],
    tags: ["regulatory", "fincen", "sar"]
  },
  {
    id: "reg-fincen-ctr",
    name: "FinCEN CTR Filing Report",
    description: "CTR filing status and tracking",
    category: "regulatory-submission",
    type: "Regulatory",
    parameters: [
      { name: "dateRange", label: "Filing Period", type: "daterange", required: true },
      { name: "status", label: "Status", type: "multiselect", required: false, options: [{ value: "draft", label: "Draft" }, { value: "filed", label: "Filed" }, { value: "acknowledged", label: "Acknowledged" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "Excel"],
    tags: ["regulatory", "fincen", "ctr"]
  },
  {
    id: "reg-314a-compliance",
    name: "314(a) Compliance Report",
    description: "FinCEN 314(a) information sharing",
    category: "regulatory-submission",
    type: "Regulatory",
    parameters: [
      { name: "dateRange", label: "Request Period", type: "daterange", required: true },
      { name: "responseStatus", label: "Response Status", type: "multiselect", required: false, options: [{ value: "received", label: "Received" }, { value: "searched", label: "Searched" }, { value: "positive", label: "Positive Match" }, { value: "negative", label: "No Match" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "Excel"],
    tags: ["regulatory", "314a", "compliance"]
  },
  {
    id: "reg-314b-compliance",
    name: "314(b) Information Sharing",
    description: "Voluntary information sharing report",
    category: "regulatory-submission",
    type: "Regulatory",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "sharingType", label: "Sharing Type", type: "multiselect", required: false, options: [{ value: "outgoing", label: "Outgoing" }, { value: "incoming", label: "Incoming" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "Excel"],
    tags: ["regulatory", "314b", "sharing"]
  },

  // ===== 11. Compliance Management (4 reports) =====
  {
    id: "compliance-program-assessment",
    name: "Compliance Program Assessment",
    description: "Overall compliance program health",
    category: "compliance-management",
    type: "Compliance",
    parameters: [
      { name: "assessmentDate", label: "Assessment Date", type: "date", required: true },
      { name: "area", label: "Focus Area", type: "multiselect", required: false, options: [{ value: "bsa", label: "BSA/AML" }, { value: "ofac", label: "OFAC" }, { value: "fraud", label: "Fraud" }, { value: "privacy", label: "Privacy" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "Excel"],
    tags: ["compliance", "assessment", "program"]
  },
  {
    id: "compliance-training-report",
    name: "Compliance Training Report",
    description: "Staff training completion and effectiveness",
    category: "compliance-management",
    type: "Compliance",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "department", label: "Department", type: "multiselect", required: false, options: [{ value: "compliance", label: "Compliance" }, { value: "operations", label: "Operations" }, { value: "retail", label: "Retail" }, { value: "lending", label: "Lending" }] },
      { name: "trainingType", label: "Training Type", type: "multiselect", required: false, options: [{ value: "annual", label: "Annual" }, { value: "new-hire", label: "New Hire" }, { value: "specialized", label: "Specialized" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "Excel"],
    tags: ["compliance", "training", "staff"]
  },
  {
    id: "compliance-audit-findings",
    name: "Compliance Audit Findings",
    description: "Internal and external audit results",
    category: "compliance-management",
    type: "Compliance",
    parameters: [
      { name: "dateRange", label: "Audit Period", type: "daterange", required: true },
      { name: "auditType", label: "Audit Type", type: "multiselect", required: false, options: [{ value: "internal", label: "Internal" }, { value: "external", label: "External" }, { value: "regulatory", label: "Regulatory" }] },
      { name: "status", label: "Finding Status", type: "multiselect", required: false, options: [{ value: "open", label: "Open" }, { value: "in-progress", label: "In Progress" }, { value: "closed", label: "Closed" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "Excel"],
    tags: ["compliance", "audit", "findings"]
  },
  {
    id: "compliance-policy-attestation",
    name: "Policy Attestation Report",
    description: "Employee policy acknowledgment status",
    category: "compliance-management",
    type: "Compliance",
    parameters: [
      { name: "asOfDate", label: "As of Date", type: "date", required: true },
      { name: "policy", label: "Policy", type: "multiselect", required: false, options: [{ value: "code", label: "Code of Conduct" }, { value: "aml", label: "AML Policy" }, { value: "privacy", label: "Privacy Policy" }, { value: "security", label: "Information Security" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "Excel"],
    tags: ["compliance", "policy", "attestation"]
  },

  // ===== 12. Data Quality (5 reports) =====
  {
    id: "data-quality-scorecard",
    name: "Data Quality Scorecard",
    description: "Overall data quality metrics",
    category: "data-quality",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "dimension", label: "Quality Dimension", type: "multiselect", required: false, options: [{ value: "completeness", label: "Completeness" }, { value: "accuracy", label: "Accuracy" }, { value: "consistency", label: "Consistency" }, { value: "timeliness", label: "Timeliness" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["data", "quality", "scorecard"]
  },
  {
    id: "data-missing-fields",
    name: "Missing Critical Fields Report",
    description: "Records with missing required data",
    category: "data-quality",
    type: "Operational",
    parameters: [
      { name: "asOfDate", label: "As of Date", type: "date", required: true },
      { name: "entity", label: "Entity Type", type: "multiselect", required: false, options: [{ value: "customer", label: "Customer" }, { value: "transaction", label: "Transaction" }, { value: "account", label: "Account" }] },
      { name: "field", label: "Critical Field", type: "multiselect", required: false, options: [{ value: "ssn", label: "SSN/TIN" }, { value: "address", label: "Address" }, { value: "dob", label: "Date of Birth" }, { value: "phone", label: "Phone" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["data", "missing", "fields"]
  },
  {
    id: "data-duplicate-detection",
    name: "Duplicate Detection Report",
    description: "Potential duplicate records",
    category: "data-quality",
    type: "Operational",
    parameters: [
      { name: "asOfDate", label: "As of Date", type: "date", required: true },
      { name: "entity", label: "Entity Type", type: "select", required: true, options: [{ value: "customer", label: "Customer" }, { value: "account", label: "Account" }, { value: "transaction", label: "Transaction" }] },
      { name: "matchConfidence", label: "Min Match Confidence %", type: "number", required: false, min: 0, max: 100, defaultValue: 85 }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["data", "duplicates", "detection"]
  },
  {
    id: "data-integrity-check",
    name: "Data Integrity Check",
    description: "Referential integrity validation",
    category: "data-quality",
    type: "Operational",
    parameters: [
      { name: "asOfDate", label: "As of Date", type: "date", required: true },
      { name: "checkType", label: "Check Type", type: "multiselect", required: false, options: [{ value: "orphan", label: "Orphan Records" }, { value: "circular", label: "Circular References" }, { value: "null", label: "Invalid Nulls" }] }
    ],
    supportsChart: false,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["data", "integrity", "validation"]
  },
  {
    id: "data-reconciliation",
    name: "Data Reconciliation Report",
    description: "System reconciliation and balancing",
    category: "data-quality",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "system", label: "System", type: "multiselect", required: false, options: [{ value: "core", label: "Core Banking" }, { value: "aml", label: "AML System" }, { value: "card", label: "Card System" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["data", "reconciliation", "balancing"]
  },

  // ===== 13. Chargeback & Dispute (4 reports) =====
  {
    id: "chargeback-summary",
    name: "Chargeback Summary Report",
    description: "Chargeback volume and reasons analysis",
    category: "chargeback-dispute",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "reasonCode", label: "Reason Code", type: "multiselect", required: false, options: [{ value: "fraud", label: "Fraud" }, { value: "customer", label: "Customer Dispute" }, { value: "processing", label: "Processing Error" }, { value: "authorization", label: "Authorization" }] },
      { name: "cardBrand", label: "Card Brand", type: "multiselect", required: false, options: [{ value: "visa", label: "Visa" }, { value: "mastercard", label: "Mastercard" }, { value: "amex", label: "Amex" }, { value: "discover", label: "Discover" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["chargeback", "summary", "dispute"]
  },
  {
    id: "chargeback-ratio",
    name: "Chargeback Ratio Analysis",
    description: "Chargeback-to-transaction ratios",
    category: "chargeback-dispute",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "granularity", label: "Granularity", type: "select", required: false, options: [{ value: "monthly", label: "Monthly" }, { value: "quarterly", label: "Quarterly" }] },
      { name: "merchant", label: "Merchant", type: "select", required: false, options: [] } // Dynamic
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["chargeback", "ratio", "analysis"]
  },
  {
    id: "dispute-resolution",
    name: "Dispute Resolution Report",
    description: "Dispute outcomes and timelines",
    category: "chargeback-dispute",
    type: "Operational",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "outcome", label: "Outcome", type: "multiselect", required: false, options: [{ value: "won", label: "Won" }, { value: "lost", label: "Lost" }, { value: "arbitration", label: "Arbitration" }, { value: "pending", label: "Pending" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["dispute", "resolution", "outcomes"]
  },
  {
    id: "friendly-fraud-analysis",
    name: "Friendly Fraud Analysis",
    description: "Suspected friendly fraud patterns",
    category: "chargeback-dispute",
    type: "Analytical",
    parameters: [
      { name: "dateRange", label: "Period", type: "daterange", required: true },
      { name: "indicator", label: "Indicator", type: "multiselect", required: false, options: [{ value: "repeat", label: "Repeat Disputes" }, { value: "high-value", label: "High Value" }, { value: "digital", label: "Digital Goods" }] }
    ],
    supportsChart: true,
    supportsExport: ["PDF", "CSV", "Excel"],
    tags: ["friendly-fraud", "analysis", "dispute"]
  }
];

// Helper function to get reports by category
export const getReportsByCategory = (categoryId: string): ReportDefinition[] => {
  return REPORT_DEFINITIONS.filter(r => r.category === categoryId);
};

// Helper function to get report by ID
export const getReportById = (reportId: string): ReportDefinition | undefined => {
  return REPORT_DEFINITIONS.find(r => r.id === reportId);
};

// Helper function to search reports
export const searchReports = (query: string): ReportDefinition[] => {
  const lowerQuery = query.toLowerCase();
  return REPORT_DEFINITIONS.filter(r =>
    r.name.toLowerCase().includes(lowerQuery) ||
    r.description.toLowerCase().includes(lowerQuery) ||
    r.tags.some(t => t.toLowerCase().includes(lowerQuery))
  );
};
