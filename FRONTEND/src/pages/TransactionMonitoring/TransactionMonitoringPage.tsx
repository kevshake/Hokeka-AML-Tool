import { Routes, Route, Navigate, useLocation, useNavigate } from "react-router-dom";
import GlassCard from "../../components/Common/GlassCard";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import SettingsTabBar from "../../components/Settings/SettingsTabBar";
import TransactionMonitoringLive from "./TransactionMonitoringLive";
import TransactionMonitoringAnalytics from "./TransactionMonitoringAnalytics";
import TransactionMonitoringSars from "./TransactionMonitoringSars";
import TransactionMonitoringReports from "./TransactionMonitoringReports";

const MONITORING_TABS = [
  { id: "live", label: "Live Monitoring" },
  { id: "analytics", label: "Analytics" },
  { id: "sars", label: "SARs" },
  { id: "reports", label: "Reports" },
];

export default function TransactionMonitoringPage() {
  const location = useLocation();
  const navigate = useNavigate();

  const activeIndex = MONITORING_TABS.findIndex((tab) =>
    location.pathname.includes(`/transaction-monitoring/${tab.id}`),
  );
  const tabIndex = activeIndex >= 0 ? activeIndex : 0;

  return (
    <HokekaPageShell
      title="Live Monitoring"
      subtitle="Real-time transaction feed and velocity alerts"
      noCard
    >
      <GlassCard padding="md" glowVariant="teal" static>
        <SettingsTabBar
          tabs={MONITORING_TABS}
          activeIndex={tabIndex}
          onChange={(index) => navigate(`/transaction-monitoring/${MONITORING_TABS[index].id}`)}
          ariaLabel="Transaction monitoring sections"
        />
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
