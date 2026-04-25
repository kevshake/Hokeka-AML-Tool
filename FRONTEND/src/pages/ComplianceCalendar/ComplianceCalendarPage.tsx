import { Box, Paper, Typography, Button, List, ListItem, ListItemText, Chip } from "@mui/material";
import { useUpcomingDeadlines, useOverdueDeadlines } from "../../features/api/queries";

export default function ComplianceCalendarPage() {
  const { data: upcoming } = useUpcomingDeadlines();
  const { data: overdue } = useOverdueDeadlines();

  return (
    <Box>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 3 }}>
        <Typography variant="h6" sx={{ color: "text.primary", fontWeight: 600 }}>
          Compliance Calendar
        </Typography>
        <Button variant="contained" sx={{ backgroundColor: "#a93226", "&:hover": { backgroundColor: "#922b21" } }}>
          Create Deadline
        </Button>
      </Box>

      <Box sx={{ display: "flex", gap: 3 }}>
        <Paper sx={{ flex: 1, p: 3, backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
          <Typography variant="h6" sx={{ color: "#e74c3c", mb: 2 }}>
            Overdue Deadlines
          </Typography>
          {overdue && Array.isArray(overdue) && overdue.length > 0 ? (
            <List>
              {overdue.map((deadline: any, idx: number) => (
                <ListItem key={idx} sx={{ borderBottom: "1px solid rgba(0,0,0,0.1)" }}>
                  <ListItemText
                    primary={deadline.title || deadline.description || "Deadline"}
                    secondary={deadline.dueDate ? new Date(deadline.dueDate).toLocaleDateString() : ""}
                    primaryTypographyProps={{ sx: { color: "text.primary" } }}
                    secondaryTypographyProps={{ sx: { color: "text.secondary" } }}
                  />
                  <Chip label="Overdue" color="error" size="small" />
                </ListItem>
              ))}
            </List>
          ) : (
            <Typography sx={{ color: "text.disabled" }}>No overdue deadlines</Typography>
          )}
        </Paper>

        <Paper sx={{ flex: 1, p: 3, backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
          <Typography variant="h6" sx={{ color: "text.primary", mb: 2 }}>
            Upcoming Deadlines (Next 30 Days)
          </Typography>
          {upcoming && Array.isArray(upcoming) && upcoming.length > 0 ? (
            <List>
              {upcoming.map((deadline: any, idx: number) => (
                <ListItem key={idx} sx={{ borderBottom: "1px solid rgba(0,0,0,0.1)" }}>
                  <ListItemText
                    primary={deadline.title || deadline.description || "Deadline"}
                    secondary={deadline.dueDate ? new Date(deadline.dueDate).toLocaleDateString() : ""}
                    primaryTypographyProps={{ sx: { color: "text.primary" } }}
                    secondaryTypographyProps={{ sx: { color: "text.secondary" } }}
                  />
                  <Chip label="Upcoming" color="warning" size="small" />
                </ListItem>
              ))}
            </List>
          ) : (
            <Typography sx={{ color: "text.disabled" }}>No upcoming deadlines</Typography>
          )}
        </Paper>
      </Box>
    </Box>
  );
}

