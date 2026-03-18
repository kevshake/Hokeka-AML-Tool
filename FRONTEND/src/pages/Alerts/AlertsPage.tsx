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
} from "@mui/material";
import { useState } from "react";
import { useAlerts } from "../../features/api/queries";
import type { Priority } from "../../types";

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
  
  const { data: alerts, isLoading, isError, error } = useAlerts({
    page: page.index,
    size: page.size,
  });

  return (
    <Box>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 3, pb: 2, borderBottom: "1px solid", borderColor: "divider" }}>
        <Typography variant="h6" sx={{ color: "text.primary", fontWeight: 600 }}>
          Alerts
        </Typography>
        <Tooltip title="Perform bulk actions on multiple selected alerts simultaneously. You can mark multiple alerts as resolved, assign priority levels, or apply status changes to all selected items at once. Select alerts using the checkboxes in the table before using this feature." arrow enterDelay={2000}>
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
                    <Tooltip title="View comprehensive details about this alert including transaction information, risk score, merchant details, investigation history, and all related case information. Opens a detailed view panel with full alert context." arrow enterDelay={2000}>
                      <Button size="small" sx={{ color: "#a93226" }}>
                        View
                      </Button>
                    </Tooltip>
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
    </Box>
  );
}

