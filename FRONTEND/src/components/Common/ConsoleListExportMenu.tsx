import { useState } from "react";
import { ChevronDown, Download, Loader2 } from "lucide-react";
import GlassButton from "./GlassButton";
import {
  downloadConsoleExportJob,
  pollConsoleExportJob,
  saveExportBlob,
  startConsoleListExport,
  type ConsoleExportFormat,
  type ConsoleListViewType,
} from "../../features/api/consoleExportApi";

interface Props {
  viewType: ConsoleListViewType;
  filters: Record<string, string>;
  disabled?: boolean;
  label?: string;
}

/** On-demand export menu (CSV, Excel, PDF) that replays the current URL filter state on the Control Plane. */
export default function ConsoleListExportMenu({ viewType, filters, disabled, label = "Export" }: Props) {
  const [open, setOpen] = useState(false);
  const [includeCorrelated, setIncludeCorrelated] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  /** Runs one export for the selected format, handling async jobs when the backend queues them. */
  const runExport = async (format: ConsoleExportFormat) => {
    setOpen(false);
    setBusy(true);
    setMessage(null);
    try {
      const result = await startConsoleListExport({ viewType, format, filters, includeCorrelated });
      if (result.kind === "blob") {
        saveExportBlob(result.blob, result.filename);
        setMessage("Export downloaded.");
        return;
      }
      setMessage("Large export queued — preparing file…");
      let status = result.job;
      for (let i = 0; i < 120 && status.status !== "COMPLETED" && status.status !== "FAILED"; i++) {
        await new Promise((r) => setTimeout(r, 2000));
        status = await pollConsoleExportJob(status.jobUuid);
      }
      if (status.status === "FAILED") {
        throw new Error(status.errorMessage || "Export job failed");
      }
      if (status.status !== "COMPLETED") {
        throw new Error("Export is still running — check back shortly.");
      }
      const blob = await downloadConsoleExportJob(status.jobUuid);
      saveExportBlob(blob, status.fileName || `${viewType.toLowerCase()}-export`);
      setMessage("Export downloaded.");
    } catch (err: unknown) {
      setMessage((err as Error)?.message || "Export failed.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="relative flex flex-col items-start gap-1">
      <GlassButton
        variant="default"
        size="sm"
        disabled={disabled || busy}
        onClick={() => setOpen((v) => !v)}
      >
        {busy ? <Loader2 size={14} className="animate-spin" /> : <Download size={14} />}
        {label}
        <ChevronDown size={14} />
      </GlassButton>
      {open && (
        <>
          <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} aria-hidden />
          <div className="absolute left-0 z-20 mt-1 w-56 rounded-lg border border-hairline bg-burgundy-850 py-2 shadow-editorial">
            <label className="flex cursor-pointer items-center gap-2 px-3 py-2 text-xs text-glass-muted">
              <input
                type="checkbox"
                checked={includeCorrelated}
                onChange={(e) => setIncludeCorrelated(e.target.checked)}
              />
              Include linked records
            </label>
            {(["CSV", "Excel", "PDF"] as ConsoleExportFormat[]).map((format) => (
              <button
                key={format}
                type="button"
                className="block w-full px-3 py-2 text-left text-xs text-ink hover:bg-burgundy-800"
                onClick={() => runExport(format)}
              >
                {format}
              </button>
            ))}
          </div>
        </>
      )}
      {message && <span className="text-[11px] text-glass-muted">{message}</span>}
    </div>
  );
}
