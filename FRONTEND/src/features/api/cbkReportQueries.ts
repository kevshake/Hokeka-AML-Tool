import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "../../lib/apiClient";

export type CbkPeriod = "daily" | "weekly" | "monthly" | "quarterly" | "semi-annual" | "annual";
export type CbkSubmissionStatus = "submitted" | "pending" | "failed";

export interface CbkReportParams {
  period: CbkPeriod;
  from: string;
  to: string;
}

export interface CbkReportRow {
  id: string;
  reportType: string;
  period: CbkPeriod;
  from: string;
  to: string;
  submissionStatus: CbkSubmissionStatus;
  submittedAt?: string;
  referenceNumber?: string;
  errorMessage?: string;
}

export interface CbkReportResponse {
  content: CbkReportRow[];
  totalElements: number;
  totalPages: number;
}

export interface CbkSubmitRequest {
  reportId: string;
  period: CbkPeriod;
  from: string;
  to: string;
  parameters?: Record<string, unknown>;
}

export interface CbkSubmitResponse {
  referenceNumber: string;
  status: CbkSubmissionStatus;
  submittedAt: string;
  message: string;
}

export const useCbkReports = (params: CbkReportParams, enabled = true) => {
  return useQuery<CbkReportResponse>({
    queryKey: ["cbk", "reports", params],
    queryFn: () =>
      apiClient.get<CbkReportResponse>(
        `compliance/cbk/reports?period=${params.period}&from=${params.from}&to=${params.to}`
      ),
    enabled,
    staleTime: 1000 * 60 * 5,
  });
};

export const useCbkSubmit = () => {
  const queryClient = useQueryClient();
  return useMutation<CbkSubmitResponse, Error, CbkSubmitRequest>({
   mutationFn: (req) =>
      apiClient.post<CbkSubmitResponse>("compliance/cbk/reports/submit", req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["cbk", "reports"] });
    }, 
  });
};
