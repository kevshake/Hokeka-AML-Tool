import { Link } from "react-router-dom";
import {
  useMonitoringTransactions,
  useMonitoringDashboardStats,
  useMonitoringRecentActivity,
} from "../../features/api/queries";
import TwBadge from "../../components/Common/TwBadge";
import TwKeysetPagination from "../../components/Common/TwKeysetPagination";
import VirtualizedTableBody from "../../components/Common/VirtualizedTableBody";
import ConsoleListExportMenu from "../../components/Common/ConsoleListExportMenu";
import { useUrlTableState } from "../../hooks/useUrlTableState";
import { Loader2 } from "lucide-react";

const decisionBadge = (decision: string | undefined): "danger" | "success" | "default" => {
  if (decision === "BLOCK") return "danger";
  if (decision === "ALLOW") return "success";
  return "default";
};

export default function TransactionMonitoringLive() {
  const { state: tableState, setPage, setPageSize, exportFilterMap } = useUrlTableState();
  
  const { data: transactions, isLoading: transactionsLoading } = useMonitoringTransactions({
    page: tableState.page,
    size: tableState.size,
    cursor: tableState.cursor,
  });
  const { data: stats, isLoading: statsLoading } = useMonitoringDashboardStats();
  const { data: recentActivity, isLoading: activityLoading } = useMonitoringRecentActivity();

  const content = (transactions as any)?.content || [];
  const totalElements = (transactions as any)?.totalElements ?? 0;
  const totalPages = (transactions as any)?.totalPages ?? 1;

  return (
    <div className="flex flex-col gap-4">
      <span className="hokeka-section-label">Monitoring</span>
      <h3 className="mt-1 font-display text-lg font-semibold tracking-tight text-ink">
        Live Transaction Monitoring
      </h3>
      <ConsoleListExportMenu
        viewType="MONITORING_TRANSACTIONS"
        filters={exportFilterMap}
        disabled={!totalElements}
      />

      {stats && !statsLoading && (
        <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
          {Object.entries(stats).map(([key, value]) => (
            <div key={key} className="hokeka-glass-card rounded-xl p-4">
              <p className="hokeka-field-label mb-1">
                {key.replace(/([A-Z])/g, " $1").trim()}
              </p>
              <p className="text-xl font-bold tabular-nums text-ink">
                {typeof value === "number" ? value.toLocaleString() : String(value)}
              </p>
            </div>
          ))}
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        {/* Transactions table */}
        <div className="hokeka-glass-card overflow-hidden rounded-xl lg:col-span-2">
          <div className="border-b border-hairline px-4 py-3">
            <h4 className="text-sm font-semibold text-ink">Monitored Transactions</h4>
          </div>
          <div className="hokeka-table-wrap !border-0 !rounded-none" style={{ maxHeight: "500px" }}>
            {transactionsLoading ? (
              <div className="flex items-center justify-center py-12">
                <Loader2 size={24} className="animate-spin text-glass-muted" />
              </div>
            ) : (
              <table className="hokeka-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Merchant</th>
                    <th>Amount</th>
                    <th>Decision</th>
                    <th>Timestamp</th>
                  </tr>
                </thead>
                {content.length > 0 ? (
                  <VirtualizedTableBody
                    rowCount={content.length}
                    maxHeight={460}
                    renderRow={(index) => {
                      const txn = content[index];
                      const id = txn.txnId || txn.transactionId || txn.id;
                      return (
                        <table className="hokeka-table w-full">
                          <tbody>
                            <tr>
                              <td><Link className="text-gold hover:underline" to={`/records/TRANSACTION/${id}`}>#{id}</Link></td>
                              <td>{txn.merchantId ? <Link className="text-gold hover:underline" to={`/records/MERCHANT/${txn.merchantId}`}>{txn.merchantId}</Link> : "-"}</td>
                              <td>{txn.amountCents ? `$${(txn.amountCents / 100).toFixed(2)}` : "-"}</td>
                              <td className="whitespace-nowrap px-4 py-3">
                                <TwBadge variant={decisionBadge(txn.decision)}>{txn.decision || "ALLOW"}</TwBadge>
                              </td>
                              <td className="whitespace-nowrap px-4 py-3 text-sm text-glass-muted">
                                {txn.txnTs ? new Date(txn.txnTs).toLocaleString() : "-"}
                              </td>
                            </tr>
                          </tbody>
                        </table>
                      );
                    }}
                  />
                ) : (
                  <tbody>
                    <tr><td colSpan={5} className="px-4 py-8 text-center text-sm text-glass-muted">No transactions found</td></tr>
                  </tbody>
                )}
              </table>
            )}
          </div>
          <TwKeysetPagination
            page={tableState.page}
            totalPages={totalPages}
            totalCount={totalElements}
            rowsPerPage={tableState.size}
            nextCursor={(transactions as any)?.nextCursor}
            onPageChange={setPage}
            onRowsPerPageChange={setPageSize}
          />
        </div>

        {/* Recent Activity */}
        <div className="hokeka-glass-card rounded-xl p-4">
          <h4 className="mb-3 text-sm font-semibold text-ink">Recent Activity</h4>
          {activityLoading ? (
            <p className="text-sm text-glass-muted">Loading activity...</p>
          ) : recentActivity && Array.isArray(recentActivity) && recentActivity.length > 0 ? (
            <div className="flex flex-col gap-1.5">
              {recentActivity.slice(0, 10).map((activity: any, idx: number) => (
                <div key={activity.id || activity.timestamp || idx} className="rounded-lg border border-hairline bg-burgundy-900/60 p-3">
                  <p className="text-sm text-ink-muted">{activity.description || activity.action || "Activity"}</p>
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
