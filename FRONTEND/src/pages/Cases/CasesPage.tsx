import { Routes, Route, Navigate } from "react-router-dom";
import TabNavigation from "../../components/Common/TabNavigation";
import GlassCard from "../../components/Common/GlassCard";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import CasesAllCases from "./CasesAllCases";
import CasesQueues from "./CasesQueues";
import CasesTimeline from "./CasesTimeline";
import CasesNetworkGraph from "./CasesNetworkGraph";

export default function CasesPage() {
  const tabs = [
    { label: "ALL CASES", value: "all", path: "/cases/all" },
    { label: "QUEUES", value: "queues", path: "/cases/queues" },
    { label: "TIMELINE", value: "timeline", path: "/cases/timeline" },
    { label: "NETWORK GRAPH", value: "network", path: "/cases/network" },
  ];

  return (
    <HokekaPageShell title="Cases" subtitle="Manage investigation workflows and queues" noCard>
      <GlassCard padding="md">
        <TabNavigation tabs={tabs} />
        <Routes>
          <Route path="/" element={<Navigate to="/cases/all" replace />} />
          <Route path="/all" element={<CasesAllCases />} />
          <Route path="/queues" element={<CasesQueues />} />
          <Route path="/timeline" element={<CasesTimeline />} />
          <Route path="/network" element={<CasesNetworkGraph />} />
        </Routes>
      </GlassCard>
    </HokekaPageShell>
  );
}
