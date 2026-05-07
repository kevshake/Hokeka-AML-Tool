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
} from "@mui/material";
import { apiClient } from "../../../lib/apiClient";
import { useQueryClient } from "@tanstack/react-query";

const ACCENT = "#8B4049";

interface CompanyTabProps {
  pspId: string;
  psp: any;
}

export default function CompanyTab({ pspId, psp }: CompanyTabProps) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({
    legalName: psp?.legalName ?? "",
    tradingName: psp?.tradingName ?? "",
    country: psp?.country ?? "",
    registrationNumber: psp?.registrationNumber ?? "",
    taxId: psp?.taxId ?? "",
    contactEmail: psp?.contactEmail ?? "",
    contactPhone: psp?.contactPhone ?? "",
    contactAddress: psp?.contactAddress ?? "",
  });
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState<{ open: boolean; severity: "success" | "error"; message: string }>({
    open: false,
    severity: "success",
    message: "",
  });

  const set = (field: string) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [field]: e.target.value }));

  const handleSave = async () => {
    setSaving(true);
    try {
      await apiClient.put(`psps/${pspId}`, form);
      queryClient.invalidateQueries({ queryKey: ["psp", pspId] });
      queryClient.invalidateQueries({ queryKey: ["psps"] });
      setToast({ open: true, severity: "success", message: "Company details saved." });
    } catch {
      setToast({ open: true, severity: "error", message: "Save failed. Please try again." });
    } finally {
      setSaving(false);
    }
  };

  return (
    <Box>
      <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 2 }}>
        Company Details
      </Typography>
      <Grid container spacing={2}>
        <Grid item xs={12} md={6}>
          <TextField
            fullWidth
            size="small"
            label="Legal Name"
            value={form.legalName}
            onChange={set("legalName")}
          />
        </Grid>
        <Grid item xs={12} md={6}>
          <TextField
            fullWidth
            size="small"
            label="Trading Name"
            value={form.tradingName}
            onChange={set("tradingName")}
          />
        </Grid>
        <Grid item xs={12} md={6}>
          <TextField
            fullWidth
            size="small"
            label="Country"
            value={form.country}
            onChange={set("country")}
          />
        </Grid>
        <Grid item xs={12} md={6}>
          <TextField
            fullWidth
            size="small"
            label="Registration Number"
            value={form.registrationNumber}
            onChange={set("registrationNumber")}
          />
        </Grid>
        <Grid item xs={12} md={6}>
          <TextField
            fullWidth
            size="small"
            label="Tax ID / PIN"
            value={form.taxId}
            onChange={set("taxId")}
          />
        </Grid>
        <Grid item xs={12} md={6}>
          <TextField
            fullWidth
            size="small"
            label="Contact Email"
            type="email"
            value={form.contactEmail}
            onChange={set("contactEmail")}
          />
        </Grid>
        <Grid item xs={12} md={6}>
          <TextField
            fullWidth
            size="small"
            label="Contact Phone"
            value={form.contactPhone}
            onChange={set("contactPhone")}
          />
        </Grid>
        <Grid item xs={12}>
          <TextField
            fullWidth
            size="small"
            label="Contact Address"
            multiline
            rows={2}
            value={form.contactAddress}
            onChange={set("contactAddress")}
          />
        </Grid>
      </Grid>

      <Box sx={{ mt: 3, display: "flex", justifyContent: "flex-end" }}>
        <Button
          variant="contained"
          onClick={handleSave}
          disabled={saving}
          sx={{ backgroundColor: ACCENT, textTransform: "none", "&:hover": { backgroundColor: "#6b313a" } }}
        >
          {saving ? <CircularProgress size={18} sx={{ color: "white" }} /> : "Save Changes"}
        </Button>
      </Box>

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
