import { useState } from "react";
import { Box, Paper, Typography, TextField, Button, Grid, Snackbar, Alert, CircularProgress } from "@mui/material";
import { useMutation } from "@tanstack/react-query";
import { apiClient } from "../../lib/apiClient";

export default function LimitsAmlPage() {
  const [transactionLimit, setTransactionLimit] = useState("");
  const [dailyLimit, setDailyLimit] = useState("");
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: "success" | "error" }>({
    open: false, message: "", severity: "success",
  });

  const saveLimits = useMutation({
    mutationFn: () =>
      apiClient.post("limits/aml", {
        transactionLimit: transactionLimit ? parseFloat(transactionLimit) : undefined,
        dailyLimit: dailyLimit ? parseFloat(dailyLimit) : undefined,
      }),
    onSuccess: () => {
      setSnackbar({ open: true, message: "AML limits saved successfully.", severity: "success" });
    },
    onError: (err: any) => {
      setSnackbar({ open: true, message: err?.message || "Failed to save limits.", severity: "error" });
    },
  });

  const fieldSx = {
    "& .MuiOutlinedInput-root": {
      color: "text.primary",
      "& fieldset": { borderColor: "rgba(0,0,0,0.2)" },
      "&:hover fieldset": { borderColor: "rgba(0,0,0,0.4)" },
    },
    "& .MuiInputLabel-root": { color: "text.secondary" },
  };

  return (
    <Box>
      <Typography variant="h6" sx={{ color: "text.primary", mb: 3, fontWeight: 600 }}>
        Limits & AML
      </Typography>

      <Paper sx={{ p: 3, backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
        <Typography variant="h6" sx={{ color: "text.primary", mb: 3 }}>
          AML Limits Configuration
        </Typography>

        <Grid container spacing={3}>
          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              label="Transaction Limit"
              type="number"
              value={transactionLimit}
              onChange={(e) => setTransactionLimit(e.target.value)}
              helperText="Maximum amount per single transaction (USD)"
              inputProps={{ min: 0, step: "0.01" }}
              sx={fieldSx}
            />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              label="Daily Limit"
              type="number"
              value={dailyLimit}
              onChange={(e) => setDailyLimit(e.target.value)}
              helperText="Maximum total transaction volume per day (USD)"
              inputProps={{ min: 0, step: "0.01" }}
              sx={fieldSx}
            />
          </Grid>
          <Grid item xs={12}>
            <Button
              variant="contained"
              onClick={() => saveLimits.mutate()}
              disabled={saveLimits.isPending || (!transactionLimit && !dailyLimit)}
              sx={{ backgroundColor: "#a93226", "&:hover": { backgroundColor: "#922b21" }, textTransform: "none" }}
            >
              {saveLimits.isPending ? <CircularProgress size={18} sx={{ color: "white", mr: 1 }} /> : null}
              Save Limits
            </Button>
          </Grid>
        </Grid>
      </Paper>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={6000}
        onClose={() => setSnackbar(prev => ({ ...prev, open: false }))}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      >
        <Alert severity={snackbar.severity} onClose={() => setSnackbar(prev => ({ ...prev, open: false }))} sx={{ width: "100%" }}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
}
