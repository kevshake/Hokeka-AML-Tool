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

const STATUS_CONFIG: Record<CbkSubmissionStatus, { label: string; color: "success" | "warning" | "error"; icon: typeof CheckCircle }> = {
  submitted: { label: "Submitted", color: "success", icon: CheckCircle },
  pending: { label: "Pending", color: "warning", icon: HourglassEmpty },
  failed: { label: "Failed", color: "error", icon: ErrorOutline },
};

const PERIOD_OPTIONS: { value: CbkPeriod; label: string }[] = [
  { value: "daily", label: "Daily" },
  { value: "weekly", label: "Weekly" },
  { value: "monthly", label: "Monthly" },
  { value: "quarterly", label: "Quarterly" },
  { value: "semi-annual", label: "Semi-Annual" },
  { value: "annual", label: "Annual" },
];

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
        reportId: "cbk-returns",
        period,
        from,
        to,
      });
      setSubmitSuccess(`Submitted. Reference: ${result.referenceNumber}`);
      onSubmitSuccess?.();
      refetch();
    } catch (e) {
      setSubmitError(e instanceof Error ? e.message : "Submission failed");
    }
  };

  return (
    <Box>
      <Typography variant="subtitle1" fontWeight={700} sx={{ mb: 1.5, color: "#2c3e50" }}>
        CBK Submission Status
      </Typography>

      {/* Filters */}
      <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap", mb: 2 }}>
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
            backgroundColor: "#800020",
            "&:hover": { backgroundColor: "#600018" },
            textTransform: "none",
          }}
        >
          Submit to CBK
        </Button>
      </Box>

      {submitSuccess && (
        <Alert severity="success" sx={{ mb: 2, borderRadius: "8px" }} onClose={() => setSubmitSuccess(null)}>
          {submitSuccess}
        </Alert>
      )}
      {submitError && (
        <Alert severity="error" sx={{ mb: 2, borderRadius: "8px" }} onClose={() => setSubmitError(null)}>
          {submitError}
        </Alert>
      )}
      {error && (
        <Alert severity="warning" sx={{ mb: 2, borderRadius: "8px" }}>
          Could not load submission history: {error.message}
        </Alert>
      )}

      {/* Submission Status Table */}
      <Paper sx={{ borderRadius: "12px", overflow: "hidden" }}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow sx={{ backgroundColor: "rgba(128, 0, 32, 0.06)" }}>
                <TableCell sx={{ fontWeight: 700 }}>Report</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Period</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Date Range</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Status</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Reference</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Submitted At</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {isLoading && (
                <TableRow>
                  <TableCell colSpan={6} align="center" sx={{ py: 3 }}>
                    <CircularProgress size={20} sx={{ color: "#800020" }} />
                  </TableCell>
                </TableRow>
              )}
              {!isLoading && (!data || data.content.length === 0) && (
                <TableRow>
                  <TableCell colSpan={6} align="center" sx={{ py: 3, color: "text.secondary" }}>
                    No submissions found for the selected period.
                  </TableCell>
                </TableRow>
              )}
              {data?.content.map((row) => {
                const cfg = STATUS_CONFIG[row.submissionStatus];
                const StatusIcon = cfg.icon;
                return (
                  <TableRow key={row.id} hover>
                    <TableCell>{row.reportType}</TableCell>
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
