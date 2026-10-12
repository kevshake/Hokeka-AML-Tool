import { useQuery } from "@tanstack/react-query";
import { LoaderCircle } from "lucide-react";
import { apiClient } from "../../lib/apiClient";

/** Assessment + findings for a transaction (WP-02 explain panel). */
interface FindingRow {
  findingId: string;
  sourceType?: string;
  sourceId?: string;
  proposedAction?: string;
  triggered?: boolean;
  explanation?: string;
}

interface AssessmentDetail {
  assessmentId: string;
  decision?: string;
  latencyMs?: number;
  findings?: FindingRow[];
}

interface Props {
  txnId: string;
}

/** Read-only assessment ledger panel for transaction investigation. */
export default function TransactionAssessmentPanel({ txnId }: Props) {
  const query = useQuery<AssessmentDetail>({
    queryKey: ["transaction-assessment", txnId],
    queryFn: () => apiClient.get<AssessmentDetail>(`transactions/${txnId}/assessment`),
    enabled: Boolean(txnId),
  });

  if (query.isLoading) {
    return (
      <p className="flex items-center gap-2 px-4 py-6 text-sm text-white/50">
        <LoaderCircle className="animate-spin" size={16} /> Loading assessment…
      </p>
    );
  }

  if (query.isError || !query.data) {
    return <p className="px-4 py-6 text-sm text-white/45">No assessment recorded for this transaction yet.</p>;
  }

  const assessment = query.data;

  return (
    <section>
      <h2 className="mb-3 text-sm font-semibold">Assessment</h2>
      <div className="border border-white/10 px-4 py-3 text-sm text-white/75">
        <p>
          Decision: <span className="text-gold">{assessment.decision ?? "—"}</span>
          {assessment.latencyMs != null && (
            <span className="ml-3 text-white/40">{assessment.latencyMs} ms</span>
          )}
        </p>
        <p className="mt-1 text-xs text-white/40">Assessment ID: {assessment.assessmentId}</p>
      </div>
      <div className="mt-3 divide-y divide-white/8 border border-white/10">
        {(assessment.findings ?? []).map((f) => (
          <div key={f.findingId} className="px-4 py-3 text-sm">
            <div className="flex flex-wrap gap-2 text-xs uppercase tracking-wide text-white/40">
              <span>{f.sourceType ?? "SOURCE"}</span>
              {f.proposedAction && <span className="text-gold">{f.proposedAction}</span>}
              {f.triggered === false && <span>no hit</span>}
            </div>
            <p className="mt-1 text-white/75">{f.explanation ?? f.sourceId ?? "Finding"}</p>
          </div>
        ))}
        {!assessment.findings?.length && (
          <p className="px-4 py-6 text-center text-sm text-white/45">No findings in this assessment.</p>
        )}
      </div>
    </section>
  );
}
