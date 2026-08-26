import { useState } from "react";
import { apiClient } from "../../../lib/apiClient";
import { useQueryClient } from "@tanstack/react-query";
import TwSnackbar from "../../../components/Common/TwSnackbar";
import { Save, Loader2 } from "lucide-react";

interface CompanyTabProps { pspId: string; psp: any }

export default function CompanyTab({ pspId, psp }: CompanyTabProps) {
  const queryClient = useQueryClient();
  // W20-13 fix: billingPlan/currency/paymentTerms/isTestMode are all accepted by the backend's
  // PUT /psps/{id} (PspUpdateRequest has all four) but were missing from this form entirely --
  // there was no way to edit them from the UI at all. status is deliberately NOT included here:
  // it's managed via the separate PUT /psps/{id}/status lifecycle endpoint, not general profile
  // edit, so it's shown read-only below instead of as an editable field.
  const [form, setForm] = useState({
    legalName: psp?.legalName ?? "", tradingName: psp?.tradingName ?? "",
    country: psp?.country ?? "", registrationNumber: psp?.registrationNumber ?? "",
    taxId: psp?.taxId ?? "", contactEmail: psp?.contactEmail ?? "",
    contactPhone: psp?.contactPhone ?? "", contactAddress: psp?.contactAddress ?? "",
    billingPlan: psp?.billingPlan ?? "PAY_AS_YOU_GO", currency: psp?.currency ?? "USD",
    paymentTerms: psp?.paymentTerms ?? 30, isTestMode: psp?.isTestMode ?? false,
  });
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState<{ open: boolean; severity: "success" | "error"; message: string }>({ open: false, severity: "success", message: "" });

  const set = (field: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) =>
    setForm(f => ({ ...f, [field]: e.target.value }));
  const setNumber = (field: string) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm(f => ({ ...f, [field]: e.target.value === "" ? 0 : parseInt(e.target.value, 10) || 0 }));
  const setChecked = (field: string) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm(f => ({ ...f, [field]: e.target.checked }));

  const handleSave = async () => {
    setSaving(true);
    try {
      await apiClient.put(`psps/${pspId}`, form);
      queryClient.invalidateQueries({ queryKey: ["psp", pspId] });
      queryClient.invalidateQueries({ queryKey: ["psps"] });
      setToast({ open: true, severity: "success", message: "Company details saved." });
    } catch { setToast({ open: true, severity: "error", message: "Save failed. Please try again." }); }
    finally { setSaving(false); }
  };

  const inputClass = "w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white placeholder:text-white/30 focus:outline-none focus:ring-1 focus:ring-burgundy-700";
  const labelClass = "text-[11px] font-semibold uppercase tracking-wider text-glass-muted";

  return (
    <div>
      <h4 className="mb-3 text-base font-semibold text-white">Company Details</h4>
      <div className="grid grid-cols-2 gap-4">
        <div><label className={labelClass}>Legal Name</label><input value={form.legalName} onChange={set("legalName")} className={inputClass} /></div>
        <div><label className={labelClass}>Trading Name</label><input value={form.tradingName} onChange={set("tradingName")} className={inputClass} /></div>
        <div><label className={labelClass}>Country</label><input value={form.country} onChange={set("country")} className={inputClass} /></div>
        <div><label className={labelClass}>Registration Number</label><input value={form.registrationNumber} onChange={set("registrationNumber")} className={inputClass} /></div>
        <div><label className={labelClass}>Tax ID / PIN</label><input value={form.taxId} onChange={set("taxId")} className={inputClass} /></div>
        <div><label className={labelClass}>Contact Email</label><input type="email" value={form.contactEmail} onChange={set("contactEmail")} className={inputClass} /></div>
        <div><label className={labelClass}>Contact Phone</label><input value={form.contactPhone} onChange={set("contactPhone")} className={inputClass} /></div>
        <div className="col-span-2"><label className={labelClass}>Contact Address</label><textarea value={form.contactAddress} onChange={set("contactAddress")} rows={2} className={inputClass} /></div>
        <div>
          <label className={labelClass}>Status</label>
          <div className={`${inputClass} flex items-center text-white/60`} title="Managed via PSP lifecycle actions (activate/suspend/terminate), not here">
            {psp?.status ?? "PENDING"}
          </div>
        </div>
        <div>
          <label className={labelClass}>Billing Plan</label>
          <select value={form.billingPlan} onChange={set("billingPlan")} className={inputClass}>
            <option value="PAY_AS_YOU_GO">Pay as you go</option>
            <option value="SUBSCRIPTION">Subscription</option>
          </select>
        </div>
        <div><label className={labelClass}>Currency</label><input value={form.currency} onChange={set("currency")} className={inputClass} /></div>
        <div><label className={labelClass}>Payment Terms (days)</label><input type="number" min={0} value={form.paymentTerms} onChange={setNumber("paymentTerms")} className={inputClass} /></div>
        <div className="col-span-2 flex items-center gap-2 pt-1">
          <input id="isTestMode" type="checkbox" checked={form.isTestMode} onChange={setChecked("isTestMode")} className="h-4 w-4 rounded border-white/20 bg-[var(--surface-3)]" />
          <label htmlFor="isTestMode" className="text-sm text-white/80">Test mode PSP (excluded from active-PSP queries used elsewhere in the platform)</label>
        </div>
      </div>
      <div className="mt-4 flex justify-end">
        <button onClick={handleSave} disabled={saving}
          className="flex items-center gap-2 rounded-lg bg-burgundy-700 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-burgundy-800 disabled:opacity-50">
          {saving ? <Loader2 size={16} className="animate-spin" /> : <Save size={16} />} Save Changes
        </button>
      </div>
      <TwSnackbar open={toast.open} message={toast.message} severity={toast.severity} onClose={() => setToast(t => ({ ...t, open: false }))} />
    </div>
  );
}