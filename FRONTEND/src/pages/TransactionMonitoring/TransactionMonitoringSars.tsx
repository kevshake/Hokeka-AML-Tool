import { Box, Typography, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Paper, Chip } from "@mui/material";
import { useSarReports } from "../../features/api/queries";

export default function TransactionMonitoringSars() {
  const { data: sars, isLoading } = useSarReports();

  const transactionSars = sars?.filter((sar) => sar.suspiciousActivityType?.toLowerCase().includes("transaction")) || [];

  return (
    <Box>
      <Typography variant="h6" sx={{ color: "text.primary", mb: 3 }}>
        Transaction-Related SAR Reports
      </Typography>

      <Paper sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell sx={{ color: "text.secondary" }}>Reference</TableCell>
                <TableCell sx={{ color: "text.secondary" }}>Status</TableCell>
                <TableCell sx={{ color: "text.secondary" }}>Activity Type</TableCell>
                <TableCell sx={{ color: "text.secondary" }}>Created</TableCell>
                <TableCell sx={{ color: "text.secondary" }}>Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {isLoading ? (
                <TableRow>
                  <TableCell colSpan={5} align="center" sx={{ color: "text.disabled", py: 4 }}>
                    Loading SAR reports...
                  </TableCell>
                </TableRow>
              ) : transactionSars.length > 0 ? (
                transactionSars.map((sar) => (
                  <TableRow key={sar.id} hover>
                    <TableCell sx={{ color: "text.primary" }}>{sar.sarReference}</TableCell>
                    <TableCell>
                      <Chip
                        label={sar.status}
                        size="small"
                        sx={{
                          backgroundColor: sar.status === "FILED" ? "#2ecc7120" : "#f39c1220",
                          color: sar.status === "FILED" ? "#2ecc71" : "#f39c12",
                          border: `1px solid ${sar.status === "FILED" ? "#2ecc71" : "#f39c12"}`,
                        }}
                      />
                    </TableCell>
                    <TableCell sx={{ color: "text.primary" }}>{sar.suspiciousActivityType}</TableCell>
                    <TableCell sx={{ color: "text.secondary" }}>
                      {new Date(sar.createdAt).toLocaleDateString()}
                    </TableCell>
                    <TableCell>
                      <Chip label="View" size="small" sx={{ cursor: "pointer", color: "#a93226" }} />
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={5} align="center" sx={{ color: "text.disabled", py: 4 }}>
                    No transaction-related SAR reports found
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>
    </Box>
  );
}

