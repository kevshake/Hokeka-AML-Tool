import { Routes, Route, Navigate } from "react-router-dom";
import TabNavigation from "../../components/Common/TabNavigation";
import GlassCard from "../../components/Common/GlassCard";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
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
    <HokekaPageShell
      title="Live Monitoring"
      subtitle="Real-time transaction feed and velocity alerts"
      noCard
    >
      <GlassCard padding="md">
        <TabNavigation tabs={tabs} />
        <Routes>
          <Route path="/" element={<Navigate to="/transaction-monitoring/live" replace />} />
          <Route path="/live" element={<TransactionMonitoringLive />} />
          <Route path="/analytics" element={<TransactionMonitoringAnalytics />} />
          <Route path="/sars" element={<TransactionMonitoringSars />} />
          <Route path="/reports" element={<TransactionMonitoringReports />} />
        </Routes>
      </GlassCard>
    </HokekaPageShell>
  );
}
