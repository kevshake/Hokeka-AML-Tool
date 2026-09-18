/** Human labels for billable service SKUs (seeded in V225 + platform defaults). */
export interface BillingServiceSku {
  code: string;
  label: string;
  description: string;
  /** Default platform rate (USD / request) when no override exists. */
  defaultRateUsd: number;
  glow: "gold" | "teal" | "amber" | "purple";
}

export const SCREENING_SKUS: BillingServiceSku[] = [
  {
    code: "WALLET_SCREENING",
    label: "Wallet screening",
    description: "Virtual-asset wallet address screening per request",
    defaultRateUsd: 0.08,
    glow: "teal",
  },
  {
    code: "VASP_SCREENING",
    label: "VASP screening",
    description: "Virtual asset service provider due diligence checks",
    defaultRateUsd: 2.5,
    glow: "gold",
  },
  {
    code: "EDD_SCREENING",
    label: "EDD screening",
    description: "Enhanced due diligence screening for high-risk merchants",
    defaultRateUsd: 15,
    glow: "amber",
  },
  {
    code: "TRAVEL_RULE_TRANSFER",
    label: "Travel Rule transfer",
    description: "Travel Rule message processing for crypto transfers",
    defaultRateUsd: 0.12,
    glow: "purple",
  },
];

const SKU_BY_CODE = new Map(SCREENING_SKUS.map((s) => [s.code, s]));

/** Resolve a machine service type to a readable label. */
export function serviceTypeLabel(code: string | null | undefined): string {
  const key = (code ?? "").trim();
  if (!key) return "Unknown service";
  return SKU_BY_CODE.get(key)?.label ?? key.replaceAll("_", " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

export function serviceTypeDescription(code: string | null | undefined): string | undefined {
  return SKU_BY_CODE.get((code ?? "").trim())?.description;
}

export const SIGNAL_MODE_OPTIONS = [
  {
    value: "REPORTING_ONLY",
    label: "Reporting only",
    description: "Signals are logged for analytics — they do not affect decisions.",
  },
  {
    value: "INFLUENCE_DECISION",
    label: "Influence decision",
    description: "Signal taxonomy can adjust risk scores and engine outcomes.",
  },
  {
    value: "ALERT_ROUTING",
    label: "Alert routing",
    description: "Signals drive alert creation and case routing rules.",
  },
] as const;

export const INVITE_ROLE_OPTIONS = [
  { value: "PSP_ADMIN", label: "PSP admin", description: "Full PSP tenant administration" },
  { value: "PSP_USER", label: "PSP user", description: "Standard PSP operator access" },
  { value: "MERCHANT_ONBOARD", label: "Merchant onboarding", description: "Invite-only merchant onboarding flow" },
] as const;
