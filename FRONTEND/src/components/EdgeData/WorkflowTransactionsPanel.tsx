import { Loader2 } from "lucide-react";
import EdgeOfflineBanner from "./EdgeOfflineBanner";
import EdgeTransactionsTable from "./EdgeTransactionsTable";
import { lastSeenOf, stateForPullStatus } from "../../lib/edgeData";
import type { EdgePullStatus, Transaction } from "../../types";

interface Props {
  title?: string;
  /** {@code transactionDetails} of the workflow view (pulled live when the view was requested). */
  details?: Partial<Transaction>[] | null;
  /** {@code transactionDetailsStatus} of the workflow view. */
  status?: EdgePullStatus | null;
  loading?: boolean;
  /** What the view still shows from the cloud when the pull failed. */
  cloudNote?: string;
  onRetry?: () => void;
}

/**
 * Transaction panel of a workflow view (alert detail, SAR detail, case timeline). The cloud keeps
 * only ID references; the details come from the Edge when the view is opened, and a failed pull
 * shows the offline / partial / timeout / busy / support banner while the cloud-held fields of the
 * view stay visible.
 */
export default function WorkflowTransactionsPanel({ title = "Transactions", details, status, loading, cloudNote, onRetry }: Props) {
  const state = stateForPullStatus(status);
  const items = details ?? [];
  const missing = status?.missing ?? [];
  return (
    <section className="space-y-2" aria-label={title}>
      <div className="flex items-center justify-between gap-2">
        <h4 className="hokeka-field-label">{title}</h4>
        {status?.source === "CLOUD_LEGACY" && (
          <span className="text-[10px] uppercase text-glass-muted">Legacy cloud records</span>
        )}
      </div>
      {loading ? (
        <div className="flex items-center gap-2 py-4 text-xs text-glass-muted">
          <Loader2 size={14} className="animate-spin" /> Pulling transaction details from the Edge…
        </div>
      ) : (
        <>
          <EdgeOfflineBanner
            state={state}
            error={status?.error}
            unavailableSites={status?.unavailableSites ?? []}
            lastSeenAt={lastSeenOf(status?.error, status?.unavailableSites)}
            retryAfterMs={status?.error?.retryAfterMs}
            onRetry={onRetry}
            cloudNote={cloudNote}
          />
          {status?.state === "NONE" ? (
            <p className="text-xs text-glass-muted">No transaction is referenced.</p>
          ) : status?.state === "NOT_REQUESTED" ? (
            <p className="text-xs text-glass-muted">Transaction details were not requested.</p>
          ) : (state === "ready" || state === "partial") && (
            <EdgeTransactionsTable items={items} emptyText="None of the referenced transactions was returned." />
          )}
          {missing.length > 0 && (
            <p className="text-xs text-glass-muted">
              Not held by any answering site: {missing.length} reference{missing.length === 1 ? "" : "s"} (possibly purged under retention).
            </p>
          )}
        </>
      )}
    </section>
  );
}
