// Types for Rules Management

export interface AmlRule {
  id?: number;
  ruleName: string;
  description?: string;
  ruleType: "JAVA_BEAN" | "SPEL" | "DROOLS_DRL";
  ruleExpression: string;
  priority: number;
  enabled: boolean;
  action: "BLOCK" | "HOLD" | "ALERT" | "ALLOW";
  score?: number;
  pspId?: number;
  version?: string;
  parameters?: Record<string, any>;
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
