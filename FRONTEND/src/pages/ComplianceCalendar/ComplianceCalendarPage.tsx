import { useState } from "react";
import {
  Box, Paper, Typography, Button, List, ListItem, ListItemText, Chip,
  Dialog, DialogTitle, DialogContent, DialogActions, TextField, MenuItem,
  Divider, Snackbar, Alert, CircularProgress, Grid,
} from "@mui/material";
import { useUpcomingDeadlines, useOverdueDeadlines } from "../../features/api/queries";
import { useCreateDeadline, type CreateDeadlineRequest } from "../../features/api/mutations";

const deadlineTypes = ["REGULATORY", "FILING", "REVIEW", "AUDIT", "REPORTING", "OTHER"];

export default function ComplianceCalendarPage() {
  const { data: upcoming } = useUpcomingDeadlines();
  const { data: overdue } = useOverdueDeadlines();
  const createDeadline = useCreateDeadline();

  const [addOpen, setAddOpen] = useState(false);
  const [formData, setFormData] = useState<CreateDeadlineRequest>({
    title: "",
    description: "",
    dueDate: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString().split("T")[0],
    deadlineType: "",
  });
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: "success" | "error" }>({
    open: false, message: "", severity: "success",
  });

  const handleCreate = async () => {
    if (!formData.title.trim() || !formData.dueDate) return;
    try {
      await createDeadline.mutateAsync(formData);
      setAddOpen(false);
      setFormData({ title: "", description: "", dueDate: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString().split("T")[0], deadlineType: "" });
      setSnackbar({ open: true, message: "Compliance deadline created.", severity: "success" });
    } catch (err: any) {
      setSnackbar({ open: true, message: err?.message || "Failed to create deadline.", severity: "error" });
    }
  };

  return (
    <Box>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 3 }}>
        <Typography variant="h6" sx={{ color: "text.primary", fontWeight: 600 }}>
          Compliance Calendar
        </Typography>
        <Button
          variant="contained"
          onClick={() => setAddOpen(true)}
          sx={{ backgroundColor: "#a93226", "&:hover": { backgroundColor: "#922b21" } }}
        >
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

      {/* Create Deadline Dialog */}
      <Dialog open={addOpen} onClose={() => setAddOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Create Compliance Deadline</DialogTitle>
        <Divider />
        <DialogContent sx={{ pt: 2 }}>
          <Grid container spacing={2}>
            <Grid item xs={12}>
              <TextField
                label="Title"
                value={formData.title}
                onChange={(e) => setFormData(prev => ({ ...prev, title: e.target.value }))}
                fullWidth
                size="small"
                required
                placeholder="e.g. Q2 SAR Filing Deadline"
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                label="Due Date"
                type="date"
                value={formData.dueDate}
                onChange={(e) => setFormData(prev => ({ ...prev, dueDate: e.target.value }))}
                fullWidth
                size="small"
                required
                InputLabelProps={{ shrink: true }}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                select
                label="Type"
                value={formData.deadlineType}
                onChange={(e) => setFormData(prev => ({ ...prev, deadlineType: e.target.value }))}
                fullWidth
                size="small"
              >
                <MenuItem value="">Select Type</MenuItem>
                {deadlineTypes.map(t => <MenuItem key={t} value={t}>{t}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12}>
              <TextField
                label="Description"
                value={formData.description}
                onChange={(e) => setFormData(prev => ({ ...prev, description: e.target.value }))}
                fullWidth
                size="small"
                multiline
                rows={3}
                placeholder="Optional details about this compliance deadline..."
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAddOpen(false)} sx={{ textTransform: "none" }}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleCreate}
            disabled={!formData.title.trim() || !formData.dueDate || createDeadline.isPending}
            sx={{ backgroundColor: "#a93226", "&:hover": { backgroundColor: "#922b21" }, textTransform: "none" }}
          >
            {createDeadline.isPending ? <CircularProgress size={18} sx={{ color: "white" }} /> : "Create Deadline"}
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={6000}
        onClose={() => setSnackbar(prev => ({ ...prev, open: false }))}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      >
        <Alert severity={snackbar.severity} onClose={() => setSnackbar(prev => ({ ...prev, open: false }))} sx={{ width: "100%" }}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
}
