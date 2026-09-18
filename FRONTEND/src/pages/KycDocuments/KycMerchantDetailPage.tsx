import { FormEvent, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  ArrowLeft, Building2, Check, ExternalLink, FileText, Loader2, Network,
  Newspaper, Plus, RefreshCw, Search, ShieldCheck, Trash2, UserRound, X,
} from "lucide-react";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import TwBadge from "../../components/Common/TwBadge";
import TwSnackbar from "../../components/Common/TwSnackbar";
import { apiClient } from "../../lib/apiClient";
import { getApiUrl } from "../../config/api";
import { useAuth } from "../../contexts/AuthContext";

type Tab = "overview" | "ownership" | "intelligence" | "edd" | "documents" | "network";

interface Owner {
  id: number; fullName: string; dateOfBirth: string; nationality: string; countryOfResidence?: string;
  passportEnding?: string; nationalIdEnding?: string; ownershipPercentage: number; pep: boolean;
  sanctioned: boolean; lastScreenedAt?: string;
}
interface Ownership { merchantId: number; totalOwnershipPercentage: number; complete: boolean; owners: Owner[]; ultimateBeneficialOwnerIds: number[]; }
interface EvidenceEvent { id: number; itemCode: string; previousValue?: boolean; newValue: boolean; performedBy: string; notes?: string; occurredAt: string; }
interface EddStatus {
  id?: number; status: string; sourceOfFundsVerified: boolean; sourceOfWealthVerified: boolean;
  siteVisitRequired: boolean; siteVisitCompleted: boolean; seniorManagementApproval: boolean;
  familyAssociateChecks: boolean; transactionPurposeReview: boolean; initiatedAt?: string; initiatedBy?: string;
  completedAt?: string; completedBy?: string; events: EvidenceEvent[];
}
interface Overview {
  merchantId: number; merchantName: string;
  cdd: { riskLevel: string; riskScore: number; cddLevel: string; requiredDocuments: string[]; eddRequired: boolean };
  completeness: { overallPercentage: number; completenessLevel: string; documentPercentage: number; ownerPercentage: number; basicInfoPercentage: number; missingItems: string[] };
  ownership: Ownership; edd: EddStatus;
}
interface DocumentEvidence {
  id: number; fileName: string; documentType: string; status: string; expiryDate?: string; uploadedAt?: string;
  verifiedBy?: string; verificationNotes?: string; malwareScanStatus?: string; fileSize?: number; sha256Hash?: string;
}
interface CorporateGraph {
  rootMerchantId: number; rootMerchantName: string;
  nodes: Array<{ id: string; label: string; type: string; details?: string; status?: string; root: boolean }>;
  edges: Array<{ relationship: string; source: string; target: string }>;
}
interface CorporateCandidate {
  name?: string; companyNumber?: string; jurisdictionCode?: string; currentStatus?: string;
  incorporationDate?: string; registeredAddress?: string; sourceUrl?: string;
}
interface AdverseMediaArticle {
  title?: string; url?: string; domain?: string; seenDate?: string; language?: string; sourceCountry?: string;
}
interface CorporateIntelligenceCheck {
  id: number; merchantId: number; checkType: string; status: string; registryStatus: string;
  registryProvider: string; registryMatchScore: number; matchedCompanyName?: string;
  matchedCompanyNumber?: string; matchedJurisdiction?: string; matchedCompanyStatus?: string;
  matchedCompanyUrl?: string; registryCandidates: CorporateCandidate[];
  registryProvenance: Record<string, unknown>; adverseMediaStatus: string;
  adverseMediaProvider: string; adverseMediaQuery?: string; adverseMediaArticleCount: number;
  adverseMediaArticles: AdverseMediaArticle[]; adverseMediaProvenance: Record<string, unknown>;
  riskScore: number; decisionReason: string; evidenceHash: string; checkedBy: string;
  checkedAt: string; retainUntil: string;
}

const blankOwner = { fullName: "", dateOfBirth: "", nationality: "KEN", countryOfResidence: "KEN", passportNumber: "", nationalId: "", ownershipPercentage: "" };
const eddItems = [
  ["SOURCE_OF_FUNDS", "Source of funds", "sourceOfFundsVerified"],
  ["SOURCE_OF_WEALTH", "Source of wealth", "sourceOfWealthVerified"],
  ["SITE_VISIT", "Site visit", "siteVisitCompleted"],
  ["SENIOR_MANAGEMENT_APPROVAL", "Senior management approval", "seniorManagementApproval"],
  ["FAMILY_ASSOCIATE_CHECKS", "Family and close-associate checks", "familyAssociateChecks"],
  ["TRANSACTION_PURPOSE_REVIEW", "Transaction purpose review", "transactionPurposeReview"],
] as const;

const errorMessage = (error: unknown) => {
  if (typeof error === "object" && error && "message" in error) return String((error as { message: unknown }).message);
  return "The request could not be completed.";
};

export default function KycMerchantDetailPage() {
  const { merchantId } = useParams();
  const id = Number(merchantId);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const [tab, setTab] = useState<Tab>("overview");
  const [ownerDialog, setOwnerDialog] = useState<Owner | "new" | null>(null);
  const [ownerForm, setOwnerForm] = useState(blankOwner);
  const [eddNotes, setEddNotes] = useState("");
  const [siteVisitRequired, setSiteVisitRequired] = useState(false);
  const [toast, setToast] = useState<{ open: boolean; severity: "success" | "error"; message: string }>({ open: false, severity: "success", message: "" });

  const role = user?.role?.name?.toUpperCase() || "";
  const canManage = ["SUPER_ADMIN", "ADMIN", "COMPLIANCE_OFFICER", "MLRO", "PSP_ADMIN"].includes(role);
  const canScreen = canManage || role === "SCREENING_ANALYST";
  const base = `compliance/kyc/merchants/${id}`;

  const overviewQuery = useQuery<Overview>({
    queryKey: ["kyc-overview", id], queryFn: () => apiClient.get(`${base}/overview`), enabled: Number.isFinite(id),
  });
  const documentsQuery = useQuery<DocumentEvidence[]>({
    queryKey: ["merchant-documents", id], queryFn: () => apiClient.get(`merchants/${id}/documents`), enabled: Number.isFinite(id),
  });
  const graphQuery = useQuery<CorporateGraph>({
    queryKey: ["corporate-graph", id], queryFn: () => apiClient.get(`compliance/kyc/corporate-graph/${id}`),
    enabled: Number.isFinite(id) && tab === "network",
  });
  const intelligenceQuery = useQuery<CorporateIntelligenceCheck[]>({
    queryKey: ["corporate-intelligence", id],
    queryFn: () => apiClient.get(`${base}/corporate-intelligence`),
    enabled: Number.isFinite(id) && tab === "intelligence",
  });

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["kyc-overview", id] }),
      queryClient.invalidateQueries({ queryKey: ["merchant-documents", id] }),
      queryClient.invalidateQueries({ queryKey: ["corporate-graph", id] }),
      queryClient.invalidateQueries({ queryKey: ["corporate-intelligence", id] }),
    ]);
  };
  const mutation = useMutation({
    mutationFn: async (action: () => Promise<unknown>) => action(),
    onSuccess: async () => { await refresh(); setToast({ open: true, severity: "success", message: "Due-diligence record updated." }); },
    onError: (error) => setToast({ open: true, severity: "error", message: errorMessage(error) }),
  });

  const overview = overviewQuery.data;
  const ownership = overview?.ownership;
  const edd = overview?.edd;
  const ultimateOwnerIds = useMemo(() => new Set(ownership?.ultimateBeneficialOwnerIds || []), [ownership]);

  const openOwner = (owner: Owner | "new") => {
    setOwnerDialog(owner);
    setOwnerForm(owner === "new" ? blankOwner : {
      fullName: owner.fullName, dateOfBirth: owner.dateOfBirth, nationality: owner.nationality,
      countryOfResidence: owner.countryOfResidence || "", passportNumber: "", nationalId: "",
      ownershipPercentage: String(owner.ownershipPercentage),
    });
  };
  const saveOwner = (event: FormEvent) => {
    event.preventDefault();
    const payload = { ...ownerForm, ownershipPercentage: Number(ownerForm.ownershipPercentage) };
    mutation.mutate(async () => {
      if (ownerDialog === "new") await apiClient.post(`${base}/beneficial-owners`, payload);
      else if (ownerDialog) await apiClient.put(`${base}/beneficial-owners/${ownerDialog.id}`, payload);
      setOwnerDialog(null);
    });
  };
  const initiateEdd = () => mutation.mutate(() => apiClient.post(`${base}/edd`, { siteVisitRequired, notes: eddNotes || undefined }));
  const updateEdd = (code: string, completed: boolean) => mutation.mutate(() => apiClient.put(`${base}/edd/items/${code}`, { completed, notes: eddNotes || undefined }));

  if (!Number.isFinite(id)) return <HokekaPageShell title="KYC" subtitle="Invalid merchant identifier" noCard><div /></HokekaPageShell>;

  return (
    <HokekaPageShell title={overview?.merchantName || "KYC Due Diligence"} subtitle="CDD, beneficial ownership, evidence, EDD and relationship trail" noCard>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <button onClick={() => navigate("/kyc-documents")} className="flex items-center gap-2 text-sm text-glass-muted hover:text-white"><ArrowLeft size={16} /> KYC register</button>
        <div className="flex items-center gap-2">
          <Link to={`/records/MERCHANT/${id}`} className="flex items-center gap-1 rounded border border-white/10 px-3 py-2 text-xs text-burgundy-400 hover:bg-white/5"><ExternalLink size={14} /> Record trail</Link>
          <button title="Refresh" onClick={() => refresh()} className="rounded border border-white/10 p-2 text-glass-muted hover:bg-white/5 hover:text-white"><RefreshCw size={16} /></button>
        </div>
      </div>

      <div className="mb-5 flex overflow-x-auto border-b border-white/10">
        {(["overview", "ownership", "intelligence", "edd", "documents", "network"] as Tab[]).map((value) => (
          <button key={value} onClick={() => setTab(value)} className={`whitespace-nowrap border-b-2 px-4 py-2.5 text-sm capitalize ${tab === value ? "border-burgundy-500 text-white" : "border-transparent text-glass-muted hover:text-white"}`}>{value}</button>
        ))}
      </div>

      {overviewQuery.isLoading ? <Loading /> : overviewQuery.isError || !overview ? (
        <div className="border border-red-700/30 bg-red-900/20 px-4 py-3 text-sm text-red-200">Failed to load the KYC workspace.</div>
      ) : (
        <>
          {tab === "overview" && <OverviewTab overview={overview} />}
          {tab === "ownership" && (
            <section>
              <div className="mb-4 flex items-center justify-between">
                <div><h2 className="text-base font-semibold text-white">Beneficial ownership</h2><p className="text-sm text-glass-muted">Declared total: {ownership?.totalOwnershipPercentage || 0}%</p></div>
                {canManage && <button onClick={() => openOwner("new")} className="flex items-center gap-1 rounded bg-burgundy-700 px-3 py-2 text-xs text-white hover:bg-burgundy-800"><Plus size={14} /> Add owner</button>}
              </div>
              {!ownership?.complete && <div className="mb-4 border border-amber-700/30 bg-amber-900/20 px-4 py-3 text-sm text-amber-200">Ownership declarations must total exactly 100%.</div>}
              <div className="grid gap-3 lg:grid-cols-2">
                {ownership?.owners.map((owner) => (
                  <article key={owner.id} className="rounded-lg border border-white/10 bg-[var(--surface-2)] p-4">
                    <div className="flex items-start justify-between gap-3">
                      <div><div className="flex items-center gap-2"><UserRound size={18} className="text-burgundy-400" /><h3 className="font-medium text-white">{owner.fullName}</h3></div><p className="mt-1 text-sm text-glass-muted">{owner.ownershipPercentage}% ownership · {owner.nationality}</p></div>
                      <div className="flex gap-1">{ultimateOwnerIds.has(owner.id) && <TwBadge variant="info">UBO</TwBadge>}{owner.pep && <TwBadge variant="warning">PEP</TwBadge>}{owner.sanctioned && <TwBadge variant="danger">Sanctioned</TwBadge>}</div>
                    </div>
                    <dl className="mt-3 grid grid-cols-2 gap-2 text-xs"><Meta label="Date of birth" value={owner.dateOfBirth} /><Meta label="Residence" value={owner.countryOfResidence || "Not recorded"} /><Meta label="Passport" value={owner.passportEnding ? `Ending ${owner.passportEnding}` : "Not recorded"} /><Meta label="National ID" value={owner.nationalIdEnding ? `Ending ${owner.nationalIdEnding}` : "Not recorded"} /></dl>
                    <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-white/10 pt-3">
                      <Link to={`/records/BENEFICIAL_OWNER/${owner.id}`} className="text-xs text-burgundy-400 hover:text-burgundy-300">Trace record</Link>
                      {canScreen && <button disabled={mutation.isPending} onClick={() => mutation.mutate(() => apiClient.post(`${base}/beneficial-owners/${owner.id}/screen`))} className="text-xs text-burgundy-400 hover:text-burgundy-300">Screen now</button>}
                      {canManage && <button onClick={() => openOwner(owner)} className="text-xs text-burgundy-400 hover:text-burgundy-300">Edit</button>}
                      {canManage && <button title="Delete owner" onClick={() => mutation.mutate(() => apiClient.delete(`${base}/beneficial-owners/${owner.id}`))} className="ml-auto rounded p-1 text-red-400 hover:bg-red-900/30"><Trash2 size={14} /></button>}
                    </div>
                  </article>
                ))}
                {!ownership?.owners.length && <Empty icon={<UserRound size={34} />} text="No beneficial owners have been declared." />}
              </div>
            </section>
          )}
          {tab === "intelligence" && (
            <IntelligenceTab
              checks={intelligenceQuery.data || []}
              loading={intelligenceQuery.isLoading}
              canRun={canScreen}
              pending={mutation.isPending}
              onRun={() => mutation.mutate(() => apiClient.post(`${base}/corporate-intelligence/check`))}
            />
          )}
          {tab === "edd" && (
            <section>
              <div className="mb-4 flex flex-wrap items-center justify-between gap-3"><div><h2 className="text-base font-semibold text-white">Enhanced due diligence</h2><p className="text-sm text-glass-muted">Status: {edd?.status}</p></div>{edd?.status === "NOT_STARTED" && canManage && <button onClick={initiateEdd} disabled={mutation.isPending} className="rounded bg-burgundy-700 px-3 py-2 text-xs text-white hover:bg-burgundy-800">Initiate EDD</button>}</div>
              {edd?.status === "NOT_STARTED" && canManage && <label className="mb-4 flex items-center gap-2 text-sm text-glass-muted"><input type="checkbox" checked={siteVisitRequired} onChange={(event) => setSiteVisitRequired(event.target.checked)} /> Require site visit</label>}
              {edd && edd.status !== "NOT_STARTED" && <div className="mb-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">{eddItems.filter(([code]) => code !== "SITE_VISIT" || edd.siteVisitRequired).map(([code, label, field]) => { const completed = Boolean(edd[field]); return <button key={code} disabled={!canManage || mutation.isPending} onClick={() => updateEdd(code, !completed)} className={`flex min-h-14 items-center gap-3 rounded-lg border px-3 py-2 text-left text-sm ${completed ? "border-emerald-700/40 bg-emerald-900/20 text-emerald-200" : "border-white/10 bg-[var(--surface-2)] text-white"}`}><span className={`flex h-6 w-6 shrink-0 items-center justify-center rounded border ${completed ? "border-emerald-500 bg-emerald-700" : "border-white/20"}`}>{completed && <Check size={14} />}</span>{label}</button>; })}</div>}
              {canManage && <textarea value={eddNotes} onChange={(event) => setEddNotes(event.target.value)} placeholder="Evidence note or revocation reason" maxLength={4000} className="mb-5 min-h-20 w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white placeholder:text-white/30 focus:outline-none focus:ring-1 focus:ring-burgundy-700" />}
              <h3 className="mb-2 text-sm font-semibold text-white">Evidence history</h3>
              <div className="overflow-auto border border-white/10"><table className="w-full text-left text-sm"><thead className="bg-[var(--surface-2)] text-xs uppercase text-glass-muted"><tr><th className="px-3 py-2">Item</th><th className="px-3 py-2">State</th><th className="px-3 py-2">Actor</th><th className="px-3 py-2">Time</th><th className="px-3 py-2">Notes</th></tr></thead><tbody className="divide-y divide-white/10">{edd?.events.map((event) => <tr key={event.id}><td className="px-3 py-2 text-white">{event.itemCode.replaceAll("_", " ")}</td><td className="px-3 py-2"><TwBadge variant={event.newValue ? "success" : "warning"}>{event.newValue ? "Complete" : "Open"}</TwBadge></td><td className="px-3 py-2 text-glass-muted">{event.performedBy}</td><td className="px-3 py-2 text-glass-muted">{new Date(event.occurredAt).toLocaleString()}</td><td className="px-3 py-2 text-glass-muted">{event.notes || ""}</td></tr>)}</tbody></table>{!edd?.events.length && <p className="p-4 text-sm text-glass-muted">No EDD evidence events yet.</p>}</div>
            </section>
          )}
          {tab === "documents" && <DocumentsTab documents={documentsQuery.data || []} loading={documentsQuery.isLoading} merchantId={id} />}
          {tab === "network" && <NetworkTab graph={graphQuery.data} loading={graphQuery.isLoading} />}
        </>
      )}

      {ownerDialog && <OwnerDialog form={ownerForm} setForm={setOwnerForm} editing={ownerDialog !== "new"} pending={mutation.isPending} onClose={() => setOwnerDialog(null)} onSubmit={saveOwner} />}
      <TwSnackbar open={toast.open} message={toast.message} severity={toast.severity} onClose={() => setToast((value) => ({ ...value, open: false }))} />
    </HokekaPageShell>
  );
}

function IntelligenceTab({ checks, loading, canRun, pending, onRun }: {
  checks: CorporateIntelligenceCheck[]; loading: boolean; canRun: boolean; pending: boolean; onRun: () => void;
}) {
  if (loading) return <Loading />;
  const latest = checks[0];
  return <section>
    <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
      <div>
        <h2 className="text-base font-semibold text-white">Corporate intelligence</h2>
        <p className="text-sm text-glass-muted">Registry identity, provider availability, adverse-media leads, and retained evidence</p>
      </div>
      {canRun && <button disabled={pending} onClick={onRun} className="flex items-center gap-2 rounded bg-burgundy-700 px-3 py-2 text-xs text-white hover:bg-burgundy-800 disabled:opacity-50">
        {pending ? <Loader2 size={14} className="animate-spin" /> : <Search size={14} />} Run live check
      </button>}
    </div>
    {latest && <div className="mb-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <IntelligenceMetric label="Decision" value={latest.status} tone={latest.status} />
      <IntelligenceMetric label="Registry" value={`${latest.registryStatus} (${latest.registryMatchScore})`} tone={latest.registryStatus} />
      <IntelligenceMetric label="Adverse media" value={`${latest.adverseMediaStatus} (${latest.adverseMediaArticleCount})`} tone={latest.adverseMediaStatus} />
      <IntelligenceMetric label="Risk score" value={String(latest.riskScore)} tone={latest.riskScore >= 70 ? "REVIEW" : latest.status} />
    </div>}
    <div className="space-y-4">
      {checks.map((check) => {
        const registryUnavailable = String(check.registryProvenance.unavailableReason || "");
        const mediaUnavailable = String(check.adverseMediaProvenance.unavailableReason || "");
        return <article key={check.id} className="rounded-lg border border-white/10 bg-[var(--surface-2)]">
          <div className="flex flex-wrap items-start justify-between gap-3 border-b border-white/10 px-4 py-3">
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <TwBadge variant={check.status === "CLEAR" ? "success" : check.status === "REVIEW" ? "warning" : "danger"}>{check.status}</TwBadge>
                <span className="text-sm font-medium text-white">{check.checkType.replaceAll("_", " ")}</span>
              </div>
              <p className="mt-1 text-xs text-glass-muted">{new Date(check.checkedAt).toLocaleString()} by {check.checkedBy}</p>
            </div>
            <Link to={`/records/CORPORATE_INTELLIGENCE_CHECK/${check.id}`} className="flex items-center gap-1 text-xs text-burgundy-400"><Network size={14} /> Trace evidence</Link>
          </div>
          <div className="grid gap-5 p-4 lg:grid-cols-2">
            <div>
              <div className="mb-2 flex items-center gap-2"><Building2 size={17} className="text-burgundy-400" /><h3 className="text-sm font-semibold text-white">Corporate registry</h3></div>
              <p className="text-sm text-glass-muted">{check.decisionReason}</p>
              <dl className="mt-3 grid grid-cols-2 gap-3 text-xs">
                <Meta label="Provider" value={check.registryProvider} />
                <Meta label="Status" value={check.registryStatus.replaceAll("_", " ")} />
                <Meta label="Matched entity" value={check.matchedCompanyName || "No verified match"} />
                <Meta label="Company number" value={check.matchedCompanyNumber || "Not available"} />
                <Meta label="Jurisdiction" value={check.matchedJurisdiction || "Not available"} />
                <Meta label="Registry standing" value={check.matchedCompanyStatus || "Not available"} />
              </dl>
              {registryUnavailable && <p className="mt-3 border-l-2 border-red-600 pl-3 text-xs text-red-200">{registryUnavailable}</p>}
              {check.matchedCompanyUrl && <a href={check.matchedCompanyUrl} target="_blank" rel="noreferrer" className="mt-3 inline-flex items-center gap-1 text-xs text-burgundy-400">Open registry source <ExternalLink size={13} /></a>}
            </div>
            <div>
              <div className="mb-2 flex items-center gap-2"><Newspaper size={17} className="text-burgundy-400" /><h3 className="text-sm font-semibold text-white">Adverse-media leads</h3></div>
              <p className="text-xs text-glass-muted">{check.adverseMediaProvider} returned {check.adverseMediaArticleCount} lead(s). Results are investigator leads, not an automated finding.</p>
              {mediaUnavailable && <p className="mt-3 border-l-2 border-red-600 pl-3 text-xs text-red-200">{mediaUnavailable}</p>}
              <div className="mt-3 max-h-52 space-y-2 overflow-y-auto">
                {check.adverseMediaArticles.map((article, index) => <a key={`${article.url}-${index}`} href={article.url} target="_blank" rel="noreferrer" className="block border-l-2 border-white/10 pl-3 hover:border-burgundy-600">
                  <p className="text-sm text-white">{article.title || article.url}</p>
                  <p className="mt-0.5 text-xs text-glass-muted">{[article.domain, article.sourceCountry, article.seenDate].filter(Boolean).join(" | ")}</p>
                </a>)}
                {!check.adverseMediaArticles.length && !mediaUnavailable && <p className="text-sm text-emerald-300">No leads returned for the configured query window.</p>}
              </div>
            </div>
          </div>
          <div className="border-t border-white/10 px-4 py-2 text-xs text-glass-muted">
            Evidence SHA-256: <span className="break-all font-mono text-white/70">{check.evidenceHash}</span> · Retain until {check.retainUntil}
          </div>
        </article>;
      })}
      {!checks.length && <Empty icon={<Search size={34} />} text="No corporate-intelligence evidence has been collected yet." />}
    </div>
  </section>;
}

function IntelligenceMetric({ label, value, tone }: { label: string; value: string; tone: string }) {
  const color = ["CLEAR", "MATCH"].includes(tone) ? "border-emerald-600" : ["REVIEW", "POTENTIAL_MATCH", "HITS"].includes(tone) ? "border-amber-600" : "border-red-600";
  return <div className={`border-l-2 ${color} bg-[var(--ink)] px-4 py-3`}><p className="text-xs uppercase text-glass-muted">{label}</p><p className="mt-1 text-base font-semibold text-white">{value.replaceAll("_", " ")}</p></div>;
}

function OverviewTab({ overview }: { overview: Overview }) {
  const metrics = [
    ["CDD level", overview.cdd.cddLevel], ["Risk", `${overview.cdd.riskLevel} (${overview.cdd.riskScore.toFixed(1)})`],
    ["KYC completeness", `${overview.completeness.overallPercentage.toFixed(0)}%`], ["Ownership", `${overview.ownership.totalOwnershipPercentage}%`],
    ["EDD", overview.edd.status],
  ];
  return <section><div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">{metrics.map(([label, value]) => <div key={label} className="border-l-2 border-burgundy-600 bg-[var(--surface-2)] px-4 py-3"><p className="text-xs uppercase text-glass-muted">{label}</p><p className="mt-1 text-lg font-semibold text-white">{value}</p></div>)}</div><div className="mt-6 grid gap-6 lg:grid-cols-2"><div><h2 className="mb-3 text-sm font-semibold text-white">Required evidence</h2><div className="flex flex-wrap gap-2">{overview.cdd.requiredDocuments.map((item) => <TwBadge key={item} variant="info">{item.replaceAll("_", " ")}</TwBadge>)}</div></div><div><h2 className="mb-3 text-sm font-semibold text-white">Outstanding KYC items</h2>{overview.completeness.missingItems.length ? <ul className="space-y-2 text-sm text-amber-200">{overview.completeness.missingItems.map((item) => <li key={item} className="border-l-2 border-amber-600 pl-3">{item}</li>)}</ul> : <p className="flex items-center gap-2 text-sm text-emerald-300"><ShieldCheck size={17} /> No missing items identified.</p>}</div></div></section>;
}

function DocumentsTab({ documents, loading, merchantId }: { documents: DocumentEvidence[]; loading: boolean; merchantId: number }) {
  if (loading) return <Loading />;
  return <section><div className="mb-4 flex items-center justify-between"><div><h2 className="text-base font-semibold text-white">Document evidence</h2><p className="text-sm text-glass-muted">Integrity, expiry, scan, and review state</p></div><Link to="/kyc-documents" className="text-xs text-burgundy-400">Upload or review</Link></div><div className="overflow-auto border border-white/10"><table className="w-full text-left text-sm"><thead className="bg-[var(--surface-2)] text-xs uppercase text-glass-muted"><tr><th className="px-3 py-2">Document</th><th className="px-3 py-2">Status</th><th className="px-3 py-2">Scan</th><th className="px-3 py-2">Expiry</th><th className="px-3 py-2">Reviewer</th><th className="px-3 py-2"></th></tr></thead><tbody className="divide-y divide-white/10">{documents.map((doc) => <tr key={doc.id}><td className="px-3 py-2"><p className="text-white">{doc.fileName}</p><p className="text-xs text-glass-muted">{doc.documentType.replaceAll("_", " ")}</p></td><td className="px-3 py-2"><TwBadge variant={doc.status === "VERIFIED" ? "success" : doc.status === "REJECTED" ? "danger" : "warning"}>{doc.status}</TwBadge></td><td className="px-3 py-2 text-glass-muted">{doc.malwareScanStatus || "Unknown"}</td><td className="px-3 py-2 text-glass-muted">{doc.expiryDate || "Not set"}</td><td className="px-3 py-2 text-glass-muted">{doc.verifiedBy || "Pending"}</td><td className="px-3 py-2"><div className="flex gap-2"><a title="Open" target="_blank" rel="noreferrer" href={getApiUrl(`documents/${doc.id}/file`)} className="text-burgundy-400"><ExternalLink size={15} /></a><Link title="Trace" to={`/records/MERCHANT_DOCUMENT/${doc.id}`} className="text-burgundy-400"><Network size={15} /></Link></div></td></tr>)}</tbody></table>{!documents.length && <Empty icon={<FileText size={34} />} text={`No document evidence for merchant ${merchantId}.`} />}</div></section>;
}

function NetworkTab({ graph, loading }: { graph?: CorporateGraph; loading: boolean }) {
  if (loading) return <Loading />;
  if (!graph) return <Empty icon={<Network size={34} />} text="No corporate graph is available." />;
  return <section><h2 className="mb-4 text-base font-semibold text-white">Corporate relationship trail</h2><div className="grid gap-3 lg:grid-cols-2">{graph.nodes.map((node) => { const merchantId = node.type === "MERCHANT" ? node.id.replace("MERCHANT-", "") : null; return <article key={node.id} className={`rounded-lg border p-4 ${node.root ? "border-burgundy-600 bg-burgundy-950/20" : "border-white/10 bg-[var(--surface-2)]"}`}><div className="flex items-center gap-2">{node.type === "MERCHANT" ? <Building2 size={18} /> : <UserRound size={18} />}<h3 className="font-medium text-white">{node.label}</h3>{node.root && <TwBadge variant="info">Root</TwBadge>}</div><p className="mt-2 text-xs text-glass-muted">{node.details || node.status || node.type}</p>{merchantId && <Link to={`/records/MERCHANT/${merchantId}`} className="mt-3 inline-block text-xs text-burgundy-400">Open record trail</Link>}</article>; })}</div><h3 className="mb-2 mt-6 text-sm font-semibold text-white">Relationships</h3><div className="space-y-2">{graph.edges.map((edge, index) => <div key={`${edge.source}-${edge.target}-${index}`} className="flex items-center gap-2 border-l-2 border-burgundy-700 pl-3 text-sm text-glass-muted"><span className="text-white">{edge.source}</span><span>{edge.relationship}</span><span className="text-white">{edge.target}</span></div>)}</div></section>;
}

function OwnerDialog({ form, setForm, editing, pending, onClose, onSubmit }: { form: typeof blankOwner; setForm: (value: typeof blankOwner) => void; editing: boolean; pending: boolean; onClose: () => void; onSubmit: (event: FormEvent) => void }) {
  const field = (key: keyof typeof blankOwner, label: string, type = "text") => <label className="flex flex-col gap-1 text-xs text-glass-muted">{label}<input required={!["passportNumber", "nationalId", "countryOfResidence"].includes(key)} type={type} value={form[key]} onChange={(event) => setForm({ ...form, [key]: event.target.value })} className="rounded border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white focus:outline-none focus:ring-1 focus:ring-burgundy-700" /></label>;
  return <><div className="fixed inset-0 z-50 bg-black/60" onClick={pending ? undefined : onClose} /><div className="fixed left-1/2 top-1/2 z-50 w-[min(92vw,680px)] -translate-x-1/2 -translate-y-1/2 rounded-lg border border-white/10 bg-[var(--surface-2)] shadow-2xl"><div className="flex items-center justify-between border-b border-white/10 px-5 py-4"><h2 className="font-semibold text-white">{editing ? "Edit" : "Add"} beneficial owner</h2><button title="Close" onClick={onClose} className="rounded p-1 text-glass-muted hover:bg-white/10"><X size={18} /></button></div><form onSubmit={onSubmit}><div className="grid max-h-[65vh] gap-4 overflow-y-auto p-5 sm:grid-cols-2">{field("fullName", "Full name")}{field("dateOfBirth", "Date of birth", "date")}{field("nationality", "Nationality (ISO-3)")}{field("countryOfResidence", "Country of residence (ISO-3)")}{field("ownershipPercentage", "Ownership percentage", "number")}{field("passportNumber", editing ? "New passport number (blank keeps current)" : "Passport number")}{field("nationalId", editing ? "New national ID (blank keeps current)" : "National ID")}</div><div className="flex justify-end gap-2 border-t border-white/10 px-5 py-3"><button type="button" onClick={onClose} className="rounded border border-white/10 px-4 py-2 text-xs text-white">Cancel</button><button disabled={pending} className="rounded bg-burgundy-700 px-4 py-2 text-xs text-white disabled:opacity-50">{pending ? "Saving..." : "Save owner"}</button></div></form></div></>;
}

function Meta({ label, value }: { label: string; value: string }) { return <div><dt className="text-glass-muted">{label}</dt><dd className="mt-0.5 text-white">{value}</dd></div>; }
function Loading() { return <div className="flex justify-center py-12"><Loader2 size={30} className="animate-spin text-burgundy-400" /></div>; }
function Empty({ icon, text }: { icon: React.ReactNode; text: string }) { return <div className="col-span-full flex flex-col items-center justify-center p-8 text-center text-sm text-glass-muted"><span className="mb-2 opacity-40">{icon}</span>{text}</div>; }
