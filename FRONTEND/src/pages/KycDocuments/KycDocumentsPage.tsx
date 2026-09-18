import { useState, useRef } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "../../lib/apiClient";
import { getApiUrl } from "../../config/api";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import TwBadge from "../../components/Common/TwBadge";
import TwPagination from "../../components/Common/TwPagination";
import TwSnackbar from "../../components/Common/TwSnackbar";
import { Download, FileText, Loader2, Search, Upload, X, Eye } from "lucide-react";
import { Link } from "react-router-dom";
import { useAuth } from "../../contexts/AuthContext";

// ── Data types ──
interface MerchantsPage {
  content: Array<{ id: number; businessName: string; kycStatus: string; riskLevel: string }>;
  totalElements: number;
}

interface MerchantDocument {
  id: number; fileName?: string; name?: string;
  documentType?: string; type?: string; status?: string;
  uploadedAt?: string; createdAt?: string; url?: string; fileUrl?: string;
  downloadUrl?: string; expiryDate?: string; contentType?: string; fileSize?: number;
  sha256Hash?: string; verifiedBy?: string; verificationNotes?: string;
  verificationMethod?: string; malwareScanStatus?: string; malwareScanEngine?: string;
  malwareScannedAt?: string;
}

const kycBadge = (s: string | undefined): "success" | "warning" | "danger" | "info" | "default" => {
  if (s === "APPROVED") return "success";
  if (s === "PENDING") return "warning";
  if (s === "REJECTED") return "danger";
  if (s === "UNDER_REVIEW") return "info";
  return "default";
};

const DOCUMENT_TYPES = ["ID_DOCUMENT", "PROOF_OF_ADDRESS", "BUSINESS_REGISTRATION", "BANK_STATEMENT", "PASSPORT", "LICENSE", "UTILITY_BILL", "OTHER"];

const DOC_LABELS: Record<string, string> = {
  ID_DOCUMENT: "ID Document", PROOF_OF_ADDRESS: "Proof of Address",
  BUSINESS_REGISTRATION: "Business Registration", BANK_STATEMENT: "Bank Statement",
  PASSPORT: "Passport", LICENSE: "License", UTILITY_BILL: "Utility Bill", OTHER: "Other",
};

// ── Sub-component: Documents Dialog ──
function DocumentsDialog({ merchantId, merchantName, onClose }: { merchantId: number; merchantName: string; onClose: () => void }) {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [uploadType, setUploadType] = useState("ID_DOCUMENT");
  const [expiryDate, setExpiryDate] = useState("");
  const [reviewNotes, setReviewNotes] = useState<Record<number, string>>({});
  const [uploading, setUploading] = useState(false);
  const [verifyingId, setVerifyingId] = useState<number | null>(null);
  const [toast, setToast] = useState<{ open: boolean; severity: "success" | "error"; message: string }>({ open: false, severity: "success", message: "" });

  const { data: documents, isLoading, isError } = useQuery<MerchantDocument[]>({
    queryKey: ["merchant-documents", merchantId],
    queryFn: () => apiClient.get<MerchantDocument[]>(`merchants/${merchantId}/documents`),
    enabled: true,
  });

  const roleName = user?.role?.name?.toUpperCase();
  const canReview = ["SUPER_ADMIN", "ADMIN", "COMPLIANCE_OFFICER", "MLRO", "PSP_ADMIN"].includes(roleName || "");
  const closeUpload = () => { setUploadOpen(false); setUploadFile(null); setUploadType("ID_DOCUMENT"); setExpiryDate(""); if (fileInputRef.current) fileInputRef.current.value = ""; };

  const handleUpload = async () => {
    if (!uploadFile) { setToast({ open: true, severity: "error", message: "Please select a file." }); return; }
    if (!["application/pdf", "image/jpeg", "image/png"].includes(uploadFile.type)) {
      setToast({ open: true, severity: "error", message: "Only PDF, JPEG, and PNG documents are accepted." }); return;
    }
    if (uploadFile.size > 10 * 1024 * 1024) {
      setToast({ open: true, severity: "error", message: "Documents must be 10 MB or smaller." }); return;
    }
    setUploading(true);
    try {
      const formData = new FormData();
      formData.append("file", uploadFile);
      formData.append("type", uploadType);
      if (expiryDate) formData.append("expiryDate", expiryDate);
      const pspId = sessionStorage.getItem("_psp") ?? "0";
      const response = await fetch(getApiUrl(`merchants/${merchantId}/documents`), {
        method: "POST", credentials: "include",
        headers: { "X-PSP-ID": pspId },
        body: formData,
      });
      if (!response.ok) throw new Error(`Upload failed (HTTP ${response.status})`);
      await queryClient.invalidateQueries({ queryKey: ["merchant-documents", merchantId] });
      setToast({ open: true, severity: "success", message: "Document uploaded." });
      closeUpload();
    } catch (err) {
      setToast({ open: true, severity: "error", message: err instanceof Error ? err.message : "Upload failed." });
    } finally { setUploading(false); }
  };

  const handleVerify = async (documentId: number, approved: boolean) => {
    const notes = reviewNotes[documentId]?.trim() || "";
    if (!approved && !notes) {
      setToast({ open: true, severity: "error", message: "Enter a reason before rejecting a document." });
      return;
    }
    setVerifyingId(documentId);
    try {
      const params = new URLSearchParams({ approved: String(approved) });
      if (notes) params.set("notes", notes);
      await apiClient.put(`documents/${documentId}/verify?${params.toString()}`);
      await queryClient.invalidateQueries({ queryKey: ["merchant-documents", merchantId] });
      setToast({ open: true, severity: "success", message: approved ? "Document approved." : "Document rejected." });
    } catch { setToast({ open: true, severity: "error", message: "Verification failed." }); }
    finally { setVerifyingId(null); }
  };

  return (
    <>
      {/* Documents List Modal */}
      <div className="fixed inset-0 z-40 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="fixed left-1/2 top-1/2 z-50 w-full max-w-lg -translate-x-1/2 -translate-y-1/2">
        <div className="overflow-hidden rounded-xl border border-white/10 bg-[var(--surface-2)] shadow-2xl">
          <div className="flex items-center justify-between border-b border-white/10 px-6 py-4">
            <h3 className="text-lg font-semibold text-white">KYC Documents — {merchantName}</h3>
            <div className="flex items-center gap-2">
              <Link to={`/records/MERCHANT/${merchantId}`} className="rounded px-2 py-1 text-xs text-burgundy-400 transition-colors hover:bg-burgundy-700/20">Trace merchant</Link>
              <Link to={`/kyc-documents/${merchantId}`} className="rounded px-2 py-1 text-xs text-burgundy-400 transition-colors hover:bg-burgundy-700/20">Due diligence</Link>
              <button onClick={() => setUploadOpen(true)} className="flex items-center gap-1.5 rounded-lg bg-burgundy-700 px-3 py-1.5 text-xs text-white transition-colors hover:bg-burgundy-800">
                <Upload size={14} /> Upload
              </button>
              <button onClick={onClose} className="rounded p-1 text-glass-muted hover:bg-white/10"><X size={18} /></button>
            </div>
          </div>
          <div className="max-h-96 overflow-y-auto">
            {isLoading ? (
              <div className="flex justify-center py-8"><Loader2 size={28} className="animate-spin text-glass-muted" /></div>
            ) : isError ? (
              <div className="px-6 py-4 text-sm text-red-400">Failed to load documents.</div>
            ) : documents && documents.length > 0 ? (
              documents.map((doc, idx) => {
                const name = doc.fileName || doc.name || `Document ${doc.id}`;
                const type = doc.documentType || doc.type || "Document";
                const date = doc.uploadedAt || doc.createdAt;
                const status = (doc.status || "").toUpperCase();
                const isUnverified = status === "PENDING" || status === "" || status === "UNDER_REVIEW";
                return (
                  <div key={doc.id} className={idx > 0 ? "border-t border-white/10" : ""}>
                    <div className="flex items-center gap-3 px-6 py-3">
                      <FileText size={20} className="shrink-0 text-burgundy-400/70" />
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium text-white">{name}</p>
                        <div className="flex flex-wrap items-center gap-2 text-xs text-glass-muted">
                          <span>{DOC_LABELS[type] || type}</span>
                          {date && <span>· {new Date(date).toLocaleDateString()}</span>}
                          {doc.expiryDate && <span>Expires {new Date(`${doc.expiryDate}T00:00:00`).toLocaleDateString()}</span>}
                          {doc.fileSize != null && <span>{(doc.fileSize / 1024).toFixed(0)} KB</span>}
                          {doc.status && <TwBadge variant={kycBadge(doc.status)}>{doc.status}</TwBadge>}
                          {doc.malwareScanStatus && <TwBadge variant={doc.malwareScanStatus === "CLEAN" ? "success" : doc.malwareScanStatus === "INFECTED" ? "danger" : "warning"}>{doc.malwareScanStatus}</TwBadge>}
                        </div>
                        {doc.verifiedBy && <p className="mt-1 text-xs text-glass-muted">Reviewed by {doc.verifiedBy}{doc.verificationNotes ? `: ${doc.verificationNotes}` : ""}</p>}
                        {isUnverified && canReview && (
                          <input value={reviewNotes[doc.id] || ""} onChange={(event) => setReviewNotes((current) => ({ ...current, [doc.id]: event.target.value }))}
                            placeholder="Reviewer notes (required to reject)" maxLength={2000}
                            className="mt-2 w-full rounded border border-white/10 bg-[var(--surface-3)] px-2 py-1 text-xs text-white placeholder:text-white/30 focus:outline-none focus:ring-1 focus:ring-burgundy-700" />
                        )}
                      </div>
                      <div className="flex shrink-0 items-center gap-1">
                        <Link to={`/records/MERCHANT_DOCUMENT/${doc.id}`} className="rounded px-2 py-1 text-xs text-burgundy-400 transition-colors hover:bg-burgundy-700/20">Trace</Link>
                        <button title="Open document" onClick={() => window.open(getApiUrl(`documents/${doc.id}/file`), "_blank", "noopener,noreferrer")} className="rounded p-1.5 text-burgundy-400 transition-colors hover:bg-burgundy-700/20"><Eye size={14} /></button>
                        <a title="Download document" href={getApiUrl(`documents/${doc.id}/download`)} className="rounded p-1.5 text-burgundy-400 transition-colors hover:bg-burgundy-700/20"><Download size={14} /></a>
                        {isUnverified && canReview && (
                          <>
                            <button onClick={() => handleVerify(doc.id, true)} disabled={verifyingId === doc.id} className="flex items-center gap-0.5 rounded px-2 py-1 text-xs text-emerald-400 transition-colors hover:bg-emerald-900/30 disabled:opacity-30">Approve</button>
                            <button onClick={() => handleVerify(doc.id, false)} disabled={verifyingId === doc.id} className="flex items-center gap-0.5 rounded px-2 py-1 text-xs text-red-400 transition-colors hover:bg-red-900/30 disabled:opacity-30">Reject</button>
                          </>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })
            ) : (
              <div className="px-6 py-8 text-center text-sm text-glass-muted">
                <FileText size={40} className="mx-auto mb-2 opacity-40" />
                <p>No documents on file for this merchant.</p>
              </div>
            )}
          </div>
          <div className="flex justify-end border-t border-white/10 px-6 py-3">
            <button onClick={onClose} className="rounded-lg border border-white/10 px-4 py-1.5 text-xs text-white transition-colors hover:bg-white/5">Close</button>
          </div>
        </div>
      </div>

      {/* Upload Dialog */}
      {uploadOpen && (
        <>
          <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm" onClick={uploading ? undefined : closeUpload} />
          <div className="fixed left-1/2 top-1/2 z-50 w-full max-w-sm -translate-x-1/2 -translate-y-1/2">
            <div className="overflow-hidden rounded-xl border border-white/10 bg-[var(--surface-2)] shadow-2xl">
              <div className="flex items-center justify-between border-b border-white/10 px-6 py-4">
                <h3 className="text-lg font-semibold text-white">Upload Document</h3>
                <button onClick={closeUpload} className="rounded p-1 text-glass-muted hover:bg-white/10"><X size={18} /></button>
              </div>
              <div className="space-y-4 px-6 py-4">
                <div className="flex flex-col gap-1.5">
                  <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Document Type</label>
                  <select value={uploadType} onChange={(e) => setUploadType(e.target.value)}
                    className="w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white focus:outline-none focus:ring-1 focus:ring-burgundy-700">
                    {DOCUMENT_TYPES.map(t => <option key={t} value={t}>{(DOC_LABELS[t] || t)}</option>)}
                  </select>
                </div>
                <div className="flex flex-col gap-1.5">
                  <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Expiry Date</label>
                  <input type="date" value={expiryDate} onChange={(event) => setExpiryDate(event.target.value)}
                    className="w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white focus:outline-none focus:ring-1 focus:ring-burgundy-700" />
                </div>
                <label className="flex cursor-pointer items-center justify-center gap-2 rounded-lg border border-dashed border-burgundy-700/50 bg-[var(--surface-3)] px-4 py-6 text-sm text-burgundy-400 transition-colors hover:bg-[var(--ink)]">
                  <Upload size={18} />
                  {uploadFile ? "Change file" : "Choose file"}
                  <input ref={fileInputRef} hidden type="file" accept="application/pdf,image/jpeg,image/png,.pdf,.jpg,.jpeg,.png" onChange={(e) => setUploadFile(e.target.files?.[0] ?? null)} />
                </label>
                {uploadFile && <p className="text-xs text-glass-muted">Selected: {uploadFile.name} ({Math.round(uploadFile.size / 1024)} KB)</p>}
              </div>
              <div className="flex justify-end gap-2 border-t border-white/10 px-6 py-3">
                <button onClick={closeUpload} disabled={uploading} className="rounded-lg border border-white/10 px-4 py-1.5 text-xs text-white transition-colors hover:bg-white/5 disabled:opacity-30">Cancel</button>
                <button onClick={handleUpload} disabled={uploading || !uploadFile}
                  className="flex items-center gap-1.5 rounded-lg bg-burgundy-700 px-4 py-1.5 text-xs font-medium text-white transition-colors hover:bg-burgundy-800 disabled:opacity-30">
                  {uploading ? <Loader2 size={14} className="animate-spin" /> : null}
                  {uploading ? "Uploading..." : "Upload"}
                </button>
              </div>
            </div>
          </div>
        </>
      )}

      <TwSnackbar open={toast.open} message={toast.message} severity={toast.severity} onClose={() => setToast(t => ({ ...t, open: false }))} />
    </>
  );
}

// ── Main Page ──
export default function KycDocumentsPage() {
  const [search, setSearch] = useState("");
  const [page, setPage] = useState({ index: 0, size: 25 });
  const [selectedMerchant, setSelectedMerchant] = useState<{ id: number; name: string } | null>(null);

  const queryString = [`page=${page.index}`, `size=${page.size}`, search ? `search=${encodeURIComponent(search)}` : ""].filter(Boolean).join("&");

  const { data: merchantsPage, isLoading, isError } = useQuery<MerchantsPage>({
    queryKey: ["merchants-kyc", page.index, page.size, search],
    queryFn: () => apiClient.get<MerchantsPage>(`merchants?${queryString}`),
    placeholderData: (prev) => prev,
  });

  const merchants = merchantsPage?.content || [];
  const totalElements = merchantsPage?.totalElements || 0;
  const totalPages = Math.ceil(totalElements / page.size) || 1;

  return (
    <HokekaPageShell title="KYC" subtitle="Manage merchant KYC status and compliance documents" noCard>
      {/* Search */}
      <div className="flex justify-end pb-3">
        <div className="relative w-72">
          <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-glass-muted" />
          <input
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage({ index: 0, size: page.size }); }}
            placeholder="Search merchants..."
            className="w-full rounded-lg border border-white/10 bg-[var(--surface-3)] py-2 pl-9 pr-3 text-sm text-white placeholder:text-white/30 focus:outline-none focus:ring-1 focus:ring-burgundy-700"
          />
        </div>
      </div>

      {isError && (
        <div className="mb-3 rounded-lg border border-red-700/30 bg-red-900/30 px-4 py-3 text-sm text-red-200">Failed to load KYC data.</div>
      )}

      <div className="overflow-hidden rounded-lg border border-white/10 bg-[var(--surface-2)]">
        <div className="overflow-auto" style={{ maxHeight: "calc(100vh - 320px)" }}>
          {isLoading ? (
            <div className="flex justify-center py-12"><Loader2 size={32} className="animate-spin text-glass-muted" /></div>
          ) : (
            <table className="w-full border-collapse">
              <thead className="sticky top-0 z-10">
                <tr className="border-b border-white/10 bg-[var(--surface-2)]">
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Merchant</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">KYC Status</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Risk Level</th>
                  <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Documents</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {merchants.length > 0 ? merchants.map((m) => (
                  <tr key={m.id} className="transition-colors hover:bg-white/[0.02]">
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <FileText size={18} className="shrink-0 text-burgundy-400/70" />
                        <span className="text-sm font-medium text-white">{m.businessName}</span>
                      </div>
                    </td>
                    <td className="whitespace-nowrap px-4 py-3">
                      <TwBadge variant={kycBadge(m.kycStatus)}>{m.kycStatus || "UNKNOWN"}</TwBadge>
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm text-glass-muted">{m.riskLevel}</td>
                    <td className="whitespace-nowrap px-4 py-3">
                      <button onClick={() => setSelectedMerchant({ id: m.id, name: m.businessName })}
                        className="flex items-center gap-1 text-xs text-burgundy-400 transition-colors hover:text-burgundy-300">
                        <Eye size={14} /> View documents
                      </button>
                    </td>
                  </tr>
                )) : (
                  <tr><td colSpan={4} className="px-4 py-8 text-center text-sm text-glass-muted">No merchants found</td></tr>
                )}
              </tbody>
            </table>
          )}
        </div>
        <TwPagination
          page={page.index} totalPages={totalPages} totalCount={totalElements} rowsPerPage={page.size}
          onPageChange={(p) => setPage(prev => ({ ...prev, index: p }))}
          onRowsPerPageChange={(s) => setPage({ index: 0, size: s })}
        />
      </div>

      {selectedMerchant && (
        <DocumentsDialog
          merchantId={selectedMerchant.id}
          merchantName={selectedMerchant.name}
          onClose={() => setSelectedMerchant(null)}
        />
      )}
    </HokekaPageShell>
  );
}
