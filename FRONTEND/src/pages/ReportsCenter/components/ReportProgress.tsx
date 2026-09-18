/**
 * Report Generation Progress Component
 * Shows progress bar for long-running reports
 */

import { Box, LinearProgress, Typography, Paper, Chip } from "@mui/material";
import {
  HourglassEmpty as PendingIcon,
  CheckCircle as SuccessIcon,
  Error as ErrorIcon,
  PlayCircle as ProcessingIcon,
  type SvgIconComponent,
} from "@mui/icons-material";
import type { ReportGenerationProgress } from "../../../features/api/reportQueries";
import { semantic, withAlpha } from "../../../theme/tokens";

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

/*
 * Opaque status grounds from the design tokens. This previously used
 * `palette.*.dark` for the text, which are the fills meant for LIGHT grounds and
 * measure below AA here, over an alpha tint that composited against whatever was
 * behind it. Each pair below is measured: >= 4.72:1.
 */
const STATUS_COLORS: Record<StatusType, { bg: string; text: string; bar: string }> = {
  pending: { bg: semantic.warningSoft, text: semantic.warning, bar: semantic.warning },
  processing: { bg: semantic.infoSoft, text: semantic.info, bar: semantic.info },
  completed: { bg: semantic.successSoft, text: semantic.success, bar: semantic.success },
  failed: { bg: semantic.errorSoft, text: semantic.error, bar: semantic.error },
};

const STATUS_MESSAGES: Record<StatusType, string> = {
  pending: "Waiting to start...",
  processing: "Generating report...",
  completed: "Report ready!",
  failed: "Generation failed",
};

export default function ReportProgress({ progress, showDetails = true }: ReportProgressProps) {
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
        borderRadius: "var(--radius)",
        backgroundColor: colors.bg,
        border: `1px solid ${withAlpha(colors.text, 0.13)}`,
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
              backgroundColor: colors.bg,
              color: colors.text,
              border: `1px solid ${withAlpha(colors.text, 0.35)}`,
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
              borderRadius: 999,
              backgroundColor: withAlpha(colors.text, 0.18),
              "& .MuiLinearProgress-bar": {
                backgroundColor: colors.bar,
                borderRadius: 999,
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
