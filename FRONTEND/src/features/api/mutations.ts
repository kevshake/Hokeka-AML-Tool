import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "../../lib/apiClient";
import type { AmlRule, VelocityRule, RiskThreshold } from "../../types/rules";
import type { Priority } from "../../types";

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
  merchantId: string;
  businessName: string;
  mcc?: string;
  kycStatus?: string;
  contractStatus?: string;
}

export const useCreateMerchant = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (req: CreateMerchantRequest) =>
      apiClient.post("merchants", req),
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
  creatorUserId: number;
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
