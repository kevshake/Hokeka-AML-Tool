import { Box, Chip, CircularProgress, Typography } from "@mui/material";
import { useQuery } from "@tanstack/react-query";
import { Sparkles } from "lucide-react";
import GlassCard from "../Common/GlassCard";
import { apiClient } from "../../lib/apiClient";

export interface JevAuditEntry {
  id: number;
  engineCode: string;
  recommendation?: string;
  riskScore?: number;
  confidence?: number;
  reasons?: string[];
  citedSignals?: string[];
  fallbackReason?: string;
  aiApplied?: boolean;
  baselineDecision?: string;
  finalDecision?: string;
  createdAt?: string;
}

interface AiVerdictPanelProps {
  auditPath: string;
  title?: string;
}

export default function AiVerdictPanel({ auditPath, title = "JEV AI recommendation" }: AiVerdictPanelProps) {
  const { data, isLoading, isError } = useQuery<JevAuditEntry[]>({
    queryKey: ["jev", "audit", auditPath],
    queryFn: () => apiClient.get<JevAuditEntry[]>(auditPath),
  });

  const latest = data && data.length > 0 ? data[0] : null;

  return (
    <GlassCard className="p-4">
      <div className="mb-3 flex items-center gap-2">
        <Sparkles size={18} className="text-gold" />
        <Typography variant="subtitle2" sx={{ color: "var(--ink)" }}>
          {title}
        </Typography>
        <Chip size="small" label="Advisory" sx={{ ml: "auto", fontSize: "0.7rem" }} />
      </div>

      {isLoading ? (
        <Box sx={{ display: "flex", justifyContent: "center", py: 2 }}>
          <CircularProgress size={22} />
        </Box>
      ) : isError || !latest ? (
        <Typography variant="body2" sx={{ color: "var(--ink-muted)" }}>
          No AI verdict recorded yet.
        </Typography>
      ) : (
        <div className="space-y-2 text-sm">
          {latest.fallbackReason ? (
            <Typography variant="body2" color="warning.main">
              Rules baseline used ({latest.fallbackReason})
            </Typography>
          ) : null}
          <div className="flex flex-wrap gap-2">
            {latest.recommendation ? (
              <Chip label={`Recommendation: ${latest.recommendation}`} size="small" color="primary" variant="outlined" />
            ) : null}
            {latest.confidence != null ? (
              <Chip label={`Confidence: ${(latest.confidence * 100).toFixed(0)}%`} size="small" variant="outlined" />
            ) : null}
            {latest.riskScore != null ? (
              <Chip label={`Risk score: ${latest.riskScore}`} size="small" variant="outlined" />
            ) : null}
          </div>
          {latest.reasons && latest.reasons.length > 0 ? (
            <ul className="list-disc pl-5 text-ink-muted">
              {latest.reasons.map((r) => (
                <li key={r}>{r}</li>
              ))}
            </ul>
          ) : null}
          {latest.citedSignals && latest.citedSignals.length > 0 ? (
            <Typography variant="caption" sx={{ color: "var(--ink-muted)" }}>
              Signals: {latest.citedSignals.join(", ")}
            </Typography>
          ) : null}
        </div>
      )}
    </GlassCard>
  );
}
