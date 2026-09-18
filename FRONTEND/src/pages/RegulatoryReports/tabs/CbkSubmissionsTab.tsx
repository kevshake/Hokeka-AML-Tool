import { useState } from "react";
import { Link } from "react-router-dom";
import { useCbkSubmissions, useAllPsps } from "../../../features/api/queries";
import { useReplayCbkSubmission } from "../../../features/api/mutations";
import { CBK_ENDPOINT_LABELS } from "../../../types/cbk";
import type { CbkEndpointType } from "../../../types/cbk";
import TwBadge from "../../../components/Common/TwBadge";
import TwPagination from "../../../components/Common/TwPagination";
import TwSnackbar from "../../../components/Common/TwSnackbar";
import { Repeat, Loader2, Network } from "lucide-react";

const statusBadge = (s: string): "success" | "danger" | "warning" | "info" | "default" => {
  if (s === "SUCCESS") return "success";
  if (s === "FAILED") return "danger";
  if (s === "PENDING") return "warning";
  if (s === "RETRYING") return "info";
  return "default";
};

export default function CbkSubmissionsTab() {
  const [page, setPage] = useState({ index: 0, size: 25 });
  const [filterPspId, setFilterPspId] = useState<string>("");
  const [filterStatus, setFilterStatus] = useState<string>("");
  const [filterEndpoint, setFilterEndpoint] = useState<string>("");
  const [toast, setToast] = useState<{ open: boolean; severity: "success" | "error"; message: string }>({ open: false, severity: "success", message: "" });
  const [replayingId, setReplayingId] = useState<number | null>(null);

  const { data: psps } = useAllPsps();
  const replay = useReplayCbkSubmission();

  const { data, isLoading, isError } = useCbkSubmissions({
    pspId: filterPspId || undefined,
    status: filterStatus || undefined,
    endpoint: filterEndpoint || undefined,
    page: page.index,
    size: page.size,
  });

  const rows = data?.content ?? [];
  const totalElements = data?.totalElements ?? 0;
  const totalPages = data?.totalPages ?? 1;

  const handleReplay = async (row: any) => {
    setReplayingId(row.id);
    try {
      await replay.mutateAsync({ endpointType: row.endpointType, pspId: row.pspId });
      setToast({ open: true, severity: "success", message: `Replay triggered for ${row.endpointType}.` });
    } catch { setToast({ open: true, severity: "error", message: "Replay failed." }); }
    finally { setReplayingId(null); }
  };

  const selectClass = "rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-1.5 text-xs text-white focus:outline-none focus:ring-1 focus:ring-burgundy-700";

  return (
    <div>
      <h4 className="mb-3 text-base font-semibold text-white">CBK Submission History</h4>

      <div className="mb-3 flex flex-wrap gap-2">
        <select value={filterPspId} onChange={(e) => { setFilterPspId(e.target.value); setPage({ index: 0, size: page.size }); }} className={selectClass}>
          <option value="">All PSPs</option>
          {(psps ?? []).map((p: any) => <option key={p.id ?? p.pspId} value={String(p.id ?? p.pspId)}>{p.legalName ?? p.tradingName ?? `PSP ${p.id}`}</option>)}
        </select>
        <select value={filterStatus} onChange={(e) => { setFilterStatus(e.target.value); setPage({ index: 0, size: page.size }); }} className={selectClass}>
          <option value="">All Status</option>
          <option value="SUCCESS">Success</option>
          <option value="FAILED">Failed</option>
          <option value="PENDING">Pending</option>
          <option value="RETRYING">Retrying</option>
        </select>
        <select value={filterEndpoint} onChange={(e) => { setFilterEndpoint(e.target.value); setPage({ index: 0, size: page.size }); }} className={selectClass}>
          <option value="">All Endpoints</option>
          {(Object.keys(CBK_ENDPOINT_LABELS) as CbkEndpointType[]).map((ep) => (
            <option key={ep} value={ep}>{CBK_ENDPOINT_LABELS[ep]}</option>
          ))}
        </select>
      </div>

      {isError && <div className="mb-3 rounded-lg border border-red-700/30 bg-red-900/30 px-4 py-3 text-sm text-red-200">Failed to load CBK submission history.</div>}

      <div className="overflow-hidden rounded-lg border border-white/10 bg-[var(--surface-2)]">
        <div className="overflow-auto" style={{ maxHeight: "calc(100vh - 380px)" }}>
          {isLoading ? (
            <div className="flex justify-center py-12"><Loader2 size={28} className="animate-spin text-glass-muted" /></div>
          ) : (
            <table className="w-full border-collapse">
              <thead className="sticky top-0 z-10">
                <tr className="border-b border-white/10 bg-[var(--surface-2)]">
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">PSP</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Endpoint</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Status</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Attempted At</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Request ID</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Records</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {rows.length > 0 ? rows.map((row: any) => {
                  const psp = (psps ?? []).find((p: any) => String(p.id ?? p.pspId) === String(row.pspId));
                  const pspName = psp?.legalName ?? psp?.tradingName ?? `PSP ${row.pspId}`;
                  return (
                    <tr key={row.id} className="transition-colors hover:bg-white/[0.02]">
                      <td className="whitespace-nowrap px-4 py-3 text-sm font-medium text-white">{pspName}</td>
                      <td className="whitespace-nowrap px-4 py-3 text-xs text-white/80">{CBK_ENDPOINT_LABELS[row.endpointType as CbkEndpointType] ?? row.endpointType}</td>
                      <td className="whitespace-nowrap px-4 py-3"><TwBadge variant={statusBadge(row.status)}>{row.status}</TwBadge></td>
                      <td className="whitespace-nowrap px-4 py-3 text-xs text-glass-muted">{row.attemptedAt ? new Date(row.attemptedAt).toLocaleString() : "—"}</td>
                      <td className="whitespace-nowrap px-4 py-3 font-mono text-xs text-glass-muted">{row.requestId ?? "—"}</td>
                      <td className="whitespace-nowrap px-4 py-3 text-xs text-glass-muted">{row.recordCount ?? "—"}</td>
                      <td className="whitespace-nowrap px-4 py-3">
                        <div className="flex items-center gap-1">
                          <Link to={`/records/CBK_SUBMISSION/${row.id}`} title="Trace record" className="rounded border border-sky-700 px-2 py-1 text-sky-300 transition-colors hover:bg-sky-700/10"><Network size={12} /></Link>
                          <button onClick={() => handleReplay(row)} disabled={replayingId === row.id}
                            className="flex items-center gap-1 rounded border border-burgundy-700 px-2 py-1 text-xs text-burgundy-400 transition-colors hover:bg-burgundy-700/10 disabled:opacity-30">
                            {replayingId === row.id ? <Loader2 size={12} className="animate-spin" /> : <Repeat size={12} />}
                            Replay
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                }) : (
                  <tr><td colSpan={7} className="px-4 py-12 text-center text-sm text-glass-muted">No CBK submissions found</td></tr>
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

      <TwSnackbar open={toast.open} message={toast.message} severity={toast.severity} onClose={() => setToast(t => ({ ...t, open: false }))} />
    </div>
  );
}
