import { Box, Paper, Typography, Grid, Card, CardContent, Button } from "@mui/material";
import { useDashboardStats } from "../../features/api/queries";
import { Download as DownloadIcon } from "@mui/icons-material";

export default function ReportsPage() {
  const { data: stats, isLoading } = useDashboardStats();

  const handleExport = (reportType: string) => {
    // TODO: Implement export functionality
    alert(`Exporting ${reportType} report...`);
  };

  return (
    <Box>
      <Typography variant="h5" sx={{ color: "text.primary", mb: 2, fontWeight: 600 }}>
        Reports
      </Typography>

      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid item xs={12} md={4}>
          <Card sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <CardContent>
              <Typography variant="h6" sx={{ color: "text.primary", mb: 1 }}>
                Case Summary Report
              </Typography>
              <Typography variant="body2" sx={{ color: "text.secondary", mb: 2 }}>
                Summary of all compliance cases by status
              </Typography>
              <Box sx={{ mb: 2 }}>
                {stats?.casesByStatus &&
                  Object.entries(stats.casesByStatus).map(([status, count]) => (
                    <Box key={status} sx={{ display: "flex", justifyContent: "space-between", mb: 1 }}>
                      <Typography variant="body2" sx={{ color: "text.primary" }}>
                        {status}:
                      </Typography>
                      <Typography variant="body2" sx={{ color: "text.primary", fontWeight: 600 }}>
                        {String(count)}
                      </Typography>
                    </Box>
                  ))}
              </Box>
              <Button
                variant="outlined"
                startIcon={<DownloadIcon />}
                onClick={() => handleExport("cases")}
                sx={{ borderColor: "#a93226", color: "#a93226", "&:hover": { borderColor: "#922b21" } }}
              >
                Export
              </Button>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={4}>
          <Card sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <CardContent>
              <Typography variant="h6" sx={{ color: "text.primary", mb: 1 }}>
                SAR Summary Report
              </Typography>
              <Typography variant="body2" sx={{ color: "text.secondary", mb: 2 }}>
                Summary of all SAR reports by status
              </Typography>
              <Box sx={{ mb: 2 }}>
                {stats?.sarsByStatus &&
                  Object.entries(stats.sarsByStatus).map(([status, count]) => (
                    <Box key={status} sx={{ display: "flex", justifyContent: "space-between", mb: 1 }}>
                      <Typography variant="body2" sx={{ color: "text.primary" }}>
                        {status}:
                      </Typography>
                      <Typography variant="body2" sx={{ color: "text.primary", fontWeight: 600 }}>
                        {String(count)}
                      </Typography>
                    </Box>
                  ))}
              </Box>
              <Button
                variant="outlined"
                startIcon={<DownloadIcon />}
                onClick={() => handleExport("sars")}
                sx={{ borderColor: "#a93226", color: "#a93226", "&:hover": { borderColor: "#922b21" } }}
              >
                Export
              </Button>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={4}>
          <Card sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <CardContent>
              <Typography variant="h6" sx={{ color: "text.primary", mb: 1 }}>
                Audit Activity Report
              </Typography>
              <Typography variant="body2" sx={{ color: "text.secondary", mb: 2 }}>
                Audit events in the last 24 hours
              </Typography>
              <Box sx={{ mb: 2 }}>
                <Box sx={{ display: "flex", justifyContent: "space-between", mb: 1 }}>
                  <Typography variant="body2" sx={{ color: "text.primary" }}>
                    Last 24h:
                  </Typography>
                  <Typography variant="body2" sx={{ color: "text.primary", fontWeight: 600 }}>
                    {stats?.auditLast24h || 0}
                  </Typography>
                </Box>
              </Box>
              <Button
                variant="outlined"
                startIcon={<DownloadIcon />}
                onClick={() => handleExport("audit")}
                sx={{ borderColor: "#a93226", color: "#a93226", "&:hover": { borderColor: "#922b21" } }}
              >
                Export
              </Button>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Paper sx={{ p: 2, backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
        <Typography variant="h6" sx={{ color: "text.primary", mb: 2 }}>
          Daily Trends
        </Typography>
        <Grid container spacing={2}>
          <Grid item xs={12} md={6}>
            <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
              Cases (Last 7 Days)
            </Typography>
            {stats?.casesLast7d ? (
              <Box>
                {Object.entries(stats.casesLast7d).map(([date, count]) => (
                  <Box key={date} sx={{ display: "flex", justifyContent: "space-between", mb: 0.5 }}>
                    <Typography variant="caption" sx={{ color: "text.primary" }}>
                      {date}:
                    </Typography>
                    <Typography variant="caption" sx={{ color: "text.primary" }}>
                      {String(count)}
                    </Typography>
                  </Box>
                ))}
              </Box>
            ) : (
              <Typography variant="body2" sx={{ color: "text.disabled" }}>
                No data available
              </Typography>
            )}
          </Grid>
          <Grid item xs={12} md={6}>
            <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
              SARs (Last 7 Days)
            </Typography>
            {stats?.sarsLast7d ? (
              <Box>
                {Object.entries(stats.sarsLast7d).map(([date, count]) => (
                  <Box key={date} sx={{ display: "flex", justifyContent: "space-between", mb: 0.5 }}>
                    <Typography variant="caption" sx={{ color: "text.primary" }}>
                      {date}:
                    </Typography>
                    <Typography variant="caption" sx={{ color: "text.primary" }}>
                      {String(count)}
                    </Typography>
                  </Box>
                ))}
              </Box>
            ) : (
              <Typography variant="body2" sx={{ color: "text.disabled" }}>
                No data available
              </Typography>
            )}
          </Grid>
        </Grid>
      </Paper>

      {isLoading && (
        <Typography sx={{ color: "text.disabled", mt: 2 }}>Loading report data...</Typography>
      )}
    </Box>
  );
}

