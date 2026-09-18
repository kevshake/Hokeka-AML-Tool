/**
 * Report Card Component
 * Displays individual report with metadata and actions
 */

import { useState } from "react";
import {
  Card,
  CardContent,
  Typography,
  Box,
  Chip,
  Button,
  IconButton,
  Tooltip,
  Collapse,
  Divider,
  CircularProgress,
} from "@mui/material";
import {
  PlayArrow as GenerateIcon,
  Schedule as ScheduleIcon,
  ExpandMore as ExpandIcon,
  PictureAsPdf as PdfIcon,
  TableChart as CsvIcon,
  Assessment as ExcelIcon,
  Gavel as RegulatoryIcon,
  Settings as OperationalIcon,
  CheckCircle as ComplianceIcon,
  Analytics as AnalyticalIcon,
  Star as StarIcon,
  StarBorder as StarBorderIcon,
} from "@mui/icons-material";
import type {
  ReportDefinition,
  ReportType,
} from "../../../types/reports/reportDefinitions";
import ReportParameterForm from "./ReportParameterForm";

interface ReportCardProps {
  report: ReportDefinition;
  onGenerate: (reportId: string, params: Record<string, unknown>, format: string) => void;
  onSchedule: (report: ReportDefinition) => void;
  isGenerating?: boolean;
  isFavorite?: boolean;
  onToggleFavorite?: () => void;
}

const TYPE_ICONS: Record<ReportType, typeof RegulatoryIcon> = {
  Regulatory: RegulatoryIcon,
  Operational: OperationalIcon,
  Compliance: ComplianceIcon,
  Analytical: AnalyticalIcon,
};

/*
 * Opaque grounds (see SOFT_TINT in src/theme/tokens.ts) so the label contrast is
 * deterministic instead of compositing against whatever card state is beneath.
 * Regulatory/Operational are deliberately separated onto gold vs amber.
 */
const TYPE_COLORS: Record<ReportType, { bg: string; text: string; border: string }> = {
  Regulatory: {
    bg: "var(--brand-soft)",
    text: "var(--gold)",
    border: "color-mix(in srgb, var(--gold) 35%, transparent)",
  },
  Operational: {
    bg: "var(--warning-soft)",
    text: "var(--warning)",
    border: "color-mix(in srgb, var(--warning) 35%, transparent)",
  },
  Compliance: {
    bg: "var(--success-soft)",
    text: "var(--success)",
    border: "color-mix(in srgb, var(--success) 35%, transparent)",
  },
  Analytical: {
    bg: "var(--info-soft)",
    text: "var(--info)",
    border: "color-mix(in srgb, var(--info) 35%, transparent)",
  },
};

export default function ReportCard({
  report,
  onGenerate,
  onSchedule,
  isGenerating = false,
  isFavorite = false,
  onToggleFavorite,
}: ReportCardProps) {
  const [expanded, setExpanded] = useState(false);
  const [parameters, setParameters] = useState<Record<string, unknown>>({});
  const [selectedFormat, setSelectedFormat] = useState(report.supportsExport[0]);
  const [isLoading, setIsLoading] = useState(false);

  const TypeIcon = TYPE_ICONS[report.type];
  const typeColors = TYPE_COLORS[report.type];

  const handleGenerate = async () => {
    setIsLoading(true);
    try {
      await onGenerate(report.id, parameters, selectedFormat);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <Card
      sx={{
        position: "relative",
        borderRadius: "var(--radius)",
        backgroundColor: "var(--surface-2)",
        border: "1px solid var(--line)",
        boxShadow: "var(--shadow-sm)",
        transition: "border-color .25s var(--ease), box-shadow .25s var(--ease), transform .25s var(--ease)",
        opacity: isGenerating && !isLoading ? 0.7 : 1,
        pointerEvents: isGenerating && !isLoading ? "none" : "auto",
        /* Gold rule on the leading edge marks the expanded card. */
        "&::before": {
          content: '""',
          position: "absolute",
          insetBlock: 0,
          left: 0,
          width: "2px",
          backgroundColor: expanded ? "var(--gold)" : "transparent",
          transition: "background-color .25s var(--ease)",
        },
        "&:hover": {
          borderColor: "var(--line-strong)",
          boxShadow: "var(--shadow-md)",
          transform: "translateY(-2px)",
        },
      }}
    >
      <CardContent sx={{ p: 2.5 }}>
        {/* Header */}
        <Box sx={{ display: "flex", gap: 2, mb: 2 }}>
          <Box
            sx={{
              width: 38,
              height: 38,
              borderRadius: "var(--radius)",
              backgroundColor: typeColors.bg,
              border: `1px solid ${typeColors.border}`,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              flexShrink: 0,
            }}
          >
            <TypeIcon sx={{ color: typeColors.text, fontSize: 19 }} />
          </Box>

          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography
              variant="h6"
              sx={{
                fontWeight: 600,
                color: "var(--ink)",
                mb: 0.5,
                fontSize: "0.9375rem",
                letterSpacing: "-0.01em",
              }}
            >
              {report.name}
            </Typography>

            <Typography
              variant="body2"
              color="text.secondary"
              sx={{
                display: "-webkit-box",
                WebkitLineClamp: expanded ? undefined : 2,
                WebkitBoxOrient: "vertical",
                overflow: "hidden",
              }}
            >
              {report.description}
            </Typography>
          </Box>

          {onToggleFavorite && (
            <Tooltip title={isFavorite ? "Remove favorite" : "Add to favorites"}>
              <IconButton size="small" onClick={onToggleFavorite} aria-label="toggle favorite">
                {isFavorite ? (
                  <StarIcon sx={{ color: "var(--gold)" }} />
                ) : (
                  <StarBorderIcon sx={{ color: "text.secondary" }} />
                )}
              </IconButton>
            </Tooltip>
          )}
        </Box>

        {/* Tags & Type */}
        <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap", mb: 2 }}>
          <Chip
            size="small"
            label={report.type}
            sx={{
              backgroundColor: typeColors.bg,
              color: typeColors.text,
              border: `1px solid ${typeColors.border}`,
              fontWeight: 500,
              fontSize: "0.75rem",
            }}
          />

          {report.tags.slice(0, 3).map((tag) => (
            <Chip
              key={tag}
              size="small"
              label={tag}
              variant="outlined"
              sx={{
                fontSize: "0.75rem",
                borderColor: "var(--line)",
              }}
            />
          ))}

          {report.tags.length > 3 && (
            <Chip
              size="small"
              label={`+${report.tags.length - 3}`}
              variant="outlined"
              sx={{ fontSize: "0.75rem" }}
            />
          )}
        </Box>

        {/* Actions */}
        <Box sx={{ display: "flex", gap: 1, alignItems: "center" }}>
          <Box sx={{ display: "flex", gap: 0.5 }}>
            {report.supportsExport.includes("PDF") && (
              <Tooltip title="PDF" arrow>
                <IconButton
                  size="small"
                  onClick={() => setSelectedFormat("PDF")}
                  disabled={isLoading}
                  sx={{
                    color: selectedFormat === "PDF" ? "var(--gold)" : "text.secondary",
                    backgroundColor:
                      selectedFormat === "PDF" ? "var(--brand-soft)" : "transparent",
                  }}
                >
                  <PdfIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            )}
            {report.supportsExport.includes("CSV") && (
              <Tooltip title="CSV" arrow>
                <IconButton
                  size="small"
                  onClick={() => setSelectedFormat("CSV")}
                  disabled={isLoading}
                  sx={{
                    color: selectedFormat === "CSV" ? "var(--gold)" : "text.secondary",
                    backgroundColor:
                      selectedFormat === "CSV" ? "var(--brand-soft)" : "transparent",
                  }}
                >
                  <CsvIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            )}
            {report.supportsExport.includes("Excel") && (
              <Tooltip title="Excel" arrow>
                <IconButton
                  size="small"
                  onClick={() => setSelectedFormat("Excel")}
                  disabled={isLoading}
                  sx={{
                    color: selectedFormat === "Excel" ? "var(--gold)" : "text.secondary",
                    backgroundColor:
                      selectedFormat === "Excel" ? "var(--brand-soft)" : "transparent",
                  }}
                >
                  <ExcelIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            )}
          </Box>

          <Box sx={{ flex: 1 }} />

          <Button
            variant="outlined"
            size="small"
            startIcon={<ScheduleIcon />}
            onClick={() => onSchedule(report)}
            disabled={isLoading || isGenerating}
            color="primary"
          >
            Schedule
          </Button>

          <Button
            variant="contained"
            size="small"
            startIcon={
              isLoading ? (
                <CircularProgress size={16} sx={{ color: "inherit" }} />
              ) : (
                <GenerateIcon />
              )
            }
            onClick={handleGenerate}
            disabled={isLoading || isGenerating}
            color="primary"
          >
            {isLoading ? "Generating..." : "Generate"}
          </Button>

          {report.parameters.length > 0 && (
            <Tooltip title={expanded ? "Collapse" : "Configure"} arrow>
              <IconButton
                size="small"
                onClick={() => setExpanded(!expanded)}
                disabled={isLoading}
                sx={{
                  transform: expanded ? "rotate(180deg)" : "rotate(0deg)",
                  transition: "transform 0.3s ease",
                  color: expanded ? "var(--gold)" : "text.secondary",
                }}
              >
                <ExpandIcon />
              </IconButton>
            </Tooltip>
          )}
        </Box>

        {/* Expanded Parameters Form */}
        <Collapse in={expanded} timeout="auto" unmountOnExit>
          <Divider sx={{ my: 2 }} />
          <Box sx={{ pt: 1 }}>
            <Typography
              variant="subtitle2"
              sx={{ mb: 2, color: "text.secondary", fontWeight: 500 }}
            >
              Report Parameters
            </Typography>
            <ReportParameterForm
              parameters={report.parameters}
              values={parameters}
              onChange={setParameters}
            />
          </Box>
        </Collapse>
      </CardContent>
    </Card>
  );
}
