import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box,
  Typography,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  Chip,
  Button,
  CircularProgress,
  Alert,
  TextField,
  InputAdornment,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Grid,
  MenuItem,
  Select,
  FormControl,
  InputLabel,
  IconButton,
  Menu,
  Tooltip,
  Divider,
} from "@mui/material";
import {
  Search as SearchIcon,
  Settings as ConfigIcon,
  Business as PspIcon,
  Add as AddIcon,
  MoreVert as MoreVertIcon,
  Delete as DeleteIcon,
  CheckCircle as ActivateIcon,
  PauseCircle as SuspendIcon,
  Cancel as TerminateIcon,
} from "@mui/icons-material";
import { useAllPsps } from "../../features/api/queries";
import {
  useRegisterPsp,
  useUpdatePspStatus,
  useDeletePsp,
} from "../../features/api/mutations";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";

const ACCENT = "#8B4049";

const STATUS_COLORS: Record<string, { bg: string; color: string; label: string }> = {
  ACTIVE:     { bg: "#e9f7ef", color: "#27ae60", label: "Active" },
  PENDING:    { bg: "#fef9e7", color: "#d4ac0d", label: "Pending" },
  SUSPENDED:  { bg: "#fdf2e9", color: "#e67e22", label: "Suspended" },
  TERMINATED: { bg: "#fdedec", color: "#c0392b", label: "Terminated" },
};

const COUNTRIES = [
  "KE", "UG", "TZ", "NG", "ZA", "GH", "RW", "ET", "GB", "US", "IN", "AE", "DE", "FR", "SG",
];

const BILLING_PLANS = ["PAY_AS_YOU_GO", "SUBSCRIPTION", "ENTERPRISE"];
const BILLING_CYCLES = ["MONTHLY", "QUARTERLY", "YEARLY"];
const CURRENCIES = ["KES", "USD", "GBP", "EUR", "UGX", "TZS"];

const EMPTY_FORM = {
  pspCode: "",
  legalName: "",
  tradingName: "",
  country: "KE",
  registrationNumber: "",
  taxId: "",
  contactEmail: "",
  contactPhone: "",
  contactAddress: "",
  billingPlan: "PAY_AS_YOU_GO",
  billingCycle: "MONTHLY",
  currency: "KES",
  paymentTerms: 30,
};

export default function PspsListPage() {
  const navigate = useNavigate();
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(25);

  // Create dialog
  const [createOpen, setCreateOpen] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [createError, setCreateError] = useState("");

  // Status action menu
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
  const [menuPspId, setMenuPspId] = useState<number | null>(null);

  // Delete confirmation
  const [deleteTarget, setDeleteTarget] = useState<{ id: number; name: string } | null>(null);

  const { data: psps, isLoading, isError } = useAllPsps();
  const registerMutation = useRegisterPsp();
  const statusMutation = useUpdatePspStatus();
  const deleteMutation = useDeletePsp();

  const rows = (psps ?? []).filter((p: any) => {
    if (!search) return true;
    const q = search.toLowerCase();
    return (
      (p.legalName ?? "").toLowerCase().includes(q) ||
      (p.tradingName ?? "").toLowerCase().includes(q) ||
      (p.pspCode ?? "").toLowerCase().includes(q) ||
      (p.country ?? "").toLowerCase().includes(q) ||
      (p.contactEmail ?? "").toLowerCase().includes(q)
    );
  });

  const paginated = rows.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage);

  const set = (field: string) => (e: React.ChangeEvent<HTMLInputElement | { value: unknown }>) =>
    setForm((f) => ({ ...f, [field]: (e.target as any).value }));

  const handleCreate = async () => {
    setCreateError("");
    if (!form.pspCode.trim()) { setCreateError("PSP Code is required."); return; }
    if (!form.legalName.trim()) { setCreateError("Legal Name is required."); return; }
    if (!form.contactEmail.trim()) { setCreateError("Contact Email is required."); return; }
    try {
      await registerMutation.mutateAsync(form as any);
      setCreateOpen(false);
      setForm(EMPTY_FORM);
    } catch (err: any) {
      setCreateError(err?.message ?? "Registration failed. PSP code may already exist.");
    }
  };

  const handleStatusChange = async (pspId: number, status: string) => {
    setMenuAnchor(null);
    await statusMutation.mutateAsync({ id: pspId, status });
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    await deleteMutation.mutateAsync(deleteTarget.id);
    setDeleteTarget(null);
  };

  return (
    <HokekaPageShell title="PSPs" subtitle="Payment service provider onboarding and configuration" noCard>
    <Box>
      <Box sx={{ display: "flex", alignItems: "center", justifyContent: "flex-end", mb: 0.5 }}>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => setCreateOpen(true)}
          sx={{
            backgroundColor: ACCENT,
            textTransform: "none",
            fontWeight: 600,
            "&:hover": { backgroundColor: "#6b313a" },
          }}
        >
          Register PSP
        </Button>
      </Box>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Manage Payment Service Provider onboarding, status, and CBK reporting configuration.
      </Typography>

      <Box sx={{ display: "flex", justifyContent: "flex-end", mb: 2 }}>
        <TextField
          size="small"
          placeholder="Search PSPs…"
          value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(0); }}
          sx={{ width: 280 }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" sx={{ color: "text.secondary" }} />
              </InputAdornment>
            ),
          }}
        />
      </Box>

      {isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Failed to load PSPs. Ensure the backend is running.
        </Alert>
      )}

      <TableContainer
        component={Paper}
        sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.08)", borderRadius: 2 }}
      >
        <Table size="small">
          <TableHead>
            <TableRow sx={{ backgroundColor: "rgba(0,0,0,0.02)" }}>
              <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>PSP Code</TableCell>
              <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Legal Name</TableCell>
              <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Trading Name</TableCell>
              <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Country</TableCell>
              <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Contact Email</TableCell>
              <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Status</TableCell>
              <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Billing</TableCell>
              <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>CBK</TableCell>
              <TableCell sx={{ color: "text.secondary", fontWeight: 600, textAlign: "right" }}>Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={9} align="center" sx={{ py: 6 }}>
                  <CircularProgress size={28} sx={{ color: ACCENT }} />
                </TableCell>
              </TableRow>
            ) : paginated.length > 0 ? (
              paginated.map((psp: any) => {
                const id = psp.id ?? psp.pspId;
                const statusMeta = STATUS_COLORS[psp.status] ?? { bg: "#f4f6f7", color: "#7f8c8d", label: psp.status ?? "Unknown" };
                const cbkEnabled = psp.cbkReportingEnabled;
                return (
                  <TableRow
                    key={id}
                    hover
                    sx={{ "&:hover": { backgroundColor: `rgba(139,64,73,0.04)` } }}
                  >
                    <TableCell>
                      <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                        <PspIcon sx={{ fontSize: 16, color: ACCENT, opacity: 0.7 }} />
                        <Typography variant="body2" sx={{ fontWeight: 600, fontFamily: "monospace" }}>
                          {psp.pspCode ?? "—"}
                        </Typography>
                      </Box>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontWeight: 500 }}>
                        {psp.legalName ?? "—"}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" color="text.secondary">
                        {psp.tradingName ?? "—"}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" color="text.secondary">
                        {psp.country ?? "—"}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" color="text.secondary" sx={{ fontSize: "0.78rem" }}>
                        {psp.contactEmail ?? "—"}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={statusMeta.label}
                        size="small"
                        sx={{
                          backgroundColor: statusMeta.bg,
                          color: statusMeta.color,
                          fontWeight: 600,
                          fontSize: "0.72rem",
                          height: 22,
                          borderRadius: 1,
                        }}
                      />
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" color="text.secondary" sx={{ fontSize: "0.78rem" }}>
                        {psp.billingPlan ?? "—"}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={cbkEnabled ? "On" : "Off"}
                        size="small"
                        sx={{
                          backgroundColor: cbkEnabled ? "#e9f7ef" : "#f4f6f7",
                          color: cbkEnabled ? "#27ae60" : "#7f8c8d",
                          fontSize: "0.72rem",
                          height: 20,
                          borderRadius: 1,
                        }}
                      />
                    </TableCell>
                    <TableCell align="right">
                      <Box sx={{ display: "flex", gap: 0.5, justifyContent: "flex-end" }}>
                        <Tooltip title="Configure">
                          <Button
                            size="small"
                            variant="outlined"
                            startIcon={<ConfigIcon sx={{ fontSize: 14 }} />}
                            onClick={() => navigate(`/psps/${id}/configure`)}
                            sx={{
                              textTransform: "none",
                              fontSize: "0.75rem",
                              borderColor: ACCENT,
                              color: ACCENT,
                              py: 0.3,
                              "&:hover": { borderColor: "#6b313a", backgroundColor: "rgba(139,64,73,0.06)" },
                            }}
                          >
                            Configure
                          </Button>
                        </Tooltip>
                        <Tooltip title="Status / Actions">
                          <IconButton
                            size="small"
                            onClick={(e) => { setMenuAnchor(e.currentTarget); setMenuPspId(id); }}
                          >
                            <MoreVertIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      </Box>
                    </TableCell>
                  </TableRow>
                );
              })
            ) : (
              <TableRow>
                <TableCell colSpan={9} align="center" sx={{ py: 8, color: "text.disabled" }}>
                  <Typography variant="body1">No PSPs found</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Click "Register PSP" to add the first one.
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
        <TablePagination
          rowsPerPageOptions={[10, 25, 50]}
          component="div"
          count={rows.length}
          rowsPerPage={rowsPerPage}
          page={page}
          onPageChange={(_, p) => setPage(p)}
          onRowsPerPageChange={(e) => { setRowsPerPage(parseInt(e.target.value, 10)); setPage(0); }}
        />
      </TableContainer>

      {/* Status action menu */}
      <Menu
        anchorEl={menuAnchor}
        open={Boolean(menuAnchor)}
        onClose={() => setMenuAnchor(null)}
        transformOrigin={{ horizontal: "right", vertical: "top" }}
        anchorOrigin={{ horizontal: "right", vertical: "bottom" }}
      >
        <MenuItem
          onClick={() => menuPspId && handleStatusChange(menuPspId, "ACTIVE")}
          sx={{ gap: 1.5, fontSize: "0.85rem" }}
        >
          <ActivateIcon sx={{ fontSize: 16, color: "#27ae60" }} /> Activate
        </MenuItem>
        <MenuItem
          onClick={() => menuPspId && handleStatusChange(menuPspId, "SUSPENDED")}
          sx={{ gap: 1.5, fontSize: "0.85rem" }}
        >
          <SuspendIcon sx={{ fontSize: 16, color: "#e67e22" }} /> Suspend
        </MenuItem>
        <MenuItem
          onClick={() => menuPspId && handleStatusChange(menuPspId, "TERMINATED")}
          sx={{ gap: 1.5, fontSize: "0.85rem" }}
        >
          <TerminateIcon sx={{ fontSize: 16, color: "#c0392b" }} /> Terminate
        </MenuItem>
        <Divider />
        <MenuItem
          onClick={() => {
            const psp = (psps ?? []).find((p: any) => (p.id ?? p.pspId) === menuPspId) as any;
            setDeleteTarget({ id: menuPspId!, name: psp?.legalName ?? `PSP ${menuPspId}` });
            setMenuAnchor(null);
          }}
          sx={{ gap: 1.5, fontSize: "0.85rem", color: "error.main" }}
        >
          <DeleteIcon sx={{ fontSize: 16 }} /> Delete PSP
        </MenuItem>
      </Menu>

      {/* Register PSP dialog */}
      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle sx={{ fontWeight: 600 }}>Register New PSP</DialogTitle>
        <DialogContent dividers>
          {createError && <Alert severity="error" sx={{ mb: 2 }}>{createError}</Alert>}

          <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1.5, color: ACCENT }}>
            Identity
          </Typography>
          <Grid container spacing={2} sx={{ mb: 2 }}>
            <Grid item xs={12} md={4}>
              <TextField
                fullWidth size="small" required
                label="PSP Code"
                helperText="Unique identifier e.g. MPESA_KE"
                value={form.pspCode}
                onChange={set("pspCode")}
                inputProps={{ style: { fontFamily: "monospace", textTransform: "uppercase" } }}
                onBlur={(e) => setForm(f => ({ ...f, pspCode: e.target.value.toUpperCase() }))}
              />
            </Grid>
            <Grid item xs={12} md={4}>
              <TextField fullWidth size="small" required label="Legal Name" value={form.legalName} onChange={set("legalName")} />
            </Grid>
            <Grid item xs={12} md={4}>
              <TextField fullWidth size="small" label="Trading Name" value={form.tradingName} onChange={set("tradingName")} />
            </Grid>
            <Grid item xs={12} md={4}>
              <FormControl fullWidth size="small">
                <InputLabel>Country</InputLabel>
                <Select value={form.country} label="Country" onChange={set("country") as any}>
                  {COUNTRIES.map((c) => <MenuItem key={c} value={c}>{c}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12} md={4}>
              <TextField fullWidth size="small" label="Registration Number" value={form.registrationNumber} onChange={set("registrationNumber")} />
            </Grid>
            <Grid item xs={12} md={4}>
              <TextField fullWidth size="small" label="Tax ID / PIN" value={form.taxId} onChange={set("taxId")} />
            </Grid>
          </Grid>

          <Divider sx={{ mb: 2 }} />
          <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1.5, color: ACCENT }}>
            Contact
          </Typography>
          <Grid container spacing={2} sx={{ mb: 2 }}>
            <Grid item xs={12} md={5}>
              <TextField fullWidth size="small" required type="email" label="Contact Email" value={form.contactEmail} onChange={set("contactEmail")} />
            </Grid>
            <Grid item xs={12} md={3}>
              <TextField fullWidth size="small" label="Contact Phone" value={form.contactPhone} onChange={set("contactPhone")} />
            </Grid>
            <Grid item xs={12} md={4}>
              <TextField fullWidth size="small" label="Address" value={form.contactAddress} onChange={set("contactAddress")} />
            </Grid>
          </Grid>

          <Divider sx={{ mb: 2 }} />
          <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1.5, color: ACCENT }}>
            Billing
          </Typography>
          <Grid container spacing={2}>
            <Grid item xs={12} md={3}>
              <FormControl fullWidth size="small">
                <InputLabel>Billing Plan</InputLabel>
                <Select value={form.billingPlan} label="Billing Plan" onChange={set("billingPlan") as any}>
                  {BILLING_PLANS.map((p) => <MenuItem key={p} value={p}>{p.replace(/_/g, " ")}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12} md={3}>
              <FormControl fullWidth size="small">
                <InputLabel>Billing Cycle</InputLabel>
                <Select value={form.billingCycle} label="Billing Cycle" onChange={set("billingCycle") as any}>
                  {BILLING_CYCLES.map((c) => <MenuItem key={c} value={c}>{c}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12} md={3}>
              <FormControl fullWidth size="small">
                <InputLabel>Currency</InputLabel>
                <Select value={form.currency} label="Currency" onChange={set("currency") as any}>
                  {CURRENCIES.map((c) => <MenuItem key={c} value={c}>{c}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12} md={3}>
              <TextField
                fullWidth size="small" type="number" label="Payment Terms (days)"
                value={form.paymentTerms}
                onChange={(e) => setForm(f => ({ ...f, paymentTerms: parseInt(e.target.value) || 30 }))}
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setCreateOpen(false)} sx={{ textTransform: "none" }}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleCreate}
            disabled={registerMutation.isPending}
            sx={{ backgroundColor: ACCENT, textTransform: "none", "&:hover": { backgroundColor: "#6b313a" } }}
          >
            {registerMutation.isPending ? <CircularProgress size={18} sx={{ color: "white" }} /> : "Register PSP"}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete confirmation dialog */}
      <Dialog open={Boolean(deleteTarget)} onClose={() => setDeleteTarget(null)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontWeight: 600, color: "error.main" }}>Delete PSP?</DialogTitle>
        <DialogContent>
          <Typography variant="body2">
            This will permanently delete <strong>{deleteTarget?.name}</strong> and all associated data.
            This action cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setDeleteTarget(null)} sx={{ textTransform: "none" }}>Cancel</Button>
          <Button
            variant="contained"
            color="error"
            onClick={handleDelete}
            disabled={deleteMutation.isPending}
            sx={{ textTransform: "none" }}
          >
            {deleteMutation.isPending ? <CircularProgress size={18} sx={{ color: "white" }} /> : "Delete"}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
    </HokekaPageShell>
  );
}
