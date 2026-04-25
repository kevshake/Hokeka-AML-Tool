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
  Tooltip,
  TablePagination,
  CircularProgress,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Grid,
  Divider,
  Checkbox,
  Menu,
  MenuItem,
  Snackbar,
  Alert as MuiAlert,
} from "@mui/material";
import { useState } from "react";
import { useAlerts } from "../../features/api/queries";
import { useUpdateAlertStatus } from "../../features/api/mutations";
import type { Alert, Priority } from "../../types";

const priorityColors: Record<Priority, string> = {
  CRITICAL: "#e74c3c",
  HIGH: "#e67e22",
  MEDIUM: "#f39c12",
  LOW: "#95a5a6",
};

const statusColors: Record<string, string> = {
  OPEN: "#e74c3c",
  INVESTIGATING: "#f39c12",
  RESOLVED: "#2ecc71",
};

export default function AlertsPage() {
  const [page, setPage] = useState({ index: 0, size: 25 });
  const [viewAlert, setViewAlert] = useState<Alert | null>(null);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [bulkMenuAnchor, setBulkMenuAnchor] = useState<null | HTMLElement>(null);
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: "success" | "error" }>({
    open: false, message: "", severity: "success",
  });

  const { data: alerts, isLoading, isError, error } = useAlerts({
    page: page.index,
    size: page.size,
  });

  const updateStatus = useUpdateAlertStatus();

  const content = alerts?.content || [];

  const handleSelectAll = (checked: boolean) => {
    if (checked) {
      setSelected(new Set(content.map(a => a.id)));
    } else {
      setSelected(new Set());
    }
  };

  const handleSelectOne = (id: number, checked: boolean) => {
    setSelected(prev => {
      const next = new Set(prev);
      if (checked) next.add(id); else next.delete(id);
      return next;
    });
  };

  const handleBulkAction = async (status: string) => {
    setBulkMenuAnchor(null);
    const ids = Array.from(selected);
    if (!ids.length) return;
    try {
      await Promise.all(ids.map(id => updateStatus.mutateAsync({ id, status })));
      setSelected(new Set());
      setSnackbar({ open: true, message: `${ids.length} alert(s) marked as ${status}.`, severity: "success" });
    } catch {
      setSnackbar({ open: true, message: "Failed to update some alerts.", severity: "error" });
    }
  };

  const handleExportCSV = () => {
    if (!content.length) return;
    const headers = ["ID", "Type", "Priority", "Status", "Description", "Transaction ID", "Case ID", "Created", "Resolved"];
    const rows = content.map(a => [
      a.id,
      a.alertType,
      a.priority,
      a.status,
      (a.description || "").replace(/,/g, ";"),
      a.transactionId ?? "",
      a.caseId ?? "",
      new Date(a.createdAt).toISOString().split("T")[0],
      a.resolvedAt ? new Date(a.resolvedAt).toISOString().split("T")[0] : "",
    ]);
    const csv = [headers, ...rows].map(r => r.join(",")).join("\n");
    const blob = new Blob([csv], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `alerts-${new Date().toISOString().split("T")[0]}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const allSelected = content.length > 0 && selected.size === content.length;
  const someSelected = selected.size > 0 && selected.size < content.length;

  return (
    <Box>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 3, pb: 2, borderBottom: "1px solid", borderColor: "divider" }}>
        <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
          <Typography variant="h6" sx={{ color: "text.primary", fontWeight: 600 }}>
            Alerts
          </Typography>
          <Button
            size="small"
            variant="outlined"
            disabled={!content.length}
            onClick={handleExportCSV}
            sx={{ textTransform: "none", color: "text.secondary", borderColor: "rgba(0,0,0,0.2)", fontSize: "0.75rem" }}
          >
            Export CSV
          </Button>
          {selected.size > 0 && (
            <Typography variant="caption" sx={{ color: "text.secondary" }}>
              {selected.size} selected
            </Typography>
          )}
        </Box>
        <Tooltip title={selected.size === 0 ? "Select alerts using checkboxes to perform bulk actions." : `Apply an action to ${selected.size} selected alert(s).`} arrow enterDelay={selected.size === 0 ? 2000 : 0}>
          <span>
            <Button
              variant="contained"
              disabled={selected.size === 0}
              onClick={(e) => setBulkMenuAnchor(e.currentTarget)}
              sx={{ backgroundColor: "#a93226", "&:hover": { backgroundColor: "#922b21" }, "&.Mui-disabled": { backgroundColor: "rgba(0,0,0,0.12)" } }}
            >
              Bulk Actions {selected.size > 0 ? `(${selected.size})` : ""}
            </Button>
          </span>
        </Tooltip>
        <Menu anchorEl={bulkMenuAnchor} open={!!bulkMenuAnchor} onClose={() => setBulkMenuAnchor(null)}>
          <MenuItem onClick={() => handleBulkAction("INVESTIGATING")}>Mark as Investigating</MenuItem>
          <MenuItem onClick={() => handleBulkAction("RESOLVED")}>Mark as Resolved</MenuItem>
          <MenuItem onClick={() => handleBulkAction("OPEN")}>Reopen</MenuItem>
        </Menu>
      </Box>

      <TableContainer component={Paper} sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.08)", borderRadius: 2 }}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell padding="checkbox">
                <Checkbox
                  indeterminate={someSelected}
                  checked={allSelected}
                  onChange={(e) => handleSelectAll(e.target.checked)}
                  disabled={!content.length}
                />
              </TableCell>
              <TableCell sx={{ color: "text.secondary", width: 80 }}>ID</TableCell>
              <TableCell sx={{ color: "text.secondary", width: 150 }}>Type</TableCell>
              <TableCell sx={{ color: "text.secondary", width: 100 }}>Priority</TableCell>
              <TableCell sx={{ color: "text.secondary", width: 100 }}>Status</TableCell>
              <TableCell sx={{ color: "text.secondary" }}>Description</TableCell>
              <TableCell sx={{ color: "text.secondary", width: 120 }}>Created</TableCell>
              <TableCell sx={{ color: "text.secondary", width: 100 }}>Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={8} align="center" sx={{ color: "text.disabled", py: 2 }}>
                  <CircularProgress size={24} />
                </TableCell>
              </TableRow>
            ) : isError ? (
              <TableRow>
                <TableCell colSpan={8} align="center" sx={{ color: "#e74c3c", py: 2 }}>
                  Error loading alerts: {error instanceof Error ? error.message : "Unknown error"}
                </TableCell>
              </TableRow>
            ) : content.length > 0 ? (
              content.map((alert) => (
                <TableRow
                  key={alert.id}
                  hover
                  selected={selected.has(alert.id)}
                  sx={{ "&.Mui-selected": { backgroundColor: "rgba(169,50,38,0.04)" } }}
                >
                  <TableCell padding="checkbox">
                    <Checkbox
                      checked={selected.has(alert.id)}
                      onChange={(e) => handleSelectOne(alert.id, e.target.checked)}
                    />
                  </TableCell>
                  <TableCell sx={{ color: "text.primary", py: 2 }}>#{alert.id}</TableCell>
                  <TableCell sx={{ color: "text.primary", py: 2 }}>{alert.alertType}</TableCell>
                  <TableCell sx={{ py: 2 }}>
                    <Chip
                      label={alert.priority}
                      size="small"
                      sx={{
                        backgroundColor: priorityColors[alert.priority] + "20",
                        color: priorityColors[alert.priority],
                        border: `1px solid ${priorityColors[alert.priority]}`,
                        fontWeight: 600,
                      }}
                    />
                  </TableCell>
                  <TableCell sx={{ py: 2 }}>
                    <Chip
                      label={alert.status}
                      size="small"
                      sx={{
                        backgroundColor: statusColors[alert.status] + "20",
                        color: statusColors[alert.status],
                        border: `1px solid ${statusColors[alert.status]}`,
                        fontWeight: 600,
                      }}
                    />
                  </TableCell>
                  <TableCell sx={{ color: "text.primary", py: 2 }}>
                    {alert.description || "-"}
                  </TableCell>
                  <TableCell sx={{ color: "text.secondary", py: 2 }}>
                    {new Date(alert.createdAt).toLocaleDateString()}
                  </TableCell>
                  <TableCell sx={{ py: 2 }}>
                    <Button size="small" sx={{ color: "#a93226", textTransform: "none" }} onClick={() => setViewAlert(alert)}>
                      View
                    </Button>
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell colSpan={8} align="center" sx={{ color: "text.disabled", py: 2 }}>
                  No alerts found
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
        <TablePagination
          rowsPerPageOptions={[10, 25, 50, 100]}
          component="div"
          count={alerts?.totalElements || 0}
          rowsPerPage={page.size}
          page={page.index}
          onPageChange={(_, newPage) => { setPage(prev => ({ ...prev, index: newPage })); setSelected(new Set()); }}
          onRowsPerPageChange={(e) => { setPage({ index: 0, size: parseInt(e.target.value, 10) }); setSelected(new Set()); }}
        />
      </TableContainer>

      {/* Alert Detail Modal */}
      <Dialog open={!!viewAlert} onClose={() => setViewAlert(null)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <Box>
            <Typography variant="h6">Alert #{viewAlert?.id}</Typography>
            <Typography variant="caption" color="text.secondary">{viewAlert?.alertType}</Typography>
          </Box>
          <Box sx={{ display: "flex", gap: 1 }}>
            {viewAlert && (
              <Chip
                label={viewAlert.priority}
                size="small"
                sx={{
                  backgroundColor: priorityColors[viewAlert.priority] + "20",
                  color: priorityColors[viewAlert.priority],
                  fontWeight: 600,
                }}
              />
            )}
            {viewAlert && (
              <Chip
                label={viewAlert.status}
                size="small"
                sx={{
                  backgroundColor: statusColors[viewAlert.status] + "20",
                  color: statusColors[viewAlert.status],
                  fontWeight: 600,
                }}
              />
            )}
          </Box>
        </DialogTitle>
        <Divider />
        {viewAlert && (
          <DialogContent sx={{ pt: 2 }}>
            <Grid container spacing={2}>
              <Grid item xs={12}>
                <Typography variant="overline" color="text.secondary">Description</Typography>
                <Typography variant="body2" sx={{ mt: 0.5, lineHeight: 1.6 }}>
                  {viewAlert.description || "No description provided."}
                </Typography>
              </Grid>
              {viewAlert.transactionId && (
                <Grid item xs={12} sm={6}>
                  <Typography variant="overline" color="text.secondary">Transaction ID</Typography>
                  <Typography variant="body2" sx={{ mt: 0.5, fontFamily: "monospace" }}>
                    #{viewAlert.transactionId}
                  </Typography>
                </Grid>
              )}
              {viewAlert.caseId && (
                <Grid item xs={12} sm={6}>
                  <Typography variant="overline" color="text.secondary">Linked Case</Typography>
                  <Typography variant="body2" sx={{ mt: 0.5, fontFamily: "monospace" }}>
                    Case #{viewAlert.caseId}
                  </Typography>
                </Grid>
              )}
              <Grid item xs={12} sm={6}>
                <Typography variant="overline" color="text.secondary">Created</Typography>
                <Typography variant="body2" sx={{ mt: 0.5 }}>
                  {new Date(viewAlert.createdAt).toLocaleString()}
                </Typography>
              </Grid>
              {viewAlert.resolvedAt && (
                <Grid item xs={12} sm={6}>
                  <Typography variant="overline" color="text.secondary">Resolved</Typography>
                  <Typography variant="body2" sx={{ mt: 0.5, color: "#2ecc71" }}>
                    {new Date(viewAlert.resolvedAt).toLocaleString()}
                  </Typography>
                </Grid>
              )}
            </Grid>
          </DialogContent>
        )}
        <DialogActions>
          <Button onClick={() => setViewAlert(null)} sx={{ textTransform: "none" }}>Close</Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={6000}
        onClose={() => setSnackbar(prev => ({ ...prev, open: false }))}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      >
        <MuiAlert severity={snackbar.severity} onClose={() => setSnackbar(prev => ({ ...prev, open: false }))} sx={{ width: "100%" }}>
          {snackbar.message}
        </MuiAlert>
      </Snackbar>
    </Box>
  );
}
