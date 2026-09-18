import type { MultiAssetRiskSignal, MultiAssetTransaction, PageResult } from "./multiAsset";

export type MobileMoneyEntityType =
  | "CUSTOMER" | "WALLET" | "DEVICE" | "SIM" | "AGENT" | "MERCHANT" | "NETWORK_CLUSTER";

export type MobileMoneyEventType =
  | "CASH_IN" | "CASH_OUT" | "P2P_TRANSFER" | "MERCHANT_PAYMENT"
  | "BILL_PAYMENT" | "REFUND" | "REVERSAL";

export interface MobileMoneyContext {
  id: number;
  transactionId: number;
  externalTransactionId: string;
  customerId: number;
  customerName: string;
  walletReference: string;
  counterpartyWalletReference?: string;
  eventType: MobileMoneyEventType;
  amount: number;
  currency: string;
  executedAt: string;
  deviceFingerprint?: string;
  agentReference?: string;
  merchantReference?: string;
  cellId?: string;
  crossBorder: boolean;
  riskScore: number;
  decision: "ALLOW" | "ALERT" | "REVIEW" | "BLOCK";
  metadata: Record<string, unknown>;
}

export interface MobileMoneyRiskProfile {
  id: number;
  entityType: MobileMoneyEntityType;
  entityReference: string;
  riskScore: number;
  riskLevel: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  signalCount: number;
  firstSeenAt: string;
  lastSeenAt: string;
  lastAssessedAt: string;
  latestTransactionId?: number;
  attributes: Record<string, unknown>;
}

export interface MobileMoneyNetworkEdge {
  id: number;
  fromEntityType: MobileMoneyEntityType;
  fromEntityReference: string;
  toEntityType: MobileMoneyEntityType;
  toEntityReference: string;
  relationshipType: string;
  occurrenceCount: number;
  cumulativeAmount: number;
  currency?: string;
  firstSeenAt: string;
  lastSeenAt: string;
  latestTransactionId?: number;
}

export interface MobileMoneyNetwork {
  entityType: MobileMoneyEntityType;
  entityReference: string;
  riskProfile?: MobileMoneyRiskProfile;
  edges: MobileMoneyNetworkEdge[];
}

export interface MobileMoneyAssessment {
  transaction: MultiAssetTransaction;
  context: MobileMoneyContext;
  signals: MultiAssetRiskSignal[];
  riskProfiles: MobileMoneyRiskProfile[];
  networkEdges: MobileMoneyNetworkEdge[];
}

export type MobileMoneyPage<T> = PageResult<T>;
