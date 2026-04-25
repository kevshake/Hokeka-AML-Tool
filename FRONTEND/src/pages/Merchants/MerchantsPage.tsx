import {
  Box,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
  Chip,
  Button,
  TablePagination,
  CircularProgress,
  Tooltip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Grid,
  Divider,
  TextField,
  MenuItem,
  Snackbar,
  Alert,
} from "@mui/material";
import { useState } from "react";
import { useMerchants } from "../../features/api/queries";
import { useCreateMerchant, type CreateMerchantRequest } from "../../features/api/mutations";
import type { Merchant } from "../../types";

const riskColors: Record<string, string> = {
  LOW: "#2ecc71",
  MEDIUM: "#f39c12",
  HIGH: "#e74c3c",
};

const formatScore = (score: number | null | undefined): string => {
  if (score === null || score === undefined || isNaN(score)) return "—";
  return score.toFixed(1);
};

const kycStatuses = ["PENDING", "VERIFIED", "REJECTED", "UNDER_REVIEW"];
const contractStatuses = ["ACTIVE", "INACTIVE", "SUSPENDED", "PENDING"];

export default function MerchantsPage() {
  const [page, setPage] = useState({ index: 0, size: 25 });
  const [viewMerchant, setViewMerchant] = useState<Merchant | null>(null);
  const [addOpen, setAddOpen] = useState(false);
  const [formData, setFormData] = useState<CreateMerchantRequest>({
    merchantId: "",
    businessName: "",
    mcc: "",
    kycStatus: "",
    contractStatus: "",
  });
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: "success" | "error" }>({
    open: false, message: "", severity: "success",
  });

  const { data: merchants, isLoading, isError, error } = useMerchants({
    page: page.index,
    size: page.size,
  });

  const createMerchant = useCreateMerchant();

  const handleExportCSV = () => {
    const content = merchants?.content || [];
    if (!content.length) return;
    const headers = ["Merchant ID", "Business Name", "MCC", "Risk Level", "KRS", "CRA", "KYC Status", "Contract Status"];
    const rows = content.map(m => [
      m.merchantId,
      (m.businessName || "").replace(/,/g, ";"),
      m.mcc || "",
      m.riskLevel || "",
      formatScore(m.krs),
      formatScore(m.cra),
      m.kycStatus || "",
      m.contractStatus || "",
    ]);
    const csv = [headers, ...rows].map(r => r.join(",")).join("\n");
    const blob = new Blob([csv], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `merchants-${new Date().toISOString().split("T")[0]}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleAddMerchant = async () => {
    if (!formData.merchantId.trim() || !formData.businessName.trim()) return;
    try {
      await createMerchant.mutateAsync(formData);
      setAddOpen(false);
      setFormData({ merchantId: "", businessName: "", mcc: "", kycStatus: "", contractStatus: "" });
      setSnackbar({ open: true, message: "Merchant onboarded successfully.", severity: "success" });
    } catch (err: any) {
      setSnackbar({ open: true, message: err?.message || "Failed to onboard merchant.", severity: "error" });
    }
  };

  return (
    <Box>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 2 }}>
        <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
          <Typography variant="h6" sx={{ color: "text.primary", fontWeight: 600 }}>
            Merchants
          </Typography>
          <Button
            size="small"
            variant="outlined"
            disabled={!merchants?.content?.length}
            onClick={handleExportCSV}
            sx={{ textTransform: "none", color: "text.secondary", borderColor: "rgba(0,0,0,0.2)", fontSize: "0.75rem" }}
          >
            Export CSV
          </Button>
        </Box>
        <Tooltip title="Onboard a new merchant to the system." arrow enterDelay={2000}>
          <Button
            variant="contained"
            onClick={() => setAddOpen(true)}
            sx={{ backgroundColor: "#a93226", "&:hover": { backgroundColor: "#922b21" } }}
          >
            Add Merchant
          </Button>
        </Tooltip>
      </Box>

      <TableContainer component={Paper} sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell sx={{ color: "text.secondary" }}>Merchant ID</TableCell>
              <TableCell sx={{ color: "text.secondary" }}>Business Name</TableCell>
              <TableCell sx={{ color: "text.secondary" }}>MCC</TableCell>
              <TableCell sx={{ color: "text.secondary" }}>Risk Level</TableCell>
              <TableCell sx={{ color: "text.secondary" }}>KRS</TableCell>
              <TableCell sx={{ color: "text.secondary" }}>CRA</TableCell>
              <TableCell sx={{ color: "text.secondary" }}>KYC Status</TableCell>
              <TableCell sx={{ color: "text.secondary" }}>Contract Status</TableCell>
              <TableCell sx={{ color: "text.secondary" }}>Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={9} align="center" sx={{ color: "text.disabled", py: 2 }}>
                  <CircularProgress size={24} />
                </TableCell>
              </TableRow>
            ) : isError ? (
              <TableRow>
                <TableCell colSpan={9} align="center" sx={{ color: "#e74c3c", py: 2 }}>
                  Error loading merchants: {error instanceof Error ? error.message : "Unknown error"}
                </TableCell>
              </TableRow>
            ) : merchants?.content && merchants.content.length > 0 ? (
              merchants.content.map((merchant) => (
                <TableRow key={merchant.id} hover>
                  <TableCell sx={{ color: "text.primary", py: 2, fontFamily: "monospace" }}>{merchant.merchantId}</TableCell>
                  <TableCell sx={{ color: "text.primary", py: 2 }}>{merchant.businessName}</TableCell>
                  <TableCell sx={{ color: "text.primary", py: 2 }}>{merchant.mcc || "—"}</TableCell>
                  <TableCell sx={{ py: 2 }}>
                    {merchant.riskLevel ? (
                      <Chip
                        label={merchant.riskLevel}
                        size="small"
                        sx={{
                          backgroundColor: riskColors[merchant.riskLevel] + "20",
                          color: riskColors[merchant.riskLevel],
                          border: `1px solid ${riskColors[merchant.riskLevel]}`,
                          fontWeight: 600,
                        }}
                      />
                    ) : "—"}
                  </TableCell>
                  <TableCell sx={{ color: "text.primary", py: 2 }}>{formatScore(merchant.krs)}</TableCell>
                  <TableCell sx={{ color: "text.primary", py: 2 }}>{formatScore(merchant.cra)}</TableCell>
                  <TableCell sx={{ color: "text.primary", py: 2 }}>{merchant.kycStatus || "—"}</TableCell>
                  <TableCell sx={{ color: "text.primary", py: 2 }}>{merchant.contractStatus || "—"}</TableCell>
                  <TableCell sx={{ py: 2 }}>
                    <Button
                      size="small"
                      sx={{ color: "#a93226", textTransform: "none" }}
                      onClick={() => setViewMerchant(merchant)}
                    >
                      View
                    </Button>
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell colSpan={9} align="center" sx={{ color: "text.disabled", py: 2 }}>
                  No merchants found
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
        <TablePagination
          rowsPerPageOptions={[10, 25, 50, 100]}
          component="div"
          count={merchants?.totalElements || 0}
          rowsPerPage={page.size}
          page={page.index}
          onPageChange={(_, newPage) => setPage(prev => ({ ...prev, index: newPage }))}
          onRowsPerPageChange={(e) => setPage({ index: 0, size: parseInt(e.target.value, 10) })}
        />
      </TableContainer>

      {/* Merchant Detail Modal */}
      <Dialog open={!!viewMerchant} onClose={() => setViewMerchant(null)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <Box>
            <Typography variant="h6">{viewMerchant?.businessName}</Typography>
            <Typography variant="caption" color="text.secondary" sx={{ fontFamily: "monospace" }}>
              {viewMerchant?.merchantId}
            </Typography>
          </Box>
          {viewMerchant?.riskLevel && (
            <Chip
              label={viewMerchant.riskLevel}
              size="small"
              sx={{
                backgroundColor: riskColors[viewMerchant.riskLevel] + "20",
                color: riskColors[viewMerchant.riskLevel],
                fontWeight: 600,
              }}
            />
          )}
        </DialogTitle>
        <Divider />
        {viewMerchant && (
          <DialogContent sx={{ pt: 2 }}>
            <Grid container spacing={2}>
              {viewMerchant.mcc && (
                <Grid item xs={12} sm={6}>
                  <Typography variant="overline" color="text.secondary">MCC Code</Typography>
                  <Typography variant="body2" sx={{ mt: 0.5, fontFamily: "monospace" }}>{viewMerchant.mcc}</Typography>
                </Grid>
              )}
              <Grid item xs={12} sm={6}>
                <Typography variant="overline" color="text.secondary">KYC Status</Typography>
                <Typography variant="body2" sx={{ mt: 0.5 }}>{viewMerchant.kycStatus || "Not set"}</Typography>
              </Grid>
              <Grid item xs={12} sm={6}>
                <Typography variant="overline" color="text.secondary">Contract Status</Typography>
                <Typography variant="body2" sx={{ mt: 0.5 }}>{viewMerchant.contractStatus || "Not set"}</Typography>
              </Grid>
              <Grid item xs={12}>
                <Divider sx={{ my: 1 }} />
                <Typography variant="overline" color="text.secondary">Risk Scores</Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography variant="body2" color="text.secondary">KRS (Know Your Risk Score)</Typography>
                <Typography variant="h6" sx={{ fontWeight: 700, color: viewMerchant.krs && viewMerchant.krs > 7 ? "#e74c3c" : viewMerchant.krs && viewMerchant.krs > 4 ? "#f39c12" : "text.primary" }}>
                  {formatScore(viewMerchant.krs)}
                </Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography variant="body2" color="text.secondary">CRA (Customer Risk Assessment)</Typography>
                <Typography variant="h6" sx={{ fontWeight: 700, color: viewMerchant.cra && viewMerchant.cra > 7 ? "#e74c3c" : viewMerchant.cra && viewMerchant.cra > 4 ? "#f39c12" : "text.primary" }}>
                  {formatScore(viewMerchant.cra)}
                </Typography>
              </Grid>
            </Grid>
          </DialogContent>
        )}
        <DialogActions>
          <Button onClick={() => setViewMerchant(null)} sx={{ textTransform: "none" }}>Close</Button>
        </DialogActions>
      </Dialog>

      {/* Add Merchant Modal */}
      <Dialog open={addOpen} onClose={() => setAddOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Onboard New Merchant</DialogTitle>
        <Divider />
        <DialogContent sx={{ pt: 2 }}>
          <Grid container spacing={2}>
            <Grid item xs={12} sm={6}>
              <TextField
                label="Merchant ID"
                value={formData.merchantId}
                onChange={(e) => setFormData(prev => ({ ...prev, merchantId: e.target.value }))}
                fullWidth
                size="small"
                required
                placeholder="e.g. MRC-00123"
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                label="Business Name"
                value={formData.businessName}
                onChange={(e) => setFormData(prev => ({ ...prev, businessName: e.target.value }))}
                fullWidth
                size="small"
                required
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                label="MCC Code"
                value={formData.mcc}
                onChange={(e) => setFormData(prev => ({ ...prev, mcc: e.target.value }))}
                fullWidth
                size="small"
                placeholder="e.g. 5411"
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                select
                label="KYC Status"
                value={formData.kycStatus}
                onChange={(e) => setFormData(prev => ({ ...prev, kycStatus: e.target.value }))}
                fullWidth
                size="small"
              >
                <MenuItem value="">Not Set</MenuItem>
                {kycStatuses.map(s => <MenuItem key={s} value={s}>{s}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                select
                label="Contract Status"
                value={formData.contractStatus}
                onChange={(e) => setFormData(prev => ({ ...prev, contractStatus: e.target.value }))}
                fullWidth
                size="small"
              >
                <MenuItem value="">Not Set</MenuItem>
                {contractStatuses.map(s => <MenuItem key={s} value={s}>{s}</MenuItem>)}
              </TextField>
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAddOpen(false)} sx={{ textTransform: "none" }}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleAddMerchant}
            disabled={!formData.merchantId.trim() || !formData.businessName.trim() || createMerchant.isPending}
            sx={{ backgroundColor: "#a93226", "&:hover": { backgroundColor: "#922b21" }, textTransform: "none" }}
          >
            {createMerchant.isPending ? <CircularProgress size={18} sx={{ color: "white" }} /> : "Onboard Merchant"}
          </Button>
        </DialogActions>
      </Dialog>

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
