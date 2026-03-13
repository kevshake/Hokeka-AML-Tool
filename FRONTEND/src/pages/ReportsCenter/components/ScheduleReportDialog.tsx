/**
 * Schedule Report Dialog Component
 * Schedule recurring report generation
 */

import { useState } from "react";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Box,
  Typography,
  Chip,
  FormControlLabel,
  Checkbox,
  OutlinedInput,
  Grid,
} from "@mui/material";
import {
  Schedule as ScheduleIcon,
} from "@mui/icons-material";
import type { ScheduleConfig, ExportFormat } from "../../types/reports/reportDefinitions";

interface ScheduleReportDialogProps {
  open: boolean;
  onClose: () => void;
  onSchedule: (schedule: ScheduleConfig) => void;
  reportName: string;
  availableFormats: ExportFormat[];
}

const FREQUENCIES = [
  { value: "once", label: "One Time" },
  { value: "hourly", label: "Hourly" },
  { value: "daily", label: "Daily" },
  { value: "weekly", label: "Weekly" },
  { value: "monthly", label: "Monthly" },
  { value: "quarterly", label: "Quarterly" },
  { value: "yearly", label: "Yearly" },
];

const DAYS_OF_WEEK = [
  { value: 0, label: "Sunday" },
  { value: 1, label: "Monday" },
  { value: 2, label: "Tuesday" },
  { value: 3, label: "Wednesday" },
  { value: 4, label: "Thursday" },
  { value: 5, label: "Friday" },
  { value: 6, label: "Saturday" },
];

const TIMEZONES = [
  "UTC",
  "America/New_York",
  "America/Chicago",
  "America/Denver",
  "America/Los_Angeles",
  "Europe/London",
  "Europe/Paris",
  "Asia/Tokyo",
  "Asia/Singapore",
  "Australia/Sydney",
];

export default function ScheduleReportDialog({
  open,
  onClose,
  onSchedule,
  reportName,
  availableFormats,
}: ScheduleReportDialogProps) {
  const [frequency, setFrequency] = useState<ScheduleConfig["frequency"]>("daily");
  const [dayOfWeek, setDayOfWeek] = useState(1); // Monday
  const [dayOfMonth, setDayOfMonth] = useState(1);
  const [time, setTime] = useState("08:00");
  const [timezone, setTimezone] = useState("America/New_York");
  const [recipients, setRecipients] = useState<string[]>([]);
  const [newRecipient, setNewRecipient] = useState("");
  const [selectedFormats, setSelectedFormats] = useState<ExportFormat[]>(["PDF"]);

  const handleAddRecipient = () => {
    if (newRecipient && !recipients.includes(newRecipient)) {
      setRecipients([...recipients, newRecipient]);
      setNewRecipient("");
    }
  };

  const handleRemoveRecipient = (email: string) => {
    setRecipients(recipients.filter((r) => r !== email));
  };

  const handleSchedule = () => {
    onSchedule({
      frequency,
      dayOfWeek: frequency === "weekly" ? dayOfWeek : undefined,
      dayOfMonth: frequency === "monthly" || frequency === "quarterly" || frequency === "yearly" ? dayOfMonth : undefined,
      time,
      timezone,
      recipients,
      formats: selectedFormats,
    });
    onClose();
  };

  const isValid = recipients.length > 0 && selectedFormats.length > 0;

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="md"
      fullWidth
      PaperProps={{
        sx: {
          borderRadius: "20px",
          background: "linear-gradient(135deg, #ffffff 0%, #faf8f5 100%)",
        },
      }}
    >
      <DialogTitle sx={{ pb: 1 }}>
        <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
          <Box
            sx={{
              width: 40,
              height: 40,
              borderRadius: "12px",
              background: "linear-gradient(135deg, #800020 0%, #a52a2a 100%)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <ScheduleIcon sx={{ color: "#FFD700", fontSize: 20 }} />
          </Box>
          <Box>
            <Typography variant="h6" sx={{ fontWeight: 600, color: "#2c3e50" }}>
              Schedule Report
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {reportName}
            </Typography>
          </Box>
        </Box>
      </DialogTitle>

      <DialogContent sx={{ pt: 2 }}>
        <Box sx={{ display: "flex", flexDirection: "column", gap: 3 }}>
          {/* Frequency */}
          <FormControl fullWidth>
            <InputLabel>Frequency</InputLabel>
            <Select
              value={frequency}
              onChange={(e) => setFrequency(e.target.value as ScheduleConfig["frequency"])}
              label="Frequency"
              sx={{ borderRadius: "12px" }}
            >
              {FREQUENCIES.map((f) => (
                <MenuItem key={f.value} value={f.value}>{f.label}</MenuItem>
              ))}
            </Select>
          </FormControl>

          {/* Weekly Day Selection */}
          {frequency === "weekly" && (
            <FormControl fullWidth>
              <InputLabel>Day of Week</InputLabel>
              <Select
                value={dayOfWeek}
                onChange={(e) => setDayOfWeek(e.target.value as number)}
                label="Day of Week"
                sx={{ borderRadius: "12px" }}
              >
                {DAYS_OF_WEEK.map((d) => (
                  <MenuItem key={d.value} value={d.value}>{d.label}</MenuItem>
                ))}
              </Select>
            </FormControl>
          )}

          {/* Monthly Day Selection */}
          {(frequency === "monthly" || frequency === "quarterly" || frequency === "yearly") && (
            <TextField
              fullWidth
              type="number"
              label="Day of Month"
              value={dayOfMonth}
              onChange={(e) => setDayOfMonth(parseInt(e.target.value))}
              inputProps={{ min: 1, max: 31 }}
              sx={{ "& .MuiOutlinedInput-root": { borderRadius: "12px" } }}
            />
          )}

          {/* Time & Timezone */}
          <Grid container spacing={2}>
            <Grid item xs={6}>
              <TextField
                fullWidth
                type="time"
                label="Time"
                value={time}
                onChange={(e) => setTime(e.target.value)}
                InputLabelProps={{ shrink: true }}
                sx={{ "& .MuiOutlinedInput-root": { borderRadius: "12px" } }}
              />
            </Grid>
            <Grid item xs={6}>
              <FormControl fullWidth>
                <InputLabel>Timezone</InputLabel>
                <Select
                  value={timezone}
                  onChange={(e) => setTimezone(e.target.value)}
                  label="Timezone"
                  sx={{ borderRadius: "12px" }}
                >
                  {TIMEZONES.map((tz) => (
                    <MenuItem key={tz} value={tz}>{tz}</MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>
          </Grid>

          {/* Export Formats */}
          <Box>
            <Typography variant="subtitle2" sx={{ mb: 1, color: "text.secondary" }}>
              Export Formats
            </Typography>
            <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
              {availableFormats.map((format) => (
                <FormControlLabel
                  key={format}
                  control={
                    <Checkbox
                      checked={selectedFormats.includes(format)}
                      onChange={(e) => {
                        if (e.target.checked) {
                          setSelectedFormats([...selectedFormats, format]);
                        } else {
                          setSelectedFormats(selectedFormats.filter((f) => f !== format));
                        }
                      }}
                      sx={{
                        color: "#800020",
                        "&. Mui-checked": {
                          color: "#800020",
                        },
                      }}
                    />
                  }
                  label={format}
                />
              ))}
            </Box>
          </Box>

          {/* Recipients */}
          <Box>
            <Typography variant="subtitle2" sx={{ mb: 1, color: "text.secondary" }}>
              Email Recipients
            </Typography>
            <Box sx={{ display: "flex", gap: 1, mb: 2 }}>
              <TextField
                fullWidth
                type="email"
                placeholder="Enter email address"
                value={newRecipient}
                onChange={(e) => setNewRecipient(e.target.value)}
                onKeyPress={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    handleAddRecipient();
                  }
                }}
                sx={{ "& .MuiOutlinedInput-root": { borderRadius: "12px" } }}
              />
              <Button
                variant="contained"
                onClick={handleAddRecipient}
                disabled={!newRecipient}
                sx={{
                  backgroundColor: "#800020",
                  "&:hover": { backgroundColor: "#600018" },
                  borderRadius: "12px",
                  minWidth: "100px",
                }}
              >
                Add
              </Button>
            </Box>
            
            <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
              {recipients.map((email) => (
                <Chip
                  key={email}
                  label={email}
                  onDelete={() => handleRemoveRecipient(email)}
                  sx={{
                    backgroundColor: "rgba(128, 0, 32, 0.1)",
                    color: "#800020",
                    "& .MuiChip-deleteIcon": {
                      color: "#800020",
                      "&: hover": { color: "#600018" },
                    },
                  }}
                />
              ))}
              {recipients.length === 0 && (
                <Typography variant="caption" color="text.disabled">
                  No recipients added
                </Typography>
              )}
            </Box>
          </Box>
        </Box>
      </DialogContent>

      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button
          onClick={onClose}
          sx={{
            color: "text.secondary",
            borderRadius: "12px",
            px: 3,
          }}
        >
          Cancel
        </Button>
        <Button
          variant="contained"
          onClick={handleSchedule}
          disabled={!isValid}
          sx={{
            backgroundColor: "#800020",
            "&:hover": { backgroundColor: "#600018" },
            borderRadius: "12px",
            px: 4,
          }}
        >
          Schedule Report
        </Button>
      </DialogActions>
    </Dialog>
  );
}
