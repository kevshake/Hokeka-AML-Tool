import { useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Box,
  Chip,
  CircularProgress,
  Alert,
  Typography,
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
  MenuItem,
} from "@mui/material";
import {
  DownloadOutlined as DownloadIcon,
  CreditCard as CreditCardIcon,
  BarChart as BarChartIcon,
  Receipt as ReceiptIcon,
  Payment as PaymentIcon,
  AccountBalance as BankIcon,
} from "@mui/icons-material";
import { useState, type ReactNode } from "react";
import { apiClient } from "../../../lib/apiClient";
import { getApiUrl } from "../../../config/api";
import { Link } from "react-router-dom";
import { useAuth } from "../../../contexts/AuthContext";
import PlanUsageCard from "./PlanUsageCard";
import GlassCard from "../../../components/Common/GlassCard";
import TwBadge from "../../../components/Common/TwBadge";
import {
  SCREENING_SKUS,
  serviceTypeLabel,
  type BillingServiceSku,
} from "../../../lib/billingServiceTypes";

const ACCENT = "var(--gold)";

interface EffectiveRate {
  rateId?: number;
  serviceType: string;
  baseRate: number;
  currency: string;
  isActive?: boolean;
  description?: string;
}

// ─── Types ─────────────────────────────────────────────────────────────────

// Matches backend SubscriptionResponse (flat — no nested `tier` object).
interface Subscription {
  subscriptionId: number;
  status: string;
  billingCycle: string;
  tierCode: string;
  tierName: string;
  monthlyFeeUsd: number;
  billingCurrency: string;
  includedChecks: number;
  contractStart: string;
  contractEnd: string | null;
  trialEndsAt: string | null;
}

// Matches backend UsageSummaryResponse.ServiceBreakdown.
interface UsageLineItem {
  serviceType: string;
  count: number;
  costUsd: number;
}

// Matches backend UsageSummaryResponse (costs are USD; `period` is a single label).
interface CurrentUsage {
  pspId: number;
  period: string;
  totalRequests: number;
  billableRequests: number;
  totalCostUsd: number;
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

type PaymentMethod = "CARD" | "BANK_TRANSFER";

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
    <GlassCard padding="sm" glowVariant="gold" static className="h-full !p-4">
      <p className="text-[0.6563rem] font-semibold uppercase tracking-widest text-ink-muted">{label}</p>
      <p className="mt-1 font-display text-2xl font-semibold tracking-tight text-gold">{value}</p>
      {sub ? <p className="mt-1 text-xs text-ink-muted">{sub}</p> : null}
    </GlassCard>
  );
}

function SectionTitle({ icon, title, hint }: { icon: ReactNode; title: string; hint?: string }) {
  return (
    <div className="mb-3 flex flex-wrap items-center gap-2">
      <span className="text-gold">{icon}</span>
      <Typography variant="subtitle1" sx={{ fontWeight: 600, fontFamily: "var(--font-display)" }}>
        {title}
      </Typography>
      {hint ? (
        <Typography variant="caption" color="text.secondary" sx={{ ml: "auto" }}>
          {hint}
        </Typography>
      ) : null}
    </div>
  );
}

function SkuRateCard({ sku, rate }: { sku: BillingServiceSku; rate: EffectiveRate | null }) {
  const amount = rate?.baseRate ?? sku.defaultRateUsd;
  const currency = rate?.currency ?? "USD";
  return (
    <GlassCard padding="sm" glowVariant={sku.glow} static className="h-full !p-4">
      <p className="font-display text-sm font-semibold text-ink">{sku.label}</p>
      <p className="mt-0.5 min-h-[2.5rem] text-xs leading-relaxed text-ink-muted">{sku.description}</p>
      <div className="mt-3 flex items-baseline gap-1">
        <span className="font-display text-xl font-semibold text-gold">
          {new Intl.NumberFormat("en-US", {
            style: "currency",
            currency,
            minimumFractionDigits: 2,
            maximumFractionDigits: 4,
          }).format(Number(amount))}
        </span>
        <span className="text-xs text-ink-muted">/ request</span>
      </div>
      <div className="mt-2">
        <TwBadge variant={rate ? "gold" : "default"}>{rate ? "Your rate" : "Platform default"}</TwBadge>
      </div>
    </GlassCard>
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
  const [payMethod, setPayMethod] = useState<PaymentMethod>("CARD");
  const [cardTokenRef, setCardTokenRef] = useState("");
  const [cardLast4, setCardLast4] = useState("");
  const [cardBrand, setCardBrand] = useState("VISA");
  const [cardExpiryMonth, setCardExpiryMonth] = useState("");
  const [cardExpiryYear, setCardExpiryYear] = useState("");
  const [bankRef, setBankRef] = useState("");
  const [paying, setPaying] = useState(false);
  const [result, setResult] = useState<{
    severity: "success" | "error" | "info";
    message: string;
  } | null>(null);

  const handleClose = () => {
    if (paying) return;
    setResult(null);
    setCardTokenRef("");
    setCardLast4("");
    setCardExpiryMonth("");
    setCardExpiryYear("");
    setBankRef("");
    setPayMethod("CARD");
    onClose();
  };

  const handleSubmit = async () => {
    if (!invoice) return;
    setPaying(true);
    setResult(null);

    try {
      if (payMethod === "CARD" && cardTokenRef.trim()) {
        await fetch(getApiUrl(`billing/payment-methods?pspId=${sessionStorage.getItem("_psp") ?? "0"}`), {
          method: "POST",
          credentials: "include",
          headers: {
            "Content-Type": "application/json",
            "X-PSP-ID": sessionStorage.getItem("_psp") ?? "0",
          },
          body: JSON.stringify({
            tokenVaultRef: cardTokenRef.trim(),
            last4: cardLast4.trim(),
            brand: cardBrand,
            expiryMonth: Number(cardExpiryMonth),
            expiryYear: Number(cardExpiryYear),
          }),
        });
      }

      const body: {
        invoiceId: number;
        paymentMethod: PaymentMethod;
        bankReference?: string;
      } = {
        invoiceId: invoice.invoiceId,
        paymentMethod: payMethod,
      };

      if (payMethod === "BANK_TRANSFER") {
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
            backgroundColor: "var(--surface-2)",
            border: "1px solid color-mix(in srgb, var(--gold) 15%, transparent)",
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
          <ToggleButton value="CARD" sx={{ textTransform: "none", gap: 0.5 }}>
            <BankIcon fontSize="small" />
            Card (annual)
          </ToggleButton>
          <ToggleButton value="BANK_TRANSFER" sx={{ textTransform: "none", gap: 0.5 }}>
            <BankIcon fontSize="small" />
            Bank Transfer
          </ToggleButton>
        </ToggleButtonGroup>

        {payMethod === "CARD" && (
          <Box sx={{ display: "grid", gap: 1.5 }}>
            <TextField
              label="Token vault reference"
              value={cardTokenRef}
              onChange={(e) => setCardTokenRef(e.target.value)}
              fullWidth
              size="small"
              disabled={paying}
              helperText="Tokenized card reference from your vault (never store PAN here)."
            />
            <TextField
              label="Last 4 digits"
              value={cardLast4}
              onChange={(e) => setCardLast4(e.target.value)}
              fullWidth
              size="small"
              disabled={paying}
            />
            <TextField
              select
              label="Brand"
              value={cardBrand}
              onChange={(e) => setCardBrand(e.target.value)}
              fullWidth
              size="small"
              disabled={paying}
            >
              <MenuItem value="VISA">VISA</MenuItem>
              <MenuItem value="MASTERCARD">MASTERCARD</MenuItem>
              <MenuItem value="AMEX">AMEX</MenuItem>
            </TextField>
            <Box sx={{ display: "flex", gap: 1 }}>
              <TextField
                label="Expiry month"
                value={cardExpiryMonth}
                onChange={(e) => setCardExpiryMonth(e.target.value)}
                size="small"
                disabled={paying}
              />
              <TextField
                label="Expiry year"
                value={cardExpiryYear}
                onChange={(e) => setCardExpiryYear(e.target.value)}
                size="small"
                disabled={paying}
              />
            </Box>
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
              (payMethod === "CARD" && (!cardTokenRef.trim() || !cardLast4.trim())) ||
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
              "&:hover": { backgroundColor: "var(--surface-3)" },
            }}
          >
            {paying
              ? "Processing..."
              : payMethod === "CARD"
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
  // /entitlements/me reports the CALLER's own tenant, so only show the plan/usage panel when the
  // tab is showing the caller's own PSP (self-service), not when an admin is viewing another PSP.
  const { user } = useAuth();
  const isOwnTenant = !!user && String(user.pspId) === String(pspId);
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

  const skuRateQueries = useQueries({
    queries: SCREENING_SKUS.map((sku) => ({
      queryKey: ["billing", "rate", pspId, sku.code],
      queryFn: () =>
        apiClient
          .get<EffectiveRate>(`billing/rates?pspId=${pspId}&serviceType=${sku.code}`)
          .catch(() => null),
      enabled: !!pspId,
      staleTime: 5 * 60_000,
    })),
  });

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
      {/* ── Section 0: Plan entitlements + usage against quota (self-service only) ──── */}
      {isOwnTenant && <PlanUsageCard />}

      {/* ── Unit rates (screening SKUs) ─────────────────────────────────── */}
      <span className="hokeka-section-label">Pricing</span>
      <SectionTitle icon={<ReceiptIcon sx={{ fontSize: 20 }} />} title="Unit rates" hint="Per-request screening prices" />
      <Grid container spacing={2} sx={{ mb: 4 }}>
        {SCREENING_SKUS.map((sku, index) => (
          <Grid item xs={12} sm={6} md={3} key={sku.code}>
            <SkuRateCard sku={sku} rate={skuRateQueries[index]?.data ?? null} />
          </Grid>
        ))}
      </Grid>

      {/* ── Section 1: Current Plan ─────────────────────────────────────── */}
      <span className="hokeka-section-label">Subscription</span>
      <SectionTitle icon={<CreditCardIcon sx={{ fontSize: 20 }} />} title="Current plan" />

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
        <GlassCard padding="md" glowVariant="gold" static className="mb-8">
            <Box sx={{ display: "flex", alignItems: "center", gap: 2, mb: 2, flexWrap: "wrap" }}>
              <Typography variant="h6" sx={{ fontWeight: 700, fontFamily: "var(--font-display)" }}>
                <Link to={`/records/SUBSCRIPTION/${subscription.subscriptionId}`}>{subscription.tierName}</Link>
              </Typography>
              <Chip
                label={subscription.tierName.toUpperCase()}
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
              {subscription.billingCycle === "ANNUAL" && (
                <TwBadge variant="gold">Annual billing</TwBadge>
              )}
            </Box>

            {/* Trial warning */}
            {subscription.trialEndsAt && (
              <Alert
                severity="warning"
                sx={{
                  mb: 2,
                  backgroundColor: "rgba(255, 167, 38, 0.1)",
                  border: "1px solid rgba(255, 167, 38, 0.4)",
                }}
              >
                Trial ends on{" "}
                <strong>{fmtDate(subscription.trialEndsAt)}</strong>. After
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
                    subscription.monthlyFeeUsd,
                    subscription.billingCurrency
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
                  {fmtNumber(subscription.includedChecks)} / month
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
                  {subscription.billingCurrency}
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
                  {fmtDate(subscription.contractStart)}
                </Typography>
              </Grid>
              {subscription.contractEnd && (
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
                    {fmtDate(subscription.contractEnd)}
                  </Typography>
                </Grid>
              )}
            </Grid>

            <Box sx={{ mt: 2 }}>
              <Typography variant="caption" color="text.secondary">
                To upgrade your plan or switch to annual billing, contact your account manager.
              </Typography>
            </Box>
        </GlassCard>
      )}

      {/* ── Section 2: Current Month Usage ──────────────────────────────── */}
      <span className="hokeka-section-label">Usage</span>
      <SectionTitle
        icon={<BarChartIcon sx={{ fontSize: 20 }} />}
        title="Current month usage"
        hint="Auto-refreshes every 60 s"
      />

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
                sub={`Period: ${usage.period}`}
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
                value={fmtMoney(usage.totalCostUsd, "USD")}
                sub="Current month estimate"
              />
            </Grid>
          </Grid>

          {usage.breakdown && usage.breakdown.length > 0 && (
            <div className="hokeka-table-wrap">
              <table className="hokeka-table">
                <thead>
                  <tr>
                    <th>Service</th>
                    <th className="text-right">Requests</th>
                    <th className="text-right">Cost (USD)</th>
                  </tr>
                </thead>
                <tbody>
                  {usage.breakdown.map((line) => (
                    <tr key={line.serviceType}>
                      <td>
                        <span className="font-medium">{serviceTypeLabel(line.serviceType)}</span>
                        <span className="mt-0.5 block font-mono text-[0.68rem] text-ink-muted">
                          {line.serviceType}
                        </span>
                      </td>
                      <td className="text-right">{fmtNumber(line.count)}</td>
                      <td className="text-right font-semibold text-gold">
                        {fmtMoney(line.costUsd, "USD")}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Box>
      )}

      {/* ── Section 3: Invoice History ───────────────────────────────────── */}
      <span className="hokeka-section-label">Invoices</span>
      <SectionTitle icon={<ReceiptIcon sx={{ fontSize: 20 }} />} title="Invoice history" />

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
        <div className="hokeka-table-wrap mb-6">
          <table className="hokeka-table">
            <thead>
              <tr>
                <th>Invoice #</th>
                <th>Period</th>
                <th className="text-right">Amount</th>
                <th>Currency</th>
                <th>Status</th>
                <th>Due</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {invoices.map((inv) => (
                <tr key={inv.invoiceId}>
                  <td className="font-mono text-sm">
                    <Link to={`/records/INVOICE/${inv.invoiceId}`}>{inv.invoiceNumber}</Link>
                  </td>
                  <td className="text-sm">
                    {fmtDate(inv.billingPeriodStart)} – {fmtDate(inv.billingPeriodEnd)}
                  </td>
                  <td className="text-right font-semibold text-gold">
                    {fmtMoney(inv.totalAmount, inv.currency)}
                  </td>
                  <td>{inv.currency}</td>
                  <td>
                    <Chip
                      label={inv.status}
                      size="small"
                      color={invoiceStatusColor(inv.status)}
                      sx={{ fontWeight: 600, fontSize: "0.7rem" }}
                    />
                  </td>
                  <td>{fmtDate(inv.dueDate)}</td>
                  <td>
                    <Box sx={{ display: "flex", gap: 0.5 }}>
                      <Button
                        size="small"
                        startIcon={<DownloadIcon fontSize="small" />}
                        onClick={() => handleDownloadPdf(inv.invoiceId, inv.invoiceNumber)}
                        sx={{
                          color: ACCENT,
                          textTransform: "none",
                          fontSize: "0.78rem",
                          minWidth: 0,
                          "&:hover": { backgroundColor: "var(--surface-3)" },
                        }}
                      >
                        PDF
                      </Button>
                      {isPayable(inv.status) && (
                        <Button
                          size="small"
                          variant="outlined"
                          startIcon={<PaymentIcon fontSize="small" />}
                          onClick={() => setPayDialog({ open: true, invoice: inv })}
                          sx={{
                            textTransform: "none",
                            fontSize: "0.78rem",
                            borderColor: ACCENT,
                            color: ACCENT,
                            "&:hover": {
                              borderColor: "var(--gold)",
                              backgroundColor: "var(--surface-3)",
                            },
                          }}
                        >
                          Pay
                        </Button>
                      )}
                    </Box>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
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
