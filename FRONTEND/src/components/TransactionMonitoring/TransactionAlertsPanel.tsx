import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { LoaderCircle } from "lucide-react";
import { apiClient } from "../../lib/apiClient";

interface AlertSummary {
  alertId: number;
  action?: string;
  status?: string;
  severity?: string;
  reason?: string;
  ruleVersionId?: number;
}

interface TransactionAlertsResponse {
  txnId: number;
  alerts: AlertSummary[];
}

interface Props {
  txnId: string;
}

/** Lists alerts linked to a transaction (WP-03). */
export default function TransactionAlertsPanel({ txnId }: Props) {
  const query = useQuery<TransactionAlertsResponse>({
    queryKey: ["transaction-alerts", txnId],
    queryFn: () => apiClient.get<TransactionAlertsResponse>(`transactions/${txnId}/alerts`),
    enabled: Boolean(txnId),
  });

  if (query.isLoading) {
    return (
      <p className="flex items-center gap-2 px-4 py-6 text-sm text-white/50">
        <LoaderCircle className="animate-spin" size={16} /> Loading alerts…
      </p>
    );
  }

  const alerts = query.data?.alerts ?? [];

  return (
    <section>
      <h2 className="mb-3 text-sm font-semibold">Linked alerts</h2>
      <div className="divide-y divide-white/8 border border-white/10">
        {alerts.map((alert) => (
          <Link
            key={alert.alertId}
            to={`/alerts?highlight=${alert.alertId}`}
            className="block px-4 py-3 text-sm transition hover:bg-white/5"
          >
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="font-medium text-white">Alert #{alert.alertId}</span>
              <span className="text-xs uppercase text-gold">{alert.action ?? "—"}</span>
            </div>
            <p className="mt-1 line-clamp-2 text-white/60">{alert.reason ?? "No reason"}</p>
            {alert.ruleVersionId != null && (
              <p className="mt-1 text-xs text-white/40">Rule version ID: {alert.ruleVersionId}</p>
            )}
          </Link>
        ))}
        {!alerts.length && (
          <p className="px-4 py-6 text-center text-sm text-white/45">No alerts linked to this transaction.</p>
        )}
      </div>
    </section>
  );
}
