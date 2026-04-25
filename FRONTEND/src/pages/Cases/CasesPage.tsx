import { Box, Typography, Paper } from "@mui/material";
import { Routes, Route, Navigate } from "react-router-dom";
import TabNavigation from "../../components/Common/TabNavigation";
import CasesAllCases from "./CasesAllCases";
import CasesQueues from "./CasesQueues";
import { Timeline as TimelineIcon, AccountTree as NetworkIcon } from "@mui/icons-material";

function ComingSoonPlaceholder({ icon, title, description }: { icon: React.ReactNode; title: string; description: string }) {
  return (
    <Paper
      sx={{
        p: 6, textAlign: "center",
        border: "1px dashed rgba(0,0,0,0.15)",
        backgroundColor: "background.paper",
        borderRadius: 2,
      }}
    >
      <Box sx={{ color: "text.disabled", mb: 2, "& .MuiSvgIcon-root": { fontSize: 48 } }}>
        {icon}
      </Box>
      <Typography variant="h6" sx={{ color: "text.secondary", mb: 1 }}>
        {title}
      </Typography>
      <Typography variant="body2" sx={{ color: "text.disabled" }}>
        {description}
      </Typography>
    </Paper>
  );
}

export default function CasesPage() {
  const tabs = [
    { label: "ALL CASES", value: "all", path: "/cases/all" },
    { label: "QUEUES", value: "queues", path: "/cases/queues" },
    { label: "TIMELINE", value: "timeline", path: "/cases/timeline" },
    { label: "NETWORK GRAPH", value: "network", path: "/cases/network" },
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
        <Route path="/queues" element={<CasesQueues />} />
        <Route
          path="/timeline"
          element={
            <ComingSoonPlaceholder
              icon={<TimelineIcon />}
              title="Timeline View"
              description="A chronological view of case events and status changes across the investigation lifecycle."
            />
          }
        />
        <Route
          path="/network"
          element={
            <ComingSoonPlaceholder
              icon={<NetworkIcon />}
              title="Network Graph"
              description="Entity relationship graph showing connections between merchants, transactions, and linked cases."
            />
          }
        />
      </Routes>
    </Box>
  );
}
