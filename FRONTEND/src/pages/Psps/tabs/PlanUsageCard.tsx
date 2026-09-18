import { useQuery } from "@tanstack/react-query";
import {
  Alert,
  Box,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  LinearProgress,
  Typography,
} from "@mui/material";
import { apiClient } from "../../../lib/apiClient";

/**
 * What the authenticated tenant's plan actually grants, straight from the backend entitlement
 * service (GET /entitlements/me). Scope is implicit — it only ever reports the caller's own PSP.
 */
export interface Entitlements {
  planCode: string;
  features: string[];
  maxChecksPerMonth: number | null;
  usedThisMonth: number;
  unlimited: boolean;
  remaining: number | null;
}

/** Human labels for the plan feature flags the pricing tiers grant. */
const FEATURE_LABELS: Record<string, string> = {
  sandbox: "Sandbox",
  basic_kyc: "Basic KYC",
  email_support: "Email support",
  case_management: "Case management",
  sar_generation: "SAR generation",
  custom_rules: "Custom rules",
  white_label: "White label",
  on_prem_option: "On-prem edge",
  priority_support: "Priority support",
  dedicated_support: "Dedicated support",
};

function labelFor(feature: string): string {
  return FEATURE_LABELS[feature] ?? feature.replace(/_/g, " ");
}

/**
 * Self-service plan + consumption panel. Surfaces the monthly quota and how much of it the tenant
 * has used — previously both were enforced server-side but invisible in the UI, so a PSP could be
 * throttled with no way to see why.
 */
export default function PlanUsageCard() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["entitlements", "me"],
    queryFn: () => apiClient.get<Entitlements>("entitlements/me"),
  });

  if (isLoading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", py: 3 }}>
        <CircularProgress size={24} />
      </Box>
    );
  }

  // Never render an error as an affirmative "no usage" state — say the fetch failed.
  if (isError || !data) {
    return (
      <Alert severity="error" sx={{ mb: 3 }}>
        Could not load your plan and usage. Please refresh; if it persists, contact support.
      </Alert>
    );
  }

  const limit = data.maxChecksPerMonth ?? 0;
  const used = data.usedThisMonth ?? 0;
  const pct = data.unlimited || limit <= 0 ? 0 : Math.min(100, (used / limit) * 100);
  const nearLimit = !data.unlimited && pct >= 80;
  const atLimit = !data.unlimited && data.remaining !== null && data.remaining <= 0;

  return (
    <Card variant="outlined" sx={{ mb: 3 }}>
      <CardContent>
        <Box sx={{ display: "flex", alignItems: "center", gap: 1.5, mb: 2, flexWrap: "wrap" }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
            Plan &amp; usage
          </Typography>
          <Chip size="small" label={data.planCode} />
          {data.unlimited && <Chip size="small" variant="outlined" label="Unlimited checks" />}
        </Box>

        {/* Consumption against the plan's monthly quota */}
        {!data.unlimited && limit > 0 ? (
          <Box sx={{ mb: 2 }}>
            <Box sx={{ display: "flex", justifyContent: "space-between", mb: 0.75 }}>
              <Typography variant="body2">
                {used.toLocaleString()} of {limit.toLocaleString()} checks this month
              </Typography>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                {data.remaining !== null ? `${data.remaining.toLocaleString()} left` : ""}
              </Typography>
            </Box>
            <LinearProgress
              variant="determinate"
              value={pct}
              color={atLimit ? "error" : nearLimit ? "warning" : "primary"}
              sx={{ height: 8, borderRadius: 1 }}
            />
          </Box>
        ) : (
          <Typography variant="body2" sx={{ mb: 2 }}>
            {used.toLocaleString()} checks this month — no monthly cap on this plan.
          </Typography>
        )}

        {atLimit && (
          <Alert severity="error" sx={{ mb: 2 }}>
            You have reached your monthly quota. Further metered API calls are rejected until the next
            billing period. Upgrade your plan to continue.
          </Alert>
        )}
        {!atLimit && nearLimit && (
          <Alert severity="warning" sx={{ mb: 2 }}>
            You have used over 80% of this month&apos;s quota.
          </Alert>
        )}

        {/* What the plan entitles the tenant to */}
        <Typography variant="body2" sx={{ fontWeight: 600, mb: 0.75 }}>
          Included in your plan
        </Typography>
        {data.features.length > 0 ? (
          <Box sx={{ display: "flex", gap: 0.75, flexWrap: "wrap" }}>
            {data.features.map((f) => (
              <Chip key={f} size="small" variant="outlined" label={labelFor(f)} />
            ))}
          </Box>
        ) : (
          <Typography variant="body2" color="text.secondary">
            No plan features are recorded for this tenant yet.
          </Typography>
        )}
      </CardContent>
    </Card>
  );
}
