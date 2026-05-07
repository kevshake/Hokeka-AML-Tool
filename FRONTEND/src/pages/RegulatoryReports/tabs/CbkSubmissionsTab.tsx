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
  TextField,
  Stack,
} from "@mui/material";
import { Refresh as ReplayIcon } from "@mui/icons-material";
import { useCbkSubmissions, useAllPsps } from "../../../features/api/queries";
import { useReplayCbkSubmission } from "../../../features/api/mutations";
import { CBK_ENDPOINT_LABELS } from "../../../types/cbk";
import type { CbkEndpointType } from "../../../types/cbk";

const ACCENT = "#8B4049";

const STATUS_COLORS: Record<string, { bg: string; color: string }> = {
  SUCCESS: { bg: "#e9f7ef", color: "#27ae60" },
  FAILED: { bg: "#fdedec", color: "#c0392b" },
  PENDING: { bg: "#fef5e7", color: "#e67e22" },
  RETRYING: { bg: "#f5eef8", color: "#8e44ad" },
};

export default function CbkSubmissionsTab() {
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(25);
  const [filterPspId, setFilterPspId] = useState<string>("");
  const [filterStatus, setFilterStatus] = useState<string>("");
  const [filterEndpoint, setFilterEndpoint] = useState<string>("");
  const [toast, setToast] = useState<{ open: boolean; severity: "success" | "error"; message: string }>({
    open: false,
    severity: "success",
    message: "",
  });
  const [replayingId, setReplayingId] = useState<number | null>(null);

  const { data: psps } = useAllPsps();
  const replay = useReplayCbkSubmission();

  const { data, isLoading, isError } = useCbkSubmissions({
    pspId: filterPspId || undefined,
    status: filterStatus || undefined,
    endpoint: filterEndpoint || undefined,
    page,
    size: rowsPerPage,
  });

  const rows = data?.content ?? [];
  const total = data?.totalElements ?? 0;

  const handleReplay = async (row: any) => {
    setReplayingId(row.id);
    try {
      await replay.mutateAsync({ endpointType: row.endpointType, pspId: row.pspId });
      setToast({ open: true, severity: "success", message: `Replay triggered for ${row.endpointType}.` });
    } catch {
      setToast({ open: true, severity: "error", message: "Replay failed." });
    } finally {
      setReplayingId(null);
    }
  };

  return (
    <Box>
      <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 2 }}>
        CBK Submission History
      </Typography>

      {/* Filters */}
      <Stack direction="row" spacing={2} sx={{ mb: 2, flexWrap: "wrap" }}>
        <FormControl size="small" sx={{ minWidth: 180 }}>
          <InputLabel>PSP</InputLabel>
          <Select value={filterPspId} label="PSP" onChange={(e) => { setFilterPspId(e.target.value); setPage(0); }}>
            <MenuItem value="">All PSPs</MenuItem>
            {(psps ?? []).map((p) => {
              const id = String(p.id ?? p.pspId ?? "");
              return (
                <MenuItem key={id} value={id}>
                  {p.legalName ?? p.tradingName ?? `PSP ${id}`}
                </MenuItem>
              );
            })}
          </Select>
        </FormControl>

        <FormControl size="small" sx={{ minWidth: 160 }}>
          <InputLabel>Status</InputLabel>
          <Select value={filterStatus} label="Status" onChange={(e) => { setFilterStatus(e.target.value); setPage(0); }}>
            <MenuItem value="">All</MenuItem>
            <MenuItem value="SUCCESS">Success</MenuItem>
            <MenuItem value="FAILED">Failed</MenuItem>
            <MenuItem value="PENDING">Pending</MenuItem>
            <MenuItem value="RETRYING">Retrying</MenuItem>
          </Select>
        </FormControl>

        <FormControl size="small" sx={{ minWidth: 240 }}>
          <InputLabel>Endpoint</InputLabel>
          <Select value={filterEndpoint} label="Endpoint" onChange={(e) => { setFilterEndpoint(e.target.value); setPage(0); }}>
            <MenuItem value="">All Endpoints</MenuItem>
            {(Object.keys(CBK_ENDPOINT_LABELS) as CbkEndpointType[]).map((ep) => (
              <MenuItem key={ep} value={ep}>{CBK_ENDPOINT_LABELS[ep]}</MenuItem>
            ))}
          </Select>
        </FormControl>

        <TextField
          size="small"
          label="Request ID"
          sx={{ minWidth: 160 }}
          placeholder="Search request ID"
          disabled
        />
      </Stack>

      {isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Failed to load CBK submission history.
        </Alert>
      )}

      <TableContainer
        component={Paper}
        sx={{ border: "1px solid rgba(0,0,0,0.08)", borderRadius: 2, backgroundColor: "background.paper" }}
      >
        <Table size="small">
          <TableHead>
            <TableRow sx={{ backgroundColor: "rgba(0,0,0,0.02)" }}>
              <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>PSP</TableCell>
              <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Endpoint</TableCell>
              <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Status</TableCell>
              <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Attempted At</TableCell>
              <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Request ID</TableCell>
              <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Records</TableCell>
              <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={7} align="center" sx={{ py: 6 }}>
                  <CircularProgress size={28} sx={{ color: ACCENT }} />
                </TableCell>
              </TableRow>
            ) : rows.length > 0 ? (
              rows.map((row: any) => {
                const statusCfg = STATUS_COLORS[row.status] ?? { bg: "#f4f6f7", color: "#7f8c8d" };
                const label =
                  CBK_ENDPOINT_LABELS[row.endpointType as CbkEndpointType] ?? row.endpointType;
                const psp = (psps ?? []).find(
                  (p) => String(p.id ?? p.pspId) === String(row.pspId)
                );
                const pspName = psp?.legalName ?? psp?.tradingName ?? `PSP ${row.pspId}`;
                return (
                  <TableRow key={row.id} hover>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontWeight: 500 }}>
                        {pspName}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontSize: "0.78rem" }}>
                        {label}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={row.status}
                        size="small"
                        sx={{
                          backgroundColor: statusCfg.bg,
                          color: statusCfg.color,
                          fontWeight: 500,
                          fontSize: "0.72rem",
                          height: 22,
                          borderRadius: 1,
                        }}
                      />
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" color="text.secondary" sx={{ fontSize: "0.78rem" }}>
                        {row.attemptedAt
                          ? new Date(row.attemptedAt).toLocaleString()
                          : "—"}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography
                        variant="body2"
                        sx={{ fontFamily: "monospace", fontSize: "0.72rem", color: "text.secondary" }}
                      >
                        {row.requestId ?? "—"}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" color="text.secondary">
                        {row.recordCount ?? "—"}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Button
                        size="small"
                        variant="outlined"
                        startIcon={
                          replayingId === row.id ? (
                            <CircularProgress size={14} />
                          ) : (
                            <ReplayIcon sx={{ fontSize: 16 }} />
                          )
                        }
                        disabled={replayingId === row.id}
                        onClick={() => handleReplay(row)}
                        sx={{
                          textTransform: "none",
                          fontSize: "0.75rem",
                          borderColor: ACCENT,
                          color: ACCENT,
                          "&:hover": { borderColor: "#6b313a", backgroundColor: "rgba(139,64,73,0.06)" },
                        }}
                      >
                        Replay
                      </Button>
                    </TableCell>
                  </TableRow>
                );
              })
            ) : (
              <TableRow>
                <TableCell colSpan={7} align="center" sx={{ py: 8, color: "text.disabled" }}>
                  <Typography variant="body1">No CBK submissions found</Typography>
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

      <Snackbar
        open={toast.open}
        autoHideDuration={4000}
        onClose={() => setToast((t) => ({ ...t, open: false }))}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      >
        <Alert severity={toast.severity} onClose={() => setToast((t) => ({ ...t, open: false }))} variant="filled">
          {toast.message}
        </Alert>
      </Snackbar>
    </Box>
  );
}
