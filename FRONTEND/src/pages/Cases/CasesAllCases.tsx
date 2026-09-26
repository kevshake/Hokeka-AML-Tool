import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Eye, Loader2 } from "lucide-react";
import { useCase, useCases } from "../../features/api/queries";
import { useCreateCase } from "../../features/api/mutations";
import { useAuth } from "../../contexts/AuthContext";
import type { ApiError } from "../../lib/apiClient";
import type { Case, Priority } from "../../types";
import TwBadge from "../../components/Common/TwBadge";
import TwPagination from "../../components/Common/TwPagination";
import GlassModal from "../../components/Common/GlassModal";
import GlassButton from "../../components/Common/GlassButton";
import { TwInput, TwSelect } from "../../components/Common/TwInput";
import AiVerdictPanel from "../../components/Jev/AiVerdictPanel";

const statusVariant = (status: string): "info" | "warning" | "success" | "danger" | "default" => {
  if (status === "NEW" || status === "ASSIGNED") return "info";
  if (status === "INVESTIGATING" || status === "PENDING_REVIEW" || status === "ESCALATED") return "warning";
  if (status === "RESOLVED") return "success";
  return "default";
};

const priorityVariant = (priority: string): "danger" | "warning" | "default" | "success" => {
  if (priority === "CRITICAL") return "danger";
  if (priority === "HIGH" || priority === "MEDIUM") return "warning";
  if (priority === "LOW") return "success";
  return "default";
};

const STATUS_OPTIONS = [
  { value: "", label: "All statuses" },
  { value: "NEW", label: "New" },
  { value: "ASSIGNED", label: "Assigned" },
  { value: "INVESTIGATING", label: "Investigating" },
  { value: "PENDING_REVIEW", label: "Pending review" },
  { value: "RESOLVED", label: "Resolved" },
  { value: "ESCALATED", label: "Escalated" },
];

const PRIORITY_OPTIONS = [
  { value: "CRITICAL", label: "Critical" },
  { value: "HIGH", label: "High" },
  { value: "MEDIUM", label: "Medium" },
  { value: "LOW", label: "Low" },
];

export default function CasesAllCases() {
  const [searchParams, setSearchParams] = useSearchParams();
  const deepLinkCaseId = Number(searchParams.get("caseId") || 0);
  const [statusFilter, setStatusFilter] = useState("");
  const [page, setPage] = useState({ index: 0, size: 25 });
  const [createOpen, setCreateOpen] = useState(false);
  const [newCase, setNewCase] = useState({
    caseReference: "",
    description: "",
    priority: "MEDIUM" as Priority,
  });
  const [viewCase, setViewCase] = useState<Case | null>(null);

  const { user } = useAuth();
  const { data: cases, isLoading, isError, error } = useCases({
    page: page.index,
    size: page.size,
    status: statusFilter || undefined,
  });
  const deepLinkedCase = useCase(deepLinkCaseId);
  const createCase = useCreateCase();

  const content = cases?.content || [];

  useEffect(() => {
    if (!deepLinkCaseId) return;
    const fromPage = cases?.content?.find((c) => c.id === deepLinkCaseId);
    if (fromPage) {
      setViewCase(fromPage);
      return;
    }
    if (deepLinkedCase.data) {
      setViewCase(deepLinkedCase.data);
    }
  }, [deepLinkCaseId, cases?.content, deepLinkedCase.data]);

  const handleExportCSV = () => {
    if (!content.length) return;
    const headers = ["Reference", "Status", "Priority", "Description", "Assigned To", "Created", "Days Open"];
    const rows = content.map((c) => [
      c.caseReference,
      c.status,
      c.priority,
      (c.description || "").replace(/,/g, ";"),
      c.assignedTo ? c.assignedTo.firstName || c.assignedTo.username || "" : "Unassigned",
      new Date(c.createdAt).toISOString().split("T")[0],
      String(c.daysOpen ?? ""),
    ]);
    const csv = [headers, ...rows].map((r) => r.join(",")).join("\n");
    const blob = new Blob([csv], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `cases-${new Date().toISOString().split("T")[0]}.csv`;
    anchor.click();
    URL.revokeObjectURL(url);
  };

  const handleCreateCase = () => {
    if (!newCase.caseReference.trim()) return;
    createCase.mutate(
      { ...newCase, creatorUserId: user?.id },
      {
        onSuccess: () => {
          setCreateOpen(false);
          setNewCase({ caseReference: "", description: "", priority: "MEDIUM" });
        },
      },
    );
  };

  const closeViewCase = () => {
    setViewCase(null);
    if (deepLinkCaseId) {
      const next = new URLSearchParams(searchParams);
      next.delete("caseId");
      next.delete("action");
      setSearchParams(next, { replace: true });
    }
  };

  const createErrorMessage = (() => {
    const err = createCase.error as ApiError | Error | null;
    if (err && "status" in err) {
      if (err.status === 503) return "Service temporarily unavailable. Please try again in a moment.";
      if (err.status === 403) return "You do not have permission to create cases.";
      if (err.message) return err.message;
    }
    return "Failed to create case. Please try again.";
  })();

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3 border-b border-hairline pb-4">
        <div className="flex flex-wrap items-center gap-3">
          <span className="text-sm text-glass-muted">{cases?.totalElements || 0} cases total</span>
          <GlassButton variant="default" size="sm" onClick={handleExportCSV} disabled={!content.length}>
            Export CSV
          </GlassButton>
        </div>
        <div className="flex flex-wrap items-end gap-3">
          <TwSelect
            label="Filter by status"
            value={statusFilter}
            options={STATUS_OPTIONS}
            onChange={(e) => {
              setStatusFilter(e.target.value);
              setPage((prev) => ({ ...prev, index: 0 }));
            }}
          />
          <button type="button" onClick={() => setCreateOpen(true)} className="hokeka-btn-primary">
            Create Case
          </button>
        </div>
      </div>

      <div className="hokeka-table-wrap" style={{ maxHeight: "calc(100vh - 340px)" }}>
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 size={24} className="animate-spin text-glass-muted" />
          </div>
        ) : isError ? (
          <div className="px-4 py-8 text-center text-sm text-danger">
            Error loading cases: {error instanceof Error ? error.message : "Unknown error"}
          </div>
        ) : (
          <table className="hokeka-table">
            <thead>
              <tr>
                <th>Reference</th>
                <th>Status</th>
                <th>Priority</th>
                <th>Description</th>
                <th>Assigned To</th>
                <th>Created</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {content.length > 0 ? (
                content.map((caseItem) => (
                  <tr key={caseItem.id}>
                    <td className="font-mono font-semibold">{caseItem.caseReference}</td>
                    <td>
                      <TwBadge variant={statusVariant(caseItem.status)}>{caseItem.status}</TwBadge>
                    </td>
                    <td>
                      <TwBadge variant={priorityVariant(caseItem.priority)}>{caseItem.priority}</TwBadge>
                    </td>
                    <td className="max-w-xs truncate text-ink-muted">{caseItem.description || "—"}</td>
                    <td>{caseItem.assignedTo?.username || "Unassigned"}</td>
                    <td className="text-glass-muted">
                      {new Date(caseItem.createdAt).toLocaleDateString(undefined, {
                        month: "short",
                        day: "numeric",
                        year: "numeric",
                      })}
                    </td>
                    <td>
                      <button
                        type="button"
                        onClick={() => setViewCase(caseItem)}
                        className="flex items-center gap-1 text-xs text-gold hover:underline"
                      >
                        <Eye size={14} /> View
                      </button>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={7} className="py-8 text-center text-glass-muted">
                    No cases found
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>

      <TwPagination
        page={page.index}
        totalPages={cases?.totalPages ?? 1}
        totalCount={cases?.totalElements ?? 0}
        rowsPerPage={page.size}
        onPageChange={(newPage) => setPage((prev) => ({ ...prev, index: newPage }))}
        onRowsPerPageChange={(size) => setPage({ index: 0, size })}
      />

      <GlassModal
        open={createOpen}
        onClose={() => {
          setCreateOpen(false);
          createCase.reset();
        }}
        title="Create New Case"
        maxWidth="md"
        bodyClassName="space-y-4"
        footer={
          <>
            <GlassButton
              variant="default"
              size="sm"
              onClick={() => {
                setCreateOpen(false);
                createCase.reset();
              }}
            >
              Cancel
            </GlassButton>
            <button
              type="button"
              onClick={handleCreateCase}
              disabled={!newCase.caseReference.trim() || createCase.isPending}
              className="hokeka-btn-primary disabled:opacity-45"
            >
              {createCase.isPending ? "Creating…" : "Create Case"}
            </button>
          </>
        }
      >
        {createCase.isError && (
          <div className="rounded-lg border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-ink">
            {createErrorMessage}
          </div>
        )}
        <TwInput
          label="Case reference"
          value={newCase.caseReference}
          onChange={(e) => setNewCase((prev) => ({ ...prev, caseReference: e.target.value }))}
          required
        />
        <div>
          <label className="hokeka-field-label">Description</label>
          <textarea
            value={newCase.description}
            onChange={(e) => setNewCase((prev) => ({ ...prev, description: e.target.value }))}
            rows={3}
            className="hokeka-field mt-1 min-h-[5rem] resize-y"
          />
        </div>
        <TwSelect
          label="Priority"
          value={newCase.priority}
          options={PRIORITY_OPTIONS}
          onChange={(e) => setNewCase((prev) => ({ ...prev, priority: e.target.value as Priority }))}
        />
      </GlassModal>

      <GlassModal
        open={!!viewCase}
        onClose={closeViewCase}
        title={viewCase?.caseReference ?? "Case"}
        subtitle="Case details"
        maxWidth="lg"
        headerExtra={
          viewCase ? (
            <>
              <TwBadge variant={statusVariant(viewCase.status)}>{viewCase.status}</TwBadge>
              <TwBadge variant={priorityVariant(viewCase.priority)}>{viewCase.priority}</TwBadge>
            </>
          ) : null
        }
        footer={
          viewCase ? (
            <>
              <Link
                to={`/records/CASE/${viewCase.id}`}
                onClick={closeViewCase}
                className="hokeka-btn-primary"
              >
                Trace record
              </Link>
              <GlassButton variant="default" size="sm" onClick={closeViewCase}>
                Close
              </GlassButton>
            </>
          ) : null
        }
      >
        {viewCase && (
          <div className="grid gap-4 sm:grid-cols-2">
            <DetailBlock label="Description" value={viewCase.description || "No description provided."} wide />
            <DetailBlock
              label="Assigned to"
              value={
                viewCase.assignedTo
                  ? `${viewCase.assignedTo.firstName || ""} ${viewCase.assignedTo.lastName || ""}`.trim() ||
                    viewCase.assignedTo.username
                  : "Unassigned"
              }
            />
            <DetailBlock
              label="Created by"
              value={
                viewCase.createdBy
                  ? `${viewCase.createdBy.firstName || ""} ${viewCase.createdBy.lastName || ""}`.trim() ||
                    viewCase.createdBy.username
                  : "—"
              }
            />
            <DetailBlock label="Created" value={new Date(viewCase.createdAt).toLocaleString()} />
            <DetailBlock label="Last updated" value={new Date(viewCase.updatedAt).toLocaleString()} />
            {viewCase.slaDeadline && (
              <DetailBlock
                label="SLA deadline"
                value={`${new Date(viewCase.slaDeadline).toLocaleString()}${
                  new Date(viewCase.slaDeadline) < new Date() ? " (OVERDUE)" : ""
                }`}
                emphasis={new Date(viewCase.slaDeadline) < new Date()}
              />
            )}
            {viewCase.daysOpen !== undefined && (
              <DetailBlock
                label="Age"
                value={viewCase.daysOpen === 0 ? "Today" : `${viewCase.daysOpen} day${viewCase.daysOpen !== 1 ? "s" : ""}`}
              />
            )}
            <div className="sm:col-span-2">
              <AiVerdictPanel auditPath={`jev/audit/case/${viewCase.id}`} />
            </div>
          </div>
        )}
      </GlassModal>
    </div>
  );
}

function DetailBlock({
  label,
  value,
  wide,
  emphasis,
}: {
  label: string;
  value: string;
  wide?: boolean;
  emphasis?: boolean;
}) {
  return (
    <div className={wide ? "sm:col-span-2" : undefined}>
      <p className="hokeka-field-label">{label}</p>
      <p className={`mt-0.5 text-sm ${emphasis ? "font-semibold text-danger" : "text-ink-muted"}`}>{value}</p>
    </div>
  );
}
