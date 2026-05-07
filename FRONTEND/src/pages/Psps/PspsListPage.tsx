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
} from "@mui/material";
import {
  Search as SearchIcon,
  Settings as ConfigIcon,
  Business as PspIcon,
} from "@mui/icons-material";
import { useAllPsps } from "../../features/api/queries";

const ACCENT = "#8B4049";

export default function PspsListPage() {
  const navigate = useNavigate();
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(25);

  const { data: psps, isLoading, isError } = useAllPsps();

  const rows = (psps ?? []).filter((p) => {
    if (!search) return true;
    const q = search.toLowerCase();
    return (
      (p.legalName ?? "").toLowerCase().includes(q) ||
      (p.tradingName ?? "").toLowerCase().includes(q) ||
      (p.pspCode ?? p.code ?? "").toLowerCase().includes(q)
    );
  });

  const paginated = rows.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage);

  return (
    <Box>
      <Typography variant="h6" sx={{ fontWeight: 600, mb: 0.5 }}>
        Payment Service Providers
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Manage PSP configuration and CBK reporting settings.
      </Typography>

      <Box sx={{ display: "flex", justifyContent: "flex-end", mb: 2 }}>
        <TextField
          size="small"
          placeholder="Search PSPs…"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
          sx={{ width: 260 }}
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
          Failed to load PSPs.
        </Alert>
      )}

      <TableContainer
        component={Paper}
        sx={{
          backgroundColor: "background.paper",
          border: "1px solid rgba(0,0,0,0.08)",
          borderRadius: 2,
        }}
      >
        <Table size="small">
          <TableHead>
            <TableRow sx={{ backgroundColor: "rgba(0,0,0,0.02)" }}>
              <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Legal Name</TableCell>
              <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Trading Name</TableCell>
              <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Code</TableCell>
              <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>CBK Reporting</TableCell>
              <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={5} align="center" sx={{ py: 6 }}>
                  <CircularProgress size={28} sx={{ color: ACCENT }} />
                </TableCell>
              </TableRow>
            ) : paginated.length > 0 ? (
              paginated.map((psp) => {
                const id = psp.id ?? psp.pspId;
                const cbkEnabled = (psp as any).cbkReportingEnabled;
                return (
                  <TableRow
                    key={id}
                    hover
                    sx={{ "&:hover": { backgroundColor: `rgba(139,64,73,0.04)` } }}
                  >
                    <TableCell>
                      <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                        <PspIcon sx={{ fontSize: 18, color: ACCENT, opacity: 0.7 }} />
                        <Typography variant="body2" sx={{ fontWeight: 500 }}>
                          {psp.legalName ?? psp.name ?? `PSP ${id}`}
                        </Typography>
                      </Box>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" color="text.secondary">
                        {psp.tradingName ?? "—"}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" color="text.secondary">
                        {psp.pspCode ?? psp.code ?? "—"}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={cbkEnabled ? "Enabled" : "Disabled"}
                        size="small"
                        sx={{
                          backgroundColor: cbkEnabled ? "#e9f7ef" : "#f4f6f7",
                          color: cbkEnabled ? "#27ae60" : "#7f8c8d",
                          fontWeight: 500,
                          fontSize: "0.73rem",
                          height: 22,
                          borderRadius: 1,
                        }}
                      />
                    </TableCell>
                    <TableCell>
                      <Button
                        size="small"
                        variant="outlined"
                        startIcon={<ConfigIcon sx={{ fontSize: 15 }} />}
                        onClick={() => navigate(`/psps/${id}/configure`)}
                        sx={{
                          textTransform: "none",
                          fontSize: "0.78rem",
                          borderColor: ACCENT,
                          color: ACCENT,
                          "&:hover": {
                            borderColor: "#6b313a",
                            backgroundColor: "rgba(139,64,73,0.06)",
                          },
                        }}
                      >
                        Configure CBK
                      </Button>
                    </TableCell>
                  </TableRow>
                );
              })
            ) : (
              <TableRow>
                <TableCell colSpan={5} align="center" sx={{ py: 8, color: "text.disabled" }}>
                  <Typography variant="body1">No PSPs found</Typography>
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
          onRowsPerPageChange={(e) => {
            setRowsPerPage(parseInt(e.target.value, 10));
            setPage(0);
          }}
        />
      </TableContainer>
    </Box>
  );
}
