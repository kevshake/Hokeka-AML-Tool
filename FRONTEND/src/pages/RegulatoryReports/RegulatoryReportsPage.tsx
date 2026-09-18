import { useState } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRegulatoryReport } from "../../features/api/queries";
import { Download, Loader2, Save } from "lucide-react";
import CbkSubmissionsTab from "./tabs/CbkSubmissionsTab";
import RegulatoryFilingsTab from "./tabs/RegulatoryFilingsTab";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import { apiClient } from "../../lib/apiClient";
import { useAuth } from "../../contexts/AuthContext";

type MainTab = "reports" | "filings" | "cbk-submissions" | "fx-rates";

interface RegulatoryRate {
  currencyCode: string;
  currencyName?: string;
  rateToUsd: number;
  isActive: boolean;
  rateSource?: string;
  effectiveAt?: string;
  expiresAt?: string;
  regulatoryApproved: boolean;
  approvedBy?: string;
  approvedAt?: string;
}

export default function RegulatoryReportsPage() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const [mainTab, setMainTab] = useState<MainTab>("reports");
  const [reportType, setReportType] = useState<"ctr" | "lctr" | "iftr">("ctr");
  const [rateForm, setRateForm] = useState({
    currency: "KES",
    rateToUsd: "",
    source: "",
    effectiveAt: new Date().toISOString().slice(0, 16),
    expiresAt: "",
  });
  const { data: report, isLoading } = useRegulatoryReport(reportType);
  const canManageRates = ["SUPER_ADMIN", "ADMIN"].includes(user?.role?.name?.toUpperCase() || "");
  const ratesQuery = useQuery<RegulatoryRate[]>({
    queryKey: ["compliance", "exchange-rates"],
    queryFn: () => apiClient.get<RegulatoryRate[]>("compliance/exchange-rates"),
    enabled: mainTab === "fx-rates",
  });
  const approveRate = useMutation({
    mutationFn: () => apiClient.put<RegulatoryRate>(
      `compliance/exchange-rates/${rateForm.currency.trim().toUpperCase()}`,
      {
        rateToUsd: Number(rateForm.rateToUsd),
        source: rateForm.source.trim(),
        effectiveAt: rateForm.effectiveAt || null,
        expiresAt: rateForm.expiresAt || null,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["compliance", "exchange-rates"] }),
  });

  const handleExport = () => {
    if (!report) return;
    const today = new Date().toISOString().split("T")[0];
    const transactions = Array.isArray((report as any).transactionDetails)
      ? (report as any).transactionDetails
      : Array.isArray(report.transactions) ? report.transactions : [];
    const headers = ["Transaction ID", "Merchant ID", "Original Amount", "Currency", "USD Equivalent", "FX Source", "Date"];
    const rows = transactions.map((txn: any) => [
      txn.txnId || txn.id || txn.transactionId || "", txn.merchantId || "",
      txn.amount != null ? Number(txn.amount).toFixed(2) : txn.amountCents != null ? (txn.amountCents / 100).toFixed(2) : "0.00",
      txn.currency || "",
      txn.usdEquivalent != null ? Number(txn.usdEquivalent).toFixed(2) : "",
      txn.rateSource || "",
      txn.transactionDate || txn.txnTs || txn.timestamp ? new Date(txn.transactionDate || txn.txnTs || txn.timestamp).toISOString().split("T")[0] : "",
    ]);
    const summary = [`${reportType.toUpperCase()} Report`, `Total Transactions,${report.totalTransactions || report.transactionCount || 0}`,
      `Total Amount,${report.totalAmount ? Number(report.totalAmount).toFixed(2) : "0.00"}`, "", headers.join(","),
      ...rows.map((r: string[]) => r.join(",")),
    ].join("\n");
    const blob = new Blob([summary], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url; a.download = `${reportType.toUpperCase()}-report-${today}.csv`;
    a.click(); URL.revokeObjectURL(url);
  };

  const tabs: MainTab[] = ["reports", "filings", "cbk-submissions", "fx-rates"];
  const subTabs = ["ctr", "lctr", "iftr"] as const;

  return (
    <HokekaPageShell title="Regulatory Reports" subtitle="FIU reports and CBK submissions" noCard>
      <div className="mb-4 flex border-b border-white/10">
        {tabs.map(t => (
          <button key={t} onClick={() => setMainTab(t)}
            className={`px-5 py-2.5 text-sm font-medium transition-colors ${mainTab === t ? "border-b-2 border-burgundy-700 text-burgundy-400" : "text-glass-muted hover:text-white"}`}>
            {t === "reports" ? "FIU Reports" : t === "filings" ? "Regulatory Filings" : t === "cbk-submissions" ? "CBK Submissions" : "Regulatory FX"}
          </button>
        ))}
      </div>

      {mainTab === "cbk-submissions" && <CbkSubmissionsTab />}
      {mainTab === "filings" && <RegulatoryFilingsTab />}

      {mainTab === "fx-rates" && (
        <div className="space-y-4">
          {canManageRates && (
            <form
              onSubmit={event => {
                event.preventDefault();
                approveRate.mutate();
              }}
              className="grid grid-cols-1 gap-3 border-b border-white/10 pb-4 md:grid-cols-6"
            >
              <label className="text-xs text-glass-muted">
                Currency
                <input value={rateForm.currency} maxLength={3} required onChange={event => setRateForm({ ...rateForm, currency: event.target.value.toUpperCase() })} className="mt-1 w-full rounded border border-white/10 bg-[var(--surface-2)] px-3 py-2 text-sm text-white" />
              </label>
              <label className="text-xs text-glass-muted">
                Rate to USD
                <input value={rateForm.rateToUsd} type="number" min="0" step="0.000001" required onChange={event => setRateForm({ ...rateForm, rateToUsd: event.target.value })} className="mt-1 w-full rounded border border-white/10 bg-[var(--surface-2)] px-3 py-2 text-sm text-white" />
              </label>
              <label className="text-xs text-glass-muted">
                Source
                <input value={rateForm.source} required onChange={event => setRateForm({ ...rateForm, source: event.target.value })} className="mt-1 w-full rounded border border-white/10 bg-[var(--surface-2)] px-3 py-2 text-sm text-white" />
              </label>
              <label className="text-xs text-glass-muted">
                Effective
                <input value={rateForm.effectiveAt} type="datetime-local" required onChange={event => setRateForm({ ...rateForm, effectiveAt: event.target.value })} className="mt-1 w-full rounded border border-white/10 bg-[var(--surface-2)] px-3 py-2 text-sm text-white" />
              </label>
              <label className="text-xs text-glass-muted">
                Expires
                <input value={rateForm.expiresAt} type="datetime-local" onChange={event => setRateForm({ ...rateForm, expiresAt: event.target.value })} className="mt-1 w-full rounded border border-white/10 bg-[var(--surface-2)] px-3 py-2 text-sm text-white" />
              </label>
              <button type="submit" disabled={approveRate.isPending} className="mt-5 flex h-10 items-center justify-center gap-2 rounded bg-burgundy-700 px-3 text-sm font-medium text-white disabled:opacity-40">
                {approveRate.isPending ? <Loader2 size={15} className="animate-spin" /> : <Save size={15} />}
                Approve
              </button>
            </form>
          )}

          {ratesQuery.isLoading ? (
            <div className="flex items-center gap-2 py-8 text-sm text-glass-muted"><Loader2 size={16} className="animate-spin" /> Loading rates...</div>
          ) : (
            <div className="overflow-hidden rounded-lg border border-white/10">
              <table className="w-full border-collapse">
                <thead><tr className="border-b border-white/10 bg-[var(--surface-3)]">
                  {["Currency", "Rate to USD", "Source", "Effective", "Expires", "Approval"].map(label => (
                    <th key={label} className="px-4 py-2 text-left text-xs font-semibold uppercase text-glass-muted">{label}</th>
                  ))}
                </tr></thead>
                <tbody className="divide-y divide-white/5">
                  {(ratesQuery.data || []).map(rate => (
                    <tr key={rate.currencyCode}>
                      <td className="px-4 py-2 text-sm font-semibold text-white">{rate.currencyCode}</td>
                      <td className="px-4 py-2 text-sm text-white/80">{Number(rate.rateToUsd).toFixed(6)}</td>
                      <td className="px-4 py-2 text-sm text-white/80">{rate.rateSource || "N/A"}</td>
                      <td className="px-4 py-2 text-sm text-glass-muted">{rate.effectiveAt ? new Date(rate.effectiveAt).toLocaleString() : "N/A"}</td>
                      <td className="px-4 py-2 text-sm text-glass-muted">{rate.expiresAt ? new Date(rate.expiresAt).toLocaleString() : "Open"}</td>
                      <td className="px-4 py-2 text-sm">
                        <span className={`inline-flex rounded px-2 py-1 text-xs font-semibold ${rate.regulatoryApproved ? "bg-emerald-500/15 text-emerald-300" : "bg-amber-500/15 text-amber-300"}`}>
                          {rate.regulatoryApproved ? "Approved" : "Not approved"}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {mainTab === "reports" && (
        <>
          <div className="mb-4 flex border-b border-white/10">
            {subTabs.map(t => (
              <button key={t} onClick={() => setReportType(t)}
                className={`px-4 py-2 text-xs font-medium transition-colors ${reportType === t ? "border-b-2 border-burgundy-700 text-burgundy-400" : "text-glass-muted hover:text-white"}`}>
                {t.toUpperCase()} {t === "ctr" ? "(Currency Transaction Report)" : t === "lctr" ? "(Large Cash Transaction Report)" : "(International Funds Transfer Report)"}
              </button>
            ))}
          </div>

          <div className="rounded-lg border border-white/10 bg-[var(--surface-2)] p-4">
            <div className="mb-4 flex items-center justify-between">
              <h3 className="text-base font-semibold text-white">{reportType.toUpperCase()} Report</h3>
              <div className="flex gap-2">
                {(report as any)?.executionId && <Link to={`/records/REPORT_EXECUTION/${(report as any).executionId}`} className="flex items-center rounded-lg border border-gold/60 px-3 py-1.5 text-xs text-gold transition-colors hover:bg-gold hover:text-black">Trace execution</Link>}
                <button onClick={handleExport} disabled={!report} className="flex items-center gap-1.5 rounded-lg border border-burgundy-700 px-3 py-1.5 text-xs text-burgundy-400 transition-colors hover:bg-burgundy-700/10 disabled:opacity-30">
                  <Download size={14} /> Export
                </button>
              </div>
            </div>

            {isLoading ? (
              <div className="flex items-center gap-2 py-8 text-sm text-glass-muted"><Loader2 size={16} className="animate-spin" /> Generating report...</div>
            ) : report ? (
              <>
                <div className="mb-4 grid grid-cols-3 gap-3">
                  {[
                    { label: "Total Transactions", value: (report.totalTransactions || report.transactionCount || 0).toLocaleString() },
                    { label: "Total Amount", value: report.totalAmount ? Number(report.totalAmount).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : "0.00" },
                    { label: "Report Period", value: report.startDate && report.endDate ? `${new Date(report.startDate).toLocaleDateString()} - ${new Date(report.endDate).toLocaleDateString()}` : "Last 30 days" },
                  ].map(s => (
                    <div key={s.label} className="rounded-lg border border-white/10 bg-[var(--surface-2)] p-3">
                      <p className="text-xs text-glass-muted">{s.label}</p>
                      <p className="text-lg font-bold text-white">{s.value}</p>
                    </div>
                  ))}
                </div>

                {((report as any).transactionDetails || report.transactions) && (
                  <div className="overflow-hidden rounded-lg border border-white/10">
                    <table className="w-full border-collapse">
                      <thead><tr className="border-b border-white/10 bg-[var(--surface-3)]">
                        <th className="px-4 py-2 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Transaction ID</th>
                        <th className="px-4 py-2 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Merchant</th>
                        <th className="px-4 py-2 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Amount</th>
                        <th className="px-4 py-2 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">USD Evidence</th>
                        <th className="px-4 py-2 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Date</th>
                      </tr></thead>
                      <tbody className="divide-y divide-white/5">
                        {(((report as any).transactionDetails || report.transactions || []) as any[]).slice(0, 20).map((txn: any, idx: number) => {
                          const transactionId = txn.txnId ?? txn.id ?? txn.transactionId;
                          return <tr key={transactionId || idx} className="transition-colors hover:bg-white/[0.02]">
                            <td className="px-4 py-2 text-sm text-white">{transactionId ? <Link to={`/records/TRANSACTION/${transactionId}`} className="text-gold hover:underline">#{transactionId}</Link> : "N/A"}</td>
                            <td className="px-4 py-2 text-sm text-white/80">{txn.merchantId ? <Link to={`/records/MERCHANT/${txn.merchantId}`} className="text-gold hover:underline">{txn.merchantName || txn.merchantId}</Link> : txn.merchantName || "N/A"}</td>
                            <td className="px-4 py-2 text-sm text-white/80">{txn.currency || ""} {txn.amount != null ? Number(txn.amount).toFixed(2) : txn.amountCents ? (txn.amountCents / 100).toFixed(2) : "0.00"}</td>
                            <td className="px-4 py-2 text-sm text-white/80">{txn.usdEquivalent != null ? `USD ${Number(txn.usdEquivalent).toFixed(2)} via ${txn.rateSource || "recorded rate"}` : "N/A"}</td>
                            <td className="px-4 py-2 text-sm text-glass-muted">{txn.transactionDate || txn.txnTs || txn.timestamp ? new Date(txn.transactionDate || txn.txnTs || txn.timestamp).toLocaleDateString() : "N/A"}</td>
                          </tr>;
                        })}
                      </tbody>
                    </table>
                  </div>
                )}

                {report.summary && (
                  <div className="mt-4 rounded-lg border border-white/10 bg-[var(--surface-2)] p-3">
                    <h4 className="mb-1 text-sm font-semibold text-white">Report Summary</h4>
                    <p className="whitespace-pre-wrap text-xs text-glass-muted">{typeof report.summary === "string" ? report.summary : JSON.stringify(report.summary, null, 2)}</p>
                  </div>
                )}
              </>
            ) : (
              <p className="py-8 text-sm text-glass-muted">Click "Generate Report" to create a {reportType.toUpperCase()} report</p>
            )}
          </div>
        </>
      )}
    </HokekaPageShell>
  );
}
