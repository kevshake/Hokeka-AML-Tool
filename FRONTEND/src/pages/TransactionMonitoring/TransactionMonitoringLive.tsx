import { useState } from "react";
import { Link } from "react-router-dom";
import {
  useMonitoringTransactions,
  useMonitoringDashboardStats,
  useMonitoringRecentActivity,
} from "../../features/api/queries";
import TwBadge from "../../components/Common/TwBadge";
import TwPagination from "../../components/Common/TwPagination";
import { Loader2 } from "lucide-react";

const decisionBadge = (decision: string | undefined): "danger" | "success" | "default" => {
  if (decision === "BLOCK") return "danger";
  if (decision === "ALLOW") return "success";
  return "default";
};

export default function TransactionMonitoringLive() {
  const [page, setPage] = useState({ index: 0, size: 25 });
  
  const { data: transactions, isLoading: transactionsLoading } = useMonitoringTransactions({
    page: page.index, size: page.size,
  });
  const { data: stats, isLoading: statsLoading } = useMonitoringDashboardStats();
  const { data: recentActivity, isLoading: activityLoading } = useMonitoringRecentActivity();

  const content = (transactions as any)?.content || [];
  const totalElements = (transactions as any)?.totalElements ?? 0;
  const totalPages = (transactions as any)?.totalPages ?? 1;

  return (
    <div className="flex flex-col gap-4">
      <h3 className="text-lg font-semibold text-white">Live Transaction Monitoring</h3>

      {/* Stats cards */}
      {stats && !statsLoading && (
        <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
          {Object.entries(stats).map(([key, value]) => (
            <div key={key} className="rounded-lg border border-white/10 bg-[var(--surface-2)] p-4">
              <p className="mb-1 text-[11px] font-semibold uppercase tracking-wider text-glass-muted">
                {key.replace(/([A-Z])/g, " $1").trim()}
              </p>
              <p className="text-xl font-bold text-white">
                {typeof value === "number" ? value.toLocaleString() : String(value)}
              </p>
            </div>
          ))}
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        {/* Transactions table */}
        <div className="overflow-hidden rounded-lg border border-white/10 bg-[var(--surface-2)] lg:col-span-2">
          <div className="border-b border-white/10 px-4 py-3">
            <h4 className="text-sm font-semibold text-white">Monitored Transactions</h4>
          </div>
          <div className="overflow-auto" style={{ maxHeight: "500px" }}>
            {transactionsLoading ? (
              <div className="flex items-center justify-center py-12">
                <Loader2 size={24} className="animate-spin text-glass-muted" />
              </div>
            ) : (
              <table className="w-full border-collapse">
                <thead className="sticky top-0 z-10">
                  <tr className="border-b border-white/10 bg-[var(--surface-2)]">
                    <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">ID</th>
                    <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Merchant</th>
                    <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Amount</th>
                    <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Decision</th>
                    <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Timestamp</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-white/5">
                  {content.length > 0 ? content.map((txn: any) => (
                    <tr key={txn.txnId || txn.transactionId || txn.id} className="transition-colors hover:bg-white/[0.02]">
                      <td className="whitespace-nowrap px-4 py-3 text-sm text-white/80"><Link className="hover:text-gold" to={`/records/TRANSACTION/${txn.txnId || txn.transactionId || txn.id}`}>#{txn.txnId || txn.transactionId || txn.id}</Link></td>
                      <td className="whitespace-nowrap px-4 py-3 text-sm text-white">{txn.merchantId ? <Link className="hover:text-gold" to={`/records/MERCHANT/${txn.merchantId}`}>{txn.merchantId}</Link> : "-"}</td>
                      <td className="whitespace-nowrap px-4 py-3 text-sm text-white">{txn.amountCents ? `$${(txn.amountCents / 100).toFixed(2)}` : "-"}</td>
                      <td className="whitespace-nowrap px-4 py-3">
                        <TwBadge variant={decisionBadge(txn.decision)}>{txn.decision || "ALLOW"}</TwBadge>
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 text-sm text-glass-muted">
                        {txn.txnTs ? new Date(txn.txnTs).toLocaleString() : "-"}
                      </td>
                    </tr>
                  )) : (
                    <tr><td colSpan={5} className="px-4 py-8 text-center text-sm text-glass-muted">No transactions found</td></tr>
                  )}
                </tbody>
              </table>
            )}
          </div>
          <TwPagination
            page={page.index} totalPages={totalPages} totalCount={totalElements} rowsPerPage={page.size}
            onPageChange={(p) => setPage(prev => ({ ...prev, index: p }))}
            onRowsPerPageChange={(s) => setPage({ index: 0, size: s })}
          />
        </div>

        {/* Recent Activity */}
        <div className="rounded-lg border border-white/10 bg-[var(--surface-2)] p-4">
          <h4 className="mb-3 text-sm font-semibold text-white">Recent Activity</h4>
          {activityLoading ? (
            <p className="text-sm text-glass-muted">Loading activity...</p>
          ) : recentActivity && Array.isArray(recentActivity) && recentActivity.length > 0 ? (
            <div className="flex flex-col gap-1.5">
              {recentActivity.slice(0, 10).map((activity: any, idx: number) => (
                <div key={activity.id || activity.timestamp || idx} className="rounded-lg border border-white/5 bg-[var(--surface-2)] p-3">
                  <p className="text-sm text-white/80">{activity.description || activity.action || "Activity"}</p>
                  <p className="mt-0.5 text-xs text-glass-muted">
                    {activity.timestamp ? new Date(activity.timestamp).toLocaleString() : ""}
                  </p>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-sm text-glass-muted">No recent activity</p>
          )}
        </div>
      </div>
    </div>
  );
}
