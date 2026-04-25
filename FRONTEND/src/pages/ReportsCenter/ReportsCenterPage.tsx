/**
 * Reports Center Page
 * Comprehensive AML reporting system with 85+ reports
 * Integrated with backend APIs, error handling, and progress tracking
 */

import { useState, useMemo, useCallback } from "react";
import {
  Box,
  Typography,
  Grid,
  Paper,
  TextField,
  InputAdornment,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Divider,
  Chip,
  Tabs,
  Tab,
  Alert,
  Snackbar,
  CircularProgress,
  Fade,
  Button,
} from "@mui/material";
import {
  Search as SearchIcon,
  Assessment as ReportsIcon,
  Refresh as RefreshIcon,
  Gavel,
  AttachMoney,
  TrendingUp,
  DesktopMac,
  Block,
  Warning,
  FolderOpen,
  Settings,
  BarChart,
  Description,
  CheckCircle,
  Storage,
  CreditCard,
} from "@mui/icons-material";
import type { ReportDefinition, ExportFormat } from "../../types/reports/reportDefinitions";
import {
  REPORT_CATEGORIES,
  REPORT_DEFINITIONS,
  getReportsByCategory,
  searchReports,
} from "../../types/reports/reportDefinitions";
import {
  useReportHistory,
  useGenerateReport,
  useScheduleReport,
  useDownloadReport,
  useDeleteReportInstance,
  useReportProgress,
  downloadBlob,
  type ReportApiError,
} from "../../features/api/reportQueries";
import { useToast } from "../../hooks/useToast";
import ReportCard from "./components/ReportCard";
import ReportHistory from "./components/ReportHistory";
import ScheduleReportDialog from "./components/ScheduleReportDialog";
import ReportPreviewDialog from "./components/ReportPreviewDialog";
import ReportProgress from "./components/ReportProgress";
import ReportErrorBoundary from "./components/ReportErrorBoundary";
import EmptyState from "./components/EmptyState";

// Category Icons mapping
const CATEGORY_ICONS: Record<string, typeof Gavel> = {
  "aml-fraud": Gavel,
  "currency-threshold": AttachMoney,
  "transaction-monitoring": TrendingUp,
  "channel-monitoring": DesktopMac,
  "sanctions": Block,
  "fraud-incidents": Warning,
  "alert-case": FolderOpen,
  "rule-engine": Settings,
  "risk-scoring": BarChart,
  "regulatory-submission": Description,
  "compliance-management": CheckCircle,
  "data-quality": Storage,
  "chargeback-dispute": CreditCard,
};

export default function ReportsCenterPage() {
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState(0);
  const [scheduleDialogOpen, setScheduleDialogOpen] = useState(false);
  const [previewDialogOpen, setPreviewDialogOpen] = useState(false);
  const [selectedReport, setSelectedReport] = useState<ReportDefinition | null>(null);
  const [previewParams, setPreviewParams] = useState<Record<string, unknown>>({});
  const [generatingReportId, setGeneratingReportId] = useState<string | null>(null);

  // Toast notifications
  const { toast, showSuccess, showError, hideToast } = useToast();

  // API hooks with error handling
  const {
    data: historyData,
    isLoading: historyLoading,
    error: historyError,
    refetch: refetchHistory,
  } = useReportHistory({ page: 0, size: 50 });

  const generateMutation = useGenerateReport();
  const scheduleMutation = useScheduleReport();
  const downloadMutation = useDownloadReport();
  const deleteMutation = useDeleteReportInstance();

  // Track progress for generating report
  const { data: progressData } = useReportProgress(generatingReportId || "", {
    enabled: !!generatingReportId,
  });

  // Filter reports based on search and category
  const filteredReports = useMemo(() => {
    if (searchQuery) {
      return searchReports(searchQuery);
    }
    if (selectedCategory) {
      return getReportsByCategory(selectedCategory);
    }
    return REPORT_DEFINITIONS;
  }, [searchQuery, selectedCategory]);

  // Group reports by category for display
  const groupedReports = useMemo(() => {
    const groups: Record<string, ReportDefinition[]> = {};
    filteredReports.forEach((report) => {
      if (!groups[report.category]) {
        groups[report.category] = [];
      }
      groups[report.category].push(report);
    });
    return groups;
  }, [filteredReports]);

  const handleCategorySelect = useCallback((categoryId: string | null) => {
    setSelectedCategory(categoryId);
    setSearchQuery("");
  }, []);

  const handleSearchChange = useCallback((event: React.ChangeEvent<HTMLInputElement>) => {
    const value = event.target.value;
    setSearchQuery(value);
    if (value) {
      setSelectedCategory(null);
    }
  }, []);

  const clearSearch = useCallback(() => {
    setSearchQuery("");
    setSelectedCategory(null);
  }, []);

  const handleGenerateReport = useCallback(
    async (reportId: string, parameters: Record<string, unknown>, format: string) => {
      try {
        const report = REPORT_DEFINITIONS.find((r) => r.id === reportId);
        if (!report) {
          showError("Report not found");
          return;
        }

        // If report has parameters, show preview dialog first
        if (report.parameters.length > 0 && Object.keys(parameters).length === 0) {
          setSelectedReport(report);
          setPreviewParams(parameters);
          setPreviewDialogOpen(true);
          return;
        }

        const result = await generateMutation.mutateAsync({
          reportId,
          parameters,
          format: format as ExportFormat,
        });

        // Track generation progress
        setGeneratingReportId(result.id);

        showSuccess(`Report "${report.name}" generation started`);
        refetchHistory();
      } catch (error) {
        const apiError = error as ReportApiError;
        showError(apiError.message || "Failed to generate report");
      }
    },
    [generateMutation, showSuccess, showError, refetchHistory]
  );

  const handleScheduleReport = useCallback((report: ReportDefinition) => {
    setSelectedReport(report);
    setScheduleDialogOpen(true);
  }, []);

  const handleScheduleSubmit = useCallback(
    async (scheduleConfig: {
      frequency: "once" | "hourly" | "daily" | "weekly" | "monthly" | "quarterly" | "yearly";
      timezone: string;
      recipients: string[];
      formats: ExportFormat[];
    }) => {
      if (!selectedReport) return;

      try {
        await scheduleMutation.mutateAsync({
          reportId: selectedReport.id,
          schedule: scheduleConfig,
          parameters: {},
        });

        showSuccess(`Report "${selectedReport.name}" scheduled successfully`);
        setScheduleDialogOpen(false);
        setSelectedReport(null);
      } catch (error) {
        const apiError = error as ReportApiError;
        showError(apiError.message || "Failed to schedule report");
      }
    },
    [scheduleMutation, selectedReport, showSuccess, showError]
  );

  const handleDownloadReport = useCallback(
    async (instance: { id: string; reportName?: string }, format: ExportFormat) => {
      try {
        const blob = await downloadMutation.mutateAsync({
          instanceId: instance.id,
          format,
        });

        const filename = `${instance.reportName || "report"}-${instance.id}.${format.toLowerCase()}`;
        downloadBlob(blob, filename);

        showSuccess("Report downloaded successfully");
      } catch (error) {
        const apiError = error as ReportApiError;
        showError(apiError.message || "Failed to download report");
      }
    },
    [downloadMutation, showSuccess, showError]
  );

  const handleDeleteReport = useCallback(
    async (instanceId: string) => {
      try {
        await deleteMutation.mutateAsync(instanceId);
        showSuccess("Report deleted successfully");
        refetchHistory();
      } catch (error) {
        const apiError = error as ReportApiError;
        showError(apiError.message || "Failed to delete report");
      }
    },
    [deleteMutation, showSuccess, showError, refetchHistory]
  );

  const getReportCount = useCallback((categoryId: string) => {
    return REPORT_DEFINITIONS.filter((r) => r.category === categoryId).length;
  }, []);

  // Handle report generation completion
  if (
    progressData?.status === "completed" &&
    generatingReportId &&
    toast.message !== "Report generation completed"
  ) {
    showSuccess("Report generation completed", 3000);
    setGeneratingReportId(null);
  }

  if (progressData?.status === "failed" && generatingReportId) {
    showError("Report generation failed");
    setGeneratingReportId(null);
  }

  return (
    <ReportErrorBoundary reportName="Reports Center">
      <Box sx={{ p: { xs: 1.5, md: 2 } }}>
        {/* Header */}
        <Box sx={{ mb: 2 }}>
          <Box sx={{ display: "flex", alignItems: "center", gap: 2, mb: 1 }}>
            <Box
              sx={{
                width: 56,
                height: 56,
                borderRadius: "16px",
                background: "linear-gradient(135deg, #800020 0%, #a52a2a 100%)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <ReportsIcon sx={{ color: "#FFD700", fontSize: 28 }} />
            </Box>
            <Box>
              <Typography
                variant="h6"
                sx={{
                  fontWeight: 700,
                  color: "#2c3e50",
                  fontSize: { xs: "1.5rem", md: "2rem" },
                }}
              >
                Reports Center
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {REPORT_DEFINITIONS.length}+ AML and compliance reports across{" "}
                {REPORT_CATEGORIES.length} categories
              </Typography>
            </Box>
          </Box>

          {/* Progress indicator for active generation */}
          {generatingReportId && progressData && (
            <Fade in>
              <Box sx={{ mt: 2, maxWidth: 600 }}>
                <ReportProgress progress={progressData} />
              </Box>
            </Fade>
          )}
        </Box>

        {/* Error Alert */}
        {historyError && (
          <Alert
            severity="error"
            sx={{ mb: 2, borderRadius: "12px" }}
            action={
              <Button color="inherit" size="small" onClick={() => refetchHistory()}>
                Retry
              </Button>
            }
          >
            Failed to load report history: {historyError.message}
          </Alert>
        )}

        {/* Tabs */}
        <Box sx={{ mb: 2 }}>
          <Tabs
            value={activeTab}
            onChange={(_, value) => setActiveTab(value)}
            sx={{
              "& .MuiTabs-indicator": {
                backgroundColor: "#800020",
                height: 3,
                borderRadius: "3px 3px 0 0",
              },
            }}
          >
            <Tab
              label={
                <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                  <ReportsIcon fontSize="small" />
                  <span>All Reports</span>
                  <Chip
                    label={REPORT_DEFINITIONS.length}
                    size="small"
                    sx={{
                      height: 20,
                      fontSize: "0.7rem",
                      backgroundColor: activeTab === 0 ? "#800020" : "rgba(0,0,0,0.1)",
                      color: activeTab === 0 ? "#fff" : "inherit",
                    }}
                  />
                </Box>
              }
              sx={{
                textTransform: "none",
                fontWeight: 600,
                color: activeTab === 0 ? "#800020 !important" : "text.secondary",
              }}
            />
            <Tab
              label={
                <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                  <RefreshIcon fontSize="small" />
                  <span>History</span>
                  {historyData?.totalElements && historyData.totalElements > 0 && (
                    <Chip
                      label={historyData.totalElements}
                      size="small"
                      sx={{
                        height: 20,
                        fontSize: "0.7rem",
                        backgroundColor: activeTab === 1 ? "#800020" : "rgba(0,0,0,0.1)",
                        color: activeTab === 1 ? "#fff" : "inherit",
                      }}
                    />
                  )}
                </Box>
              }
              sx={{
                textTransform: "none",
                fontWeight: 600,
                color: activeTab === 1 ? "#800020 !important" : "text.secondary",
              }}
            />
          </Tabs>
        </Box>

        {activeTab === 0 ? (
          <Grid container spacing={3}>
            {/* Sidebar - Categories */}
            <Grid item xs={12} md={3} lg={2.5}>
              <Paper
                sx={{
                  borderRadius: "16px",
                  boxShadow: "0 4px 20px rgba(0, 0, 0, 0.05)",
                  overflow: "hidden",
                  position: { md: "sticky" },
                  top: { md: 24 },
                }}
              >
                <Box sx={{ p: 2, borderBottom: "1px solid rgba(0,0,0,0.05)" }}>
                  <TextField
                    fullWidth
                    placeholder="Search reports..."
                    value={searchQuery}
                    onChange={handleSearchChange}
                    InputProps={{
                      startAdornment: (
                        <InputAdornment position="start">
                          <SearchIcon sx={{ color: "text.secondary" }} />
                        </InputAdornment>
                      ),
                    }}
                    sx={{
                      "& .MuiOutlinedInput-root": {
                        borderRadius: "12px",
                        backgroundColor: "#fafafa",
                      },
                    }}
                  />
                </Box>

                <List sx={{ py: 1 }}>
                  <ListItem disablePadding>
                    <ListItemButton
                      selected={selectedCategory === null && !searchQuery}
                      onClick={() => handleCategorySelect(null)}
                      sx={{
                        py: 1.2,
                        "&. Mui-selected": {
                          backgroundColor: "rgba(128, 0, 32, 0.08)",
                          borderLeft: "3px solid #800020",
                        },
                      }}
                    >
                      <ListItemIcon sx={{ minWidth: 40 }}>
                        <ReportsIcon
                          sx={{
                            color: selectedCategory === null && !searchQuery ? "#800020" : "text.secondary",
                          }}
                        />
                      </ListItemIcon>
                      <ListItemText
                        primary="All Reports"
                        primaryTypographyProps={{
                          fontWeight: selectedCategory === null && !searchQuery ? 600 : 400,
                          color: selectedCategory === null && !searchQuery ? "#800020" : "inherit",
                        }}
                      />
                      <Chip
                        label={REPORT_DEFINITIONS.length}
                        size="small"
                        sx={{
                          height: 20,
                          fontSize: "0.7rem",
                          backgroundColor:
                            selectedCategory === null && !searchQuery ? "#800020" : "rgba(0,0,0,0.08)",
                          color: selectedCategory === null && !searchQuery ? "#fff" : "inherit",
                        }}
                      />
                    </ListItemButton>
                  </ListItem>

                  <Divider sx={{ my: 1 }} />

                  {REPORT_CATEGORIES.map((category) => {
                    const CategoryIcon = CATEGORY_ICONS[category.id];
                    const isSelected = selectedCategory === category.id;
                    const count = getReportCount(category.id);

                    return (
                      <ListItem key={category.id} disablePadding>
                        <ListItemButton
                          selected={isSelected}
                          onClick={() => handleCategorySelect(category.id)}
                          sx={{
                            py: 1.2,
                            "&. Mui-selected": {
                              backgroundColor: "rgba(128, 0, 32, 0.08)",
                              borderLeft: "3px solid #800020",
                            },
                          }}
                        >
                          <ListItemIcon sx={{ minWidth: 40 }}>
                            <CategoryIcon
                              sx={{
                                color: isSelected ? "#800020" : "text.secondary",
                                fontSize: 20,
                              }}
                            />
                          </ListItemIcon>
                          <ListItemText
                            primary={category.name}
                            secondary={category.description}
                            primaryTypographyProps={{
                              fontSize: "0.9rem",
                              fontWeight: isSelected ? 600 : 400,
                              color: isSelected ? "#800020" : "inherit",
                            }}
                            secondaryTypographyProps={{
                              fontSize: "0.75rem",
                              noWrap: true,
                            }}
                          />
                          <Chip
                            label={count}
                            size="small"
                            sx={{
                              height: 18,
                              fontSize: "0.65rem",
                              backgroundColor: isSelected ? "#800020" : "rgba(0,0,0,0.08)",
                              color: isSelected ? "#fff" : "inherit",
                            }}
                          />
                        </ListItemButton>
                      </ListItem>
                    );
                  })}
                </List>
              </Paper>
            </Grid>

            {/* Main Content - Report Cards */}
            <Grid item xs={12} md={9} lg={9.5}>
              {/* Loading State */}
              {generateMutation.isPending && (
                <Box sx={{ mb: 2, display: "flex", alignItems: "center", gap: 1 }}>
                  <CircularProgress size={16} sx={{ color: "#800020" }} />
                  <Typography variant="body2" color="text.secondary">
                    Generating report...
                  </Typography>
                </Box>
              )}

              {searchQuery && (
                <Box sx={{ mb: 2 }}>
                  <Typography variant="body2" color="text.secondary">
                    Search results for "<strong>{searchQuery}</strong>" ({" "}
                    {filteredReports.length} reports found)
                  </Typography>
                </Box>
              )}

              {Object.entries(groupedReports).map(([categoryId, reports]) => {
                const category = REPORT_CATEGORIES.find((c) => c.id === categoryId);
                if (!category) return null;

                return (
                  <Box key={categoryId} sx={{ mb: 2 }}>
                    <Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 2 }}>
                      <Typography variant="h6" sx={{ fontWeight: 600, color: "#2c3e50" }}>
                        {category.name}
                      </Typography>
                      <Chip
                        label={`${reports.length} reports`}
                        size="small"
                        sx={{
                          backgroundColor: "rgba(128, 0, 32, 0.1)",
                          color: "#800020",
                        }}
                      />
                    </Box>

                    <Grid container spacing={2}>
                      {reports.map((report) => (
                        <Grid item xs={12} md={6} lg={4} key={report.id}>
                          <ReportCard
                            report={report}
                            onGenerate={handleGenerateReport}
                            onSchedule={handleScheduleReport}
                            isGenerating={generatingReportId !== null}
                          />
                        </Grid>
                      ))}
                    </Grid>
                  </Box>
                );
              })}

              {filteredReports.length === 0 && (
                <EmptyState
                  type={searchQuery ? "no-results" : "no-filter-results"}
                  searchQuery={searchQuery}
                  onClearSearch={clearSearch}
                />
              )}
            </Grid>
          </Grid>
        ) : (
          <ReportHistory
            instances={historyData?.content || []}
            loading={historyLoading}
            onDownload={handleDownloadReport}
            onDelete={handleDeleteReport}
            onRefresh={refetchHistory}
            emptyState={
              <EmptyState
                type="no-history"
                onGenerateReport={() => setActiveTab(0)}
              />
            }
          />
        )}

        {/* Schedule Dialog */}
        {selectedReport && (
          <ScheduleReportDialog
            open={scheduleDialogOpen}
            onClose={() => {
              setScheduleDialogOpen(false);
              setSelectedReport(null);
            }}
            onSchedule={handleScheduleSubmit}
            reportName={selectedReport.name}
            availableFormats={selectedReport.supportsExport}
          />
        )}

        {/* Preview Dialog */}
        {selectedReport && (
          <ReportPreviewDialog
            open={previewDialogOpen}
            onClose={() => {
              setPreviewDialogOpen(false);
              setSelectedReport(null);
            }}
            report={selectedReport}
            parameters={previewParams}
            onGenerate={(format) => handleGenerateReport(selectedReport.id, previewParams, format)}
            onSchedule={() => {
              setPreviewDialogOpen(false);
              setScheduleDialogOpen(true);
            }}
          />
        )}

        {/* Toast Notifications */}
        <Snackbar
          open={toast.open}
          autoHideDuration={toast.autoHideDuration}
          onClose={hideToast}
          anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
        >
          <Alert
            severity={toast.severity}
            onClose={hideToast}
            sx={{ borderRadius: "12px" }}
          >
            {toast.message}
          </Alert>
        </Snackbar>
      </Box>
    </ReportErrorBoundary>
  );
}
