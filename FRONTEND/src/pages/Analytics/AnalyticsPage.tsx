import { Box, Typography, Alert, Paper } from "@mui/material";
import { Routes, Route, Navigate } from "react-router-dom";
import TabNavigation from "../../components/Common/TabNavigation";

// Grafana base URL - should be configured via environment variable
const GRAFANA_BASE_URL = import.meta.env.VITE_GRAFANA_URL || "http://localhost:3000";

interface GrafanaDashboardProps {
    dashboardId: string;
    title: string;
}

function GrafanaDashboard({ dashboardId, title }: GrafanaDashboardProps) {
    const iframeUrl = `${GRAFANA_BASE_URL}/d/${dashboardId}?orgId=1&kiosk&theme=light`;

    return (
        <Box>
            <Alert severity="info" sx={{ mb: 2 }}>
                <strong>{title}</strong> - Real-time metrics from Prometheus. Use the time range selector in the top-right to adjust the view.
            </Alert>
            <Paper
                sx={{
                    backgroundColor: "background.paper",
                    border: "1px solid rgba(0,0,0,0.1)",
                    borderRadius: 2,
                    overflow: "hidden",
                    height: "calc(100vh - 280px)",
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

export default function AnalyticsPage() {
    const tabs = [
        { label: "TRANSACTION OVERVIEW", value: "transactions", path: "/analytics/transactions" },
        { label: "AML RISK", value: "aml-risk", path: "/analytics/aml-risk" },
        { label: "FRAUD DETECTION", value: "fraud", path: "/analytics/fraud" },
        { label: "COMPLIANCE", value: "compliance", path: "/analytics/compliance" },
        { label: "MODEL PERFORMANCE", value: "models", path: "/analytics/models" },
        { label: "SCREENING", value: "screening", path: "/analytics/screening" },
        { label: "SYSTEM PERFORMANCE", value: "system", path: "/analytics/system" },
        { label: "INFRASTRUCTURE", value: "infrastructure", path: "/analytics/infrastructure" },
        { label: "THREAD POOLS", value: "threads", path: "/analytics/threads" },
        { label: "CIRCUIT BREAKER", value: "circuit-breaker", path: "/analytics/circuit-breaker" },
    ];

    return (
        <Box>
            <Box sx={{ mb: 3 }}>
                <Typography variant="h6" sx={{ color: "text.primary", fontWeight: 600, mb: 1 }}>
                    Analytics & Monitoring
                </Typography>
                <Typography variant="body2" sx={{ color: "text.secondary" }}>
                    Real-time AML metrics and system performance dashboards powered by Grafana
                </Typography>
            </Box>

            <TabNavigation tabs={tabs} />

            <Routes>
                <Route path="/" element={<Navigate to="/analytics/transactions" replace />} />
                <Route
                    path="/transactions"
                    element={<GrafanaDashboard dashboardId="transaction-overview" title="Transaction Overview" />}
                />
                <Route
                    path="/aml-risk"
                    element={<GrafanaDashboard dashboardId="aml-risk" title="AML Risk Assessment" />}
                />
                <Route
                    path="/fraud"
                    element={<GrafanaDashboard dashboardId="fraud-detection" title="Fraud Detection" />}
                />
                <Route
                    path="/compliance"
                    element={<GrafanaDashboard dashboardId="compliance" title="Compliance Tracking" />}
                />
                <Route
                    path="/models"
                    element={<GrafanaDashboard dashboardId="model-performance" title="Model Performance" />}
                />
                <Route
                    path="/screening"
                    element={<GrafanaDashboard dashboardId="screening" title="Screening Operations" />}
                />
                <Route
                    path="/system"
                    element={<GrafanaDashboard dashboardId="system-performance" title="System Performance" />}
                />
                <Route
                    path="/infrastructure"
                    element={<GrafanaDashboard dashboardId="infrastructure-resources" title="Infrastructure Resources" />}
                />
                <Route
                    path="/threads"
                    element={<GrafanaDashboard dashboardId="thread-pools-throughput" title="Thread Pools & Throughput" />}
                />
                <Route
                    path="/circuit-breaker"
                    element={<GrafanaDashboard dashboardId="circuit-breaker-resilience" title="Circuit Breaker & Resilience" />}
                />
            </Routes>
        </Box>
    );
}

