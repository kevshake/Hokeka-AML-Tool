import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "../../lib/apiClient";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import TwSnackbar from "../../components/Common/TwSnackbar";
import { Loader2, Save, DollarSign } from "lucide-react";

export default function LimitsAmlPage() {
  const queryClient = useQueryClient();
  const [transactionLimit, setTransactionLimit] = useState("");
  const [dailyLimit, setDailyLimit] = useState("");
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: "success" | "error" }>({
    open: false, message: "", severity: "success",
  });

  const limitsQuery = useQuery({
    queryKey: ["limits", "aml"],
    queryFn: () => apiClient.get<{ transactionLimit?: number; dailyLimit?: number }>("limits/aml"),
  });

  useEffect(() => {
    if (!limitsQuery.data) return;
    setTransactionLimit(limitsQuery.data.transactionLimit?.toString() ?? "");
    setDailyLimit(limitsQuery.data.dailyLimit?.toString() ?? "");
  }, [limitsQuery.data]);

  const saveLimits = useMutation({
    mutationFn: () => apiClient.post("limits/aml", {
      transactionLimit: transactionLimit ? parseFloat(transactionLimit) : undefined,
      dailyLimit: dailyLimit ? parseFloat(dailyLimit) : undefined,
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["limits", "aml"] });
      setSnackbar({ open: true, message: "AML limits saved successfully.", severity: "success" });
    },
    onError: (err: any) => { setSnackbar({ open: true, message: err?.message || "Failed to save limits.", severity: "error" }); },
  });

  return (
    <HokekaPageShell title="Transaction Limits" subtitle="Configure AML transaction and daily volume limits">
      <div className="rounded-lg border border-white/10 bg-[var(--surface-2)] p-6">
        <h3 className="mb-4 flex items-center gap-2 text-base font-semibold text-white">
          <DollarSign size={20} className="text-burgundy-400" /> AML Limits Configuration
        </h3>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div>
            <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Transaction Limit</label>
            <input type="number" min="0" step="0.01" value={transactionLimit} onChange={(e) => setTransactionLimit(e.target.value)}
              placeholder="e.g. 10000"
              disabled={limitsQuery.isLoading}
              className="mt-1 w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white placeholder:text-white/30 focus:outline-none focus:ring-1 focus:ring-burgundy-700" />
            <p className="mt-1 text-xs text-glass-muted">Maximum amount per single transaction</p>
          </div>
          <div>
            <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Daily Limit</label>
            <input type="number" min="0" step="0.01" value={dailyLimit} onChange={(e) => setDailyLimit(e.target.value)}
              placeholder="e.g. 50000"
              disabled={limitsQuery.isLoading}
              className="mt-1 w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white placeholder:text-white/30 focus:outline-none focus:ring-1 focus:ring-burgundy-700" />
            <p className="mt-1 text-xs text-glass-muted">Maximum total PSP transaction volume per day</p>
          </div>
        </div>
        <div className="mt-6">
          <button onClick={() => saveLimits.mutate()} disabled={limitsQuery.isLoading || saveLimits.isPending || (!transactionLimit && !dailyLimit)}
            className="flex items-center gap-2 rounded-lg bg-burgundy-700 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-burgundy-800 disabled:opacity-50">
            {saveLimits.isPending ? <Loader2 size={16} className="animate-spin" /> : <Save size={16} />}
            Save Limits
          </button>
        </div>
      </div>
      <TwSnackbar open={snackbar.open} message={snackbar.message} severity={snackbar.severity} onClose={() => setSnackbar(prev => ({ ...prev, open: false }))} />
    </HokekaPageShell>
  );
}
