export type MarketOrderSide = "BUY" | "SELL";
export type MarketOrderStatus = "OPEN" | "PARTIALLY_FILLED" | "FILLED" | "CANCELLED" | "REJECTED";
export type MarketSignalStatus = "OPEN" | "UNDER_REVIEW" | "DISMISSED" | "ESCALATED";

export interface MarketOrder {
  id: number;
  externalOrderId: string;
  customerId: number;
  customerName: string;
  accountReference: string;
  instrumentId: string;
  symbol?: string;
  side: MarketOrderSide;
  orderType: string;
  quantity: number;
  executedQuantity: number;
  limitPrice?: number;
  currency: string;
  venue?: string;
  status: MarketOrderStatus;
  placedAt: string;
  cancelledAt?: string;
}

export interface MarketSurveillanceSignal {
  id: number;
  customerId: number;
  customerName: string;
  orderId?: number;
  executionId?: number;
  scenarioCode: string;
  signalType: "MARKET_ABUSE";
  severity: string;
  score: number;
  description: string;
  evidence: Record<string, unknown>;
  status: MarketSignalStatus;
  createdAt: string;
  reviewedAt?: string;
  reviewedBy?: string;
  reviewNotes?: string;
}

export interface SurveillanceResult<T> {
  record: T;
  signals: MarketSurveillanceSignal[];
}

export interface FixSession {
  sessionId: string;
  pspId: number;
  connectionType: string;
  enabled: boolean;
  loggedOn: boolean;
  expectedSenderSequence?: number;
  expectedTargetSequence?: number;
}

export interface FixMessageEvent {
  id: number;
  sessionId: string;
  direction: string;
  messageType: string;
  messageSequenceNumber: number;
  sendingTime?: string;
  businessReference?: string;
  messageHash: string;
  sanitizedFields: Record<string, unknown>;
  outcome: "RECEIVED" | "ACCEPTED" | "REJECTED" | "IGNORED";
  errorCode?: string;
  errorMessage?: string;
  marketOrderId?: number;
  marketExecutionId?: number;
  receivedAt: string;
  processedAt?: string;
}

export interface FixMessagePage {
  content: FixMessageEvent[];
  totalElements: number;
  totalPages: number;
  number: number;
}
