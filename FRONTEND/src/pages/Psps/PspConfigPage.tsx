import { useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import {
  Box,
  Typography,
  Tabs,
  Tab,
  CircularProgress,
  Alert,
  Button,
  Breadcrumbs,
  Link,
} from "@mui/material";
import { ArrowBack as BackIcon } from "@mui/icons-material";
import { usePsp } from "../../features/api/queries";
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
import HokekaPageShell from "../../components/Layout/HokekaPageShell";

const ACCENT = "#8B4049";

const TAB_LABELS = [
  "Company",
  "CBK Reporting",
  "Directors",
  "Shareholders",
  "Trustees",
  "Senior Management",
  "Products",
  "Trust Accounts",
  "Tariffs",
  "Billing",
];

export default function PspConfigPage() {
  const { pspId } = useParams<{ pspId: string }>();
  const navigate = useNavigate();
  const [tab, setTab] = useState(0);

  const numericId = Number(pspId ?? 0);
  const { data: psp, isLoading, isError } = usePsp(numericId);

  if (!pspId) return <Alert severity="error">Invalid PSP ID.</Alert>;

  const pspName =
    (psp as any)?.legalName ??
    (psp as any)?.tradingName ??
    (psp as any)?.name ??
    `PSP ${pspId}`;

  return (
    <HokekaPageShell title={pspName} subtitle="PSP configuration and compliance settings" noCard>
    <Box>
      {/* Breadcrumb */}
      <Breadcrumbs sx={{ mb: 1 }}>
        <Link
          underline="hover"
          sx={{ cursor: "pointer", color: ACCENT }}
          onClick={() => navigate("/psps")}
        >
          PSPs
        </Link>
        <Typography color="text.primary">{pspName}</Typography>
      </Breadcrumbs>

      <Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 2 }}>
        <Button
          size="small"
          startIcon={<BackIcon />}
          onClick={() => navigate("/psps")}
          sx={{ color: ACCENT, textTransform: "none" }}
        >
          Back
        </Button>
        <Typography variant="h6" sx={{ fontWeight: 600 }}>
          {isLoading ? "Loading…" : `Configure ${pspName}`}
        </Typography>
      </Box>

      {isError && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          Could not load PSP details. You can still manage child entities below.
        </Alert>
      )}

      {isLoading ? (
        <Box sx={{ display: "flex", justifyContent: "center", py: 6 }}>
          <CircularProgress sx={{ color: ACCENT }} />
        </Box>
      ) : (
        <>
          <Tabs
            value={tab}
            onChange={(_, v) => setTab(v)}
            variant="scrollable"
            scrollButtons="auto"
            sx={{
              mb: 3,
              borderBottom: "1px solid rgba(0,0,0,0.1)",
              "& .MuiTab-root": {
                color: "text.secondary",
                textTransform: "none",
                fontSize: "0.85rem",
                "&.Mui-selected": { color: ACCENT },
              },
              "& .MuiTabs-indicator": { backgroundColor: ACCENT },
            }}
          >
            {TAB_LABELS.map((label) => (
              <Tab key={label} label={label} />
            ))}
          </Tabs>

          <Box sx={{ mt: 1 }}>
            {tab === 0 && <CompanyTab pspId={pspId} psp={psp} />}
            {tab === 1 && <CbkReportingTab pspId={pspId} psp={psp} />}
            {tab === 2 && <DirectorsTab pspId={pspId} />}
            {tab === 3 && <ShareholdersTab pspId={pspId} />}
            {tab === 4 && <TrusteesTab pspId={pspId} />}
            {tab === 5 && <SeniorManagementTab pspId={pspId} />}
            {tab === 6 && <ProductsTab pspId={pspId} />}
            {tab === 7 && <TrustAccountsTab pspId={pspId} />}
            {tab === 8 && <TariffsTab pspId={pspId} />}
            {tab === 9 && <BillingTab pspId={pspId} />}
          </Box>
        </>
      )}
    </Box>
    </HokekaPageShell>
  );
}
