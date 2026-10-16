import { useState } from "react";
import { Alert, Box, Button, CircularProgress, Divider, FormControlLabel, Switch, TextField, Typography } from "@mui/material";
import { LifeBuoy } from "lucide-react";
import GlassCard from "../../../components/Common/GlassCard";
import { useAuth } from "../../../contexts/AuthContext";
import {
  errorMessage,
  useDataSettings,
  useSetCrossPspOptIn,
  useSetSupportAccess,
} from "../../../features/api/dataResidency";
import { canProposeDataSettings, formatWhen } from "../../../lib/dataResidency";
import DataSettingChangesPanel from "./DataSettingChangesPanel";

interface Props {
  pspId: number;
}

type Toggle = "support" | "crossPsp";

/**
 * Settings → Support & Data Sharing: the PSP's consent for Hokeka support staff to view its
 * transaction data, and its opt-in to cross-PSP intelligence. Granting either needs a second
 * reviewer; withdrawing takes effect immediately.
 */
export default function SupportAccessTab({ pspId }: Props) {
  const { user } = useAuth();
  const { data, isLoading, isError } = useDataSettings(pspId);
  const setSupport = useSetSupportAccess(pspId);
  const setCrossPsp = useSetCrossPspOptIn(pspId);
  const [reasons, setReasons] = useState<Record<Toggle, string>>({ support: "", crossPsp: "" });
  const [feedback, setFeedback] = useState<{ severity: "success" | "error"; text: string } | null>(null);

  if (isLoading) {
    return (
      <GlassCard padding="md" glowVariant="gold">
        <CircularProgress size={24} />
      </GlassCard>
    );
  }
  if (isError || !data) {
    return <Alert severity="error">Support and data sharing settings could not be loaded.</Alert>;
  }

  const canPropose = canProposeDataSettings(user);
  const pending = (setting: "SUPPORT_ACCESS" | "CROSS_PSP_OPT_IN") =>
    data.openChanges.some((c) => c.setting === setting && c.status === "PENDING_APPROVAL");

  /** Grants (proposes) or withdraws (immediate) one consent. */
  const change = async (toggle: Toggle, value: boolean) => {
    setFeedback(null);
    const reason = reasons[toggle].trim();
    try {
      const result = toggle === "support"
        ? await setSupport.mutateAsync({ enabled: value, reason })
        : await setCrossPsp.mutateAsync({ optIn: value, reason });
      setReasons((r) => ({ ...r, [toggle]: "" }));
      setFeedback({
        severity: "success",
        text: result.status === "APPLIED"
          ? "Saved. The change is in force and is sent to your Edge."
          : "Change proposed. It takes effect once a second reviewer approves it.",
      });
    } catch (e) {
      setFeedback({ severity: "error", text: errorMessage(e, "The change could not be saved.") });
    }
  };

  /** One consent row: current state, reason field and the grant / withdraw button. */
  const renderToggle = (
    toggle: Toggle,
    title: string,
    description: string,
    enabled: boolean,
    changedAt: string,
    setting: "SUPPORT_ACCESS" | "CROSS_PSP_OPT_IN",
  ) => (
    <Box sx={{ mt: 2 }} data-testid={`toggle-${toggle}`}>
      <FormControlLabel
        control={<Switch checked={enabled} disabled inputProps={{ "aria-label": title }} />}
        label={<Typography variant="subtitle1">{title}: {enabled ? "on" : "off"}</Typography>}
      />
      <Typography variant="body2" color="text.secondary">{description}</Typography>
      <Typography variant="caption" color="text.secondary">Last changed {formatWhen(changedAt)}</Typography>
      {canPropose && (
        <Box sx={{ display: "flex", gap: 2, mt: 1.5, flexDirection: { xs: "column", sm: "row" } }}>
          <TextField
            size="small"
            label="Reason (recorded in the audit log)"
            value={reasons[toggle]}
            onChange={(e) => setReasons((r) => ({ ...r, [toggle]: e.target.value }))}
            fullWidth
            inputProps={{ maxLength: 2000 }}
          />
          <Button
            variant={enabled ? "outlined" : "contained"}
            color={enabled ? "error" : "primary"}
            disabled={!reasons[toggle].trim() || (!enabled && pending(setting)) || setSupport.isPending || setCrossPsp.isPending}
            onClick={() => change(toggle, !enabled)}
            sx={{ whiteSpace: "nowrap" }}
          >
            {enabled ? "Withdraw now" : pending(setting) ? "Awaiting approval" : "Propose turning on"}
          </Button>
        </Box>
      )}
    </Box>
  );

  return (
    <GlassCard padding="md" glowVariant="gold">
      <div className="mb-2 flex items-start gap-3">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-hairline bg-surface-2 text-gold">
          <LifeBuoy size={18} />
        </div>
        <div>
          <span className="hokeka-section-label">Data residency</span>
          <Typography variant="h6" sx={{ mt: 0.5, fontFamily: "var(--font-display)" }}>
            Support & data sharing
          </Typography>
        </div>
      </div>

      {renderToggle(
        "support",
        "Hokeka support access",
        "Allows Hokeka support staff to view your transaction data from your Edge to help with a ticket. Every view needs a stated reason and appears in your data access log. Your Edge enforces this setting too.",
        data.supportAccessEnabled,
        data.supportAccessChangedAt,
        "SUPPORT_ACCESS",
      )}

      <Divider sx={{ my: 3 }} />

      {renderToggle(
        "crossPsp",
        "Cross-PSP intelligence",
        "Shares and receives indicators drawn only from filed SARs: merchant ids and anonymised device tokens. No amounts, names, card data or your identity are shared, and no other PSP can see your data.",
        data.crossPspOptIn,
        data.crossPspOptInChangedAt,
        "CROSS_PSP_OPT_IN",
      )}

      {!canPropose && (
        <Alert severity="info" sx={{ mt: 3 }}>
          Only a PSP administrator can change these settings.
        </Alert>
      )}
      {feedback && (
        <Alert severity={feedback.severity} sx={{ mt: 2 }}>
          {feedback.text}
        </Alert>
      )}

      <DataSettingChangesPanel
        pspId={pspId}
        changes={data.openChanges}
        settings={["SUPPORT_ACCESS", "CROSS_PSP_OPT_IN"]}
      />
    </GlassCard>
  );
}
