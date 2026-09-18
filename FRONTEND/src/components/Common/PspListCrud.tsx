import { useState } from "react";
import { Plus, Trash2, Loader2 } from "lucide-react";
import TwSnackbar from "./TwSnackbar";

interface PspListCrudProps {
  title: string;
  items: any[];
  pspId: string;
  apiPath: string;
  onRefresh: () => void;
  /** Extra input fields beyond the standard name field */
  extraFields?: { key: string; label: string; placeholder?: string; type?: string }[];
  /**
   * The JSON property the primary "name" input maps to on THIS entity's DTO.
   *
   * The CBK DTOs each name it differently (directorNames, productName, shareholderName, ...) and
   * none of them has a plain `name`. This component used to post `{name: ...}` unconditionally, and
   * because Spring's FAIL_ON_UNKNOWN_PROPERTIES is disabled the field was silently dropped — the
   * server persisted an all-null row and the UI still reported "Added successfully."
   */
  nameField?: string;
}

// Backend CBK sub-resource controllers are all mapped under
// `/psps/{pspId}/cbk/{entity}` (e.g. PspDirectorController = /psps/{pspId}/cbk/directors),
// with POST on the collection and DELETE on `/{id}`. `apiPath` is the bare entity
// segment (e.g. "directors", "trust-accounts").
const cbkBase = (apiPath: string, pspId: string) => `/api/v1/psps/${pspId}/cbk/${apiPath}`;

export default function PspListCrud({ title, items, pspId, apiPath, onRefresh, extraFields, nameField = "name" }: PspListCrudProps) {
  const [adding, setAdding] = useState(false);
  const [form, setForm] = useState<Record<string, string>>({ name: "" });
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState<number | null>(null);
  const [toast, setToast] = useState<{ open: boolean; severity: "success" | "error"; message: string }>({ open: false, severity: "success", message: "" });

  const ic = "w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white placeholder:text-white/30 focus:outline-none focus:ring-1 focus:ring-burgundy-700";

  const handleAdd = async () => {
    if (!form.name?.trim()) return;
    setSaving(true);
    try {
      // Map the generic `name` input onto the DTO's real property before sending, so the value
      // actually lands instead of being silently discarded as an unknown field.
      const { name, ...rest } = form;
      const payload: Record<string, string> = { ...rest, [nameField]: name };
      const res = await fetch(cbkBase(apiPath, pspId), {
        method: "POST", credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      onRefresh();
      setForm({ name: "" });
      setAdding(false);
      setToast({ open: true, severity: "success", message: `Added successfully.` });
    } catch {
      setToast({ open: true, severity: "error", message: "Failed to add." });
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    setDeleting(id);
    try {
      const res = await fetch(`${cbkBase(apiPath, pspId)}/${id}`, { method: "DELETE", credentials: "include" });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      onRefresh();
      setToast({ open: true, severity: "success", message: "Removed." });
    } catch {
      setToast({ open: true, severity: "error", message: "Failed to remove." });
    } finally {
      setDeleting(null);
    }
  };

  const allFields = [{ key: "name", label: "Name", placeholder: "Enter name..." }, ...(extraFields || [])];

  return (
    <div>
      <div className="mb-3 flex items-center justify-between">
        <h4 className="text-base font-semibold text-white">{title} ({items.length})</h4>
        <button onClick={() => setAdding(true)} className="flex items-center gap-1.5 rounded-lg bg-burgundy-700 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-burgundy-800">
          <Plus size={14} /> Add
        </button>
      </div>

      {adding && (
        <div className="mb-3 space-y-2 rounded-lg border border-white/10 bg-[var(--surface-2)] p-3">
          <div className="flex flex-wrap gap-2">
            {allFields.map((f) => (
              <div key={f.key} className="flex-1 min-w-[120px]">
                <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">{f.label}</label>
                <input
                  type={f.type || "text"}
                  value={form[f.key] || ""}
                  onChange={(e) => setForm((prev) => ({ ...prev, [f.key]: e.target.value }))}
                  placeholder={f.placeholder}
                  className={ic}
                />
              </div>
            ))}
          </div>
          <div className="flex justify-end gap-2">
            <button onClick={() => setAdding(false)} className="rounded-lg border border-white/10 px-3 py-1.5 text-xs text-glass-muted transition-colors hover:bg-white/5">Cancel</button>
            <button onClick={handleAdd} disabled={saving || !form.name?.trim()}
              className="flex items-center gap-1 rounded-lg bg-burgundy-700 px-3 py-1.5 text-xs text-white transition-colors hover:bg-burgundy-800 disabled:opacity-50">
              {saving ? <Loader2 size={14} className="animate-spin" /> : null} Save
            </button>
          </div>
        </div>
      )}

      <div className="space-y-1">
        {items.map((item: any) => (
          <div key={item.id} className="flex items-center justify-between rounded-lg border border-white/5 px-3 py-2 transition-colors hover:bg-white/[0.02]">
            <div className="min-w-0 flex-1">
              <p className="text-sm text-white truncate">{item[nameField] || item.name || item.fullName || `#${item.id}`}</p>
              {extraFields?.map((f) => (
                item[f.key] ? <p key={f.key} className="text-xs text-glass-muted truncate">{item[f.key]}</p> : null
              ))}
            </div>
            <button onClick={() => handleDelete(item.id)} disabled={deleting === item.id}
              className="shrink-0 rounded p-1 text-red-400 transition-colors hover:bg-white/10 disabled:opacity-30">
              {deleting === item.id ? <Loader2 size={14} className="animate-spin" /> : <Trash2 size={14} />}
            </button>
          </div>
        ))}
        {!items.length && <p className="py-4 text-center text-sm text-glass-muted">No records found.</p>}
      </div>

      <TwSnackbar open={toast.open} message={toast.message} severity={toast.severity} onClose={() => setToast((t) => ({ ...t, open: false }))} />
    </div>
  );
}