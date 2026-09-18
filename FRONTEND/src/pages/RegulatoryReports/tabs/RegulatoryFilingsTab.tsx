import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import {
  Check,
  FileCheck2,
  Loader2,
  Network,
  RefreshCw,
  Send,
  ShieldCheck,
  X,
} from "lucide-react";
import TwBadge from "../../../components/Common/TwBadge";
import TwPagination from "../../../components/Common/TwPagination";
import TwSnackbar from "../../../components/Common/TwSnackbar";
import { useReportHistory } from "../../../features/api/reportQueries";
import {
  type RegulatorySubmission,
  useApproveRegulatorySubmission,
  useFileRegulatorySubmission,
  usePrepareRegulatorySubmission,
  useRegulatorySubmissions,
  useRejectRegulatorySubmission,
  useSubmitRegulatoryReview,
} from "../../../features/api/regulatorySubmissionQueries";

const statusVariant = (
  status: RegulatorySubmission["status"],
): "success" | "danger" | "warning" | "info" | "default" => {
  if (status === "FILED") return "success";
  if (status === "REJECTED") return "danger";
  if (status === "APPROVED" || status === "SUBMISSION_PENDING") return "warning";
  if (status === "PENDING_REVIEW") return "info";
  return "default";
};

const mutationMessage = (error: unknown) => {
  if (error && typeof error === "object" && "message" in error) {
    return String((error as { message?: string }).message || "Operation failed");
  }
  return "Operation failed";
};

export default function RegulatoryFilingsTab() {
  const [page, setPage] = useState({ index: 0, size: 25 });
  const [status, setStatus] = useState("");
  const [regulator, setRegulator] = useState("FRC");
  const [executionId, setExecutionId] = useState("");
  const [rejectingId, setRejectingId] = useState<number | null>(null);
  const [rejectionReason, setRejectionReason] = useState("");
  const [busyId, setBusyId] = useState<number | null>(null);
  const [toast, setToast] = useState<{
    open: boolean;
    severity: "success" | "error";
    message: string;
  }>({ open: false, severity: "success", message: "" });

  const submissions = useRegulatorySubmissions({
    status: status || undefined,
    regulator: regulator || undefined,
    page: page.index,
    size: page.size,
  });
  const history = useReportHistory({ page: 0, size: 100, status: "COMPLETED" });
  const prepare = usePrepareRegulatorySubmission();
  const submitReview = useSubmitRegulatoryReview();
  const approve = useApproveRegulatorySubmission();
  const file = useFileRegulatorySubmission();
  const reject = useRejectRegulatorySubmission();

  const eligibleExecutions = useMemo(
    () =>
      (history.data?.content || []).filter(
        (execution) =>
          execution.status === "completed"
          && ["CTR_001", "CTR_005"].includes(execution.reportCode || ""),
      ),
    [history.data?.content],
  );

  const run = async (
    id: number,
    operation: () => Promise<RegulatorySubmission>,
    successMessage: string,
  ) => {
    setBusyId(id);
    try {
      await operation();
      setToast({ open: true, severity: "success", message: successMessage });
    } catch (error) {
      setToast({ open: true, severity: "error", message: mutationMessage(error) });
    } finally {
      setBusyId(null);
    }
  };

  const selectClass =
    "rounded border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-xs text-white focus:outline-none focus:ring-1 focus:ring-burgundy-700";
  const rows = submissions.data?.content || [];

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-3 border-b border-white/10 pb-4">
        <label className="min-w-72 text-xs text-glass-muted">
          Completed filing report
          <select
            value={executionId}
            onChange={(event) => setExecutionId(event.target.value)}
            className={`mt-1 w-full ${selectClass}`}
          >
            <option value="">Select an exact execution</option>
            {eligibleExecutions.map((execution) => (
              <option key={execution.id} value={execution.id}>
                {execution.reportName} - {new Date(execution.createdAt).toLocaleString()}
              </option>
            ))}
          </select>
        </label>
        <button
          type="button"
          disabled={!executionId || prepare.isPending}
          onClick={async () => {
            try {
              await prepare.mutateAsync({
                executionId: Number(executionId),
                regulatoryBody: "FRC",
              });
              setToast({
                open: true,
                severity: "success",
                message: "FRC filing draft prepared from the selected execution.",
              });
            } catch (error) {
              setToast({
                open: true,
                severity: "error",
                message: mutationMessage(error),
              });
            }
          }}
          className="flex h-9 items-center gap-2 rounded bg-burgundy-700 px-3 text-xs font-semibold text-white disabled:opacity-40"
        >
          {prepare.isPending ? <Loader2 size={14} className="animate-spin" /> : <FileCheck2 size={14} />}
          Prepare FRC filing
        </button>
        <span className="text-xs text-glass-muted">
          Only completed CTR and cash-structuring executions are currently eligible.
        </span>
      </div>

      <div className="flex flex-wrap gap-2">
        <select
          value={regulator}
          onChange={(event) => {
            setRegulator(event.target.value);
            setPage((current) => ({ ...current, index: 0 }));
          }}
          className={selectClass}
        >
          <option value="">All regulators</option>
          <option value="FRC">FRC Kenya</option>
          <option value="FINCEN">FinCEN</option>
          <option value="FCA">FCA</option>
        </select>
        <select
          value={status}
          onChange={(event) => {
            setStatus(event.target.value);
            setPage((current) => ({ ...current, index: 0 }));
          }}
          className={selectClass}
        >
          <option value="">All statuses</option>
          {[
            "DRAFT",
            "PENDING_REVIEW",
            "APPROVED",
            "SUBMISSION_PENDING",
            "FILED",
            "REJECTED",
            "AMENDED",
          ].map((value) => <option key={value} value={value}>{value.replaceAll("_", " ")}</option>)}
        </select>
      </div>

      {submissions.isError && (
        <div className="rounded border border-red-700/30 bg-red-900/30 px-4 py-3 text-sm text-red-200">
          Failed to load regulatory filings.
        </div>
      )}

      <div className="overflow-hidden rounded border border-white/10 bg-[var(--surface-2)]">
        <div className="overflow-auto" style={{ maxHeight: "calc(100vh - 390px)" }}>
          {submissions.isLoading ? (
            <div className="flex justify-center py-12">
              <Loader2 size={28} className="animate-spin text-glass-muted" />
            </div>
          ) : (
            <table className="w-full border-collapse">
              <thead className="sticky top-0 z-10">
                <tr className="border-b border-white/10 bg-[var(--surface-2)]">
                  {["Reference", "Report", "Regulator", "Status", "Deadline", "Evidence", "Actions"].map((label) => (
                    <th key={label} className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase text-glass-muted">
                      {label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {rows.map((row) => (
                  <tr key={row.id} className="align-top hover:bg-white/[0.02]">
                    <td className="whitespace-nowrap px-4 py-3">
                      <Link to={`/records/REGULATORY_SUBMISSION/${row.id}`} className="font-mono text-xs text-gold hover:underline">
                        {row.submissionReference}
                      </Link>
                      {row.regulatorReference && <div className="mt-1 text-xs text-glass-muted">{row.regulatorReference}</div>}
                    </td>
                    <td className="min-w-52 px-4 py-3 text-xs text-white/80">
                      <div className="font-medium text-white">{row.reportName || row.reportCode}</div>
                      <div>{row.filingPeriodStart} to {row.filingPeriodEnd}</div>
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-xs text-white/80">{row.regulatorCode}</td>
                    <td className="whitespace-nowrap px-4 py-3">
                      <TwBadge variant={statusVariant(row.status)}>{row.status.replaceAll("_", " ")}</TwBadge>
                      {row.rejectionReason && <div className="mt-1 max-w-56 whitespace-normal text-xs text-red-300">{row.rejectionReason}</div>}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-xs text-glass-muted">
                      {row.filingDeadline || "Not set"}
                      {row.isLateFiling && <div className="mt-1 text-red-300">Filed late</div>}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3">
                      <div className="flex gap-1">
                        <Link title="Filing trail" to={`/records/REGULATORY_SUBMISSION/${row.id}`} className="rounded border border-sky-700 px-2 py-1 text-sky-300 hover:bg-sky-700/10">
                          <Network size={13} />
                        </Link>
                        {row.executionId && (
                          <Link title="Source execution" to={`/records/REPORT_EXECUTION/${row.executionId}`} className="rounded border border-gold/60 px-2 py-1 text-gold hover:bg-gold/10">
                            <ShieldCheck size={13} />
                          </Link>
                        )}
                      </div>
                    </td>
                    <td className="min-w-56 px-4 py-3">
                      <div className="flex flex-wrap gap-1">
                        {row.status === "DRAFT" && (
                          <button
                            title="Submit for review"
                            disabled={busyId === row.id}
                            onClick={() => run(row.id, () => submitReview.mutateAsync({ id: row.id }), "Filing sent for review.")}
                            className="flex items-center gap-1 rounded border border-sky-700 px-2 py-1 text-xs text-sky-300 disabled:opacity-40"
                          >
                            <Send size={12} /> Review
                          </button>
                        )}
                        {row.status === "PENDING_REVIEW" && (
                          <button
                            title="Approve filing"
                            disabled={busyId === row.id}
                            onClick={() => run(row.id, () => approve.mutateAsync({ id: row.id }), "Filing approved.")}
                            className="flex items-center gap-1 rounded border border-emerald-700 px-2 py-1 text-xs text-emerald-300 disabled:opacity-40"
                          >
                            <Check size={12} /> Approve
                          </button>
                        )}
                        {["APPROVED", "SUBMISSION_PENDING"].includes(row.status) && (
                          <button
                            title={row.status === "SUBMISSION_PENDING" ? "Retry filing" : "File with regulator"}
                            disabled={busyId === row.id}
                            onClick={() => run(row.id, () => file.mutateAsync({ id: row.id }), "Regulator transport completed.")}
                            className="flex items-center gap-1 rounded border border-burgundy-700 px-2 py-1 text-xs text-burgundy-400 disabled:opacity-40"
                          >
                            {row.status === "SUBMISSION_PENDING" ? <RefreshCw size={12} /> : <FileCheck2 size={12} />}
                            {row.status === "SUBMISSION_PENDING" ? "Retry" : "File"}
                          </button>
                        )}
                        {["DRAFT", "PENDING_REVIEW", "APPROVED", "SUBMISSION_PENDING"].includes(row.status) && (
                          <button
                            title="Reject filing"
                            onClick={() => {
                              setRejectingId(row.id);
                              setRejectionReason("");
                            }}
                            className="rounded border border-red-700 px-2 py-1 text-red-300"
                          >
                            <X size={12} />
                          </button>
                        )}
                      </div>
                      {rejectingId === row.id && (
                        <div className="mt-2 flex gap-1">
                          <input
                            value={rejectionReason}
                            onChange={(event) => setRejectionReason(event.target.value)}
                            placeholder="Rejection reason"
                            className="min-w-0 flex-1 rounded border border-white/10 bg-[var(--surface-3)] px-2 py-1 text-xs text-white"
                          />
                          <button
                            disabled={!rejectionReason.trim() || reject.isPending}
                            onClick={() => run(
                              row.id,
                              () => reject.mutateAsync({ id: row.id, reason: rejectionReason.trim() }),
                              "Filing rejected.",
                            ).then(() => setRejectingId(null))}
                            className="rounded bg-red-700 px-2 py-1 text-xs text-white disabled:opacity-40"
                          >
                            Confirm
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
                {rows.length === 0 && (
                  <tr>
                    <td colSpan={7} className="px-4 py-12 text-center text-sm text-glass-muted">
                      No regulatory filings match the selected filters.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          )}
        </div>
        <TwPagination
          page={page.index}
          totalPages={submissions.data?.totalPages || 1}
          totalCount={submissions.data?.totalElements || 0}
          rowsPerPage={page.size}
          onPageChange={(index) => setPage((current) => ({ ...current, index }))}
          onRowsPerPageChange={(size) => setPage({ index: 0, size })}
        />
      </div>

      <TwSnackbar
        open={toast.open}
        message={toast.message}
        severity={toast.severity}
        onClose={() => setToast((current) => ({ ...current, open: false }))}
      />
    </div>
  );
}
