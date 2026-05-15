import { Box, Typography, Grid, Card, CardContent, Paper } from "@mui/material";
import { useTransactionStats } from "../../features/api/queries";

export default function TransactionMonitoringReports() {
  const { data: stats, isLoading } = useTransactionStats();

  const totalCount      = stats?.totalCount      ?? 0;
  const approvedCount   = stats?.approvedCount   ?? 0;
  const declinedCount   = stats?.declinedCount   ?? 0;
  const manualCount     = stats?.manualReviewCount ?? 0;
  const highRiskCount   = stats?.highRiskCount   ?? 0;
  const mediumRiskCount = stats?.mediumRiskCount ?? 0;
  const lowRiskCount    = stats?.lowRiskCount    ?? 0;
  const totalAmountCents   = stats?.totalAmountCents   ?? 0;
  const averageAmountCents = stats?.averageAmountCents ?? 0;
  const fraudAlertCount    = stats?.fraudAlertCount    ?? 0;

  const declineRate = totalCount > 0 ? ((declinedCount / totalCount) * 100).toFixed(2) : "0.00";
  const manualRate  = totalCount > 0 ? ((manualCount  / totalCount) * 100).toFixed(2) : "0.00";

  return (
    <Box>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 3 }}>
        <Typography variant="h6" sx={{ color: "text.primary" }}>
          Transaction Monitoring Reports
        </Typography>
      </Box>

      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <CardContent>
              <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
                Total Transactions
              </Typography>
              <Typography variant="h6" sx={{ color: "text.primary" }}>
                {totalCount.toLocaleString()}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <CardContent>
              <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
                Approved
              </Typography>
              <Typography variant="h6" sx={{ color: "#27ae60" }}>
                {approvedCount.toLocaleString()}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <CardContent>
              <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
                Declined
              </Typography>
              <Typography variant="h6" sx={{ color: "#e74c3c" }}>
                {declinedCount.toLocaleString()}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <CardContent>
              <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
                Manual Review
              </Typography>
              <Typography variant="h6" sx={{ color: "#f39c12" }}>
                {manualCount.toLocaleString()}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <CardContent>
              <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
                Total Volume
              </Typography>
              <Typography variant="h6" sx={{ color: "text.primary" }}>
                ${(totalAmountCents / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <CardContent>
              <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
                Avg Transaction
              </Typography>
              <Typography variant="h6" sx={{ color: "text.primary" }}>
                ${(averageAmountCents / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <CardContent>
              <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
                High-Risk Transactions
              </Typography>
              <Typography variant="h6" sx={{ color: "#e74c3c" }}>
                {highRiskCount.toLocaleString()}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <CardContent>
              <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
                Fraud Alerts (Critical)
              </Typography>
              <Typography variant="h6" sx={{ color: "#c0392b" }}>
                {fraudAlertCount.toLocaleString()}
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
          <Grid item xs={12} md={4}>
            <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
              Decline Rate
            </Typography>
            <Typography variant="h6" sx={{ color: "text.primary" }}>
              {declineRate}%
            </Typography>
          </Grid>
          <Grid item xs={12} md={4}>
            <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
              Manual Review Rate
            </Typography>
            <Typography variant="h6" sx={{ color: "text.primary" }}>
              {manualRate}%
            </Typography>
          </Grid>
          <Grid item xs={12} md={4}>
            <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
              Risk Breakdown (H / M / L)
            </Typography>
            <Typography variant="h6" sx={{ color: "text.primary" }}>
              {highRiskCount.toLocaleString()} / {mediumRiskCount.toLocaleString()} / {lowRiskCount.toLocaleString()}
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

