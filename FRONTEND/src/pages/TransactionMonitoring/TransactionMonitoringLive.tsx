import { Box, Typography, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Paper, Chip, Grid, Card, CardContent, TablePagination, CircularProgress } from "@mui/material";
import { useState } from "react";
import {
  useMonitoringTransactions,
  useMonitoringDashboardStats,
  useMonitoringRecentActivity,
} from "../../features/api/queries";

export default function TransactionMonitoringLive() {
  const [page, setPage] = useState({ index: 0, size: 25 });
  
  const { data: transactions, isLoading: transactionsLoading } = useMonitoringTransactions({
    page: page.index,
    size: page.size,
  });
  const { data: stats, isLoading: statsLoading } = useMonitoringDashboardStats();
  const { data: recentActivity, isLoading: activityLoading } = useMonitoringRecentActivity();

  return (
    <Box>
      <Typography variant="h6" sx={{ color: "text.primary", mb: 3 }}>
        Live Transaction Monitoring
      </Typography>

      {stats && !statsLoading && (
        <Grid container spacing={2} sx={{ mb: 3 }}>
          {Object.entries(stats).map(([key, value]) => (
            <Grid item xs={12} sm={6} md={3} key={key}>
              <Card sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
                <CardContent>
                  <Typography variant="body2" sx={{ color: "text.secondary", mb: 0.5, textTransform: "capitalize" }}>
                    {key.replace(/([A-Z])/g, " $1").trim()}
                  </Typography>
                  <Typography variant="h6" sx={{ color: "text.primary" }}>
                    {typeof value === "number" ? value.toLocaleString() : String(value)}
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      )}

      <Grid container spacing={3}>
        <Grid item xs={12} md={8}>
          <Paper sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <Box sx={{ p: 2, borderBottom: "1px solid rgba(0,0,0,0.1)" }}>
              <Typography variant="h6" sx={{ color: "text.primary" }}>
                Monitored Transactions
              </Typography>
            </Box>
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ color: "text.secondary" }}>ID</TableCell>
                    <TableCell sx={{ color: "text.secondary" }}>Merchant</TableCell>
                    <TableCell sx={{ color: "text.secondary" }}>Amount</TableCell>
                    <TableCell sx={{ color: "text.secondary" }}>Decision</TableCell>
                    <TableCell sx={{ color: "text.secondary" }}>Timestamp</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {transactionsLoading ? (
                    <TableRow>
                      <TableCell colSpan={5} align="center" sx={{ color: "text.disabled", py: 4 }}>
                        <CircularProgress size={24} />
                      </TableCell>
                    </TableRow>
                  ) : transactions?.content && transactions.content.length > 0 ? (
                    transactions.content.map((txn: any, idx: number) => (
                      <TableRow key={idx} hover>
                        <TableCell sx={{ color: "text.primary", py: 2 }}>#{txn.txnId || txn.transactionId || txn.id || idx}</TableCell>
                        <TableCell sx={{ color: "text.primary", py: 2 }}>{txn.merchantId || "-"}</TableCell>
                        <TableCell sx={{ color: "text.primary", py: 2 }}>
                          {txn.amountCents ? `$${(txn.amountCents / 100).toFixed(2)}` : "-"}
                        </TableCell>
                        <TableCell sx={{ py: 2 }}>
                          <Chip
                            label={txn.decision || "ALLOW"}
                            size="small"
                            sx={{
                              backgroundColor: txn.decision === "BLOCK" ? "#e74c3c20" : "#2ecc7120",
                              color: txn.decision === "BLOCK" ? "#e74c3c" : "#2ecc71",
                              border: `1px solid ${txn.decision === "BLOCK" ? "#e74c3c" : "#2ecc71"}`,
                              fontWeight: 600,
                            }}
                          />
                        </TableCell>
                        <TableCell sx={{ color: "text.secondary", py: 2 }}>
                          {txn.txnTs ? new Date(txn.txnTs).toLocaleString() : "-"}
                        </TableCell>
                      </TableRow>
                    ))
                  ) : (
                    <TableRow>
                      <TableCell colSpan={5} align="center" sx={{ color: "text.disabled", py: 4 }}>
                        No transactions found
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
              <TablePagination
                rowsPerPageOptions={[10, 25, 50, 100]}
                component="div"
                count={transactions?.totalElements || 0}
                rowsPerPage={page.size}
                page={page.index}
                onPageChange={(_, newPage) => setPage(prev => ({ ...prev, index: newPage }))}
                onRowsPerPageChange={(e) => setPage({ index: 0, size: parseInt(e.target.value, 10) })}
              />
            </TableContainer>
          </Paper>
        </Grid>
        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 2, backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <Typography variant="h6" sx={{ color: "text.primary", mb: 2 }}>
              Recent Activity
            </Typography>
            {activityLoading ? (
              <Typography sx={{ color: "text.disabled" }}>Loading activity...</Typography>
            ) : recentActivity && Array.isArray(recentActivity) && recentActivity.length > 0 ? (
              <Box>
                {recentActivity.slice(0, 10).map((activity: any, idx: number) => (
                  <Box
                    key={idx}
                    sx={{
                      p: 1.5,
                      mb: 0.5,
                      backgroundColor: "background.paper",
                      borderRadius: 1,
                      border: "1px solid rgba(0,0,0,0.1)",
                    }}
                  >
                    <Typography variant="body2" sx={{ color: "text.primary" }}>
                      {activity.description || activity.action || "Activity"}
                    </Typography>
                    <Typography variant="caption" sx={{ color: "text.disabled" }}>
                      {activity.timestamp ? new Date(activity.timestamp).toLocaleString() : ""}
                    </Typography>
                  </Box>
                ))}
              </Box>
            ) : (
              <Typography sx={{ color: "text.disabled" }}>No recent activity</Typography>
            )}
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
}

