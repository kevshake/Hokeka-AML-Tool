import { useState } from "react";
import { useRiskHeatmap, useRiskTrends } from "../../features/api/queries";
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
} from "chart.js";
import { Line } from "react-chartjs-2";
import GlassCard from "../../components/Common/GlassCard";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import { Loader2 } from "lucide-react";
import { colors } from "../../theme/tokens";

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend);

export default function RiskAnalyticsPage() {
  const [period, setPeriod] = useState<number>(30);
  const [heatmapType, setHeatmapType] = useState<"customer" | "merchant">("customer");

  const { data: heatmap } = useRiskHeatmap(heatmapType);
  const { data: trends } = useRiskTrends(period);

  const trendsChartData = trends
    ? {
        labels: trends.labels || Object.keys(trends),
        datasets: [
          {
            label: "Risk Trend",
            data: trends.data || Object.values(trends),
            borderColor: colors.gold,
            backgroundColor: "rgba(211, 179, 113, 0.12)",
            tension: 0.4,
          },
        ],
      }
    : null;

  const heatmapEntries = heatmap ? Object.entries(heatmap).slice(0, 20) : [];

  return (
    <HokekaPageShell title="Risk Analytics" subtitle="Heatmaps, trends, and risk distribution" noCard>
      <div className="mb-3 flex justify-end">
        <div className="flex gap-3">
          <select value={period} onChange={(e) => setPeriod(Number(e.target.value))}
            className="rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-1.5 text-xs text-white focus:outline-none focus:ring-1 focus:ring-burgundy-700">
            <option value={7}>7 days</option>
            <option value={30}>30 days</option>
            <option value={90}>90 days</option>
            <option value={180}>180 days</option>
          </select>
          <select value={heatmapType} onChange={(e) => setHeatmapType(e.target.value as any)}
            className="rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-1.5 text-xs text-white focus:outline-none focus:ring-1 focus:ring-burgundy-700">
            <option value="customer">Customer</option>
            <option value="merchant">Merchant</option>
          </select>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <div className="md:col-span-2">
          <GlassCard padding="md" glowVariant="red" className="h-full">
            <h3 className="mb-3 text-base font-semibold text-white">Risk Heatmap - {heatmapType.charAt(0).toUpperCase() + heatmapType.slice(1)}</h3>
            {heatmapEntries.length > 0 ? (
              <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-3">
                {heatmapEntries.map(([key, value]: [string, any]) => {
                  const numVal = typeof value === "number" ? value : 0;
                  const isHigh = numVal > 50;
                  const isMed = numVal > 25;
                  return (
                    <div key={key} className={`rounded-lg border p-3 text-center ${
                      isHigh ? "border-red-700/30 bg-red-900/20" : isMed ? "border-amber-700/30 bg-amber-900/20" : "border-emerald-700/30 bg-emerald-900/20"
                    }`}>
                      <p className="text-xs text-glass-muted">{key}</p>
                      <p className="text-lg font-bold text-white">{typeof value === "number" ? value : String(value)}</p>
                    </div>
                  );
                })}
              </div>
            ) : (
              <div className="flex h-[300px] items-center justify-center text-sm text-glass-muted"><Loader2 size={20} className="mr-2 animate-spin" />Loading heatmap data...</div>
            )}
          </GlassCard>
        </div>
        <div>
          <GlassCard padding="md" glowVariant="orange" className="h-full">
            <h3 className="mb-3 text-base font-semibold text-white">Risk Trends ({period} days)</h3>
            {trendsChartData ? (
              <div className="h-[350px]">
                <Line
                  data={trendsChartData}
                  options={{
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: { legend: { labels: { color: colors.muted } } },
                    scales: {
                      x: { ticks: { color: colors.muted }, grid: { color: "rgba(255,255,255,0.10)" } },
                      y: { ticks: { color: colors.muted }, grid: { color: "rgba(255,255,255,0.10)" } },
                    },
                  }}
                />
              </div>
            ) : (
              <div className="flex h-[350px] items-center justify-center text-sm text-glass-muted"><Loader2 size={20} className="mr-2 animate-spin" />Loading trend data...</div>
            )}
          </GlassCard>
        </div>
      </div>
    </HokekaPageShell>
  );
}