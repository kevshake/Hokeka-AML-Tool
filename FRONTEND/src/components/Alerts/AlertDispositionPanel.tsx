import { useState } from "react";
import { Loader2 } from "lucide-react";
import GlassButton from "../Common/GlassButton";
import { ALERT_DISPOSITION_OPTIONS, type AlertDispositionCode } from "../../lib/alertDispositions";
import type { Alert } from "../../types";
import { useResolveAlert } from "../../features/api/mutations";
import { canPerformAction } from "../../lib/consoleRbac";
import { useAuth } from "../../contexts/AuthContext";

interface Props {
  alert: Alert;
  onResolved?: () => void;
}

/** Submits PUT /alerts/{id}/resolve with disposition + notes (maker-checker aware). */
export default function AlertDispositionPanel({ alert, onResolved }: Props) {
  const { user } = useAuth();
  const resolveAlert = useResolveAlert();
  const [disposition, setDisposition] = useState<AlertDispositionCode>("FALSE_POSITIVE");
  const [notes, setNotes] = useState("");
  const [feedback, setFeedback] = useState<string | null>(null);

  const canResolve = canPerformAction(user, "alert.resolveDisposition");
  const pendingSecond =
    alert.dispositionReviewStatus === "PENDING_SECOND_REVIEW" ||
    alert.status?.toLowerCase() === "pending_second_review";

  if (!canResolve) {
    return (
      <p className="rounded-lg border border-white/10 bg-white/[0.02] px-4 py-3 text-sm text-white/45">
        Alert disposition is limited to compliance investigators. Your role can view this alert but
        cannot submit a disposition.
      </p>
    );
  }

  const handleSubmit = async () => {
    setFeedback(null);
    try {
      const saved = await resolveAlert.mutateAsync({
        id: alert.id,
        disposition,
        notes: notes.trim() || undefined,
      });
      if (
        saved?.dispositionReviewStatus === "PENDING_SECOND_REVIEW" ||
        saved?.status?.toLowerCase() === "pending_second_review"
      ) {
        setFeedback(
          "First review recorded. A different investigator must confirm this true-positive disposition.",
        );
      } else {
        setFeedback("Disposition saved.");
        onResolved?.();
      }
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Could not save disposition.";
      setFeedback(message);
    }
  };

  return (
    <div className="space-y-3 rounded-lg border border-white/10 bg-white/[0.02] p-4">
      <p className="text-sm font-semibold text-white">Resolve with disposition</p>
      {pendingSecond ? (
        <p className="text-xs text-amber-200/90">
          Pending second review
          {alert.firstReviewer ? ` (first reviewer: ${alert.firstReviewer})` : ""}. Confirm the same
          disposition to finalize.
        </p>
      ) : null}
      <label className="block text-xs text-white/45 max-[480px]:w-full">
        Disposition
        <select
          value={disposition}
          onChange={(e) => setDisposition(e.target.value as AlertDispositionCode)}
          className="mt-1 w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white focus:outline-none focus-visible:ring-2 focus-visible:ring-gold/60"
        >
          {ALERT_DISPOSITION_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
              {opt.makerChecker ? " (two-person review)" : ""}
            </option>
          ))}
        </select>
      </label>
      <label className="block text-xs text-white/45">
        Notes
        <textarea
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          rows={3}
          className="mt-1 w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white focus:outline-none focus-visible:ring-2 focus-visible:ring-gold/60"
          placeholder="Investigation summary for the audit trail"
        />
      </label>
      <GlassButton
        variant="primary"
        size="sm"
        disabled={resolveAlert.isPending}
        onClick={handleSubmit}
      >
        {resolveAlert.isPending ? <Loader2 size={14} className="animate-spin" /> : null}
        {pendingSecond ? "Confirm second review" : "Submit disposition"}
      </GlassButton>
      {feedback ? <p className="text-xs text-gold">{feedback}</p> : null}
    </div>
  );
}
