import { useState } from "react";
import {
  Box,
  Typography,
  Paper,
  Grid,
  Card,
  CardContent,
  Alert,
  CircularProgress,
  Tab,
  Tabs,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableRow,
} from "@mui/material";
import {
  LineChart,
  Line,
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from "recharts";
import {
  useTransactionVolume,
  useRiskDistribution,
  useRiskTrends,
  useFraudMetrics,
  useModelMetricsLatest,
  useModelMetricsRange,
  useAlertTrends,
  useDashboardGlobalStats,
  type ModelMetricsEntry,
} from "../../features/api/queries";

// Grafana base URL — optional; when set, an extra "Advanced Analytics" tab appears
const GRAFANA_BASE_URL = import.meta.env.VITE_GRAFANA_URL as string | undefined;

const BRAND = "#8B4049";
const COLORS = {
  HIGH: "#e74c3c",
  MEDIUM: "#f39c12",
  LOW: "#2ecc71",
  approved: "#27ae60",
  declined: "#e74c3c",
  manual: "#f39c12",
};
const CHART_COLORS = [BRAND, "#3498db", "#2ecc71", "#f39c12", "#9b59b6", "#e74c3c"];

// ─── Helpers ─────────────────────────────────────────────────────────────────

function KpiCard({
  label,
  value,
  sub,
  color = BRAND,
}: {
  label: string;
  value: string | number;
  sub?: string;
  color?: string;
}) {
  return (
    <Card sx={{ height: "100%" }}>
      <CardContent sx={{ p: 3 }}>
        <Typography variant="body2" sx={{ color: "text.secondary", mb: 1, fontWeight: 500 }}>
          {label}
        </Typography>
        <Typography variant="h4" sx={{ color, fontWeight: 700, mb: 0.5 }}>
          {value}
        </Typography>
        {sub && (
          <Typography variant="caption" sx={{ color: "text.secondary" }}>
            {sub}
          </Typography>
        )}
      </CardContent>
    </Card>
  );
}

function ChartShell({
  title,
  height = 280,
  loading,
  empty,
  children,
}: {
  title: string;
  height?: number;
  loading: boolean;
  empty: boolean;
  children: React.ReactNode;
}) {
  return (
    <Paper sx={{ p: 3, borderRadius: 2, border: "1px solid rgba(0,0,0,0.08)", height: "100%" }}>
      <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>
        {title}
      </Typography>
      {loading ? (
        <Box sx={{ height, display: "flex", alignItems: "center", justifyContent: "center" }}>
          <CircularProgress size={36} sx={{ color: BRAND }} />
        </Box>
      ) : empty ? (
        <Box sx={{ height, display: "flex", alignItems: "center", justifyContent: "center" }}>
          <Typography variant="body2" sx={{ color: "text.disabled" }}>
            No data available
          </Typography>
        </Box>
      ) : (
        <Box sx={{ height }}>{children}</Box>
      )}
    </Paper>
  );
}

// ─── Tab: Transaction Overview ────────────────────────────────────────────────

function TransactionOverviewTab() {
  const { data: volumeRaw, isLoading: volLoading } = useTransactionVolume(30);
  const { data: globalStats, isLoading: statsLoading } = useDashboardGlobalStats();

  const volData: { date: string; count: number }[] = (() => {
    if (!volumeRaw) return [];
    const raw = volumeRaw as { labels?: string[]; data?: number[] };
    const labels: string[] = raw.labels ?? [];
    const data: number[] = raw.data ?? [];
    return labels.map((d, i) => ({ date: d, count: data[i] ?? 0 }));
  })();

  const totalMonth = volData.reduce((s, r) => s + r.count, 0);
  const avgPerDay = volData.length > 0 ? Math.round(totalMonth / volData.length) : 0;
  const peakDay =
    volData.length > 0
      ? volData.reduce((best, r) => (r.count > best.count ? r : best), volData[0])
      : null;

  const stats = globalStats as Record<string, unknown> | undefined;
  const decisionData = [
    { name: "Approved", value: Number(stats?.approvedTransactions ?? 0), color: COLORS.approved },
    { name: "Declined", value: Number(stats?.declinedTransactions ?? 0), color: COLORS.declined },
    { name: "Manual Review", value: Number(stats?.manualReviewTransactions ?? 0), color: COLORS.manual },
  ].filter((d) => d.value > 0);

  return (
    <Box>
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={4}>
          <KpiCard
            label="Total This Month"
            value={totalMonth.toLocaleString()}
            sub="transactions"
          />
        </Grid>
        <Grid item xs={12} sm={4}>
          <KpiCard
            label="Avg Per Day"
            value={statsLoading ? "…" : avgPerDay.toLocaleString()}
            sub="transactions / day"
            color="#3498db"
          />
        </Grid>
        <Grid item xs={12} sm={4}>
          <KpiCard
            label="Peak Day"
            value={peakDay ? peakDay.count.toLocaleString() : "—"}
            sub={peakDay?.date ?? ""}
            color="#27ae60"
          />
        </Grid>
      </Grid>

      <Grid container spacing={3}>
        <Grid item xs={12} md={8}>
          <ChartShell
            title="Daily Transaction Volume (Last 30 Days)"
            loading={volLoading}
            empty={volData.length === 0}
          >
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={volData}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(0,0,0,0.06)" />
                <XAxis dataKey="date" tick={{ fontSize: 11 }} interval="preserveStartEnd" />
                <YAxis tick={{ fontSize: 11 }} />
                <Tooltip />
                <Line
                  type="monotone"
                  dataKey="count"
                  name="Transactions"
                  stroke={BRAND}
                  strokeWidth={2}
                  dot={false}
                  activeDot={{ r: 4 }}
                />
              </LineChart>
            </ResponsiveContainer>
          </ChartShell>
        </Grid>

        <Grid item xs={12} md={4}>
          <ChartShell
            title="Decision Breakdown"
            loading={statsLoading}
            empty={decisionData.length === 0}
          >
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={decisionData}
                  cx="50%"
                  cy="50%"
                  innerRadius={60}
                  outerRadius={90}
                  dataKey="value"
                  nameKey="name"
                  label={({ name, percent }) =>
                    `${name} ${((percent ?? 0) * 100).toFixed(0)}%`
                  }
                  labelLine={false}
                >
                  {decisionData.map((entry, idx) => (
                    <Cell key={idx} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          </ChartShell>
        </Grid>
      </Grid>
    </Box>
  );
}

// ─── Tab: Risk Analytics ──────────────────────────────────────────────────────

function RiskAnalyticsTab() {
  const { data: riskDist, isLoading: distLoading } = useRiskDistribution();
  const { data: trends, isLoading: trendsLoading } = useRiskTrends(30);

  const distData: { name: string; value: number; color: string }[] = riskDist
    ? Object.entries(riskDist as Record<string, number>).map(([k, v]) => ({
        name: k,
        value: Number(v),
        color: COLORS[k as keyof typeof COLORS] ?? CHART_COLORS[0],
      }))
    : [];

  const trendData: { label: string; score: number }[] =
    trends?.labels && trends?.data
      ? trends.labels.map((l, i) => ({ label: l, score: trends.data![i] ?? 0 }))
      : [];

  return (
    <Box>
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={4}>
          <KpiCard
            label="Total Cases"
            value={trends?.totalCases?.toLocaleString() ?? "—"}
            sub="in selected window"
          />
        </Grid>
        <Grid item xs={12} sm={4}>
          <KpiCard
            label="High Risk Cases"
            value={trends?.highRiskCases?.toLocaleString() ?? "—"}
            sub="requires attention"
            color={COLORS.HIGH}
          />
        </Grid>
        <Grid item xs={12} sm={4}>
          <KpiCard
            label="Trend Direction"
            value={trends?.trendDirection ?? "—"}
            sub="last 30 days"
            color={
              trends?.trendDirection === "INCREASING"
                ? COLORS.HIGH
                : trends?.trendDirection === "DECREASING"
                ? COLORS.LOW
                : "#3498db"
            }
          />
        </Grid>
      </Grid>

      <Grid container spacing={3}>
        <Grid item xs={12} md={4}>
          <ChartShell
            title="Risk Distribution"
            loading={distLoading}
            empty={distData.length === 0}
          >
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={distData}
                  cx="50%"
                  cy="50%"
                  innerRadius={65}
                  outerRadius={95}
                  dataKey="value"
                  nameKey="name"
                >
                  {distData.map((entry, idx) => (
                    <Cell key={idx} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          </ChartShell>
        </Grid>

        <Grid item xs={12} md={8}>
          <ChartShell
            title="Risk Score Trend (Last 30 Days)"
            loading={trendsLoading}
            empty={trendData.length === 0}
          >
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={trendData}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(0,0,0,0.06)" />
                <XAxis dataKey="label" tick={{ fontSize: 11 }} interval="preserveStartEnd" />
                <YAxis tick={{ fontSize: 11 }} />
                <Tooltip />
                <Line
                  type="monotone"
                  dataKey="score"
                  name="Risk Score"
                  stroke={COLORS.HIGH}
                  strokeWidth={2}
                  dot={false}
                  activeDot={{ r: 4 }}
                />
              </LineChart>
            </ResponsiveContainer>
          </ChartShell>
        </Grid>
      </Grid>
    </Box>
  );
}

// ─── Tab: Alert Trends ────────────────────────────────────────────────────────

function AlertTrendsTab() {
  const { data: trendsRaw, isLoading } = useAlertTrends(30);

  const trends = Array.isArray(trendsRaw) ? trendsRaw : [];

  const totalAlerts = trends.reduce((s, r) => s + (r.total ?? 0), 0);
  const totalResolved = trends.reduce((s, r) => s + (r.resolved ?? 0), 0);
  const resolutionRate =
    totalAlerts > 0 ? `${((totalResolved / totalAlerts) * 100).toFixed(1)}%` : "—";

  return (
    <Box>
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={4}>
          <KpiCard label="Total Alerts (30d)" value={totalAlerts.toLocaleString()} />
        </Grid>
        <Grid item xs={12} sm={4}>
          <KpiCard
            label="Resolution Rate"
            value={resolutionRate}
            sub="resolved / total"
            color="#27ae60"
          />
        </Grid>
        <Grid item xs={12} sm={4}>
          <KpiCard
            label="Resolved Alerts"
            value={totalResolved.toLocaleString()}
            sub="last 30 days"
            color="#27ae60"
          />
        </Grid>
      </Grid>

      <Grid container spacing={3}>
        <Grid item xs={12} md={6}>
          <ChartShell
            title="Alerts Per Day"
            loading={isLoading}
            empty={trends.length === 0}
          >
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={trends}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(0,0,0,0.06)" />
                <XAxis dataKey="date" tick={{ fontSize: 11 }} interval="preserveStartEnd" />
                <YAxis tick={{ fontSize: 11 }} />
                <Tooltip />
                <Bar dataKey="total" name="Total Alerts" fill={BRAND} radius={[2, 2, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </ChartShell>
        </Grid>

        <Grid item xs={12} md={6}>
          <ChartShell
            title="Alerts by Status"
            loading={isLoading}
            empty={trends.length === 0}
          >
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={trends}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(0,0,0,0.06)" />
                <XAxis dataKey="date" tick={{ fontSize: 11 }} interval="preserveStartEnd" />
                <YAxis tick={{ fontSize: 11 }} />
                <Tooltip />
                <Legend />
                <Bar dataKey="open" name="Open" stackId="a" fill="#3498db" />
                <Bar dataKey="resolved" name="Resolved" stackId="a" fill="#27ae60" />
                <Bar dataKey="escalated" name="Escalated" stackId="a" fill={COLORS.HIGH} radius={[2, 2, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </ChartShell>
        </Grid>
      </Grid>
    </Box>
  );
}

// ─── Tab: Model Performance ───────────────────────────────────────────────────

function ModelPerformanceTab() {
  const { data: fraudMetrics, isLoading: fmLoading } = useFraudMetrics();
  const { data: latest, isLoading: latestLoading } = useModelMetricsLatest();

  // Last 12 months window
  const endDate = new Date().toISOString().slice(0, 10);
  const startDate = new Date(Date.now() - 365 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
  const { data: metricsRange, isLoading: rangeLoading } = useModelMetricsRange(startDate, endDate);

  const aucTrend = (metricsRange ?? [])
    .filter((m: ModelMetricsEntry) => m.auc !== null)
    .map((m: ModelMetricsEntry) => ({ date: m.date, auc: Number(m.auc) }));

  const anyLoading = fmLoading || latestLoading;

  return (
    <Box>
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={3}>
          <KpiCard
            label="AUC-ROC"
            value={anyLoading ? "…" : latest?.auc != null ? latest.auc.toFixed(3) : "—"}
            sub="latest model"
            color={BRAND}
          />
        </Grid>
        <Grid item xs={12} sm={3}>
          <KpiCard
            label="Precision"
            value={anyLoading ? "…" : fraudMetrics?.precision ?? "—"}
            sub="at threshold"
            color="#3498db"
          />
        </Grid>
        <Grid item xs={12} sm={3}>
          <KpiCard
            label="Recall"
            value={anyLoading ? "…" : fraudMetrics?.recall ?? "—"}
            sub="at threshold"
            color="#27ae60"
          />
        </Grid>
        <Grid item xs={12} sm={3}>
          <KpiCard
            label="F1 Score"
            value={anyLoading ? "…" : fraudMetrics?.f1 ?? "—"}
            sub="harmonic mean"
            color="#f39c12"
          />
        </Grid>
      </Grid>

      <Grid container spacing={3}>
        <Grid item xs={12} md={8}>
          <ChartShell
            title="AUC Trend (Last 12 Months)"
            loading={rangeLoading}
            empty={aucTrend.length === 0}
          >
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={aucTrend}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(0,0,0,0.06)" />
                <XAxis dataKey="date" tick={{ fontSize: 11 }} interval="preserveStartEnd" />
                <YAxis domain={[0.8, 1]} tick={{ fontSize: 11 }} />
                <Tooltip />
                <Line
                  type="monotone"
                  dataKey="auc"
                  name="AUC"
                  stroke={BRAND}
                  strokeWidth={2}
                  dot={{ r: 3, fill: BRAND }}
                  activeDot={{ r: 5 }}
                />
              </LineChart>
            </ResponsiveContainer>
          </ChartShell>
        </Grid>

        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 3, borderRadius: 2, border: "1px solid rgba(0,0,0,0.08)", height: "100%" }}>
            <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>
              Latest Model Snapshot
            </Typography>
            {latestLoading ? (
              <Box sx={{ display: "flex", justifyContent: "center", pt: 4 }}>
                <CircularProgress size={32} sx={{ color: BRAND }} />
              </Box>
            ) : latest == null ? (
              <Typography variant="body2" sx={{ color: "text.disabled" }}>
                No metrics recorded yet
              </Typography>
            ) : (
              <TableContainer>
                <Table size="small">
                  <TableBody>
                    {[
                      { label: "Date", value: latest.date },
                      { label: "AUC", value: latest.auc?.toFixed(4) ?? "—" },
                      {
                        label: "Precision@100",
                        value:
                          latest.precisionAt100 != null
                            ? `${(latest.precisionAt100 * 100).toFixed(1)}%`
                            : "—",
                      },
                      {
                        label: "Avg Latency",
                        value:
                          latest.avgLatencyMs != null ? `${latest.avgLatencyMs.toFixed(1)} ms` : "—",
                      },
                      {
                        label: "Drift Score",
                        value: latest.driftScore?.toFixed(4) ?? "—",
                      },
                    ].map((row) => (
                      <TableRow key={row.label}>
                        <TableCell sx={{ color: "text.secondary", borderBottom: "1px solid rgba(0,0,0,0.06)" }}>
                          {row.label}
                        </TableCell>
                        <TableCell sx={{ fontWeight: 600, borderBottom: "1px solid rgba(0,0,0,0.06)" }}>
                          {row.value}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
}

// ─── Grafana iframe tab (optional) ───────────────────────────────────────────

interface GrafanaDashboardProps {
  dashboardId: string;
  title: string;
}

function GrafanaDashboard({ dashboardId, title }: GrafanaDashboardProps) {
  const iframeUrl = `${GRAFANA_BASE_URL}/d/${dashboardId}?orgId=1&kiosk&theme=light`;
  return (
    <Box>
      <Alert severity="info" sx={{ mb: 2 }}>
        <strong>{title}</strong> — Real-time metrics from Prometheus. Use the time range selector in Grafana to adjust the view.
      </Alert>
      <Paper
        sx={{
          backgroundColor: "background.paper",
          border: "1px solid rgba(0,0,0,0.1)",
          borderRadius: 2,
          overflow: "hidden",
          height: "calc(100vh - 320px)",
          minHeight: "600px",
        }}
      >
        <iframe
          src={iframeUrl}
          width="100%"
          height="100%"
          frameBorder="0"
          title={title}
          style={{ display: "block" }}
        />
      </Paper>
    </Box>
  );
}

const GRAFANA_TABS = [
  { label: "Transactions", id: "transaction-overview" },
  { label: "AML Risk", id: "aml-risk" },
  { label: "Fraud Detection", id: "fraud-detection" },
  { label: "Compliance", id: "compliance" },
  { label: "Models", id: "model-performance" },
  { label: "Screening", id: "screening" },
  { label: "System", id: "system-performance" },
  { label: "Infrastructure", id: "infrastructure-resources" },
  { label: "Thread Pools", id: "thread-pools-throughput" },
  { label: "Circuit Breaker", id: "circuit-breaker-resilience" },
];

function GrafanaSection() {
  const [subTab, setSubTab] = useState(0);
  return (
    <Box>
      <Tabs
        value={subTab}
        onChange={(_, v: number) => setSubTab(v)}
        variant="scrollable"
        scrollButtons="auto"
        sx={{
          mb: 3,
          "& .MuiTab-root": { fontSize: 12, minWidth: 100 },
          "& .Mui-selected": { color: BRAND },
          "& .MuiTabs-indicator": { backgroundColor: BRAND },
        }}
      >
        {GRAFANA_TABS.map((t) => (
          <Tab key={t.id} label={t.label} />
        ))}
      </Tabs>
      <GrafanaDashboard
        dashboardId={GRAFANA_TABS[subTab].id}
        title={GRAFANA_TABS[subTab].label}
      />
    </Box>
  );
}

// ─── Root page ────────────────────────────────────────────────────────────────

const NATIVE_TABS = [
  { label: "TRANSACTION OVERVIEW" },
  { label: "RISK ANALYTICS" },
  { label: "ALERT TRENDS" },
  { label: "MODEL PERFORMANCE" },
  ...(GRAFANA_BASE_URL ? [{ label: "ADVANCED ANALYTICS (GRAFANA)" }] : []),
];

export default function AnalyticsPage() {
  const [activeTab, setActiveTab] = useState(0);

  return (
    <Box>
      <Box sx={{ mb: 3 }}>
        <Box sx={{ display: "flex", alignItems: "center", gap: 2, mb: 1 }}>
          <Typography variant="h6" sx={{ color: "text.primary", fontWeight: 600 }}>
            Analytics &amp; Monitoring
          </Typography>
          {GRAFANA_BASE_URL && (
            <Chip label="Grafana Connected" size="small" sx={{ backgroundColor: "#e8f5e9", color: "#27ae60", fontWeight: 600 }} />
          )}
        </Box>
        <Typography variant="body2" sx={{ color: "text.secondary" }}>
          Native AML metrics powered by live API data
          {GRAFANA_BASE_URL ? " — Grafana advanced dashboards also available" : ""}
        </Typography>
      </Box>

      <Tabs
        value={activeTab}
        onChange={(_, v: number) => setActiveTab(v)}
        variant="scrollable"
        scrollButtons="auto"
        sx={{
          mb: 3,
          borderBottom: "1px solid rgba(0,0,0,0.08)",
          "& .MuiTab-root": { fontSize: 12, fontWeight: 600, minWidth: 140 },
          "& .Mui-selected": { color: BRAND },
          "& .MuiTabs-indicator": { backgroundColor: BRAND },
        }}
      >
        {NATIVE_TABS.map((t) => (
          <Tab key={t.label} label={t.label} />
        ))}
      </Tabs>

      {activeTab === 0 && <TransactionOverviewTab />}
      {activeTab === 1 && <RiskAnalyticsTab />}
      {activeTab === 2 && <AlertTrendsTab />}
      {activeTab === 3 && <ModelPerformanceTab />}
      {activeTab === 4 && GRAFANA_BASE_URL && <GrafanaSection />}
    </Box>
  );
}
