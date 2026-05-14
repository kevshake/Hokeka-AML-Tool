import { useQuery, useQueryClient } from "@tanstack/react-query";
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
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
} from "@mui/material";
import {
  DownloadOutlined as DownloadIcon,
  CreditCard as CreditCardIcon,
  BarChart as BarChartIcon,
  Receipt as ReceiptIcon,
  Payment as PaymentIcon,
  AccountBalance as BankIcon,
  PhoneAndroid as PhoneIcon,
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

type InvoiceStatus =
  | "PAID"
  | "OVERDUE"
  | "SENT"
  | "DRAFT"
  | "CANCELLED"
  | "PENDING_PAYMENT_VERIFICATION";

interface Invoice {
  invoiceId: number;
  invoiceNumber: string;
  billingPeriodStart: string;
  billingPeriodEnd: string;
  totalAmount: number;
  currency: string;
  status: InvoiceStatus;
  dueDate: string;
}

interface BankDetails {
  bankName: string;
  accountName: string;
  accountNumber: string;
  branch: string;
  swiftCode: string;
}

interface PaymentInitiateResponse {
  attemptId: number | null;
  checkoutRequestId: string | null;
  status: string;
  message: string;
}

type PaymentMethod = "MPESA" | "BANK_TRANSFER";

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

const useBankDetails = () =>
  useQuery<BankDetails | null>({
    queryKey: ["billing", "bank-details"],
    queryFn: () =>
      apiClient.get<BankDetails>("billing/bank-details").catch(() => null),
    staleTime: 10 * 60_000, // cache for 10 minutes — static data
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

function invoiceStatusColor(
  status: InvoiceStatus
): "success" | "error" | "primary" | "warning" | "default" {
  switch (status) {
    case "PAID":
      return "success";
    case "OVERDUE":
      return "error";
    case "SENT":
      return "primary";
    case "PENDING_PAYMENT_VERIFICATION":
      return "warning";
    default:
      return "default";
  }
}

function isPayable(status: InvoiceStatus): boolean {
  return status === "SENT" || status === "OVERDUE";
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
        <Typography
          variant="caption"
          color="text.secondary"
          sx={{ fontWeight: 600, textTransform: "uppercase", letterSpacing: 0.5 }}
        >
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

// ─── Payment Dialog ─────────────────────────────────────────────────────────

interface PaymentDialogProps {
  invoice: Invoice | null;
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
  bankDetails: BankDetails | null;
}

function PaymentDialog({
  invoice,
  open,
  onClose,
  onSuccess,
  bankDetails,
}: PaymentDialogProps) {
  const [payMethod, setPayMethod] = useState<PaymentMethod>("MPESA");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [bankRef, setBankRef] = useState("");
  const [paying, setPaying] = useState(false);
  const [result, setResult] = useState<{
    severity: "success" | "error" | "info";
    message: string;
  } | null>(null);

  const handleClose = () => {
    if (paying) return;
    setResult(null);
    setPhoneNumber("");
    setBankRef("");
    setPayMethod("MPESA");
    onClose();
  };

  const handleSubmit = async () => {
    if (!invoice) return;
    setPaying(true);
    setResult(null);

    try {
      const body: {
        invoiceId: number;
        paymentMethod: PaymentMethod;
        phoneNumber?: string;
        bankReference?: string;
      } = {
        invoiceId: invoice.invoiceId,
        paymentMethod: payMethod,
      };

      if (payMethod === "MPESA") {
        body.phoneNumber = phoneNumber;
      } else {
        body.bankReference = bankRef;
      }

      const response = await fetch(getApiUrl("billing/payments/initiate"), {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
          "X-PSP-ID": sessionStorage.getItem("_psp") ?? "0",
        },
        body: JSON.stringify(body),
      });

      const data: PaymentInitiateResponse = await response.json();

      if (!response.ok || data.status === "FAILED" || data.status === "REJECTED") {
        setResult({ severity: "error", message: data.message || "Payment initiation failed." });
      } else {
        setResult({ severity: "success", message: data.message });
        onSuccess();
      }
    } catch {
      setResult({ severity: "error", message: "Network error. Please try again." });
    } finally {
      setPaying(false);
    }
  };

  if (!invoice) return null;

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ fontWeight: 700 }}>
        Pay Invoice {invoice.invoiceNumber}
      </DialogTitle>

      <DialogContent dividers>
        {/* Invoice summary */}
        <Box
          sx={{
            p: 1.5,
            mb: 2,
            borderRadius: 1,
            backgroundColor: "rgba(139,64,73,0.05)",
            border: "1px solid rgba(139,64,73,0.15)",
          }}
        >
          <Grid container spacing={1}>
            <Grid item xs={6}>
              <Typography variant="caption" color="text.secondary">
                Invoice
              </Typography>
              <Typography variant="body2" sx={{ fontWeight: 600, fontFamily: "monospace" }}>
                {invoice.invoiceNumber}
              </Typography>
            </Grid>
            <Grid item xs={6}>
              <Typography variant="caption" color="text.secondary">
                Amount Due
              </Typography>
              <Typography variant="body2" sx={{ fontWeight: 700, color: ACCENT }}>
                {fmtMoney(invoice.totalAmount, invoice.currency)}
              </Typography>
            </Grid>
            <Grid item xs={6}>
              <Typography variant="caption" color="text.secondary">
                Status
              </Typography>
              <Chip
                label={invoice.status}
                size="small"
                color={invoiceStatusColor(invoice.status)}
                sx={{ fontWeight: 600, fontSize: "0.7rem" }}
              />
            </Grid>
            <Grid item xs={6}>
              <Typography variant="caption" color="text.secondary">
                Due Date
              </Typography>
              <Typography variant="body2">{fmtDate(invoice.dueDate)}</Typography>
            </Grid>
          </Grid>
        </Box>

        {/* Payment method toggle */}
        <Typography variant="subtitle2" sx={{ mb: 1, fontWeight: 600 }}>
          Payment Method
        </Typography>
        <ToggleButtonGroup
          value={payMethod}
          exclusive
          onChange={(_e, val: PaymentMethod | null) => {
            if (val) {
              setPayMethod(val);
              setResult(null);
            }
          }}
          size="small"
          sx={{ mb: 2 }}
          disabled={paying}
        >
          <ToggleButton value="MPESA" sx={{ textTransform: "none", gap: 0.5 }}>
            <PhoneIcon fontSize="small" />
            M-Pesa
          </ToggleButton>
          <ToggleButton value="BANK_TRANSFER" sx={{ textTransform: "none", gap: 0.5 }}>
            <BankIcon fontSize="small" />
            Bank Transfer
          </ToggleButton>
        </ToggleButtonGroup>

        {/* M-Pesa section */}
        {payMethod === "MPESA" && (
          <Box>
            <TextField
              label="M-Pesa Phone Number"
              placeholder="07XXXXXXXX or 254XXXXXXXXX"
              value={phoneNumber}
              onChange={(e) => setPhoneNumber(e.target.value)}
              fullWidth
              size="small"
              disabled={paying}
              helperText="Enter the Kenya phone number registered with M-Pesa (e.g. 0712345678)"
              InputProps={{ startAdornment: <PhoneIcon fontSize="small" sx={{ mr: 0.5, color: "text.secondary" }} /> }}
            />
          </Box>
        )}

        {/* Bank Transfer section */}
        {payMethod === "BANK_TRANSFER" && (
          <Box>
            {bankDetails ? (
              <Box
                sx={{
                  p: 1.5,
                  mb: 2,
                  borderRadius: 1,
                  backgroundColor: "rgba(0,0,0,0.03)",
                  border: "1px solid rgba(0,0,0,0.08)",
                }}
              >
                <Typography variant="caption" sx={{ fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.5 }}>
                  Transfer To
                </Typography>
                <Grid container spacing={0.5} sx={{ mt: 0.5 }}>
                  {[
                    ["Bank", bankDetails.bankName],
                    ["Account Name", bankDetails.accountName],
                    ["Account Number", bankDetails.accountNumber || "Contact billing@hokeka.com"],
                    ["Branch", bankDetails.branch],
                    ["SWIFT / BIC", bankDetails.swiftCode],
                  ].map(([label, value]) => (
                    <Grid item xs={12} key={label}>
                      <Box sx={{ display: "flex", gap: 1 }}>
                        <Typography variant="caption" color="text.secondary" sx={{ minWidth: 110 }}>
                          {label}:
                        </Typography>
                        <Typography variant="caption" sx={{ fontWeight: 600, fontFamily: label === "Account Number" ? "monospace" : "inherit" }}>
                          {value}
                        </Typography>
                      </Box>
                    </Grid>
                  ))}
                </Grid>
                <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 1 }}>
                  Use invoice number <strong>{invoice.invoiceNumber}</strong> as the payment reference.
                </Typography>
              </Box>
            ) : (
              <Alert severity="info" sx={{ mb: 2 }}>
                Contact billing@hokeka.com for bank transfer details.
              </Alert>
            )}

            <TextField
              label="Your Bank Transfer Reference"
              placeholder="e.g. TXN12345 or bank receipt number"
              value={bankRef}
              onChange={(e) => setBankRef(e.target.value)}
              fullWidth
              size="small"
              disabled={paying}
              helperText="Enter the reference or receipt number from your bank after completing the transfer."
            />
          </Box>
        )}

        {/* Result message */}
        {result && (
          <Alert severity={result.severity} sx={{ mt: 2 }}>
            {result.message}
          </Alert>
        )}
      </DialogContent>

      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={handleClose} disabled={paying} sx={{ textTransform: "none" }}>
          {result?.severity === "success" ? "Close" : "Cancel"}
        </Button>
        {!result || result.severity !== "success" ? (
          <Button
            variant="contained"
            onClick={handleSubmit}
            disabled={
              paying ||
              (payMethod === "MPESA" && !phoneNumber.trim()) ||
              (payMethod === "BANK_TRANSFER" && !bankRef.trim())
            }
            startIcon={
              paying ? (
                <CircularProgress size={16} color="inherit" />
              ) : (
                <PaymentIcon fontSize="small" />
              )
            }
            sx={{
              textTransform: "none",
              backgroundColor: ACCENT,
              "&:hover": { backgroundColor: "#6e3139" },
            }}
          >
            {paying
              ? "Processing..."
              : payMethod === "MPESA"
              ? "Send STK Push"
              : "Submit Reference"}
          </Button>
        ) : null}
      </DialogActions>
    </Dialog>
  );
}

// ─── BillingTab ────────────────────────────────────────────────────────────

interface BillingTabProps {
  pspId: string;
}

export default function BillingTab({ pspId }: BillingTabProps) {
  const queryClient = useQueryClient();
  const { data: subscription, isLoading: subLoading, isError: subError } =
    usePspSubscription(pspId);
  const { data: usage, isLoading: usageLoading, isError: usageError } =
    useCurrentUsage(pspId);
  const {
    data: invoices,
    isLoading: invoicesLoading,
    isError: invoicesError,
  } = useInvoices(pspId);
  const { data: bankDetails } = useBankDetails();

  const [toast, setToast] = useState<{
    open: boolean;
    severity: "success" | "error";
    message: string;
  }>({ open: false, severity: "success", message: "" });

  const [payDialog, setPayDialog] = useState<{
    open: boolean;
    invoice: Invoice | null;
  }>({ open: false, invoice: null });

  const handleDownloadPdf = async (
    invoiceId: number,
    invoiceNumber: string
  ) => {
    try {
      const url = getApiUrl(`billing/invoices/${invoiceId}/pdf`);
      const response = await fetch(url, {
        method: "GET",
        credentials: "include",
        headers: {
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

      setToast({
        open: true,
        severity: "success",
        message: `Invoice ${invoiceNumber} downloaded.`,
      });
    } catch {
      setToast({
        open: true,
        severity: "error",
        message: "Could not download invoice. Please try again.",
      });
    }
  };

  const handlePaySuccess = () => {
    // Refresh the invoice list so the updated status is reflected
    queryClient.invalidateQueries({ queryKey: ["psp", pspId, "invoices"] });
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
                <Typography
                  variant="caption"
                  color="text.secondary"
                  sx={{
                    fontWeight: 600,
                    textTransform: "uppercase",
                    letterSpacing: 0.5,
                  }}
                >
                  Monthly Fee
                </Typography>
                <Typography variant="body1" sx={{ fontWeight: 600 }}>
                  {fmtMoney(
                    subscription.tier.monthlyFee,
                    subscription.tier.currency
                  )}
                </Typography>
              </Grid>
              <Grid item xs={12} sm={6} md={3}>
                <Typography
                  variant="caption"
                  color="text.secondary"
                  sx={{
                    fontWeight: 600,
                    textTransform: "uppercase",
                    letterSpacing: 0.5,
                  }}
                >
                  Billing Cycle
                </Typography>
                <Typography variant="body1" sx={{ fontWeight: 600 }}>
                  {subscription.billingCycle}
                </Typography>
              </Grid>
              <Grid item xs={12} sm={6} md={3}>
                <Typography
                  variant="caption"
                  color="text.secondary"
                  sx={{
                    fontWeight: 600,
                    textTransform: "uppercase",
                    letterSpacing: 0.5,
                  }}
                >
                  Included Checks
                </Typography>
                <Typography variant="body1" sx={{ fontWeight: 600 }}>
                  {fmtNumber(subscription.tier.includedChecks)} / month
                </Typography>
              </Grid>
              <Grid item xs={12} sm={6} md={3}>
                <Typography
                  variant="caption"
                  color="text.secondary"
                  sx={{
                    fontWeight: 600,
                    textTransform: "uppercase",
                    letterSpacing: 0.5,
                  }}
                >
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
                <Typography
                  variant="caption"
                  color="text.secondary"
                  sx={{
                    fontWeight: 600,
                    textTransform: "uppercase",
                    letterSpacing: 0.5,
                  }}
                >
                  Contract Start
                </Typography>
                <Typography variant="body2">
                  {fmtDate(subscription.startDate)}
                </Typography>
              </Grid>
              {subscription.endDate && (
                <Grid item xs={12} sm={6}>
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    sx={{
                      fontWeight: 600,
                      textTransform: "uppercase",
                      letterSpacing: 0.5,
                    }}
                  >
                    Contract End
                  </Typography>
                  <Typography variant="body2">
                    {fmtDate(subscription.endDate)}
                  </Typography>
                </Grid>
              )}
            </Grid>

            <Box sx={{ mt: 2 }}>
              <Typography variant="caption" color="text.secondary">
                To upgrade your plan or change your billing cycle, please
                contact your account manager.
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
        <Typography
          variant="caption"
          color="text.secondary"
          sx={{ ml: "auto" }}
        >
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
                sub={`Period: ${fmtDate(usage.periodStart)} – ${fmtDate(
                  usage.periodEnd
                )}`}
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
                    <TableCell
                      align="right"
                      sx={{ fontWeight: 700, fontSize: "0.8rem" }}
                    >
                      Request Count
                    </TableCell>
                    <TableCell
                      align="right"
                      sx={{ fontWeight: 700, fontSize: "0.8rem" }}
                    >
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

      {!invoicesLoading &&
        !invoicesError &&
        (!invoices || invoices.length === 0) && (
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
                <TableCell sx={{ fontWeight: 700, fontSize: "0.8rem" }}>
                  Invoice #
                </TableCell>
                <TableCell sx={{ fontWeight: 700, fontSize: "0.8rem" }}>
                  Period
                </TableCell>
                <TableCell
                  align="right"
                  sx={{ fontWeight: 700, fontSize: "0.8rem" }}
                >
                  Total Amount
                </TableCell>
                <TableCell sx={{ fontWeight: 700, fontSize: "0.8rem" }}>
                  Currency
                </TableCell>
                <TableCell sx={{ fontWeight: 700, fontSize: "0.8rem" }}>
                  Status
                </TableCell>
                <TableCell sx={{ fontWeight: 700, fontSize: "0.8rem" }}>
                  Due Date
                </TableCell>
                <TableCell sx={{ fontWeight: 700, fontSize: "0.8rem" }}>
                  Actions
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {invoices.map((inv) => (
                <TableRow key={inv.invoiceId} hover>
                  <TableCell
                    sx={{
                      fontSize: "0.85rem",
                      fontFamily: "monospace",
                    }}
                  >
                    {inv.invoiceNumber}
                  </TableCell>
                  <TableCell sx={{ fontSize: "0.85rem" }}>
                    {fmtDate(inv.billingPeriodStart)} –{" "}
                    {fmtDate(inv.billingPeriodEnd)}
                  </TableCell>
                  <TableCell
                    align="right"
                    sx={{ fontSize: "0.85rem", fontWeight: 600 }}
                  >
                    {fmtMoney(inv.totalAmount, inv.currency)}
                  </TableCell>
                  <TableCell sx={{ fontSize: "0.85rem" }}>
                    {inv.currency}
                  </TableCell>
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
                    <Box sx={{ display: "flex", gap: 0.5 }}>
                      <Button
                        size="small"
                        startIcon={<DownloadIcon fontSize="small" />}
                        onClick={() =>
                          handleDownloadPdf(inv.invoiceId, inv.invoiceNumber)
                        }
                        sx={{
                          color: ACCENT,
                          textTransform: "none",
                          fontSize: "0.78rem",
                          minWidth: 0,
                          "&:hover": {
                            backgroundColor: "rgba(139,64,73,0.06)",
                          },
                        }}
                      >
                        PDF
                      </Button>

                      {isPayable(inv.status) && (
                        <Button
                          size="small"
                          variant="outlined"
                          startIcon={<PaymentIcon fontSize="small" />}
                          onClick={() =>
                            setPayDialog({ open: true, invoice: inv })
                          }
                          sx={{
                            textTransform: "none",
                            fontSize: "0.78rem",
                            borderColor: ACCENT,
                            color: ACCENT,
                            "&:hover": {
                              borderColor: "#6e3139",
                              backgroundColor: "rgba(139,64,73,0.06)",
                            },
                          }}
                        >
                          Pay
                        </Button>
                      )}
                    </Box>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* Payment Dialog */}
      <PaymentDialog
        open={payDialog.open}
        invoice={payDialog.invoice}
        onClose={() => setPayDialog({ open: false, invoice: null })}
        onSuccess={handlePaySuccess}
        bankDetails={bankDetails ?? null}
      />

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
