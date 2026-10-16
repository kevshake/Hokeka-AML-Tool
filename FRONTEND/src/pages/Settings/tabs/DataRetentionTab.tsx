import { useEffect, useState } from "react";
import { Alert, Box, Button, CircularProgress, Grid, TextField, Typography } from "@mui/material";
import { Archive } from "lucide-react";
import GlassCard from "../../../components/Common/GlassCard";
import { useAuth } from "../../../contexts/AuthContext";
import { errorMessage, useDataSettings, useProposeRetention } from "../../../features/api/dataResidency";
import { canProposeDataSettings, formatWhen, retentionChangeNotice, retentionError } from "../../../lib/dataResidency";
import DataSettingChangesPanel from "./DataSettingChangesPanel";

interface Props {
  pspId: number;
}

/**
 * Settings → Data Retention. Shows the retention period in force (5–25 years, enforced on the
 * PSP's Edge and for cloud decision records), any approved shortening that is waiting out its
 * 30-day delay, and lets a PSP admin propose a new period for a second reviewer to approve.
 */
export default function DataRetentionTab({ pspId }: Props) {
  const { user } = useAuth();
  const { data, isLoading, isError } = useDataSettings(pspId);
  const propose = useProposeRetention(pspId);
  const [years, setYears] = useState<string>("");
  const [reason, setReason] = useState("");
  const [feedback, setFeedback] = useState<{ severity: "success" | "error"; text: string } | null>(null);

  useEffect(() => {
    if (data && years === "") {
      setYears(String(data.retentionYears));
    }
  }, [data, years]);

  if (isLoading) {
    return (
      <GlassCard padding="md" glowVariant="teal">
        <CircularProgress size={24} />
      </GlassCard>
    );
  }
  if (isError || !data) {
    return <Alert severity="error">Data retention settings could not be loaded.</Alert>;
  }

  const requested = Number(years);
  const canPropose = canProposeDataSettings(user);
  const reductionWaiting = !!data.pendingRetention;
  const validation = years === "" ? null
    : retentionError(requested, data.retentionYears, data.minRetentionYears, data.maxRetentionYears, reductionWaiting);
  const awaitingApproval = data.openChanges.some(
    (c) => c.setting === "RETENTION_YEARS" && c.status === "PENDING_APPROVAL",
  );

  /** Sends the proposal; a second reviewer must approve it before anything changes. */
  const submit = async () => {
    setFeedback(null);
    try {
      await propose.mutateAsync({ retentionYears: requested, reason: reason.trim() });
      setReason("");
      setFeedback({ severity: "success", text: "Change proposed. It takes effect once a second reviewer approves it." });
    } catch (e) {
      setFeedback({ severity: "error", text: errorMessage(e, "The change could not be proposed.") });
    }
  };

  return (
    <GlassCard padding="md" glowVariant="teal">
      <div className="mb-4 flex items-start gap-3">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-hairline bg-surface-2 text-teal">
          <Archive size={18} />
        </div>
        <div>
          <span className="hokeka-section-label">Data residency</span>
          <Typography variant="h6" sx={{ mt: 0.5, fontFamily: "var(--font-display)" }}>
            Data retention
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            Transaction records stay on your Edge for this period and are then deleted automatically.
            Decision records, alerts and cases in the Console follow the same period. The minimum is{" "}
            {data.minRetentionYears} years (FATF Recommendation 11); records still needed by an open alert,
            case or unfiled report are kept until that work is closed.
          </Typography>
        </div>
      </div>

      <Grid container spacing={2}>
        <Grid item xs={12} sm={6}>
          <Typography variant="overline" color="text.secondary">In force</Typography>
          <Typography variant="h4" data-testid="retention-in-force">{data.retentionYears} years</Typography>
          <Typography variant="body2" color="text.secondary">since {formatWhen(data.retentionEffectiveSince)}</Typography>
        </Grid>
        {data.pendingRetention && (
          <Grid item xs={12} sm={6}>
            <Alert severity="warning" data-testid="retention-pending">
              Shortening to {data.pendingRetention.years} years was approved on{" "}
              {formatWhen(data.pendingRetention.approvedAt)} and takes effect on{" "}
              {formatWhen(data.pendingRetention.effectiveAt)}.
            </Alert>
          </Grid>
        )}
      </Grid>

      {canPropose ? (
        <Box sx={{ mt: 3 }}>
          <span className="hokeka-section-label">Propose a new period</span>
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid item xs={12} sm={3}>
              <TextField
                label="Years"
                type="number"
                value={years}
                onChange={(e) => setYears(e.target.value)}
                inputProps={{ min: data.minRetentionYears, max: data.maxRetentionYears, step: 1 }}
                error={!!validation}
                helperText={validation ?? " "}
                fullWidth
              />
            </Grid>
            <Grid item xs={12} sm={9}>
              <TextField
                label="Reason (recorded in the audit log)"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                fullWidth
                inputProps={{ maxLength: 2000 }}
              />
            </Grid>
          </Grid>
          {!validation && years !== "" && (
            <Alert severity={requested < data.retentionYears ? "warning" : "info"} sx={{ mt: 1 }}>
              {retentionChangeNotice(requested, data.retentionYears, data.reductionDelayDays)}
            </Alert>
          )}
          <Box sx={{ display: "flex", justifyContent: "flex-end", mt: 2 }}>
            <Button
              variant="contained"
              onClick={submit}
              disabled={!!validation || years === "" || !reason.trim() || awaitingApproval || propose.isPending}
            >
              {awaitingApproval ? "A change is awaiting approval" : "Propose change"}
            </Button>
          </Box>
        </Box>
      ) : (
        <Alert severity="info" sx={{ mt: 3 }}>
          Only a PSP administrator can propose a change to data retention.
        </Alert>
      )}

      {feedback && (
        <Alert severity={feedback.severity} sx={{ mt: 2 }}>
          {feedback.text}
        </Alert>
      )}

      <DataSettingChangesPanel pspId={pspId} changes={data.openChanges} settings={["RETENTION_YEARS"]} />
    </GlassCard>
  );
}
