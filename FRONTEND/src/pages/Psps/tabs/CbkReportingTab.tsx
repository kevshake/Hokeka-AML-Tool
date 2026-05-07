import { useState, useEffect } from "react";
import {
  Box,
  Grid,
  TextField,
  Button,
  CircularProgress,
  Snackbar,
  Alert,
  Typography,
  FormControlLabel,
  Switch,
  Chip,
  Paper,
  MenuItem,
  Divider,
} from "@mui/material";
import { useUpdatePspCbkConfig, type UpdatePspCbkConfigRequest } from "../../../features/api/mutations";
import { usePspCbkConfig } from "../../../features/api/queries";
import { CBK_ENDPOINT_LABELS } from "../../../types/cbk";
import type { CbkEndpointType } from "../../../types/cbk";
import { useAuth } from "../../../contexts/AuthContext";

const ACCENT = "#8B4049";
const ALL_ENDPOINTS = Object.keys(CBK_ENDPOINT_LABELS) as CbkEndpointType[];

const PLATFORM_ADMIN_ROLES = new Set(["SUPER_ADMIN", "ADMIN"]);

interface CbkReportingTabProps {
  pspId: string;
  psp: any;
}

export default function CbkReportingTab({ pspId, psp }: CbkReportingTabProps) {
  const { user } = useAuth();
  const isPlatformAdmin = !!user?.role?.name && PLATFORM_ADMIN_ROLES.has(user.role.name);

  const cbkCfg = usePspCbkConfig(pspId);
  const update = useUpdatePspCbkConfig();

  const [form, setForm] = useState({
    cbkInstitutionCode: psp?.cbkInstitutionCode ?? "",
    cbkReportingEnabled: psp?.cbkReportingEnabled ?? false,
    cbkClientId: psp?.cbkClientId ?? "",
    cbkClientSecret: "",
    cbkEnvironment: (psp?.cbkEnvironment ?? "preprod") as "preprod" | "live",
    cbkAllowLive: psp?.cbkAllowLive ?? false,
  });

  // Hydrate from the dedicated cbk-config endpoint (canonical source of truth).
  useEffect(() => {
    const cfg = cbkCfg.data;
    if (!cfg) return;
    setForm((f) => ({
      ...f,
      cbkInstitutionCode: cfg.cbkInstitutionCode ?? "",
      cbkReportingEnabled: !!cfg.cbkReportingEnabled,
      cbkClientId: cfg.cbkClientId ?? "",
      cbkEnvironment: cfg.cbkEnvironment ?? "preprod",
      cbkAllowLive: !!cfg.cbkAllowLive,
    }));
  }, [cbkCfg.data]);

  const [toast, setToast] = useState<{ open: boolean; severity: "success" | "error"; message: string }>({
    open: false,
    severity: "success",
    message: "",
  });

  const set = (field: string) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [field]: e.target.value }));

  const handleSave = async () => {
    try {
      const payload: UpdatePspCbkConfigRequest = {
        pspId,
        cbkInstitutionCode: form.cbkInstitutionCode,
        cbkReportingEnabled: form.cbkReportingEnabled,
        cbkClientId: form.cbkClientId,
        ...(form.cbkClientSecret ? { cbkClientSecret: form.cbkClientSecret } : {}),
        // Platform-admin-only fields. Backend rejects PSP_ADMIN edits even if these
        // were sent — we only include them in the payload when the caller is an
        // admin so a partial update from a PSP_ADMIN doesn't accidentally null them.
        ...(isPlatformAdmin
          ? { cbkEnvironment: form.cbkEnvironment, cbkAllowLive: form.cbkAllowLive }
          : {}),
      };
      await update.mutateAsync(payload);
      setToast({ open: true, severity: "success", message: "CBK configuration saved." });
    } catch {
      setToast({ open: true, severity: "error", message: "Save failed. Please try again." });
    }
  };

  const liveEffective = !!cbkCfg.data?.liveEffective;
  const envBadgeColor = liveEffective ? "#b00020" : "#1976d2";
  const envBadgeLabel = liveEffective ? "LIVE" : "PREPROD / TEST";

  return (
    <Box>
      <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", mb: 2 }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
          CBK Reporting Configuration
        </Typography>
        <Chip
          label={`Effective: ${envBadgeLabel}`}
          size="small"
          sx={{
            backgroundColor: envBadgeColor,
            color: "white",
            fontWeight: 600,
            letterSpacing: 0.5,
          }}
        />
      </Box>

      {!isPlatformAdmin && (
        <Alert severity="info" sx={{ mb: 2 }}>
          The CBK environment (live vs preprod) and the live-allow flag are controlled by platform
          administrators. You can configure your institution code and credentials below; promotion
          to live will be done by your account manager.
        </Alert>
      )}

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} md={6}>
          <TextField
            fullWidth
            size="small"
            label="Institution Code"
            value={form.cbkInstitutionCode}
            onChange={set("cbkInstitutionCode")}
            helperText="Assigned by the Central Bank of Kenya"
          />
        </Grid>
        <Grid item xs={12} md={6}>
          <FormControlLabel
            control={
              <Switch
                checked={form.cbkReportingEnabled}
                onChange={(e) => setForm((f) => ({ ...f, cbkReportingEnabled: e.target.checked }))}
                sx={{
                  "& .MuiSwitch-switchBase.Mui-checked": { color: ACCENT },
                  "& .MuiSwitch-switchBase.Mui-checked + .MuiSwitch-track": { backgroundColor: ACCENT },
                }}
              />
            }
            label="CBK Reporting Enabled"
          />
        </Grid>
        <Grid item xs={12} md={6}>
          <TextField
            fullWidth
            size="small"
            label="OAuth2 Client ID"
            value={form.cbkClientId}
            onChange={set("cbkClientId")}
          />
        </Grid>
        <Grid item xs={12} md={6}>
          <TextField
            fullWidth
            size="small"
            label="OAuth2 Client Secret"
            type="password"
            value={form.cbkClientSecret}
            onChange={set("cbkClientSecret")}
            placeholder={cbkCfg.data?.hasClientSecret ? "Leave blank to keep existing" : "Not configured"}
          />
        </Grid>
      </Grid>

      {/* Platform-admin-only environment promotion section */}
      {isPlatformAdmin && (
        <Paper
          sx={{
            p: 2,
            mb: 3,
            border: "2px solid #b00020",
            borderRadius: 2,
            backgroundColor: "rgba(176, 0, 32, 0.03)",
          }}
        >
          <Typography variant="subtitle2" sx={{ fontWeight: 700, color: "#b00020", mb: 0.5 }}>
            Platform Admin — Environment Promotion
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ display: "block", mb: 2 }}>
            Switching to LIVE sends real submissions to the production CBK GDI host
            (gdicbk.centralbank.go.ke). Both flags below AND the platform-wide
            CBK_ALLOW_LIVE env var must be true for live to actually fire.
          </Typography>
          <Grid container spacing={2}>
            <Grid item xs={12} md={6}>
              <TextField
                select
                fullWidth
                size="small"
                label="CBK Environment"
                value={form.cbkEnvironment}
                onChange={(e) =>
                  setForm((f) => ({ ...f, cbkEnvironment: e.target.value as "preprod" | "live" }))
                }
              >
                <MenuItem value="preprod">PREPROD (sandbox / test)</MenuItem>
                <MenuItem value="live">LIVE (production)</MenuItem>
              </TextField>
            </Grid>
            <Grid item xs={12} md={6}>
              <FormControlLabel
                control={
                  <Switch
                    checked={form.cbkAllowLive}
                    onChange={(e) => setForm((f) => ({ ...f, cbkAllowLive: e.target.checked }))}
                    color="error"
                  />
                }
                label="Allow Live Submissions"
              />
            </Grid>
          </Grid>
          <Divider sx={{ my: 2 }} />
          <Typography variant="caption" color="text.secondary">
            Effective live: <b>{liveEffective ? "YES" : "no"}</b>
            {!liveEffective && form.cbkEnvironment === "live" && (
              <> — environment is "live" but a guard is off (allow-live flag or platform kill switch).</>
            )}
          </Typography>
        </Paper>
      )}

      <Box sx={{ mt: 3, display: "flex", justifyContent: "flex-end", mb: 3 }}>
        <Button
          variant="contained"
          onClick={handleSave}
          disabled={update.isPending}
          sx={{ backgroundColor: ACCENT, textTransform: "none", "&:hover": { backgroundColor: "#6b313a" } }}
        >
          {update.isPending ? <CircularProgress size={18} sx={{ color: "white" }} /> : "Save CBK Config"}
        </Button>
      </Box>

      {/* Endpoint overview */}
      <Paper
        sx={{ p: 2, border: "1px solid rgba(0,0,0,0.08)", borderRadius: 2, backgroundColor: "background.paper" }}
      >
        <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1.5 }}>
          CBK Endpoint Types ({ALL_ENDPOINTS.length} total)
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
          When CBK reporting is enabled this PSP will submit data to all applicable endpoints according
          to their scheduled frequency (daily / monthly / annual).
        </Typography>
        <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
          {ALL_ENDPOINTS.map((ep) => (
            <Chip
              key={ep}
              label={CBK_ENDPOINT_LABELS[ep]}
              size="small"
              sx={{
                backgroundColor: form.cbkReportingEnabled ? "rgba(139,64,73,0.1)" : "rgba(0,0,0,0.05)",
                color: form.cbkReportingEnabled ? ACCENT : "text.secondary",
                fontSize: "0.72rem",
              }}
            />
          ))}
        </Box>
      </Paper>

      <Snackbar
        open={toast.open}
        autoHideDuration={4000}
        onClose={() => setToast((t) => ({ ...t, open: false }))}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      >
        <Alert severity={toast.severity} onClose={() => setToast((t) => ({ ...t, open: false }))} variant="filled">
          {toast.message}
        </Alert>
      </Snackbar>
    </Box>
  );
}
