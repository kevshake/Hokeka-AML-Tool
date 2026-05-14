import { useQuery } from "@tanstack/react-query";
import {
  Box,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Alert,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Button,
  Grid,
  Divider,
  Snackbar,
} from "@mui/material";
import {
  DownloadOutlined as DownloadIcon,
  CreditCard as CreditCardIcon,
  BarChart as BarChartIcon,
  Receipt as ReceiptIcon,
} from "@mui/icons-material";
import { useState } from "react";
import { apiClient } from "../../../lib/apiClient";
import { getApiUrl } from "../../../config/api";

const ACCENT = "#8B4049";

// ─── Types ─────────────────────────────────────────────────────────────────

interface PricingTier {
  id: number;
  name: string;
  monthlyFee: number;
  currency: string;
  includedChecks: number;
}

interface Subscription {
  id: number;
  status: string;
  billingCycle: string;
  startDate: string;
  endDate: string | null;
  trialEndDate: string | null;
  tier: PricingTier;
}

interface UsageLineItem {
  serviceType: string;
  requestCount: number;
  cost: number;
}

interface CurrentUsage {
  pspId: string;
  totalRequests: number;
  billableRequests: number;
  estimatedCost: number;
  currency: string;
  periodStart: string;
  periodEnd: string;
  breakdown: UsageLineItem[];
}

interface Invoice {
  id: number;
  invoiceNumber: string;
  periodStart: string;
  periodEnd: string;
  totalAmount: number;
  currency: string;
  status: "PAID" | "OVERDUE" | "SENT" | "DRAFT";
  dueDate: string;
}

// ─── Local hooks (billing) ─────────────────────────────────────────────────

const usePspSubscription = (pspId: string) =>
  useQuery<Subscription | null>({
    queryKey: ["psp", pspId, "subscription"],
    queryFn: () =>
      apiClient.get<Subscription>(`subscriptions/psp/${pspId}`).catch(() => null),
    enabled: !!pspId,
  });

const useCurrentUsage = (pspId: string) =>
  useQuery<CurrentUsage | null>({
    queryKey: ["psp", pspId, "usage", "current"],
    queryFn: () =>
      apiClient
        .get<CurrentUsage>(`billing/usage/${pspId}/current`)
        .catch(() => null),
    enabled: !!pspId,
    refetchInterval: 60_000,
  });

const useInvoices = (pspId: string) =>
  useQuery<Invoice[]>({
    queryKey: ["psp", pspId, "invoices"],
    queryFn: () =>
      apiClient
        .get<Invoice[]>(`billing/invoices?pspId=${pspId}&size=12`)
        .catch(() => []),
    enabled: !!pspId,
  });

// ─── Helpers ───────────────────────────────────────────────────────────────

function fmtDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("en-KE", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

function fmtMoney(amount: number, currency: string): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: currency || "USD",
    minimumFractionDigits: 2,
  }).format(amount);
}

function fmtNumber(n: number): string {
  return new Intl.NumberFormat("en-US").format(n);
}

type InvoiceStatus = "PAID" | "OVERDUE" | "SENT" | "DRAFT";

function invoiceStatusColor(
  status: InvoiceStatus
): "success" | "error" | "primary" | "default" {
  switch (status) {
    case "PAID":
      return "success";
    case "OVERDUE":
      return "error";
    case "SENT":
      return "primary";
    default:
      return "default";
  }
}

// ─── Sub-components ────────────────────────────────────────────────────────

interface KpiCardProps {
  label: string;
  value: string;
  sub?: string;
}

function KpiCard({ label, value, sub }: KpiCardProps) {
  return (
    <Card variant="outlined" sx={{ borderRadius: 2, height: "100%" }}>
      <CardContent sx={{ pb: "16px !important" }}>
        <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, textTransform: "uppercase", letterSpacing: 0.5 }}>
          {label}
        </Typography>
        <Typography variant="h5" sx={{ fontWeight: 700, mt: 0.5, color: ACCENT }}>
          {value}
        </Typography>
        {sub && (
          <Typography variant="caption" color="text.secondary">
            {sub}
          </Typography>
        )}
      </CardContent>
    </Card>
  );
}

// ─── BillingTab ────────────────────────────────────────────────────────────

interface BillingTabProps {
  pspId: string;
}

export default function BillingTab({ pspId }: BillingTabProps) {
  const { data: subscription, isLoading: subLoading, isError: subError } = usePspSubscription(pspId);
  const { data: usage, isLoading: usageLoading, isError: usageError } = useCurrentUsage(pspId);
  const { data: invoices, isLoading: invoicesLoading, isError: invoicesError } = useInvoices(pspId);

  const [toast, setToast] = useState<{
    open: boolean;
    severity: "success" | "error";
    message: string;
  }>({ open: false, severity: "success", message: "" });

  const handleDownloadPdf = async (invoiceId: number, invoiceNumber: string) => {
    try {
      const url = getApiUrl(`billing/invoices/${invoiceId}/pdf`);
      const response = await fetch(url, {
        method: "GET",
        credentials: "include",
        headers: {
          // apiClient uses cookie-based auth + X-PSP-ID header
          "X-PSP-ID": sessionStorage.getItem("_psp") ?? "0",
        },
      });

      if (!response.ok) {
        throw new Error(`Download failed: ${response.status}`);
      }

      const blob = await response.blob();
      const objectUrl = window.URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = objectUrl;
      anchor.download = `invoice-${invoiceNumber}.pdf`;
      document.body.appendChild(anchor);
      anchor.click();
      window.URL.revokeObjectURL(objectUrl);
      document.body.removeChild(anchor);

      setToast({ open: true, severity: "success", message: `Invoice ${invoiceNumber} downloaded.` });
    } catch {
      setToast({ open: true, severity: "error", message: "Could not download invoice. Please try again." });
    }
  };

  return (
    <Box>
      {/* ── Section 1: Current Plan ─────────────────────────────────────── */}
      <Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 2 }}>
        <CreditCardIcon sx={{ color: ACCENT, fontSize: 20 }} />
        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
          Current Plan
        </Typography>
      </Box>

      {subLoading && (
        <Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
          <CircularProgress sx={{ color: ACCENT }} size={28} />
        </Box>
      )}

      {subError && !subLoading && (
        <Alert severity="error" sx={{ mb: 3 }}>
          Failed to load subscription. Please refresh the page.
        </Alert>
      )}

      {!subLoading && !subError && !subscription && (
        <Alert severity="info" sx={{ mb: 3 }}>
          No active subscription. Contact your account manager.
        </Alert>
      )}

      {!subLoading && !subError && subscription && (
        <Card
          variant="outlined"
          sx={{ borderRadius: 2, mb: 4, borderColor: "rgba(139,64,73,0.25)" }}
        >
          <CardContent>
            <Box sx={{ display: "flex", alignItems: "center", gap: 2, mb: 2 }}>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>
                {subscription.tier.name}
              </Typography>
              <Chip
                label={subscription.tier.name.toUpperCase()}
                size="small"
                sx={{
                  backgroundColor: ACCENT,
                  color: "white",
                  fontWeight: 700,
                  letterSpacing: 0.5,
                  fontSize: "0.7rem",
                }}
              />
              <Chip
                label={subscription.status}
                size="small"
                color={
                  subscription.status === "ACTIVE"
                    ? "success"
                    : subscription.status === "TRIAL"
                    ? "warning"
                    : "default"
                }
                sx={{ fontWeight: 600, fontSize: "0.7rem" }}
              />
            </Box>

            {/* Trial warning */}
            {subscription.trialEndDate && (
              <Alert
                severity="warning"
                sx={{
                  mb: 2,
                  backgroundColor: "rgba(255, 167, 38, 0.1)",
                  border: "1px solid rgba(255, 167, 38, 0.4)",
                }}
              >
                Trial ends on{" "}
                <strong>{fmtDate(subscription.trialEndDate)}</strong>. After
                this date your plan will transition to the standard billing
                cycle.
              </Alert>
            )}

            <Grid container spacing={2}>
              <Grid item xs={12} sm={6} md={3}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, textTransform: "uppercase", letterSpacing: 0.5 }}>
                  Monthly Fee
                </Typography>
                <Typography variant="body1" sx={{ fontWeight: 600 }}>
                  {fmtMoney(subscription.tier.monthlyFee, subscription.tier.currency)}
                </Typography>
              </Grid>
              <Grid item xs={12} sm={6} md={3}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, textTransform: "uppercase", letterSpacing: 0.5 }}>
                  Billing Cycle
                </Typography>
                <Typography variant="body1" sx={{ fontWeight: 600 }}>
                  {subscription.billingCycle}
                </Typography>
              </Grid>
              <Grid item xs={12} sm={6} md={3}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, textTransform: "uppercase", letterSpacing: 0.5 }}>
                  Included Checks
                </Typography>
                <Typography variant="body1" sx={{ fontWeight: 600 }}>
                  {fmtNumber(subscription.tier.includedChecks)} / month
                </Typography>
              </Grid>
              <Grid item xs={12} sm={6} md={3}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, textTransform: "uppercase", letterSpacing: 0.5 }}>
                  Currency
                </Typography>
                <Typography variant="body1" sx={{ fontWeight: 600 }}>
                  {subscription.tier.currency}
                </Typography>
              </Grid>
            </Grid>

            <Divider sx={{ my: 2 }} />

            <Grid container spacing={2}>
              <Grid item xs={12} sm={6}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, textTransform: "uppercase", letterSpacing: 0.5 }}>
                  Contract Start
                </Typography>
                <Typography variant="body2">{fmtDate(subscription.startDate)}</Typography>
              </Grid>
              {subscription.endDate && (
                <Grid item xs={12} sm={6}>
                  <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, textTransform: "uppercase", letterSpacing: 0.5 }}>
                    Contract End
                  </Typography>
                  <Typography variant="body2">{fmtDate(subscription.endDate)}</Typography>
                </Grid>
              )}
            </Grid>

            <Box sx={{ mt: 2 }}>
              <Typography variant="caption" color="text.secondary">
                To upgrade your plan or change your billing cycle, please contact your account manager.
              </Typography>
            </Box>
          </CardContent>
        </Card>
      )}

      {/* ── Section 2: Current Month Usage ──────────────────────────────── */}
      <Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 2 }}>
        <BarChartIcon sx={{ color: ACCENT, fontSize: 20 }} />
        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
          Current Month Usage
        </Typography>
        <Typography variant="caption" color="text.secondary" sx={{ ml: "auto" }}>
          Auto-refreshes every 60 s
        </Typography>
      </Box>

      {usageLoading && (
        <Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
          <CircularProgress sx={{ color: ACCENT }} size={28} />
        </Box>
      )}

      {usageError && !usageLoading && (
        <Alert severity="error" sx={{ mb: 3 }}>
          Failed to load usage data. Please refresh the page.
        </Alert>
      )}

      {!usageLoading && !usageError && !usage && (
        <Alert severity="info" sx={{ mb: 3 }}>
          No usage data available for this billing period.
        </Alert>
      )}

      {!usageLoading && !usageError && usage && (
        <Box sx={{ mb: 4 }}>
          <Grid container spacing={2} sx={{ mb: 3 }}>
            <Grid item xs={12} sm={4}>
              <KpiCard
                label="Total API Requests"
                value={fmtNumber(usage.totalRequests)}
                sub={`Period: ${fmtDate(usage.periodStart)} – ${fmtDate(usage.periodEnd)}`}
              />
            </Grid>
            <Grid item xs={12} sm={4}>
              <KpiCard
                label="Billable Requests"
                value={fmtNumber(usage.billableRequests)}
                sub="After included-check allowance"
              />
            </Grid>
            <Grid item xs={12} sm={4}>
              <KpiCard
                label="Estimated Cost"
                value={fmtMoney(usage.estimatedCost, usage.currency)}
                sub="Current month estimate"
              />
            </Grid>
          </Grid>

          {usage.breakdown && usage.breakdown.length > 0 && (
            <TableContainer
              component={Paper}
              variant="outlined"
              sx={{ borderRadius: 2 }}
            >
              <Table size="small">
                <TableHead>
                  <TableRow sx={{ backgroundColor: "rgba(139,64,73,0.05)" }}>
                    <TableCell sx={{ fontWeight: 700, fontSize: "0.8rem" }}>
                      Service Type
                    </TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700, fontSize: "0.8rem" }}>
                      Request Count
                    </TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700, fontSize: "0.8rem" }}>
                      Cost (USD)
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {usage.breakdown.map((line) => (
                    <TableRow key={line.serviceType} hover>
                      <TableCell sx={{ fontSize: "0.85rem" }}>
                        {line.serviceType}
                      </TableCell>
                      <TableCell align="right" sx={{ fontSize: "0.85rem" }}>
                        {fmtNumber(line.requestCount)}
                      </TableCell>
                      <TableCell align="right" sx={{ fontSize: "0.85rem" }}>
                        {fmtMoney(line.cost, "USD")}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Box>
      )}

      {/* ── Section 3: Invoice History ───────────────────────────────────── */}
      <Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 2 }}>
        <ReceiptIcon sx={{ color: ACCENT, fontSize: 20 }} />
        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
          Invoice History
        </Typography>
      </Box>

      {invoicesLoading && (
        <Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
          <CircularProgress sx={{ color: ACCENT }} size={28} />
        </Box>
      )}

      {invoicesError && !invoicesLoading && (
        <Alert severity="error" sx={{ mb: 3 }}>
          Failed to load invoices. Please refresh the page.
        </Alert>
      )}

      {!invoicesLoading && !invoicesError && (!invoices || invoices.length === 0) && (
        <Alert severity="info" sx={{ mb: 3 }}>
          No invoices found for this PSP.
        </Alert>
      )}

      {!invoicesLoading && !invoicesError && invoices && invoices.length > 0 && (
        <TableContainer
          component={Paper}
          variant="outlined"
          sx={{ borderRadius: 2, mb: 3 }}
        >
          <Table size="small">
            <TableHead>
              <TableRow sx={{ backgroundColor: "rgba(139,64,73,0.05)" }}>
                <TableCell sx={{ fontWeight: 700, fontSize: "0.8rem" }}>Invoice #</TableCell>
                <TableCell sx={{ fontWeight: 700, fontSize: "0.8rem" }}>Period</TableCell>
                <TableCell align="right" sx={{ fontWeight: 700, fontSize: "0.8rem" }}>Total Amount</TableCell>
                <TableCell sx={{ fontWeight: 700, fontSize: "0.8rem" }}>Currency</TableCell>
                <TableCell sx={{ fontWeight: 700, fontSize: "0.8rem" }}>Status</TableCell>
                <TableCell sx={{ fontWeight: 700, fontSize: "0.8rem" }}>Due Date</TableCell>
                <TableCell sx={{ fontWeight: 700, fontSize: "0.8rem" }}>Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {invoices.map((inv) => (
                <TableRow key={inv.id} hover>
                  <TableCell sx={{ fontSize: "0.85rem", fontFamily: "monospace" }}>
                    {inv.invoiceNumber}
                  </TableCell>
                  <TableCell sx={{ fontSize: "0.85rem" }}>
                    {fmtDate(inv.periodStart)} – {fmtDate(inv.periodEnd)}
                  </TableCell>
                  <TableCell align="right" sx={{ fontSize: "0.85rem", fontWeight: 600 }}>
                    {fmtMoney(inv.totalAmount, inv.currency)}
                  </TableCell>
                  <TableCell sx={{ fontSize: "0.85rem" }}>{inv.currency}</TableCell>
                  <TableCell>
                    <Chip
                      label={inv.status}
                      size="small"
                      color={invoiceStatusColor(inv.status)}
                      sx={{ fontWeight: 600, fontSize: "0.7rem" }}
                    />
                  </TableCell>
                  <TableCell sx={{ fontSize: "0.85rem" }}>
                    {fmtDate(inv.dueDate)}
                  </TableCell>
                  <TableCell>
                    <Button
                      size="small"
                      startIcon={<DownloadIcon fontSize="small" />}
                      onClick={() => handleDownloadPdf(inv.id, inv.invoiceNumber)}
                      sx={{
                        color: ACCENT,
                        textTransform: "none",
                        fontSize: "0.78rem",
                        "&:hover": { backgroundColor: "rgba(139,64,73,0.06)" },
                      }}
                    >
                      PDF
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* Toast */}
      <Snackbar
        open={toast.open}
        autoHideDuration={4000}
        onClose={() => setToast((t) => ({ ...t, open: false }))}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      >
        <Alert
          severity={toast.severity}
          onClose={() => setToast((t) => ({ ...t, open: false }))}
          variant="filled"
        >
          {toast.message}
        </Alert>
      </Snackbar>
    </Box>
  );
}
