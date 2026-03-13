/**
 * Empty State Component
 * Displays when no reports are available
 */

import { Box, Typography, Button, Paper } from "@mui/material";
import {
  Assessment as ReportsIcon,
  SearchOff as NoResultsIcon,
  History as HistoryIcon,
  Add as AddIcon,
} from "@mui/icons-material";

interface EmptyStateProps {
  type: "no-reports" | "no-results" | "no-history" | "no-filter-results";
  searchQuery?: string;
  onClearSearch?: () => void;
  onGenerateReport?: () => void;
}

export default function EmptyState({
  type,
  searchQuery,
  onClearSearch,
  onGenerateReport,
}: EmptyStateProps) {
  const configs = {
    "no-reports": {
      icon: ReportsIcon,
      title: "No Reports Available",
      description: "There are no reports configured in the system. Please contact your administrator.",
      action: null,
    },
    "no-results": {
      icon: NoResultsIcon,
      title: searchQuery ? `No results for "${searchQuery}"` : "No Reports Found",
      description: searchQuery
        ? "Try adjusting your search terms or browse by category"
        : "No reports match your current filters.",
      action: searchQuery
        ? { label: "Clear Search", onClick: onClearSearch }
        : { label: "Browse All Reports", onClick: onClearSearch },
    },
    "no-history": {
      icon: HistoryIcon,
      title: "No Report History",
      description: "You haven't generated any reports yet. Start by selecting a report from the catalog.",
      action: { label: "Browse Reports", onClick: onGenerateReport },
    },
    "no-filter-results": {
      icon: NoResultsIcon,
      title: "No Matching Reports",
      description: "No reports match the selected filters. Try selecting a different category or clearing filters.",
      action: { label: "Clear Filters", onClick: onClearSearch },
    },
  };

  const config = configs[type];
  const IconComponent = config.icon;

  return (
    <Paper
      elevation={0}
      sx={{
        p: 6,
        textAlign: "center",
        borderRadius: "16px",
        backgroundColor: "#fafafa",
        border: "1px dashed rgba(0, 0, 0, 0.1)",
      }}
    >
      <Box
        sx={{
          width: 80,
          height: 80,
          borderRadius: "50%",
          backgroundColor: "rgba(128, 0, 32, 0.05)",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          mx: "auto",
          mb: 3,
        }}
      >
        <IconComponent sx={{ fontSize: 40, color: "#800020" }} />
      </Box>

      <Typography variant="h6" sx={{ fontWeight: 600, color: "#2c3e50", mb: 1 }}>
        {config.title}
      </Typography>

      <Typography variant="body2" color="text.secondary" sx={{ mb: 3, maxWidth: 400, mx: "auto" }}>
        {config.description}
      </Typography>

      {config.action && (
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={config.action.onClick}
          sx={{
            backgroundColor: "#800020",
            "&:hover": { backgroundColor: "#600018" },
            borderRadius: "10px",
          }}
        >
          {config.action.label}
        </Button>
      )}
    </Paper>
  );
}
