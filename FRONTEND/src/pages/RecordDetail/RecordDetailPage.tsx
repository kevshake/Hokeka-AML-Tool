import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, ExternalLink, FileText, Link2, LoaderCircle } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { apiClient } from "../../lib/apiClient";

interface RecordLink {
  recordType: string;
  recordId: string;
  label: string;
  relationship: string;
  summary: Record<string, unknown>;
}

interface RecordOccurrence {
  occurrenceType: string;
  occurredAt?: string;
  description?: string;
  data: Record<string, unknown>;
  source?: RecordLink;
}

interface RecordDetail {
  recordType: string;
  recordId: string;
  title: string;
  data: Record<string, unknown>;
  relatedRecords: RecordLink[];
  occurrences: RecordOccurrence[];
}

function value(value: unknown): string {
  if (value === null || value === undefined || value === "") return "-";
  if (typeof value === "object") return JSON.stringify(value, null, 2);
  return String(value);
}

function date(value?: string) {
  return value ? new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "-";
}

function RecordAnchor({ link }: { link: RecordLink }) {
  return <Link to={`/records/${link.recordType}/${link.recordId}`} className="group flex items-center justify-between gap-3 border-l-2 border-transparent px-4 py-3 transition hover:border-gold hover:bg-white/5"><div className="min-w-0"><p className="truncate text-sm font-medium text-white group-hover:text-gold">{link.label}</p><p className="mt-1 text-[11px] uppercase tracking-wide text-white/40">{link.relationship.replace(/_/g, " ")} / {link.recordType.replace(/_/g, " ")}</p></div><ExternalLink size={15} className="shrink-0 text-white/40 group-hover:text-gold" /></Link>;
}

export default function RecordDetailPage() {
  const { recordType = "", recordId = "" } = useParams();
  const query = useQuery<RecordDetail>({
    queryKey: ["record-detail", recordType, recordId],
    queryFn: () => apiClient.get<RecordDetail>(`records/${recordType}/${recordId}`),
    enabled: Boolean(recordType && recordId),
  });

  if (query.isLoading) return <div className="flex min-h-[60vh] items-center justify-center text-white/60"><LoaderCircle className="animate-spin" size={22} /></div>;
  if (query.isError || !query.data) return <div className="p-6"><Link to="/dashboard" className="inline-flex items-center gap-2 text-sm text-gold"><ArrowLeft size={16}/> Back to dashboard</Link><p className="mt-8 text-white/60">This record could not be loaded in the current PSP scope.</p></div>;
  const record = query.data;
  const calculationSummary = record.data.calculationSummary as Record<string, unknown> | undefined;

  return <div className="min-h-0 flex-1 overflow-y-auto bg-[var(--ink)] p-4 text-white md:p-6">
    <Link to="/dashboard" className="inline-flex items-center gap-2 text-sm text-white/55 transition hover:text-gold"><ArrowLeft size={16}/> Back to workspace</Link>
    <header className="mt-6 flex flex-wrap items-start justify-between gap-4 border-b border-white/10 pb-6"><div><p className="text-xs font-semibold uppercase tracking-widest text-gold">{record.recordType.replace(/_/g, " ")}</p><h1 className="mt-2 text-2xl font-semibold">{record.title}</h1><p className="mt-2 text-sm text-white/45">Record ID: {record.recordId}</p></div><Link to={`/reports?recordType=${record.recordType}&recordId=${record.recordId}`} className="inline-flex items-center gap-2 border border-gold/70 px-4 py-2 text-sm font-medium text-gold transition hover:bg-gold hover:text-black"><FileText size={16}/> Run report from record</Link></header>

    <div className="mt-6 grid gap-6 xl:grid-cols-[minmax(0,1.15fr)_minmax(330px,.85fr)]">
      <section><h2 className="mb-3 text-sm font-semibold">Record data</h2><dl className="divide-y divide-white/8 border border-white/10">{Object.entries(record.data).map(([key, item]) => <div key={key} className="grid gap-2 px-4 py-3 md:grid-cols-[190px_1fr]"><dt className="text-xs uppercase tracking-wide text-white/40">{key.replace(/([A-Z])/g, " $1")}</dt><dd className="whitespace-pre-wrap break-words text-sm text-white/80">{value(item)}</dd></div>)}</dl>{calculationSummary && <><h2 className="mb-3 mt-6 text-sm font-semibold">Report calculations</h2><pre className="overflow-x-auto border border-white/10 bg-black/20 p-4 text-xs leading-6 text-white/70">{JSON.stringify(calculationSummary, null, 2)}</pre></>}</section>
      <aside className="space-y-6"><section><h2 className="mb-3 flex items-center gap-2 text-sm font-semibold"><Link2 size={16} className="text-gold"/> Related records</h2><div className="divide-y divide-white/8 border border-white/10">{record.relatedRecords.map((link, index) => <RecordAnchor key={`${link.recordType}-${link.recordId}-${index}`} link={link} />)}{!record.relatedRecords.length && <p className="px-4 py-8 text-center text-sm text-white/45">No related records are available.</p>}</div></section><section><h2 className="mb-3 text-sm font-semibold">Occurrences</h2><div className="divide-y divide-white/8 border border-white/10">{record.occurrences.map((occurrence, index) => <div key={`${occurrence.occurrenceType}-${index}`} className="p-4"><div className="flex flex-wrap items-center justify-between gap-2"><span className="text-xs font-semibold text-gold">{occurrence.occurrenceType.replace(/_/g, " ")}</span><span className="text-xs text-white/40">{date(occurrence.occurredAt)}</span></div><p className="mt-2 text-sm text-white/75">{occurrence.description || "Record occurrence"}</p>{occurrence.source && <div className="mt-3"><RecordAnchor link={occurrence.source}/></div>}</div>)}{!record.occurrences.length && <p className="px-4 py-8 text-center text-sm text-white/45">No occurrences recorded.</p>}</div></section></aside>
    </div>
  </div>;
}
