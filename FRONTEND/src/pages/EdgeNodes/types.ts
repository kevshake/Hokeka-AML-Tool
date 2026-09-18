// Shapes returned by the edge-admin API (BACKEND: EdgeAdminController + edge DTOs).
// These mirror the Java records/entities 1:1 — see
// docs/architecture/edge-channel-contract.md §5/§7 and the DTOs under dto/edge/.

/** One edge node as projected for the admin/PSP UI (`EdgeNodeView`). */
export interface EdgeNodeView {
  id: number;
  pspId: number;
  edgeId: string | null;
  displayName: string;
  status: EdgeNodeStatus;
  enrollmentCodeOutstanding: boolean;
  enrollmentCodeExpiresAt: string | null;
  edgeX25519PublicKey: string | null;
  edgeEd25519PublicKey: string | null;
  lastSeenAt: string | null;
  lastBundleVersion: number | null;
  activatedAt: string | null;
  suspendedAt: string | null;
  revokedAt: string | null;
  createdBy: string | null;
  approvedBy: string | null;
  hostname: string | null;
  agentVersion: string | null;
  createdAt: string | null;
  health: EdgeHealth;
  secondsSinceLastSeen: number | null;
}

export type EdgeNodeStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'ACTIVE'
  | 'SUSPENDED'
  | 'REVOKED'
  | 'REJECTED';

export type EdgeHealth =
  | 'AWAITING_APPROVAL'
  | 'AWAITING_ACTIVATION'
  | 'HEALTHY'
  | 'STALE'
  | 'NEVER_SEEN'
  | 'SUSPENDED'
  | 'REVOKED'
  | 'REJECTED';

/** One aggregate metrics window (`EdgeMetricsRecord`). Counts and latencies only. */
export interface EdgeMetricsRecord {
  windowStart: string;
  windowEnd: string;
  ruleBundleVersion: number;
  ruleBundleHash: string | null;
  totalEvaluated: number;
  allowed: number;
  alerted: number;
  held: number;
  blocked: number;
  ruleHitCountsJson: string | null;
  p50LatencyMicros: number;
  p95LatencyMicros: number;
  p99LatencyMicros: number;
  engineHealth: string | null;
  // The endpoint serialises the JPA entity, so a few non-UI fields ride along.
  [extra: string]: unknown;
}

/** `GET /edge/nodes/{id}` body. */
export interface EdgeNodeDetail {
  node: EdgeNodeView;
  recentMetrics: EdgeMetricsRecord[];
}

/** `POST /edge/nodes` 201 body — the enrollment code is present here and NOWHERE else. */
export interface EdgeNodeCreatedResponse {
  node: EdgeNodeView;
  enrollmentCode: string;
  enrollmentCodeExpiresAt: string | null;
  notice: string;
}

/** The lifecycle transitions the admin API exposes as POST actions. */
export type NodeAction = 'approve' | 'reject' | 'suspend' | 'revoke';
