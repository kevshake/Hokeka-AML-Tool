import { AlertTriangle, CloudOff, Hourglass, RefreshCw, ShieldAlert, TimerOff } from "lucide-react";
import type { ReactNode } from "react";
import { siteCodeLabel, type EdgeDataState } from "../../lib/edgeData";
import type { EdgeDataError, EdgeUnavailableSite } from "../../types";

interface Props {
  state: EdgeDataState;
  lastSeenAt?: string;
  unavailableSites?: EdgeUnavailableSite[];
  error?: EdgeDataError;
  retryAfterMs?: number;
  onRetry?: () => void;
  /** What still shows while the Edge data is missing, e.g. "Alert fields held in the cloud are shown." */
  cloudNote?: string;
}

/** Short local date-time, or the raw value when it does not parse. */
function when(value: string | undefined): string {
  if (!value) return "unknown";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

/** One line per site that did not answer. */
function SiteList({ sites }: { sites: EdgeUnavailableSite[] }) {
  if (!sites.length) return null;
  return (
    <ul className="mt-2 space-y-0.5 text-xs" aria-label="Unavailable Edge sites">
      {sites.map((site) => (
        <li key={`${site.siteId}-${site.code}`}>
          <span className="font-mono">{site.siteId}</span>: {siteCodeLabel(site.code)}
          {site.lastSeenAt ? `, last seen ${when(site.lastSeenAt)}` : ""}
        </li>
      ))}
    </ul>
  );
}

/** Banner shell with tone colours. */
function Shell({ tone, icon, title, children, onRetry }: {
  tone: "danger" | "warning" | "info";
  icon: ReactNode;
  title: string;
  children?: ReactNode;
  onRetry?: () => void;
}) {
  const colours = {
    danger: "border-red-700/40 bg-red-900/25 text-red-100",
    warning: "border-amber-600/40 bg-amber-900/25 text-amber-100",
    info: "border-sky-600/40 bg-sky-900/25 text-sky-100",
  }[tone];
  return (
    <div role="status" data-testid="edge-banner" className={`flex items-start gap-3 rounded-lg border px-4 py-3 text-sm ${colours}`}>
      <span className="mt-0.5 shrink-0">{icon}</span>
      <div className="min-w-0 flex-1">
        <p className="font-semibold">{title}</p>
        {children}
      </div>
      {onRetry && (
        <button type="button" onClick={onRetry} className="flex shrink-0 items-center gap-1 text-xs underline-offset-2 hover:underline">
          <RefreshCw size={13} /> Retry
        </button>
      )}
    </div>
  );
}

/**
 * Tells the user that transaction data could not be pulled (or only partly) from the PSP's Edge:
 * offline with the time it was last seen, timed out, busy, a tenant or support-access refusal, and
 * the sites that did not answer. Renders nothing for loading, idle and complete answers.
 */
export default function EdgeOfflineBanner({ state, lastSeenAt, unavailableSites = [], error, retryAfterMs, onRetry, cloudNote }: Props) {
  const note = cloudNote ? <p className="mt-1 text-xs opacity-80">{cloudNote}</p> : null;
  switch (state) {
    case "offline":
      return (
        <Shell tone="danger" icon={<CloudOff size={16} />} title="The PSP's Edge is offline" onRetry={onRetry}>
          <p className="mt-1 text-xs">
            Transaction details are held on the Edge and cannot be shown until it reconnects. Last seen: {when(lastSeenAt ?? error?.lastSeenAt)}.
          </p>
          <SiteList sites={unavailableSites} />
          {note}
        </Shell>
      );
    case "partial":
      return (
        <Shell tone="warning" icon={<AlertTriangle size={16} />} title="Incomplete results: some Edge sites did not answer" onRetry={onRetry}>
          <p className="mt-1 text-xs">The rows shown come from the sites that answered. Missing sites are kept in place so later pages include them once they answer.</p>
          <SiteList sites={unavailableSites} />
          {note}
        </Shell>
      );
    case "timeout":
      return (
        <Shell tone="warning" icon={<TimerOff size={16} />} title="The Edge did not answer in time" onRetry={onRetry}>
          <p className="mt-1 text-xs">Try again, or narrow the time window or filters.</p>
          {note}
        </Shell>
      );
    case "busy":
      return (
        <Shell tone="warning" icon={<Hourglass size={16} />} title="The Edge is busy" onRetry={onRetry}>
          <p className="mt-1 text-xs">
            Every node of the site is handling its maximum number of requests.
            {retryAfterMs != null ? ` Try again in ${Math.max(1, Math.ceil(retryAfterMs / 1000))} s.` : " Try again shortly."}
          </p>
          {note}
        </Shell>
      );
    case "tenant-error":
      return (
        <Shell tone="danger" icon={<ShieldAlert size={16} />} title="This data belongs to another PSP">
          <p className="mt-1 text-xs">You can only read your own PSP's transactions.</p>
        </Shell>
      );
    case "support-disabled":
      return (
        <Shell tone="danger" icon={<ShieldAlert size={16} />} title="Support access is not enabled by this PSP">
          <p className="mt-1 text-xs">
            Hokeka staff can read a PSP's transaction data only after a PSP administrator allows it in Settings → Support &amp; Data Sharing.
          </p>
          {note}
        </Shell>
      );
    case "support-required":
      return (
        <Shell tone="info" icon={<ShieldAlert size={16} />} title="A support reason is needed">
          <p className="mt-1 text-xs">Choose the PSP and give a support reason to load its transaction data. Every pull is recorded in the PSP's data access log.</p>
          {note}
        </Shell>
      );
    case "not-found":
      return (
        <Shell tone="info" icon={<AlertTriangle size={16} />} title="Transaction not found on the Edge">
          <p className="mt-1 text-xs">It may have been purged under the PSP's retention period.</p>
          {note}
        </Shell>
      );
    case "error":
      return (
        <Shell tone="danger" icon={<AlertTriangle size={16} />} title="Transaction data could not be loaded" onRetry={onRetry}>
          {error?.message && <p className="mt-1 text-xs">{error.message}</p>}
          {note}
        </Shell>
      );
    default:
      return null;
  }
}
