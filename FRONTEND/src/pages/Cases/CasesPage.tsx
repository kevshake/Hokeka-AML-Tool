import { Routes, Route, Navigate, useLocation, useNavigate } from "react-router-dom";
import GlassCard from "../../components/Common/GlassCard";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import SettingsTabBar from "../../components/Settings/SettingsTabBar";
import CasesAllCases from "./CasesAllCases";
import CasesQueues from "./CasesQueues";
import CasesTimeline from "./CasesTimeline";
import CasesNetworkGraph from "./CasesNetworkGraph";

const CASE_TABS = [
  { id: "all", label: "All Cases" },
  { id: "queues", label: "Queues" },
  { id: "timeline", label: "Timeline" },
  { id: "network", label: "Network Graph" },
];

export default function CasesPage() {
  const location = useLocation();
  const navigate = useNavigate();

  const activeIndex = CASE_TABS.findIndex((tab) => location.pathname.includes(`/cases/${tab.id}`));
  const tabIndex = activeIndex >= 0 ? activeIndex : 0;

  return (
    <HokekaPageShell title="Cases" subtitle="Manage investigation workflows and queues" noCard>
      <GlassCard padding="md" glowVariant="purple" static>
        <SettingsTabBar
          tabs={CASE_TABS}
          activeIndex={tabIndex}
          onChange={(index) => navigate(`/cases/${CASE_TABS[index].id}${location.search}`)}
          ariaLabel="Case management sections"
        />
        <Routes>
          <Route path="/" element={<Navigate to={`/cases/all${location.search}`} replace />} />
          <Route path="/all" element={<CasesAllCases />} />
          <Route path="/queues" element={<CasesQueues />} />
          <Route path="/timeline" element={<CasesTimeline />} />
          <Route path="/network" element={<CasesNetworkGraph />} />
        </Routes>
      </GlassCard>
    </HokekaPageShell>
  );
}
