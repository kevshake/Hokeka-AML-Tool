import { useState } from "react";
import {
  Box,
  Paper,
  Typography,
  TextField,
  Button,
  Grid,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Alert,
  CircularProgress,
  IconButton,
  Tooltip,
  Chip,
  Snackbar,
} from "@mui/material";
import { Add as AddIcon, Delete as DeleteIcon, ContentCopy as CopyIcon } from "@mui/icons-material";
import {
  useWebhookSubscriptions,
  type WebhookSubscriptionRow,
} from "../../../features/api/queries";
import {
  useCreateWebhookSubscription,
  useDeleteWebhookSubscription,
} from "../../../features/api/mutations";

const EVENT_TYPES = ["RISK_ALERT", "CASE_UPDATE", "MERCHANT_STATUS_CHANGE"];

/**
 * W26-8 fix: the Psp entity had no webhook/notification settings at all -- PSPs had no way to
 * configure a callback URL for AML results. Built over W36-2's WebhookSubscriptionController
 * (already tenant-scoped: subscribe/list/unsubscribe act only on the caller's own PSP).
 *
 * Note: CASE_UPDATE and MERCHANT_STATUS_CHANGE can be subscribed to, but nothing in the codebase
 * emits them yet (only RISK_ALERT is actually wired to a real trigger, from this same session's
 * W36-2 fix) -- the UI says so rather than implying full delivery.
 */
export default function WebhooksTab() {
  const { data: subscriptions = [], isLoading, isError } = useWebhookSubscriptions();
  const createSub = useCreateWebhookSubscription();
  const deleteSub = useDeleteWebhookSubscription();

  const [form, setForm] = useState({ callbackUrl: "", eventType: "RISK_ALERT" });
  const [creating, setCreating] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [newSecret, setNewSecret] = useState<string | null>(null);
  const [toast, setToast] = useState<{ open: boolean; severity: "success" | "error"; message: string }>({
    open: false, severity: "success", message: "",
  });

  const handleCreate = async () => {
    if (!form.callbackUrl || !form.eventType) return;
    setCreating(true);
    try {
      const result = await createSub.mutateAsync(form);
      const secret = (result as WebhookSubscriptionRow)?.secretKey;
      if (secret) setNewSecret(secret);
      setForm({ callbackUrl: "", eventType: "RISK_ALERT" });
      setToast({ open: true, severity: "success", message: "Webhook subscription created." });
    } catch {
      setToast({ open: true, severity: "error", message: "Failed to create webhook subscription." });
    } finally {
      setCreating(false);
    }
  };

  const handleDelete = async (id: number) => {
    setDeletingId(id);
    try {
      await deleteSub.mutateAsync(id);
      setToast({ open: true, severity: "success", message: "Webhook subscription removed." });
    } catch {
      setToast({ open: true, severity: "error", message: "Failed to remove webhook subscription." });
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <Paper sx={{ p: 2, backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
      <Typography variant="h6" sx={{ color: "text.primary", mb: 1 }}>
        Webhook Subscriptions
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Get notified in real time instead of polling. Only <strong>RISK_ALERT</strong> is currently
        delivered — CASE_UPDATE and MERCHANT_STATUS_CHANGE can be subscribed to, but nothing emits
        them yet.
      </Typography>

      {newSecret && (
        <Alert
          severity="info"
          sx={{ mb: 2 }}
          onClose={() => setNewSecret(null)}
          action={
            <Tooltip title="Copy signing secret">
              <IconButton size="small" onClick={() => navigator.clipboard.writeText(newSecret)}>
                <CopyIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          }
        >
          Signing secret (shown once, store it now): <code>{newSecret}</code>
        </Alert>
      )}

      {isError && <Alert severity="error" sx={{ mb: 2 }}>Failed to load webhook subscriptions.</Alert>}

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6}>
          <TextField
            label="Callback URL (HTTPS)"
            size="small"
            fullWidth
            value={form.callbackUrl}
            onChange={(e) => setForm((f) => ({ ...f, callbackUrl: e.target.value }))}
            placeholder="https://yourdomain.com/webhooks/hokeka"
          />
        </Grid>
        <Grid item xs={8} sm={4}>
          <FormControl fullWidth size="small">
            <InputLabel>Event Type</InputLabel>
            <Select
              value={form.eventType}
              label="Event Type"
              onChange={(e) => setForm((f) => ({ ...f, eventType: e.target.value }))}
            >
              {EVENT_TYPES.map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}
            </Select>
          </FormControl>
        </Grid>
        <Grid item xs={4} sm={2}>
          <Button
            fullWidth
            variant="contained"
            startIcon={creating ? <CircularProgress size={16} /> : <AddIcon />}
            onClick={handleCreate}
            disabled={creating || !form.callbackUrl}
            sx={{ height: "40px", textTransform: "none" }}
          >
            Add
          </Button>
        </Grid>
      </Grid>

      {isLoading ? (
        <Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
          <CircularProgress size={24} />
        </Box>
      ) : subscriptions.length > 0 ? (
        <Box sx={{ display: "flex", flexDirection: "column", gap: 1 }}>
          {subscriptions.map((sub) => (
            <Box
              key={sub.id}
              sx={{
                display: "flex", alignItems: "center", justifyContent: "space-between",
                p: 1.5, border: "1px solid rgba(0,0,0,0.08)", borderRadius: 1,
              }}
            >
              <Box sx={{ minWidth: 0, flex: 1 }}>
                <Typography variant="body2" sx={{ fontFamily: "monospace", wordBreak: "break-all" }}>
                  {sub.callbackUrl}
                </Typography>
                <Box sx={{ display: "flex", gap: 1, mt: 0.5, alignItems: "center" }}>
                  <Chip label={sub.eventType} size="small" />
                  <Chip
                    label={sub.active ? "Active" : "Inactive"}
                    size="small"
                    color={sub.active ? "success" : "default"}
                  />
                  {sub.failureCount > 0 && (
                    <Chip label={`${sub.failureCount} failed deliveries`} size="small" color="warning" />
                  )}
                </Box>
              </Box>
              <Tooltip title="Remove subscription">
                <span>
                  <IconButton
                    size="small"
                    onClick={() => handleDelete(sub.id)}
                    disabled={deletingId === sub.id}
                    sx={{ color: "var(--danger, #d32f2f)" }}
                  >
                    {deletingId === sub.id ? <CircularProgress size={16} /> : <DeleteIcon fontSize="small" />}
                  </IconButton>
                </span>
              </Tooltip>
            </Box>
          ))}
        </Box>
      ) : (
        <Typography variant="body2" color="text.disabled" sx={{ textAlign: "center", py: 3 }}>
          No webhook subscriptions configured.
        </Typography>
      )}

      <Snackbar
        open={toast.open}
        autoHideDuration={4000}
        onClose={() => setToast((t) => ({ ...t, open: false }))}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      >
        <Alert severity={toast.severity} onClose={() => setToast((t) => ({ ...t, open: false }))} variant="filled">
          {toast.message}
        </Alert>
      </Snackbar>
    </Paper>
  );
}
