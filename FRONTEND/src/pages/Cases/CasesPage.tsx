import { Box, Typography } from "@mui/material";
import { Routes, Route, Navigate } from "react-router-dom";
import TabNavigation from "../../components/Common/TabNavigation";
import CasesAllCases from "./CasesAllCases";

export default function CasesPage() {
  const tabs = [
    { label: "ALL CASES", value: "all", path: "/cases/all" },
    { label: "TIMELINE", value: "timeline", path: "/cases/timeline" },
    { label: "NETWORK GRAPH", value: "network", path: "/cases/network" },
    { label: "QUEUES", value: "queues", path: "/cases/queues" },
  ];

  return (
    <Box>
      <Typography variant="h6" sx={{ color: "text.primary", mb: 0.5, fontWeight: 600 }}>
        Cases
      </Typography>

      <TabNavigation tabs={tabs} />

      <Routes>
        <Route path="/" element={<Navigate to="/cases/all" replace />} />
        <Route path="/all" element={<CasesAllCases />} />
        <Route path="/timeline" element={<Box sx={{ p: 2 }}><Typography>Timeline View - Coming Soon</Typography></Box>} />
        <Route path="/network" element={<Box sx={{ p: 2 }}><Typography>Network Graph - Coming Soon</Typography></Box>} />
        <Route path="/queues" element={<Box sx={{ p: 2 }}><Typography>Queues - Coming Soon</Typography></Box>} />
      </Routes>
    </Box>
  );
}
