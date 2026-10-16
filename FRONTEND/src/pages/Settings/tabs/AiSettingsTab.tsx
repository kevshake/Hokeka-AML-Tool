import { Box, CircularProgress, FormControlLabel, Switch, Typography } from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BrainCircuit, Settings2 } from "lucide-react";
import GlassCard from "../../../components/Common/GlassCard";
import { apiClient } from "../../../lib/apiClient";
import { useAuth } from "../../../contexts/AuthContext";
import { canAccessAiSettingsTab } from "../../../lib/settingsTabs";

interface AiStatus {
  configured: boolean;
  model?: string;
  circuitState?: string;
  avgLatencyMsLast24h?: number;
  fallbacksLast24h?: number;
  callsLast24h?: number;
  spendLast24hUsd?: number;
  engines?: Array<{
    engineCode: string;
    enabled: boolean;
    advisoryOnly: boolean;
    promptVersion: string;
  }>;
}

function Metric({ label, value }: { label: string; value: string | number | undefined }) {
  return (
    <div className="rounded-lg border border-hairline bg-surface-2 px-3 py-2">
      <div className="text-xs uppercase tracking-wide text-ink-muted">{label}</div>
      <div className="mt-1 font-medium text-ink">{value ?? "—"}</div>
    </div>
  );
}

export default function AiSettingsTab() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const allowed = canAccessAiSettingsTab(user);

  const { data: status, isLoading } = useQuery<AiStatus>({
    queryKey: ["ai", "status"],
    queryFn: () => apiClient.get<AiStatus>("ai/status"),
    enabled: allowed,
  });

  const updateEngine = useMutation({
    mutationFn: (payload: { engineCode: string; enabled: boolean }) =>
      apiClient.put(`ai/engines/${payload.engineCode}`, { enabled: payload.enabled }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["ai", "status"] }),
  });

  if (!allowed) {
    return (
      <Typography variant="body2" sx={{ color: "var(--ink-muted)" }}>
        Hokeka AI operator settings are restricted to platform operators.
      </Typography>
    );
  }

  if (isLoading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <div className="space-y-4">
      <GlassCard className="p-4">
        <div className="mb-4 flex items-center gap-2">
          <BrainCircuit size={20} className="text-gold" />
          <Typography variant="h6">Hokeka AI decision layer</Typography>
        </div>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Metric label="Configured" value={status?.configured ? "Yes" : "No"} />
          <Metric label="Model" value={status?.model ?? "Not set"} />
          <Metric label="Circuit" value={status?.circuitState} />
          <Metric label="Avg latency (24h)" value={status?.avgLatencyMsLast24h != null ? `${Math.round(status.avgLatencyMsLast24h)} ms` : undefined} />
          <Metric label="Calls (24h)" value={status?.callsLast24h} />
          <Metric label="Fallbacks (24h)" value={status?.fallbacksLast24h} />
          <Metric label="Spend (24h USD)" value={status?.spendLast24hUsd} />
        </div>
      </GlassCard>

      <GlassCard className="p-4">
        <div className="mb-3 flex items-center gap-2">
          <Settings2 size={18} className="text-gold" />
          <Typography variant="subtitle1">Per-engine toggles</Typography>
        </div>
        <div className="space-y-2">
          {(status?.engines ?? []).map((engine) => (
            <FormControlLabel
              key={engine.engineCode}
              control={
                <Switch
                  checked={engine.enabled}
                  onChange={(e) =>
                    updateEngine.mutate({ engineCode: engine.engineCode, enabled: e.target.checked })
                  }
                />
              }
              label={`${engine.engineCode} (prompt ${engine.promptVersion}, advisory=${engine.advisoryOnly ? "yes" : "no"})`}
            />
          ))}
        </div>
      </GlassCard>
    </div>
  );
}
