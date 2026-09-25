import { Box, Chip, CircularProgress, Typography } from "@mui/material";
import { useQuery } from "@tanstack/react-query";
import { Sparkles } from "lucide-react";
import GlassCard from "../Common/GlassCard";
import { apiClient } from "../../lib/apiClient";
import { useAuth } from "../../contexts/AuthContext";
import { canViewJevVerdict } from "../../lib/jevRbac";
import { isPlatformAdmin } from "../../lib/userAccess";

/** Tenant-facing audit row from `/jev/audit/*` (redacted server-side). */
export interface TenantAiAuditEntry {
  id: number;
  recommendation?: string;
  confidence?: number;
  reasons?: string[];
  createdAt?: string;
  aiApplied?: boolean;
  advisoryOnly?: boolean;
}

/** Operator-facing audit row — full detail from Control Plane. */
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
  /** Relative API path under /api/v1, e.g. jev/audit/alert/123 */
  auditPath?: string;
  /** Direct audit id lookup (e.g. rule generation preview) */
  auditId?: number;
  title?: string;
  /** Poll while async AI advisory may still be writing audit rows */
  pollUntilFound?: boolean;
  /** Override RBAC gate (default: investigator/compliance roles) */
  forceShow?: boolean;
}

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
      <div className="mb-2 flex flex-wrap items-center gap-2">
        {entry.createdAt ? (
          <Typography variant="caption" sx={{ color: "var(--ink-muted)" }}>
            {formatWhen(entry.createdAt)}
          </Typography>
        ) : null}
      </div>
      <div className="flex flex-wrap gap-2">
        {entry.recommendation ? (
          <Chip label={`Recommendation: ${entry.recommendation}`} size="small" color="primary" variant="outlined" />
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

function OperatorAuditEntryBlock({ entry, isLatest }: { entry: OperatorAiAuditEntry; isLatest: boolean }) {
  return (
    <div
      className={`rounded-lg border p-3 ${isLatest ? "border-gold/40 bg-gold/5" : "border-hairline bg-surface-2/40"}`}
    >
      <div className="mb-2 flex flex-wrap items-center gap-2">
        {entry.engineCode ? (
          <Chip size="small" label={entry.engineCode.replace(/_/g, " ")} variant="outlined" />
        ) : null}
        {entry.createdAt ? (
          <Typography variant="caption" sx={{ color: "var(--ink-muted)" }}>
            {formatWhen(entry.createdAt)}
          </Typography>
        ) : null}
        {entry.modelId ? (
          <Typography variant="caption" sx={{ color: "var(--ink-muted)", ml: "auto" }}>
            {entry.modelId}
          </Typography>
        ) : null}
      </div>
      {entry.fallbackReason ? (
        <Typography variant="body2" color="warning.main" sx={{ mb: 1 }}>
          Rules baseline used ({entry.fallbackReason})
        </Typography>
      ) : null}
      <div className="flex flex-wrap gap-2">
        {entry.recommendation ? (
          <Chip label={`Recommendation: ${entry.recommendation}`} size="small" color="primary" variant="outlined" />
        ) : null}
        {entry.confidence != null ? (
          <Chip label={`Confidence: ${(entry.confidence * 100).toFixed(0)}%`} size="small" variant="outlined" />
        ) : null}
        {entry.riskScore != null ? (
          <Chip label={`Risk score: ${entry.riskScore}`} size="small" variant="outlined" />
        ) : null}
        {entry.baselineDecision ? (
          <Chip label={`Baseline: ${entry.baselineDecision}`} size="small" variant="outlined" />
        ) : null}
      </div>
      {entry.reasons && entry.reasons.length > 0 ? (
        <ul className="mt-2 list-disc pl-5 text-sm text-ink-muted">
          {entry.reasons.map((r) => (
            <li key={r}>{r}</li>
          ))}
        </ul>
      ) : null}
      {entry.citedSignals && entry.citedSignals.length > 0 ? (
        <Typography variant="caption" sx={{ color: "var(--ink-muted)", display: "block", mt: 1 }}>
          Signals: {entry.citedSignals.join(", ")}
        </Typography>
      ) : null}
      <Typography variant="caption" sx={{ color: "var(--ink-muted)", display: "block", mt: 1 }}>
        Audit #{entry.id}
        {entry.aiApplied ? " · AI applied to decision path" : " · Advisory only — no automated override"}
        {entry.promptVersion ? ` · prompt ${entry.promptVersion}` : ""}
        {entry.inputTokens != null ? ` · tokens ${entry.inputTokens + (entry.outputTokens ?? 0)}` : ""}
      </Typography>
    </div>
  );
}

export default function AiVerdictPanel({
  auditPath,
  auditId,
  title = "Hokeka AI recommendation",
  pollUntilFound = false,
  forceShow = false,
}: AiVerdictPanelProps) {
  const { user } = useAuth();
  const operatorView = isPlatformAdmin(user);
  const allowed = forceShow || canViewJevVerdict(user);
  const path = auditId != null ? `jev/audit/id/${auditId}` : auditPath;

  const { data, isLoading, isError } = useQuery<(TenantAiAuditEntry | OperatorAiAuditEntry)[]>({
    queryKey: ["hokeka-ai", "audit", path, operatorView],
    queryFn: () => apiClient.get<(TenantAiAuditEntry | OperatorAiAuditEntry)[]>(path!),
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
          {title}
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
        Hokeka Intelligence suggestions do not replace analyst judgment or automated rules baselines.
        Review evidence before acting.
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
          {entries.length > 1 ? (
            <Typography variant="caption" sx={{ color: "var(--ink-muted)" }}>
              Recommendation history ({entries.length} entries, newest first)
            </Typography>
          ) : null}
          {entries.map((entry, index) =>
            operatorView ? (
              <OperatorAuditEntryBlock
                key={entry.id}
                entry={entry as OperatorAiAuditEntry}
                isLatest={index === 0}
              />
            ) : (
              <TenantAuditEntryBlock key={entry.id} entry={entry} isLatest={index === 0} />
            )
          )}
        </div>
      )}
    </GlassCard>
  );
}
