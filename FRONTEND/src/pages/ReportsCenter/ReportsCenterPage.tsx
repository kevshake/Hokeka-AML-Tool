/**
 * Reports Center Page
 * Comprehensive AML reporting system with 85+ reports
 * Integrated with backend APIs, error handling, and progress tracking
 */

import { useState, useMemo, useCallback } from "react";
import { Link, useSearchParams } from "react-router-dom";
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
  AccountBalance,
} from "@mui/icons-material";
import type { ReportDefinition, ExportFormat } from "../../types/reports/reportDefinitions";
import { REPORT_CATEGORIES } from "../../types/reports/reportDefinitions";
import {
  useReportDefinitions,
  useReportHistory,
  useGenerateReport,
  useScheduleReport,
  useDownloadReport,
  useDeleteReportInstance,
  useReportProgress,
  useReportFavorites,
  useAddReportFavorite,
  useRemoveReportFavorite,
  downloadBlob,
  type ReportApiError,
} from "../../features/api/reportQueries";
import { useToast } from "../../hooks/useToast";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import ReportCard from "./components/ReportCard";
import ReportHistory from "./components/ReportHistory";
import ScheduleReportDialog from "./components/ScheduleReportDialog";
import ReportPreviewDialog from "./components/ReportPreviewDialog";
import ReportProgress from "./components/ReportProgress";
import ReportErrorBoundary from "./components/ReportErrorBoundary";
import EmptyState from "./components/EmptyState";
import CbkSubmissionPanel from "./components/CbkSubmissionPanel";
import ChargebackDisputesPanel from "./components/ChargebackDisputesPanel";

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
  "cbk-reporting": AccountBalance,
};

export default function ReportsCenterPage() {
  const [searchParams] = useSearchParams();
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState(0);
  const [scheduleDialogOpen, setScheduleDialogOpen] = useState(false);
  const [previewDialogOpen, setPreviewDialogOpen] = useState(false);
  const [selectedReport, setSelectedReport] = useState<ReportDefinition | null>(null);
  const [previewParams, setPreviewParams] = useState<Record<string, unknown>>({});
  const [generatingReportId, setGeneratingReportId] = useState<string | null>(null);
  const recordContext = useMemo(() => {
    const recordType = searchParams.get("recordType");
    const recordId = searchParams.get("recordId");
    return recordType && recordId ? { recordType, recordId } : {};
  }, [searchParams]);

  // Toast notifications
  const { toast, showSuccess, showError, hideToast } = useToast();

  // API hooks with error handling
  // Live report catalog from the DB `reports` table.
  const {
    data: reportCatalog,
    isLoading: catalogLoading,
    error: catalogError,
    refetch: refetchCatalog,
  } = useReportDefinitions();

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

  const { data: favorites = [] } = useReportFavorites();
  const addFavorite = useAddReportFavorite();
  const removeFavorite = useRemoveReportFavorite();
  const favoriteCodes = useMemo(
    () => new Set(favorites.map((f) => f.reportCode || String(f.reportId))),
    [favorites]
  );

  const handleToggleFavorite = useCallback(
    async (reportId: string) => {
      try {
        if (favoriteCodes.has(reportId)) {
          await removeFavorite.mutateAsync(reportId);
          showSuccess("Removed from favorites");
        } else {
          await addFavorite.mutateAsync({ reportCode: reportId });
          showSuccess("Added to favorites");
        }
      } catch {
        showError("Failed to update favorite");
      }
    },
    [favoriteCodes, addFavorite, removeFavorite, showSuccess, showError]
  );

  // Track progress for generating report
  const { data: progressData } = useReportProgress(generatingReportId || "", {
    enabled: !!generatingReportId,
  });

  // All reports from the fetched catalog (stable reference for the memos below).
  const allReports = useMemo<ReportDefinition[]>(() => reportCatalog ?? [], [reportCatalog]);

  // Filter reports based on search and category — derived from the fetched catalog.
  const filteredReports = useMemo(() => {
    if (searchQuery) {
      const q = searchQuery.toLowerCase();
      return allReports.filter(
        (r) =>
          r.name.toLowerCase().includes(q) ||
          r.code.toLowerCase().includes(q) ||
          r.description.toLowerCase().includes(q) ||
          r.tags.some((t) => t.toLowerCase().includes(q))
      );
    }
    if (selectedCategory) {
      return allReports.filter((r) => r.category === selectedCategory);
    }
    return allReports;
  }, [allReports, searchQuery, selectedCategory]);

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
        const report = allReports.find((r) => r.id === reportId || r.code === reportId);
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
          // Send the DB report_code; useGenerateReport maps reportId -> reportType.
          reportId: report.code,
          parameters: { ...parameters, ...recordContext },
          format: format as ExportFormat,
        });

        // Track generation progress — backend returns executionId for /reports/status polling
        setGeneratingReportId(result.executionId ?? null);

        showSuccess(`Report "${report.name}" generation started`);
        refetchHistory();
      } catch (error) {
        const apiError = error as ReportApiError;
        showError(apiError.message || "Failed to generate report");
      }
    },
    [allReports, generateMutation, showSuccess, showError, refetchHistory, recordContext]
  );

  const handleScheduleReport = useCallback((report: ReportDefinition) => {
    setSelectedReport(report);
    setScheduleDialogOpen(true);
  }, []);

  const handleScheduleSubmit = useCallback(
    async (scheduleConfig: {
      frequency: "daily" | "weekly" | "monthly" | "quarterly" | "yearly";
      timezone: string;
      recipients: string[];
      formats: ExportFormat[];
    }) => {
      if (!selectedReport) return;
      if (!selectedReport.databaseId) {
        showError("This report is not available for scheduling");
        return;
      }

      try {
        await scheduleMutation.mutateAsync({
          reportId: selectedReport.databaseId,
          schedule: scheduleConfig,
          parameters: recordContext,
        });

        showSuccess(`Report "${selectedReport.name}" scheduled successfully`);
        setScheduleDialogOpen(false);
        setSelectedReport(null);
      } catch (error) {
        const apiError = error as ReportApiError;
        showError(apiError.message || "Failed to schedule report");
      }
    },
    [scheduleMutation, selectedReport, showSuccess, showError, recordContext]
  );

  const handleDownloadReport = useCallback(
    async (instance: { id: string; reportName?: string }, format: ExportFormat) => {
      try {
        const blob = await downloadMutation.mutateAsync({
          instanceId: instance.id,
          format,
        });

        const extension = format === "Excel" ? "xlsx" : format.toLowerCase();
        const filename = `${instance.reportName || "report"}-${instance.id}.${extension}`;
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

  const getReportCount = useCallback(
    (categoryId: string) => allReports.filter((r) => r.category === categoryId).length,
    [allReports]
  );

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
      <HokekaPageShell
        title="Reports Center"
        subtitle="Generate, schedule, and download AML compliance reports"
        noCard
      >
      <Box sx={{ p: { xs: 0, md: 0 } }}>
          {recordContext.recordType && recordContext.recordId && (
            <Alert severity="info" sx={{ mb: 2 }} action={<Button component={Link} to={`/records/${recordContext.recordType}/${recordContext.recordId}`} size="small">View source</Button>}>
              New reports will include the selected {recordContext.recordType.replace(/_/g, " ").toLowerCase()} record and its traceable relationships.
            </Alert>
          )}
          {/* Progress indicator for active generation */}
          {generatingReportId && progressData && (
            <Fade in>
              <Box sx={{ mb: 2, maxWidth: 600 }}>
                <ReportProgress progress={progressData} />
              </Box>
            </Fade>
          )}

        {/* Error Alert */}
        {historyError && (
          <Alert
            severity="error"
            sx={{ mb: 2, borderRadius: "var(--radius)" }}
            action={
              <Button color="inherit" size="small" onClick={() => refetchHistory()}>
                Retry
              </Button>
            }
          >
            Failed to load report history: {historyError.message}
          </Alert>
        )}

        {/* Catalog Error Alert */}
        {catalogError && (
          <Alert
            severity="error"
            sx={{ mb: 2, borderRadius: "var(--radius)" }}
            action={
              <Button color="inherit" size="small" onClick={() => refetchCatalog()}>
                Retry
              </Button>
            }
          >
            Failed to load report catalog: {catalogError.message}
          </Alert>
        )}

        {/* Tabs */}
        <Box sx={{ mb: 2 }}>
          <Tabs
            value={activeTab}
            onChange={(_, value) => setActiveTab(value)}
          >
            <Tab
              label={
                <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                  <ReportsIcon fontSize="small" />
                  <span>All Reports</span>
                  <Chip
                    label={allReports.length}
                    size="small"
                    sx={{
                      height: 20,
                      fontSize: "0.7rem",
                      backgroundColor: activeTab === 0 ? "var(--brand-soft)" : "var(--surface-3)",
                      color: activeTab === 0 ? "var(--gold)" : "var(--muted)",
                      border: "1px solid var(--line)",
                    }}
                  />
                </Box>
              }
              sx={{ fontWeight: 600 }}
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
                        backgroundColor: activeTab === 1 ? "var(--brand-soft)" : "var(--surface-3)",
                        color: activeTab === 1 ? "var(--gold)" : "var(--muted)",
                        border: "1px solid var(--line)",
                      }}
                    />
                  )}
                </Box>
              }
              sx={{ fontWeight: 600 }}
            />
          </Tabs>
        </Box>

        {activeTab === 0 ? (
          <Grid container spacing={2}>
            {/* Sidebar - Categories */}
            <Grid item xs={12} md={3} lg={2.5}>
              <Paper
                sx={{
                  borderRadius: "var(--radius)",
                  backgroundColor: "var(--surface-2)",
                  border: "1px solid var(--line)",
                  boxShadow: "none",
                  overflow: "hidden",
                  position: { md: "sticky" },
                  top: { md: 16 },
                }}
              >
                <Box sx={{ p: 2, borderBottom: "1px solid var(--line)" }}>
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
                        borderRadius: "var(--radius)",
                        backgroundColor: "var(--surface-2)",
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
                        "&.Mui-selected": {
                          backgroundColor: "var(--surface-3)",
                          borderLeft: "3px solid var(--gold)",
                        },
                      }}
                    >
                      <ListItemIcon sx={{ minWidth: 40 }}>
                        <ReportsIcon
                          sx={{
                            color: selectedCategory === null && !searchQuery ? "var(--gold)" : "text.secondary",
                          }}
                        />
                      </ListItemIcon>
                      <ListItemText
                        primary="All Reports"
                        primaryTypographyProps={{
                          fontWeight: selectedCategory === null && !searchQuery ? 600 : 400,
                          color: selectedCategory === null && !searchQuery ? "var(--gold)" : "inherit",
                        }}
                      />
                      <Chip
                        label={allReports.length}
                        size="small"
                        sx={{
                          height: 20,
                          fontSize: "0.7rem",
                          backgroundColor:
                            selectedCategory === null && !searchQuery ? "var(--brand-soft)" : "var(--surface-3)",
                          color: selectedCategory === null && !searchQuery ? "var(--gold)" : "var(--muted)",
                          border: "1px solid var(--line)",
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
                          sx={{ py: 1 }}
                        >
                          <ListItemIcon sx={{ minWidth: 40 }}>
                            <CategoryIcon
                              sx={{
                                color: isSelected ? "var(--gold)" : "text.secondary",
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
                              color: isSelected ? "var(--gold)" : "inherit",
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
                              backgroundColor: isSelected ? "var(--brand-soft)" : "var(--surface-3)",
                              color: isSelected ? "var(--gold)" : "var(--muted)",
                              border: "1px solid var(--line)",
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
              {/* Catalog Loading State */}
              {catalogLoading && (
                <Box
                  sx={{
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                    justifyContent: "center",
                    py: 8,
                    gap: 2,
                  }}
                >
                  <CircularProgress sx={{ color: "var(--gold)" }} />
                  <Typography variant="body2" color="text.secondary">
                    Loading report catalog...
                  </Typography>
                </Box>
              )}

              {/* Loading State */}
              {generateMutation.isPending && (
                <Box sx={{ mb: 2, display: "flex", alignItems: "center", gap: 1 }}>
                  <CircularProgress size={16} sx={{ color: "var(--gold)" }} />
                  <Typography variant="body2" color="text.secondary">
                    Generating report...
                  </Typography>
                </Box>
              )}

              {!catalogLoading && searchQuery && (
                <Box sx={{ mb: 2 }}>
                  <Typography variant="body2" color="text.secondary">
                    Search results for "<strong>{searchQuery}</strong>" ({" "}
                    {filteredReports.length} reports found)
                  </Typography>
                </Box>
              )}

              {!catalogLoading &&
                Object.entries(groupedReports).map(([categoryId, reports]) => {
                const category = REPORT_CATEGORIES.find((c) => c.id === categoryId);
                if (!category) return null;

                return (
                  <Box key={categoryId} sx={{ mb: 2 }}>
                    <Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 2 }}>
                      <Typography variant="h6" sx={{ fontWeight: 600, color: "var(--ink)" }}>
                        {category.name}
                      </Typography>
                      <Chip
                        label={`${reports.length} reports`}
                        size="small"
                        sx={{
                          backgroundColor: "var(--brand-soft)",
                          color: "var(--gold)",
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
                            isFavorite={favoriteCodes.has(report.id)}
                            onToggleFavorite={() => handleToggleFavorite(report.id)}
                          />
                        </Grid>
                      ))}
                    </Grid>
                  </Box>
                );
              })}

              {!catalogLoading &&
                !catalogError &&
                filteredReports.length === 0 &&
                selectedCategory !== "cbk-reporting" &&
                selectedCategory !== "chargeback-dispute" && (
                  <EmptyState
                    type={
                      searchQuery
                        ? "no-results"
                        : allReports.length === 0
                          ? "no-reports"
                          : "no-filter-results"
                    }
                    searchQuery={searchQuery}
                    onClearSearch={clearSearch}
                  />
                )}

              {selectedCategory === "cbk-reporting" && (
                <Box sx={{ mt: 3 }}>
                  <CbkSubmissionPanel onSubmitSuccess={refetchHistory} />
                </Box>
              )}

              {selectedCategory === "chargeback-dispute" && (
                <ChargebackDisputesPanel />
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
            sx={{ borderRadius: "var(--radius)" }}
          >
            {toast.message}
          </Alert>
        </Snackbar>
      </Box>
      </HokekaPageShell>
    </ReportErrorBoundary>
  );
}
