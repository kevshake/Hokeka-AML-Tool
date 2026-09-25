import { useState } from "react";
import { Link } from "react-router-dom";
import { useAlerts } from "../../features/api/queries";
import { useUpdateAlertStatus } from "../../features/api/mutations";
import { useResponsivePagination } from "../../hooks/useResponsivePagination";
import type { ApiError } from "../../lib/apiClient";
import type { Alert, Priority } from "../../types";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import GlassCard from "../../components/Common/GlassCard";
import GlassModal from "../../components/Common/GlassModal";
import GlassButton from "../../components/Common/GlassButton";
import TwBadge from "../../components/Common/TwBadge";
import TwPagination from "../../components/Common/TwPagination";
import TwSnackbar from "../../components/Common/TwSnackbar";
import AiVerdictPanel from "../../components/Jev/AiVerdictPanel";
import {
  CheckCheck,
  ChevronDown,
  Download,
  Eye,
  Loader2,
  RotateCcw,
  Search,
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
      <GlassCard padding="md" glowVariant="red" static>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3 border-b border-hairline pb-4">
        <div className="flex items-center gap-3">
          <GlassButton variant="default" size="sm" onClick={handleExportCSV} disabled={!content.length}>
            <Download size={14} />
            Export CSV
          </GlassButton>
          {selected.size > 0 && (
            <span className="text-xs text-glass-muted">{selected.size} selected</span>
          )}
        </div>

        <div className="relative">
          <button
            disabled={selected.size === 0}
            onClick={() => setBulkOpen(!bulkOpen)}
            className="hokeka-btn-primary disabled:opacity-45"
          >
            <ChevronDown size={14} />
            Bulk Actions {selected.size > 0 ? `(${selected.size})` : ""}
          </button>
          {bulkOpen && (
            <>
              <div className="fixed inset-0 z-10" onClick={() => setBulkOpen(false)} />
              <div className="absolute right-0 z-20 mt-1 w-52 rounded-lg border border-hairline bg-burgundy-850 py-1 shadow-editorial">
                <button type="button" onClick={() => handleBulkAction("INVESTIGATING")} className="flex w-full items-center gap-2 px-3 py-2 text-left text-xs text-ink transition-colors hover:bg-burgundy-800">
                  <Search size={14} /> Mark as Investigating
                </button>
                <button type="button" onClick={() => handleBulkAction("RESOLVED")} className="flex w-full items-center gap-2 px-3 py-2 text-left text-xs text-ink transition-colors hover:bg-burgundy-800">
                  <CheckCheck size={14} /> Mark as Resolved
                </button>
                <button type="button" onClick={() => handleBulkAction("OPEN")} className="flex w-full items-center gap-2 px-3 py-2 text-left text-xs text-ink transition-colors hover:bg-burgundy-800">
                  <RotateCcw size={14} /> Reopen
                </button>
              </div>
            </>
          )}
        </div>
      </div>

      <div className="hokeka-table-wrap" style={{ maxHeight: "calc(100vh - 320px)" }}>
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
            <table className="hokeka-table">
              <thead>
                <tr>
                  <th className="w-10">
                    <input
                      type="checkbox"
                      checked={allSelected}
                      ref={(el) => { if (el) el.indeterminate = someSelected }}
                      onChange={(e) => handleSelectAll(e.target.checked)}
                      disabled={!content.length}
                      className="rounded border-hairline accent-gold"
                    />
                  </th>
                  <th>ID</th>
                  <th>Type</th>
                  <th>Priority</th>
                  <th>Status</th>
                  <th>Description</th>
                  <th>Created</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {content.map((alert) => (
                  <tr
                    key={alert.id}
                    className={selected.has(alert.id) ? "bg-burgundy-800/40" : undefined}
                  >
                    <td>
                      <input
                        type="checkbox"
                        checked={selected.has(alert.id)}
                        onChange={(e) => handleSelectOne(alert.id, e.target.checked)}
                        className="rounded border-hairline accent-gold"
                      />
                    </td>
                    <td>#{alert.id}</td>
                    <td>{alert.alertType}</td>
                    <td className="whitespace-nowrap px-4 py-3">
                      <TwBadge variant={priorityVariant(alert.priority)}>{alert.priority}</TwBadge>
                    </td>
                    <td className="whitespace-nowrap px-4 py-3">
                      <TwBadge variant={alertStatusVariant(alert.status)}>{alert.status}</TwBadge>
                    </td>
                    <td className="max-w-xs truncate text-ink-muted">
                      {alert.description || <span className="text-glass-muted">-</span>}
                    </td>
                    <td className="text-glass-muted">
                      {new Date(alert.createdAt).toLocaleDateString()}
                    </td>
                    <td>
                      <button
                        type="button"
                        onClick={() => setViewAlert(alert)}
                        className="flex items-center gap-1 text-xs text-gold hover:underline"
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
      </GlassCard>

      <GlassModal
        open={!!viewAlert}
        onClose={() => setViewAlert(null)}
        title={viewAlert ? `Alert #${viewAlert.id}` : "Alert"}
        subtitle={viewAlert?.alertType}
        maxWidth="md"
        headerExtra={
          viewAlert ? (
            <>
              <TwBadge variant={priorityVariant(viewAlert.priority)}>{viewAlert.priority}</TwBadge>
              <TwBadge variant={alertStatusVariant(viewAlert.status)}>{viewAlert.status}</TwBadge>
            </>
          ) : null
        }
        footer={
          viewAlert ? (
            <>
              <Link
                to={`/records/ALERT/${viewAlert.id}`}
                onClick={() => setViewAlert(null)}
                className="hokeka-btn-primary"
              >
                Trace record
              </Link>
              <GlassButton variant="default" size="sm" onClick={() => setViewAlert(null)}>
                Close
              </GlassButton>
            </>
          ) : null
        }
      >
        {viewAlert && (
          <div className="space-y-4">
            <div>
              <p className="hokeka-field-label">Description</p>
              <p className="mt-1 text-sm leading-relaxed text-ink-muted">
                {viewAlert.description || "No description provided."}
              </p>
            </div>
            <div className="grid grid-cols-2 gap-4">
              {viewAlert.transactionId && (
                <div>
                  <p className="hokeka-field-label">Transaction ID</p>
                  <p className="mt-0.5 font-mono text-sm text-ink">#{viewAlert.transactionId}</p>
                </div>
              )}
              {viewAlert.caseId && (
                <div>
                  <p className="hokeka-field-label">Linked Case</p>
                  <p className="mt-0.5 font-mono text-sm text-ink">Case #{viewAlert.caseId}</p>
                </div>
              )}
              <div>
                <p className="hokeka-field-label">Created</p>
                <p className="mt-0.5 text-sm text-ink-muted">{new Date(viewAlert.createdAt).toLocaleString()}</p>
              </div>
              {viewAlert.resolvedAt && (
                <div>
                  <p className="hokeka-field-label">Resolved</p>
                  <p className="mt-0.5 text-sm text-success">{new Date(viewAlert.resolvedAt).toLocaleString()}</p>
                </div>
              )}
            </div>
            {viewAlert.id ? (
              <AiVerdictPanel auditPath={`jev/audit/alert/${viewAlert.id}`} />
            ) : null}
          </div>
        )}
      </GlassModal>

      <TwSnackbar
        open={snackbar.open}
        message={snackbar.message}
        severity={snackbar.severity}
        onClose={() => setSnackbar(prev => ({ ...prev, open: false }))}
      />
    </HokekaPageShell>
  );
}
