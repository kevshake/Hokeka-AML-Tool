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
            <Paper sx={{ p: 2.5, borderRadius: "var(--radius)", backgroundColor: "var(--surface-1)", border: "1px solid var(--line)" }}>
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

            <Paper sx={{ p: 2.5, borderRadius: "var(--radius)", backgroundColor: "var(--surface-1)", border: "1px solid var(--line)" }}>
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
                        color="primary"
                        sx={{
                          borderColor:
                            selectedFormat === format ? "var(--gold)" : "var(--line-control)",
                          color:
                            selectedFormat === format ? "var(--brand-on-primary)" : "var(--muted)",
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
          borderRadius: "var(--radius)",
          minHeight: "70vh",
        },
      }}
    >
      <DialogTitle sx={{ pb: 0 }}>
        <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
          <Box
            sx={{
              width: 36,
              height: 36,
              borderRadius: "var(--radius)",
              backgroundColor: "var(--brand-soft)",
              border: "1px solid color-mix(in srgb, var(--gold) 35%, transparent)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <PreviewIcon sx={{ color: "var(--gold-bright)", fontSize: 24 }} />
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
          sx={{ borderRadius: "var(--radius)", color: "text.secondary" }}
        >
          Cancel
        </Button>

        {activeStep > 0 && (
          <Button
            onClick={handleBack}
            sx={{ borderRadius: "var(--radius)" }}
          >
            Back
          </Button>
        )}

        {activeStep < STEPS.length - 1 ? (
          <Button
            variant="contained"
            color="primary"
            onClick={handleNext}
          >
            Next
          </Button>
        ) : (
          <Box sx={{ display: "flex", gap: 1 }}>
            <Button
              variant="outlined"
              color="primary"
              onClick={onSchedule}
              startIcon={<ScheduleIcon />}
            >
              Schedule
            </Button>
            <Button
              variant="contained"
              color="primary"
              onClick={handleGenerate}
              startIcon={<GenerateIcon />}
            >
              Generate {selectedFormat}
            </Button>
          </Box>
        )}
      </DialogActions>
    </Dialog>
  );
}
