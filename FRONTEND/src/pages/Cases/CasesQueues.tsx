import { Loader2 } from "lucide-react";
import { useCases } from "../../features/api/queries";
import type { CaseStatus, Priority } from "../../types";
import TwBadge from "../../components/Common/TwBadge";

const statusConfig: Record<CaseStatus, { label: string; glow: "info" | "warning" | "danger" | "success" | "gold" }> = {
  NEW: { label: "New", glow: "info" },
  ASSIGNED: { label: "Assigned", glow: "gold" },
  INVESTIGATING: { label: "Investigating", glow: "warning" },
  PENDING_REVIEW: { label: "Pending Review", glow: "danger" },
  ESCALATED: { label: "Escalated", glow: "danger" },
  RESOLVED: { label: "Resolved", glow: "success" },
};

const priorityVariant = (priority: Priority): "danger" | "warning" | "success" | "default" => {
  if (priority === "CRITICAL") return "danger";
  if (priority === "HIGH" || priority === "MEDIUM") return "warning";
  if (priority === "LOW") return "success";
  return "default";
};

const QUEUE_STATUSES: CaseStatus[] = ["NEW", "ASSIGNED", "INVESTIGATING", "PENDING_REVIEW", "ESCALATED"];

function QueueColumn({ status }: { status: CaseStatus }) {
  const cfg = statusConfig[status];
  const { data, isLoading } = useCases({ page: 0, size: 20, status });
  const cases = data?.content || [];

  return (
    <div className="min-w-[240px] flex-[0_0_240px]">
      <div className="mb-2 flex items-center justify-between rounded-lg border border-hairline bg-burgundy-900 px-3 py-2">
        <span className="text-xs font-semibold uppercase tracking-wide text-ink">{cfg.label}</span>
        <TwBadge variant="default">{data?.totalElements ?? (isLoading ? "…" : 0)}</TwBadge>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-6">
          <Loader2 size={20} className="animate-spin text-glass-muted" />
        </div>
      ) : cases.length === 0 ? (
        <div className="rounded-lg border border-dashed border-hairline px-3 py-6 text-center text-xs text-glass-muted">
          No cases
        </div>
      ) : (
        cases.map((c) => (
          <div
            key={c.id}
            className="mb-2 rounded-lg border border-hairline bg-burgundy-850/80 p-3 transition-colors hover:border-hairline-strong"
          >
            <p className="font-mono text-[11px] text-glass-muted">{c.caseReference}</p>
            <p className="mt-1 line-clamp-2 text-sm text-ink-muted">
              {c.description?.length > 80 ? `${c.description.slice(0, 80)}…` : c.description || "No description"}
            </p>
            <div className="mt-2 flex items-center justify-between gap-2">
              <TwBadge variant={priorityVariant(c.priority)}>{c.priority}</TwBadge>
              <span className="truncate text-[11px] text-glass-muted">
                {c.assignedTo?.username || "Unassigned"}
              </span>
            </div>
          </div>
        ))
      )}
    </div>
  );
}

export default function CasesQueues() {
  return (
    <div>
      <span className="hokeka-section-label">Workflow</span>
      <h3 className="mt-1 mb-4 font-display text-lg font-semibold tracking-tight text-ink">Case queues</h3>
      <div className="flex gap-3 overflow-x-auto pb-2">
        {QUEUE_STATUSES.map((status) => (
          <QueueColumn key={status} status={status} />
        ))}
      </div>
    </div>
  );
}
