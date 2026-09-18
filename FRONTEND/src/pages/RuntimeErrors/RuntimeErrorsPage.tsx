import { Fragment, useState } from "react";
import { useRuntimeErrors, RuntimeError } from "../../features/api/queries";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import TwPagination from "../../components/Common/TwPagination";
import { Loader2, ChevronDown, ChevronRight } from "lucide-react";

/**
 * W31-1: admin viewer for GET /admin/runtime-errors. The endpoint (RuntimeErrorController,
 * backed by the runtime_errors table) existed with no frontend caller at all -- server errors
 * were persisted but nobody could see them without a direct DB query.
 */
export default function RuntimeErrorsPage() {
  const [page, setPage] = useState({ index: 0, size: 25 });
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const { data, isLoading } = useRuntimeErrors(page.index, page.size);

  const content = data?.content || [];
  const totalElements = data?.totalElements ?? 0;
  const totalPages = data?.totalPages ?? 1;

  return (
    <HokekaPageShell title="Runtime Errors" subtitle="Persisted server errors, most recent first" noCard>
      <div className="overflow-hidden rounded-lg border border-white/10 bg-[var(--surface-2)]">
        <div className="overflow-auto" style={{ maxHeight: "calc(100vh - 320px)" }}>
          {isLoading ? (
            <div className="flex items-center justify-center py-16">
              <Loader2 size={24} className="animate-spin text-glass-muted" />
              <span className="ml-3 text-sm text-glass-muted">Loading runtime errors...</span>
            </div>
          ) : (
            <table className="w-full border-collapse">
              <thead className="sticky top-0 z-10">
                <tr className="border-b border-white/10 bg-[var(--surface-2)]">
                  <th className="w-8 px-4 py-3" />
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Occurred At</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Error Code</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">PSP</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">User</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Message</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {content.map((err: RuntimeError) => {
                  const isExpanded = expandedId === err.id;
                  return (
                    <Fragment key={err.id}>
                      <tr
                        className="cursor-pointer transition-colors hover:bg-white/[0.02]"
                        onClick={() => setExpandedId(isExpanded ? null : err.id)}
                      >
                        <td className="px-4 py-3 text-glass-muted">
                          {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
                        </td>
                        <td className="whitespace-nowrap px-4 py-3 text-sm text-white">
                          {err.occurredAt ? new Date(err.occurredAt).toLocaleString() : "—"}
                        </td>
                        <td className="whitespace-nowrap px-4 py-3 font-mono text-sm text-white/80">
                          {err.errorCode || "—"}
                        </td>
                        <td className="whitespace-nowrap px-4 py-3 text-sm text-glass-muted">
                          {err.pspId ? `PSP #${err.pspId}` : "System"}
                        </td>
                        <td className="whitespace-nowrap px-4 py-3 text-sm text-glass-muted">
                          {err.userId ?? "—"}
                        </td>
                        <td className="max-w-md truncate px-4 py-3 text-sm text-white/80">
                          {err.message || <span className="text-glass-muted/50">N/A</span>}
                        </td>
                      </tr>
                      {isExpanded && (
                        <tr className="bg-black/20">
                          <td colSpan={6} className="px-4 py-3">
                            <pre className="max-h-64 overflow-auto whitespace-pre-wrap break-words rounded-lg border border-white/10 bg-black/30 p-3 font-mono text-xs text-white/70">
                              {err.stackTrace || "No stack trace recorded."}
                            </pre>
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  );
                })}
                {!content.length && (
                  <tr>
                    <td colSpan={6} className="px-4 py-8 text-center text-sm text-glass-muted">
                      No runtime errors recorded
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
          onPageChange={(p) => setPage((prev) => ({ ...prev, index: p }))}
          onRowsPerPageChange={(s) => setPage({ index: 0, size: s })}
        />
      </div>
    </HokekaPageShell>
  );
}
