import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  AlertTriangle,
  ArrowRight,
  CheckCircle2,
  CircleDot,
  Clock3,
  FileCheck2,
  FileText,
  Link2,
  Loader2,
  MessageSquareText,
  Paperclip,
  ShieldAlert,
  UserRoundCheck,
  WalletCards,
} from "lucide-react";
import { useAllPsps, useCases, useCaseTimeline } from "../../features/api/queries";
import { useAuth } from "../../contexts/AuthContext";
import type { Case, CaseStatus, CaseTimelineEvent } from "../../types";

const STATUS_LABELS: Record<CaseStatus, string> = {
  NEW: "New",
  ASSIGNED: "Assigned",
  INVESTIGATING: "Investigating",
  PENDING_REVIEW: "Pending review",
  RESOLVED: "Resolved",
  ESCALATED: "Escalated",
};

const EVENT_STYLES: Record<string, { icon: typeof CircleDot; color: string; background: string }> = {
  CASE_CREATED: { icon: FileText, color: "text-sky-300", background: "bg-sky-500/15" },
  CASE_ASSIGNED: { icon: UserRoundCheck, color: "text-cyan-300", background: "bg-cyan-500/15" },
  CASE_STATUS_CHANGED: { icon: CircleDot, color: "text-violet-300", background: "bg-violet-500/15" },
  CASE_PRIORITY_SET: { icon: ShieldAlert, color: "text-amber-300", background: "bg-amber-500/15" },
  SLA_DEADLINE: { icon: Clock3, color: "text-orange-300", background: "bg-orange-500/15" },
  TRANSACTION: { icon: WalletCards, color: "text-emerald-300", background: "bg-emerald-500/15" },
  NOTE: { icon: MessageSquareText, color: "text-blue-300", background: "bg-blue-500/15" },
  EVIDENCE_ATTACHED: { icon: Paperclip, color: "text-indigo-300", background: "bg-indigo-500/15" },
  SAR_CREATED: { icon: FileText, color: "text-fuchsia-300", background: "bg-fuchsia-500/15" },
  SAR_APPROVED: { icon: FileCheck2, color: "text-green-300", background: "bg-green-500/15" },
  SAR_FILED: { icon: FileCheck2, color: "text-teal-300", background: "bg-teal-500/15" },
  ESCALATION: { icon: AlertTriangle, color: "text-red-300", background: "bg-red-500/15" },
  CASE_RESOLVED: { icon: CheckCircle2, color: "text-green-300", background: "bg-green-500/15" },
  CASE_LINKED: { icon: Link2, color: "text-purple-300", background: "bg-purple-500/15" },
};

const FALLBACK_EVENT_STYLE = {
  icon: CircleDot,
  color: "text-white/70",
  background: "bg-white/10",
};

function formatTimestamp(timestamp: string): string {
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) return timestamp;
  return date.toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function labelForKey(key: string): string {
  return key.replace(/([A-Z])/g, " $1").replace(/_/g, " ").trim();
}

function displayValue(value: unknown): string | null {
  if (value === null || value === undefined || value === "") return null;
  if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  return null;
}

function TimelineEventRow({ event, isLast }: { event: CaseTimelineEvent; isLast: boolean }) {
  const style = EVENT_STYLES[event.type] ?? FALLBACK_EVENT_STYLE;
  const Icon = style.icon;
  const metadata = Object.entries(event.data ?? {})
    .map(([key, value]) => [key, displayValue(value)] as const)
    .filter((entry): entry is readonly [string, string] => entry[1] !== null)
    .slice(0, 6);

  return (
    <div className="relative grid grid-cols-[36px_minmax(0,1fr)] gap-3 pb-4">
      {!isLast && <div className="absolute bottom-0 left-[17px] top-9 w-px bg-white/10" />}
      <div className={`relative z-10 flex h-9 w-9 items-center justify-center rounded-full ${style.background} ${style.color}`}>
        <Icon size={17} />
      </div>
      <div className="min-w-0 rounded-lg border border-white/10 bg-[var(--surface-2)] px-4 py-3">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div className="min-w-0">
            <p className="text-sm font-semibold text-white">{event.description}</p>
            <p className="mt-1 text-xs uppercase text-white/40">{labelForKey(event.type)}</p>
          </div>
          <time className="shrink-0 text-xs text-glass-muted" dateTime={event.timestamp}>
            {formatTimestamp(event.timestamp)}
          </time>
        </div>
        {metadata.length > 0 && (
          <dl className="mt-3 grid gap-x-6 gap-y-2 border-t border-white/10 pt-3 sm:grid-cols-2 xl:grid-cols-3">
            {metadata.map(([key, value]) => (
              <div key={key} className="min-w-0">
                <dt className="text-[11px] uppercase text-white/35">{labelForKey(key)}</dt>
                <dd className="mt-0.5 truncate text-xs text-white/75" title={value}>{value}</dd>
              </div>
            ))}
          </dl>
        )}
      </div>
    </div>
  );
}

export default function CasesTimeline() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const isSystemUser = (user?.pspId ?? 0) === 0;
  const [statusFilter, setStatusFilter] = useState("");
  const [pspFilter, setPspFilter] = useState("");
  const [selectedCaseId, setSelectedCaseId] = useState<number>(0);

  const { data: allPsps } = useAllPsps();
  const casesQuery = useCases({ page: 0, size: 100, status: statusFilter || undefined });
  const cases = useMemo(() => {
    const content = casesQuery.data?.content ?? [];
    const scoped = pspFilter
      ? content.filter((item) => String(item.pspId ?? item.assignedTo?.pspId ?? "") === pspFilter)
      : content;
    return [...scoped].sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime());
  }, [casesQuery.data?.content, pspFilter]);

  useEffect(() => {
    if (cases.length === 0) {
      setSelectedCaseId(0);
      return;
    }
    if (!cases.some((item) => item.id === selectedCaseId)) {
      setSelectedCaseId(cases[0].id);
    }
  }, [cases, selectedCaseId]);

  const timelineQuery = useCaseTimeline(selectedCaseId);
  const selectedCase: Case | undefined = cases.find((item) => item.id === selectedCaseId);
  const events = [...(timelineQuery.data?.events ?? [])].sort(
    (a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime(),
  );

  return (
    <div className="space-y-4">
      <div className="grid gap-3 md:grid-cols-3">
        <label className="block">
          <span className="mb-1 block text-xs font-medium uppercase text-glass-muted">Status</span>
          <select
            value={statusFilter}
            onChange={(event) => setStatusFilter(event.target.value)}
            className="h-10 w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 text-sm text-white focus:outline-none focus:ring-1 focus:ring-burgundy-700"
          >
            <option value="">All statuses</option>
            {(Object.keys(STATUS_LABELS) as CaseStatus[]).map((status) => (
              <option key={status} value={status}>{STATUS_LABELS[status]}</option>
            ))}
          </select>
        </label>

        {isSystemUser && (
          <label className="block">
            <span className="mb-1 block text-xs font-medium uppercase text-glass-muted">PSP</span>
            <select
              value={pspFilter}
              onChange={(event) => setPspFilter(event.target.value)}
              className="h-10 w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 text-sm text-white focus:outline-none focus:ring-1 focus:ring-burgundy-700"
            >
              <option value="">All PSPs</option>
              {(allPsps ?? []).map((psp) => {
                const id = psp.id ?? psp.pspId;
                if (id === undefined) return null;
                return <option key={id} value={String(id)}>{psp.name ?? psp.legalName ?? `PSP ${id}`}</option>;
              })}
            </select>
          </label>
        )}

        <label className="block">
          <span className="mb-1 block text-xs font-medium uppercase text-glass-muted">Case</span>
          <select
            value={selectedCaseId || ""}
            onChange={(event) => setSelectedCaseId(Number(event.target.value))}
            disabled={casesQuery.isLoading || cases.length === 0}
            className="h-10 w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 text-sm text-white focus:outline-none focus:ring-1 focus:ring-burgundy-700 disabled:opacity-50"
          >
            {cases.length === 0 && <option value="">No cases available</option>}
            {cases.map((item) => (
              <option key={item.id} value={item.id}>{item.caseReference} - {STATUS_LABELS[item.status] ?? item.status}</option>
            ))}
          </select>
        </label>
      </div>

      {selectedCase && (
        <div className="flex flex-wrap items-center justify-between gap-3 border-y border-white/10 py-3">
          <div>
            <p className="text-sm font-semibold text-white">{selectedCase.caseReference}</p>
            <p className="mt-0.5 text-xs text-glass-muted">{events.length} recorded event{events.length === 1 ? "" : "s"}</p>
          </div>
          <button
            type="button"
            onClick={() => navigate(`/cases/all?caseId=${selectedCase.id}`)}
            className="flex items-center gap-1.5 text-xs font-medium text-burgundy-300 hover:text-burgundy-200"
          >
            Open case <ArrowRight size={14} />
          </button>
        </div>
      )}

      {(casesQuery.isLoading || timelineQuery.isLoading) && (
        <div className="flex justify-center py-12"><Loader2 size={28} className="animate-spin text-burgundy-400" /></div>
      )}

      {(casesQuery.isError || timelineQuery.isError) && (
        <div className="rounded-lg border border-red-700/30 bg-red-900/20 px-4 py-3 text-sm text-red-200">
          The case timeline could not be loaded. Check your case access and try again.
        </div>
      )}

      {!casesQuery.isLoading && !casesQuery.isError && cases.length === 0 && (
        <div className="border-y border-white/10 py-12 text-center text-sm text-glass-muted">
          No cases match the selected filters.
        </div>
      )}

      {!timelineQuery.isLoading && !timelineQuery.isError && selectedCaseId > 0 && events.length === 0 && (
        <div className="border-y border-white/10 py-12 text-center text-sm text-glass-muted">
          No lifecycle events have been recorded for this case.
        </div>
      )}

      {!timelineQuery.isLoading && !timelineQuery.isError && events.length > 0 && (
        <div>
          {events.map((event, index) => (
            <TimelineEventRow
              key={`${event.timestamp}-${event.type}-${index}`}
              event={event}
              isLast={index === events.length - 1}
            />
          ))}
        </div>
      )}
    </div>
  );
}
