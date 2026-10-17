import { useState } from "react";
import { ShieldCheck, X } from "lucide-react";
import { useAllPsps } from "../../features/api/queries";
import { useEdgeSupport } from "../../contexts/edgeSupport";
import { SUPPORT_REASON_MAX, supportReasonError } from "../../lib/edgeData";
import { pspOptionId, pspOptionLabel } from "../../lib/userAccess";

/**
 * Platform-admin support flow (contract section 9). Before Hokeka staff can open a PSP's transaction
 * data they choose the PSP and enter a support reason; the reason is sent with every pull as
 * {@code X-Support-Reason} and appears in the PSP's data access log. Once a session is open this
 * shows which PSP is being supported and lets the admin end it. Renders nothing for PSP users.
 */
export default function EdgeSupportGate() {
  const support = useEdgeSupport();
  const psps = useAllPsps();
  const [pspId, setPspId] = useState("");
  const [reason, setReason] = useState("");
  const [touched, setTouched] = useState(false);

  if (!support.isPlatform) return null;

  if (support.session) {
    const expires = new Date(support.session.expiresAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
    return (
      <div data-testid="support-session" className="flex flex-wrap items-center gap-3 rounded-lg border border-sky-600/40 bg-sky-900/20 px-4 py-2 text-xs text-sky-100">
        <ShieldCheck size={15} />
        <span>
          Support access to <strong>{support.session.pspLabel}</strong> until {expires}. Reason: “{support.session.reason}”
        </span>
        <button type="button" onClick={support.end} className="ml-auto flex items-center gap-1 underline-offset-2 hover:underline">
          <X size={13} /> End support session
        </button>
      </div>
    );
  }

  const options = (psps.data ?? []).filter((psp) => pspOptionId(psp) > 0);
  const reasonProblem = supportReasonError(reason);
  const selected = options.find((psp) => String(pspOptionId(psp)) === pspId);

  /** Opens the session when a PSP is chosen and the reason is acceptable. */
  const submit = () => {
    setTouched(true);
    if (!selected || reasonProblem) return;
    support.start(pspOptionId(selected), pspOptionLabel(selected), reason);
  };

  return (
    <form
      data-testid="support-gate"
      className="space-y-3 rounded-lg border border-sky-600/40 bg-sky-900/20 px-4 py-3 text-sm text-sky-100"
      onSubmit={(event) => {
        event.preventDefault();
        submit();
      }}
    >
      <div className="flex items-center gap-2 font-semibold">
        <ShieldCheck size={16} /> Open Hokeka support access
      </div>
      <p className="text-xs opacity-80">
        PSP transaction data stays on the PSP's Edge. To read it you need the PSP to have allowed support access, and a
        reason for this ticket. The session lasts up to 60 minutes and every pull is shown in the PSP's data access log.
      </p>
      <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_minmax(0,2fr)_auto] md:items-end">
        <label className="block">
          <span className="mb-1 block text-[11px] font-semibold uppercase tracking-wider text-glass-muted">PSP</span>
          <select aria-label="PSP" value={pspId} onChange={(event) => setPspId(event.target.value)} className="hokeka-field h-10 w-full">
            <option value="">Choose a PSP</option>
            {options.map((psp) => (
              <option key={pspOptionId(psp)} value={String(pspOptionId(psp))}>{pspOptionLabel(psp)}</option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="mb-1 block text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Support reason</span>
          <input
            aria-label="Support reason"
            value={reason}
            maxLength={SUPPORT_REASON_MAX}
            onChange={(event) => setReason(event.target.value)}
            placeholder="Ticket number and what you need to check"
            className="hokeka-field h-10 w-full"
          />
        </label>
        <button type="submit" className="hokeka-btn-primary h-10">Open support session</button>
      </div>
      {touched && !selected && <p className="text-xs text-red-300">Choose the PSP you are supporting.</p>}
      {touched && reasonProblem && <p className="text-xs text-red-300">{reasonProblem}</p>}
    </form>
  );
}
