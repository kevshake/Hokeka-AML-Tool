/**
 * Reports Center API Hooks
 * Full API integration with error handling, retry logic, and progress tracking
 */

import { useQuery, useMutation, useQueryClient, type UseQueryOptions } from "@tanstack/react-query";
import { apiClient, type ApiError } from "../../lib/apiClient";
import type {
  ReportInstance,
  ScheduleConfig,
  ExportFormat,
  ReportDefinition,
} from "../../types/reports/reportDefinitions";
import { REPORT_CATEGORIES } from "../../types/reports/reportDefinitions";

// Report Generation Request
export interface GenerateReportRequest {
  reportId: string;
  parameters: Record<string, unknown>;
  format: ExportFormat;
  schedule?: ScheduleConfig;
}

// Report Preview Response
export interface ReportPreviewResponse {
  columns: string[];
  data: Record<string, unknown>[];
  totalRows: number;
  summary?: Record<string, unknown>;
}

// Report History Query Params
export interface ReportHistoryParams {
  page?: number;
  size?: number;
  reportId?: string;
  status?: string;
  startDate?: string;
  endDate?: string;
}

// Chart Data Response
export interface ChartDataResponse {
  type: "bar" | "line" | "pie" | "area";
  labels?: string[];
  datasets: Array<{
    label: string;
    data: number[];
    color?: string;
  }>;
  data?: Record<string, unknown>[];
}

// Report Generation Progress
export interface ReportGenerationProgress {
  instanceId: string;
  status: "pending" | "processing" | "completed" | "failed";
  progress: number; // 0-100
  message?: string;
  estimatedTimeRemaining?: number; // seconds
}

// Page Response type (shared)
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

// Custom error class for report operations
export class ReportApiError extends Error {
  constructor(
    message: string,
    public statusCode: number,
    public errorCode?: string,
    public details?: string[]
  ) {
    super(message);
    this.name = "ReportApiError";
  }
}

// Helper to handle API errors
const handleApiError = (error: unknown): never => {
  if (error && typeof error === "object" && "status" in error) {
    const apiError = error as ApiError;
    throw new ReportApiError(
      apiError.message || "An error occurred",
      apiError.status,
      apiError.errorCode,
      apiError.details
    );
  }
  throw new ReportApiError(
    error instanceof Error ? error.message : "Unknown error",
    500
  );
};

// ==================== QUERIES ====================

/**
 * Get report definitions from the server catalog (reports table)
 */
export const useReportDefinitions = (options?: Partial<UseQueryOptions<ReportDefinition[], ReportApiError>>) => {
  return useQuery<ReportDefinition[], ReportApiError>({
    queryKey: ["reports", "definitions"],
    queryFn: async () => {
      try {
        // Backend returns a Spring Page<ReportDefinitionDTO>; unwrap content
        const page = await apiClient.get<{ content?: ReportDefinition[] } | ReportDefinition[]>(
          "reports/definitions?size=200"
        );
        return Array.isArray(page) ? page : page.content ?? [];
      } catch (error) {
        return handleApiError(error);
      }
    },
    staleTime: 1000 * 60 * 60, // 1 hour - report definitions rarely change
    gcTime: 1000 * 60 * 60 * 24, // 24 hours
    ...options,
  });
};

/**
 * Get report categories (cached)
 */
export const useReportCategories = (options?: Partial<UseQueryOptions<typeof REPORT_CATEGORIES, ReportApiError>>) => {
  return useQuery<typeof REPORT_CATEGORIES, ReportApiError>({
    queryKey: ["reports", "categories"],
    queryFn: async () => {
      try {
        return await apiClient.get<typeof REPORT_CATEGORIES>("reports/definitions/categories");
      } catch (error) {
        return handleApiError(error);
      }
    },
    staleTime: 1000 * 60 * 60, // 1 hour
    gcTime: 1000 * 60 * 60 * 24, // 24 hours
    ...options,
  });
};

/**
 * Get report preview data with retry logic
 */
export const useReportPreview = (
  reportId: string, 
  parameters: Record<string, unknown> | null,
  options?: Partial<UseQueryOptions<ReportPreviewResponse, ReportApiError>>
) => {
  return useQuery<ReportPreviewResponse, ReportApiError>({
    queryKey: ["reports", "preview", reportId, parameters],
    queryFn: async () => {
      if (!reportId || !parameters) {
        throw new ReportApiError("Report ID and parameters are required", 400);
      }
      try {
        return await apiClient.post<ReportPreviewResponse>("reports/preview", { 
          reportId, 
          parameters 
        });
      } catch (error) {
        return handleApiError(error);
      }
    },
    enabled: !!reportId && !!parameters && Object.keys(parameters).length > 0,
    staleTime: 5 * 60 * 1000, // 5 minutes
    retry: (failureCount, error) => {
      // Retry on network errors or 5xx errors, up to 3 times
      if (error.statusCode >= 500 && failureCount < 3) {
        return true;
      }
      return false;
    },
    retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000), // Exponential backoff
    ...options,
  });
};

/**
 * Get report history with pagination
 */
export const useReportHistory = (
  params?: ReportHistoryParams,
  options?: Partial<UseQueryOptions<PageResponse<ReportInstance>, ReportApiError>>
) => {
  const queryParams = params || {};
  const queryString = Object.entries(queryParams)
    .filter(([_, value]) => value !== undefined && value !== null && value !== "")
    .map(([key, value]) => `${key}=${encodeURIComponent(String(value))}`)
    .join("&");

  return useQuery<PageResponse<ReportInstance>, ReportApiError>({
    queryKey: ["reports", "history", queryParams],
    queryFn: async () => {
      try {
        return await apiClient.get<PageResponse<ReportInstance>>(
          `reports/history${queryString ? `?${queryString}` : ""}`
        );
      } catch (error) {
        return handleApiError(error);
      }
    },
    staleTime: 30 * 1000, // 30 seconds
    ...options,
  });
};

/**
 * Get single report instance by ID
 */
export const useReportInstance = (
  instanceId: string,
  options?: Partial<UseQueryOptions<ReportInstance, ReportApiError>>
) => {
  return useQuery<ReportInstance, ReportApiError>({
    queryKey: ["reports", "instance", instanceId],
    queryFn: async () => {
      try {
        return await apiClient.get<ReportInstance>(`reports/${instanceId}`);
      } catch (error) {
        return handleApiError(error);
      }
    },
    enabled: !!instanceId,
    staleTime: 10 * 1000, // 10 seconds
    refetchInterval: (query) => {
      // Poll every 5 seconds if report is still generating
      const data = query.state.data;
      if (data?.status === "generating" || data?.status === "scheduled") {
        return 5000;
      }
      return false;
    },
    ...options,
  });
};

/**
 * Get scheduled reports
 */
export const useScheduledReports = (options?: Partial<UseQueryOptions<ReportInstance[], ReportApiError>>) => {
  return useQuery<ReportInstance[], ReportApiError>({
    queryKey: ["reports", "scheduled"],
    queryFn: async () => {
      try {
        return await apiClient.get<ReportInstance[]>("reports/schedule");
      } catch (error) {
        return handleApiError(error);
      }
    },
    staleTime: 60 * 1000, // 1 minute
    ...options,
  });
};

/**
 * Get report chart data
 */
export const useReportChartData = (
  reportId: string, 
  parameters: Record<string, unknown> | null, 
  chartType: string,
  options?: Partial<UseQueryOptions<ChartDataResponse, ReportApiError>>
) => {
  return useQuery<ChartDataResponse, ReportApiError>({
    queryKey: ["reports", "chart", reportId, chartType, parameters],
    queryFn: async () => {
      try {
        return await apiClient.post<ChartDataResponse>("reports/chart", { 
          reportId, 
          parameters, 
          chartType 
        });
      } catch (error) {
        return handleApiError(error);
      }
    },
    enabled: !!reportId && !!parameters && !!chartType,
    staleTime: 5 * 60 * 1000, // 5 minutes
    retry: (failureCount, error) => error.statusCode >= 500 && failureCount < 2,
    ...options,
  });
};

/**
 * Get report generation progress
 */
export const useReportProgress = (
  instanceId: string,
  options?: Partial<UseQueryOptions<ReportGenerationProgress, ReportApiError>>
) => {
  return useQuery<ReportGenerationProgress, ReportApiError>({
    queryKey: ["reports", "progress", instanceId],
    queryFn: async () => {
      try {
        // Backend ReportExecutionDTO -> normalize to ReportGenerationProgress
        const dto = await apiClient.get<{
          executionId: string;
          status: string;
          progressPercent?: number;
          errorMessage?: string;
        }>(`reports/status/${instanceId}`);
        const statusMap: Record<string, ReportGenerationProgress["status"]> = {
          PENDING: "pending",
          RUNNING: "processing",
          COMPLETED: "completed",
          FAILED: "failed",
          CANCELLED: "failed",
        };
        return {
          instanceId: dto.executionId,
          status: statusMap[dto.status] ?? "processing",
          progress: dto.progressPercent ?? 0,
          message: dto.errorMessage,
        } satisfies ReportGenerationProgress;
      } catch (error) {
        return handleApiError(error);
      }
    },
    enabled: !!instanceId,
    refetchInterval: (query) => {
      const data = query.state.data;
      // Keep polling while processing
      if (data?.status === "pending" || data?.status === "processing") {
        return 2000; // Poll every 2 seconds
      }
      return false;
    },
    ...options,
  });
};

// ==================== MUTATIONS ====================

/**
 * Generate report with progress tracking
 */
export const useGenerateReport = () => {
  const queryClient = useQueryClient();

  return useMutation<ReportInstance, ReportApiError, GenerateReportRequest>({
    mutationFn: async (request) => {
      try {
        // Backend ReportGenerateRequest expects reportType (= report_code) + outputFormat
        return await apiClient.post<ReportInstance>("reports/generate", {
          reportType: request.reportId,
          parameters: request.parameters,
          outputFormat: request.format,
        });
      } catch (error) {
        return handleApiError(error);
      }
    },
    onSuccess: (data) => {
      // Invalidate history cache
      queryClient.invalidateQueries({ queryKey: ["reports", "history"] });
      // Pre-cache the new instance
      queryClient.setQueryData(["reports", "instance", data.id], data);
    },
  });
};

/**
 * Schedule report with optimistic updates
 */
export const useScheduleReport = () => {
  const queryClient = useQueryClient();

  return useMutation<
    ReportInstance, 
    ReportApiError, 
    { reportId: string; schedule: ScheduleConfig; parameters: Record<string, unknown> }
  >({
    mutationFn: async (request) => {
      try {
        return await apiClient.post<ReportInstance>("reports/schedule", request);
      } catch (error) {
        return handleApiError(error);
      }
    },
    onMutate: async (newSchedule) => {
      // Cancel outgoing refetches
      await queryClient.cancelQueries({ queryKey: ["reports", "scheduled"] });

      // Snapshot previous value
      const previousScheduled = queryClient.getQueryData<ReportInstance[]>(["reports", "scheduled"]);

      // Optimistically update
      queryClient.setQueryData<ReportInstance[]>(["reports", "scheduled"], (old) => {
        const optimisticInstance: ReportInstance = {
          id: `temp-${Date.now()}`,
          reportId: newSchedule.reportId,
          reportName: "Scheduled Report",
          status: "scheduled",
          parameters: newSchedule.parameters,
          createdAt: new Date().toISOString(),
          format: newSchedule.schedule.formats[0] || "PDF",
          createdBy: "current-user",
        };
        return old ? [optimisticInstance, ...old] : [optimisticInstance];
      });

      return { previousScheduled } as { previousScheduled: ReportInstance[] | undefined };
    },
    onError: (_err, _newSchedule, context) => {
      // Rollback on error
      const ctx = context as { previousScheduled: ReportInstance[] | undefined } | undefined;
      if (ctx?.previousScheduled) {
        queryClient.setQueryData(["reports", "scheduled"], ctx.previousScheduled);
      }
    },
    onSettled: () => {
      // Always refetch after error or success
      queryClient.invalidateQueries({ queryKey: ["reports", "scheduled"] });
    },
  });
};

/**
 * Cancel scheduled report
 */
export const useCancelScheduledReport = () => {
  const queryClient = useQueryClient();

  return useMutation<void, ReportApiError, string>({
    mutationFn: async (scheduleId) => {
      try {
        await apiClient.delete(`reports/schedule/${scheduleId}`);
      } catch (error) {
        return handleApiError(error);
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["reports", "scheduled"] });
      queryClient.invalidateQueries({ queryKey: ["reports", "history"] });
    },
  });
};

/**
 * Download report file
 */
export const useDownloadReport = () => {
  return useMutation<Blob, ReportApiError, { instanceId: string; format: ExportFormat }>({
    mutationFn: async ({ instanceId, format }) => {
      const apiUrl = import.meta.env.VITE_API_URL || "";
      const response = await fetch(
        `${apiUrl}/api/v1/reports/download/${instanceId}?format=${format}`,
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("token") || ""}`,
            "X-PSP-ID": String(sessionStorage.getItem("_psp") || "0"),
          },
          credentials: "include",
        }
      );

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({
          message: "Failed to download report",
          status: response.status,
        }));
        throw new ReportApiError(
          errorData.message || "Failed to download report",
          response.status,
          errorData.errorCode
        );
      }

      return response.blob();
    },
  });
};

/**
 * Delete report instance
 */
export const useDeleteReportInstance = () => {
  const queryClient = useQueryClient();

  return useMutation<void, ReportApiError, string>({
    mutationFn: async (instanceId) => {
      try {
        await apiClient.delete(`reports/${instanceId}`);
      } catch (error) {
        return handleApiError(error);
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["reports", "history"] });
    },
  });
};

// ==================== UTILITIES ====================

/**
 * Trigger file download from blob
 */
export const downloadBlob = (blob: Blob, filename: string): void => {
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  window.URL.revokeObjectURL(url);
  document.body.removeChild(a);
};

/**
 * Format file size for display
 */
export const formatFileSize = (bytes?: number): string => {
  if (!bytes || bytes === 0) return "-";
  const kb = bytes / 1024;
  if (kb < 1024) return `${kb.toFixed(1)} KB`;
  const mb = kb / 1024;
  if (mb < 1024) return `${mb.toFixed(1)} MB`;
  const gb = mb / 1024;
  return `${gb.toFixed(1)} GB`;
};

/**
 * Get status color for UI
 */
export const getStatusColor = (status: ReportInstance["status"]): { bg: string; text: string } => {
  const colors: Record<ReportInstance["status"], { bg: string; text: string }> = {
    draft: { bg: "rgba(158, 158, 158, 0.1)", text: "#757575" },
    scheduled: { bg: "rgba(25, 118, 210, 0.1)", text: "#1976D2" },
    generating: { bg: "rgba(255, 152, 0, 0.1)", text: "#F57C00" },
    completed: { bg: "rgba(46, 125, 50, 0.1)", text: "#2E7D32" },
    failed: { bg: "rgba(211, 47, 47, 0.1)", text: "#C62828" },
  };
  return colors[status] || colors.draft;
};

/**
 * Get status label for display
 */
export const getStatusLabel = (status: ReportInstance["status"]): string => {
  const labels: Record<ReportInstance["status"], string> = {
    draft: "Draft",
    scheduled: "Scheduled",
    generating: "Generating...",
    completed: "Completed",
    failed: "Failed",
  };
  return labels[status] || status;
};
