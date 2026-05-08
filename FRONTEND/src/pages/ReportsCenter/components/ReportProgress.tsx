/**
 * Report Generation Progress Component
 * Shows progress bar for long-running reports
 */

import { Box, LinearProgress, Typography, Paper, Chip, useTheme } from "@mui/material";
import { alpha } from "@mui/material/styles";
import {
  HourglassEmpty as PendingIcon,
  CheckCircle as SuccessIcon,
  Error as ErrorIcon,
  PlayCircle as ProcessingIcon,
  type SvgIconComponent,
} from "@mui/icons-material";
import type { ReportGenerationProgress } from "../../../features/api/reportQueries";

interface ReportProgressProps {
  progress?: ReportGenerationProgress;
  showDetails?: boolean;
}

type StatusType = "pending" | "processing" | "completed" | "failed";

const STATUS_ICONS: Record<StatusType, SvgIconComponent> = {
  pending: PendingIcon,
  processing: ProcessingIcon,
  completed: SuccessIcon,
  failed: ErrorIcon,
};

const useStatusColors = (): Record<StatusType, { bg: string; text: string; bar: string }> => {
  const theme = useTheme();
  return {
    pending: { bg: alpha(theme.palette.warning.main, 0.1), text: theme.palette.warning.dark, bar: theme.palette.warning.dark },
    processing: { bg: alpha(theme.palette.info.main, 0.1), text: theme.palette.info.main, bar: theme.palette.info.main },
    completed: { bg: alpha(theme.palette.success.main, 0.1), text: theme.palette.success.main, bar: theme.palette.success.main },
    failed: { bg: alpha(theme.palette.error.main, 0.1), text: theme.palette.error.dark, bar: theme.palette.error.dark },
  };
};

const STATUS_MESSAGES: Record<StatusType, string> = {
  pending: "Waiting to start...",
  processing: "Generating report...",
  completed: "Report ready!",
  failed: "Generation failed",
};

export default function ReportProgress({ progress, showDetails = true }: ReportProgressProps) {
  const STATUS_COLORS = useStatusColors();
  if (!progress) return null;

  const status = progress.status as StatusType;
  const StatusIcon = STATUS_ICONS[status];
  const colors = STATUS_COLORS[status];

  const formatTime = (seconds?: number): string => {
    if (!seconds || seconds < 0) return "";
    if (seconds < 60) return `${Math.ceil(seconds)}s remaining`;
    const minutes = Math.ceil(seconds / 60);
    return `${minutes}m remaining`;
  };

  return (
    <Paper
      elevation={0}
      sx={{
        p: 2,
        borderRadius: "12px",
        backgroundColor: colors.bg,
        border: `1px solid ${colors.text}20`,
      }}
    >
      <Box sx={{ display: "flex", alignItems: "center", gap: 1.5, mb: 1.5 }}>
        <StatusIcon sx={{ color: colors.text, fontSize: 20 }} />
        <Typography variant="body2" sx={{ fontWeight: 600, color: colors.text }}>
          {progress.message || STATUS_MESSAGES[status]}
        </Typography>
        {showDetails && progress.estimatedTimeRemaining && progress.estimatedTimeRemaining > 0 && (
          <Chip
            label={formatTime(progress.estimatedTimeRemaining)}
            size="small"
            sx={{
              height: 20,
              fontSize: "0.7rem",
              ml: "auto",
              backgroundColor: colors.text,
              color: "#fff",
            }}
          />
        )}
      </Box>

      <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
        <Box sx={{ flex: 1 }}>
          <LinearProgress
            variant={status === "processing" ? "determinate" : "indeterminate"}
            value={progress.progress}
            sx={{
              height: 8,
              borderRadius: 4,
              backgroundColor: `${colors.text}20`,
              "& .MuiLinearProgress-bar": {
                backgroundColor: colors.bar,
                borderRadius: 4,
              },
            }}
          />
        </Box>
        <Typography
          variant="body2"
          sx={{
            minWidth: 45,
            fontWeight: 600,
            color: colors.text,
            fontVariantNumeric: "tabular-nums",
          }}
        >
          {progress.progress}%
        </Typography>
      </Box>
    </Paper>
  );
}
