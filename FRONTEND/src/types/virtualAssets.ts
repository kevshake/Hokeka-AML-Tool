import type { PageResult } from "./multiAsset";

export interface VaspEntry {
  id: number; legalName: string; tradingName?: string; jurisdiction: string; regulatorName?: string;
  licenceNumber?: string; licenceStatus: string; licenceValidUntil?: string; registrationNumber?: string;
  website?: string; travelRuleProtocols: string[]; sanctionsStatus: string;
  sanctionsScreenedAt?: string; sanctionsProvider?: string; sanctionsMatchCount: number; sanctionsNextScreeningAt: string;
  beneficialOwnership: Array<Record<string, unknown>>; walletClusters: Array<Record<string, unknown>>;
  riskScore: number; riskLevel: string; transferDecision: "PERMITTED" | "REVIEW" | "PROHIBITED";
  reviewNotes?: string; lastReviewedAt?: string; nextReviewAt?: string;
}

export interface VaspScreeningRecord {
  id: number; vaspId: number; vaspName: string; subjectName: string; subjectType: string;
  provider: string; available: boolean; status: string; matchCount: number;
  matches: Array<Record<string, unknown>>; evidence: Record<string, unknown>;
  screenedAt: string; retainUntil: string;
}

export interface CryptoWalletProfile {
  id: number; assetAccountId: number; customerId: number; customerName: string; vaspId?: number; vaspName?: string;
  walletAddress: string; network: string; addressLabel?: string; ownershipType: string; status: string;
  screeningIntervalHours: number; lastScreenedAt?: string; nextScreeningAt: string;
  latestRiskScore?: number; latestCategories: string[]; latestProvider?: string;
  latestProviderReference?: string; screeningAvailable: boolean; screeningStatus: string;
}

export interface WalletScreeningRecord {
  id: number; walletProfileId?: number; customerId: number; customerName: string; transactionId?: number;
  screenedAddress?: string; network?: string; triggerType: string; provider: string;
  providerReference?: string; available: boolean; riskScore?: number; categories: string[];
  directExposurePercent?: number; indirectExposurePercent?: number; maximumExposureDepth?: number;
  attributions: Array<Record<string, unknown>>; evidence: Record<string, unknown>;
  unavailableReason?: string; screenedAt: string; retainUntil: string;
}

export interface CryptoTransactionSummary {
  id: number; externalTransactionId: string; customerId: number; customerName: string; transactionType: string;
  amount: number; fiatEquivalentUsd?: number; currency: string; assetSymbol?: string; counterpartyReference?: string;
  sourceNetwork?: string; destinationNetwork?: string; riskScore: number; decision: string;
  travelRuleStatus: string; executedAt: string;
}

export interface TravelRulePolicy {
  id: number; jurisdiction: string; policyCode: string; thresholdUsd: number; appliesToAllTransfers: boolean;
  requiredFields: string[]; verifyOriginator: boolean; verifyBeneficiary: boolean;
  acceptedProtocols: string[]; retentionYears: number; effectiveFrom: string; effectiveUntil?: string;
  enabled: boolean; legalReference?: string;
}

export interface TravelRuleTransfer {
  id: number; transactionId: number; externalTransactionId: string; policyId?: number; policyCode?: string;
  originatorVaspId?: number; originatorVaspName?: string; beneficiaryVaspId?: number; beneficiaryVaspName?: string;
  jurisdiction: string; status: string; originatorVerification: string; beneficiaryVerification: string;
  originatorVerificationReference?: string; originatorVerifiedBy?: string; originatorVerifiedAt?: string;
  beneficiaryVerificationReference?: string; beneficiaryVerifiedBy?: string; beneficiaryVerifiedAt?: string;
  protocol?: string; payloadHash?: string; providerMessageId?: string; transmissionAttempts: number;
  transmittedAt?: string; acknowledgedAt?: string; failureReason?: string; retainUntil: string; createdAt: string;
}

export interface RegulatorGrant {
  id: number; regulatorName: string; jurisdiction: string; scopes: string[]; allowedIpAddresses: string[];
  expiresAt: string; revokedAt?: string; lastAccessedAt?: string; createdBy: string; createdAt: string; accessKey?: string;
}

export type VirtualAssetPage<T> = PageResult<T>;
