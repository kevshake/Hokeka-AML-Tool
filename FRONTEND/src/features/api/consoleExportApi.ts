import { apiClient } from "../../lib/apiClient";

export type ConsoleExportFormat = "CSV" | "PDF" | "Excel";

export type ConsoleListViewType =
  | "ALERTS"
  | "CASES"
  | "TRANSACTIONS"
  | "MERCHANTS"
  | "AUDIT_LOGS"
  | "SARS"
  | "CHARGEBACKS"
  | "SCREENING_ACTIVITY"
  | "USERS"
  | "RULES"
  | "RECORD_DETAIL"
  | "CUSTOMERS"
  | "EDGE_NODES"
  | "PSPS"
  | "KYC_DOCUMENTS"
  | "ASSET_ACCOUNTS"
  | "MESSAGES"
  | "RUNTIME_ERRORS"
  | "REGULATORY_SUBMISSIONS";

export interface ConsoleExportRequest {
  viewType: ConsoleListViewType;
  format: ConsoleExportFormat;
  filters?: Record<string, string>;
  includeCorrelated?: boolean;
}

export interface ConsoleExportJobStatus {
  jobUuid: string;
  status: string;
  rowCount: number;
  fileName?: string;
  errorMessage?: string;
}

/** Starts a Console list export; returns a Blob for sync responses or job metadata for async. */
export async function startConsoleListExport(
  request: ConsoleExportRequest,
): Promise<{ kind: "blob"; blob: Blob; filename: string } | { kind: "job"; job: ConsoleExportJobStatus }> {
  const response = await fetch("/api/v1/console/exports", {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json", Accept: "*/*" },
    body: JSON.stringify({
      viewType: request.viewType,
      format: request.format,
      filters: request.filters ?? {},
      includeCorrelated: request.includeCorrelated ?? false,
    }),
  });

  if (response.status === 202) {
    const job = (await response.json()) as ConsoleExportJobStatus;
    return { kind: "job", job };
  }

  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || "Export failed");
  }

  const blob = await response.blob();
  const disposition = response.headers.get("Content-Disposition") || "";
  const match = disposition.match(/filename="([^"]+)"/);
  const filename = match?.[1] ?? `${request.viewType.toLowerCase()}-export`;
  return { kind: "blob", blob, filename };
}

/** Polls async export job status until the artifact is ready or the job fails. */
export async function pollConsoleExportJob(jobUuid: string): Promise<ConsoleExportJobStatus> {
  return apiClient.get<ConsoleExportJobStatus>(`console/exports/jobs/${jobUuid}`);
}

/** Downloads a completed async export artifact as a Blob. */
export async function downloadConsoleExportJob(jobUuid: string): Promise<Blob> {
  const response = await fetch(`/api/v1/console/exports/jobs/${jobUuid}/download`, {
    credentials: "include",
  });
  if (!response.ok) {
    throw new Error("Export download failed");
  }
  return response.blob();
}

/** Triggers a browser download for an export Blob with a stable filename. */
export function saveExportBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}
