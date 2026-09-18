import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useAllPsps, useMerchants } from "../../features/api/queries";
import { useCreateMerchant, type CreateMerchantRequest } from "../../features/api/mutations";
import type { Merchant, Psp } from "../../types";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import TwBadge from "../../components/Common/TwBadge";
import TwPagination from "../../components/Common/TwPagination";
import TwSnackbar from "../../components/Common/TwSnackbar";
import { TwInput, TwSelect } from "../../components/Common/TwInput";
import { Download, Eye, Loader2, Plus, Trash2, UserPlus, X } from "lucide-react";

type Owner = CreateMerchantRequest["beneficialOwners"][number];

const emptyOwner = (): Owner => ({
  fullName: "",
  dateOfBirth: "",
  nationality: "",
  countryOfResidence: "",
  passportNumber: "",
  nationalId: "",
  ownershipPercentage: 0,
});

const emptyForm = (): CreateMerchantRequest => ({
  pspId: 0,
  legalName: "",
  tradingName: "",
  country: "KEN",
  registrationNumber: "",
  taxId: "",
  mcc: "",
  businessType: "",
  expectedMonthlyVolume: undefined,
  transactionChannel: "",
  website: "",
  contactEmail: "",
  cbkSettlementAccountNumber: "",
  cbkEconomicSectorCode: "",
  beneficialOwners: [emptyOwner()],
});

const riskBadge = (level: string | undefined): "danger" | "warning" | "success" | "default" => {
  if (level === "HIGH" || level === "CRITICAL") return "danger";
  if (level === "MEDIUM") return "warning";
  if (level === "LOW") return "success";
  return "default";
};

const formatScore = (score: number | null | undefined): string => {
  if (score === null || score === undefined || Number.isNaN(score)) return "-";
  return score.toFixed(1);
};

const pspIdOf = (psp: Psp): number => Number(psp.pspId ?? psp.id ?? 0);
const pspLabel = (psp: Psp): string =>
  psp.legalName || psp.tradingName || psp.name || psp.pspCode || `PSP #${pspIdOf(psp)}`;

export default function MerchantsPage() {
  const [page, setPage] = useState({ index: 0, size: 25 });
  const [viewMerchant, setViewMerchant] = useState<Merchant | null>(null);
  const [addOpen, setAddOpen] = useState(false);
  const [formData, setFormData] = useState<CreateMerchantRequest>(emptyForm);
  const [snackbar, setSnackbar] = useState<{
    open: boolean;
    message: string;
    severity: "success" | "error";
  }>({ open: false, message: "", severity: "success" });

  const { data: merchants, isLoading, isError, error } = useMerchants({
    page: page.index,
    size: page.size,
  });
  const { data: psps = [] } = useAllPsps();
  const createMerchant = useCreateMerchant();

  useEffect(() => {
    if (psps.length === 1 && !formData.pspId) {
      setFormData((current) => ({ ...current, pspId: pspIdOf(psps[0]) }));
    }
  }, [formData.pspId, psps]);

  const content = merchants?.content || [];
  const totalElements = merchants?.totalElements ?? 0;
  const totalPages = merchants?.totalPages ?? 1;
  const ownershipTotal = useMemo(
    () => formData.beneficialOwners.reduce((total, owner) => total + (owner.ownershipPercentage || 0), 0),
    [formData.beneficialOwners],
  );

  const pspOptions = [
    { value: "0", label: "Select PSP" },
    ...psps
      .filter((psp) => pspIdOf(psp) > 0)
      .map((psp) => ({ value: String(pspIdOf(psp)), label: pspLabel(psp) })),
  ];

  const handleExportCSV = () => {
    if (!content.length) return;
    const headers = [
      "Merchant ID",
      "Legal Name",
      "Trading Name",
      "Country",
      "MCC",
      "Risk Level",
      "Risk Score",
      "KRS",
      "CRA",
      "KYC Status",
      "CBK Sector",
      "CBK Account Configured",
    ];
    const rows = content.map((merchant) => [
      merchant.merchantId,
      (merchant.legalName || "").replace(/,/g, ";"),
      (merchant.tradingName || "").replace(/,/g, ";"),
      merchant.country || "",
      merchant.mcc || "",
      merchant.riskLevel || "",
      merchant.riskScore ?? "",
      formatScore(merchant.krs),
      formatScore(merchant.cra),
      merchant.kycStatus || "",
      merchant.cbkEconomicSectorCode || "",
      merchant.cbkSettlementAccountConfigured ? "YES" : "NO",
    ]);
    const csv = [headers, ...rows].map((row) => row.join(",")).join("\n");
    const blob = new Blob([csv], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `merchants-${new Date().toISOString().split("T")[0]}.csv`;
    anchor.click();
    URL.revokeObjectURL(url);
  };

  const updateOwner = <K extends keyof Owner>(index: number, field: K, value: Owner[K]) => {
    setFormData((current) => ({
      ...current,
      beneficialOwners: current.beneficialOwners.map((owner, ownerIndex) =>
        ownerIndex === index ? { ...owner, [field]: value } : owner,
      ),
    }));
  };

  const removeOwner = (index: number) => {
    setFormData((current) => ({
      ...current,
      beneficialOwners: current.beneficialOwners.filter((_, ownerIndex) => ownerIndex !== index),
    }));
  };

  const formIsValid =
    formData.pspId > 0 &&
    formData.legalName.trim().length > 0 &&
    formData.country.trim().length === 3 &&
    formData.registrationNumber.trim().length > 0 &&
    formData.mcc.trim().length > 0 &&
    formData.beneficialOwners.length > 0 &&
    ownershipTotal > 0 &&
    ownershipTotal <= 100 &&
    formData.beneficialOwners.every(
      (owner) =>
        owner.fullName.trim().length > 0 &&
        owner.dateOfBirth.length > 0 &&
        owner.nationality.trim().length === 3 &&
        Boolean(owner.nationalId?.trim() || owner.passportNumber?.trim()) &&
        owner.ownershipPercentage > 0 &&
        owner.ownershipPercentage <= 100,
    );

  const handleAddMerchant = async () => {
    if (!formIsValid) return;
    try {
      const response = await createMerchant.mutateAsync({
        ...formData,
        country: formData.country.toUpperCase(),
        beneficialOwners: formData.beneficialOwners.map((owner) => ({
          ...owner,
          nationality: owner.nationality.toUpperCase(),
          countryOfResidence: owner.countryOfResidence?.toUpperCase(),
        })),
      });
      setAddOpen(false);
      setFormData(emptyForm());
      const decision = (response as { decision?: string })?.decision;
      setSnackbar({
        open: true,
        message: decision ? `Merchant screening completed with decision ${decision}.` : "Merchant onboarded.",
        severity: "success",
      });
    } catch (caught: unknown) {
      const message = caught instanceof Error ? caught.message : "Failed to onboard merchant.";
      setSnackbar({ open: true, message, severity: "error" });
    }
  };

  return (
    <HokekaPageShell title="Merchants" subtitle="Onboard, screen, and monitor merchant risk profiles" noCard>
      <div className="flex items-center justify-between pb-3">
        <button
          onClick={handleExportCSV}
          disabled={!content.length}
          className="flex items-center gap-1.5 rounded-lg border border-white/10 px-3 py-1.5 text-xs text-glass-muted transition-colors hover:bg-white/5 disabled:cursor-not-allowed disabled:opacity-30"
        >
          <Download size={14} /> Export CSV
        </button>
        <button
          onClick={() => setAddOpen(true)}
          className="flex items-center gap-1.5 rounded-lg bg-burgundy-700 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-burgundy-800"
        >
          <Plus size={14} /> Onboard Merchant
        </button>
      </div>

      <div className="overflow-hidden rounded-lg border border-white/10 bg-[var(--surface-2)]">
        <div className="overflow-auto" style={{ maxHeight: "calc(100vh - 320px)" }}>
          {isLoading ? (
            <div className="flex items-center justify-center py-16">
              <Loader2 size={24} className="animate-spin text-glass-muted" />
            </div>
          ) : isError ? (
            <div className="px-4 py-8 text-center text-sm text-red-400">
              Error loading merchants: {error instanceof Error ? error.message : "Unknown error"}
            </div>
          ) : (
            <table className="w-full border-collapse">
              <thead className="sticky top-0 z-10">
                <tr className="border-b border-white/10 bg-[var(--surface-2)]">
                  {["Merchant ID", "Legal Name", "Country", "MCC", "Risk", "Score", "KYC", "CBK Ready", "Actions"].map(
                    (heading) => (
                      <th
                        key={heading}
                        className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted"
                      >
                        {heading}
                      </th>
                    ),
                  )}
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {content.map((merchant) => (
                  <tr key={merchant.merchantId} className="transition-colors hover:bg-white/[0.02]">
                    <td className="whitespace-nowrap px-4 py-3 font-mono text-sm text-white">
                      {merchant.merchantId}
                    </td>
                    <td className="px-4 py-3 text-sm text-white">
                      <p>{merchant.legalName}</p>
                      {merchant.tradingName && (
                        <p className="mt-0.5 text-xs text-glass-muted">{merchant.tradingName}</p>
                      )}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm text-white/80">{merchant.country || "-"}</td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm text-white/80">{merchant.mcc || "-"}</td>
                    <td className="whitespace-nowrap px-4 py-3">
                      {merchant.riskLevel ? (
                        <TwBadge variant={riskBadge(merchant.riskLevel)}>{merchant.riskLevel}</TwBadge>
                      ) : (
                        <span className="text-glass-muted">-</span>
                      )}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm text-white/80">
                      {merchant.riskScore ?? "-"}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm text-white/80">
                      {merchant.kycStatus || "-"}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3">
                      <TwBadge
                        variant={
                          merchant.cbkSettlementAccountConfigured && merchant.cbkEconomicSectorCode
                            ? "success"
                            : "warning"
                        }
                      >
                        {merchant.cbkSettlementAccountConfigured && merchant.cbkEconomicSectorCode
                          ? "Configured"
                          : "Incomplete"}
                      </TwBadge>
                    </td>
                    <td className="whitespace-nowrap px-4 py-3">
                      <button
                        onClick={() => setViewMerchant(merchant)}
                        className="flex items-center gap-1 text-xs text-burgundy-400 transition-colors hover:text-burgundy-300"
                      >
                        <Eye size={14} /> View
                      </button>
                    </td>
                  </tr>
                ))}
                {!content.length && (
                  <tr>
                    <td colSpan={9} className="px-4 py-8 text-center text-sm text-glass-muted">
                      No merchants found
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          )}
        </div>
        <TwPagination
          page={page.index}
          totalPages={totalPages}
          totalCount={totalElements}
          rowsPerPage={page.size}
          onPageChange={(nextPage) => setPage((current) => ({ ...current, index: nextPage }))}
          onRowsPerPageChange={(size) => setPage({ index: 0, size })}
        />
      </div>

      {viewMerchant && (
        <>
          <div className="fixed inset-0 z-40 bg-black/60 backdrop-blur-sm" onClick={() => setViewMerchant(null)} />
          <div className="fixed left-1/2 top-1/2 z-50 max-h-[90vh] w-[calc(100%-2rem)] max-w-2xl -translate-x-1/2 -translate-y-1/2">
            <div className="overflow-hidden rounded-lg border border-white/10 bg-[var(--surface-2)] shadow-2xl">
              <div className="flex items-start justify-between border-b border-white/10 px-6 py-4">
                <div>
                  <h3 className="text-lg font-semibold text-white">{viewMerchant.legalName}</h3>
                  <p className="mt-0.5 font-mono text-xs text-glass-muted">Merchant #{viewMerchant.merchantId}</p>
                </div>
                <div className="flex items-center gap-2">
                  {viewMerchant.riskLevel && (
                    <TwBadge variant={riskBadge(viewMerchant.riskLevel)}>{viewMerchant.riskLevel}</TwBadge>
                  )}
                  <button
                    title="Close"
                    onClick={() => setViewMerchant(null)}
                    className="rounded p-1 text-glass-muted transition-colors hover:bg-white/10"
                  >
                    <X size={18} />
                  </button>
                </div>
              </div>
              <div className="max-h-[65vh] overflow-y-auto px-6 py-4">
                <div className="grid gap-4 sm:grid-cols-3">
                  <Detail label="Trading name" value={viewMerchant.tradingName} />
                  <Detail label="Country" value={viewMerchant.country} />
                  <Detail label="MCC" value={viewMerchant.mcc} />
                  <Detail label="Business type" value={viewMerchant.businessType} />
                  <Detail label="KYC status" value={viewMerchant.kycStatus} />
                  <Detail label="Contract status" value={viewMerchant.contractStatus} />
                  <Detail label="Risk score" value={viewMerchant.riskScore} />
                  <Detail label="KRS" value={formatScore(viewMerchant.krs)} />
                  <Detail label="CRA" value={formatScore(viewMerchant.cra)} />
                  <Detail label="CBK sector" value={viewMerchant.cbkEconomicSectorCode} />
                  <Detail
                    label="CBK settlement account"
                    value={viewMerchant.cbkSettlementAccountConfigured ? "Configured" : "Not configured"}
                  />
                  <Detail label="Screening decision" value={viewMerchant.decision} />
                </div>

                {viewMerchant.decisionReason && (
                  <div className="mt-5 border-t border-white/10 pt-4">
                    <p className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">
                      Decision evidence
                    </p>
                    <p className="mt-1 text-sm text-white/80">{viewMerchant.decisionReason}</p>
                  </div>
                )}

                <div className="mt-5 border-t border-white/10 pt-4">
                  <p className="mb-3 text-[11px] font-semibold uppercase tracking-wider text-glass-muted">
                    Beneficial owners
                  </p>
                  <div className="space-y-2">
                    {(viewMerchant.beneficialOwnerResults || []).map((owner) => (
                      <div
                        key={owner.ownerId}
                        className="flex items-center justify-between border border-white/10 px-3 py-2"
                      >
                        <div>
                          <p className="text-sm text-white">{owner.fullName}</p>
                          <p className="text-xs text-glass-muted">
                            {owner.isSanctioned ? "Sanctions match" : "No sanctions match"}
                            {owner.isPep ? " / PEP" : ""}
                          </p>
                        </div>
                        <Link
                          to={`/records/BENEFICIAL_OWNER/${owner.ownerId}`}
                          onClick={() => setViewMerchant(null)}
                          className="text-xs text-burgundy-400 hover:text-burgundy-300"
                        >
                          Trace
                        </Link>
                      </div>
                    ))}
                    {!viewMerchant.beneficialOwnerResults?.length && (
                      <p className="text-sm text-glass-muted">No beneficial-owner evidence is attached.</p>
                    )}
                  </div>
                </div>
              </div>
              <div className="flex justify-end gap-2 border-t border-white/10 px-6 py-3">
                {viewMerchant.complianceCaseId && (
                  <Link
                    to={`/records/COMPLIANCE_CASE/${viewMerchant.complianceCaseId}`}
                    onClick={() => setViewMerchant(null)}
                    className="rounded-lg border border-white/10 px-4 py-1.5 text-xs text-white hover:bg-white/5"
                  >
                    Open case
                  </Link>
                )}
                <Link
                  to={`/records/MERCHANT/${viewMerchant.merchantId}`}
                  onClick={() => setViewMerchant(null)}
                  className="rounded-lg border border-gold/50 px-4 py-1.5 text-xs text-gold hover:bg-gold hover:text-black"
                >
                  Trace record
                </Link>
              </div>
            </div>
          </div>
        </>
      )}

      {addOpen && (
        <>
          <div className="fixed inset-0 z-40 bg-black/60 backdrop-blur-sm" onClick={() => setAddOpen(false)} />
          <div className="fixed left-1/2 top-1/2 z-50 max-h-[94vh] w-[calc(100%-2rem)] max-w-4xl -translate-x-1/2 -translate-y-1/2">
            <div className="overflow-hidden rounded-lg border border-white/10 bg-[var(--surface-2)] shadow-2xl">
              <div className="flex items-center justify-between border-b border-white/10 px-6 py-4">
                <h3 className="text-lg font-semibold text-white">Onboard and Screen Merchant</h3>
                <button
                  title="Close"
                  onClick={() => setAddOpen(false)}
                  className="rounded p-1 text-glass-muted transition-colors hover:bg-white/10"
                >
                  <X size={18} />
                </button>
              </div>

              <div className="max-h-[76vh] space-y-6 overflow-y-auto px-6 py-5">
                <section>
                  <h4 className="mb-3 text-sm font-semibold text-white">Legal entity</h4>
                  <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                    <TwSelect
                      label="PSP"
                      value={String(formData.pspId)}
                      options={pspOptions}
                      onChange={(event) =>
                        setFormData((current) => ({ ...current, pspId: Number(event.target.value) }))
                      }
                    />
                    <TwInput
                      label="Legal name"
                      value={formData.legalName}
                      onChange={(event) =>
                        setFormData((current) => ({ ...current, legalName: event.target.value }))
                      }
                    />
                    <TwInput
                      label="Trading name"
                      value={formData.tradingName}
                      onChange={(event) =>
                        setFormData((current) => ({ ...current, tradingName: event.target.value }))
                      }
                    />
                    <TwInput
                      label="Registration number"
                      value={formData.registrationNumber}
                      onChange={(event) =>
                        setFormData((current) => ({ ...current, registrationNumber: event.target.value }))
                      }
                    />
                    <TwInput
                      label="Tax ID"
                      value={formData.taxId}
                      onChange={(event) => setFormData((current) => ({ ...current, taxId: event.target.value }))}
                    />
                    <TwInput
                      label="Country (ISO-3)"
                      maxLength={3}
                      value={formData.country}
                      onChange={(event) =>
                        setFormData((current) => ({ ...current, country: event.target.value.toUpperCase() }))
                      }
                    />
                    <TwInput
                      label="MCC"
                      maxLength={10}
                      value={formData.mcc}
                      onChange={(event) => setFormData((current) => ({ ...current, mcc: event.target.value }))}
                    />
                    <TwSelect
                      label="Business type"
                      value={formData.businessType}
                      options={[
                        { value: "", label: "Select type" },
                        { value: "CORPORATION", label: "Corporation" },
                        { value: "LLC", label: "LLC" },
                        { value: "PARTNERSHIP", label: "Partnership" },
                        { value: "SOLE_PROPRIETOR", label: "Sole proprietor" },
                      ]}
                      onChange={(event) =>
                        setFormData((current) => ({ ...current, businessType: event.target.value }))
                      }
                    />
                    <TwSelect
                      label="Transaction channel"
                      value={formData.transactionChannel}
                      options={[
                        { value: "", label: "Select channel" },
                        { value: "ONLINE", label: "Online" },
                        { value: "IN_STORE", label: "In store" },
                        { value: "MOBILE", label: "Mobile" },
                      ]}
                      onChange={(event) =>
                        setFormData((current) => ({ ...current, transactionChannel: event.target.value }))
                      }
                    />
                    <TwInput
                      label="Contact email"
                      type="email"
                      value={formData.contactEmail}
                      onChange={(event) =>
                        setFormData((current) => ({ ...current, contactEmail: event.target.value }))
                      }
                    />
                    <TwInput
                      label="Website"
                      type="url"
                      value={formData.website}
                      onChange={(event) => setFormData((current) => ({ ...current, website: event.target.value }))}
                    />
                    <TwInput
                      label="Expected monthly volume (cents)"
                      type="number"
                      min={0}
                      value={formData.expectedMonthlyVolume ?? ""}
                      onChange={(event) =>
                        setFormData((current) => ({
                          ...current,
                          expectedMonthlyVolume:
                            event.target.value === "" ? undefined : Number(event.target.value),
                        }))
                      }
                    />
                  </div>
                </section>

                <section className="border-t border-white/10 pt-5">
                  <h4 className="mb-3 text-sm font-semibold text-white">CBK reporting source fields</h4>
                  <div className="grid gap-3 sm:grid-cols-2">
                    <TwInput
                      label="Settlement account number"
                      type="password"
                      autoComplete="off"
                      value={formData.cbkSettlementAccountNumber}
                      onChange={(event) =>
                        setFormData((current) => ({
                          ...current,
                          cbkSettlementAccountNumber: event.target.value,
                        }))
                      }
                    />
                    <TwInput
                      label="Economic sector code"
                      value={formData.cbkEconomicSectorCode}
                      onChange={(event) =>
                        setFormData((current) => ({ ...current, cbkEconomicSectorCode: event.target.value }))
                      }
                    />
                  </div>
                </section>

                <section className="border-t border-white/10 pt-5">
                  <div className="mb-3 flex items-center justify-between">
                    <div>
                      <h4 className="text-sm font-semibold text-white">Beneficial owners</h4>
                      <p className={`mt-0.5 text-xs ${ownershipTotal > 100 ? "text-red-400" : "text-glass-muted"}`}>
                        Declared ownership: {ownershipTotal}%
                      </p>
                    </div>
                    <button
                      onClick={() =>
                        setFormData((current) => ({
                          ...current,
                          beneficialOwners: [...current.beneficialOwners, emptyOwner()],
                        }))
                      }
                      className="flex items-center gap-1.5 rounded-lg border border-white/10 px-3 py-1.5 text-xs text-white hover:bg-white/5"
                    >
                      <UserPlus size={14} /> Add owner
                    </button>
                  </div>

                  <div className="space-y-4">
                    {formData.beneficialOwners.map((owner, index) => (
                      <div key={index} className="border border-white/10 p-4">
                        <div className="mb-3 flex items-center justify-between">
                          <p className="text-xs font-semibold uppercase tracking-wider text-glass-muted">
                            Owner {index + 1}
                          </p>
                          <button
                            title="Remove owner"
                            disabled={formData.beneficialOwners.length === 1}
                            onClick={() => removeOwner(index)}
                            className="rounded p-1 text-red-400 hover:bg-red-500/10 disabled:cursor-not-allowed disabled:opacity-30"
                          >
                            <Trash2 size={15} />
                          </button>
                        </div>
                        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                          <TwInput
                            label="Full legal name"
                            value={owner.fullName}
                            onChange={(event) => updateOwner(index, "fullName", event.target.value)}
                          />
                          <TwInput
                            label="Date of birth"
                            type="date"
                            max={new Date().toISOString().split("T")[0]}
                            value={owner.dateOfBirth}
                            onChange={(event) => updateOwner(index, "dateOfBirth", event.target.value)}
                          />
                          <TwInput
                            label="Nationality (ISO-3)"
                            maxLength={3}
                            value={owner.nationality}
                            onChange={(event) =>
                              updateOwner(index, "nationality", event.target.value.toUpperCase())
                            }
                          />
                          <TwInput
                            label="Residence (ISO-3)"
                            maxLength={3}
                            value={owner.countryOfResidence}
                            onChange={(event) =>
                              updateOwner(index, "countryOfResidence", event.target.value.toUpperCase())
                            }
                          />
                          <TwInput
                            label="National ID"
                            value={owner.nationalId}
                            onChange={(event) => updateOwner(index, "nationalId", event.target.value)}
                          />
                          <TwInput
                            label="Passport number"
                            value={owner.passportNumber}
                            onChange={(event) => updateOwner(index, "passportNumber", event.target.value)}
                          />
                          <TwInput
                            label="Ownership %"
                            type="number"
                            min={1}
                            max={100}
                            value={owner.ownershipPercentage || ""}
                            onChange={(event) =>
                              updateOwner(index, "ownershipPercentage", Number(event.target.value))
                            }
                          />
                        </div>
                      </div>
                    ))}
                  </div>
                </section>
              </div>

              <div className="flex justify-end gap-2 border-t border-white/10 px-6 py-3">
                <button
                  onClick={() => setAddOpen(false)}
                  className="rounded-lg border border-white/10 px-4 py-1.5 text-xs text-white hover:bg-white/5"
                >
                  Cancel
                </button>
                <button
                  onClick={handleAddMerchant}
                  disabled={!formIsValid || createMerchant.isPending}
                  className="flex items-center gap-1.5 rounded-lg bg-burgundy-700 px-4 py-1.5 text-xs font-medium text-white hover:bg-burgundy-800 disabled:cursor-not-allowed disabled:opacity-30"
                >
                  {createMerchant.isPending && <Loader2 size={14} className="animate-spin" />}
                  Screen and Onboard
                </button>
              </div>
            </div>
          </div>
        </>
      )}

      <TwSnackbar
        open={snackbar.open}
        message={snackbar.message}
        severity={snackbar.severity}
        onClose={() => setSnackbar((current) => ({ ...current, open: false }))}
      />
    </HokekaPageShell>
  );
}

function Detail({ label, value }: { label: string; value: string | number | undefined | null }) {
  return (
    <div>
      <p className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">{label}</p>
      <p className="mt-0.5 text-sm text-white/80">{value === undefined || value === null || value === "" ? "-" : value}</p>
    </div>
  );
}
