import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "../../lib/apiClient";
import type { AmlRule, VelocityRule, RiskThreshold, RuleVersion } from "../../types/rules";
import type { Priority } from "../../types";
import type { SubscriptionRequest } from "../../types/billing";
import type { MarketOrder, MarketSignalStatus, MarketSurveillanceSignal, SurveillanceResult } from "../../types/marketSurveillance";
import type { MobileMoneyAssessment } from "../../types/mobileMoney";
import type { CryptoWalletProfile, RegulatorGrant, TravelRulePolicy, TravelRuleTransfer, VaspEntry, WalletScreeningRecord } from "../../types/virtualAssets";

// Case Mutations
export interface CreateCaseRequest {
  caseReference: string;
  description: string;
  priority: Priority;
  creatorUserId?: number;
}

export const useCreateCase = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (req: CreateCaseRequest) =>
      apiClient.post("compliance/cases/workflow/create", req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["cases"] });
    },
  });
};

// ---- AI Rule Generation ----
// Calls POST /rules/generate. Returns a *preview* RuleDefinition that the operator
// must review and explicitly save via useCreateAmlRule. Does NOT auto-persist.
export interface GeneratedRulePreview {
  id?: number;
  name: string;
  description?: string;
  ruleType: "SPEL" | "DROOLS_DRL" | "JAVA_BEAN";
  ruleExpression: string;
  action?: "BLOCK" | "HOLD" | "ALERT" | "ALLOW";
  score?: number;
  priority?: number;
  enabled?: boolean;
  // Backend may include these depending on the model output
  ruleJson?: string;
  drlContent?: string;
}

export type GenerateRuleErrorKind = "not_configured" | "ai_failed" | "bad_request" | "unknown";

export interface GenerateRuleError extends Error {
  kind: GenerateRuleErrorKind;
  details?: string;
  hint?: string;
  status?: number;
}

export const useGenerateRule = () => {
  return useMutation<GeneratedRulePreview, GenerateRuleError, { prompt: string }>({
    mutationFn: async ({ prompt }) => {
      try {
        // apiClient.post throws on non-2xx with the parsed error body attached.
        return await apiClient.post<GeneratedRulePreview>("rules/generate", { prompt });
      } catch (e: any) {
        // apiClient throws the parsed ApiError-shaped body directly. Backend's /rules/generate
        // returns { error, hint? , details? } on failure — read both shapes.
        const status: number | undefined = e?.status;
        const msg: string = e?.error ?? e?.message ?? "Failed to generate rule";
        let kind: GenerateRuleErrorKind = "unknown";
        if (status === 503) kind = "not_configured";
        else if (status === 502) kind = "ai_failed";
        else if (status === 400) kind = "bad_request";
        const err = new Error(msg) as GenerateRuleError;
        err.kind = kind;
        err.status = status;
        err.details = e?.details;
        err.hint = e?.hint;
        throw err;
      }
    },
  });
};

// AML Rules Mutations
export const useCreateAmlRule = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (rule: AmlRule) => apiClient.post<AmlRule>("rules", rule),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["rules"] });
    },
  });
};

export const useUpdateAmlRule = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, rule }: { id: number; rule: AmlRule }) =>
      apiClient.put<AmlRule>(`rules/${id}`, rule),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ["rules"] });
      queryClient.invalidateQueries({ queryKey: ["rule", variables.id] });
    },
  });
};

export const useDeleteAmlRule = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => apiClient.delete(`rules/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["rules"] });
    },
  });
};

export const useEnableAmlRule = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => apiClient.post(`rules/${id}/enable`),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: ["rules"] });
      queryClient.invalidateQueries({ queryKey: ["rule", id] });
    },
  });
};

export const useDisableAmlRule = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => apiClient.post(`rules/${id}/disable`),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: ["rules"] });
      queryClient.invalidateQueries({ queryKey: ["rule", id] });
    },
  });
};

export interface ReviewRuleVersionRequest {
  versionId: number;
  reason: string;
  effectiveFrom?: string;
}

export const useApproveRuleVersion = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ versionId, reason, effectiveFrom }: ReviewRuleVersionRequest) =>
      apiClient.post<RuleVersion>(`rules/governance/versions/${versionId}/approve`, { reason, effectiveFrom }),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ["rules"] });
      queryClient.invalidateQueries({ queryKey: ["rule"] });
      queryClient.invalidateQueries({ queryKey: ["rules", "governance", "pending"] });
      queryClient.invalidateQueries({ queryKey: ["rule-version", variables.versionId] });
    },
  });
};

export const useRejectRuleVersion = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ versionId, reason }: ReviewRuleVersionRequest) =>
      apiClient.post<RuleVersion>(`rules/governance/versions/${versionId}/reject`, { reason }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["rules"] });
      queryClient.invalidateQueries({ queryKey: ["rules", "governance", "pending"] });
    },
  });
};

const invalidateMarketSurveillance = (queryClient: ReturnType<typeof useQueryClient>) => {
  queryClient.invalidateQueries({ queryKey: ["market-surveillance"] });
  queryClient.invalidateQueries({ queryKey: ["alerts"] });
};

export const usePlaceMarketOrder = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post<SurveillanceResult<MarketOrder>>("market-surveillance/orders", body),
    onSuccess: () => invalidateMarketSurveillance(queryClient),
  });
};

export const useRecordMarketExecution = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post<SurveillanceResult<Record<string, unknown>>>("market-surveillance/executions", body),
    onSuccess: () => invalidateMarketSurveillance(queryClient),
  });
};

export const useCancelMarketOrder = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ externalOrderId, reason }: { externalOrderId: string; reason: string }) =>
      apiClient.post<SurveillanceResult<MarketOrder>>(`market-surveillance/orders/${encodeURIComponent(externalOrderId)}/cancel`, { reason }),
    onSuccess: () => invalidateMarketSurveillance(queryClient),
  });
};

export const useReviewMarketSignal = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status, notes }: { id: number; status: MarketSignalStatus; notes: string }) =>
      apiClient.put<MarketSurveillanceSignal>(`market-surveillance/signals/${id}/review`, { status, notes }),
    onSuccess: () => invalidateMarketSurveillance(queryClient),
  });
};

export const useIngestMobileMoneyTransaction = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<MobileMoneyAssessment>("mobile-money/transactions", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["mobile-money"] });
      queryClient.invalidateQueries({ queryKey: ["multi-asset"] });
      queryClient.invalidateQueries({ queryKey: ["alerts"] });
    },
  });
};

const invalidateVirtualAssets = (queryClient: ReturnType<typeof useQueryClient>) => {
  queryClient.invalidateQueries({ queryKey: ["virtual-assets"] });
  queryClient.invalidateQueries({ queryKey: ["multi-asset"] });
  queryClient.invalidateQueries({ queryKey: ["alerts"] });
};
export const useSaveVasp = () => { const qc = useQueryClient(); return useMutation({
  mutationFn: ({ id, body }: { id?: number; body: Record<string, unknown> }) => id
    ? apiClient.put<VaspEntry>(`virtual-assets/vasps/${id}`, body)
    : apiClient.post<VaspEntry>("virtual-assets/vasps", body),
  onSuccess: () => invalidateVirtualAssets(qc),
}); };
export const useScreenVasp = () => { const qc = useQueryClient(); return useMutation({
  mutationFn: (id: number) => apiClient.post<VaspEntry>(`virtual-assets/vasps/${id}/screen`, {}),
  onSuccess: () => invalidateVirtualAssets(qc),
}); };
export const useRegisterCryptoWallet = () => { const qc = useQueryClient(); return useMutation({
  mutationFn: (body: Record<string, unknown>) => apiClient.post<CryptoWalletProfile>("virtual-assets/wallets", body),
  onSuccess: () => invalidateVirtualAssets(qc),
}); };
export const useScreenCryptoWallet = () => { const qc = useQueryClient(); return useMutation({
  mutationFn: ({ id, trigger, transactionId }: { id: number; trigger: string; transactionId?: number }) =>
    apiClient.post<WalletScreeningRecord>(`virtual-assets/wallets/${id}/screen?trigger=${trigger}${transactionId ? `&transactionId=${transactionId}` : ""}`, {}),
  onSuccess: () => invalidateVirtualAssets(qc),
}); };
export const useSaveTravelRulePolicy = () => { const qc = useQueryClient(); return useMutation({
  mutationFn: ({ id, body }: { id?: number; body: Record<string, unknown> }) => id
    ? apiClient.put<TravelRulePolicy>(`virtual-assets/travel-rule/policies/${id}`, body)
    : apiClient.post<TravelRulePolicy>("virtual-assets/travel-rule/policies", body),
  onSuccess: () => invalidateVirtualAssets(qc),
}); };
export const usePrepareTravelRuleTransfer = () => { const qc = useQueryClient(); return useMutation({
  mutationFn: (body: Record<string, unknown>) => apiClient.post<TravelRuleTransfer>("virtual-assets/travel-rule/transfers", body),
  onSuccess: () => invalidateVirtualAssets(qc),
}); };
export const useVerifyTravelRuleIdentity = () => { const qc = useQueryClient(); return useMutation({
  mutationFn: ({ id, ...body }: { id: number; party: string; verified: boolean; evidenceReference?: string }) =>
    apiClient.put<TravelRuleTransfer>(`virtual-assets/travel-rule/transfers/${id}/verify`, body),
  onSuccess: () => invalidateVirtualAssets(qc),
}); };
export const useTransmitTravelRule = () => { const qc = useQueryClient(); return useMutation({
  mutationFn: (id: number) => apiClient.post<TravelRuleTransfer>(`virtual-assets/travel-rule/transfers/${id}/transmit`, {}),
  onSuccess: () => invalidateVirtualAssets(qc),
}); };
export const useCreateRegulatorGrant = () => { const qc = useQueryClient(); return useMutation({
  mutationFn: (body: Record<string, unknown>) => apiClient.post<RegulatorGrant>("virtual-assets/regulator-grants", body),
  onSuccess: () => invalidateVirtualAssets(qc),
}); };
export const useRevokeRegulatorGrant = () => { const qc = useQueryClient(); return useMutation({
  mutationFn: (id: number) => apiClient.delete(`virtual-assets/regulator-grants/${id}`),
  onSuccess: () => invalidateVirtualAssets(qc),
}); };

// Velocity Rules Mutations
export const useCreateVelocityRule = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (rule: VelocityRule) => apiClient.post<VelocityRule>("limits/velocity-rules", rule),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["rules", "velocity"] });
    },
  });
};

export const useUpdateVelocityRule = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, rule }: { id: number; rule: VelocityRule }) =>
      apiClient.put<VelocityRule>(`limits/velocity-rules/${id}`, rule),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["rules", "velocity"] });
    },
  });
};

export const useDeleteVelocityRule = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => apiClient.delete(`limits/velocity-rules/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["rules", "velocity"] });
    },
  });
};

// Risk Thresholds Mutations
export const useCreateRiskThreshold = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (threshold: RiskThreshold) =>
      apiClient.post<RiskThreshold>("limits/risk-thresholds", threshold),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["rules", "risk-thresholds"] });
    },
  });
};

// Alert Mutations
export const useUpdateAlertStatus = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status }: { id: number; status: string }) =>
      apiClient.put(`alerts/${id}/status`, { status }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["alerts"] });
    },
  });
};

// Merchant Mutations
export interface CreateMerchantRequest {
  pspId: number;
  legalName: string;
  tradingName?: string;
  country: string;
  registrationNumber: string;
  taxId?: string;
  mcc: string;
  businessType?: string;
  expectedMonthlyVolume?: number;
  transactionChannel?: string;
  website?: string;
  contactEmail?: string;
  cbkSettlementAccountNumber?: string;
  cbkEconomicSectorCode?: string;
  beneficialOwners: Array<{
    fullName: string;
    dateOfBirth: string;
    nationality: string;
    countryOfResidence?: string;
    passportNumber?: string;
    nationalId?: string;
    ownershipPercentage: number;
  }>;
}

export const useCreateMerchant = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (req: CreateMerchantRequest) =>
      apiClient.post("merchants/onboard", req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["merchants"] });
    },
  });
};

// SAR Mutations
export interface CreateSarRequest {
  sarReference: string;
  narrative: string;
  suspiciousActivityType: string;
  jurisdiction?: string;
  sarType?: string;
  suspicionAroseAt: string;
}

export const useCreateSar = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (req: CreateSarRequest) =>
      apiClient.post("compliance/sar/workflow/create", req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["sar"] });
    },
  });
};

// Compliance Calendar Mutations
export interface CreateDeadlineRequest {
  title: string;
  description?: string;
  dueDate: string;
  deadlineType?: string;
}

export const useCreateDeadline = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (req: CreateDeadlineRequest) =>
      apiClient.post("compliance/calendar", req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["compliance", "calendar"] });
    },
  });
};

// ─────────────────────────────────────────────
// PSP CBK config update
// ─────────────────────────────────────────────

export interface UpdatePspCbkConfigRequest {
  pspId: number | string;
  cbkInstitutionCode?: string;
  cbkReportingEnabled?: boolean;
  cbkClientId?: string;
  cbkClientSecret?: string;
  // Platform-admin only — backend rejects PSP_ADMIN edits.
  cbkEnvironment?: "preprod" | "live";
  cbkAllowLive?: boolean;
}

export const useRegisterPsp = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("psps", body),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["psps"] }); },
  });
};

export const useUpdatePspStatus = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status }: { id: number; status: string }) =>
      apiClient.put(`psps/${id}/status`, { status }),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["psps"] }); },
  });
};

export const useDeletePsp = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => apiClient.delete(`psps/${id}`),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["psps"] }); },
  });
};

export const useUpdatePspCbkConfig = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ pspId, ...body }: UpdatePspCbkConfigRequest) =>
      apiClient.put(`psps/${pspId}/cbk-config`, body),
    onSuccess: (_data, { pspId }) => {
      queryClient.invalidateQueries({ queryKey: ["psp", pspId] });
      queryClient.invalidateQueries({ queryKey: ["psp", pspId, "cbk-config"] });
      queryClient.invalidateQueries({ queryKey: ["psps"] });
    },
  });
};

// ─────────────────────────────────────────────
// PSP child-entity CRUD mutations
// Each hook takes pspId (create/delete) or pspId+id (update) at call time.
// ─────────────────────────────────────────────

// Directors
export const useCreatePspDirector = (pspId: number | string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post(`psps/${pspId}/cbk/directors`, body),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["psp", pspId, "directors"] }); },
  });
};
export const useUpdatePspDirector = (pspId: number | string, id: number | string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.put(`psps/${pspId}/cbk/directors/${id}`, body),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["psp", pspId, "directors"] }); },
  });
};
export const useDeletePspDirector = (pspId: number | string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number | string) => apiClient.delete(`psps/${pspId}/cbk/directors/${id}`),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["psp", pspId, "directors"] }); },
  });
};

// Shareholders
export const useCreatePspShareholder = (pspId: number | string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post(`psps/${pspId}/cbk/shareholders`, body),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["psp", pspId, "shareholders"] }); },
  });
};
export const useUpdatePspShareholder = (pspId: number | string, id: number | string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.put(`psps/${pspId}/cbk/shareholders/${id}`, body),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["psp", pspId, "shareholders"] }); },
  });
};
export const useDeletePspShareholder = (pspId: number | string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number | string) => apiClient.delete(`psps/${pspId}/cbk/shareholders/${id}`),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["psp", pspId, "shareholders"] }); },
  });
};

// Trustees
export const useCreatePspTrustee = (pspId: number | string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post(`psps/${pspId}/cbk/trustees`, body),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["psp", pspId, "trustees"] }); },
  });
};
export const useUpdatePspTrustee = (pspId: number | string, id: number | string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.put(`psps/${pspId}/cbk/trustees/${id}`, body),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["psp", pspId, "trustees"] }); },
  });
};
export const useDeletePspTrustee = (pspId: number | string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number | string) => apiClient.delete(`psps/${pspId}/cbk/trustees/${id}`),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["psp", pspId, "trustees"] }); },
  });
};

// Senior Management
export const useCreatePspSeniorManagement = (pspId: number | string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post(`psps/${pspId}/cbk/senior-management`, body),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["psp", pspId, "senior-management"] }); },
  });
};
export const useUpdatePspSeniorManagement = (pspId: number | string, id: number | string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.put(`psps/${pspId}/cbk/senior-management/${id}`, body),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["psp", pspId, "senior-management"] }); },
  });
};
export const useDeletePspSeniorManagement = (pspId: number | string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number | string) => apiClient.delete(`psps/${pspId}/cbk/senior-management/${id}`),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["psp", pspId, "senior-management"] }); },
  });
};

// Products
export const useCreatePspProduct = (pspId: number | string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post(`psps/${pspId}/cbk/products`, body),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["psp", pspId, "products"] }); },
  });
};
export const useUpdatePspProduct = (pspId: number | string, id: number | string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.put(`psps/${pspId}/cbk/products/${id}`, body),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["psp", pspId, "products"] }); },
  });
};
export const useDeletePspProduct = (pspId: number | string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number | string) => apiClient.delete(`psps/${pspId}/cbk/products/${id}`),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["psp", pspId, "products"] }); },
  });
};

// Trust Accounts
export const useCreatePspTrustAccount = (pspId: number | string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post(`psps/${pspId}/cbk/trust-accounts`, body),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["psp", pspId, "trust-accounts"] }); },
  });
};
export const useUpdatePspTrustAccount = (pspId: number | string, id: number | string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.put(`psps/${pspId}/cbk/trust-accounts/${id}`, body),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["psp", pspId, "trust-accounts"] }); },
  });
};
export const useDeletePspTrustAccount = (pspId: number | string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number | string) => apiClient.delete(`psps/${pspId}/cbk/trust-accounts/${id}`),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["psp", pspId, "trust-accounts"] }); },
  });
};

// Tariffs
export const useCreatePspTariff = (pspId: number | string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post(`psps/${pspId}/cbk/tariffs`, body),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["psp", pspId, "tariffs"] }); },
  });
};
export const useUpdatePspTariff = (pspId: number | string, id: number | string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.put(`psps/${pspId}/cbk/tariffs/${id}`, body),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["psp", pspId, "tariffs"] }); },
  });
};
export const useDeletePspTariff = (pspId: number | string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number | string) => apiClient.delete(`psps/${pspId}/cbk/tariffs/${id}`),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["psp", pspId, "tariffs"] }); },
  });
};

// ─────────────────────────────────────────────
// CBK Submission replay
// ─────────────────────────────────────────────

export const useReplayCbkSubmission = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ endpointType, pspId }: { endpointType: string; pspId: number | string }) =>
      apiClient.post(`compliance/cbk/submissions/${endpointType}/run`, { pspId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["cbk-submissions"] });
    },
  });
};

// ─── Billing mutations ────────────────────────────────────────────────────────

export interface UpdateInvoiceStatusRequest {
  invoiceId: number;
  status: string;
  paymentReference?: string;
  paymentMethod?: string;
  paymentAmount?: number;
}

export const useUpdateInvoiceStatus = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ invoiceId, ...body }: UpdateInvoiceStatusRequest) =>
      apiClient.put(`billing/invoices/${invoiceId}/status`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['billing'] });
    },
  });
};

// W27-2 fix: POST /admin/psp-billing/{pspId}/invoice/generate existed with zero frontend
// callers -- invoices were only ever created by the scheduled job, so an admin had no way to
// issue an off-cycle invoice.
export const useGenerateInvoice = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ pspId, month }: { pspId: number; month?: string }) =>
      apiClient.post(`admin/psp-billing/${pspId}/invoice/generate${month ? `?month=${month}` : ""}`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['billing'] });
    },
  });
};

// W27-3 fix: POST /admin/psp-billing/{pspId}/notify existed with zero frontend callers -- admins
// had no way to resend an invoice email or trigger a dunning reminder from the console.
export const useSendBillingNotification = () => {
  return useMutation({
    mutationFn: ({ pspId, type, invoiceId }: { pspId: number; type: "INVOICE" | "REMINDER"; invoiceId?: number }) =>
      apiClient.post(`admin/psp-billing/${pspId}/notify`, {
        type,
        ...(invoiceId != null ? { invoiceId: String(invoiceId) } : {}),
      }),
  });
};

// W26-8 fix: PSP self-service webhook management, built over W36-2's WebhookSubscriptionController.
export const useCreateWebhookSubscription = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { callbackUrl: string; eventType: string }) =>
      apiClient.post("webhooks/subscribe", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["webhooks", "subscriptions"] });
    },
  });
};

export const useDeleteWebhookSubscription = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => apiClient.delete(`webhooks/subscriptions/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["webhooks", "subscriptions"] });
    },
  });
};

export const useCreateSubscription = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: SubscriptionRequest) => apiClient.post('subscriptions', body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['billing'] });
    },
  });
};

export const useUpdateSubscription = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...body }: { id: number } & SubscriptionRequest) =>
      apiClient.put(`subscriptions/${id}`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['billing'] });
    },
  });
};

export const useCancelSubscription = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => apiClient.delete(`subscriptions/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['billing'] });
    },
  });
};
