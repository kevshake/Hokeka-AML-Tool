import { useState } from "react";
import {
  Box,
  Typography,
  Paper,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  MenuItem,
  TextField,
  Button,
  CircularProgress,
  Alert,
} from "@mui/material";
import {
  CheckCircle,
  HourglassEmpty,
  ErrorOutline,
  Send,
} from "@mui/icons-material";
import {
  useCbkReports,
  useCbkSubmit,
  type CbkPeriod,
  type CbkSubmissionStatus,
} from "../../../features/api/cbkReportQueries";
import { Link } from "react-router-dom";
import { CBK_ENDPOINT_LABELS, type CbkEndpointType } from "../../../types/cbk";

const STATUS_CONFIG: Record<CbkSubmissionStatus, { label: string; color: "success" | "warning" | "error"; icon: typeof CheckCircle }> = {
  submitted: { label: "Submitted", color: "success", icon: CheckCircle },
  pending: { label: "Pending", color: "warning", icon: HourglassEmpty },
  failed: { label: "Failed", color: "error", icon: ErrorOutline },
};

const PERIOD_OPTIONS: { value: CbkPeriod; label: string }[] = [
  { value: "daily", label: "Daily" },
  { value: "monthly", label: "Monthly" },
  { value: "annual", label: "Annual" },
];

const ENDPOINT_CADENCE: Record<CbkEndpointType, CbkPeriod> = {
  SENIOR_MANAGEMENT: "annual",
  DIRECTORS: "annual",
  TRUSTEES: "annual",
  SHAREHOLDERS: "annual",
  CUSTOMER_COMPLAINTS: "monthly",
  PRODUCTS_INFO: "monthly",
  CARD_BRANDS: "monthly",
  TRANSACTION_DETAILS: "monthly",
  TRANSACTION_TARIFFS: "monthly",
  CYBER_INCIDENT: "daily",
  FRAUD_INCIDENTS: "daily",
  SYSTEM_STABILITY: "daily",
  SYSTEM_ACTIVITY: "daily",
  TRUST_ACCOUNT: "daily",
  BILLING_TEMPLATE: "daily",
  MERCHANT_TRANSACTIONS: "daily",
  FAILED_TRANSACTIONS: "daily",
};

function today(): string {
  return new Date().toISOString().split("T")[0];
}

function monthStart(): string {
  const d = new Date();
  d.setDate(1);
  return d.toISOString().split("T")[0];
}

interface CbkSubmissionPanelProps {
  onSubmitSuccess?: () => void;
}

export default function CbkSubmissionPanel({ onSubmitSuccess }: CbkSubmissionPanelProps) {
  const [endpointType, setEndpointType] = useState<CbkEndpointType>("CARD_BRANDS");
  const [period, setPeriod] = useState<CbkPeriod>("monthly");
  const [from, setFrom] = useState(monthStart());
  const [to, setTo] = useState(today());
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitSuccess, setSubmitSuccess] = useState<string | null>(null);

  const { data, isLoading, error, refetch } = useCbkReports({ period, from, to }, !!(from && to));
  const submitMutation = useCbkSubmit();

  const handleSubmit = async () => {
    setSubmitError(null);
    setSubmitSuccess(null);
    try {
      const result = await submitMutation.mutateAsync({
        endpointType,
        period,
        from,
        to,
      });
      setSubmitSuccess(
        `${result.message}${result.referenceNumber ? ` Reference: ${result.referenceNumber}` : ""}`
      );
      onSubmitSuccess?.();
      refetch();
    } catch (e) {
      setSubmitError(e instanceof Error ? e.message : "Submission failed");
    }
  };

  return (
    <Box>
      <Typography variant="subtitle1" fontWeight={700} sx={{ mb: 1.5, color: "var(--ink)" }}>
        CBK Submission Status
      </Typography>

      {/* Filters */}
      <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap", mb: 2 }}>
        <TextField
          select
          label="CBK endpoint"
          value={endpointType}
          onChange={(e) => {
            const next = e.target.value as CbkEndpointType;
            setEndpointType(next);
            setPeriod(ENDPOINT_CADENCE[next]);
          }}
          size="small"
          sx={{ minWidth: 260 }}
        >
          {(Object.keys(CBK_ENDPOINT_LABELS) as CbkEndpointType[]).map((value) => (
            <MenuItem key={value} value={value}>
              {CBK_ENDPOINT_LABELS[value]}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          label="Period"
          value={period}
          onChange={(e) => setPeriod(e.target.value as CbkPeriod)}
          size="small"
          sx={{ minWidth: 160 }}
        >
          {PERIOD_OPTIONS.map((o) => (
            <MenuItem key={o.value} value={o.value}>
              {o.label}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          label="From"
          type="date"
          size="small"
          value={from}
          onChange={(e) => setFrom(e.target.value)}
          InputLabelProps={{ shrink: true }}
        />
        <TextField
          label="To"
          type="date"
          size="small"
          value={to}
          onChange={(e) => setTo(e.target.value)}
          InputLabelProps={{ shrink: true }}
        />
        <Button
          variant="contained"
          startIcon={submitMutation.isPending ? <CircularProgress size={14} color="inherit" /> : <Send />}
          onClick={handleSubmit}
          disabled={submitMutation.isPending || !from || !to}
          sx={{
                        textTransform: "none",
          }}
        >
          Run CBK submission
        </Button>
      </Box>

      {submitSuccess && (
        <Alert severity="success" sx={{ mb: 2, borderRadius: "var(--radius)" }} onClose={() => setSubmitSuccess(null)}>
          {submitSuccess}
        </Alert>
      )}
      {submitError && (
        <Alert severity="error" sx={{ mb: 2, borderRadius: "var(--radius)" }} onClose={() => setSubmitError(null)}>
          {submitError}
        </Alert>
      )}
      {error && (
        <Alert severity="warning" sx={{ mb: 2, borderRadius: "var(--radius)" }}>
          Could not load submission history: {error.message}
        </Alert>
      )}

      {/* Submission Status Table */}
      <Paper sx={{ borderRadius: "var(--radius)", overflow: "hidden" }}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow sx={{ backgroundColor: "var(--surface-3)" }}>
                <TableCell sx={{ fontWeight: 700 }}>Report</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Period</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Date Range</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Status</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Records</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Reference</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Submitted At</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {isLoading && (
                <TableRow>
                  <TableCell colSpan={7} align="center" sx={{ py: 3 }}>
                    <CircularProgress size={20} sx={{ color: "var(--gold)" }} />
                  </TableCell>
                </TableRow>
              )}
              {!isLoading && (!data || data.content.length === 0) && (
                <TableRow>
                  <TableCell colSpan={7} align="center" sx={{ py: 3, color: "text.secondary" }}>
                    No submissions found for the selected period.
                  </TableCell>
                </TableRow>
              )}
              {data?.content.map((row) => {
                const cfg = STATUS_CONFIG[row.submissionStatus];
                const StatusIcon = cfg.icon;
                return (
                  <TableRow key={row.id} hover>
                    <TableCell><Link to={`/records/CBK_SUBMISSION/${row.id}`}>{row.reportType}</Link></TableCell>
                    <TableCell sx={{ textTransform: "capitalize" }}>{row.period}</TableCell>
                    <TableCell>
                      {row.from} – {row.to}
                    </TableCell>
                    <TableCell>
                      <Chip
                        icon={<StatusIcon sx={{ fontSize: "14px !important" }} />}
                        label={cfg.label}
                        color={cfg.color}
                        size="small"
                        sx={{ fontWeight: 600 }}
                      />
                    </TableCell>
                    <TableCell>{row.recordCount ?? "-"}</TableCell>
                    <TableCell>
                      <Typography variant="caption" sx={{ fontFamily: "monospace" }}>
                        {row.referenceNumber || "—"}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      {row.submittedAt
                        ? new Date(row.submittedAt).toLocaleString()
                        : row.submissionStatus === "failed" && row.errorMessage
                        ? <Typography variant="caption" color="error">{row.errorMessage}</Typography>
                        : "—"}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>
    </Box>
  );
}
