import { useState } from "react";
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
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Snackbar,
  Stack,
  Tab,
  Tabs,
  Card,
  CardContent,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  IconButton,
  Tooltip,
  Grid,
} from "@mui/material";
import {
  AttachMoney as MoneyIcon,
  Receipt as ReceiptIcon,
  CreditCard as CreditCardIcon,
  BarChart as BarChartIcon,
  Edit as EditIcon,
  Cancel as CancelIcon,
  Add as AddIcon,
  PictureAsPdf as PdfIcon,
  CheckCircle as CheckIcon,
  Warning as WarningIcon,
  Close as CloseIcon,
} from "@mui/icons-material";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip as RechartsTooltip,
  ResponsiveContainer,
} from "recharts";
import {
  useRevenueSummary,
  useInvoices,
  useOverdueInvoices,
  useSubscriptions,
  usePricingTiers,
  useUsageSummary,
  useCurrentUsage,
  useAllPsps,
} from "../../features/api/queries";
import {
  useUpdateInvoiceStatus,
  useCreateSubscription,
  useUpdateSubscription,
  useCancelSubscription,
} from "../../features/api/mutations";
import type { Invoice, Subscription, SubscriptionRequest } from "../../types/billing";
import type { Psp } from "../../types";
import { getApiUrl } from "../../config/api";

const ACCENT = "#8B4049";

// ─── Status chip helpers ──────────────────────────────────────────────────────

const INVOICE_STATUS_COLORS: Record<string, { bg: string; color: string }> = {
  PAID:      { bg: "#e9f7ef", color: "#27ae60" },
  OVERDUE:   { bg: "#fdedec", color: "#c0392b" },
  SENT:      { bg: "#eaf4fb", color: "#2980b9" },
  DRAFT:     { bg: "#f4f6f7", color: "#7f8c8d" },
  CANCELLED: { bg: "#f4f6f7", color: "#7f8c8d" },
};

const SUBSCRIPTION_STATUS_COLORS: Record<string, { bg: string; color: string }> = {
  ACTIVE:    { bg: "#e9f7ef", color: "#27ae60" },
  TRIAL:     { bg: "#eaf4fb", color: "#2980b9" },
  CANCELLED: { bg: "#f4f6f7", color: "#7f8c8d" },
  EXPIRED:   { bg: "#fef5e7", color: "#e67e22" },
};

function StatusChip({ status, colorMap }: { status: string; colorMap: Record<string, { bg: string; color: string }> }) {
  const cfg = colorMap[status] ?? { bg: "#f4f6f7", color: "#7f8c8d" };
  return (
    <Chip
      label={status}
      size="small"
      sx={{
        backgroundColor: cfg.bg,
        color: cfg.color,
        fontWeight: 500,
        fontSize: "0.72rem",
        height: 22,
        borderRadius: 1,
      }}
    />
  );
}

function fmt(amount: number, currency = "USD") {
  return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(amount);
}

function fmtDate(iso?: string) {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString();
}

// ─── KPI Card ────────────────────────────────────────────────────────────────

interface KpiCardProps {
  title: string;
  value: string;
  icon: React.ReactNode;
  color: string;
  subtitle?: string;
}

function KpiCard({ title, value, icon, color, subtitle }: KpiCardProps) {
  return (
    <Card sx={{ border: "1px solid rgba(0,0,0,0.08)", borderRadius: 2, flex: 1 }}>
      <CardContent sx={{ pb: "16px !important" }}>
        <Box sx={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between" }}>
          <Box>
            <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, textTransform: "uppercase", fontSize: "0.68rem", letterSpacing: "0.4px" }}>
              {title}
            </Typography>
            <Typography variant="h5" sx={{ fontWeight: 700, mt: 0.5, color }}>
              {value}
            </Typography>
            {subtitle && (
              <Typography variant="caption" color="text.secondary" sx={{ fontSize: "0.72rem" }}>
                {subtitle}
              </Typography>
            )}
          </Box>
          <Box sx={{ bgcolor: `${color}18`, borderRadius: "10px", p: 1, mt: 0.5 }}>
            <Box sx={{ color, display: "flex" }}>{icon}</Box>
          </Box>
        </Box>
      </CardContent>
    </Card>
  );
}

// ─── Tab 1: Revenue Dashboard ────────────────────────────────────────────────

function RevenueTab() {
  const { data: summary, isLoading, isError } = useRevenueSummary();
  const { data: overdue = [] } = useOverdueInvoices();

  if (isLoading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", py: 6 }}>
        <CircularProgress size={28} sx={{ color: ACCENT }} />
      </Box>
    );
  }

  if (isError || !summary) {
    return <Alert severity="error" sx={{ mt: 2 }}>Failed to load revenue summary.</Alert>;
  }

  const currency = summary.currency || "USD";

  const chartData = [
    { name: "Paid", value: summary.currentMonthRevenuePaid },
    { name: "Expected", value: summary.currentMonthRevenueExpected },
    { name: "Overdue", value: summary.overdueAmount },
  ];

  return (
    <Box>
      {/* KPI Cards */}
      <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap", mb: 3 }}>
        <KpiCard
          title="Monthly Revenue (Paid)"
          value={fmt(summary.currentMonthRevenuePaid, currency)}
          icon={<MoneyIcon />}
          color="#27ae60"
          subtitle={`${summary.paidInvoicesThisMonth} invoices paid`}
        />
        <KpiCard
          title="Expected Revenue"
          value={fmt(summary.currentMonthRevenueExpected, currency)}
          icon={<BarChartIcon />}
          color="#2980b9"
        />
        <KpiCard
          title="Overdue Amount"
          value={fmt(summary.overdueAmount, currency)}
          icon={<WarningIcon />}
          color="#c0392b"
          subtitle={`${summary.overdueInvoicesCount} overdue invoices`}
        />
        <KpiCard
          title="Active Subscriptions"
          value={String(summary.activeSubscriptions)}
          icon={<CreditCardIcon />}
          color={ACCENT}
        />
      </Box>

      {/* Revenue Chart */}
      <Paper sx={{ border: "1px solid rgba(0,0,0,0.08)", borderRadius: 2, p: 2, mb: 3 }}>
        <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 2 }}>
          Current Month Revenue Breakdown
        </Typography>
        <ResponsiveContainer width="100%" height={220}>
          <BarChart data={chartData} margin={{ top: 4, right: 16, left: 0, bottom: 4 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="rgba(0,0,0,0.06)" />
            <XAxis dataKey="name" tick={{ fontSize: 12 }} />
            <YAxis tick={{ fontSize: 12 }} tickFormatter={(v) => `$${(v / 1000).toFixed(0)}k`} />
            <RechartsTooltip formatter={(v) => typeof v === "number" ? fmt(v, currency) : "—"} />
            <Bar dataKey="value" fill={ACCENT} radius={[4, 4, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </Paper>

      {/* Overdue Invoices Alert */}
      {overdue.length > 0 && (
        <Paper sx={{ border: "1px solid rgba(192,57,43,0.2)", borderRadius: 2, p: 2 }}>
          <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1.5, color: "#c0392b" }}>
            Overdue Invoices ({overdue.length})
          </Typography>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow sx={{ backgroundColor: "rgba(0,0,0,0.02)" }}>
                  <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Invoice #</TableCell>
                  <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>PSP</TableCell>
                  <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Amount</TableCell>
                  <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Due Date</TableCell>
                  <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Days Overdue</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {overdue.map((inv) => {
                  const due = new Date(inv.dueDate);
                  const daysOverdue = Math.floor((Date.now() - due.getTime()) / 86_400_000);
                  return (
                    <TableRow key={inv.invoiceId} hover>
                      <TableCell>
                        <Typography variant="body2" sx={{ fontFamily: "monospace", fontSize: "0.78rem" }}>
                          {inv.invoiceNumber}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">{inv.pspName ?? `PSP ${inv.pspId}`}</Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" sx={{ fontWeight: 600, color: "#c0392b" }}>
                          {fmt(inv.totalAmount, inv.currency)}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" color="text.secondary">{fmtDate(inv.dueDate)}</Typography>
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={`${daysOverdue}d overdue`}
                          size="small"
                          sx={{ bgcolor: "#fdedec", color: "#c0392b", fontWeight: 500, fontSize: "0.72rem", height: 22, borderRadius: 1 }}
                        />
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        </Paper>
      )}
    </Box>
  );
}

// ─── Tab 2: Subscriptions ────────────────────────────────────────────────────

const EMPTY_SUB_FORM: SubscriptionRequest = {
  pspId: 0,
  tierCode: "",
  billingCycle: "MONTHLY",
  billingCurrency: "USD",
  discountPercentage: undefined,
  contractStart: new Date().toISOString().split("T")[0],
  contractEnd: undefined,
  trialEndsAt: undefined,
  notes: undefined,
};

function SubscriptionsTab() {
  const { data: subscriptions = [], isLoading, isError } = useSubscriptions();
  const { data: tiers = [] } = usePricingTiers();
  const { data: psps = [] } = useAllPsps();

  const createSub = useCreateSubscription();
  const updateSub = useUpdateSubscription();
  const cancelSub = useCancelSubscription();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<SubscriptionRequest>({ ...EMPTY_SUB_FORM });
  const [cancelTarget, setCancelTarget] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [toast, setToast] = useState<{ open: boolean; severity: "success" | "error"; message: string }>({ open: false, severity: "success", message: "" });

  const openCreate = () => {
    setForm({ ...EMPTY_SUB_FORM });
    setEditingId(null);
    setDialogOpen(true);
  };

  const openEdit = (sub: Subscription) => {
    setForm({
      pspId: sub.pspId,
      tierCode: sub.tierCode,
      billingCycle: sub.billingCycle as "MONTHLY" | "ANNUAL",
      billingCurrency: sub.billingCurrency,
      discountPercentage: sub.discountPercentage,
      contractStart: sub.contractStart?.split("T")[0] ?? "",
      contractEnd: sub.contractEnd?.split("T")[0],
      trialEndsAt: sub.trialEndsAt?.split("T")[0],
      notes: undefined,
    });
    setEditingId(sub.subscriptionId);
    setDialogOpen(true);
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      if (editingId != null) {
        await updateSub.mutateAsync({ id: editingId, ...form });
        setToast({ open: true, severity: "success", message: "Subscription updated." });
      } else {
        await createSub.mutateAsync(form);
        setToast({ open: true, severity: "success", message: "Subscription created." });
      }
      setDialogOpen(false);
    } catch {
      setToast({ open: true, severity: "error", message: "Failed to save subscription." });
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = async () => {
    if (cancelTarget == null) return;
    setCancelling(true);
    try {
      await cancelSub.mutateAsync(cancelTarget);
      setToast({ open: true, severity: "success", message: "Subscription cancelled." });
    } catch {
      setToast({ open: true, severity: "error", message: "Failed to cancel subscription." });
    } finally {
      setCancelling(false);
      setCancelTarget(null);
    }
  };

  const pspName = (id: number) => {
    const p = (psps as Psp[]).find((x) => (x.id ?? (x as unknown as { pspId?: number }).pspId) === id);
    return p ? (p.legalName ?? p.tradingName ?? `PSP ${id}`) : `PSP ${id}`;
  };

  return (
    <Box>
      <Box sx={{ display: "flex", justifyContent: "flex-end", mb: 2 }}>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={openCreate}
          sx={{ textTransform: "none", bgcolor: ACCENT, "&:hover": { bgcolor: "#6b313a" } }}
        >
          New Subscription
        </Button>
      </Box>

      {isError && <Alert severity="error" sx={{ mb: 2 }}>Failed to load subscriptions.</Alert>}

      <TableContainer component={Paper} sx={{ border: "1px solid rgba(0,0,0,0.08)", borderRadius: 2 }}>
        <Table size="small">
          <TableHead>
            <TableRow sx={{ backgroundColor: "rgba(0,0,0,0.02)" }}>
              {["PSP", "Tier", "Billing Cycle", "Currency", "Status", "Contract Start", "Contract End", "Discount", "Actions"].map((h) => (
                <TableCell key={h} sx={{ color: "text.secondary", fontWeight: 600 }}>{h}</TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={9} align="center" sx={{ py: 6 }}>
                  <CircularProgress size={28} sx={{ color: ACCENT }} />
                </TableCell>
              </TableRow>
            ) : subscriptions.length > 0 ? (
              subscriptions.map((sub) => (
                <TableRow key={sub.subscriptionId} hover sx={{ "&:hover": { bgcolor: "rgba(139,64,73,0.04)" } }}>
                  <TableCell><Typography variant="body2" sx={{ fontWeight: 500 }}>{sub.pspName ?? pspName(sub.pspId)}</Typography></TableCell>
                  <TableCell><Typography variant="body2">{sub.tierName ?? sub.tierCode}</Typography></TableCell>
                  <TableCell><Typography variant="body2" color="text.secondary">{sub.billingCycle}</Typography></TableCell>
                  <TableCell><Typography variant="body2" color="text.secondary">{sub.billingCurrency}</Typography></TableCell>
                  <TableCell><StatusChip status={sub.status} colorMap={SUBSCRIPTION_STATUS_COLORS} /></TableCell>
                  <TableCell><Typography variant="body2" color="text.secondary">{fmtDate(sub.contractStart)}</Typography></TableCell>
                  <TableCell><Typography variant="body2" color="text.secondary">{fmtDate(sub.contractEnd)}</Typography></TableCell>
                  <TableCell>
                    <Typography variant="body2" color="text.secondary">
                      {sub.discountPercentage ? `${sub.discountPercentage}%` : "—"}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Stack direction="row" spacing={0.5}>
                      <Tooltip title="Edit">
                        <IconButton size="small" onClick={() => openEdit(sub)} sx={{ color: ACCENT }}>
                          <EditIcon sx={{ fontSize: 16 }} />
                        </IconButton>
                      </Tooltip>
                      {sub.status !== "CANCELLED" && (
                        <Tooltip title="Cancel">
                          <IconButton size="small" onClick={() => setCancelTarget(sub.subscriptionId)} sx={{ color: "#c0392b" }}>
                            <CancelIcon sx={{ fontSize: 16 }} />
                          </IconButton>
                        </Tooltip>
                      )}
                    </Stack>
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell colSpan={9} align="center" sx={{ py: 8, color: "text.disabled" }}>
                  <Typography variant="body1">No subscriptions found</Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Create / Edit Dialog */}
      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <Typography variant="h6" sx={{ fontWeight: 600 }}>
            {editingId != null ? "Edit Subscription" : "New Subscription"}
          </Typography>
          <IconButton size="small" onClick={() => setDialogOpen(false)}><CloseIcon /></IconButton>
        </DialogTitle>
        <DialogContent dividers>
          <Grid container spacing={2} sx={{ pt: 1 }}>
            <Grid item xs={12}>
              <FormControl fullWidth size="small">
                <InputLabel>PSP</InputLabel>
                <Select
                  value={form.pspId || ""}
                  label="PSP"
                  onChange={(e) => setForm((f) => ({ ...f, pspId: Number(e.target.value) }))}
                >
                  {(psps as Psp[]).map((p) => {
                    const id = p.id ?? (p as unknown as { pspId?: number }).pspId ?? 0;
                    return (
                      <MenuItem key={id} value={id}>
                        {p.legalName ?? p.tradingName ?? `PSP ${id}`}
                      </MenuItem>
                    );
                  })}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={6}>
              <FormControl fullWidth size="small">
                <InputLabel>Pricing Tier</InputLabel>
                <Select
                  value={form.tierCode}
                  label="Pricing Tier"
                  onChange={(e) => setForm((f) => ({ ...f, tierCode: e.target.value }))}
                >
                  {tiers.map((t) => (
                    <MenuItem key={t.tierCode} value={t.tierCode}>
                      {t.tierName} ({fmt(t.monthlyFeeUsd, "USD")}/mo)
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={6}>
              <FormControl fullWidth size="small">
                <InputLabel>Billing Cycle</InputLabel>
                <Select
                  value={form.billingCycle}
                  label="Billing Cycle"
                  onChange={(e) => setForm((f) => ({ ...f, billingCycle: e.target.value as "MONTHLY" | "ANNUAL" }))}
                >
                  <MenuItem value="MONTHLY">Monthly</MenuItem>
                  <MenuItem value="ANNUAL">Annual</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={6}>
              <TextField
                label="Currency"
                size="small"
                fullWidth
                value={form.billingCurrency}
                onChange={(e) => setForm((f) => ({ ...f, billingCurrency: e.target.value.toUpperCase() }))}
              />
            </Grid>
            <Grid item xs={6}>
              <TextField
                label="Discount %"
                size="small"
                fullWidth
                type="number"
                inputProps={{ min: 0, max: 100, step: 0.5 }}
                value={form.discountPercentage ?? ""}
                onChange={(e) => setForm((f) => ({ ...f, discountPercentage: e.target.value ? Number(e.target.value) : undefined }))}
              />
            </Grid>
            <Grid item xs={6}>
              <TextField
                label="Contract Start"
                size="small"
                fullWidth
                type="date"
                InputLabelProps={{ shrink: true }}
                value={form.contractStart}
                onChange={(e) => setForm((f) => ({ ...f, contractStart: e.target.value }))}
              />
            </Grid>
            <Grid item xs={6}>
              <TextField
                label="Contract End"
                size="small"
                fullWidth
                type="date"
                InputLabelProps={{ shrink: true }}
                value={form.contractEnd ?? ""}
                onChange={(e) => setForm((f) => ({ ...f, contractEnd: e.target.value || undefined }))}
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                label="Trial Ends At"
                size="small"
                fullWidth
                type="date"
                InputLabelProps={{ shrink: true }}
                value={form.trialEndsAt ?? ""}
                onChange={(e) => setForm((f) => ({ ...f, trialEndsAt: e.target.value || undefined }))}
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                label="Notes"
                size="small"
                fullWidth
                multiline
                rows={2}
                value={form.notes ?? ""}
                onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value || undefined }))}
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setDialogOpen(false)} sx={{ textTransform: "none" }}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleSave}
            disabled={saving || !form.pspId || !form.tierCode}
            startIcon={saving ? <CircularProgress size={16} /> : undefined}
            sx={{ textTransform: "none", bgcolor: ACCENT, "&:hover": { bgcolor: "#6b313a" } }}
          >
            {saving ? "Saving…" : "Save"}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Cancel Confirmation Dialog */}
      <Dialog open={cancelTarget != null} onClose={() => setCancelTarget(null)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontWeight: 600 }}>Cancel Subscription</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary">
            Are you sure you want to cancel this subscription? This action cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setCancelTarget(null)} sx={{ textTransform: "none" }}>Keep Active</Button>
          <Button
            variant="contained"
            color="error"
            onClick={handleCancel}
            disabled={cancelling}
            startIcon={cancelling ? <CircularProgress size={16} /> : undefined}
            sx={{ textTransform: "none" }}
          >
            {cancelling ? "Cancelling…" : "Cancel Subscription"}
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar open={toast.open} autoHideDuration={4000} onClose={() => setToast((t) => ({ ...t, open: false }))} anchorOrigin={{ vertical: "bottom", horizontal: "center" }}>
        <Alert severity={toast.severity} onClose={() => setToast((t) => ({ ...t, open: false }))} variant="filled">{toast.message}</Alert>
      </Snackbar>
    </Box>
  );
}

// ─── Tab 3: Invoices ─────────────────────────────────────────────────────────

const PAYMENT_METHODS = ["BANK_TRANSFER", "CREDIT_CARD", "WIRE_TRANSFER", "CHEQUE", "CRYPTO", "OTHER"];

interface MarkPaidForm {
  status: string;
  paymentReference: string;
  paymentMethod: string;
  paymentAmount: string;
}

function InvoicesTab() {
  const { data: psps = [] } = useAllPsps();
  const [filterPspId, setFilterPspId] = useState<string>("");
  const [filterStatus, setFilterStatus] = useState<string>("");
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(25);

  const { data: invoicesPage, isLoading, isError } = useInvoices({
    pspId: filterPspId ? Number(filterPspId) : undefined,
    page,
    size: rowsPerPage,
  });

  const rows: Invoice[] = invoicesPage?.content ?? (Array.isArray(invoicesPage) ? (invoicesPage as Invoice[]) : []);
  const total: number = (invoicesPage as { totalElements?: number })?.totalElements ?? rows.length;

  const updateStatus = useUpdateInvoiceStatus();
  const [markPaidTarget, setMarkPaidTarget] = useState<Invoice | null>(null);
  const [paidForm, setPaidForm] = useState<MarkPaidForm>({ status: "PAID", paymentReference: "", paymentMethod: "", paymentAmount: "" });
  const [marking, setMarking] = useState(false);
  const [downloading, setDownloading] = useState<number | null>(null);
  const [toast, setToast] = useState<{ open: boolean; severity: "success" | "error"; message: string }>({ open: false, severity: "success", message: "" });

  const openMarkPaid = (inv: Invoice) => {
    setMarkPaidTarget(inv);
    setPaidForm({ status: "PAID", paymentReference: "", paymentMethod: "", paymentAmount: String(inv.totalAmount) });
  };

  const handleMarkPaid = async () => {
    if (!markPaidTarget) return;
    setMarking(true);
    try {
      await updateStatus.mutateAsync({
        invoiceId: markPaidTarget.invoiceId,
        status: paidForm.status,
        paymentReference: paidForm.paymentReference || undefined,
        paymentMethod: paidForm.paymentMethod || undefined,
        paymentAmount: paidForm.paymentAmount ? Number(paidForm.paymentAmount) : undefined,
      });
      setToast({ open: true, severity: "success", message: "Invoice status updated." });
      setMarkPaidTarget(null);
    } catch {
      setToast({ open: true, severity: "error", message: "Failed to update invoice status." });
    } finally {
      setMarking(false);
    }
  };

  const handleDownloadPdf = async (inv: Invoice) => {
    setDownloading(inv.invoiceId);
    try {
      const url = getApiUrl(`billing/invoices/${inv.invoiceId}/pdf`);
      const pspId = sessionStorage.getItem("_psp") ?? "0";
      const response = await fetch(url, {
        method: "GET",
        credentials: "include",
        headers: { "X-PSP-ID": pspId },
      });
      if (!response.ok) throw new Error("Download failed");
      const blob = await response.blob();
      const objectUrl = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = objectUrl;
      a.download = `invoice-${inv.invoiceNumber}.pdf`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(objectUrl);
    } catch {
      setToast({ open: true, severity: "error", message: "Failed to download PDF." });
    } finally {
      setDownloading(null);
    }
  };

  const filteredRows = filterStatus
    ? rows.filter((r) => r.status === filterStatus)
    : rows;

  return (
    <Box>
      {/* Filters */}
      <Stack direction="row" spacing={2} sx={{ mb: 2, flexWrap: "wrap" }}>
        <FormControl size="small" sx={{ minWidth: 200 }}>
          <InputLabel>PSP</InputLabel>
          <Select value={filterPspId} label="PSP" onChange={(e) => { setFilterPspId(e.target.value); setPage(0); }}>
            <MenuItem value="">All PSPs</MenuItem>
            {(psps as Psp[]).map((p) => {
              const id = String(p.id ?? (p as unknown as { pspId?: number }).pspId ?? "");
              return <MenuItem key={id} value={id}>{p.legalName ?? p.tradingName ?? `PSP ${id}`}</MenuItem>;
            })}
          </Select>
        </FormControl>

        <FormControl size="small" sx={{ minWidth: 160 }}>
          <InputLabel>Status</InputLabel>
          <Select value={filterStatus} label="Status" onChange={(e) => setFilterStatus(e.target.value)}>
            <MenuItem value="">All</MenuItem>
            {["DRAFT", "SENT", "PAID", "OVERDUE", "CANCELLED"].map((s) => (
              <MenuItem key={s} value={s}>{s}</MenuItem>
            ))}
          </Select>
        </FormControl>
      </Stack>

      {isError && <Alert severity="error" sx={{ mb: 2 }}>Failed to load invoices.</Alert>}

      <TableContainer component={Paper} sx={{ border: "1px solid rgba(0,0,0,0.08)", borderRadius: 2 }}>
        <Table size="small">
          <TableHead>
            <TableRow sx={{ backgroundColor: "rgba(0,0,0,0.02)" }}>
              {["Invoice #", "PSP", "Period", "Total", "Status", "Due Date", "Paid At", "Actions"].map((h) => (
                <TableCell key={h} sx={{ color: "text.secondary", fontWeight: 600 }}>{h}</TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={8} align="center" sx={{ py: 6 }}>
                  <CircularProgress size={28} sx={{ color: ACCENT }} />
                </TableCell>
              </TableRow>
            ) : filteredRows.length > 0 ? (
              filteredRows.map((inv) => (
                <TableRow key={inv.invoiceId} hover sx={{ "&:hover": { bgcolor: "rgba(139,64,73,0.04)" } }}>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontFamily: "monospace", fontSize: "0.78rem" }}>
                      {inv.invoiceNumber}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2">{inv.pspName ?? `PSP ${inv.pspId}`}</Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" color="text.secondary" sx={{ fontSize: "0.78rem" }}>
                      {fmtDate(inv.billingPeriodStart)} – {fmtDate(inv.billingPeriodEnd)}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>{fmt(inv.totalAmount, inv.currency)}</Typography>
                  </TableCell>
                  <TableCell><StatusChip status={inv.status} colorMap={INVOICE_STATUS_COLORS} /></TableCell>
                  <TableCell><Typography variant="body2" color="text.secondary">{fmtDate(inv.dueDate)}</Typography></TableCell>
                  <TableCell><Typography variant="body2" color="text.secondary">{fmtDate(inv.paidAt)}</Typography></TableCell>
                  <TableCell>
                    <Stack direction="row" spacing={0.5}>
                      {inv.status !== "PAID" && inv.status !== "CANCELLED" && (
                        <Tooltip title="Mark Paid">
                          <IconButton size="small" onClick={() => openMarkPaid(inv)} sx={{ color: "#27ae60" }}>
                            <CheckIcon sx={{ fontSize: 16 }} />
                          </IconButton>
                        </Tooltip>
                      )}
                      <Tooltip title="Download PDF">
                        <span>
                          <IconButton
                            size="small"
                            onClick={() => handleDownloadPdf(inv)}
                            disabled={downloading === inv.invoiceId}
                            sx={{ color: ACCENT }}
                          >
                            {downloading === inv.invoiceId
                              ? <CircularProgress size={14} />
                              : <PdfIcon sx={{ fontSize: 16 }} />}
                          </IconButton>
                        </span>
                      </Tooltip>
                    </Stack>
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell colSpan={8} align="center" sx={{ py: 8, color: "text.disabled" }}>
                  <Typography variant="body1">No invoices found</Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
        <TablePagination
          rowsPerPageOptions={[10, 25, 50]}
          component="div"
          count={total}
          rowsPerPage={rowsPerPage}
          page={page}
          onPageChange={(_, p) => setPage(p)}
          onRowsPerPageChange={(e) => { setRowsPerPage(parseInt(e.target.value, 10)); setPage(0); }}
        />
      </TableContainer>

      {/* Mark Paid Dialog */}
      <Dialog open={markPaidTarget != null} onClose={() => setMarkPaidTarget(null)} fullWidth maxWidth="sm">
        <DialogTitle sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <Typography variant="h6" sx={{ fontWeight: 600 }}>Update Invoice Status</Typography>
          <IconButton size="small" onClick={() => setMarkPaidTarget(null)}><CloseIcon /></IconButton>
        </DialogTitle>
        <DialogContent dividers>
          <Grid container spacing={2} sx={{ pt: 1 }}>
            <Grid item xs={12}>
              <FormControl fullWidth size="small">
                <InputLabel>New Status</InputLabel>
                <Select value={paidForm.status} label="New Status" onChange={(e) => setPaidForm((f) => ({ ...f, status: e.target.value }))}>
                  {["PAID", "SENT", "CANCELLED"].map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={6}>
              <FormControl fullWidth size="small">
                <InputLabel>Payment Method</InputLabel>
                <Select value={paidForm.paymentMethod} label="Payment Method" onChange={(e) => setPaidForm((f) => ({ ...f, paymentMethod: e.target.value }))}>
                  <MenuItem value="">—</MenuItem>
                  {PAYMENT_METHODS.map((m) => <MenuItem key={m} value={m}>{m.replace(/_/g, " ")}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={6}>
              <TextField
                label="Payment Amount"
                size="small"
                fullWidth
                type="number"
                value={paidForm.paymentAmount}
                onChange={(e) => setPaidForm((f) => ({ ...f, paymentAmount: e.target.value }))}
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                label="Payment Reference"
                size="small"
                fullWidth
                value={paidForm.paymentReference}
                onChange={(e) => setPaidForm((f) => ({ ...f, paymentReference: e.target.value }))}
                placeholder="Transaction ID, cheque number, etc."
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setMarkPaidTarget(null)} sx={{ textTransform: "none" }}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleMarkPaid}
            disabled={marking}
            startIcon={marking ? <CircularProgress size={16} /> : undefined}
            sx={{ textTransform: "none", bgcolor: ACCENT, "&:hover": { bgcolor: "#6b313a" } }}
          >
            {marking ? "Updating…" : "Update Status"}
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar open={toast.open} autoHideDuration={4000} onClose={() => setToast((t) => ({ ...t, open: false }))} anchorOrigin={{ vertical: "bottom", horizontal: "center" }}>
        <Alert severity={toast.severity} onClose={() => setToast((t) => ({ ...t, open: false }))} variant="filled">{toast.message}</Alert>
      </Snackbar>
    </Box>
  );
}

// ─── Tab 4: Usage ────────────────────────────────────────────────────────────

function UsageTab() {
  const { data: psps = [] } = useAllPsps();
  const [selectedPspId, setSelectedPspId] = useState<number | null>(null);
  const [useCustomMonth, setUseCustomMonth] = useState(false);
  const [selectedMonth, setSelectedMonth] = useState<string>(() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
  });

  const { data: usageCurrent, isLoading: loadingCurrent } = useCurrentUsage(!useCustomMonth ? selectedPspId : null);
  const { data: usageCustom, isLoading: loadingCustom } = useUsageSummary(useCustomMonth ? selectedPspId : null, selectedMonth);

  const usage = useCustomMonth ? usageCustom : usageCurrent;
  const isLoading = useCustomMonth ? loadingCustom : loadingCurrent;

  return (
    <Box>
      {/* Controls */}
      <Stack direction="row" spacing={2} sx={{ mb: 3, flexWrap: "wrap", alignItems: "center" }}>
        <FormControl size="small" sx={{ minWidth: 220 }}>
          <InputLabel>PSP</InputLabel>
          <Select
            value={selectedPspId ?? ""}
            label="PSP"
            onChange={(e) => setSelectedPspId(e.target.value ? Number(e.target.value) : null)}
          >
            <MenuItem value="">Select a PSP</MenuItem>
            {(psps as Psp[]).map((p) => {
              const id = p.id ?? (p as unknown as { pspId?: number }).pspId ?? 0;
              return <MenuItem key={id} value={id}>{p.legalName ?? p.tradingName ?? `PSP ${id}`}</MenuItem>;
            })}
          </Select>
        </FormControl>

        <FormControl size="small" sx={{ minWidth: 160 }}>
          <InputLabel>Period</InputLabel>
          <Select
            value={useCustomMonth ? "custom" : "current"}
            label="Period"
            onChange={(e) => setUseCustomMonth(e.target.value === "custom")}
          >
            <MenuItem value="current">Current Month</MenuItem>
            <MenuItem value="custom">Custom Month</MenuItem>
          </Select>
        </FormControl>

        {useCustomMonth && (
          <TextField
            label="Month"
            size="small"
            type="month"
            InputLabelProps={{ shrink: true }}
            value={selectedMonth}
            onChange={(e) => setSelectedMonth(e.target.value)}
            sx={{ minWidth: 160 }}
          />
        )}
      </Stack>

      {!selectedPspId && (
        <Alert severity="info">Select a PSP to view usage data.</Alert>
      )}

      {selectedPspId && isLoading && (
        <Box sx={{ display: "flex", justifyContent: "center", py: 6 }}>
          <CircularProgress size={28} sx={{ color: ACCENT }} />
        </Box>
      )}

      {selectedPspId && !isLoading && !usage && (
        <Alert severity="warning">No usage data available for the selected period.</Alert>
      )}

      {selectedPspId && !isLoading && usage && (
        <>
          {/* Summary Cards */}
          <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap", mb: 3 }}>
            <KpiCard
              title="Total Requests"
              value={usage.totalRequests.toLocaleString()}
              icon={<BarChartIcon />}
              color="#2980b9"
              subtitle={`Period: ${usage.period ?? selectedMonth}`}
            />
            <KpiCard
              title="Billable Requests"
              value={usage.billableRequests.toLocaleString()}
              icon={<ReceiptIcon />}
              color={ACCENT}
            />
            <KpiCard
              title="Estimated Cost"
              value={fmt(usage.totalCostUsd, "USD")}
              icon={<MoneyIcon />}
              color="#27ae60"
            />
          </Box>

          {/* Breakdown Table */}
          {usage.breakdown && usage.breakdown.length > 0 && (
            <TableContainer component={Paper} sx={{ border: "1px solid rgba(0,0,0,0.08)", borderRadius: 2 }}>
              <Table size="small">
                <TableHead>
                  <TableRow sx={{ backgroundColor: "rgba(0,0,0,0.02)" }}>
                    <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Service Type</TableCell>
                    <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Request Count</TableCell>
                    <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Cost (USD)</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {usage.breakdown.map((item, idx) => (
                    <TableRow key={idx} hover>
                      <TableCell>
                        <Typography variant="body2" sx={{ fontWeight: 500 }}>
                          {item.serviceType.replace(/_/g, " ")}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">{item.count.toLocaleString()}</Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>{fmt(item.costUsd, "USD")}</Typography>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </>
      )}
    </Box>
  );
}

// ─── BillingPage (root) ───────────────────────────────────────────────────────

export default function BillingPage() {
  const [tab, setTab] = useState(0);

  return (
    <Box>
      <Typography variant="h6" sx={{ fontWeight: 600, mb: 0.5 }}>
        Billing
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Revenue, subscriptions, invoices, and usage management.
      </Typography>

      <Paper sx={{ border: "1px solid rgba(0,0,0,0.08)", borderRadius: 2, mb: 0 }}>
        <Tabs
          value={tab}
          onChange={(_, v) => setTab(v)}
          sx={{
            borderBottom: "1px solid rgba(0,0,0,0.08)",
            px: 2,
            "& .MuiTab-root": { textTransform: "none", fontWeight: 500, minHeight: 48 },
            "& .Mui-selected": { color: ACCENT, fontWeight: 600 },
            "& .MuiTabs-indicator": { backgroundColor: ACCENT },
          }}
        >
          <Tab label="Revenue Dashboard" icon={<MoneyIcon sx={{ fontSize: 18 }} />} iconPosition="start" />
          <Tab label="Subscriptions" icon={<CreditCardIcon sx={{ fontSize: 18 }} />} iconPosition="start" />
          <Tab label="Invoices" icon={<ReceiptIcon sx={{ fontSize: 18 }} />} iconPosition="start" />
          <Tab label="Usage" icon={<BarChartIcon sx={{ fontSize: 18 }} />} iconPosition="start" />
        </Tabs>

        <Box sx={{ p: 3 }}>
          {tab === 0 && <RevenueTab />}
          {tab === 1 && <SubscriptionsTab />}
          {tab === 2 && <InvoicesTab />}
          {tab === 3 && <UsageTab />}
        </Box>
      </Paper>
    </Box>
  );
}
