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
  ReportDefinitionDTO,
} from "../../types/reports/reportDefinitions";
import { REPORT_CATEGORIES, mapDtoToReportDefinition } from "../../types/reports/reportDefinitions";

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

interface ReportExecutionDto {
  id?: number;
  executionId: string;
  reportId?: number;
  reportName?: string;
  reportCode?: string;
  parameters?: Record<string, unknown>;
  status: string;
  fileFormats?: string[];
  fileSizes?: Record<string, number>;
  startedAt?: string;
  completedAt?: string;
  createdAt?: string;
  errorMessage?: string;
  triggeredByName?: string;
}

export interface ReportScheduleResponse {
  id: number;
  reportId: number;
  reportName: string;
  reportCode: string;
  scheduleName: string;
  frequency: string;
  timezone: string;
  exportFormats: string[];
  nextRunAt?: string;
  isActive: boolean;
}

const normalizeExportFormat = (format?: string): ExportFormat => {
  const normalized = (format || "PDF").toUpperCase();
  if (["EXCEL", "XLS", "XLSX"].includes(normalized)) return "Excel";
  return normalized === "CSV" ? "CSV" : "PDF";
};

const mapExecutionDto = (dto: ReportExecutionDto): ReportInstance => {
  const requestedFormat = typeof dto.parameters?.outputFormat === "string"
    ? dto.parameters.outputFormat : dto.fileFormats?.[0];
  const format = normalizeExportFormat(requestedFormat);
  const fileSizeKey = format === "Excel" ? "XLSX" : format.toUpperCase();
  const statusMap: Record<string, ReportInstance["status"]> = {
    PENDING: "scheduled",
    RUNNING: "generating",
    COMPLETED: "completed",
    FAILED: "failed",
    CANCELLED: "failed",
  };
  return {
    id: String(dto.id ?? dto.executionId),
    executionId: dto.executionId,
    reportId: String(dto.reportId ?? dto.reportCode ?? ""),
    reportCode: dto.reportCode,
    reportName: dto.reportName ?? dto.reportCode ?? "Report",
    status: statusMap[dto.status] ?? "draft",
    parameters: dto.parameters ?? {},
    createdAt: dto.createdAt ?? dto.startedAt ?? new Date().toISOString(),
    completedAt: dto.completedAt,
    fileSize: dto.fileSizes?.[fileSizeKey],
    format,
    errorMessage: dto.errorMessage,
    createdBy: dto.triggeredByName ?? "System",
  };
};

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
        // Backend returns a Spring Page<ReportDefinitionDTO>; unwrap content and map
        // each DTO onto the ReportDefinition shape this UI renders.
        const page = await apiClient.get<{ content?: ReportDefinitionDTO[] } | ReportDefinitionDTO[]>(
          "reports/definitions?size=200"
        );
        const dtos = Array.isArray(page) ? page : page.content ?? [];
        return dtos.map(mapDtoToReportDefinition);
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
        const dto = await apiClient.post<{
          columns?: Array<Record<string, unknown>>;
          data?: Record<string, unknown>[];
          totalCount?: number;
        }>("reports/preview", {
          reportType: reportId,
          parameters,
        });
        return {
          columns: (dto.columns ?? []).map((column) => String(column.name ?? column.label ?? "")),
          data: dto.data ?? [],
          totalRows: dto.totalCount ?? 0,
        };
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
        const page = await apiClient.get<PageResponse<ReportExecutionDto>>(
          `reports/history${queryString ? `?${queryString}` : ""}`
        );
        return { ...page, content: page.content.map(mapExecutionDto) };
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
        const dto = await apiClient.get<ReportExecutionDto>(`reports/${instanceId}`);
        return mapExecutionDto(dto);
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
export const useScheduledReports = (options?: Partial<UseQueryOptions<PageResponse<ReportScheduleResponse>, ReportApiError>>) => {
  return useQuery<PageResponse<ReportScheduleResponse>, ReportApiError>({
    queryKey: ["reports", "scheduled"],
    queryFn: async () => {
      try {
        return await apiClient.get<PageResponse<ReportScheduleResponse>>("reports/schedule");
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
        const dto = await apiClient.post<ReportExecutionDto>("reports/generate", {
          reportType: request.reportId,
          parameters: request.parameters,
          outputFormat: request.format,
        });
        return mapExecutionDto(dto);
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
 * Schedule report using the backend ReportScheduleRequest contract.
 */
export const useScheduleReport = () => {
  const queryClient = useQueryClient();

  return useMutation<
    ReportScheduleResponse,
    ReportApiError,
    { reportId: number; schedule: ScheduleConfig; parameters: Record<string, unknown> }
  >({
    mutationFn: async (request) => {
      try {
        const { schedule } = request;
        const dateRangeByFrequency: Record<ScheduleConfig["frequency"], string> = {
          daily: "PREVIOUS_DAY",
          weekly: "PREVIOUS_WEEK",
          monthly: "PREVIOUS_MONTH",
          quarterly: "PREVIOUS_QUARTER",
          yearly: "PREVIOUS_YEAR",
        };
        return await apiClient.post<ReportScheduleResponse>("reports/schedule", {
          reportId: request.reportId,
          scheduleName: `Scheduled report ${request.reportId} - ${schedule.frequency}`,
          frequency: schedule.frequency.toUpperCase(),
          timeOfDay: schedule.time ?? "08:00",
          dayOfWeek: schedule.dayOfWeek,
          dayOfMonth: schedule.dayOfMonth,
          timezone: schedule.timezone,
          defaultParameters: request.parameters,
          dateRangeType: dateRangeByFrequency[schedule.frequency],
          emailRecipients: schedule.recipients,
          exportFormats: schedule.formats.map((format) => format === "Excel" ? "XLSX" : format),
        });
      } catch (error) {
        return handleApiError(error);
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

export interface ReportFavoriteDto {
  id: number;
  reportId?: number;
  reportCode?: string;
  pspId?: number;
  displayOrder?: number;
  createdAt?: string;
}

export const useReportFavorites = () => {
  return useQuery<ReportFavoriteDto[]>({
    queryKey: ["reports", "favorites"],
    queryFn: async () => apiClient.get<ReportFavoriteDto[]>("reports/favorites"),
  });
};

export const useAddReportFavorite = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: { reportCode: string; pspId?: number }) =>
      apiClient.post<ReportFavoriteDto>("reports/favorites", payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["reports", "favorites"] });
    },
  });
};

export const useRemoveReportFavorite = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (reportCode: string) => {
      await apiClient.delete(`reports/favorites/${encodeURIComponent(reportCode)}`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["reports", "favorites"] });
    },
  });
};
