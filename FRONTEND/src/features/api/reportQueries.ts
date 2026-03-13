/**
 * Reports Center API Hooks
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "../../lib/apiClient";
import type {
  ReportDefinition,
  ReportInstance,
  ScheduleConfig,
  ExportFormat
} from "./reportDefinitions";

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

// ==================== QUERIES ====================

// Get report preview data
export const useReportPreview = (reportId: string, parameters: Record<string, unknown> | null) => {
  return useQuery<ReportPreviewResponse>({
    queryKey: ["reports", "preview", reportId, parameters],
    queryFn: () => apiClient.post<ReportPreviewResponse>(`reports/preview`, { reportId, parameters }),
    enabled: !!reportId && !!parameters,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};

// Get report history
export const useReportHistory = (params?: ReportHistoryParams) => {
  const queryParams = params || {};
  const queryString = Object.entries(queryParams)
    .filter(([_, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${key}=${encodeURIComponent(String(value))}`)
    .join('&');

  return useQuery<PageResponse<ReportInstance>>({
    queryKey: ["reports", "history", queryParams],
    queryFn: () => apiClient.get<PageResponse<ReportInstance>>(`reports/history${queryString ? `?${queryString}` : ""}`),
  });
};

// Get report instance by ID
export const useReportInstance = (instanceId: string) => {
  return useQuery<ReportInstance>({
    queryKey: ["reports", "instance", instanceId],
    queryFn: () => apiClient.get<ReportInstance>(`reports/history/${instanceId}`),
    enabled: !!instanceId,
  });
};

// Get scheduled reports
export const useScheduledReports = () => {
  return useQuery<ReportInstance[]>({
    queryKey: ["reports", "scheduled"],
    queryFn: () => apiClient.get<ReportInstance[]>("reports/scheduled"),
  });
};

// Get report chart data
export const useReportChartData = (reportId: string, parameters: Record<string, unknown> | null, chartType: string) => {
  return useQuery<Record<string, unknown>>({
    queryKey: ["reports", "chart", reportId, chartType, parameters],
    queryFn: () => apiClient.post<Record<string, unknown>>(`reports/chart`, { reportId, parameters, chartType }),
    enabled: !!reportId && !!parameters && !!chartType,
    staleTime: 5 * 60 * 1000,
  });
};

// ==================== MUTATIONS ====================

// Generate report
export const useGenerateReport = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: GenerateReportRequest) => 
      apiClient.post<ReportInstance>("reports/generate", request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["reports", "history"] });
      queryClient.invalidateQueries({ queryKey: ["reports", "scheduled"] });
    },
  });
};

// Schedule report
export const useScheduleReport = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: { reportId: string; schedule: ScheduleConfig; parameters: Record<string, unknown> }) => 
      apiClient.post<ReportInstance>("reports/schedule", request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["reports", "scheduled"] });
    },
  });
};

// Cancel scheduled report
export const useCancelScheduledReport = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (scheduleId: string) => 
      apiClient.delete(`reports/schedule/${scheduleId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["reports", "scheduled"] });
    },
  });
};

// Download report
export const useDownloadReport = () => {
  return useMutation({
    mutationFn: async ({ instanceId, format }: { instanceId: string; format: ExportFormat }) => {
      const response = await fetch(
        `${import.meta.env.VITE_API_URL || "/api"}/reports/download/${instanceId}?format=${format}`,
        {
          headers: {
            "Authorization": `Bearer ${localStorage.getItem("token") || ""}`,
          },
        }
      );
      
      if (!response.ok) {
        throw new Error("Failed to download report");
      }
      
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `report-${instanceId}.${format.toLowerCase()}`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    },
  });
};

// Delete report instance
export const useDeleteReportInstance = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (instanceId: string) => 
      apiClient.delete(`reports/history/${instanceId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["reports", "history"] });
    },
  });
};

// Page Response type (shared)
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
