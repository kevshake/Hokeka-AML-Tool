import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Loader2, ShieldCheck, ShieldOff, AlertTriangle } from "lucide-react";
import { apiClient } from "../../../lib/apiClient";
import { useAuth } from "../../../contexts/AuthContext";

const PLATFORM_ADMIN_ROLES = new Set(["SUPER_ADMIN", "ADMIN"]);

interface KycTabProps {
  pspId: string;
  psp: any;
}

/**
 * Per-PSP KYC/KYB enforcement toggle.
 *
 * Only platform admins (SUPER_ADMIN / ADMIN) may change it — a PSP must not be able to
 * disable its own KYC. When disabled, that PSP's merchants onboard as ACTIVE with
 * kycStatus=NOT_REQUIRED and transactions flow without KYC gating.
 */
export default function KycTab({ pspId, psp }: KycTabProps) {
  const { user } = useAuth();
  const isPlatformAdmin = !!user?.role?.name && PLATFORM_ADMIN_ROLES.has(user.role.name);
  const queryClient = useQueryClient();

  // Default to enabled (safe) when the field is absent.
  const [enabled, setEnabled] = useState<boolean>(psp?.kycEnabled ?? true);
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState<{ kind: "success" | "error"; msg: string } | null>(null);

  const apply = async (next: boolean) => {
    if (!isPlatformAdmin || !pspId) return;
    setSaving(true);
    setToast(null);
    try {
      await apiClient.put(`psps/${pspId}/kyc-config?enabled=${next}`);
      setEnabled(next);
      setToast({
        kind: "success",
        msg: next
          ? "KYC/KYB enforcement enabled for this PSP."
          : "KYC/KYB waived for this PSP — new merchants activate without screening.",
      });
      queryClient.invalidateQueries({ queryKey: ["psp"] });
      queryClient.invalidateQueries({ queryKey: ["psps"] });
      queryClient.invalidateQueries({ queryKey: ["myPsp"] });
    } catch {
      setToast({ kind: "error", msg: "Could not update the KYC setting. Please try again." });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="max-w-2xl">
      <div className="mb-4 flex items-center gap-2">
        {enabled ? (
          <ShieldCheck size={20} className="text-emerald-400" />
        ) : (
          <ShieldOff size={20} className="text-amber-400" />
        )}
        <h4 className="text-sm font-semibold text-white">KYC / KYB Enforcement</h4>
        <span
          className={`ml-1 rounded-full px-2 py-0.5 text-[11px] font-semibold ${
            enabled ? "bg-emerald-900/40 text-emerald-300" : "bg-amber-900/40 text-amber-300"
          }`}
        >
          {enabled ? "ENABLED" : "WAIVED"}
        </span>
      </div>

      <p className="mb-4 text-xs leading-relaxed text-glass-muted">
        When <b>enabled</b>, merchants under this PSP go through full KYC/KYB screening and underwriting
        at onboarding. When <b>waived</b>, this PSP's merchants onboard as <b>ACTIVE</b> with
        KYC status <code>NOT_REQUIRED</code> and their transactions flow without any KYC gating.
      </p>

      {!enabled && (
        <div className="mb-4 flex items-start gap-2 rounded-lg border border-amber-700/40 bg-amber-900/20 px-3 py-2 text-xs text-amber-200">
          <AlertTriangle size={15} className="mt-0.5 shrink-0" />
          <span>
            KYC is currently waived for this PSP. New merchants are activated without sanctions/PEP
            screening or underwriting. This is an audited compliance decision — re-enable KYC unless a
            deliberate exception applies.
          </span>
        </div>
      )}

      {!isPlatformAdmin ? (
        <div className="rounded-lg border border-white/10 bg-white/5 px-3 py-2 text-xs text-glass-muted">
          KYC enforcement is controlled by platform administrators. Current status:{" "}
          <b className="text-white">{enabled ? "Enabled" : "Waived"}</b>.
        </div>
      ) : (
        <div className="flex items-center gap-3">
          <button
            type="button"
            role="switch"
            aria-checked={enabled}
            disabled={saving}
            onClick={() => apply(!enabled)}
            className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors disabled:opacity-50 ${
              enabled ? "bg-emerald-600" : "bg-white/20"
            }`}
          >
            <span
              className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                enabled ? "translate-x-6" : "translate-x-1"
              }`}
            />
          </button>
          <span className="text-xs text-white">
            {enabled ? "KYC required" : "KYC waived"}
          </span>
          {saving && <Loader2 size={16} className="animate-spin text-glass-muted" />}
        </div>
      )}

      {toast && (
        <div
          className={`mt-4 rounded-lg border px-3 py-2 text-xs ${
            toast.kind === "success"
              ? "border-emerald-700/40 bg-emerald-900/20 text-emerald-200"
              : "border-red-700/40 bg-red-900/20 text-red-200"
          }`}
        >
          {toast.msg}
        </div>
      )}
    </div>
  );
}
