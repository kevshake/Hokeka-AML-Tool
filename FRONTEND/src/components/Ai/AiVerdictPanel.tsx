import { Box, Chip, CircularProgress, Typography } from "@mui/material";
import { useQuery } from "@tanstack/react-query";
import { Sparkles } from "lucide-react";
import GlassCard from "../Common/GlassCard";
import { apiClient } from "../../lib/apiClient";
import { useAuth } from "../../contexts/AuthContext";
import { canViewAiVerdict } from "../../lib/aiDecisionRbac";

/** Tenant-facing audit row from `/ai/audit/*` (redacted server-side). */
export interface TenantAiAuditEntry {
  id: number;
  recommendation?: string;
  confidence?: number;
  reasons?: string[];
  createdAt?: string;
  aiApplied?: boolean;
  advisoryOnly?: boolean;
}

/** Operator-facing audit row — full detail from Control Plane (operator settings only). */
export interface OperatorAiAuditEntry extends TenantAiAuditEntry {
  engineCode?: string;
  riskScore?: number;
  citedSignals?: string[];
  fallbackReason?: string;
  baselineDecision?: string;
  finalDecision?: string;
  modelId?: string;
  promptVersion?: string;
  inputTokens?: number;
  outputTokens?: number;
  estimatedCostUsd?: number;
}

interface AiVerdictPanelProps {
  auditPath?: string;
  auditId?: number;
  title?: string;
  pollUntilFound?: boolean;
  forceShow?: boolean;
}

const PRODUCT_TITLE = "Hokeka AI recommendation";

const ADVISORY_COPY =
  "This recommendation is advisory only. Review evidence and apply your policies before acting.";

function formatWhen(value?: string) {
  if (!value) return "";
  try {
    return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(
      new Date(value)
    );
  } catch {
    return value;
  }
}

function TenantAuditEntryBlock({ entry, isLatest }: { entry: TenantAiAuditEntry; isLatest: boolean }) {
  return (
    <div
      className={`rounded-lg border p-3 ${isLatest ? "border-gold/40 bg-gold/5" : "border-hairline bg-surface-2/40"}`}
    >
      {entry.createdAt ? (
        <Typography variant="caption" sx={{ color: "var(--ink-muted)", display: "block", mb: 1 }}>
          {formatWhen(entry.createdAt)}
        </Typography>
      ) : null}
      <div className="flex flex-wrap gap-2">
        {entry.recommendation ? (
          <Chip label={`Verdict: ${entry.recommendation}`} size="small" color="primary" variant="outlined" />
        ) : null}
        {entry.confidence != null ? (
          <Chip label={`Confidence: ${(entry.confidence * 100).toFixed(0)}%`} size="small" variant="outlined" />
        ) : null}
      </div>
      {entry.reasons && entry.reasons.length > 0 ? (
        <ul className="mt-2 list-disc pl-5 text-sm text-ink-muted">
          {entry.reasons.map((r) => (
            <li key={r}>{r}</li>
          ))}
        </ul>
      ) : null}
      <Typography variant="caption" sx={{ color: "var(--ink-muted)", display: "block", mt: 1 }}>
        {entry.aiApplied ? "Applied to decision path" : "Advisory only — no automated override"}
      </Typography>
    </div>
  );
}

export default function AiVerdictPanel({
  auditPath,
  auditId,
  title = PRODUCT_TITLE,
  pollUntilFound = false,
  forceShow = false,
}: AiVerdictPanelProps) {
  const { user } = useAuth();
  const allowed = forceShow || canViewAiVerdict(user);
  const path = auditId != null ? `ai/audit/id/${auditId}` : auditPath;

  const { data, isLoading, isError } = useQuery<TenantAiAuditEntry[]>({
    queryKey: ["hokeka-ai", "audit", path],
    queryFn: () => apiClient.get<TenantAiAuditEntry[]>(path!),
    enabled: allowed && Boolean(path),
    refetchInterval: (query) =>
      pollUntilFound && (!query.state.data || query.state.data.length === 0) ? 3000 : false,
  });

  if (!allowed) {
    return null;
  }

  const entries = data ?? [];

  return (
    <GlassCard className="p-4">
      <div className="mb-3 flex items-center gap-2">
        <Sparkles size={18} className="text-gold" />
        <Typography variant="subtitle2" sx={{ color: "var(--ink)" }}>
          {title ?? PRODUCT_TITLE}
        </Typography>
        <Chip
          size="small"
          label="Advisory only"
          color="warning"
          variant="outlined"
          sx={{ ml: "auto", fontSize: "0.7rem" }}
        />
      </div>
      <Typography variant="caption" sx={{ color: "var(--ink-muted)", display: "block", mb: 2 }}>
        {ADVISORY_COPY}
      </Typography>

      {isLoading ? (
        <Box sx={{ display: "flex", justifyContent: "center", py: 2 }}>
          <CircularProgress size={22} />
        </Box>
      ) : isError || entries.length === 0 ? (
        <Typography variant="body2" sx={{ color: "var(--ink-muted)" }}>
          {pollUntilFound ? "Waiting for Hokeka AI recommendation…" : "No Hokeka AI recommendation recorded yet."}
        </Typography>
      ) : (
        <div className="space-y-3">
          {entries.map((entry, index) => (
            <TenantAuditEntryBlock key={entry.id} entry={entry} isLatest={index === 0} />
          ))}
        </div>
      )}
    </GlassCard>
  );
}
