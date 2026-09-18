export type AssetClass = "BANKING" | "SECURITIES" | "E_MONEY" | "TOKENIZED_FIAT" | "CRYPTO";

export type ProductDomain =
  | "BANKING"
  | "SECURITIES_AML"
  | "SECURITIES_MARKET_SURVEILLANCE"
  | "E_MONEY_MOBILE_MONEY"
  | "PREPAID_CLOSED_LOOP"
  | "VIRTUAL_ASSET"
  | "TOKENIZED_FIAT_CBDC";

export type FinancialCrimeSignalType =
  | "AML"
  | "MARKET_ABUSE"
  | "SANCTIONS"
  | "FRAUD"
  | "CYBER"
  | "CRYPTO_EXPOSURE";

export type AssetAccountType =
  | "BANK_ACCOUNT"
  | "BROKERAGE"
  | "MOBILE_MONEY"
  | "DIGITAL_WALLET"
  | "PREPAID_VALUE"
  | "TOKENIZED_FIAT_ACCOUNT"
  | "CRYPTO_WALLET"
  | "EXCHANGE_ACCOUNT";

export type MultiAssetTransactionType =
  | "DEPOSIT"
  | "WITHDRAWAL"
  | "TRANSFER"
  | "TOP_UP"
  | "PAYMENT"
  | "BUY"
  | "SELL"
  | "REFUND"
  | "BRIDGE"
  | "SWAP";

export interface MultiAssetCustomer {
  id: number;
  externalCustomerId: string;
  customerType: "INDIVIDUAL" | "ENTITY";
  displayName: string;
  countryCode?: string;
  riskTier: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  kycStatus: "PENDING" | "VERIFIED" | "REVIEW" | "REJECTED";
  lastReviewedAt?: string;
  createdAt: string;
}

export interface AssetAccount {
  id: number;
  externalAccountId: string;
  assetClass: AssetClass;
  productDomain: ProductDomain;
  accountType: AssetAccountType;
  providerName?: string;
  currency?: string;
  countryCode?: string;
  publicAddress?: string;
  status: string;
  metadata: Record<string, unknown>;
}

export type CustomerRelationshipType =
  | "UBO"
  | "DIRECTOR"
  | "SHAREHOLDER"
  | "SIGNATORY"
  | "CONTROLLING_PERSON"
  | "RELATED_CUSTOMER";

export interface CustomerRelationship {
  id: number;
  relatedCustomerId: number;
  relatedCustomerExternalId: string;
  relatedCustomerName: string;
  relationshipType: CustomerRelationshipType;
  ownershipPercentage?: number;
  metadata: Record<string, unknown>;
  createdAt: string;
}

export interface MultiAssetRiskSignal {
  id: number;
  transactionId: number;
  customerId: number;
  signalCode: string;
  signalType: FinancialCrimeSignalType;
  productDomain: ProductDomain;
  severity: string;
  scoreImpact: number;
  description: string;
  evidence: Record<string, unknown>;
  createdAt: string;
}

export interface MultiAssetTransaction {
  id: number;
  externalTransactionId: string;
  customerId: number;
  assetClass: AssetClass;
  productDomain: ProductDomain;
  sourceProductDomain?: ProductDomain;
  destinationProductDomain?: ProductDomain;
  transactionType: MultiAssetTransactionType;
  amount: number;
  fiatEquivalentUsd?: number;
  currency: string;
  assetSymbol?: string;
  executedAt: string;
  counterpartyReference?: string;
  sourceNetwork?: string;
  destinationNetwork?: string;
  travelRuleRequired: boolean;
  travelRuleStatus: "NOT_REQUIRED" | "COMPLETE" | "INCOMPLETE" | "PENDING_VERIFICATION";
  riskScore: number;
  decision: "ALLOW" | "ALERT" | "REVIEW" | "BLOCK";
  signals: MultiAssetRiskSignal[];
}

export interface Customer360 {
  customer: MultiAssetCustomer;
  relationships: CustomerRelationship[];
  accounts: AssetAccount[];
  recentTransactions: MultiAssetTransaction[];
  recentSignals: MultiAssetRiskSignal[];
  volumeByAssetClass: Partial<Record<AssetClass, number>>;
  transactionCountByAssetClass: Partial<Record<AssetClass, number>>;
  volumeByProductDomain: Partial<Record<ProductDomain, number>>;
  transactionCountByProductDomain: Partial<Record<ProductDomain, number>>;
  topCounterparties: Array<{ counterpartyReference: string; volume: number }>;
  compositeRiskScore: number;
}

export interface PageResult<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
