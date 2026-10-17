// Common types used across the application

export interface User {
  id: number;
  username: string;
  email: string;
  firstName?: string;
  lastName?: string;
  role?: Role;
  pspId?: number;
  psp?: Psp;
  active?: boolean;
  enabled?: boolean;
  createdAt?: string;
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
  pspId?: number;
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
  alerts?: { id: number }[];
}

export interface CaseTimelineEvent {
  timestamp: string;
  type: string;
  description: string;
  data: Record<string, unknown>;
}

export interface CaseTimeline {
  caseId: number;
  caseReference: string;
  events: CaseTimelineEvent[];
  /** How the live pull of the TRANSACTION events' details went (R6). */
  transactionDetailsStatus?: EdgePullStatus;
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
  suspicionAroseAt?: string;
  suspicionTimestampSource?: string;
  filingDeadline?: string;
  deadlineWarningAt?: string;
  deadlinePolicyCode?: string;
  deadlineBreached?: boolean;
  reviewedAt?: string;
  reviewNotes?: string;
  filedAt?: string;
  filingReference?: string;
  /** The SAR's transactions by ID only; details are pulled live from the Edge. */
  transactionReferences?: TransactionReference[];
  /** Set once the SAR is filed: the stored regulatory record (no Edge pull to view it). */
  filedDocument?: FiledDocumentSummary;
}

/**
 * Metadata of a filed SAR/STR/CTR document (R9). The document itself is the stored regulatory
 * record: download it from `GET /filed-documents/{id}/content` (audited, never pulled from the Edge).
 */
export interface FiledDocumentSummary {
  id: number;
  documentKind: "SAR" | "STR" | "CTR";
  workflowType: "SAR" | "CTR_DEADLINE";
  /** Id of the SAR or of the CTR compliance deadline. */
  workflowId: number;
  filingRef: string;
  documentFormat: string;
  contentType: string;
  fileName: string;
  /** SHA-256 hex of the document bytes (also sent as `X-Content-SHA256` on download). */
  contentSha256: string;
  contentLength: number;
  transactionCount: number;
  dataSource: "EDGE" | "CLOUD_LEGACY" | "NONE";
  regulatorReference?: string;
  filedBy?: string;
  filedAt: string;
  retainUntil: string;
}

/** `POST /sar/{id}/file` and `POST /ctr/{id}/file` body. */
export interface FileFilingRequest {
  /** The regulator's reference, when already known at filing time. */
  regulatorReference?: string;
}

/** Answer of a successful filing. */
export interface FilingResult {
  workflowType: "SAR" | "CTR_DEADLINE";
  workflowId: number;
  status: "FILED";
  document: FiledDocumentSummary;
}

/**
 * A refused filing: nothing was filed and nothing changed. `retryable` refusals (Edge offline,
 * partial answer, busy, timeout) can be retried as they are.
 */
export interface FilingRefusal {
  code:
    | "FILING_NOT_ALLOWED"
    | "FILING_NOT_FOUND"
    | "FILING_STATE_CONFLICT"
    | "ALREADY_FILED"
    | "FILING_CONCURRENT_CHANGE"
    | "FILING_INCOMPLETE"
    | "EDGE_PULL_FAILED"
    | "CTR_BELOW_THRESHOLD"
    | "FILING_STORE_FAILED";
  httpStatus: number;
  message: string;
  retryable: boolean;
  filingRef?: string;
  /** The Edge's §7.11 error when the pull failed as a whole. */
  edgeError?: EdgeDataError;
  unavailableSites?: Array<{ siteId?: string; code: EdgeDataErrorCode }>;
  /** Transactions no answering site returned. */
  missingCount?: number;
}

/** `GET /filed-documents` page. */
export interface FiledDocumentPage {
  content: FiledDocumentSummary[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

/**
 * How a workflow record (alert, case, SAR) references a transaction: by ID only. The cloud holds
 * no transaction details; they are pulled live from the PSP's Edge.
 */
export interface TransactionReference {
  /** Legacy cloud transaction id (legacy ingest rows only). */
  transactionId?: number;
  /** Decision record id. */
  edgeAssessmentId?: string;
  /** The PSP's own transaction id. */
  txnRef?: string;
  /** Edge Site holding the transaction. */
  siteId?: string;
}

export type SarStatus = "DRAFT" | "REVIEW" | "APPROVED" | "FILED" | "REJECTED";

export interface Alert {
  id: number;
  alertType: string;
  priority: Priority;
  status: AlertStatus | string;
  transactionId?: number;
  /** Decision record id of the alerted transaction. */
  edgeAssessmentId?: string;
  /** The PSP's own transaction id. */
  txnRef?: string;
  /** Edge Site holding the transaction. */
  siteId?: string;
  caseId?: number;
  description: string;
  investigator?: string;
  createdAt: string;
  resolvedAt?: string;
  disposition?: string;
  dispositionReviewStatus?: string;
  firstReviewer?: string;
  secondReviewer?: string;
}

export type AlertStatus = "OPEN" | "INVESTIGATING" | "RESOLVED" | "pending_second_review";

export interface Transaction {
  /** Decision record id for Edge items; the legacy numeric id for CLOUD_LEGACY items. */
  id: number | string;
  /** Legacy cloud-ingest rows only; never sent for Edge items. */
  pan?: string;
  merchantId: string;
  terminalId?: string;
  amountCents: number;
  currency: string;
  txnTs: string;
  decision: "BLOCK" | "HOLD" | "ALERT" | "ALLOW";
  score?: number;
  /** Where the transaction fields came from (R6). */
  source?: TransactionSource;
  /** Decision record id. */
  edgeAssessmentId?: string;
  /** The PSP's own transaction id. */
  txnRef?: string;
  /** Edge Site holding the transaction. */
  siteId?: string;
  nodeId?: string;
  /** Evaluation time on the Edge (same instant as txnTs). */
  evaluatedAt?: string;
  /** Amount in minor units (same value as amountCents). */
  amountMinor?: number;
  mcc?: string;
  countryCode?: string;
  channel?: string;
  triggeredRuleIds?: number[];
  ctrRequired?: boolean;
  sanctionsHit?: boolean;
  origin?: "LIVE" | "BACKFILL";
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
  merchantId: number;
  pspId?: number;
  pspCode?: string;
  legalName: string;
  tradingName?: string;
  contactEmail?: string;
  country?: string;
  mcc?: string;
  mccDescription?: string;
  businessType?: string;
  status?: string;
  decision?: string;
  decisionReason?: string;
  riskScore?: number;
  riskLevel?: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL" | "UNKNOWN";
  kycStatus?: string;
  contractStatus?: string;
  krs?: number;
  cra?: number;
  complianceCaseId?: number;
  cbkEconomicSectorCode?: string;
  cbkSettlementAccountConfigured?: boolean;
  beneficialOwnerResults?: Array<{
    ownerId: number;
    fullName: string;
    isSanctioned: boolean;
    isPep: boolean;
  }>;
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
  nextCursor?: string | null;
  hasNext?: boolean;
}

export interface DashboardStats {
  casesByStatus?: Record<CaseStatus, number>;
  sarsByStatus?: Record<SarStatus, number>;
  auditLast24h?: number;
  casesLast7d?: Record<string, number>;
  sarsLast7d?: Record<string, number>;
}

export interface Psp {
  id?: number;
  pspId?: number;
  pspCode?: string;
  code?: string;
  name?: string;
  legalName?: string;
  tradingName?: string;
  primaryColor?: string;
  secondaryColor?: string;
  accentColor?: string;
}

// ── Edge data residency (R6): tunnel-backed endpoints ───────────────────────────────────────────

/** Source of a transaction's fields: pulled live from the PSP's Edge, or a legacy cloud row. */
export type TransactionSource = "EDGE" | "CLOUD_LEGACY";

/** Contract section 7.11 error codes the Console can receive from tunnel-backed endpoints. */
export type EdgeDataErrorCode =
  | "INVALID_PARAMS"
  | "INVALID_CURSOR"
  | "PAGE_LIMIT_EXCEEDED"
  | "OP_NOT_ALLOWED"
  | "TENANT_MISMATCH"
  | "SUPPORT_ACCESS_DISABLED"
  | "NOT_FOUND"
  | "STREAM_STALLED"
  | "EDGE_BUSY"
  | "CANCELLED"
  | "INTERNAL"
  | "LEDGER_UNAVAILABLE"
  | "EDGE_OFFLINE"
  | "DEADLINE_EXCEEDED"
  | "EDGE_TIMEOUT";

/** Error body of a tunnel-backed endpoint (HTTP status = httpStatus). */
export interface EdgeDataError {
  code: EdgeDataErrorCode;
  httpStatus: number;
  message: string;
  retryable: boolean;
  retryAfterMs?: number;
  /** Required for EDGE_OFFLINE: when the site was last heard from. */
  lastSeenAt?: string;
  siteId?: string;
  /** JSON pointer of the offending parameter (never its value). */
  param?: string;
}

/** A site that did not answer a pull. */
export interface EdgeUnavailableSite {
  siteId: string;
  code: EdgeDataErrorCode;
  lastSeenAt?: string;
}

/** List envelope of the tunnel-backed endpoints (contract section 7.14). */
export interface EdgeDataEnvelope<T> {
  items: T[];
  nextCursor: string | null;
  /** Some site did not answer, or reported an incomplete page. */
  incomplete: boolean;
  incompleteReason?: string;
  unavailableSites: EdgeUnavailableSite[];
  /** batchGet only: ids no answering site holds. */
  missing?: string[];
  source: TransactionSource;
}

/** Cloud-held decision record fields shown next to an Edge transaction detail. */
export interface EdgeDecisionRecord {
  assessmentId: string;
  edgeAssessmentId?: string;
  txnRef?: string;
  siteId?: string;
  edgeNodeId?: string;
  evaluatedAt?: string;
  decision?: string;
  score?: number;
  merchantId?: string;
  bundleVersion?: number;
  evaluator?: string;
  failClosedCode?: string;
  ctrRequired?: boolean;
  sanctionsHit?: boolean;
  triggeredRuleIds?: number[];
  ruleVersionIds?: number[];
  watchlistEntryIds?: string[];
  typologyCodes?: string[];
  recordedAt?: string;
}

/** GET /transactions/{id}: the Edge detail plus the cloud decision record. */
export interface EdgeTransactionDetail extends Transaction {
  reasons?: string[];
  features?: Record<string, unknown>;
  legalHold?: boolean;
  decisionRecord?: EdgeDecisionRecord;
  incomplete?: boolean;
}

/** GET /transactions/{edgeAssessmentId}/related item. */
export interface EdgeRelatedTransaction {
  link: "CARD" | "MERCHANT" | "DEVICE" | "ACCOUNT" | "IP";
  hop: 1 | 2;
  viaEdgeAssessmentId: string;
  transaction: Transaction;
  source: TransactionSource;
}

/** How the live pull behind a workflow view went (alert detail, SAR detail, case timeline/graph). */
export interface EdgePullStatus {
  source?: TransactionSource;
  /** OK, PARTIAL, NOT_REQUESTED, NONE or the error code of a failed pull. */
  state: "OK" | "PARTIAL" | "NOT_REQUESTED" | "NONE" | EdgeDataErrorCode;
  incomplete: boolean;
  unavailableSites: EdgeUnavailableSite[];
  missing: string[];
  error?: EdgeDataError;
}

/** Ledger health of one site (counts and timestamps only), or the state of a failed health pull. */
export interface EdgeLedgerHealth {
  state?: string;
  status?: "OK" | "DEGRADED" | "DOWN";
  schemaVersion?: number;
  rows?: { approxCount: number; oldestEvaluatedAt?: string; newestEvaluatedAt?: string };
  outbox?: { backlog: number; oldestPendingAt?: string; lastShippedAt?: string; failedAttempts24h: number };
  retention?: { years: number; configVersion?: number; lastPurgeAt?: string; lastPurgeDroppedPartitions: number;
    nextPurgeAt?: string };
  holds?: { active: number; heldRows: number; activeHoldDigest: string };
  storage?: { dbSizeBytes: number; diskFreeBytes: number };
  writeFailures24h?: number;
}

/** GET /edge-data/status */
export interface EdgeDataStatus {
  pspId: number;
  source: TransactionSource;
  tunnelEnabled: boolean;
  generatedAt: string;
  sites: Array<{
    siteId: string;
    tunnel: "ONLINE" | "OFFLINE" | "DISABLED";
    liveSessions: number;
    lastSeenAt?: string;
    nodes: Array<{
      edgeId: string;
      displayName?: string;
      status?: string;
      connected: boolean;
      inflight: number;
      lastPolledAt?: string;
      agentVersion?: string;
    }>;
    ledger: EdgeLedgerHealth;
  }>;
}

/** One row of GET /edge-data/access-log (contract section 7.15). */
export interface EdgeDataAccessLogEntry {
  auditId: string;
  requestedAt: string;
  completedAt?: string | null;
  actorKind: "PSP_USER" | "PLATFORM_USER" | "SYSTEM";
  actorUserId: string;
  actorRole: string;
  purpose: string;
  caseId?: string | null;
  alertId?: string | null;
  filingRef?: string | null;
  supportAccess: boolean;
  supportReason?: string | null;
  op: string;
  paramsSummary: Record<string, unknown>;
  paramsDigest: string;
  sitesContacted: string[];
  sitesAnswered: string[];
  itemCount: number;
  bytes: number;
  outcome?: string | null;
}

/** GET /edge-data/access-log page. */
export interface EdgeDataAccessLogPage extends PageResponse<EdgeDataAccessLogEntry> {
  pspId: number;
}
