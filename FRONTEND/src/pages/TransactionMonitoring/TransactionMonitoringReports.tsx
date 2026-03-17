import { Box, Typography, Grid, Card, CardContent, Paper, Button } from "@mui/material";
import { useTransactions } from "../../features/api/queries";
import { format } from "date-fns";

export default function TransactionMonitoringReports() {
  // Load first page with large size for statistics (up to 1000 records)
  // Note: Stats are calculated from paginated data, may not reflect all transactions
  const { data: transactions, isLoading } = useTransactions({ page: 0, size: 1000 });

  const totalTransactions = transactions?.totalElements || transactions?.content?.length || 0;
  const transactionList = transactions?.content || [];
  const blockedTransactions = transactionList.filter((t) => t.decision === "BLOCK").length;
  const heldTransactions = transactionList.filter((t) => t.decision === "HOLD").length;
  const totalAmount = transactionList.reduce((sum, t) => sum + (t.amountCents || 0), 0);
  const blockedAmount = transactionList
    .filter((t) => t.decision === "BLOCK")
    .reduce((sum, t) => sum + (t.amountCents || 0), 0);

  const handleExport = () => {
    // TODO: Implement export functionality
    alert("Export functionality will be implemented");
  };

  return (
    <Box>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 3 }}>
        <Typography variant="h6" sx={{ color: "text.primary" }}>
          Transaction Monitoring Reports
        </Typography>
        <Button
          variant="contained"
          onClick={handleExport}
          sx={{ backgroundColor: "#a93226", "&:hover": { backgroundColor: "#922b21" } }}
        >
          Export Report
        </Button>
      </Box>

      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <CardContent>
              <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
                Total Transactions
              </Typography>
              <Typography variant="h5" sx={{ color: "text.primary" }}>
                {totalTransactions.toLocaleString()}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <CardContent>
              <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
                Blocked Transactions
              </Typography>
              <Typography variant="h5" sx={{ color: "#e74c3c" }}>
                {blockedTransactions.toLocaleString()}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <CardContent>
              <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
                Total Amount
              </Typography>
              <Typography variant="h5" sx={{ color: "text.primary" }}>
                ${(totalAmount / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <CardContent>
              <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
                Blocked Amount
              </Typography>
              <Typography variant="h5" sx={{ color: "#e74c3c" }}>
                ${(blockedAmount / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Paper sx={{ p: 3, backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
        <Typography variant="h6" sx={{ color: "text.primary", mb: 2 }}>
          Summary Statistics
        </Typography>
        <Grid container spacing={2}>
          <Grid item xs={12} md={6}>
            <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
              Block Rate
            </Typography>
            <Typography variant="h5" sx={{ color: "text.primary" }}>
              {totalTransactions > 0 ? ((blockedTransactions / totalTransactions) * 100).toFixed(2) : 0}%
            </Typography>
          </Grid>
          <Grid item xs={12} md={6}>
            <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
              Hold Rate
            </Typography>
            <Typography variant="h5" sx={{ color: "text.primary" }}>
              {totalTransactions > 0 ? ((heldTransactions / totalTransactions) * 100).toFixed(2) : 0}%
            </Typography>
          </Grid>
        </Grid>
      </Paper>

      {isLoading && (
        <Typography sx={{ color: "text.disabled", mt: 2 }}>Loading report data...</Typography>
      )}
    </Box>
  );
}

