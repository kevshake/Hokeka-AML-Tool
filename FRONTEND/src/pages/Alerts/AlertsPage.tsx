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
} from "@mui/material";
import { useState } from "react";
import { useAlerts } from "../../features/api/queries";
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

  const { data: alerts, isLoading, isError, error } = useAlerts({
    page: page.index,
    size: page.size,
  });

  const handleExportCSV = () => {
    const content = alerts?.content || [];
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
            disabled={!alerts?.content?.length}
            onClick={handleExportCSV}
            sx={{ textTransform: "none", color: "text.secondary", borderColor: "rgba(0,0,0,0.2)", fontSize: "0.75rem" }}
          >
            Export CSV
          </Button>
        </Box>
        <Tooltip title="Perform bulk actions on multiple selected alerts simultaneously." arrow enterDelay={2000}>
          <Button variant="contained" sx={{ backgroundColor: "#a93226", "&:hover": { backgroundColor: "#922b21" } }}>
            Bulk Actions
          </Button>
        </Tooltip>
      </Box>

      <TableContainer component={Paper} sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.08)", borderRadius: 2 }}>
        <Table>
          <TableHead>
            <TableRow>
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
                <TableCell colSpan={7} align="center" sx={{ color: "text.disabled", py: 2 }}>
                  <CircularProgress size={24} />
                </TableCell>
              </TableRow>
            ) : isError ? (
              <TableRow>
                <TableCell colSpan={7} align="center" sx={{ color: "#e74c3c", py: 2 }}>
                  Error loading alerts: {error instanceof Error ? error.message : "Unknown error"}
                </TableCell>
              </TableRow>
            ) : alerts?.content && alerts.content.length > 0 ? (
              alerts.content.map((alert) => (
                <TableRow key={alert.id} hover>
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
                <TableCell colSpan={7} align="center" sx={{ color: "text.disabled", py: 2 }}>
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
          onPageChange={(_, newPage) => setPage(prev => ({ ...prev, index: newPage }))}
          onRowsPerPageChange={(e) => setPage({ index: 0, size: parseInt(e.target.value, 10) })}
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
    </Box>
  );
}
