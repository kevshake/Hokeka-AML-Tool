import { useState } from "react";
import { Link } from "react-router-dom";
import { useSarReports } from "../../features/api/queries";
import { useCreateSar } from "../../features/api/mutations";
import type { SarReport } from "../../types";
import TwBadge from "../../components/Common/TwBadge";
import { TwInput } from "../../components/Common/TwInput";
import { Eye, Loader2, Plus, X } from "lucide-react";

const statusBadge = (s: string | undefined): "default" | "warning" | "info" | "success" | "danger" | "info" => {
  const map: Record<string, "default" | "warning" | "info" | "success" | "danger"> = {
    DRAFT: "default", REVIEW: "warning", APPROVED: "info",
    FILED: "success", REJECTED: "danger", AMENDED: "info",
  };
  return map[s || ""] || "default";
};

const SAR_ACTIVITY_TYPES = [
  "Transaction Structuring", "Money Laundering", "Fraud",
  "Terrorism Financing", "Sanctions Evasion", "Other Transaction Activity",
];

function localDateTimeValue(date = new Date()) {
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

const newSarForm = () => ({ sarReference: "", suspiciousActivityType: "", narrative: "", jurisdiction: "KE", suspicionAroseAt: localDateTimeValue() });

export default function TransactionMonitoringSars() {
  const { data: sars, isLoading } = useSarReports();
  const [viewSar, setViewSar] = useState<SarReport | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [form, setForm] = useState(newSarForm);
  const [formError, setFormError] = useState<string | null>(null);

  const createSar = useCreateSar();

  const transactionSars = (sars?.filter((sar: any) => sar.suspiciousActivityType?.toLowerCase().includes("transaction")) || []);

  const handleCreate = () => {
    if (!form.sarReference.trim() || !form.suspiciousActivityType || !form.narrative.trim()) {
      setFormError("Reference, Activity Type, and Narrative are required.");
      return;
    }
    setFormError(null);
    createSar.mutate(
      {
        sarReference: form.sarReference.trim(),
        suspiciousActivityType: form.suspiciousActivityType,
        narrative: form.narrative.trim(),
        jurisdiction: form.jurisdiction.trim() || undefined,
        sarType: "INITIAL",
        suspicionAroseAt: form.suspicionAroseAt,
      },
      {
        onSuccess: () => { setCreateOpen(false); setForm(newSarForm()); },
        onError: (err: unknown) => {
          const msg = (err as { message?: string })?.message || "Failed to create SAR.";
          setFormError(msg);
        },
      }
    );
  };

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold text-white">Transaction-Related SAR Reports</h3>
        <button onClick={() => { setCreateOpen(true); setFormError(null); setForm(newSarForm()); }}
          className="flex items-center gap-1.5 rounded-lg bg-burgundy-700 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-burgundy-800">
          <Plus size={14} /> Create SAR
        </button>
      </div>

      <div className="overflow-hidden rounded-lg border border-white/10 bg-[var(--surface-2)]">
        <div className="overflow-auto" style={{ maxHeight: "calc(100vh - 320px)" }}>
          {isLoading ? (
            <div className="flex items-center justify-center py-12"><Loader2 size={24} className="animate-spin text-glass-muted" /></div>
          ) : (
            <table className="w-full border-collapse">
              <thead className="sticky top-0 z-10">
                <tr className="border-b border-white/10 bg-[var(--surface-2)]">
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Reference</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Status</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Activity Type</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Created</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {transactionSars.length > 0 ? transactionSars.map((sar: any) => (
                  <tr key={sar.id} className="transition-colors hover:bg-white/[0.02]">
                    <td className="whitespace-nowrap px-4 py-3 font-mono text-sm text-white">{sar.sarReference}</td>
                    <td className="whitespace-nowrap px-4 py-3"><TwBadge variant={statusBadge(sar.status)}>{sar.status}</TwBadge></td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm text-white/80">{sar.suspiciousActivityType}</td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm text-glass-muted">{new Date(sar.createdAt).toLocaleDateString()}</td>
                    <td className="whitespace-nowrap px-4 py-3">
                      <div className="flex items-center gap-2">
                        <button onClick={() => setViewSar(sar)} className="flex items-center gap-1 text-xs text-burgundy-400 transition-colors hover:text-burgundy-300"><Eye size={14} /> View</button>
                        <Link to={`/records/SUSPICIOUS_ACTIVITY_REPORT/${sar.id}`} className="text-xs text-sky-300 hover:text-sky-200">Trace</Link>
                      </div>
                    </td>
                  </tr>
                )) : (
                  <tr><td colSpan={5} className="px-4 py-8 text-center text-sm text-glass-muted">No transaction-related SAR reports found</td></tr>
                )}
              </tbody>
            </table>
          )}
        </div>
      </div>

      {/* Create SAR Dialog */}
      {createOpen && (
        <>
          <div className="fixed inset-0 z-40 bg-black/60 backdrop-blur-sm" onClick={() => setCreateOpen(false)} />
          <div className="fixed left-1/2 top-1/2 z-50 w-full max-w-lg -translate-x-1/2 -translate-y-1/2">
            <div className="overflow-hidden rounded-xl border border-white/10 bg-[var(--surface-2)] shadow-2xl">
              <div className="flex items-center justify-between border-b border-white/10 px-6 py-4">
                <h3 className="text-lg font-semibold text-white">Create SAR</h3>
                <button onClick={() => setCreateOpen(false)} className="rounded p-1 text-glass-muted hover:bg-white/10"><X size={18} /></button>
              </div>
              <div className="space-y-4 px-6 py-4">
                {formError && <div className="rounded-lg border border-red-700/30 bg-red-900/30 px-3 py-2 text-sm text-red-200">{formError}</div>}
                <TwInput label="SAR Reference" value={form.sarReference} onChange={(e) => setForm(f => ({ ...f, sarReference: e.target.value }))} />
                <div className="flex flex-col gap-1.5">
                  <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Activity Type</label>
                  <select value={form.suspiciousActivityType} onChange={(e) => setForm(f => ({ ...f, suspiciousActivityType: e.target.value }))}
                    className="w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white focus:outline-none focus:ring-1 focus:ring-burgundy-700">
                    <option value="">Select...</option>
                    {SAR_ACTIVITY_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
                  </select>
                </div>
                <div className="flex flex-col gap-1.5">
                  <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Narrative</label>
                  <textarea value={form.narrative} onChange={(e) => setForm(f => ({ ...f, narrative: e.target.value }))} rows={4}
                    className="w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white placeholder:text-white/30 focus:outline-none focus:ring-1 focus:ring-burgundy-700" />
                </div>
                <TwInput label="Jurisdiction (optional)" value={form.jurisdiction} onChange={(e) => setForm(f => ({ ...f, jurisdiction: e.target.value }))} />
                <TwInput label="Suspicion arose at" type="datetime-local" required value={form.suspicionAroseAt} max={localDateTimeValue()} onChange={(e) => setForm(f => ({ ...f, suspicionAroseAt: e.target.value }))} />
              </div>
              <div className="flex justify-end gap-2 border-t border-white/10 px-6 py-3">
                <button onClick={() => setCreateOpen(false)} className="rounded-lg border border-white/10 px-4 py-1.5 text-xs text-white transition-colors hover:bg-white/5">Cancel</button>
                <button onClick={handleCreate} disabled={createSar.isPending}
                  className="flex items-center gap-1.5 rounded-lg bg-burgundy-700 px-4 py-1.5 text-xs font-medium text-white transition-colors hover:bg-burgundy-800 disabled:opacity-30">
                  {createSar.isPending ? <Loader2 size={14} className="animate-spin" /> : null}
                  Create SAR
                </button>
              </div>
            </div>
          </div>
        </>
      )}

      {/* SAR Detail Modal */}
      {viewSar && (
        <>
          <div className="fixed inset-0 z-40 bg-black/60 backdrop-blur-sm" onClick={() => setViewSar(null)} />
          <div className="fixed left-1/2 top-1/2 z-50 w-full max-w-lg -translate-x-1/2 -translate-y-1/2">
            <div className="overflow-hidden rounded-xl border border-white/10 bg-[var(--surface-2)] shadow-2xl">
              <div className="flex items-start justify-between border-b border-white/10 px-6 py-4">
                <div>
                  <h3 className="font-mono text-lg font-semibold text-white">{viewSar.sarReference}</h3>
                  <p className="mt-0.5 text-xs text-glass-muted">SAR Report — {viewSar.sarType}</p>
                </div>
                <div className="flex items-center gap-2">
                  <TwBadge variant={statusBadge(viewSar.status)}>{viewSar.status}</TwBadge>
                  <button onClick={() => setViewSar(null)} className="rounded p-1 text-glass-muted hover:bg-white/10"><X size={18} /></button>
                </div>
              </div>
              <div className="space-y-4 px-6 py-4">
                <div><p className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Activity Type</p><p className="mt-1 text-sm text-white/80">{viewSar.suspiciousActivityType}</p></div>
                {viewSar.narrative && <div><p className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Narrative</p><p className="mt-1 text-sm leading-relaxed text-white/80">{viewSar.narrative}</p></div>}
                <div className="grid grid-cols-2 gap-4">
                  <div><p className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Jurisdiction</p><p className="mt-0.5 text-sm text-white/80">{viewSar.jurisdiction || "—"}</p></div>
                  <div><p className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Created</p><p className="mt-0.5 text-sm text-white/80">{new Date(viewSar.createdAt).toLocaleString()}</p></div>
                  {viewSar.suspicionAroseAt && <div><p className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Suspicion arose</p><p className="mt-0.5 text-sm text-white/80">{new Date(viewSar.suspicionAroseAt).toLocaleString()}</p></div>}
                  {viewSar.filingDeadline && <div><p className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Filing deadline</p><p className={`mt-0.5 text-sm ${viewSar.deadlineBreached ? "text-red-400" : "text-amber-300"}`}>{new Date(viewSar.filingDeadline).toLocaleString()}</p><p className="mt-1 text-[10px] text-glass-muted">{viewSar.deadlinePolicyCode || "Policy unavailable"}</p></div>}
                  {viewSar.filedAt && <div><p className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Filed</p><p className="mt-0.5 text-sm text-emerald-400">{new Date(viewSar.filedAt).toLocaleString()}</p></div>}
                  {viewSar.filingReference && <div><p className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Filing Reference</p><p className="mt-0.5 font-mono text-sm text-white/80">{viewSar.filingReference}</p></div>}
                </div>
                {viewSar.reviewNotes && <div><p className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Review decision</p><p className="mt-1 text-sm leading-relaxed text-white/80">{viewSar.reviewNotes}</p>{viewSar.reviewedAt && <p className="mt-1 text-xs text-glass-muted">{new Date(viewSar.reviewedAt).toLocaleString()}</p>}</div>}
              </div>
              <div className="flex justify-end border-t border-white/10 px-6 py-3">
                <Link to={`/records/SUSPICIOUS_ACTIVITY_REPORT/${viewSar.id}`} onClick={() => setViewSar(null)} className="mr-2 rounded-lg border border-gold/50 px-4 py-1.5 text-xs text-gold transition-colors hover:bg-gold hover:text-black">Trace record</Link>
                <button onClick={() => setViewSar(null)} className="rounded-lg border border-white/10 px-4 py-1.5 text-xs text-white transition-colors hover:bg-white/5">Close</button>
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
