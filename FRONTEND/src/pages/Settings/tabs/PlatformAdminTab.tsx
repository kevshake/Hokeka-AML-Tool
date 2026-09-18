import {
  Alert,
  Box,
  Button,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  TextField,
  Typography,
} from "@mui/material";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { apiClient } from "../../../lib/apiClient";

interface PspOption {
  id: number;
  code: string;
  name: string;
}

interface BillingRateRow {
  rateId: number;
  serviceType: string;
  baseRate: number;
  currency: string;
  isActive: boolean;
}

const SIGNAL_MODES = ["REPORTING_ONLY", "INFLUENCE_DECISION", "ALERT_ROUTING"] as const;

export default function PlatformAdminTab() {
  const [selectedPspId, setSelectedPspId] = useState<number | "">("");
  const [inviteRole, setInviteRole] = useState("PSP_ADMIN");
  const [issuedToken, setIssuedToken] = useState<string | null>(null);
  const [signalMode, setSignalMode] = useState<string>("INFLUENCE_DECISION");
  const [rateServiceType, setRateServiceType] = useState("WALLET_SCREENING");
  const [rateAmount, setRateAmount] = useState("0.08");

  const { data: psps } = useQuery<PspOption[]>({
    queryKey: ["settings", "psps"],
    queryFn: () => apiClient.get<PspOption[]>("settings/psps"),
  });

  const { data: billingRates, refetch: refetchRates } = useQuery<BillingRateRow[]>({
    queryKey: ["admin", "billing-rates", selectedPspId],
    queryFn: () => apiClient.get<BillingRateRow[]>(`admin/psps/${selectedPspId}/billing-rates`),
    enabled: selectedPspId !== "",
  });

  const issueInvite = useMutation({
    mutationFn: () =>
      apiClient.post<{ token: string }>("admin/onboarding/invites", {
        pspId: selectedPspId,
        role: inviteRole,
        expiresAt: new Date(Date.now() + 7 * 86400000).toISOString().slice(0, 19),
      }),
    onSuccess: (data) => setIssuedToken(data.token),
  });

  const saveSignalMode = useMutation({
    mutationFn: () =>
      apiClient.put(`psps/${selectedPspId}/signal-settings`, { mode: signalMode }),
  });

  const createRate = useMutation({
    mutationFn: () =>
      apiClient.post(`admin/psps/${selectedPspId}/billing-rates`, {
        serviceType: rateServiceType,
        pricingModel: "PER_REQUEST",
        baseRate: Number(rateAmount),
        currency: "USD",
        isActive: true,
      }),
    onSuccess: () => refetchRates(),
  });

  return (
    <Box sx={{ display: "grid", gap: 3 }}>
      <Paper sx={{ p: 2 }}>
        <Typography variant="h6" sx={{ mb: 2 }}>
          Platform admin — PSP scope
        </Typography>
        <FormControl fullWidth size="small" sx={{ mb: 2 }}>
          <InputLabel>PSP</InputLabel>
          <Select
            label="PSP"
            value={selectedPspId}
            onChange={(e) => setSelectedPspId(e.target.value as number)}
          >
            {psps?.map((psp) => (
              <MenuItem key={psp.id} value={psp.id}>
                {psp.name} ({psp.code})
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      </Paper>

      <Paper sx={{ p: 2 }}>
        <Typography variant="h6" sx={{ mb: 1 }}>
          Onboarding invites
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Issue invite-only credentials for PSP / merchant onboarding. Share the token once; it is not stored in plaintext.
        </Typography>
        <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap", mb: 2 }}>
          <TextField
            select
            label="Role"
            size="small"
            value={inviteRole}
            onChange={(e) => setInviteRole(e.target.value)}
            sx={{ minWidth: 180 }}
          >
            <MenuItem value="PSP_ADMIN">PSP_ADMIN</MenuItem>
            <MenuItem value="PSP_USER">PSP_USER</MenuItem>
            <MenuItem value="MERCHANT_ONBOARD">MERCHANT_ONBOARD</MenuItem>
          </TextField>
          <Button
            variant="contained"
            disabled={selectedPspId === "" || issueInvite.isPending}
            onClick={() => issueInvite.mutate()}
          >
            Generate invite
          </Button>
        </Box>
        {issuedToken && (
          <Alert severity="success">
            Invite token (copy now): <strong>{issuedToken}</strong>
          </Alert>
        )}
      </Paper>

      <Paper sx={{ p: 2 }}>
        <Typography variant="h6" sx={{ mb: 1 }}>
          Signal taxonomy mode
        </Typography>
        <Box sx={{ display: "flex", gap: 2, alignItems: "center", flexWrap: "wrap" }}>
          <TextField
            select
            label="Mode"
            size="small"
            value={signalMode}
            onChange={(e) => setSignalMode(e.target.value)}
            sx={{ minWidth: 240 }}
          >
            {SIGNAL_MODES.map((mode) => (
              <MenuItem key={mode} value={mode}>
                {mode}
              </MenuItem>
            ))}
          </TextField>
          <Button
            variant="outlined"
            disabled={selectedPspId === "" || saveSignalMode.isPending}
            onClick={() => saveSignalMode.mutate()}
          >
            Save signal mode
          </Button>
        </Box>
      </Paper>

      <Paper sx={{ p: 2 }}>
        <Typography variant="h6" sx={{ mb: 1 }}>
          Per-PSP billing rate overrides
        </Typography>
        <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap", mb: 2 }}>
          <TextField
            label="Service type"
            size="small"
            value={rateServiceType}
            onChange={(e) => setRateServiceType(e.target.value)}
          />
          <TextField
            label="USD rate"
            size="small"
            value={rateAmount}
            onChange={(e) => setRateAmount(e.target.value)}
          />
          <Button
            variant="contained"
            disabled={selectedPspId === "" || createRate.isPending}
            onClick={() => createRate.mutate()}
          >
            Add override
          </Button>
        </Box>
        {billingRates?.map((rate) => (
          <Typography key={rate.rateId} variant="body2">
            {rate.serviceType}: {rate.currency} {rate.baseRate} {rate.isActive ? "" : "(inactive)"}
          </Typography>
        ))}
      </Paper>
    </Box>
  );
}
