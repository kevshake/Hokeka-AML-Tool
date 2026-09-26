import {
  Box,
  Button,
  CircularProgress,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  TextField,
  Typography,
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BookOpen, ExternalLink, KeyRound, Mail, Radio, Receipt, Shield } from "lucide-react";
import { OPERATOR_DOC_LINKS, operatorDocUrl } from "../../../lib/operatorDocs";
import { useMemo, useState, type ReactNode } from "react";
import GlassCard from "../../../components/Common/GlassCard";
import CopyOnceToken from "../../../components/Common/CopyOnceToken";
import TwBadge from "../../../components/Common/TwBadge";
import {
  INVITE_ROLE_OPTIONS,
  SCREENING_SKUS,
  SIGNAL_MODE_OPTIONS,
  serviceTypeLabel,
} from "../../../lib/billingServiceTypes";
import { apiClient } from "../../../lib/apiClient";

interface PspOption {
  id: number;
  code: string;
  name: string;
}

interface BillingRateRow {
  rateId: number;
  serviceType: string;
  baseRate: number;
  currency: string;
  isActive: boolean;
  description?: string;
}

interface PspApiKeyRow {
  id: number;
  keyPrefix: string;
  createdAt: string;
  rotatedAt?: string | null;
  revokedAt?: string | null;
}

interface IssuedApiKey {
  id: number;
  keyPrefix: string;
  plaintextKey: string;
}

function SectionHeader({
  eyebrow,
  title,
  description,
  icon,
}: {
  eyebrow: string;
  title: string;
  description?: string;
  icon: ReactNode;
}) {
  return (
    <div className="mb-4 flex items-start gap-3">
      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-hairline bg-surface-2 text-gold">
        {icon}
      </div>
      <div className="min-w-0">
        <span className="hokeka-section-label">{eyebrow}</span>
        <h3 className="mt-1 font-display text-base font-semibold tracking-tight text-ink">{title}</h3>
        {description ? (
          <p className="mt-0.5 text-sm leading-relaxed text-ink-muted">{description}</p>
        ) : null}
      </div>
    </div>
  );
}

const inputSx = {
  "& .MuiOutlinedInput-root": {
    borderRadius: "var(--radius)",
    backgroundColor: "var(--surface-1)",
    "& fieldset": { borderColor: "var(--line-control)" },
    "&:hover fieldset": { borderColor: "rgb(var(--brand-accent-rgb) / 0.5)" },
    "&.Mui-focused fieldset": { borderColor: "var(--brand-accent)" },
  },
};

export default function PlatformAdminTab() {
  const queryClient = useQueryClient();
  const [selectedPspId, setSelectedPspId] = useState<number | "">("");
  const [inviteRole, setInviteRole] = useState("PSP_ADMIN");
  const [issuedToken, setIssuedToken] = useState<string | null>(null);
  const [issuedApiKey, setIssuedApiKey] = useState<string | null>(null);
  const [signalMode, setSignalMode] = useState<string>("INFLUENCE_DECISION");
  const [rateServiceType, setRateServiceType] = useState("WALLET_SCREENING");
  const [rateAmount, setRateAmount] = useState("0.08");
  const [editingRateId, setEditingRateId] = useState<number | null>(null);
  const [editAmount, setEditAmount] = useState("");

  const selectedPsp = useMemo(
    () => (selectedPspId !== "" ? Number(selectedPspId) : null),
    [selectedPspId],
  );

  const { data: psps, isLoading: pspsLoading } = useQuery<PspOption[]>({
    queryKey: ["settings", "psps"],
    queryFn: () => apiClient.get<PspOption[]>("settings/psps"),
  });

  const { data: billingRates, isLoading: ratesLoading } = useQuery<BillingRateRow[]>({
    queryKey: ["admin", "billing-rates", selectedPspId],
    queryFn: () => apiClient.get<BillingRateRow[]>(`admin/psps/${selectedPspId}/billing-rates`),
    enabled: selectedPspId !== "",
  });

  const { data: apiKeys, isLoading: keysLoading } = useQuery<PspApiKeyRow[]>({
    queryKey: ["admin", "psp-api-keys", selectedPspId],
    queryFn: () => apiClient.get<PspApiKeyRow[]>(`admin/psp-api-keys?pspId=${selectedPspId}`),
    enabled: selectedPspId !== "",
  });

  const issueInvite = useMutation({
    mutationFn: () =>
      apiClient.post<{ token: string }>("admin/onboarding/invites", {
        pspId: selectedPspId,
        role: inviteRole,
        expiresAt: new Date(Date.now() + 7 * 86400000).toISOString().slice(0, 19),
      }),
    onSuccess: (data) => setIssuedToken(data.token),
  });

  const saveSignalMode = useMutation({
    mutationFn: () =>
      apiClient.put(`psps/${selectedPspId}/signal-settings`, { mode: signalMode }),
  });

  const createRate = useMutation({
    mutationFn: () =>
      apiClient.post(`admin/psps/${selectedPspId}/billing-rates`, {
        serviceType: rateServiceType,
        pricingModel: "PER_REQUEST",
        baseRate: Number(rateAmount),
        currency: "USD",
        isActive: true,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "billing-rates", selectedPspId] });
    },
  });

  const updateRate = useMutation({
    mutationFn: ({ rateId, baseRate }: { rateId: number; baseRate: number }) =>
      apiClient.put(`admin/psps/${selectedPspId}/billing-rates/${rateId}`, {
        baseRate,
        currency: "USD",
        isActive: true,
      }),
    onSuccess: () => {
      setEditingRateId(null);
      queryClient.invalidateQueries({ queryKey: ["admin", "billing-rates", selectedPspId] });
    },
  });

  const createApiKey = useMutation({
    mutationFn: () =>
      apiClient.post<IssuedApiKey>(`admin/psp-api-keys?pspId=${selectedPspId}`, {}),
    onSuccess: (data) => {
      setIssuedApiKey(data.plaintextKey);
      queryClient.invalidateQueries({ queryKey: ["admin", "psp-api-keys", selectedPspId] });
    },
  });

  const rotateApiKey = useMutation({
    mutationFn: (id: number) =>
      apiClient.post<IssuedApiKey>(`admin/psp-api-keys/${id}/rotate`, {}),
    onSuccess: (data) => {
      setIssuedApiKey(data.plaintextKey);
      queryClient.invalidateQueries({ queryKey: ["admin", "psp-api-keys", selectedPspId] });
    },
  });

  const revokeApiKey = useMutation({
    mutationFn: (id: number) => apiClient.delete(`admin/psp-api-keys/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "psp-api-keys", selectedPspId] });
    },
  });

  const activeRates = billingRates?.filter((r) => r.isActive) ?? [];
  const scoped = selectedPsp !== null;

  return (
    <div className="flex flex-col gap-5">
      {/* Sticky PSP scope picker */}
      <div className="sticky top-0 z-20 -mx-1 px-1 pb-1 pt-0.5 backdrop-blur-md">
        <GlassCard padding="md" glowVariant="burgundy" static>
          <SectionHeader
            eyebrow="Scope"
            title="Platform admin — PSP context"
            description="All actions below apply to the selected payment service provider."
            icon={<Shield size={18} />}
          />
          <FormControl fullWidth size="small" sx={inputSx} disabled={pspsLoading}>
            <InputLabel id="platform-admin-psp-label">Payment service provider</InputLabel>
            <Select
              labelId="platform-admin-psp-label"
              label="Payment service provider"
              value={selectedPspId}
              onChange={(e) => {
                setSelectedPspId(e.target.value as number);
                setIssuedToken(null);
                setIssuedApiKey(null);
                setEditingRateId(null);
              }}
            >
              {psps?.map((psp) => (
                <MenuItem key={psp.id} value={psp.id}>
                  {psp.name} ({psp.code})
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          {!scoped && (
            <p className="mt-3 text-sm text-ink-muted">Select a PSP to manage invites, signals, rates, and API keys.</p>
          )}
        </GlassCard>
      </div>

      {OPERATOR_DOC_LINKS.some((link) => operatorDocUrl(link.key)) && (
        <GlassCard padding="md" glowVariant="teal" static>
          <SectionHeader
            eyebrow="Documentation"
            title="Operator install guides"
            description="Deep links to the docs-site install series (operator build only)."
            icon={<BookOpen size={18} />}
          />
          <ul className="grid gap-2 sm:grid-cols-2">
            {OPERATOR_DOC_LINKS.map((link) => {
              const href = operatorDocUrl(link.key);
              if (!href) return null;
              return (
                <li key={link.key}>
                  <a
                    href={href}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex h-full flex-col rounded-lg border border-hairline bg-surface-1 px-3 py-2.5 transition-colors hover:border-glass-border-hover hover:bg-surface-2"
                  >
                    <span className="inline-flex items-center gap-1.5 text-sm font-semibold text-ink">
                      {link.label}
                      <ExternalLink size={14} className="text-gold" />
                    </span>
                    <span className="mt-0.5 text-xs text-ink-muted">{link.description}</span>
                  </a>
                </li>
              );
            })}
          </ul>
        </GlassCard>
      )}

      {!scoped ? null : (
        <>
          {/* Onboarding invites */}
          <GlassCard padding="md" glowVariant="gold">
            <SectionHeader
              eyebrow="Onboarding"
              title="Invite credentials"
              description="Issue invite-only credentials for PSP or merchant onboarding. Share the token once — it is not stored in plaintext."
              icon={<Mail size={18} />}
            />

            <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap", alignItems: "flex-end", mb: 2 }}>
              <TextField
                select
                label="Role"
                size="small"
                value={inviteRole}
                onChange={(e) => setInviteRole(e.target.value)}
                sx={{ minWidth: 220, ...inputSx }}
              >
                {INVITE_ROLE_OPTIONS.map((opt) => (
                  <MenuItem key={opt.value} value={opt.value}>
                    <Box>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>
                        {opt.label}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {opt.description}
                      </Typography>
                    </Box>
                  </MenuItem>
                ))}
              </TextField>
              <Button
                variant="contained"
                disabled={issueInvite.isPending}
                onClick={() => issueInvite.mutate()}
                sx={{
                  textTransform: "none",
                  borderRadius: "var(--radius)",
                  backgroundColor: "var(--brand-accent)",
                  color: "var(--brand-on-primary)",
                  "&:hover": { backgroundColor: "var(--gold-bright)" },
                }}
              >
                {issueInvite.isPending ? "Generating…" : "Generate invite"}
              </Button>
            </Box>

            {issuedToken ? (
              <CopyOnceToken
                token={issuedToken}
                title="Invite token — copy now"
                hint="This token is shown once and cannot be retrieved again after you leave this page."
                onDismiss={() => setIssuedToken(null)}
              />
            ) : null}
          </GlassCard>

          {/* Signal taxonomy */}
          <GlassCard padding="md" glowVariant="teal">
            <SectionHeader
              eyebrow="Signals"
              title="Signal taxonomy mode"
              description="Control how enriched signal taxonomy interacts with the rules engine for this PSP."
              icon={<Radio size={18} />}
            />

            <Box sx={{ display: "flex", gap: 2, alignItems: "flex-end", flexWrap: "wrap" }}>
              <TextField
                select
                label="Mode"
                size="small"
                value={signalMode}
                onChange={(e) => setSignalMode(e.target.value)}
                sx={{ minWidth: 280, ...inputSx }}
              >
                {SIGNAL_MODE_OPTIONS.map((mode) => (
                  <MenuItem key={mode.value} value={mode.value}>
                    <Box>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>
                        {mode.label}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {mode.description}
                      </Typography>
                    </Box>
                  </MenuItem>
                ))}
              </TextField>
              <Button
                variant="outlined"
                disabled={saveSignalMode.isPending}
                onClick={() => saveSignalMode.mutate()}
                sx={{
                  textTransform: "none",
                  borderRadius: "var(--radius)",
                  borderColor: "var(--line-control)",
                  color: "var(--brand-accent)",
                  "&:hover": { borderColor: "var(--brand-accent)", backgroundColor: "var(--surface-2)" },
                }}
              >
                {saveSignalMode.isPending ? "Saving…" : "Save signal mode"}
              </Button>
            </Box>
            {saveSignalMode.isSuccess && (
              <p className="mt-3 text-xs text-success">Signal mode saved for this PSP.</p>
            )}
          </GlassCard>

          {/* Billing rate overrides */}
          <GlassCard padding="md" glowVariant="amber">
            <SectionHeader
              eyebrow="Billing"
              title="Per-PSP rate overrides"
              description="Negotiated unit prices for screening SKUs. Overrides supersede platform defaults."
              icon={<Receipt size={18} />}
            />

            <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap", alignItems: "flex-end", mb: 3 }}>
              <TextField
                select
                label="Service"
                size="small"
                value={rateServiceType}
                onChange={(e) => {
                  const code = e.target.value;
                  setRateServiceType(code);
                  const sku = SCREENING_SKUS.find((s) => s.code === code);
                  if (sku) setRateAmount(String(sku.defaultRateUsd));
                }}
                sx={{ minWidth: 240, ...inputSx }}
              >
                {SCREENING_SKUS.map((sku) => (
                  <MenuItem key={sku.code} value={sku.code}>
                    {sku.label}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                label="USD / request"
                size="small"
                value={rateAmount}
                onChange={(e) => setRateAmount(e.target.value)}
                sx={{ width: 140, ...inputSx }}
              />
              <Button
                variant="contained"
                disabled={createRate.isPending}
                onClick={() => createRate.mutate()}
                sx={{
                  textTransform: "none",
                  borderRadius: "var(--radius)",
                  backgroundColor: "var(--brand-accent)",
                  color: "var(--brand-on-primary)",
                }}
              >
                Add override
              </Button>
            </Box>

            {ratesLoading ? (
              <Box sx={{ display: "flex", justifyContent: "center", py: 3 }}>
                <CircularProgress size={24} sx={{ color: "var(--brand-accent)" }} />
              </Box>
            ) : activeRates.length === 0 ? (
              <div className="hokeka-empty-state py-8">
                <p className="hokeka-empty-state__title">No overrides yet</p>
                <p className="hokeka-empty-state__body">Platform default rates apply until you add a PSP-specific price.</p>
              </div>
            ) : (
              <div className="grid gap-3 sm:grid-cols-2">
                {activeRates.map((rate) => {
                  const sku = SCREENING_SKUS.find((s) => s.code === rate.serviceType);
                  const isEditing = editingRateId === rate.rateId;
                  return (
                    <GlassCard
                      key={rate.rateId}
                      padding="sm"
                      glowVariant={sku?.glow ?? "charcoal"}
                      static
                      className="!p-4"
                    >
                      <div className="flex items-start justify-between gap-2">
                        <div>
                          <p className="font-display text-sm font-semibold text-ink">
                            {serviceTypeLabel(rate.serviceType)}
                          </p>
                          <p className="mt-0.5 text-xs text-ink-muted">
                            {sku?.description ?? rate.serviceType}
                          </p>
                        </div>
                        <TwBadge variant={rate.isActive ? "success" : "default"}>
                          {rate.isActive ? "Active" : "Inactive"}
                        </TwBadge>
                      </div>

                      <div className="mt-3 flex items-end gap-2">
                        {isEditing ? (
                          <>
                            <TextField
                              label="USD / request"
                              size="small"
                              value={editAmount}
                              onChange={(e) => setEditAmount(e.target.value)}
                              sx={{ flex: 1, ...inputSx }}
                            />
                            <Button
                              size="small"
                              variant="contained"
                              disabled={updateRate.isPending}
                              onClick={() =>
                                updateRate.mutate({
                                  rateId: rate.rateId,
                                  baseRate: Number(editAmount),
                                })
                              }
                              sx={{ textTransform: "none", borderRadius: "var(--radius)" }}
                            >
                              Save
                            </Button>
                            <Button
                              size="small"
                              onClick={() => setEditingRateId(null)}
                              sx={{ textTransform: "none" }}
                            >
                              Cancel
                            </Button>
                          </>
                        ) : (
                          <>
                            <p className="font-display text-2xl font-semibold tracking-tight text-gold">
                              {rate.currency} {Number(rate.baseRate).toFixed(4)}
                            </p>
                            <span className="mb-1 text-xs text-ink-muted">/ request</span>
                            <Button
                              size="small"
                              sx={{ ml: "auto", textTransform: "none", color: "var(--brand-accent)" }}
                              onClick={() => {
                                setEditingRateId(rate.rateId);
                                setEditAmount(String(rate.baseRate));
                              }}
                            >
                              Edit
                            </Button>
                          </>
                        )}
                      </div>
                    </GlassCard>
                  );
                })}
              </div>
            )}
          </GlassCard>

          {/* Platform API keys */}
          <GlassCard padding="md" glowVariant="purple">
            <SectionHeader
              eyebrow="API access"
              title="Platform API keys"
              description="Machine credentials for PSP integrations. Plaintext is shown only at creation or rotation."
              icon={<KeyRound size={18} />}
            />

            <Box sx={{ mb: 2 }}>
              <Button
                variant="contained"
                disabled={createApiKey.isPending}
                onClick={() => createApiKey.mutate()}
                sx={{
                  textTransform: "none",
                  borderRadius: "var(--radius)",
                  backgroundColor: "var(--brand-secondary)",
                  color: "var(--brand-on-secondary)",
                  "&:hover": { filter: "brightness(1.08)" },
                }}
              >
                {createApiKey.isPending ? "Creating…" : "Issue new API key"}
              </Button>
            </Box>

            {issuedApiKey ? (
              <CopyOnceToken
                token={issuedApiKey}
                title="API key — copy now"
                hint="Store this key in your secret manager. It cannot be shown again."
                onDismiss={() => setIssuedApiKey(null)}
                className="mb-4"
              />
            ) : null}

            {keysLoading ? (
              <Box sx={{ display: "flex", justifyContent: "center", py: 3 }}>
                <CircularProgress size={24} sx={{ color: "var(--brand-secondary)" }} />
              </Box>
            ) : !apiKeys?.length ? (
              <div className="hokeka-empty-state py-8">
                <p className="hokeka-empty-state__title">No API keys</p>
                <p className="hokeka-empty-state__body">Issue a key to enable programmatic access for this PSP.</p>
              </div>
            ) : (
              <div className="flex flex-col gap-2">
                {apiKeys.map((key) => {
                  const revoked = Boolean(key.revokedAt);
                  return (
                    <div
                      key={key.id}
                      className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-hairline bg-surface-1/80 px-4 py-3"
                    >
                      <div className="min-w-0">
                        <p className="font-mono text-sm text-ink">
                          {key.keyPrefix}
                          <span className="text-ink-muted">••••••••</span>
                        </p>
                        <p className="mt-0.5 text-xs text-ink-muted">
                          Created {new Date(key.createdAt).toLocaleString()}
                          {key.rotatedAt ? ` · Rotated ${new Date(key.rotatedAt).toLocaleString()}` : ""}
                        </p>
                      </div>
                      <div className="flex flex-wrap items-center gap-2">
                        <TwBadge variant={revoked ? "danger" : "success"}>
                          {revoked ? "Revoked" : "Active"}
                        </TwBadge>
                        {!revoked && (
                          <>
                            <Button
                              size="small"
                              variant="outlined"
                              disabled={rotateApiKey.isPending}
                              onClick={() => rotateApiKey.mutate(key.id)}
                              sx={{ textTransform: "none", borderRadius: "var(--radius)" }}
                            >
                              Rotate
                            </Button>
                            <Button
                              size="small"
                              color="error"
                              disabled={revokeApiKey.isPending}
                              onClick={() => revokeApiKey.mutate(key.id)}
                              sx={{ textTransform: "none" }}
                            >
                              Revoke
                            </Button>
                          </>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </GlassCard>
        </>
      )}
    </div>
  );
}
