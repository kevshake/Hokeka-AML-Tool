import { Box, Paper, Typography, Chip, CircularProgress } from "@mui/material";
import { useCases } from "../../features/api/queries";
import type { CaseStatus, Priority } from "../../types";

const statusConfig: Record<CaseStatus, { label: string; color: string; bg: string }> = {
  NEW: { label: "New", color: "#3498db", bg: "#ebf5fb" },
  ASSIGNED: { label: "Assigned", color: "#8e44ad", bg: "#f5eef8" },
  INVESTIGATING: { label: "Investigating", color: "#f39c12", bg: "#fef9e7" },
  PENDING_REVIEW: { label: "Pending Review", color: "#e67e22", bg: "#fef5e7" },
  ESCALATED: { label: "Escalated", color: "#e74c3c", bg: "#fdedec" },
  RESOLVED: { label: "Resolved", color: "#27ae60", bg: "#eafaf1" },
};

const priorityColors: Record<Priority, string> = {
  CRITICAL: "#e74c3c",
  HIGH: "#e67e22",
  MEDIUM: "#f39c12",
  LOW: "#95a5a6",
};

const QUEUE_STATUSES: CaseStatus[] = ["NEW", "ASSIGNED", "INVESTIGATING", "PENDING_REVIEW", "ESCALATED"];

function QueueColumn({ status }: { status: CaseStatus }) {
  const cfg = statusConfig[status];
  const { data, isLoading } = useCases({ page: 0, size: 20, status });
  const cases = data?.content || [];

  return (
    <Box sx={{ minWidth: 240, flex: "0 0 240px" }}>
      <Box
        sx={{
          px: 1.5, py: 1, mb: 1, borderRadius: 1,
          backgroundColor: cfg.bg,
          border: `1px solid ${cfg.color}40`,
          display: "flex", alignItems: "center", justifyContent: "space-between",
        }}
      >
        <Typography variant="subtitle2" sx={{ color: cfg.color, fontWeight: 700 }}>
          {cfg.label}
        </Typography>
        <Chip
          label={data?.totalElements ?? (isLoading ? "…" : 0)}
          size="small"
          sx={{ backgroundColor: cfg.color + "20", color: cfg.color, fontWeight: 600, height: 20, fontSize: "0.7rem" }}
        />
      </Box>

      {isLoading ? (
        <Box sx={{ display: "flex", justifyContent: "center", py: 3 }}>
          <CircularProgress size={20} />
        </Box>
      ) : cases.length === 0 ? (
        <Box sx={{ p: 2, textAlign: "center" }}>
          <Typography variant="caption" sx={{ color: "text.disabled" }}>No cases</Typography>
        </Box>
      ) : (
        cases.map(c => (
          <Paper
            key={c.id}
            elevation={0}
            sx={{
              p: 1.5, mb: 1,
              border: "1px solid rgba(0,0,0,0.08)",
              borderRadius: 1,
              backgroundColor: "background.paper",
              "&:hover": { borderColor: cfg.color + "60", boxShadow: "0 2px 8px rgba(0,0,0,0.06)" },
              cursor: "default",
            }}
          >
            <Typography variant="caption" sx={{ color: "text.disabled", fontFamily: "monospace", display: "block", mb: 0.5 }}>
              {c.caseReference}
            </Typography>
            <Typography variant="body2" sx={{ color: "text.primary", fontSize: "0.8rem", mb: 1, lineHeight: 1.4 }}>
              {c.description?.length > 80 ? c.description.slice(0, 80) + "…" : c.description || "No description"}
            </Typography>
            <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
              <Chip
                label={c.priority}
                size="small"
                sx={{
                  height: 18, fontSize: "0.65rem",
                  backgroundColor: priorityColors[c.priority] + "20",
                  color: priorityColors[c.priority],
                  fontWeight: 600,
                }}
              />
              {c.slaDeadline && (
                <Typography variant="caption" sx={{ color: new Date(c.slaDeadline) < new Date() ? "#e74c3c" : "text.disabled", fontSize: "0.65rem" }}>
                  SLA: {new Date(c.slaDeadline).toLocaleDateString()}
                </Typography>
              )}
            </Box>
          </Paper>
        ))
      )}
    </Box>
  );
}

export default function CasesQueues() {
  return (
    <Box>
      <Typography variant="body2" sx={{ color: "text.secondary", mb: 2 }}>
        Cases grouped by workflow status. Showing up to 20 per queue.
      </Typography>
      <Box
        sx={{
          display: "flex",
          gap: 2,
          overflowX: "auto",
          pb: 2,
          "&::-webkit-scrollbar": { height: 6 },
          "&::-webkit-scrollbar-track": { backgroundColor: "rgba(0,0,0,0.05)", borderRadius: 3 },
          "&::-webkit-scrollbar-thumb": { backgroundColor: "rgba(0,0,0,0.2)", borderRadius: 3 },
        }}
      >
        {QUEUE_STATUSES.map(status => (
          <QueueColumn key={status} status={status} />
        ))}
      </Box>
    </Box>
  );
}
