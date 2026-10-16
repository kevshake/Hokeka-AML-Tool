import { useState } from "react";
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import TwBadge from "../../../components/Common/TwBadge";
import { useAuth } from "../../../contexts/AuthContext";
import {
  errorMessage,
  useReviewDataSettingChange,
  type DataSetting,
  type DataSettingChange,
} from "../../../features/api/dataResidency";
import { canReviewChange, describeValue, formatWhen, settingLabel } from "../../../lib/dataResidency";

interface Props {
  pspId: number;
  changes: DataSettingChange[];
  /** Only changes of these settings are listed. */
  settings: DataSetting[];
}

/**
 * Open maker-checker requests (awaiting approval, or approved and waiting to take effect) with the
 * checker's approve / reject actions. The requester never sees the actions on their own request.
 */
export default function DataSettingChangesPanel({ pspId, changes, settings }: Props) {
  const { user } = useAuth();
  const review = useReviewDataSettingChange(pspId);
  const [pending, setPending] = useState<{ change: DataSettingChange; decision: "approve" | "reject" } | null>(null);
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);

  const visible = changes.filter((c) => settings.includes(c.setting));
  if (visible.length === 0) {
    return null;
  }

  /** Sends the checker decision with its justification. */
  const submit = async () => {
    if (!pending) return;
    setError(null);
    try {
      await review.mutateAsync({ changeId: pending.change.id, decision: pending.decision, reason: reason.trim() });
      setPending(null);
      setReason("");
    } catch (e) {
      setError(errorMessage(e, "The decision could not be saved."));
    }
  };

  return (
    <Box sx={{ mt: 3 }} data-testid="data-setting-changes">
      <span className="hokeka-section-label">Changes awaiting a decision</span>
      <Stack spacing={1.5} sx={{ mt: 1 }}>
        {visible.map((change) => (
          <Box
            key={change.id}
            sx={{ border: "1px solid var(--line-control)", borderRadius: "var(--radius)", p: 1.5 }}
          >
            <Stack direction={{ xs: "column", sm: "row" }} spacing={1} justifyContent="space-between">
              <Box>
                <Typography variant="subtitle2">
                  {settingLabel(change.setting)}: {describeValue(change.setting, change.oldValue)} →{" "}
                  {describeValue(change.setting, change.newValue)}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Proposed by {change.requestedBy} on {formatWhen(change.requestedAt)}
                  {change.reason ? ` — “${change.reason}”` : ""}
                </Typography>
                {change.status === "APPROVED" && (
                  <Typography variant="body2" color="text.secondary">
                    Approved by {change.reviewedBy}; takes effect {formatWhen(change.effectiveAt)}.
                  </Typography>
                )}
              </Box>
              <Stack direction="row" spacing={1} alignItems="center">
                <TwBadge variant={change.status === "APPROVED" ? "success" : "warning"}>
                  {change.status === "APPROVED" ? "Approved, not yet in force" : "Awaiting approval"}
                </TwBadge>
                {canReviewChange(user, change) && (
                  <>
                    <Button size="small" variant="contained" onClick={() => setPending({ change, decision: "approve" })}>
                      Approve
                    </Button>
                    <Button size="small" variant="outlined" color="error" onClick={() => setPending({ change, decision: "reject" })}>
                      Reject
                    </Button>
                  </>
                )}
              </Stack>
            </Stack>
          </Box>
        ))}
      </Stack>

      <Dialog open={!!pending} onClose={() => setPending(null)} fullWidth maxWidth="sm">
        <DialogTitle>{pending?.decision === "approve" ? "Approve change" : "Reject change"}</DialogTitle>
        <DialogContent>
          {pending && (
            <Typography variant="body2" sx={{ mb: 2 }}>
              {settingLabel(pending.change.setting)}: {describeValue(pending.change.setting, pending.change.oldValue)} →{" "}
              {describeValue(pending.change.setting, pending.change.newValue)}, proposed by {pending.change.requestedBy}.
            </Typography>
          )}
          <TextField
            label="Reason (recorded in the audit log)"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            fullWidth
            multiline
            minRows={2}
            inputProps={{ maxLength: 2000 }}
          />
          {error && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {error}
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPending(null)}>Cancel</Button>
          <Button
            variant="contained"
            color={pending?.decision === "reject" ? "error" : "primary"}
            disabled={!reason.trim() || review.isPending}
            onClick={submit}
          >
            {pending?.decision === "approve" ? "Approve" : "Reject"}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
