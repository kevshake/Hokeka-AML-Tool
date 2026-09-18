import { useState } from "react";
import { useUpcomingDeadlines, useOverdueDeadlines } from "../../features/api/queries";
import { useCreateDeadline, type CreateDeadlineRequest } from "../../features/api/mutations";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import TwBadge from "../../components/Common/TwBadge";
import TwSnackbar from "../../components/Common/TwSnackbar";
import { Plus, Loader2, X, Calendar } from "lucide-react";

const deadlineTypes = ["REGULATORY", "FILING", "REVIEW", "AUDIT", "REPORTING", "OTHER"];

export default function ComplianceCalendarPage() {
  const { data: upcoming } = useUpcomingDeadlines();
  const { data: overdue } = useOverdueDeadlines();
  const createDeadline = useCreateDeadline();

  const [addOpen, setAddOpen] = useState(false);
  const [formData, setFormData] = useState<CreateDeadlineRequest>({
    title: "", description: "",
    dueDate: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString().split("T")[0],
    deadlineType: "",
  });
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: "success" | "error" }>({
    open: false, message: "", severity: "success",
  });

  const handleCreate = async () => {
    if (!formData.title.trim() || !formData.dueDate) return;
    try {
      await createDeadline.mutateAsync(formData);
      setAddOpen(false);
      setFormData({ title: "", description: "", dueDate: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString().split("T")[0], deadlineType: "" });
      setSnackbar({ open: true, message: "Compliance deadline created.", severity: "success" });
    } catch (err: any) { setSnackbar({ open: true, message: err?.message || "Failed to create deadline.", severity: "error" }); }
  };

  const overdueList = overdue && Array.isArray(overdue) ? overdue : [];
  const upcomingList = upcoming && Array.isArray(upcoming) ? upcoming : [];

  return (
    <HokekaPageShell
      title="Compliance Calendar"
      subtitle="Regulatory deadlines and filing schedules"
      noCard
      actions={
        <button onClick={() => setAddOpen(true)} className="flex items-center gap-1.5 rounded-lg bg-burgundy-700 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-burgundy-800">
          <Plus size={14} /> Create Deadline
        </button>
      }
    >
      <div className="flex flex-col gap-4 md:flex-row">
        {/* Overdue */}
        <div className="flex-1 rounded-lg border border-red-700/30 bg-[var(--surface-2)] p-4">
          <h3 className="mb-3 flex items-center gap-2 text-base font-semibold text-red-400">
            <Calendar size={18} /> Overdue Deadlines
          </h3>
          {overdueList.length > 0 ? (
            <div className="space-y-1">
              {overdueList.map((deadline: any, idx: number) => (
                <div key={deadline.id ?? idx} className="flex items-center justify-between border-b border-white/5 py-2">
                  <div>
                    <p className="text-sm text-white">{deadline.title || deadline.description || "Deadline"}</p>
                    <p className="text-xs text-glass-muted">{deadline.dueDate ? new Date(deadline.dueDate).toLocaleDateString() : ""}</p>
                  </div>
                  <TwBadge variant="danger">Overdue</TwBadge>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-sm text-glass-muted">No overdue deadlines</p>
          )}
        </div>

        {/* Upcoming */}
        <div className="flex-1 rounded-lg border border-white/10 bg-[var(--surface-2)] p-4">
          <h3 className="mb-3 flex items-center gap-2 text-base font-semibold text-white">
            <Calendar size={18} /> Upcoming Deadlines (Next 30 Days)
          </h3>
          {upcomingList.length > 0 ? (
            <div className="space-y-1">
              {upcomingList.map((deadline: any, idx: number) => (
                <div key={deadline.id ?? idx} className="flex items-center justify-between border-b border-white/5 py-2">
                  <div>
                    <p className="text-sm text-white">{deadline.title || deadline.description || "Deadline"}</p>
                    <p className="text-xs text-glass-muted">{deadline.dueDate ? new Date(deadline.dueDate).toLocaleDateString() : ""}</p>
                  </div>
                  <TwBadge variant="warning">Upcoming</TwBadge>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-sm text-glass-muted">No upcoming deadlines</p>
          )}
        </div>
      </div>

      {/* Create Deadline Dialog */}
      {addOpen && (
        <>
          <div className="fixed inset-0 z-40 bg-black/60 backdrop-blur-sm" onClick={() => setAddOpen(false)} />
          <div className="fixed left-1/2 top-1/2 z-50 w-full max-w-lg -translate-x-1/2 -translate-y-1/2">
            <div className="overflow-hidden rounded-xl border border-white/10 bg-[var(--surface-2)] shadow-2xl">
              <div className="flex items-center justify-between border-b border-white/10 px-6 py-4">
                <h3 className="text-lg font-semibold text-white">Create Compliance Deadline</h3>
                <button onClick={() => setAddOpen(false)} className="rounded p-1 text-glass-muted hover:bg-white/10"><X size={18} /></button>
              </div>
              <div className="space-y-4 px-6 py-4">
                <div>
                  <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Title</label>
                  <input value={formData.title} onChange={(e) => setFormData(prev => ({ ...prev, title: e.target.value }))}
                    placeholder="e.g. Q2 SAR Filing Deadline"
                    className="mt-1 w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white placeholder:text-white/30 focus:outline-none focus:ring-1 focus:ring-burgundy-700" />
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Due Date</label>
                    <input type="date" value={formData.dueDate} onChange={(e) => setFormData(prev => ({ ...prev, dueDate: e.target.value }))}
                      className="mt-1 w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white focus:outline-none focus:ring-1 focus:ring-burgundy-700" />
                  </div>
                  <div>
                    <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Type</label>
                    <select value={formData.deadlineType} onChange={(e) => setFormData(prev => ({ ...prev, deadlineType: e.target.value }))}
                      className="mt-1 w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white focus:outline-none focus:ring-1 focus:ring-burgundy-700">
                      <option value="">Select Type</option>
                      {deadlineTypes.map(t => <option key={t} value={t}>{t}</option>)}
                    </select>
                  </div>
                </div>
                <div>
                  <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Description</label>
                  <textarea value={formData.description} onChange={(e) => setFormData(prev => ({ ...prev, description: e.target.value }))}
                    rows={3} placeholder="Optional details about this compliance deadline..."
                    className="mt-1 w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white placeholder:text-white/30 focus:outline-none focus:ring-1 focus:ring-burgundy-700" />
                </div>
              </div>
              <div className="flex justify-end gap-2 border-t border-white/10 px-6 py-3">
                <button onClick={() => setAddOpen(false)} className="rounded-lg border border-white/10 px-4 py-1.5 text-xs text-white transition-colors hover:bg-white/5">Cancel</button>
                <button onClick={handleCreate} disabled={!formData.title.trim() || !formData.dueDate || createDeadline.isPending}
                  className="flex items-center gap-1.5 rounded-lg bg-burgundy-700 px-4 py-1.5 text-xs font-medium text-white transition-colors hover:bg-burgundy-800 disabled:opacity-30">
                  {createDeadline.isPending ? <Loader2 size={14} className="animate-spin" /> : null}
                  Create Deadline
                </button>
              </div>
            </div>
          </div>
        </>
      )}

      <TwSnackbar open={snackbar.open} message={snackbar.message} severity={snackbar.severity} onClose={() => setSnackbar(prev => ({ ...prev, open: false }))} />
    </HokekaPageShell>
  );
}