// Common types used across the application

export interface User {
  id: number;
  username: string;
  email: string;
  firstName?: string;
  lastName?: string;
  role?: Role;
  pspId?: number;
  active: boolean;
}

export interface Role {
  id: number;
  name: string;
  description?: string;
  permissions?: string[];
  global: boolean;
  pspId?: number;
}

export interface Case {
  id: number;
  caseReference: string;
  status: CaseStatus;
  priority: Priority;
  description: string;
  assignedTo?: User;
  createdBy: User;
  createdAt: string;
  updatedAt: string;
  slaDeadline?: string;
  daysOpen?: number;
}

export type CaseStatus =
  | "NEW"
  | "ASSIGNED"
  | "INVESTIGATING"
  | "PENDING_REVIEW"
  | "RESOLVED"
  | "ESCALATED";

export type Priority = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";

export interface SarReport {
  id: number;
  sarReference: string;
  status: SarStatus;
  suspiciousActivityType: string;
  narrative: string;
  jurisdiction: string;
  sarType: string;
  createdBy: User;
  createdAt: string;
  filedAt?: string;
  filingReference?: string;
}

export type SarStatus = "DRAFT" | "REVIEW" | "APPROVED" | "FILED" | "REJECTED";

export interface Alert {
  id: number;
  alertType: string;
  priority: Priority;
  status: AlertStatus;
  transactionId?: number;
  caseId?: number;
  description: string;
  createdAt: string;
  resolvedAt?: string;
}

export type AlertStatus = "OPEN" | "INVESTIGATING" | "RESOLVED";

export interface Transaction {
  id: number;
  pan: string;
  merchantId: string;
  terminalId?: string;
  amountCents: number;
  currency: string;
  txnTs: string;
  decision: "BLOCK" | "HOLD" | "ALERT" | "ALLOW";
  score?: number;
  scores?: {
    mlScore?: number;
    krsScore?: number;
    trsScore?: number;
    craScore?: number;
    anomalyScore?: number;
    fraudScore?: number;
    amlScore?: number;
  };
}

export interface Merchant {
  id: number;
  merchantId: string;
  businessName: string;
  mcc?: string;
  riskLevel?: "LOW" | "MEDIUM" | "HIGH";
  kycStatus?: string;
  contractStatus?: string;
  krs?: number;
  cra?: number;
}

export interface AuditLog {
  id: number;
  entityType: string;
  entityId: string;
  actionType: string;
  username: string;
  timestamp: string;
  reason?: string;
  details?: string;
  pspId?: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface DashboardStats {
  casesByStatus?: Record<CaseStatus, number>;
  sarsByStatus?: Record<SarStatus, number>;
  auditLast24h?: number;
  casesLast7d?: Record<string, number>;
  sarsLast7d?: Record<string, number>;
}

export interface Psp {
  pspId: number;
  pspCode: string;
  legalName: string;
  tradingName?: string;
  primaryColor?: string;
  secondaryColor?: string;
  accentColor?: string;
}
