import { useState } from "react";
import {
  Box,
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
  Snackbar,
} from "@mui/material";
import { Add as AddIcon, Delete as DeleteIcon } from "@mui/icons-material";
import { Webhook } from "lucide-react";
import GlassCard from "../../../components/Common/GlassCard";
import CopyOnceToken from "../../../components/Common/CopyOnceToken";
import TwBadge from "../../../components/Common/TwBadge";
import {
  useWebhookSubscriptions,
  type WebhookSubscriptionRow,
} from "../../../features/api/queries";
import {
  useCreateWebhookSubscription,
  useDeleteWebhookSubscription,
} from "../../../features/api/mutations";

const EVENT_TYPES = ["RISK_ALERT", "CASE_UPDATE", "MERCHANT_STATUS_CHANGE"];

const inputSx = {
  "& .MuiOutlinedInput-root": {
    borderRadius: "var(--radius)",
    backgroundColor: "var(--surface-1)",
    "& fieldset": { borderColor: "var(--line-control)" },
    "&:hover fieldset": { borderColor: "rgb(var(--brand-accent-rgb) / 0.5)" },
    "&.Mui-focused fieldset": { borderColor: "var(--brand-accent)" },
  },
};

export default function WebhooksTab() {
  const { data: subscriptions = [], isLoading, isError } = useWebhookSubscriptions();
  const createSub = useCreateWebhookSubscription();
  const deleteSub = useDeleteWebhookSubscription();

  const [form, setForm] = useState({ callbackUrl: "", eventType: "RISK_ALERT" });
  const [creating, setCreating] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [newSecret, setNewSecret] = useState<string | null>(null);
  const [toast, setToast] = useState<{ open: boolean; severity: "success" | "error"; message: string }>({
    open: false,
    severity: "success",
    message: "",
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
    <GlassCard padding="md" glowVariant="teal">
      <div className="mb-4 flex items-start gap-3">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-hairline bg-surface-2 text-teal">
          <Webhook size={18} />
        </div>
        <div>
          <span className="hokeka-section-label">Integrations</span>
          <Typography variant="h6" sx={{ mt: 0.5, fontFamily: "var(--font-display)" }}>
            Webhook subscriptions
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            Get notified in real time instead of polling. Only <strong>RISK_ALERT</strong> is currently
            delivered — other event types can be subscribed to, but nothing emits them yet.
          </Typography>
        </div>
      </div>

      {newSecret && (
        <CopyOnceToken
          token={newSecret}
          title="Signing secret — copy now"
          hint="Store this secret to verify webhook payloads. It cannot be shown again."
          onDismiss={() => setNewSecret(null)}
          className="mb-4"
        />
      )}

      {isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Failed to load webhook subscriptions.
        </Alert>
      )}

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6}>
          <TextField
            label="Callback URL (HTTPS)"
            size="small"
            fullWidth
            value={form.callbackUrl}
            onChange={(e) => setForm((f) => ({ ...f, callbackUrl: e.target.value }))}
            placeholder="https://yourdomain.com/webhooks/hokeka"
            sx={inputSx}
          />
        </Grid>
        <Grid item xs={8} sm={4}>
          <FormControl fullWidth size="small" sx={inputSx}>
            <InputLabel>Event type</InputLabel>
            <Select
              value={form.eventType}
              label="Event type"
              onChange={(e) => setForm((f) => ({ ...f, eventType: e.target.value }))}
            >
              {EVENT_TYPES.map((t) => (
                <MenuItem key={t} value={t}>
                  {t.replaceAll("_", " ")}
                </MenuItem>
              ))}
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
            sx={{
              height: "40px",
              textTransform: "none",
              borderRadius: "var(--radius)",
              backgroundColor: "var(--brand-secondary)",
              color: "var(--brand-on-secondary)",
            }}
          >
            Add
          </Button>
        </Grid>
      </Grid>

      {isLoading ? (
        <Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
          <CircularProgress size={24} sx={{ color: "var(--brand-secondary)" }} />
        </Box>
      ) : subscriptions.length > 0 ? (
        <div className="flex flex-col gap-2">
          {subscriptions.map((sub) => (
            <div
              key={sub.id}
              className="flex items-center justify-between gap-3 rounded-xl border border-hairline bg-surface-1/80 px-4 py-3 transition hover:border-hairline-strong"
            >
              <Box sx={{ minWidth: 0, flex: 1 }}>
                <Typography variant="body2" sx={{ fontFamily: "monospace", wordBreak: "break-all" }}>
                  {sub.callbackUrl}
                </Typography>
                <Box sx={{ display: "flex", gap: 1, mt: 0.75, alignItems: "center", flexWrap: "wrap" }}>
                  <TwBadge variant="info">{sub.eventType.replaceAll("_", " ")}</TwBadge>
                  <TwBadge variant={sub.active ? "success" : "default"}>
                    {sub.active ? "Active" : "Inactive"}
                  </TwBadge>
                  {sub.failureCount > 0 && (
                    <TwBadge variant="warning">{sub.failureCount} failed deliveries</TwBadge>
                  )}
                </Box>
              </Box>
              <Tooltip title="Remove subscription">
                <span>
                  <IconButton
                    size="small"
                    onClick={() => handleDelete(sub.id)}
                    disabled={deletingId === sub.id}
                    sx={{ color: "var(--danger)" }}
                  >
                    {deletingId === sub.id ? <CircularProgress size={16} /> : <DeleteIcon fontSize="small" />}
                  </IconButton>
                </span>
              </Tooltip>
            </div>
          ))}
        </div>
      ) : (
        <div className="hokeka-empty-state py-8">
          <p className="hokeka-empty-state__title">No webhooks configured</p>
          <p className="hokeka-empty-state__body">
            Add a callback URL to receive AML alerts and future event types at your endpoint.
          </p>
        </div>
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
    </GlassCard>
  );
}
