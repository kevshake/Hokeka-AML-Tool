import { useState } from "react";
import { apiClient } from "../../lib/apiClient";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import TwBadge from "../../components/Common/TwBadge";
import { Loader2, Search, Shield, UserCheck } from "lucide-react";
import MonitoringAlertsPanel from "../../components/monitoring/MonitoringAlertsPanel";

export default function ScreeningPage() {
  const [name, setName] = useState("");
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<any>(null);
  const [error, setError] = useState<string | null>(null);

  const handleScreening = async () => {
    if (!name.trim()) { setError("Please enter a name to screen"); return; }
    setLoading(true); setError(null); setResult(null);
    try {
      const response = await apiClient.post("sanctions/screen", { name });
      setResult(response);
    } catch (err: any) { setError(err.message || "Screening failed"); }
    finally { setLoading(false); }
  };

  return (
    <HokekaPageShell title="Screening" subtitle="Sanctions, PEP, and watchlist screening">
      <div className="rounded-lg border border-white/10 bg-[var(--surface-2)] p-4">
        <div className="mb-4 flex items-center gap-2">
          <Shield size={20} className="text-burgundy-400" />
          <h3 className="text-base font-semibold text-white">Sanctions Screening</h3>
        </div>

        <div className="mb-4 flex gap-3">
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleScreening()}
            placeholder="Enter a name to screen against sanctions lists..."
            className="flex-1 rounded-lg border border-white/10 bg-[var(--surface-3)] px-4 py-2.5 text-sm text-white placeholder:text-white/30 focus:outline-none focus:ring-1 focus:ring-burgundy-700"
          />
          <button
            onClick={handleScreening}
            disabled={loading}
            className="flex min-w-[130px] items-center justify-center gap-1.5 rounded-lg bg-burgundy-700 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-burgundy-800 disabled:opacity-50"
          >
            {loading ? <Loader2 size={16} className="animate-spin" /> : <Search size={16} />}
            {loading ? "Screening..." : "Screen"}
          </button>
        </div>

        {error && (
          <div className="mb-3 rounded-lg border border-red-700/30 bg-red-900/30 px-4 py-3 text-sm text-red-200">{error}</div>
        )}

        {result && (
          <div className="rounded-lg border border-white/10 bg-[var(--surface-2)] p-4">
            <div className="mb-3 flex items-center justify-between">
              <h4 className="flex items-center gap-2 text-sm font-semibold text-white">
                <UserCheck size={16} /> Screening Results
              </h4>
              {result.matchFound !== undefined && (
                <TwBadge variant={result.matchFound ? "danger" : "success"}>
                  {result.matchFound ? "MATCH FOUND" : "NO MATCH"}
                </TwBadge>
              )}
            </div>
            <hr className="mb-3 border-white/10" />

            {result.matches && Array.isArray(result.matches) && result.matches.length > 0 ? (
              <div className="space-y-2">
                {result.matches.map((match: any, idx: number) => (
                  <div key={match.id ?? match.name ?? idx} className="rounded-lg border border-red-700/30 bg-red-900/10 p-3">
                    <p className="text-sm font-semibold text-white">{match.name || match.fullName || "Unknown"}</p>
                    <div className="mt-1 flex flex-wrap gap-3 text-xs text-glass-muted">
                      {match.listName && <span>List: {match.listName}</span>}
                      {match.score !== undefined && <span>Score: {match.score}</span>}
                      {match.category && <span>Category: {match.category}</span>}
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="space-y-1">
                {Object.entries(result).map(([key, val]) => (
                  key !== "matches" && key !== "matchFound" ? (
                    <div key={key} className="flex gap-2 text-sm">
                      <span className="min-w-[120px] text-xs font-semibold uppercase tracking-wider text-glass-muted">
                        {key.replace(/([A-Z])/g, " $1").trim()}
                      </span>
                      <span className="text-white/80">
                        {typeof val === "object" ? JSON.stringify(val) : String(val)}
                      </span>
                    </div>
                  ) : null
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      <div className="mt-4">
        <MonitoringAlertsPanel />
      </div>
    </HokekaPageShell>
  );
}
