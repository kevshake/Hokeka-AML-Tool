import { useState } from "react";
import { useAuditLogs, useAllPsps } from "../../features/api/queries";
import { useAuth } from "../../contexts/AuthContext";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import TwBadge from "../../components/Common/TwBadge";
import TwPagination from "../../components/Common/TwPagination";
import { TwInput, TwSelect } from "../../components/Common/TwInput";
import { Download, Loader2, RotateCcw } from "lucide-react";
import { Link } from "react-router-dom";
import { normalizeRecordType, recordPath } from "../../lib/recordLinks";

const actionBadge: Record<string, "danger" | "success" | "info" | "warning" | "default"> = {
  DELETE: "danger",
  CREATE: "success",
  UPDATE: "info",
  LOGIN: "info",
  LOGOUT: "default",
  EXPORT: "warning",
  OVERRIDE: "warning",
  VIEW: "default",
};

function badgeForAction(action: string) {
  return actionBadge[action] || "default";
}

export default function AuditLogsPage() {
  const { user } = useAuth();
  const isSuperAdmin = user?.pspId === 0;

  const defaultStartDate = new Date();
  defaultStartDate.setDate(defaultStartDate.getDate() - 30);

  const [page, setPage] = useState({ index: 0, size: 25 });
  const [filters, setFilters] = useState({
    startDate: defaultStartDate.toISOString().split("T")[0],
    endDate: new Date().toISOString().split("T")[0],
    actionType: "",
    username: "",
    pspId: "",
  });

  const { data: logs, isLoading } = useAuditLogs({
    page: page.index,
    size: page.size,
    start: filters.startDate ? `${filters.startDate}T00:00:00` : undefined,
    end: filters.endDate ? `${filters.endDate}T23:59:59` : undefined,
    actionType: filters.actionType || undefined,
    username: filters.username || undefined,
    pspId: filters.pspId ? parseInt(filters.pspId) : undefined,
  });

  const { data: psps } = useAllPsps();

  const handleFilterChange = (field: string, value: any) => {
    setFilters((prev) => ({ ...prev, [field]: value }));
  };

  const actionTypes = [
    { value: "", label: "All Actions" },
    ...["LOGIN", "LOGOUT", "CREATE", "UPDATE", "DELETE", "VIEW", "EXPORT", "OVERRIDE"].map((a) => ({ value: a, label: a })),
  ];

  const pspOptions = [
    { value: "", label: "All PSPs" },
    ...(psps || []).map((p: any) => ({ value: p.id.toString(), label: p.name || p.legalName || `PSP #${p.id}` })),
  ];

  const handleExportCSV = () => {
    const content = (logs as any)?.content || [];
    if (!content.length) return;
    const headers = ["ID", "Action", "Entity Type", "Entity ID", "Username", "Role", "PSP ID", "Timestamp", "Success", "Reason"];
    const rows = content.map((log: any) => [
      log.id, log.actionType, log.entityType || "", log.entityId || "",
      log.username || "", log.userRole || "", log.pspId ?? "",
      log.timestamp ? new Date(log.timestamp).toISOString() : "",
      log.success ?? "", (log.reason || "").replace(/,/g, ";"),
    ]);
    const csv = [headers, ...rows].map(r => r.join(",")).join("\n");
    const blob = new Blob([csv], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `audit-logs-${filters.startDate}-to-${filters.endDate}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const content = (logs as any)?.content || [];
  const totalElements = (logs as any)?.totalElements ?? 0;
  const totalPages = (logs as any)?.totalPages ?? 1;

  return (
    <HokekaPageShell title="Audit Logs" subtitle="Platform activity and compliance audit trail" noCard>
      {/* Export button */}
      <div className="flex justify-end pb-3">
        <button
          onClick={handleExportCSV}
          disabled={!content.length}
          className="flex items-center gap-1.5 rounded-lg border border-white/10 px-3 py-1.5 text-xs text-glass-muted transition-colors hover:bg-white/5 disabled:cursor-not-allowed disabled:opacity-30"
        >
          <Download size={14} /> Export CSV
        </button>
      </div>

      {/* Filters */}
      <div className="mb-3 rounded-lg border border-white/10 bg-[var(--surface-2)] p-4">
        <div className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-5 xl:grid-cols-6">
          <TwInput
            type="date"
            label="Start Date"
            value={filters.startDate}
            onChange={(e) => handleFilterChange("startDate", e.target.value)}
          />
          <TwInput
            type="date"
            label="End Date"
            value={filters.endDate}
            onChange={(e) => handleFilterChange("endDate", e.target.value)}
          />
          <TwSelect
            label="Action"
            value={filters.actionType}
            onChange={(e) => handleFilterChange("actionType", e.target.value)}
            options={actionTypes}
          />
          <TwInput
            label="Username"
            value={filters.username}
            onChange={(e) => handleFilterChange("username", e.target.value)}
            placeholder="Filter by username..."
          />
          {isSuperAdmin && (
            <TwSelect
              label="PSP"
              value={filters.pspId}
              onChange={(e) => handleFilterChange("pspId", e.target.value)}
              options={pspOptions}
            />
          )}
          <div className="flex items-end">
            <button
              onClick={() => setFilters({
                startDate: defaultStartDate.toISOString().split("T")[0],
                endDate: new Date().toISOString().split("T")[0],
                actionType: "", username: "", pspId: "",
              })}
              className="flex w-full items-center justify-center gap-1.5 rounded-lg border border-white/10 px-3 py-2 text-xs text-glass-muted transition-colors hover:bg-white/5"
            >
              <RotateCcw size={14} /> Reset
            </button>
          </div>
        </div>
      </div>

      {/* Table */}
      <div className="overflow-hidden rounded-lg border border-white/10 bg-[var(--surface-2)]">
        <div className="overflow-auto" style={{ maxHeight: "calc(100vh - 380px)" }}>
          {isLoading ? (
            <div className="flex items-center justify-center py-16">
              <Loader2 size={24} className="animate-spin text-glass-muted" />
              <span className="ml-3 text-sm text-glass-muted">Loading audit logs...</span>
            </div>
          ) : (
            <table className="w-full border-collapse">
              <thead className="sticky top-0 z-10">
                <tr className="border-b border-white/10 bg-[var(--surface-2)]">
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Timestamp</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">PSP</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">User</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Action</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Entity Type</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Entity ID</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Details</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {content.map((log: any) => (
                  <tr key={log.id} className="transition-colors hover:bg-white/[0.02]">
                    <td className="whitespace-nowrap px-4 py-3 text-sm text-white">
                      <Link to={`/records/AUDIT_LOG/${log.id}`} className="hover:text-gold">{new Date(log.timestamp).toLocaleString()}</Link>
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm text-glass-muted">
                      {log.pspId && log.pspId !== 0 ? <Link to={`/records/PSP/${log.pspId}`} className="hover:text-gold">PSP #{log.pspId}</Link> : "System"}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm text-white">{log.username}</td>
                    <td className="whitespace-nowrap px-4 py-3">
                      <TwBadge variant={badgeForAction(log.actionType)}>{log.actionType}</TwBadge>
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm text-white/80">{log.entityType}</td>
                    <td className="whitespace-nowrap px-4 py-3 font-mono text-sm text-white/80">
                      {log.entityType && recordPath(normalizeRecordType(log.entityType), log.entityId)
                        ? <Link to={recordPath(normalizeRecordType(log.entityType), log.entityId)!} className="hover:text-gold">{log.entityId}</Link>
                        : log.entityId}
                    </td>
                    <td className="max-w-xs truncate px-4 py-3 text-sm text-glass-muted">
                      {log.reason || log.details || <span className="text-glass-muted/50">N/A</span>}
                    </td>
                  </tr>
                ))}
                {!content.length && (
                  <tr>
                    <td colSpan={7} className="px-4 py-8 text-center text-sm text-glass-muted">
                      No audit logs found for the selected period
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          )}
        </div>
        <TwPagination
          page={page.index}
          totalPages={totalPages}
          totalCount={totalElements}
          rowsPerPage={page.size}
          onPageChange={(p) => setPage(prev => ({ ...prev, index: p }))}
          onRowsPerPageChange={(s) => setPage({ index: 0, size: s })}
        />
      </div>
    </HokekaPageShell>
  );
}
