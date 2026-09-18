// Types for Rules Management

// The backend returns `name` (RuleDefinition.name); legacy frontend code uses
// `ruleName`. Both are accepted so historical screens keep working.
export type RuleCategory   = "AML" | "FRAUD" | "SCREENING";
export type RuleAppliesTo  = "Transaction" | "User";
export type RuleAction     = "BLOCK" | "HOLD" | "ALERT" | "ALLOW";
export type RuleEngineType = "JAVA_BEAN" | "SPEL" | "DROOLS_DRL";
export type RuleLifecycleStatus = "DRAFT" | "PENDING_APPROVAL" | "APPROVED" | "SCHEDULED" | "ACTIVE" | "REJECTED" | "SUPERSEDED" | "RETIRED";
export type RuleChangeType = "CREATE" | "UPDATE" | "ENABLE" | "DISABLE" | "RETIRE" | "ROLLBACK";

export interface AmlRule {
  id?: number;
  /** Canonical server-side field. */
  name?: string;
  /** Legacy alias kept so existing screens keep rendering. */
  ruleName?: string;
  description?: string;
  ruleType: RuleEngineType;
  ruleExpression?: string;
  priority: number;
  enabled: boolean;
  action: RuleAction;
  score?: number;
  pspId?: number;
  version?: string;
  parameters?: Record<string, any> | string;
  lifecycleStatus?: RuleLifecycleStatus;
  currentVersionNumber?: number;
  currentVersionId?: number;
  pendingVersionId?: number;

  // System-managed catalog metadata (V134/V135)
  isSystemManaged?: boolean;
  category?: RuleCategory;
  ruleSubtype?: string;
  appliesTo?: RuleAppliesTo;
  typology?: string;
  checksFor?: string;
  externalCode?: string;
  recommended?: boolean;
  sampleUseCase?: string;

  createdAt?: string;
  updatedAt?: string;
  createdBy?: number;
  createdByUser?: {
    id: number;
    username: string;
    role?: { name: string };
    pspId?: number;
  };
  isSuperAdmin?: boolean;
  pspThemeColor?: string;
}

export interface RuleVersion {
  id: number;
  ruleId: number;
  ruleName: string;
  versionNumber: number;
  lifecycleStatus: RuleLifecycleStatus;
  changeType: RuleChangeType;
  snapshot: Record<string, unknown>;
  contentHash: string;
  changeSummary?: string;
  pspId?: number;
  createdById: number;
  createdByUsername: string;
  createdAt: string;
  effectiveFrom?: string;
  reviewedById?: number;
  reviewedByUsername?: string;
  reviewedAt?: string;
  reviewReason?: string;
  activatedAt?: string;
}

export interface VelocityRule {
  id?: number;
  ruleName: string;
  description?: string;
  maxTransactions: number;
  maxAmount: number;
  timeWindowMinutes: number;
  riskLevel: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  triggerCount?: number;
  lastTriggeredAt?: string;
  status: "ACTIVE" | "INACTIVE";
  createdAt?: string;
  updatedAt?: string;
  createdBy?: number;
  createdByUser?: {
    id: number;
    username: string;
    role?: {
      name: string;
    };
    pspId?: number;
  };
  isSuperAdmin?: boolean;
  pspThemeColor?: string;
}

export interface RiskThreshold {
  id?: number;
  thresholdName: string;
  riskLevel: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  minScore?: number;
  maxScore?: number;
  action: string;
  enabled: boolean;
}

export interface RuleEffectiveness {
  ruleId: number;
  ruleName: string;
  totalExecutions: number;
  triggeredCount: number;
  falsePositiveRate: number;
  truePositiveRate: number;
  averageExecutionTime: number;
  lastExecutedAt?: string;
}
