import { Box, Typography } from "@mui/material";
import { Routes, Route, Navigate } from "react-router-dom";
import TabNavigation from "../../components/Common/TabNavigation";
import TransactionMonitoringLive from "./TransactionMonitoringLive";
import TransactionMonitoringAnalytics from "./TransactionMonitoringAnalytics";
import TransactionMonitoringSars from "./TransactionMonitoringSars";
import TransactionMonitoringReports from "./TransactionMonitoringReports";

export default function TransactionMonitoringPage() {
  const tabs = [
    { label: "LIVE MONITORING", value: "live", path: "/transaction-monitoring/live" },
    { label: "ANALYTICS", value: "analytics", path: "/transaction-monitoring/analytics" },
    { label: "SARS", value: "sars", path: "/transaction-monitoring/sars" },
    { label: "REPORTS", value: "reports", path: "/transaction-monitoring/reports" },
  ];

  return (
    <Box>
      <Typography variant="h4" sx={{ color: "text.primary", mb: 2, fontWeight: 600 }}>
        Transaction Monitoring
      </Typography>

      <TabNavigation tabs={tabs} />

      <Routes>
        <Route path="/" element={<Navigate to="/transaction-monitoring/live" replace />} />
        <Route path="/live" element={<TransactionMonitoringLive />} />
        <Route path="/analytics" element={<TransactionMonitoringAnalytics />} />
        <Route path="/sars" element={<TransactionMonitoringSars />} />
        <Route path="/reports" element={<TransactionMonitoringReports />} />
      </Routes>
    </Box>
  );
}

