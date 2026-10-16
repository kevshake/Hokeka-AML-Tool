import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "../../lib/apiClient";

/** Settings governed by maker-checker (backend PspDataSettingChange.Setting). */
export type DataSetting = "RETENTION_YEARS" | "SUPPORT_ACCESS" | "CROSS_PSP_OPT_IN";

/** Change request lifecycle (backend PspDataSettingChange.Status). */
export type DataSettingChangeStatus = "PENDING_APPROVAL" | "APPROVED" | "APPLIED" | "REJECTED" | "SUPERSEDED";

/** One maker-checker change request (GET /psps/{id}/data-settings/changes). */
export interface DataSettingChange {
  id: number;
  setting: DataSetting;
  oldValue: string;
  newValue: string;
  status: DataSettingChangeStatus;
  reason: string | null;
  requestedBy: string;
  requestedAt: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
  reviewReason: string | null;
  effectiveAt: string | null;
  appliedAt: string | null;
}

/** An approved retention reduction that is not yet in force. */
export interface PendingRetention {
  years: number;
  approvedAt: string;
  effectiveAt: string;
}

/** GET /psps/{id}/data-retention: every data residency setting of the PSP. */
export interface DataSettings {
  pspId: number;
  retentionYears: number;
  retentionEffectiveSince: string;
  pendingRetention: PendingRetention | null;
  supportAccessEnabled: boolean;
  supportAccessChangedAt: string;
  crossPspOptIn: boolean;
  crossPspOptInChangedAt: string;
  minRetentionYears: number;
  maxRetentionYears: number;
  reductionDelayDays: number;
  openChanges: DataSettingChange[];
}

/** Query key root of a PSP's data settings; every mutation below invalidates it. */
export const dataSettingsKey = (pspId: number) => ["psps", pspId, "data-settings"] as const;

/** Reads the PSP's retention, support access and cross-PSP settings plus open change requests. */
export function useDataSettings(pspId: number | null | undefined) {
  return useQuery<DataSettings>({
    queryKey: dataSettingsKey(pspId ?? 0),
    queryFn: () => apiClient.get<DataSettings>(`psps/${pspId}/data-retention`),
    enabled: !!pspId && pspId > 0,
  });
}

/** Full change history of the PSP's data settings, newest first. */
export function useDataSettingChanges(pspId: number | null | undefined) {
  return useQuery<DataSettingChange[]>({
    queryKey: [...dataSettingsKey(pspId ?? 0), "changes"],
    queryFn: () => apiClient.get<DataSettingChange[]>(`psps/${pspId}/data-settings/changes`),
    enabled: !!pspId && pspId > 0,
  });
}

/** Invalidates the data-settings queries of a PSP after a change. */
function useInvalidateDataSettings(pspId: number) {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: dataSettingsKey(pspId) });
}

/** Proposes a new retention period (maker). Resolves with the pending change request. */
export function useProposeRetention(pspId: number) {
  const invalidate = useInvalidateDataSettings(pspId);
  return useMutation({
    mutationFn: (body: { retentionYears: number; reason: string }) =>
      apiClient.put<DataSettingChange>(`psps/${pspId}/data-retention`, body),
    onSuccess: invalidate,
  });
}

/** Proposes enabling, or immediately withdraws, Hokeka support access. */
export function useSetSupportAccess(pspId: number) {
  const invalidate = useInvalidateDataSettings(pspId);
  return useMutation({
    mutationFn: (body: { enabled: boolean; reason: string }) =>
      apiClient.put<DataSettingChange>(`psps/${pspId}/support-access`, body),
    onSuccess: invalidate,
  });
}

/** Proposes opting in, or immediately opts out of, cross-PSP intelligence. */
export function useSetCrossPspOptIn(pspId: number) {
  const invalidate = useInvalidateDataSettings(pspId);
  return useMutation({
    mutationFn: (body: { optIn: boolean; reason: string }) =>
      apiClient.put<DataSettingChange>(`psps/${pspId}/cross-psp-opt-in`, body),
    onSuccess: invalidate,
  });
}

/** Checker decision on a pending change request. */
export function useReviewDataSettingChange(pspId: number) {
  const invalidate = useInvalidateDataSettings(pspId);
  return useMutation({
    mutationFn: (args: { changeId: number; decision: "approve" | "reject"; reason: string }) =>
      apiClient.post<DataSettingChange>(
        `psps/${pspId}/data-settings/changes/${args.changeId}/${args.decision}`,
        { reason: args.reason },
      ),
    onSuccess: invalidate,
  });
}

/** The server's error message, or a fallback, for a failed mutation. */
export function errorMessage(error: unknown, fallback: string): string {
  if (error && typeof error === "object" && "message" in error) {
    const message = (error as { message?: unknown }).message;
    if (typeof message === "string" && message.trim() && message !== "An error occurred") {
      return message;
    }
  }
  return fallback;
}
