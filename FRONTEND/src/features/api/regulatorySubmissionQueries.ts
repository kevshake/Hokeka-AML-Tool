import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "../../lib/apiClient";

export type RegulatorySubmissionStatus =
  | "DRAFT"
  | "PENDING_REVIEW"
  | "APPROVED"
  | "SUBMISSION_PENDING"
  | "FILED"
  | "REJECTED"
  | "AMENDED";

export interface RegulatorySubmission {
  id: number;
  submissionReference: string;
  reportId: number;
  reportName?: string;
  reportCode?: string;
  executionId?: number;
  regulatorCode: string;
  submissionType: string;
  jurisdiction: string;
  filingPeriodStart: string;
  filingPeriodEnd: string;
  filingDeadline?: string;
  status: RegulatorySubmissionStatus;
  preparedByName?: string;
  preparedAt?: string;
  reviewedByName?: string;
  reviewedAt?: string;
  approvedByName?: string;
  approvedAt?: string;
  filedByName?: string;
  filedAt?: string;
  regulatorReference?: string;
  rejectionReason?: string;
  pspId?: number;
  isLateFiling?: boolean;
  daysUntilDeadline?: number;
}

export interface RegulatorySubmissionPage {
  content: RegulatorySubmission[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface RegulatorySubmissionFilters {
  status?: string;
  regulator?: string;
  page?: number;
  size?: number;
}

export const useRegulatorySubmissions = (
  filters: RegulatorySubmissionFilters = {},
) => {
  const params = new URLSearchParams();
  params.set("page", String(filters.page ?? 0));
  params.set("size", String(filters.size ?? 25));
  params.set("sortBy", "createdAt");
  params.set("sortDirection", "DESC");
  if (filters.status) params.set("status", filters.status);
  if (filters.regulator) params.set("regulator", filters.regulator);
  return useQuery<RegulatorySubmissionPage>({
    queryKey: ["regulatory-submissions", filters],
    queryFn: () =>
      apiClient.get<RegulatorySubmissionPage>(
        `reports/submissions?${params.toString()}`,
      ),
  });
};

const useSubmissionMutation = (
  mutationFn: (variables: any) => Promise<RegulatorySubmission>,
) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["regulatory-submissions"] });
      queryClient.invalidateQueries({ queryKey: ["reports", "history"] });
    },
  });
};

export const usePrepareRegulatorySubmission = () =>
  useSubmissionMutation(
    ({ executionId, regulatoryBody }: { executionId: number; regulatoryBody: string }) =>
      apiClient.post<RegulatorySubmission>(
        `reports/executions/${executionId}/prepare`,
        { regulatoryBody },
      ),
  );

export const useSubmitRegulatoryReview = () =>
  useSubmissionMutation(({ id }: { id: number }) =>
    apiClient.put<RegulatorySubmission>(`reports/submissions/${id}/status`, {
      status: "PENDING_REVIEW",
    }),
  );

export const useApproveRegulatorySubmission = () =>
  useSubmissionMutation(({ id }: { id: number }) =>
    apiClient.post<RegulatorySubmission>(`reports/submissions/${id}/approve`),
  );

export const useFileRegulatorySubmission = () =>
  useSubmissionMutation(({ id }: { id: number }) =>
    apiClient.post<RegulatorySubmission>(`reports/submissions/${id}/file`),
  );

export const useRejectRegulatorySubmission = () =>
  useSubmissionMutation(({ id, reason }: { id: number; reason: string }) =>
    apiClient.post<RegulatorySubmission>(`reports/submissions/${id}/reject`, {
      reason,
    }),
  );
