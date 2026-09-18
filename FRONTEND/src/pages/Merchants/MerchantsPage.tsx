import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useAllPsps, useMerchants } from "../../features/api/queries";
import { useCreateMerchant, type CreateMerchantRequest } from "../../features/api/mutations";
import type { Merchant, Psp } from "../../types";
import { useAuth } from "../../contexts/AuthContext";
import { isPlatformAdmin } from "../EdgeNodes/edgeMeta";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import GlassCard from "../../components/Common/GlassCard";
import GlassModal from "../../components/Common/GlassModal";
import GlassButton from "../../components/Common/GlassButton";
import TwBadge from "../../components/Common/TwBadge";
import TwPagination from "../../components/Common/TwPagination";
import TwSnackbar from "../../components/Common/TwSnackbar";
import { TwInput, TwSelect } from "../../components/Common/TwInput";
import { Download, Eye, Loader2, Plus, Trash2, UserPlus } from "lucide-react";

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
  const { user } = useAuth();
  const platformAdmin = isPlatformAdmin(user?.role?.name);
  const [page, setPage] = useState({ index: 0, size: 25 });
  const [pspFilter, setPspFilter] = useState("");
  const [viewMerchant, setViewMerchant] = useState<Merchant | null>(null);
  const [addOpen, setAddOpen] = useState(false);
  const [formData, setFormData] = useState<CreateMerchantRequest>(emptyForm);
  const [snackbar, setSnackbar] = useState<{
    open: boolean;
    message: string;
    severity: "success" | "error";
  }>({ open: false, message: "", severity: "success" });

  const filterPspId = pspFilter ? Number(pspFilter) : undefined;
  const { data: merchants, isLoading, isError, error } = useMerchants({
    page: page.index,
    size: page.size,
    pspId: filterPspId,
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

  const tenantPsps = psps.filter((psp) => {
    const id = pspIdOf(psp);
    const code = psp.pspCode || "";
    return id > 0 && code !== "HOKEKA_PLATFORM";
  });

  const pspOptions = [
    { value: "0", label: "Select PSP" },
    ...tenantPsps.map((psp) => ({ value: String(pspIdOf(psp)), label: pspLabel(psp) })),
  ];

  const pspFilterOptions = [
    { value: "", label: "All PSPs" },
    ...tenantPsps.map((psp) => ({ value: String(pspIdOf(psp)), label: pspLabel(psp) })),
  ];

  const pspNameById = useMemo(() => {
    const map = new Map<number, string>();
    tenantPsps.forEach((psp) => map.set(pspIdOf(psp), pspLabel(psp)));
    return map;
  }, [tenantPsps]);

  const showPspColumn = platformAdmin || tenantPsps.length > 1;

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
      <GlassCard padding="md" glowVariant="teal" static>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-3">
          {platformAdmin && (
            <div className="min-w-[200px]">
              <TwSelect
                label="Filter by PSP"
                value={pspFilter}
                options={pspFilterOptions}
                onChange={(event) => {
                  setPspFilter(event.target.value);
                  setPage((current) => ({ ...current, index: 0 }));
                }}
              />
            </div>
          )}
          <GlassButton variant="default" size="sm" onClick={handleExportCSV} disabled={!content.length}>
            <Download size={14} /> Export CSV
          </GlassButton>
        </div>
        <button type="button" onClick={() => setAddOpen(true)} className="hokeka-btn-primary">
          <Plus size={14} /> Onboard Merchant
        </button>
      </div>

      <div className="hokeka-table-wrap" style={{ maxHeight: "calc(100vh - 320px)" }}>
          {isLoading ? (
            <div className="flex items-center justify-center py-16">
              <Loader2 size={24} className="animate-spin text-glass-muted" />
            </div>
          ) : isError ? (
            <div className="px-4 py-8 text-center text-sm text-red-400">
              Error loading merchants: {error instanceof Error ? error.message : "Unknown error"}
            </div>
          ) : (
            <table className="hokeka-table">
              <thead>
                <tr>
                  {[
                    "Merchant ID",
                    ...(showPspColumn ? ["PSP"] : []),
                    "Legal Name",
                    "Country",
                    "MCC",
                    "Risk",
                    "Score",
                    "KYC",
                    "CBK Ready",
                    "Actions",
                  ].map(
                    (heading) => (
                      <th key={heading}>{heading}</th>
                    ),
                  )}
                </tr>
              </thead>
              <tbody>
                {content.map((merchant) => (
                  <tr key={merchant.merchantId}>
                    <td className="font-mono">{merchant.merchantId}</td>
                    {showPspColumn && (
                      <td className="text-ink-muted">
                        {merchant.pspCode ||
                          (merchant.pspId ? pspNameById.get(merchant.pspId) : undefined) ||
                          "-"}
                      </td>
                    )}
                    <td>
                      <p>{merchant.legalName}</p>
                      {merchant.tradingName && (
                        <p className="mt-0.5 text-xs text-glass-muted">{merchant.tradingName}</p>
                      )}
                    </td>
                    <td className="text-ink-muted">{merchant.country || "-"}</td>
                    <td className="text-ink-muted">{merchant.mcc || "-"}</td>
                    <td className="whitespace-nowrap px-4 py-3">
                      {merchant.riskLevel ? (
                        <TwBadge variant={riskBadge(merchant.riskLevel)}>{merchant.riskLevel}</TwBadge>
                      ) : (
                        <span className="text-glass-muted">-</span>
                      )}
                    </td>
                    <td className="text-ink-muted">{merchant.riskScore ?? "-"}</td>
                    <td className="text-ink-muted">{merchant.kycStatus || "-"}</td>
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
                    <td>
                      <button
                        type="button"
                        onClick={() => setViewMerchant(merchant)}
                        className="flex items-center gap-1 text-xs text-gold transition-colors hover:underline"
                      >
                        <Eye size={14} /> View
                      </button>
                    </td>
                  </tr>
                ))}
                {!content.length && (
                  <tr>
                    <td colSpan={showPspColumn ? 10 : 9} className="px-4 py-8 text-center text-sm text-glass-muted">
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
      </GlassCard>

      <GlassModal
        open={!!viewMerchant}
        onClose={() => setViewMerchant(null)}
        title={viewMerchant?.legalName ?? "Merchant"}
        subtitle={viewMerchant ? `Merchant #${viewMerchant.merchantId}` : undefined}
        maxWidth="lg"
        headerExtra={
          viewMerchant?.riskLevel ? (
            <TwBadge variant={riskBadge(viewMerchant.riskLevel)}>{viewMerchant.riskLevel}</TwBadge>
          ) : null
        }
        bodyClassName="max-h-[65vh]"
        footer={
          viewMerchant ? (
            <>
              {viewMerchant.complianceCaseId && (
                <Link
                  to={`/records/COMPLIANCE_CASE/${viewMerchant.complianceCaseId}`}
                  onClick={() => setViewMerchant(null)}
                  className="rounded-lg border border-hairline px-4 py-1.5 text-xs text-ink hover:bg-burgundy-800"
                >
                  Open case
                </Link>
              )}
              <Link
                to={`/records/MERCHANT/${viewMerchant.merchantId}`}
                onClick={() => setViewMerchant(null)}
                className="hokeka-btn-primary"
              >
                Trace record
              </Link>
            </>
          ) : null
        }
      >
              {viewMerchant && (
              <div>
                <div className="grid gap-4 sm:grid-cols-3">
                  {showPspColumn && (
                    <Detail
                      label="PSP"
                      value={
                        viewMerchant.pspCode ||
                        (viewMerchant.pspId ? pspNameById.get(viewMerchant.pspId) : undefined)
                      }
                    />
                  )}
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
                  <div className="mt-5 border-t border-hairline pt-4">
                    <p className="hokeka-field-label">Decision evidence</p>
                    <p className="mt-1 text-sm text-ink-muted">{viewMerchant.decisionReason}</p>
                  </div>
                )}

                <div className="mt-5 border-t border-hairline pt-4">
                  <p className="mb-3 hokeka-field-label">Beneficial owners</p>
                  <div className="space-y-2">
                    {(viewMerchant.beneficialOwnerResults || []).map((owner) => (
                      <div
                        key={owner.ownerId}
                        className="flex items-center justify-between border border-hairline px-3 py-2"
                      >
                        <div>
                          <p className="text-sm text-ink">{owner.fullName}</p>
                          <p className="text-xs text-glass-muted">
                            {owner.isSanctioned ? "Sanctions match" : "No sanctions match"}
                            {owner.isPep ? " / PEP" : ""}
                          </p>
                        </div>
                        <Link
                          to={`/records/BENEFICIAL_OWNER/${owner.ownerId}`}
                          onClick={() => setViewMerchant(null)}
                          className="text-xs text-gold hover:underline"
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
              )}
      </GlassModal>

      <GlassModal
        open={addOpen}
        onClose={() => setAddOpen(false)}
        title="Onboard and Screen Merchant"
        maxWidth="xl"
        bodyClassName="max-h-[76vh] space-y-6"
        footer={
          <>
            <GlassButton variant="default" size="sm" onClick={() => setAddOpen(false)}>
              Cancel
            </GlassButton>
            <button
              type="button"
              onClick={handleAddMerchant}
              disabled={!formIsValid || createMerchant.isPending}
              className="hokeka-btn-primary disabled:opacity-45"
            >
              {createMerchant.isPending && <Loader2 size={14} className="animate-spin" />}
              Screen and Onboard
            </button>
          </>
        }
      >
                <section>
                  <h4 className="mb-3 text-sm font-semibold text-ink">Legal entity</h4>
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

                <section className="border-t border-hairline pt-5">
                  <h4 className="mb-3 text-sm font-semibold text-ink">CBK reporting source fields</h4>
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

                <section className="border-t border-hairline pt-5">
                  <div className="mb-3 flex items-center justify-between">
                    <div>
                      <h4 className="text-sm font-semibold text-ink">Beneficial owners</h4>
                      <p className={`mt-0.5 text-xs ${ownershipTotal > 100 ? "text-danger" : "text-glass-muted"}`}>
                        Declared ownership: {ownershipTotal}%
                      </p>
                    </div>
                    <GlassButton
                      variant="default"
                      size="sm"
                      onClick={() =>
                        setFormData((current) => ({
                          ...current,
                          beneficialOwners: [...current.beneficialOwners, emptyOwner()],
                        }))
                      }
                    >
                      <UserPlus size={14} /> Add owner
                    </GlassButton>
                  </div>

                  <div className="space-y-4">
                    {formData.beneficialOwners.map((owner, index) => (
                      <div key={index} className="border border-hairline p-4">
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
      </GlassModal>

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
      <p className="hokeka-field-label">{label}</p>
      <p className="mt-0.5 text-sm text-ink-muted">
        {value === undefined || value === null || value === "" ? "-" : value}
      </p>
    </div>
  );
}
