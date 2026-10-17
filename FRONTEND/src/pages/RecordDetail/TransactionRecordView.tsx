import { useState } from "react";
import { ArrowLeft, FileText, Link2, LoaderCircle } from "lucide-react";
import { Link } from "react-router-dom";
import AiVerdictPanel from "../../components/Ai/AiVerdictPanel";
import TransactionAssessmentPanel from "../../components/TransactionMonitoring/TransactionAssessmentPanel";
import TransactionAlertsPanel from "../../components/TransactionMonitoring/TransactionAlertsPanel";
import ComplianceLinksPanel from "../../components/compliance/ComplianceLinksPanel";
import ScreeningActivityPanel from "../../components/screening/ScreeningActivityPanel";
import EdgeOfflineBanner from "../../components/EdgeData/EdgeOfflineBanner";
import EdgeSupportGate from "../../components/EdgeData/EdgeSupportGate";
import EdgeTransactionsTable from "../../components/EdgeData/EdgeTransactionsTable";
import { useEdgeData } from "../../hooks/useEdgeData";
import { formatMinorAmount } from "../../lib/edgeFormat";
import type { EdgeDataEnvelope, EdgeRelatedTransaction, EdgeTransactionDetail } from "../../types";

const LINK_TYPES = ["CARD", "DEVICE", "ACCOUNT", "IP", "MERCHANT"] as const;

/** Display text of a field value. */
function show(value: unknown): string {
  if (value === null || value === undefined || value === "") return "-";
  if (typeof value === "boolean") return value ? "Yes" : "No";
  if (typeof value === "object") return JSON.stringify(value, null, 2);
  return String(value);
}

/** Local date-time, tolerant of missing values. */
function when(value?: string): string {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

/** Linked evaluations of the transaction (Edge {@code txn.related}), pulled when asked for. */
function RelatedTransactions({ id }: { id: string }) {
  const [links, setLinks] = useState<string[]>(["CARD", "DEVICE", "ACCOUNT"]);
  const [requested, setRequested] = useState(false);
  const related = useEdgeData<EdgeDataEnvelope<EdgeRelatedTransaction>>({
    key: ["related", id, links],
    path: `transactions/${encodeURIComponent(id)}/related`,
    query: { linkBy: links, depth: 1, size: 50 },
    enabled: requested && links.length > 0,
  });

  return (
    <section>
      <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold"><Link2 size={16} className="text-gold" /> Linked transactions (from the Edge)</h2>
      <div className="mb-3 flex flex-wrap items-center gap-3 text-xs text-white/70">
        {LINK_TYPES.map((link) => (
          <label key={link} className="flex items-center gap-1">
            <input
              type="checkbox"
              checked={links.includes(link)}
              onChange={(e) => setLinks((prev) => (e.target.checked ? [...prev, link] : prev.filter((l) => l !== link)))}
            />
            {link.toLowerCase()}
          </label>
        ))}
        <button type="button" className="hokeka-btn-primary" disabled={!links.length} onClick={() => (requested ? related.refetch() : setRequested(true))}>
          Find linked transactions
        </button>
      </div>
      <p className="mb-2 text-[11px] text-white/40">The Edge links by card, device, account or IP internally; the key values never leave it.</p>
      {requested && (
        <div className="space-y-2">
          <EdgeOfflineBanner state={related.state} error={related.error} lastSeenAt={related.lastSeenAt}
            unavailableSites={related.unavailableSites} retryAfterMs={related.retryAfterMs} onRetry={related.refetch} />
          {related.state === "loading" && <LoaderCircle className="animate-spin text-white/50" size={18} />}
          {(related.state === "ready" || related.state === "partial") && (
            <EdgeTransactionsTable
              items={(related.data?.items ?? []).map((r) => r.transaction)}
              emptyText="No linked transactions in the last 30 days."
              extra={{ header: "Linked by", render: (_item, index) => {
                const r = related.data?.items[index];
                return r ? `${r.link.toLowerCase()} (hop ${r.hop})` : "-";
              } }}
            />
          )}
        </div>
      )}
    </section>
  );
}

/**
 * Record page of one transaction: its fields are pulled live from the PSP's Edge
 * ({@code GET /transactions/{id}}, with the cloud decision record); alerts, assessment, compliance
 * links and screening are cloud workflow data by ID and stay visible when the Edge is offline.
 */
export default function TransactionRecordView({ recordId }: { recordId: string }) {
  const detail = useEdgeData<EdgeTransactionDetail>({
    key: ["transaction", recordId],
    path: `transactions/${encodeURIComponent(recordId)}`,
    completeness: (d) => ({ incomplete: d.incomplete, unavailableSites: [] }),
  });
  const txn = detail.data;
  const record = txn?.decisionRecord;
  const fields: Array<[string, unknown]> = txn
    ? [
        ["Transaction reference", txn.txnRef],
        ["Decision record id", txn.edgeAssessmentId ?? txn.id],
        ["Merchant", txn.merchantId],
        ["Terminal", txn.terminalId],
        ["Amount", formatMinorAmount(txn.amountCents ?? txn.amountMinor, txn.currency)],
        ["Currency", txn.currency],
        ["Decision", txn.decision],
        ["Score", txn.score],
        ["Evaluated at", when(txn.txnTs ?? txn.evaluatedAt)],
        ["MCC", txn.mcc],
        ["Country", txn.countryCode],
        ["Channel", txn.channel],
        ["Edge Site", txn.siteId],
        ["Edge node", txn.nodeId],
        ["CTR required", txn.ctrRequired],
        ["Sanctions hit", txn.sanctionsHit],
        ["Legal hold", txn.legalHold],
        ["Origin", txn.origin],
        ["Source", txn.source === "CLOUD_LEGACY" ? "Legacy cloud record" : "Edge (live)"],
      ]
    : [];

  return (
    <div className="min-h-0 flex-1 overflow-y-auto bg-[var(--ink)] p-4 text-white md:p-6">
      <Link to="/transaction-monitoring/live" className="inline-flex items-center gap-2 text-sm text-white/55 transition hover:text-gold"><ArrowLeft size={16} /> Back to monitoring</Link>
      <header className="mt-6 flex flex-wrap items-start justify-between gap-4 border-b border-white/10 pb-6">
        <div>
          <p className="text-xs font-semibold uppercase tracking-widest text-gold">Transaction</p>
          <h1 className="mt-2 text-2xl font-semibold">{txn?.txnRef || recordId}</h1>
          <p className="mt-2 text-sm text-white/45">Record ID: {recordId}</p>
        </div>
        <Link to={`/reports?recordType=TRANSACTION&recordId=${encodeURIComponent(recordId)}`} className="inline-flex items-center gap-2 border border-gold/70 px-4 py-2 text-sm font-medium text-gold transition hover:bg-gold hover:text-black"><FileText size={16} /> Run report from record</Link>
      </header>

      <div className="mt-4 space-y-3">
        <EdgeSupportGate />
        <EdgeOfflineBanner
          state={detail.state}
          error={detail.error}
          lastSeenAt={detail.lastSeenAt}
          unavailableSites={detail.unavailableSites}
          retryAfterMs={detail.retryAfterMs}
          onRetry={detail.refetch}
          cloudNote="The assessment, alerts, compliance links and screening held in the cloud are shown alongside."
        />
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-[minmax(0,1.15fr)_minmax(330px,.85fr)]">
        <section className="space-y-6">
          <div>
            <h2 className="mb-3 text-sm font-semibold">Transaction data (live from the Edge)</h2>
            {detail.state === "loading" ? (
              <div className="flex items-center justify-center py-12 text-white/60"><LoaderCircle className="animate-spin" size={22} /></div>
            ) : txn ? (
              <dl className="divide-y divide-white/8 border border-white/10">
                {fields.map(([label, value]) => (
                  <div key={label} className="grid gap-2 px-4 py-3 md:grid-cols-[190px_1fr]">
                    <dt className="text-xs uppercase tracking-wide text-white/40">{label}</dt>
                    <dd className="whitespace-pre-wrap break-words text-sm text-white/80">{show(value)}</dd>
                  </div>
                ))}
                {!!txn.reasons?.length && (
                  <div className="grid gap-2 px-4 py-3 md:grid-cols-[190px_1fr]">
                    <dt className="text-xs uppercase tracking-wide text-white/40">Reasons</dt>
                    <dd className="text-sm text-white/80"><ul className="list-disc pl-4">{txn.reasons.map((r) => <li key={r}>{r}</li>)}</ul></dd>
                  </div>
                )}
              </dl>
            ) : (
              <p className="border border-white/10 px-4 py-8 text-center text-sm text-white/45">Transaction fields are not available right now.</p>
            )}
          </div>
          {txn?.features && Object.keys(txn.features).length > 0 && (
            <details className="border border-white/10 p-4">
              <summary className="cursor-pointer text-sm font-semibold">Evaluation features</summary>
              <pre className="mt-3 overflow-x-auto text-xs leading-6 text-white/70">{JSON.stringify(txn.features, null, 2)}</pre>
            </details>
          )}
          {record && (
            <div>
              <h2 className="mb-3 text-sm font-semibold">Decision record (held in the cloud)</h2>
              <dl className="divide-y divide-white/8 border border-white/10">
                {([
                  ["Bundle version", record.bundleVersion],
                  ["Evaluator", record.evaluator],
                  ["Fail-closed code", record.failClosedCode],
                  ["Triggered rules", record.triggeredRuleIds?.join(", ")],
                  ["Typologies", record.typologyCodes?.join(", ")],
                  ["Watchlist entries", record.watchlistEntryIds?.join(", ")],
                  ["Recorded at", when(record.recordedAt)],
                ] as Array<[string, unknown]>).map(([label, value]) => (
                  <div key={label} className="grid gap-2 px-4 py-3 md:grid-cols-[190px_1fr]">
                    <dt className="text-xs uppercase tracking-wide text-white/40">{label}</dt>
                    <dd className="text-sm text-white/80">{show(value)}</dd>
                  </div>
                ))}
              </dl>
            </div>
          )}
          {(detail.state === "ready" || detail.state === "partial") && txn?.source !== "CLOUD_LEGACY" && (
            <RelatedTransactions id={String(txn?.edgeAssessmentId ?? recordId)} />
          )}
        </section>
        <aside className="space-y-6">
          <TransactionAssessmentPanel txnId={recordId} />
          <TransactionAlertsPanel txnId={recordId} />
          <ComplianceLinksPanel subjectType="TRANSACTION" subjectId={recordId} />
          <ScreeningActivityPanel txnId={recordId} />
          <AiVerdictPanel auditPath={`ai/audit/transaction/${recordId}`} title="Hokeka AI recommendation" />
        </aside>
      </div>
    </div>
  );
}
