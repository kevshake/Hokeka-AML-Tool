/**
 * Report Preview Dialog Component
 * Shows parameter form, data preview, and charts
 */

import { useState } from "react";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  Typography,
  Tabs,
  Tab,
  Grid,
  Paper,
  Stepper,
  Step,
  StepLabel,
} from "@mui/material";
import {
  PlayArrow as GenerateIcon,
  Schedule as ScheduleIcon,
  Preview as PreviewIcon,
  BarChart as ChartIcon,
  Download as DownloadIcon,
} from "@mui/icons-material";
import type {
  ReportDefinition,
  ExportFormat,
} from "../../../types/reports/reportDefinitions";
import ReportParameterForm from "./ReportParameterForm";
import ReportPreviewTable from "./ReportPreviewTable";
import ReportChart from "./ReportChart";
import { useReportPreview, useReportChartData } from "../../../features/api/reportQueries";

interface ReportPreviewDialogProps {
  open: boolean;
  onClose: () => void;
  report: ReportDefinition;
  parameters: Record<string, unknown>;
  onGenerate: (format: ExportFormat) => void;
  onSchedule: () => void;
}

const STEPS = ["Parameters", "Preview", "Generate"];

export default function ReportPreviewDialog({
  open,
  onClose,
  report,
  parameters: initialParams,
  onGenerate,
  onSchedule,
}: ReportPreviewDialogProps) {
  const [activeStep, setActiveStep] = useState(0);
  const [activeTab, setActiveTab] = useState(0);
  const [parameters, setParameters] = useState<Record<string, unknown>>(initialParams);
  const [selectedFormat, setSelectedFormat] = useState<ExportFormat>(report.supportsExport[0]);

  // Fetch preview data when on preview step
  const { data: previewData, isLoading: previewLoading } = useReportPreview(
    activeStep >= 1 ? report.id : "",
    activeStep >= 1 ? parameters : null
  );

  // Fetch chart data if report supports charts
  const { data: chartData } = useReportChartData(
    activeStep >= 1 && report.supportsChart ? report.id : "",
    activeStep >= 1 ? parameters : null,
    "default"
  );

  const handleNext = () => {
    setActiveStep((prev) => Math.min(prev + 1, STEPS.length - 1));
  };

  const handleBack = () => {
    setActiveStep((prev) => Math.max(prev - 1, 0));
  };

  const handleGenerate = () => {
    onGenerate(selectedFormat);
    onClose();
  };

  const renderStepContent = () => {
    switch (activeStep) {
      case 0:
        return (
          <Box sx={{ py: 2 }}>
            <Typography variant="h6" sx={{ mb: 2, fontWeight: 600 }}>
              Configure Report Parameters
            </Typography>
            <Paper sx={{ p: 3, borderRadius: "16px" }}>
              <ReportParameterForm
                parameters={report.parameters}
                values={parameters}
                onChange={setParameters}
              />
            </Paper>
          </Box>
        );

      case 1:
        return (
          <Box sx={{ py: 2 }}>
            <Box sx={{ borderBottom: 1, borderColor: "divider", mb: 2 }}>
              <Tabs value={activeTab} onChange={(_, v) => setActiveTab(v)}>
                <Tab
                  icon={<PreviewIcon fontSize="small" />}
                  iconPosition="start"
                  label="Data Preview"
                />
                {report.supportsChart && (
                  <Tab
                    icon={<ChartIcon fontSize="small" />}
                    iconPosition="start"
                    label="Charts"
                  />
                )}
              </Tabs>
            </Box>

            {activeTab === 0 && (
              <ReportPreviewTable
                columns={previewData?.columns || []}
                data={previewData?.data || []}
                totalRows={previewData?.totalRows || 0}
                loading={previewLoading}
              />
            )}

            {activeTab === 1 && report.supportsChart && (
              <ReportChart
                data={chartData || {}}
                title="Analysis Chart"
              />
            )}
          </Box>
        );

      case 2:
        return (
          <Box sx={{ py: 2 }}>
            <Typography variant="h6" sx={{ mb: 2, fontWeight: 600 }}>
              Generate Report
            </Typography>

            <Paper sx={{ p: 3, borderRadius: "16px" }}>
              <Grid container spacing={3}>
                <Grid item xs={12} md={6}>
                  <Typography variant="subtitle2" sx={{ mb: 2, color: "text.secondary" }}>
                    Report Details
                  </Typography>
                  <Box sx={{ mb: 2 }}>
                    <Typography variant="body2" color="text.secondary">Name</Typography>
                    <Typography variant="body1" sx={{ fontWeight: 500 }}>{report.name}</Typography>
                  </Box>
                  <Box sx={{ mb: 2 }}>
                    <Typography variant="body2" color="text.secondary">Type</Typography>
                    <Typography variant="body1">{report.type}</Typography>
                  </Box>
                  <Box sx={{ mb: 2 }}>
                    <Typography variant="body2" color="text.secondary">Parameters</Typography>
                    <Box sx={{ mt: 1 }}>
                      {Object.entries(parameters).map(([key, value]) => (
                        <Typography
                          key={key}
                          variant="caption"
                          sx={{
                            display: "block",
                            color: "text.secondary",
                            fontFamily: "monospace",
                          }}
                        >
                          {key}: {JSON.stringify(value)}
                        </Typography>
                      ))}
                    </Box>
                  </Box>
                </Grid>

                <Grid item xs={12} md={6}>
                  <Typography variant="subtitle2" sx={{ mb: 2, color: "text.secondary" }}>
                    Export Format
                  </Typography>
                  <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
                    {report.supportsExport.map((format) => (
                      <Button
                        key={format}
                        variant={selectedFormat === format ? "contained" : "outlined"}
                        onClick={() => setSelectedFormat(format)}
                        startIcon={<DownloadIcon />}
                        sx={{
                          borderRadius: "10px",
                          backgroundColor: selectedFormat === format ? "#800020" : "transparent",
                          borderColor: selectedFormat === format ? "#800020" : "rgba(0,0,0,0.2)",
                          color: selectedFormat === format ? "#fff" : "text.primary",
                          "&:hover": {
                            backgroundColor: selectedFormat === format ? "#600018" : "rgba(128, 0, 32, 0.05)",
                          },
                        }}
                      >
                        {format}
                      </Button>
                    ))}
                  </Box>
                </Grid>
              </Grid>
            </Paper>
          </Box>
        );

      default:
        return null;
    }
  };

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="lg"
      fullWidth
      PaperProps={{
        sx: {
          borderRadius: "20px",
          minHeight: "70vh",
        },
      }}
    >
      <DialogTitle sx={{ pb: 0 }}>
        <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
          <Box
            sx={{
              width: 48,
              height: 48,
              borderRadius: "12px",
              background: "linear-gradient(135deg, #800020 0%, #a52a2a 100%)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <PreviewIcon sx={{ color: "#FFD700", fontSize: 24 }} />
          </Box>
          <Box sx={{ flex: 1 }}>
            <Typography variant="h6" sx={{ fontWeight: 600 }}>{report.name}</Typography>
            <Typography variant="caption" color="text.secondary">{report.description}</Typography>
          </Box>
        </Box>

        <Box sx={{ mt: 3 }}>
          <Stepper activeStep={activeStep} alternativeLabel>
            {STEPS.map((label) => (
              <Step key={label}>
                <StepLabel>{label}</StepLabel>
              </Step>
            ))}
          </Stepper>
        </Box>
      </DialogTitle>

      <DialogContent>{renderStepContent()}</DialogContent>

      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button
          onClick={onClose}
          sx={{ borderRadius: "10px", color: "text.secondary" }}
        >
          Cancel
        </Button>

        {activeStep > 0 && (
          <Button
            onClick={handleBack}
            sx={{ borderRadius: "10px" }}
          >
            Back
          </Button>
        )}

        {activeStep < STEPS.length - 1 ? (
          <Button
            variant="contained"
            onClick={handleNext}
            sx={{
              backgroundColor: "#800020",
              borderRadius: "10px",
              "&:hover": { backgroundColor: "#600018" },
            }}
          >
            Next
          </Button>
        ) : (
          <Box sx={{ display: "flex", gap: 1 }}>
            <Button
              variant="outlined"
              onClick={onSchedule}
              startIcon={<ScheduleIcon />}
              sx={{
                borderColor: "#C9A961",
                color: "#8B6914",
                borderRadius: "10px",
              }}
            >
              Schedule
            </Button>
            <Button
              variant="contained"
              onClick={handleGenerate}
              startIcon={<GenerateIcon />}
              sx={{
                backgroundColor: "#800020",
                borderRadius: "10px",
                "&:hover": { backgroundColor: "#600018" },
              }}
            >
              Generate {selectedFormat}
            </Button>
          </Box>
        )}
      </DialogActions>
    </Dialog>
  );
}
