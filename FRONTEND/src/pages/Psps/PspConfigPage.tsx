import { useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useMyPsp, usePsp } from "../../features/api/queries";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Loader2 } from "lucide-react";
import { apiClient } from "../../lib/apiClient";
import CompanyTab from "./tabs/CompanyTab";
import CbkReportingTab from "./tabs/CbkReportingTab";
import DirectorsTab from "./tabs/DirectorsTab";
import ShareholdersTab from "./tabs/ShareholdersTab";
import TrusteesTab from "./tabs/TrusteesTab";
import SeniorManagementTab from "./tabs/SeniorManagementTab";
import ProductsTab from "./tabs/ProductsTab";
import TrustAccountsTab from "./tabs/TrustAccountsTab";
import TariffsTab from "./tabs/TariffsTab";
import BillingTab from "./tabs/BillingTab";
import BrandingTab from "./tabs/BrandingTab";
import KycTab from "./tabs/KycTab";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import { useQueryClient } from "@tanstack/react-query";

const TAB_LABELS = [
  "Company", "CBK Reporting", "Directors", "Shareholders", "Trustees",
  "Senior Management", "Products", "Trust Accounts", "Tariffs", "Branding", "Billing", "KYC",
];

const LIST_QUERIES: Record<string, string> = {
  directors: "directors", shareholders: "shareholders", trustees: "trustees",
  "senior-management": "senior-management", products: "products",
  "trust-accounts": "trust-accounts", tariffs: "tariffs",
};

export default function PspConfigPage() {
  const { pspId } = useParams<{ pspId: string }>();
  const navigate = useNavigate();
  const [tab, setTab] = useState(0);
  const queryClient = useQueryClient();

  const selfService = !pspId;
  const numericId = Number(pspId ?? 0);
  const directPsp = usePsp(numericId);
  const myPsp = useMyPsp(selfService);
  const psp = selfService ? myPsp.data : directPsp.data;
  const isLoading = selfService ? myPsp.isLoading : directPsp.isLoading;
  const isError = selfService ? myPsp.isError : directPsp.isError;
  const effectivePspId = pspId ?? String((psp as any)?.id ?? "");

  const listKeys = Object.keys(LIST_QUERIES);
  const listIndex = tab >= 2 ? tab - 2 : -1;
  const listKey = listIndex >= 0 && listIndex < listKeys.length ? listKeys[listIndex] : null;

  const { data: listData, refetch: refetchList } = useQuery({
    queryKey: ["psp", effectivePspId, "list", listKey],
    queryFn: async () => {
      if (!listKey || !effectivePspId) return [];
      // CBK sub-resource lists live under /psps/{pspId}/cbk/{entity} (see PspListCrud + the
      // Psp*Controllers under controller/psp/cbk). Reads must match that path or every tab 404s.
      return apiClient.get<any[]>(`psps/${effectivePspId}/cbk/${LIST_QUERIES[listKey]}`);
    },
    enabled: listKey !== null && !!effectivePspId,
  });

  const onRefresh = () => {
    queryClient.invalidateQueries({ queryKey: ["psp", effectivePspId, "list"] });
    if (refetchList) refetchList();
  };

  if (!selfService && !pspId) return <div className="rounded-lg border border-red-700/30 bg-red-900/30 px-4 py-3 text-sm text-red-200">Invalid PSP ID.</div>;
  if (!isLoading && !effectivePspId) return <div className="rounded-lg border border-red-700/30 bg-red-900/30 px-4 py-3 text-sm text-red-200">Your account is not linked to an organization.</div>;

  const pspName = (psp as any)?.legalName ?? (psp as any)?.tradingName ?? (psp as any)?.name ?? "Organization";

  return (
    <HokekaPageShell title={pspName} subtitle="PSP configuration and compliance settings" noCard>
      <div className="mb-4 flex items-center gap-2">
        <button onClick={() => navigate(selfService ? "/dashboard" : "/psps")} className="flex items-center gap-1 rounded p-1 text-xs text-burgundy-400 transition-colors hover:bg-white/10">
          <ArrowLeft size={16} /> {selfService ? "Dashboard" : "Back"}
        </button>
        <h3 className="text-base font-semibold text-white">{isLoading ? "Loading..." : selfService ? "My Organization" : `Configure ${pspName}`}</h3>
      </div>

      {isError && <div className="mb-3 rounded-lg border border-amber-700/30 bg-amber-900/30 px-4 py-3 text-sm text-amber-200">Could not load PSP details. You can still manage entities below.</div>}

      {isLoading ? (
        <div className="flex justify-center py-8"><Loader2 size={28} className="animate-spin text-glass-muted" /></div>
      ) : (
        <>
          <div className="mb-4 flex gap-0 overflow-x-auto border-b border-white/10">
            {TAB_LABELS.map((label, i) => (
              <button key={label} onClick={() => setTab(i)}
                className={`whitespace-nowrap px-4 py-2.5 text-xs font-medium transition-colors ${
                  tab === i ? "border-b-2 border-burgundy-700 text-burgundy-400" : "text-glass-muted hover:text-white"
                }`}
              >
                {label}
              </button>
            ))}
          </div>

          <div className="mt-2">
            {tab === 0 && <CompanyTab pspId={effectivePspId} psp={psp} />}
            {tab === 1 && <CbkReportingTab pspId={effectivePspId} psp={psp} />}
            {tab >= 2 && listKey && (() => {
              const items = listData || [];
              const props = { pspId: effectivePspId, items, onRefresh };
              switch (listKey) {
                case "directors": return <DirectorsTab {...props} directors={items} />;
                case "shareholders": return <ShareholdersTab {...props} shareholders={items} />;
                case "trustees": return <TrusteesTab {...props} trustees={items} />;
                case "senior-management": return <SeniorManagementTab {...props} seniorMgmt={items} />;
                case "products": return <ProductsTab {...props} products={items} />;
                case "trust-accounts": return <TrustAccountsTab {...props} trustAccounts={items} />;
                case "tariffs": return <TariffsTab {...props} tariffs={items} />;
                default: return null;
              }
            })()}
            {tab === 9 && <BrandingTab pspId={effectivePspId} />}
            {tab === 10 && <BillingTab pspId={effectivePspId} />}
            {tab === 11 && <KycTab pspId={effectivePspId} psp={psp} />}
          </div>
        </>
      )}
    </HokekaPageShell>
  );
}
