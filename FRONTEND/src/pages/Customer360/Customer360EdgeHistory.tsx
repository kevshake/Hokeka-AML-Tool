import { useMemo, useState } from "react";
import { Loader2 } from "lucide-react";
import EdgeOfflineBanner from "../../components/EdgeData/EdgeOfflineBanner";
import EdgeSupportGate from "../../components/EdgeData/EdgeSupportGate";
import EdgeTransactionsTable from "../../components/EdgeData/EdgeTransactionsTable";
import { useEdgeData } from "../../hooks/useEdgeData";
import type { MultiAssetTransaction } from "../../types/multiAsset";
import type { EdgeDataEnvelope, EdgeRelatedTransaction, Transaction } from "../../types";

/** How far back the Edge looks for linked transactions (contract section 7.6 allows 1..180 days). */
const HISTORY_WINDOW_DAYS = 180;
const DAY_MS = 86_400_000;

interface Props {
  transactions: MultiAssetTransaction[];
}

/**
 * The customer's transaction history pulled live from the PSP's Edge. The cloud holds no customer
 * identifier for Edge transactions, so the history is anchored on one of the customer's
 * transaction references: the Edge finds that transaction, then the evaluations linked to it by
 * account, card or device ({@code txn.related}); the link key values never leave the Edge.
 */
export default function Customer360EdgeHistory({ transactions }: Props) {
  const anchors = useMemo(
    () => transactions.filter((tx) => !!tx.externalTransactionId && !!tx.executedAt),
    [transactions],
  );
  const [anchorRef, setAnchorRef] = useState(anchors[0]?.externalTransactionId ?? "");
  const [requested, setRequested] = useState(false);
  const anchor = anchors.find((tx) => tx.externalTransactionId === anchorRef) ?? anchors[0];

  const executedAt = anchor ? new Date(anchor.executedAt).getTime() : 0;
  const lookup = useEdgeData<EdgeDataEnvelope<Transaction>>({
    key: ["customer360-anchor", anchor?.externalTransactionId],
    path: "transactions",
    query: anchor
      ? {
          txnRef: anchor.externalTransactionId,
          from: new Date(executedAt - DAY_MS).toISOString(),
          to: new Date(executedAt + DAY_MS).toISOString(),
          size: 1,
        }
      : undefined,
    enabled: requested && !!anchor,
  });
  const edgeId = lookup.data?.items[0]?.edgeAssessmentId ?? (lookup.data?.items[0]?.id as string | undefined);
  const related = useEdgeData<EdgeDataEnvelope<EdgeRelatedTransaction>>({
    key: ["customer360-related", edgeId],
    path: `transactions/${encodeURIComponent(String(edgeId))}/related`,
    query: { linkBy: ["ACCOUNT", "CARD", "DEVICE"], windowDays: HISTORY_WINDOW_DAYS, depth: 1, size: 100 },
    enabled: requested && !!edgeId && lookup.data?.source !== "CLOUD_LEGACY",
  });

  if (!anchors.length) {
    return (
      <section>
        <h3 className="mb-2 text-sm font-semibold">Transaction history (live from the Edge)</h3>
        <p className="border border-white/10 px-4 py-6 text-center text-sm text-white/45">
          This customer has no transaction reference to anchor an Edge history on.
        </p>
      </section>
    );
  }

  const active = lookup.state !== "ready" && lookup.state !== "partial" ? lookup : related;
  const history = [
    ...(lookup.data?.items ?? []),
    ...(related.data?.items ?? []).map((item) => item.transaction),
  ];

  return (
    <section className="space-y-3">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold">Transaction history (live from the Edge)</h3>
          <p className="text-xs text-white/40">Linked by account, card or device over the last {HISTORY_WINDOW_DAYS} days. Every pull is recorded in the data access log.</p>
        </div>
        <div className="flex items-end gap-2">
          <label className="block text-xs">
            <span className="mb-1 block text-white/45">Anchor transaction</span>
            <select aria-label="Anchor transaction" value={anchor?.externalTransactionId ?? ""} onChange={(e) => setAnchorRef(e.target.value)} className="hokeka-field h-9">
              {anchors.map((tx) => (
                <option key={tx.id} value={tx.externalTransactionId}>{tx.externalTransactionId}</option>
              ))}
            </select>
          </label>
          <button type="button" className="hokeka-btn-primary h-9" onClick={() => (requested ? (lookup.refetch(), related.refetch()) : setRequested(true))}>
            Load history from the Edge
          </button>
        </div>
      </div>
      <EdgeSupportGate />
      {requested && (
        <>
          <EdgeOfflineBanner
            state={active.state}
            error={active.error}
            lastSeenAt={active.lastSeenAt}
            unavailableSites={[...lookup.unavailableSites, ...related.unavailableSites]}
            retryAfterMs={active.retryAfterMs}
            onRetry={active.refetch}
            cloudNote="The customer profile, accounts and signals above are held in the cloud."
          />
          {(lookup.state === "loading" || related.state === "loading") && (
            <div className="flex items-center gap-2 text-xs text-white/50"><Loader2 size={14} className="animate-spin" /> Pulling from the Edge…</div>
          )}
          {lookup.state === "ready" && !edgeId && (
            <p className="text-xs text-white/45">The Edge holds no transaction with reference {anchor?.externalTransactionId}.</p>
          )}
          {(related.state === "ready" || related.state === "partial" || lookup.data?.source === "CLOUD_LEGACY") && history.length > 0 && (
            <EdgeTransactionsTable items={history} />
          )}
        </>
      )}
    </section>
  );
}
