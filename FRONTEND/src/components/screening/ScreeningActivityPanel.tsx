import { useQuery } from "@tanstack/react-query";
import { History, LoaderCircle } from "lucide-react";
import { apiClient } from "../../lib/apiClient";
import TwBadge from "../Common/TwBadge";

interface ScreeningActivityRow {
  id: number;
  txnId?: number;
  merchantId?: number;
  channel?: string;
  matchFound?: boolean;
  pepMatchFound?: boolean;
  createdAt?: string;
  subjectName?: string;
}

interface PageResponse {
  content: ScreeningActivityRow[];
  totalElements: number;
}

interface Props {
  txnId?: string;
  merchantId?: string;
  title?: string;
}

/** Lists persisted screening runs from GET /screening/activity for regulators and analysts. */
export default function ScreeningActivityPanel({
  txnId,
  merchantId,
  title = "Screening activity",
}: Props) {
  const query = useQuery<PageResponse>({
    queryKey: ["screening-activity", txnId, merchantId],
    queryFn: () => {
      const params = new URLSearchParams();
      if (txnId) params.set("txnId", txnId);
      if (merchantId) params.set("merchantId", merchantId);
      params.set("size", "10");
      return apiClient.get<PageResponse>(`screening/activity?${params.toString()}`);
    },
    enabled: Boolean(txnId || merchantId),
  });

  if (!txnId && !merchantId) {
    return null;
  }

  if (query.isLoading) {
    return (
      <p className="flex items-center gap-2 px-4 py-6 text-sm text-white/50">
        <LoaderCircle className="animate-spin" size={16} /> Loading screening activity…
      </p>
    );
  }

  if (query.isError) {
    return (
      <p className="px-4 py-6 text-sm text-white/45">
        Screening activity is unavailable for your role or tenant scope.
      </p>
    );
  }

  const rows = query.data?.content ?? [];

  return (
    <section>
      <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold">
        <History size={16} className="text-gold" /> {title}
      </h2>
      <div className="divide-y divide-white/8 border border-white/10">
        {!rows.length ? (
          <p className="px-4 py-8 text-center text-sm text-white/45">No screening runs recorded yet.</p>
        ) : (
          rows.map((row) => (
            <div key={row.id} className="flex flex-wrap items-center justify-between gap-2 px-4 py-3 text-sm">
              <div>
                <p className="font-medium text-white/85">{row.subjectName ?? `Run #${row.id}`}</p>
                <p className="mt-0.5 text-xs text-white/40">
                  {row.channel ?? "SCREEN"} ·{" "}
                  {row.createdAt ? new Date(row.createdAt).toLocaleString() : "—"}
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                {row.matchFound ? <TwBadge variant="danger">Match</TwBadge> : <TwBadge variant="success">Clear</TwBadge>}
                {row.pepMatchFound ? <TwBadge variant="warning">PEP</TwBadge> : null}
              </div>
            </div>
          ))
        )}
      </div>
    </section>
  );
}
