import { useEffect, useState } from "react";
import { useAuditLogs, useAllPsps } from "../../features/api/queries";
import { useAuth } from "../../contexts/AuthContext";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import TwBadge from "../../components/Common/TwBadge";
import TwKeysetPagination from "../../components/Common/TwKeysetPagination";
import { TwInput, TwSelect } from "../../components/Common/TwInput";
import { Loader2, RotateCcw } from "lucide-react";
import ConsoleListExportMenu from "../../components/Common/ConsoleListExportMenu";
import { Link } from "react-router-dom";
import { normalizeRecordType, recordPath } from "../../lib/recordLinks";
import { useUrlTableState } from "../../hooks/useUrlTableState";
import { useDebouncedUrlFilter } from "../../hooks/useDebouncedUrlFilter";

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

  const { state: tableState, setFilter, setPage, setPageSize, patchParams } =
    useUrlTableState();
  const filters = {
    startDate: tableState.filters.startDate ?? defaultStartDate.toISOString().split("T")[0],
    endDate: tableState.filters.endDate ?? new Date().toISOString().split("T")[0],
    actionType: tableState.filters.actionType ?? "",
    username: tableState.filters.username ?? "",
    pspId: tableState.filters.pspId ?? "",
  };
  const [username, setUsername] = useState(filters.username);
  const debouncedUsername = useDebouncedUrlFilter(username);

  useEffect(() => {
    if (debouncedUsername !== filters.username) {
      setFilter("username", debouncedUsername);
    }
  }, [debouncedUsername, filters.username, setFilter]);

  const { data: logs, isLoading } = useAuditLogs({
    page: tableState.page,
    size: tableState.size,
    cursor: tableState.cursor,
    start: filters.startDate ? `${filters.startDate}T00:00:00` : undefined,
    end: filters.endDate ? `${filters.endDate}T23:59:59` : undefined,
    actionType: filters.actionType || undefined,
    username: filters.username || undefined,
    pspId: filters.pspId ? parseInt(filters.pspId) : undefined,
  });

  const { data: psps } = useAllPsps();

  const actionTypes = [
    { value: "", label: "All Actions" },
    ...["LOGIN", "LOGOUT", "CREATE", "UPDATE", "DELETE", "VIEW", "EXPORT", "OVERRIDE"].map((a) => ({ value: a, label: a })),
  ];

  const pspOptions = [
    { value: "", label: "All PSPs" },
    ...(psps || []).map((p: any) => ({ value: p.id.toString(), label: p.name || p.legalName || `PSP #${p.id}` })),
  ];

  const content = (logs as any)?.content || [];
  const totalElements = (logs as any)?.totalElements ?? 0;
  const totalPages = (logs as any)?.totalPages ?? 1;

  return (
    <HokekaPageShell title="Audit Logs" subtitle="Platform activity and compliance audit trail" noCard>
      {/* Export button */}
      <div className="flex justify-end pb-3">
        <ConsoleListExportMenu
          viewType="AUDIT_LOGS"
          filters={{
            page: String(tableState.page),
            size: String(tableState.size),
            start: filters.startDate ? `${filters.startDate}T00:00:00` : "",
            end: filters.endDate ? `${filters.endDate}T23:59:59` : "",
            actionType: filters.actionType,
            username: filters.username,
            pspId: filters.pspId,
          }}
          disabled={!totalElements}
        />
      </div>

      {/* Filters */}
      <div className="mb-3 rounded-lg border border-white/10 bg-[var(--surface-2)] p-4">
        <div className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-5 xl:grid-cols-6">
          <TwInput
            type="date"
            label="Start Date"
            value={filters.startDate}
            onChange={(e) => setFilter("startDate", e.target.value)}
          />
          <TwInput
            type="date"
            label="End Date"
            value={filters.endDate}
            onChange={(e) => setFilter("endDate", e.target.value)}
          />
          <TwSelect
            label="Action"
            value={filters.actionType}
            onChange={(e) => setFilter("actionType", e.target.value)}
            options={actionTypes}
          />
          <TwInput
            label="Username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="Filter by username..."
          />
          {isSuperAdmin && (
            <TwSelect
              label="PSP"
              value={filters.pspId}
              onChange={(e) => setFilter("pspId", e.target.value)}
              options={pspOptions}
            />
          )}
          <div className="flex items-end">
            <button
              onClick={() => {
                setUsername("");
                patchParams({
                  startDate: undefined,
                  endDate: undefined,
                  actionType: undefined,
                  username: undefined,
                  pspId: undefined,
                  cursor: undefined,
                  page: 0,
                });
              }}
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
        <TwKeysetPagination
          page={tableState.page}
          totalPages={totalPages}
          totalCount={totalElements}
          rowsPerPage={tableState.size}
          nextCursor={(logs as any)?.nextCursor}
          onPageChange={setPage}
          onRowsPerPageChange={setPageSize}
        />
      </div>
    </HokekaPageShell>
  );
}
