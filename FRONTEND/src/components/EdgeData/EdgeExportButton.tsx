import { useState } from "react";
import { AlertTriangle, Download, Loader2 } from "lucide-react";
import { useEdgeSupport } from "../../contexts/edgeSupport";
import { saveExportBlob } from "../../features/api/consoleExportApi";
import { EdgeRequestError, stateForError, type QueryValue } from "../../lib/edgeData";
import { streamEdgeExport, type EdgeExportFormat } from "../../lib/edgeExport";

interface Props {
  /** Streamed export endpoint, e.g. {@code "transactions/export"}. */
  path: string;
  /** The list's filters, so the export matches what is on screen (all pages). */
  query: Record<string, QueryValue>;
  disabled?: boolean;
  /** Whether a platform admin needs a support session for this export. */
  requiresSupportSession?: boolean;
}

/** Message for a failed export. */
function failureText(error: unknown): string {
  switch (stateForError(error)) {
    case "offline":
      return "The PSP's Edge is offline, so nothing could be exported.";
    case "timeout":
      return "The Edge did not answer in time. Narrow the time window and try again.";
    case "busy":
      return "The Edge is busy. Try again shortly.";
    case "support-disabled":
      return "Support access is not enabled by this PSP.";
    case "support-required":
      return "Open a support session (PSP and reason) first.";
    case "tenant-error":
      return "You can only export your own PSP's data.";
    default:
      return error instanceof EdgeRequestError && error.body.message ? error.body.message : "The export failed.";
  }
}

/**
 * Downloads every row matching the list's filters as CSV or JSON lines, streamed from the Edge
 * through the Control Plane (never stored there), and warns when the export is incomplete because
 * a site did not answer.
 */
export default function EdgeExportButton({ path, query, disabled, requiresSupportSession = true }: Props) {
  const support = useEdgeSupport();
  const [busy, setBusy] = useState<EdgeExportFormat | null>(null);
  const [warning, setWarning] = useState<string | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const gated = support.isPlatform && requiresSupportSession && !support.ready;

  /** Runs one export and reports its completeness. */
  const run = async (format: EdgeExportFormat) => {
    setBusy(format);
    setWarning(null);
    setFailure(null);
    try {
      const result = await streamEdgeExport(path, format, query, support.params);
      saveExportBlob(result.blob, result.filename);
      if (result.incomplete) {
        setWarning(
          `Incomplete export${result.rows != null ? ` (${result.rows} rows)` : ""}: some Edge sites did not answer` +
            (result.unavailableSites ? ` (${result.unavailableSites})` : "") + ". The file says so in its last line.",
        );
      }
    } catch (error) {
      setFailure(failureText(error));
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="flex flex-col gap-1">
      <div className="flex items-center gap-2">
        {(["csv", "ndjson"] as const).map((format) => (
          <button
            key={format}
            type="button"
            disabled={disabled || gated || busy !== null}
            onClick={() => void run(format)}
            className="flex items-center gap-1.5 rounded-lg border border-hairline px-3 py-1.5 text-xs text-ink transition-colors hover:bg-white/5 disabled:opacity-40"
          >
            {busy === format ? <Loader2 size={13} className="animate-spin" /> : <Download size={13} />}
            Export {format === "csv" ? "CSV" : "JSON lines"}
          </button>
        ))}
      </div>
      {warning && (
        <p role="alert" data-testid="export-incomplete" className="flex items-center gap-1 text-xs text-amber-300">
          <AlertTriangle size={13} /> {warning}
        </p>
      )}
      {failure && <p role="alert" className="text-xs text-red-300">{failure}</p>}
    </div>
  );
}
