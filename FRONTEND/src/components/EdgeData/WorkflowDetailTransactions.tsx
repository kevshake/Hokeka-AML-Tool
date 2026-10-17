import EdgeOfflineBanner from "./EdgeOfflineBanner";
import EdgeSupportGate from "./EdgeSupportGate";
import WorkflowTransactionsPanel from "./WorkflowTransactionsPanel";
import { useEdgeData } from "../../hooks/useEdgeData";
import type { EdgePullStatus, Transaction } from "../../types";

/** The part of a workflow detail response this panel reads. */
interface WorkflowDetail {
  transactionDetails?: Partial<Transaction>[] | null;
  transactionDetailsStatus?: EdgePullStatus | null;
}

interface Props {
  /** Workflow detail endpoint, e.g. {@code alerts/12} or {@code compliance/sar/5/detail}. */
  path: string;
  title?: string;
  cloudNote?: string;
}

/**
 * Loads a workflow record's detail view (alert, SAR) whose transactions the Control Plane pulls
 * live from the Edge for this view only, and shows them with the pull's state. The record's
 * cloud-held fields are rendered by the caller and never depend on the Edge being reachable.
 */
export default function WorkflowDetailTransactions({ path, title, cloudNote }: Props) {
  const view = useEdgeData<WorkflowDetail>({
    key: ["workflow", path],
    path,
    requiresSupportSession: false,
    completeness: () => null,
  });

  if (view.state !== "loading" && view.state !== "ready") {
    return (
      <div className="space-y-2">
      <EdgeSupportGate />
      <EdgeOfflineBanner
        state={view.state}
        error={view.error}
        lastSeenAt={view.lastSeenAt}
        retryAfterMs={view.retryAfterMs}
        onRetry={view.refetch}
        cloudNote={cloudNote}
      />
      </div>
    );
  }
  return (
    <div className="space-y-2">
    <EdgeSupportGate />
    <WorkflowTransactionsPanel
      title={title}
      loading={view.state === "loading"}
      details={view.data?.transactionDetails}
      status={view.data?.transactionDetailsStatus}
      cloudNote={cloudNote}
      onRetry={view.refetch}
    />
    </div>
  );
}
