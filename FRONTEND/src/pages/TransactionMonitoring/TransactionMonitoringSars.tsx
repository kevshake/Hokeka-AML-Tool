import { useState } from "react";
import {
  Box, Typography, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, Paper, Chip, Dialog, DialogTitle,
  DialogContent, DialogActions, Button, Grid, Divider,
  TextField, MenuItem, Alert, Stack,
} from "@mui/material";
import { useSarReports } from "../../features/api/queries";
import { useCreateSar } from "../../features/api/mutations";
import { useAuth } from "../../contexts/AuthContext";
import type { SarReport } from "../../types";

const statusColors: Record<string, string> = {
  DRAFT: "#95a5a6",
  REVIEW: "#f39c12",
  APPROVED: "#3498db",
  FILED: "#2ecc71",
  REJECTED: "#e74c3c",
  AMENDED: "#8e44ad",
};

const SAR_ACTIVITY_TYPES = [
  "Transaction Structuring",
  "Money Laundering",
  "Fraud",
  "Terrorism Financing",
  "Sanctions Evasion",
  "Other Transaction Activity",
];

const defaultForm = {
  sarReference: "",
  suspiciousActivityType: "",
  narrative: "",
  jurisdiction: "",
};

export default function TransactionMonitoringSars() {
  const { data: sars, isLoading } = useSarReports();
  const [viewSar, setViewSar] = useState<SarReport | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [form, setForm] = useState(defaultForm);
  const [formError, setFormError] = useState<string | null>(null);

  const { user } = useAuth();
  const createSar = useCreateSar();

  const transactionSars = sars?.filter((sar) => sar.suspiciousActivityType?.toLowerCase().includes("transaction")) || [];

  const handleCreate = () => {
    if (!form.sarReference.trim() || !form.suspiciousActivityType || !form.narrative.trim()) {
      setFormError("Reference, Activity Type, and Narrative are required.");
      return;
    }
    setFormError(null);
    createSar.mutate(
      {
        sarReference: form.sarReference.trim(),
        suspiciousActivityType: form.suspiciousActivityType,
        narrative: form.narrative.trim(),
        jurisdiction: form.jurisdiction.trim() || undefined,
        sarType: "INITIAL",
        creatorUserId: user!.id,
      },
      {
        onSuccess: () => {
          setCreateOpen(false);
          setForm(defaultForm);
        },
        onError: (err: unknown) => {
          const msg = (err as { message?: string })?.message || "Failed to create SAR.";
          setFormError(msg);
        },
      }
    );
  };

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 3 }}>
        <Typography variant="h6" sx={{ color: "text.primary" }}>
          Transaction-Related SAR Reports
        </Typography>
        <Button
          variant="contained"
          onClick={() => { setCreateOpen(true); setFormError(null); setForm(defaultForm); }}
          sx={{
            backgroundColor: "#a93226",
            "&:hover": { backgroundColor: "#922b21" },
            textTransform: "none",
            fontWeight: 600,
          }}
        >
          + Create SAR
        </Button>
      </Stack>

      <Paper sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell sx={{ color: "text.secondary" }}>Reference</TableCell>
                <TableCell sx={{ color: "text.secondary" }}>Status</TableCell>
                <TableCell sx={{ color: "text.secondary" }}>Activity Type</TableCell>
                <TableCell sx={{ color: "text.secondary" }}>Created</TableCell>
                <TableCell sx={{ color: "text.secondary" }}>Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {isLoading ? (
                <TableRow>
                  <TableCell colSpan={5} align="center" sx={{ color: "text.disabled", py: 4 }}>
                    Loading SAR reports...
                  </TableCell>
                </TableRow>
              ) : transactionSars.length > 0 ? (
                transactionSars.map((sar) => (
                  <TableRow key={sar.id} hover>
                    <TableCell sx={{ color: "text.primary" }}>{sar.sarReference}</TableCell>
                    <TableCell>
                      <Chip
                        label={sar.status}
                        size="small"
                        sx={{
                          backgroundColor: (statusColors[sar.status] || "#95a5a6") + "20",
                          color: statusColors[sar.status] || "#95a5a6",
                          border: `1px solid ${statusColors[sar.status] || "#95a5a6"}`,
                        }}
                      />
                    </TableCell>
                    <TableCell sx={{ color: "text.primary" }}>{sar.suspiciousActivityType}</TableCell>
                    <TableCell sx={{ color: "text.secondary" }}>
                      {new Date(sar.createdAt).toLocaleDateString()}
                    </TableCell>
                    <TableCell>
                      <Chip
                        label="View"
                        size="small"
                        onClick={() => setViewSar(sar)}
                        sx={{ cursor: "pointer", color: "#a93226", "&:hover": { backgroundColor: "rgba(169,50,38,0.08)" } }}
                      />
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={5} align="center" sx={{ color: "text.disabled", py: 4 }}>
                    No transaction-related SAR reports found
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>

      {/* Create SAR Dialog */}
      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>Create SAR</DialogTitle>
        <Divider />
        <DialogContent sx={{ pt: 2 }}>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2}>
            <Grid item xs={12}>
              <TextField
                label="SAR Reference"
                fullWidth
                required
                value={form.sarReference}
                onChange={(e) => setForm((f) => ({ ...f, sarReference: e.target.value }))}
                size="small"
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                label="Activity Type"
                fullWidth
                required
                select
                value={form.suspiciousActivityType}
                onChange={(e) => setForm((f) => ({ ...f, suspiciousActivityType: e.target.value }))}
                size="small"
              >
                {SAR_ACTIVITY_TYPES.map((t) => (
                  <MenuItem key={t} value={t}>{t}</MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid item xs={12}>
              <TextField
                label="Narrative"
                fullWidth
                required
                multiline
                rows={4}
                value={form.narrative}
                onChange={(e) => setForm((f) => ({ ...f, narrative: e.target.value }))}
                size="small"
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                label="Jurisdiction (optional)"
                fullWidth
                value={form.jurisdiction}
                onChange={(e) => setForm((f) => ({ ...f, jurisdiction: e.target.value }))}
                size="small"
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setCreateOpen(false)} sx={{ textTransform: "none" }}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleCreate}
            disabled={createSar.isPending}
            sx={{
              backgroundColor: "#a93226",
              "&:hover": { backgroundColor: "#922b21" },
              textTransform: "none",
              fontWeight: 600,
            }}
          >
            {createSar.isPending ? "Creating..." : "Create SAR"}
          </Button>
        </DialogActions>
      </Dialog>

      {/* SAR Detail Modal */}
      <Dialog open={!!viewSar} onClose={() => setViewSar(null)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <Box>
            <Typography variant="h6" sx={{ fontFamily: "monospace", fontWeight: 700 }}>
              {viewSar?.sarReference}
            </Typography>
            <Typography variant="caption" color="text.secondary">SAR Report — {viewSar?.sarType}</Typography>
          </Box>
          {viewSar && (
            <Chip
              label={viewSar.status}
              size="small"
              sx={{
                backgroundColor: (statusColors[viewSar.status] || "#95a5a6") + "20",
                color: statusColors[viewSar.status] || "#95a5a6",
                fontWeight: 600,
              }}
            />
          )}
        </DialogTitle>
        <Divider />
        {viewSar && (
          <DialogContent sx={{ pt: 2 }}>
            <Grid container spacing={2}>
              <Grid item xs={12}>
                <Typography variant="overline" color="text.secondary">Activity Type</Typography>
                <Typography variant="body2" sx={{ mt: 0.5 }}>{viewSar.suspiciousActivityType}</Typography>
              </Grid>
              {viewSar.narrative && (
                <Grid item xs={12}>
                  <Typography variant="overline" color="text.secondary">Narrative</Typography>
                  <Typography variant="body2" sx={{ mt: 0.5, lineHeight: 1.6 }}>{viewSar.narrative}</Typography>
                </Grid>
              )}
              <Grid item xs={12} sm={6}>
                <Typography variant="overline" color="text.secondary">Jurisdiction</Typography>
                <Typography variant="body2" sx={{ mt: 0.5 }}>{viewSar.jurisdiction || "—"}</Typography>
              </Grid>
              <Grid item xs={12} sm={6}>
                <Typography variant="overline" color="text.secondary">Created</Typography>
                <Typography variant="body2" sx={{ mt: 0.5 }}>{new Date(viewSar.createdAt).toLocaleString()}</Typography>
              </Grid>
              {viewSar.filedAt && (
                <Grid item xs={12} sm={6}>
                  <Typography variant="overline" color="text.secondary">Filed</Typography>
                  <Typography variant="body2" sx={{ mt: 0.5, color: "#2ecc71" }}>{new Date(viewSar.filedAt).toLocaleString()}</Typography>
                </Grid>
              )}
              {viewSar.filingReference && (
                <Grid item xs={12} sm={6}>
                  <Typography variant="overline" color="text.secondary">Filing Reference</Typography>
                  <Typography variant="body2" sx={{ mt: 0.5, fontFamily: "monospace" }}>{viewSar.filingReference}</Typography>
                </Grid>
              )}
            </Grid>
          </DialogContent>
        )}
        <DialogActions>
          <Button onClick={() => setViewSar(null)} sx={{ textTransform: "none" }}>Close</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
