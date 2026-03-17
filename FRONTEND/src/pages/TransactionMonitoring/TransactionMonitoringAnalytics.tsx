import { Box, Typography, Grid, Card, CardContent, Paper } from "@mui/material";
import {
  useMonitoringRiskDistribution,
  useMonitoringRiskIndicators,
} from "../../features/api/queries";
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  ArcElement,
  Title,
  Tooltip,
  Legend,
} from "chart.js";
import { Bar, Doughnut } from "react-chartjs-2";

ChartJS.register(CategoryScale, LinearScale, BarElement, ArcElement, Title, Tooltip, Legend);

export default function TransactionMonitoringAnalytics() {
  const { data: riskDistribution } = useMonitoringRiskDistribution();
  const { data: riskIndicators } = useMonitoringRiskIndicators();

  const riskChartData = riskDistribution
    ? {
        labels: Object.keys(riskDistribution),
        datasets: [
          {
            label: "Risk Distribution",
            data: Object.values(riskDistribution),
            backgroundColor: ["#2ecc71", "#f39c12", "#e74c3c"],
            borderColor: ["#27ae60", "#e67e22", "#c0392b"],
            borderWidth: 2,
          },
        ],
      }
    : null;

  return (
    <Box>
      <Typography variant="h6" sx={{ color: "text.primary", mb: 3 }}>
        Transaction Analytics
      </Typography>

      <Grid container spacing={3} sx={{ mb: 3 }}>
        {riskIndicators && Array.isArray(riskIndicators) && riskIndicators.length > 0 && (
          <>
            {riskIndicators.slice(0, 4).map((indicator: any, idx: number) => (
              <Grid item xs={12} sm={6} md={3} key={idx}>
                <Card sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
                  <CardContent>
                    <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
                      {indicator.name || indicator.type || "Risk Indicator"}
                    </Typography>
                    <Typography variant="h5" sx={{ color: "text.primary" }}>
                      {indicator.value || indicator.count || 0}
                    </Typography>
                    {indicator.percentage && (
                      <Typography variant="caption" sx={{ color: "text.disabled" }}>
                        {indicator.percentage}%
                      </Typography>
                    )}
                  </CardContent>
                </Card>
              </Grid>
            ))}
          </>
        )}
      </Grid>

      <Grid container spacing={3}>
        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3, backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <Typography variant="h6" sx={{ color: "text.primary", mb: 2 }}>
              Risk Distribution
            </Typography>
            {riskChartData ? (
              <Box sx={{ height: 300 }}>
                <Doughnut
                  data={riskChartData}
                  options={{
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                      legend: { position: "bottom", labels: { color: "text.primary", padding: 15 } },
                    },
                  }}
                />
              </Box>
            ) : (
              <Box sx={{ height: 300, display: "flex", alignItems: "center", justifyContent: "center", color: "text.disabled" }}>
                No risk distribution data available
              </Box>
            )}
          </Paper>
        </Grid>
        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3, backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <Typography variant="h6" sx={{ color: "text.primary", mb: 2 }}>
              Top Risk Indicators
            </Typography>
            {riskIndicators && Array.isArray(riskIndicators) && riskIndicators.length > 0 ? (
              <Box>
                {riskIndicators.map((indicator: any, idx: number) => (
                  <Box
                    key={idx}
                    sx={{
                      p: 2,
                      mb: 0.5,
                      backgroundColor: "background.paper",
                      borderRadius: 1,
                      border: "1px solid rgba(0,0,0,0.1)",
                    }}
                  >
                    <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                      <Typography variant="body1" sx={{ color: "text.primary" }}>
                        {indicator.name || indicator.type || "Indicator"}
                      </Typography>
                      <Typography variant="h6" sx={{ color: "#a93226" }}>
                        {indicator.value || indicator.count || 0}
                      </Typography>
                    </Box>
                  </Box>
                ))}
              </Box>
            ) : (
              <Typography sx={{ color: "text.disabled" }}>No risk indicators available</Typography>
            )}
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
}

