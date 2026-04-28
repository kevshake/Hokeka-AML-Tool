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
