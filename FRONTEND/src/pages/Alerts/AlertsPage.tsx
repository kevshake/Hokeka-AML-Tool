import { useState } from "react";
import { Link } from "react-router-dom";
import { useAlerts } from "../../features/api/queries";
import { useUpdateAlertStatus } from "../../features/api/mutations";
import { useResponsivePagination } from "../../hooks/useResponsivePagination";
import type { ApiError } from "../../lib/apiClient";
import type { Alert, Priority } from "../../types";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import TwBadge from "../../components/Common/TwBadge";
import TwPagination from "../../components/Common/TwPagination";
import TwSnackbar from "../../components/Common/TwSnackbar";
import {
  CheckCheck,
  ChevronDown,
  Download,
  Eye,
  Loader2,
  RotateCcw,
  Search,
  X,
} from "lucide-react";

const priorityVariant = (p: Priority): "danger" | "warning" | "default" => {
  if (p === "CRITICAL") return "danger";
  if (p === "HIGH" || p === "MEDIUM") return "warning";
  return "default";
};

const statusBadge: Record<string, "warning" | "danger" | "info" | "success"> = {
  OPEN: "danger",
  INVESTIGATING: "warning",
  RESOLVED: "success",
};

function alertStatusVariant(status: string): "warning" | "danger" | "info" | "success" | "default" {
  return statusBadge[status] || "default";
}

export default function AlertsPage() {
  const [defaultRows] = useResponsivePagination();
  const [page, setPage] = useState({ index: 0, size: defaultRows });
  const [viewAlert, setViewAlert] = useState<Alert | null>(null);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [bulkOpen, setBulkOpen] = useState(false);
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: "success" | "error" }>({
    open: false, message: "", severity: "success",
  });

  const { data: alerts, isLoading, isError, error } = useAlerts({
    page: page.index,
    size: page.size,
  });

  const updateStatus = useUpdateAlertStatus();

  const content = alerts?.content || [];
  const totalPages = alerts?.totalPages ?? 1;
  const totalElements = alerts?.totalElements ?? 0;

  const handleSelectAll = (checked: boolean) => {
    if (checked) setSelected(new Set(content.map(a => a.id)));
    else setSelected(new Set());
  };

  const handleSelectOne = (id: number, checked: boolean) => {
    setSelected(prev => {
      const next = new Set(prev);
      if (checked) next.add(id); else next.delete(id);
      return next;
    });
  };

  const handleBulkAction = async (status: string) => {
    setBulkOpen(false);
    const ids = Array.from(selected);
    if (!ids.length) return;
    try {
      await Promise.all(ids.map(id => updateStatus.mutateAsync({ id, status })));
      setSelected(new Set());
      setSnackbar({ open: true, message: `${ids.length} alert(s) marked as ${status}.`, severity: "success" });
    } catch {
      setSnackbar({ open: true, message: "Failed to update some alerts.", severity: "error" });
    }
  };

  const handleExportCSV = () => {
    if (!content.length) return;
    const headers = ["ID", "Type", "Priority", "Status", "Description", "Transaction ID", "Case ID", "Created", "Resolved"];
    const rows = content.map(a => [
      a.id,
      a.alertType,
      a.priority,
      a.status,
      (a.description || "").replace(/,/g, ";"),
      a.transactionId ?? "",
      a.caseId ?? "",
      new Date(a.createdAt).toISOString().split("T")[0],
      a.resolvedAt ? new Date(a.resolvedAt).toISOString().split("T")[0] : "",
    ]);
    const csv = [headers, ...rows].map(r => r.join(",")).join("\n");
    const blob = new Blob([csv], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `alerts-${new Date().toISOString().split("T")[0]}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const allSelected = content.length > 0 && selected.size === content.length;
  const someSelected = selected.size > 0 && selected.size < content.length;

  return (
    <HokekaPageShell title="Alerts" subtitle="Review, triage, and resolve compliance alerts" noCard>
      {/* Toolbar */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-white/10 pb-4">
        <div className="flex items-center gap-3">
          <button
            onClick={handleExportCSV}
            disabled={!content.length}
            className="flex items-center gap-1.5 rounded-lg border border-white/10 px-3 py-1.5 text-xs text-glass-muted transition-colors hover:bg-white/5 disabled:cursor-not-allowed disabled:opacity-30"
          >
            <Download size={14} />
            Export CSV
          </button>
          {selected.size > 0 && (
            <span className="text-xs text-glass-muted">{selected.size} selected</span>
          )}
        </div>

        <div className="relative">
          <button
            disabled={selected.size === 0}
            onClick={() => setBulkOpen(!bulkOpen)}
            className="flex items-center gap-1.5 rounded-lg bg-burgundy-700 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-burgundy-800 disabled:cursor-not-allowed disabled:opacity-30"
          >
            <ChevronDown size={14} />
            Bulk Actions {selected.size > 0 ? `(${selected.size})` : ""}
          </button>
          {bulkOpen && (
            <>
              <div className="fixed inset-0 z-10" onClick={() => setBulkOpen(false)} />
              <div className="absolute right-0 z-20 mt-1 w-52 rounded-lg border border-white/10 bg-[var(--surface-2)] py-1 shadow-xl">
                <button onClick={() => handleBulkAction("INVESTIGATING")} className="flex w-full items-center gap-2 px-3 py-2 text-left text-xs text-white transition-colors hover:bg-white/5">
                  <Search size={14} /> Mark as Investigating
                </button>
                <button onClick={() => handleBulkAction("RESOLVED")} className="flex w-full items-center gap-2 px-3 py-2 text-left text-xs text-white transition-colors hover:bg-white/5">
                  <CheckCheck size={14} /> Mark as Resolved
                </button>
                <button onClick={() => handleBulkAction("OPEN")} className="flex w-full items-center gap-2 px-3 py-2 text-left text-xs text-white transition-colors hover:bg-white/5">
                  <RotateCcw size={14} /> Reopen
                </button>
              </div>
            </>
          )}
        </div>
      </div>

      {/* Table */}
      <div className="overflow-hidden rounded-lg border border-white/10 bg-[var(--surface-2)]">
        <div className="overflow-auto" style={{ maxHeight: "calc(100vh - 320px)" }}>
          {isLoading ? (
            <div className="flex items-center justify-center py-16">
              <Loader2 size={24} className="animate-spin text-glass-muted" />
            </div>
          ) : isError ? (
            <div className="px-4 py-8 text-center text-sm text-red-400">
              Error loading alerts:{" "}
              {error instanceof Error
                ? error.message
                : typeof error === "object" && error !== null && "message" in error
                  ? `${(error as ApiError).status ?? ""} ${(error as ApiError).message}`.trim()
                  : "Unknown error"}
            </div>
          ) : (
            <table className="w-full border-collapse">
              <thead className="sticky top-0 z-10">
                <tr className="border-b border-white/10 bg-[var(--surface-2)]">
                  <th className="w-10 px-4 py-3 text-left">
                    <input
                      type="checkbox"
                      checked={allSelected}
                      ref={(el) => { if (el) el.indeterminate = someSelected }}
                      onChange={(e) => handleSelectAll(e.target.checked)}
                      disabled={!content.length}
                      className="rounded border-white/20 bg-white/5 accent-gold text-gold focus:ring-gold"
                    />
                  </th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">ID</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Type</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Priority</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Status</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Description</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Created</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {content.map((alert) => (
                  <tr
                    key={alert.id}
                    className={`transition-colors hover:bg-white/[0.02] ${
                      selected.has(alert.id) ? "bg-burgundy-700/5" : ""
                    }`}
                  >
                    <td className="px-4 py-3">
                      <input
                        type="checkbox"
                        checked={selected.has(alert.id)}
                        onChange={(e) => handleSelectOne(alert.id, e.target.checked)}
                        className="rounded border-white/20 bg-white/5 accent-gold text-gold focus:ring-gold"
                      />
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm text-white">#{alert.id}</td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm text-white">{alert.alertType}</td>
                    <td className="whitespace-nowrap px-4 py-3">
                      <TwBadge variant={priorityVariant(alert.priority)}>{alert.priority}</TwBadge>
                    </td>
                    <td className="whitespace-nowrap px-4 py-3">
                      <TwBadge variant={alertStatusVariant(alert.status)}>{alert.status}</TwBadge>
                    </td>
                    <td className="max-w-xs truncate px-4 py-3 text-sm text-white/80">
                      {alert.description || <span className="text-glass-muted">-</span>}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm text-glass-muted">
                      {new Date(alert.createdAt).toLocaleDateString()}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3">
                      <button
                        onClick={() => setViewAlert(alert)}
                        className="flex items-center gap-1 text-xs text-burgundy-400 transition-colors hover:text-burgundy-300"
                      >
                        <Eye size={14} /> View
                      </button>
                    </td>
                  </tr>
                ))}
                {!content.length && (
                  <tr>
                    <td colSpan={8} className="px-4 py-8 text-center text-sm text-glass-muted">
                      No alerts found
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
          onPageChange={(newPage) => { setPage(prev => ({ ...prev, index: newPage })); setSelected(new Set()); }}
          onRowsPerPageChange={(newSize) => { setPage({ index: 0, size: newSize }); setSelected(new Set()); }}
        />
      </div>

      {/* Alert Detail Modal */}
      {viewAlert && (
        <>
          <div className="fixed inset-0 z-40 bg-black/60 backdrop-blur-sm" onClick={() => setViewAlert(null)} />
          <div className="fixed left-1/2 top-1/2 z-50 w-full max-w-lg -translate-x-1/2 -translate-y-1/2">
            <div className="overflow-hidden rounded-xl border border-white/10 bg-[var(--surface-2)] shadow-2xl">
              {/* Header */}
              <div className="flex items-start justify-between border-b border-white/10 px-6 py-4">
                <div>
                  <h3 className="text-lg font-semibold text-white">Alert #{viewAlert.id}</h3>
                  <p className="mt-0.5 text-xs text-glass-muted">{viewAlert.alertType}</p>
                </div>
                <div className="flex items-center gap-2">
                  <TwBadge variant={priorityVariant(viewAlert.priority)}>{viewAlert.priority}</TwBadge>
                  <TwBadge variant={alertStatusVariant(viewAlert.status)}>{viewAlert.status}</TwBadge>
                  <button
                    onClick={() => setViewAlert(null)}
                    className="rounded p-1 text-glass-muted transition-colors hover:bg-white/10 hover:text-white"
                  >
                    <X size={18} />
                  </button>
                </div>
              </div>

              {/* Content */}
              <div className="space-y-4 px-6 py-4">
                <div>
                  <p className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Description</p>
                  <p className="mt-1 text-sm leading-relaxed text-white/80">
                    {viewAlert.description || "No description provided."}
                  </p>
                </div>
                <div className="grid grid-cols-2 gap-4">
                  {viewAlert.transactionId && (
                    <div>
                      <p className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Transaction ID</p>
                      <p className="mt-0.5 font-mono text-sm text-white">#{viewAlert.transactionId}</p>
                    </div>
                  )}
                  {viewAlert.caseId && (
                    <div>
                      <p className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Linked Case</p>
                      <p className="mt-0.5 font-mono text-sm text-white">Case #{viewAlert.caseId}</p>
                    </div>
                  )}
                  <div>
                    <p className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Created</p>
                    <p className="mt-0.5 text-sm text-white/80">{new Date(viewAlert.createdAt).toLocaleString()}</p>
                  </div>
                  {viewAlert.resolvedAt && (
                    <div>
                      <p className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Resolved</p>
                      <p className="mt-0.5 text-sm text-emerald-400">{new Date(viewAlert.resolvedAt).toLocaleString()}</p>
                    </div>
                  )}
                </div>
              </div>

              {/* Footer */}
              <div className="flex justify-end border-t border-white/10 px-6 py-3">
                <Link to={`/records/ALERT/${viewAlert.id}`} onClick={() => setViewAlert(null)} className="mr-2 rounded-lg border border-gold/50 px-4 py-1.5 text-xs text-gold transition-colors hover:bg-gold hover:text-black">Trace record</Link>
                <button
                  onClick={() => setViewAlert(null)}
                  className="rounded-lg border border-white/10 px-4 py-1.5 text-xs text-white transition-colors hover:bg-white/5"
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        </>
      )}

      <TwSnackbar
        open={snackbar.open}
        message={snackbar.message}
        severity={snackbar.severity}
        onClose={() => setSnackbar(prev => ({ ...prev, open: false }))}
      />
    </HokekaPageShell>
  );
}
