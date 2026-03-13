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
} from "@mui/material";
import {
  PlayArrow as GenerateIcon,
  Schedule as ScheduleIcon,
  ExpandMore as ExpandIcon,
  ExpandLess as CollapseIcon,
  PictureAsPdf as PdfIcon,
  TableChart as CsvIcon,
  Assessment as ExcelIcon,
  Gavel as RegulatoryIcon,
  Settings as OperationalIcon,
  CheckCircle as ComplianceIcon,
  Analytics as AnalyticalIcon,
} from "@mui/icons-material";
import type {
  ReportDefinition,
  ReportType,
} from "../../types/reports/reportDefinitions";
import ReportParameterForm from "./ReportParameterForm";

interface ReportCardProps {
  report: ReportDefinition;
  onGenerate: (reportId: string, params: Record<string, unknown>, format: string) => void;
  onSchedule: (report: ReportDefinition) => void;
}

const TYPE_ICONS: Record<ReportType, typeof RegulatoryIcon> = {
  Regulatory: RegulatoryIcon,
  Operational: OperationalIcon,
  Compliance: ComplianceIcon,
  Analytical: AnalyticalIcon,
};

const TYPE_COLORS: Record<ReportType, { bg: string; text: string; border: string }> = {
  Regulatory: {
    bg: "rgba(128, 0, 32, 0.1)",
    text: "#800020",
    border: "rgba(128, 0, 32, 0.3)",
  },
  Operational: {
    bg: "rgba(201, 169, 97, 0.15)",
    text: "#8B6914",
    border: "rgba(201, 169, 97, 0.4)",
  },
  Compliance: {
    bg: "rgba(46, 125, 50, 0.1)",
    text: "#2E7D32",
    border: "rgba(46, 125, 50, 0.3)",
  },
  Analytical: {
    bg: "rgba(25, 118, 210, 0.1)",
    text: "#1976D2",
    border: "rgba(25, 118, 210, 0.3)",
  },
};

export default function ReportCard({
  report,
  onGenerate,
  onSchedule,
}: ReportCardProps) {
  const [expanded, setExpanded] = useState(false);
  const [parameters, setParameters] = useState<Record<string, unknown>>({});
  const [selectedFormat, setSelectedFormat] = useState(report.supportsExport[0]);

  const TypeIcon = TYPE_ICONS[report.type];
  const typeColors = TYPE_COLORS[report.type];

  const handleGenerate = () => {
    onGenerate(report.id, parameters, selectedFormat);
  };

  return (
    <Card
      sx={{
        borderRadius: "16px",
        boxShadow: "0 4px 20px rgba(0, 0, 0, 0.05)",
        transition: "all 0.3s ease",
        border: `1px solid ${expanded ? "#800020" : "transparent"}`,
        "&: hover": {
          boxShadow: "0 8px 30px rgba(0, 0, 0, 0.1)",
          transform: "translateY(-2px)",
        },
      }}
    >
      <CardContent sx={{ p: 3 }}>
        {/* Header */}
        <Box sx={{ display: "flex", gap: 2, mb: 2 }}>
          <Box
            sx={{
              width: 48,
              height: 48,
              borderRadius: "12px",
              backgroundColor: typeColors.bg,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              flexShrink: 0,
            }}
          >
            <TypeIcon sx={{ color: typeColors.text, fontSize: 24 }} />
          </Box>

          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography
              variant="h6"
              sx={{
                fontWeight: 600,
                color: "#2c3e50",
                mb: 0.5,
                fontSize: "1rem",
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
                borderColor: "rgba(0, 0, 0, 0.1)",
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
                  sx={{
                    color: selectedFormat === "PDF" ? "#800020" : "text.secondary",
                    backgroundColor:
                      selectedFormat === "PDF" ? "rgba(128, 0, 32, 0.1)" : "transparent",
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
                  sx={{
                    color: selectedFormat === "CSV" ? "#800020" : "text.secondary",
                    backgroundColor:
                      selectedFormat === "CSV" ? "rgba(128, 0, 32, 0.1)" : "transparent",
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
                  sx={{
                    color: selectedFormat === "Excel" ? "#800020" : "text.secondary",
                    backgroundColor:
                      selectedFormat === "Excel" ? "rgba(128, 0, 32, 0.1)" : "transparent",
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
            startIcon={<ScheduleIcon sx={{ color: "#C9A961" }} />}
            onClick={() => onSchedule(report)}
            sx={{
              borderColor: "rgba(201, 169, 97, 0.5)",
              color: "#8B6914",
              borderRadius: "10px",
              textTransform: "none",
              fontWeight: 500,
              "&:hover": {
                borderColor: "#C9A961",
                backgroundColor: "rgba(201, 169, 97, 0.05)",
              },
            }}
          >
            Schedule
          </Button>

          <Button
            variant="contained"
            size="small"
            startIcon={<GenerateIcon />}
            onClick={handleGenerate}
            sx={{
              backgroundColor: "#800020",
              borderRadius: "10px",
              textTransform: "none",
              fontWeight: 500,
              "&:hover": {
                backgroundColor: "#600018",
              },
            }}
          >
            Generate
          </Button>

          {report.parameters.length > 0 && (
            <Tooltip title={expanded ? "Collapse" : "Configure"} arrow>
              <IconButton
                size="small"
                onClick={() => setExpanded(!expanded)}
                sx={{
                  transform: expanded ? "rotate(180deg)" : "rotate(0deg)",
                  transition: "transform 0.3s ease",
                  color: expanded ? "#800020" : "text.secondary",
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
