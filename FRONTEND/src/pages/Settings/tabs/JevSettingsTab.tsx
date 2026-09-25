import {
  Box,
  Button,
  CircularProgress,
  FormControlLabel,
  MenuItem,
  Select,
  Switch,
  TextField,
  Typography,
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BrainCircuit, Gauge, Settings2 } from "lucide-react";
import { useState } from "react";
import GlassCard from "../../../components/Common/GlassCard";
import { apiClient } from "../../../lib/apiClient";
import { useAuth } from "../../../contexts/AuthContext";
import { canAccessJevSettingsTab } from "../../../lib/settingsTabs";

interface JevStatus {
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

interface PspOption {
  id: number;
  code: string;
  name: string;
}

interface PspAiSettings {
  pspId: number;
  aiInlineMode: boolean;
  aiInlineBudgetMs: number;
}

function Metric({ label, value }: { label: string; value: string | number | undefined }) {
  return (
    <div className="rounded-lg border border-hairline bg-surface-2 px-3 py-2">
      <div className="text-xs uppercase tracking-wide text-ink-muted">{label}</div>
      <div className="mt-1 font-medium text-ink">{value ?? "—"}</div>
    </div>
  );
}

export default function JevSettingsTab() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const allowed = canAccessJevSettingsTab(user);

  const { data: status, isLoading } = useQuery<JevStatus>({
    queryKey: ["jev", "status"],
    queryFn: () => apiClient.get<JevStatus>("jev/status"),
    enabled: allowed,
  });

  const { data: psps } = useQuery<PspOption[]>({
    queryKey: ["settings", "psps"],
    queryFn: () => apiClient.get<PspOption[]>("settings/psps"),
  });

  const [selectedPspId, setSelectedPspId] = useState<number | "">("");
  const { data: pspAi } = useQuery<PspAiSettings>({
    queryKey: ["jev", "psp-ai", selectedPspId],
    queryFn: () => apiClient.get<PspAiSettings>(`jev/psps/${selectedPspId}/ai-settings`),
    enabled: selectedPspId !== "",
  });

  const [inlineMode, setInlineMode] = useState(false);
  const [inlineBudget, setInlineBudget] = useState(500);

  const updateEngine = useMutation({
    mutationFn: (payload: { engineCode: string; enabled: boolean }) =>
      apiClient.put(`jev/engines/${payload.engineCode}`, { enabled: payload.enabled }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["jev", "status"] }),
  });

  const updatePspAi = useMutation({
    mutationFn: () =>
      apiClient.put(`jev/psps/${selectedPspId}/ai-settings`, {
        aiInlineMode: inlineMode,
        aiInlineBudgetMs: inlineBudget,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["jev", "psp-ai", selectedPspId] }),
  });

  if (!allowed) {
    return (
      <Typography variant="body2" sx={{ color: "var(--ink-muted)" }}>
        JEV operator settings are restricted to platform operators.
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
          <Typography variant="h6">JEV AI decision layer</Typography>
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

      <GlassCard className="p-4">
        <div className="mb-3 flex items-center gap-2">
          <Gauge size={18} className="text-gold" />
          <Typography variant="subtitle1">PSP inline AI mode</Typography>
        </div>
        <Select
          fullWidth
          size="small"
          displayEmpty
          value={selectedPspId}
          onChange={(e) => {
            const v = e.target.value;
            setSelectedPspId(v === "" ? "" : Number(v));
          }}
          sx={{ mb: 2 }}
        >
          <MenuItem value="">Select PSP</MenuItem>
          {(psps ?? []).map((p) => (
            <MenuItem key={p.id} value={p.id}>
              {p.name} ({p.code})
            </MenuItem>
          ))}
        </Select>
        {pspAi ? (
          <div className="space-y-3">
            <FormControlLabel
              control={
                <Switch
                  checked={inlineMode || pspAi.aiInlineMode}
                  onChange={(e) => setInlineMode(e.target.checked)}
                />
              }
              label="aiInlineMode — edge waits for JEV on borderline pre-auth (falls back to rules on timeout)"
            />
            <TextField
              label="Inline budget (ms)"
              type="number"
              size="small"
              fullWidth
              value={inlineBudget || pspAi.aiInlineBudgetMs}
              onChange={(e) => setInlineBudget(Number(e.target.value))}
            />
            <Button variant="contained" onClick={() => updatePspAi.mutate()} disabled={selectedPspId === ""}>
              Save PSP AI settings
            </Button>
          </div>
        ) : null}
      </GlassCard>
    </div>
  );
}
