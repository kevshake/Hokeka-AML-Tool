import { useState } from "react";
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
} from "@mui/material";
import { useUpdatePspCbkConfig, type UpdatePspCbkConfigRequest } from "../../../features/api/mutations";
import { CBK_ENDPOINT_LABELS } from "../../../types/cbk";
import type { CbkEndpointType } from "../../../types/cbk";

const ACCENT = "#8B4049";

const ALL_ENDPOINTS = Object.keys(CBK_ENDPOINT_LABELS) as CbkEndpointType[];

interface CbkReportingTabProps {
  pspId: string;
  psp: any;
}

export default function CbkReportingTab({ pspId, psp }: CbkReportingTabProps) {
  const update = useUpdatePspCbkConfig();

  const [form, setForm] = useState({
    cbkInstitutionCode: psp?.cbkInstitutionCode ?? "",
    cbkReportingEnabled: psp?.cbkReportingEnabled ?? false,
    cbkClientId: psp?.cbkClientId ?? "",
    cbkClientSecret: "",
  });

  const [toast, setToast] = useState<{ open: boolean; severity: "success" | "error"; message: string }>({
    open: false,
    severity: "success",
    message: "",
  });

  const set = (field: string) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [field]: e.target.value }));

  const handleToggle = (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, cbkReportingEnabled: e.target.checked }));

  const handleSave = async () => {
    try {
      const payload: UpdatePspCbkConfigRequest = {
        pspId,
        cbkInstitutionCode: form.cbkInstitutionCode,
        cbkReportingEnabled: form.cbkReportingEnabled,
        cbkClientId: form.cbkClientId,
        ...(form.cbkClientSecret ? { cbkClientSecret: form.cbkClientSecret } : {}),
      };
      await update.mutateAsync(payload);
      setToast({ open: true, severity: "success", message: "CBK configuration saved." });
    } catch {
      setToast({ open: true, severity: "error", message: "Save failed. Please try again." });
    }
  };

  return (
    <Box>
      <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 2 }}>
        CBK Reporting Configuration
      </Typography>

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
                onChange={handleToggle}
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
            placeholder="Leave blank to keep existing"
          />
        </Grid>
      </Grid>

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
