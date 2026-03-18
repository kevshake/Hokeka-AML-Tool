import { useState } from "react";
import { Box, Paper, Typography, Select, MenuItem, FormControl, InputLabel, Grid } from "@mui/material";
import { useRiskHeatmap, useRiskTrends } from "../../features/api/queries";
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
} from "chart.js";
import { Line } from "react-chartjs-2";

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend);

export default function RiskAnalyticsPage() {
  const [period, setPeriod] = useState<number>(30);
  const [heatmapType, setHeatmapType] = useState<"customer" | "merchant">("customer");

  const { data: heatmap } = useRiskHeatmap(heatmapType);
  const { data: trends } = useRiskTrends(period);

  const trendsChartData = trends
    ? {
        labels: trends.labels || Object.keys(trends),
        datasets: [
          {
            label: "Risk Trend",
            data: trends.data || Object.values(trends),
            borderColor: "#a93226",
            backgroundColor: "rgba(169, 50, 38, 0.1)",
            tension: 0.4,
          },
        ],
      }
    : null;

  return (
    <Box>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 2 }}>
        <Typography variant="h6" sx={{ color: "text.primary", fontWeight: 600 }}>
          Risk Analytics
        </Typography>
        <Box sx={{ display: "flex", gap: 2 }}>
          <FormControl size="small" sx={{ minWidth: 150 }}>
            <InputLabel sx={{ color: "text.secondary" }}>Period</InputLabel>
            <Select
              value={period}
              onChange={(e) => setPeriod(Number(e.target.value))}
              label="Period"
              sx={{ color: "text.primary" }}
            >
              <MenuItem value={7}>7 days</MenuItem>
              <MenuItem value={30}>30 days</MenuItem>
              <MenuItem value={90}>90 days</MenuItem>
              <MenuItem value={180}>180 days</MenuItem>
            </Select>
          </FormControl>
          <FormControl size="small" sx={{ minWidth: 150 }}>
            <InputLabel sx={{ color: "text.secondary" }}>Heatmap Type</InputLabel>
            <Select
              value={heatmapType}
              onChange={(e) => setHeatmapType(e.target.value as "customer" | "merchant")}
              label="Heatmap Type"
              sx={{ color: "text.primary" }}
            >
              <MenuItem value="customer">Customer</MenuItem>
              <MenuItem value="merchant">Merchant</MenuItem>
            </Select>
          </FormControl>
        </Box>
      </Box>

      <Grid container spacing={2}>
        <Grid item xs={12} md={8}>
          <Paper sx={{ p: 2, backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <Typography variant="h6" sx={{ color: "text.primary", mb: 2 }}>
              Risk Heatmap - {heatmapType.charAt(0).toUpperCase() + heatmapType.slice(1)}
            </Typography>
            {heatmap ? (
              <Box sx={{ p: 2, backgroundColor: "background.paper", borderRadius: 1 }}>
                <Grid container spacing={1}>
                  {Object.entries(heatmap).slice(0, 20).map(([key, value]: [string, any]) => (
                    <Grid item xs={6} sm={4} md={3} key={key}>
                      <Box
                        sx={{
                          p: 2,
                          backgroundColor:
                            typeof value === "number" && value > 50
                              ? "#e74c3c20"
                              : typeof value === "number" && value > 25
                              ? "#f39c1220"
                              : "#2ecc7120",
                          border: `1px solid ${
                            typeof value === "number" && value > 50
                              ? "#e74c3c"
                              : typeof value === "number" && value > 25
                              ? "#f39c12"
                              : "#2ecc71"
                          }`,
                          borderRadius: 1,
                          textAlign: "center",
                        }}
                      >
                        <Typography variant="caption" sx={{ color: "text.secondary", display: "block" }}>
                          {key}
                        </Typography>
                        <Typography variant="h6" sx={{ color: "text.primary" }}>
                          {typeof value === "number" ? value : String(value)}
                        </Typography>
                      </Box>
                    </Grid>
                  ))}
                </Grid>
              </Box>
            ) : (
              <Box sx={{ height: 400, display: "flex", alignItems: "center", justifyContent: "center", color: "text.disabled" }}>
                Loading heatmap data...
              </Box>
            )}
          </Paper>
        </Grid>
        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 2, backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <Typography variant="h6" sx={{ color: "text.primary", mb: 2 }}>
              Risk Trends ({period} days)
            </Typography>
            {trendsChartData ? (
              <Box sx={{ height: 400 }}>
                <Line
                  data={trendsChartData}
                  options={{
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                      legend: { labels: { color: "text.primary" } },
                    },
                    scales: {
                      x: { ticks: { color: "text.primary" }, grid: { color: "rgba(255,255,255,0.1)" } },
                      y: { ticks: { color: "text.primary" }, grid: { color: "rgba(255,255,255,0.1)" } },
                    },
                  }}
                />
              </Box>
            ) : (
              <Box sx={{ height: 400, display: "flex", alignItems: "center", justifyContent: "center", color: "text.disabled" }}>
                Loading trend data...
              </Box>
            )}
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
}

