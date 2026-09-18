import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Building2, FileKey2, KeyRound, Plus, RefreshCw, Send, ShieldCheck, WalletCards } from "lucide-react";
import {
  Alert, Box, Button, Checkbox, Chip, Dialog, DialogActions, DialogContent, DialogTitle,
  FormControlLabel, MenuItem, Paper, Snackbar, Tab, Table, TableBody, TableCell,
  TableContainer, TableHead, TablePagination, TableRow, Tabs, TextField, Typography,
} from "@mui/material";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import {
  useCryptoTransactions, useCryptoWalletProfiles, useCustomer360, useMultiAssetCustomers,
  useRegulatorGrants, useTravelRulePolicies, useTravelRuleTransfers, useVaspDirectory, useVaspScreeningHistory,
  useWalletScreeningHistory,
} from "../../features/api/queries";
import {
  useCreateRegulatorGrant, usePrepareTravelRuleTransfer, useRegisterCryptoWallet,
  useRevokeRegulatorGrant, useSaveTravelRulePolicy, useSaveVasp, useScreenCryptoWallet, useScreenVasp,
  useTransmitTravelRule, useVerifyTravelRuleIdentity,
} from "../../features/api/mutations";
import type { TravelRulePolicy, TravelRuleTransfer, VaspEntry } from "../../types/virtualAssets";

type DialogName = "wallet" | "vasp" | "transfer" | "verify" | "policy" | "grant" | null;

const walletInitial = { customerId: "", assetAccountId: "", vaspId: "", network: "", addressLabel: "", ownershipType: "CUSTOMER", screeningIntervalHours: "24" };
const vaspInitial = {
  id: "", legalName: "", tradingName: "", jurisdiction: "KE", regulatorName: "", licenceNumber: "",
  licenceStatus: "UNKNOWN", licenceValidUntil: "", registrationNumber: "", website: "",
  travelRuleProtocols: "", sanctionsStatus: "PENDING", beneficialOwnership: "[]", walletClusters: "[]",
  riskScore: "0", transferDecision: "REVIEW", reviewNotes: "", nextReviewAt: "",
};
const transferInitial = {
  transactionId: "", jurisdiction: "KE", originatorVaspId: "", beneficiaryVaspId: "", protocol: "",
  originatorName: "", originatorAccount: "", originatorAddress: "", originatorCountry: "KE",
  beneficiaryName: "", beneficiaryAccount: "", beneficiaryVasp: "",
};
const policyInitial = {
  id: "", jurisdiction: "KE", policyCode: "", thresholdUsd: "0", appliesToAllTransfers: true,
  requiredFields: "originatorName,originatorAccount,originatorAddress,originatorCountry,beneficiaryName,beneficiaryAccount,beneficiaryVasp",
  verifyOriginator: true, verifyBeneficiary: true, acceptedProtocols: "TRISA,OPENVASP,SYGNA,NOTABENE,CUSTOM_API",
  retentionYears: "7", effectiveFrom: "", effectiveUntil: "", enabled: true, legalReference: "",
};
const grantInitial = { regulatorName: "", jurisdiction: "KE", scopes: "TRANSACTIONS_READ", allowedIpAddresses: "", expiresAt: "" };

function displayDate(value?: string) { return value ? new Date(value).toLocaleString() : "-"; }
function csv(value: string) { return value.split(",").map((item) => item.trim()).filter(Boolean); }
function mutationError(value: unknown) { return (value as { message?: string })?.message || "The request could not be completed."; }
function riskColor(score?: number): "default" | "warning" | "error" | "success" {
  if (score === undefined) return "default";
  if (score >= 70) return "error";
  if (score >= 40) return "warning";
  return "success";
}

export default function WalletIntelligencePage() {
  const [tab, setTab] = useState(0);
  const [dialog, setDialog] = useState<DialogName>(null);
  const [walletPage, setWalletPage] = useState(0);
  const [screeningPage, setScreeningPage] = useState(0);
  const [transferPage, setTransferPage] = useState(0);
  const [selectedWalletId, setSelectedWalletId] = useState<number>();
  const [selectedVaspId, setSelectedVaspId] = useState<number>();
  const [selectedTransfer, setSelectedTransfer] = useState<TravelRuleTransfer | null>(null);
  const [verifyParty, setVerifyParty] = useState("ORIGINATOR");
  const [verifyEvidence, setVerifyEvidence] = useState("");
  const [verificationPassed, setVerificationPassed] = useState(true);
  const [walletForm, setWalletForm] = useState(walletInitial);
  const [vaspForm, setVaspForm] = useState(vaspInitial);
  const [transferForm, setTransferForm] = useState(transferInitial);
  const [policyForm, setPolicyForm] = useState(policyInitial);
  const [grantForm, setGrantForm] = useState(grantInitial);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [issuedAccessKey, setIssuedAccessKey] = useState("");

  const wallets = useCryptoWalletProfiles(walletPage, 25);
  const screenings = useWalletScreeningHistory(selectedWalletId, screeningPage, 25);
  const vasps = useVaspDirectory("", 0, 100);
  const vaspScreenings = useVaspScreeningHistory(selectedVaspId, 0, 50);
  const transactions = useCryptoTransactions(0, 100);
  const transfers = useTravelRuleTransfers(transferPage, 25);
  const policies = useTravelRulePolicies();
  const grants = useRegulatorGrants();
  const customers = useMultiAssetCustomers("", 0, 100);
  const selectedCustomerId = walletForm.customerId ? Number(walletForm.customerId) : null;
  const customer360 = useCustomer360(selectedCustomerId);
  const eligibleAccounts = useMemo(() => customer360.data?.accounts.filter((account) =>
    account.assetClass === "CRYPTO" || account.accountType === "CRYPTO_WALLET" || account.accountType === "EXCHANGE_ACCOUNT") ?? [], [customer360.data]);

  const saveVasp = useSaveVasp();
  const screenVasp = useScreenVasp();
  const registerWallet = useRegisterCryptoWallet();
  const screenWallet = useScreenCryptoWallet();
  const savePolicy = useSaveTravelRulePolicy();
  const prepareTransfer = usePrepareTravelRuleTransfer();
  const verifyIdentity = useVerifyTravelRuleIdentity();
  const transmit = useTransmitTravelRule();
  const createGrant = useCreateRegulatorGrant();
  const revokeGrant = useRevokeRegulatorGrant();

  const closeDialog = () => setDialog(null);
  const success = (message: string) => { setNotice(message); closeDialog(); };

  const submitWallet = () => registerWallet.mutate({
    assetAccountId: Number(walletForm.assetAccountId), vaspId: walletForm.vaspId ? Number(walletForm.vaspId) : undefined,
    network: walletForm.network.trim(), addressLabel: walletForm.addressLabel.trim() || undefined,
    ownershipType: walletForm.ownershipType.trim(), screeningIntervalHours: Number(walletForm.screeningIntervalHours),
  }, { onSuccess: (result) => { setWalletForm(walletInitial); success(`Wallet registered and onboarding screening completed with status ${result.screeningStatus}.`); }, onError: (value) => setError(mutationError(value)) });

  const submitVasp = () => {
    try {
      const body = {
        legalName: vaspForm.legalName.trim(), tradingName: vaspForm.tradingName.trim() || undefined,
        jurisdiction: vaspForm.jurisdiction.trim().toUpperCase(), regulatorName: vaspForm.regulatorName.trim() || undefined,
        licenceNumber: vaspForm.licenceNumber.trim() || undefined, licenceStatus: vaspForm.licenceStatus,
        licenceValidUntil: vaspForm.licenceValidUntil || undefined, registrationNumber: vaspForm.registrationNumber.trim() || undefined,
        website: vaspForm.website.trim() || undefined, travelRuleProtocols: csv(vaspForm.travelRuleProtocols),
        beneficialOwnership: JSON.parse(vaspForm.beneficialOwnership),
        walletClusters: JSON.parse(vaspForm.walletClusters), riskScore: Number(vaspForm.riskScore),
        transferDecision: vaspForm.transferDecision, reviewNotes: vaspForm.reviewNotes.trim() || undefined,
        nextReviewAt: vaspForm.nextReviewAt || undefined,
      };
      saveVasp.mutate({ id: vaspForm.id ? Number(vaspForm.id) : undefined, body }, {
        onSuccess: () => { setVaspForm(vaspInitial); success("VASP due-diligence record saved."); }, onError: (value) => setError(mutationError(value)),
      });
    } catch { setError("Beneficial ownership and wallet clusters must be valid JSON arrays."); }
  };

  const editVasp = (item: VaspEntry) => {
    setVaspForm({
      id: String(item.id), legalName: item.legalName, tradingName: item.tradingName ?? "", jurisdiction: item.jurisdiction,
      regulatorName: item.regulatorName ?? "", licenceNumber: item.licenceNumber ?? "", licenceStatus: item.licenceStatus,
      licenceValidUntil: item.licenceValidUntil ?? "", registrationNumber: item.registrationNumber ?? "", website: item.website ?? "",
      travelRuleProtocols: item.travelRuleProtocols.join(","), sanctionsStatus: item.sanctionsStatus,
      beneficialOwnership: JSON.stringify(item.beneficialOwnership, null, 2), walletClusters: JSON.stringify(item.walletClusters, null, 2),
      riskScore: String(item.riskScore), transferDecision: item.transferDecision, reviewNotes: item.reviewNotes ?? "",
      nextReviewAt: item.nextReviewAt?.slice(0, 16) ?? "",
    });
    setDialog("vasp");
  };

  const submitTransfer = () => prepareTransfer.mutate({
    transactionId: Number(transferForm.transactionId), jurisdiction: transferForm.jurisdiction.trim().toUpperCase(),
    originatorVaspId: transferForm.originatorVaspId ? Number(transferForm.originatorVaspId) : undefined,
    beneficiaryVaspId: transferForm.beneficiaryVaspId ? Number(transferForm.beneficiaryVaspId) : undefined,
    protocol: transferForm.protocol || undefined,
    payload: {
      originatorName: transferForm.originatorName.trim(), originatorAccount: transferForm.originatorAccount.trim(),
      originatorAddress: transferForm.originatorAddress.trim(), originatorCountry: transferForm.originatorCountry.trim().toUpperCase(),
      beneficiaryName: transferForm.beneficiaryName.trim(), beneficiaryAccount: transferForm.beneficiaryAccount.trim(),
      beneficiaryVasp: transferForm.beneficiaryVasp.trim(),
    },
  }, { onSuccess: (result) => { setTransferForm(transferInitial); success(`Travel Rule case prepared with status ${result.status}.`); }, onError: (value) => setError(mutationError(value)) });

  const submitPolicy = () => savePolicy.mutate({
    id: policyForm.id ? Number(policyForm.id) : undefined,
    body: {
      jurisdiction: policyForm.jurisdiction.trim().toUpperCase(), policyCode: policyForm.policyCode.trim(),
      thresholdUsd: Number(policyForm.thresholdUsd), appliesToAllTransfers: policyForm.appliesToAllTransfers,
      requiredFields: csv(policyForm.requiredFields), verifyOriginator: policyForm.verifyOriginator,
      verifyBeneficiary: policyForm.verifyBeneficiary, acceptedProtocols: csv(policyForm.acceptedProtocols),
      retentionYears: Number(policyForm.retentionYears), effectiveFrom: policyForm.effectiveFrom,
      effectiveUntil: policyForm.effectiveUntil || undefined, enabled: policyForm.enabled,
      legalReference: policyForm.legalReference.trim() || undefined,
    },
  }, { onSuccess: () => { setPolicyForm(policyInitial); success("Travel Rule policy saved."); }, onError: (value) => setError(mutationError(value)) });

  const editPolicy = (item: TravelRulePolicy) => {
    setPolicyForm({
      id: String(item.id), jurisdiction: item.jurisdiction, policyCode: item.policyCode, thresholdUsd: String(item.thresholdUsd),
      appliesToAllTransfers: item.appliesToAllTransfers, requiredFields: item.requiredFields.join(","),
      verifyOriginator: item.verifyOriginator, verifyBeneficiary: item.verifyBeneficiary,
      acceptedProtocols: item.acceptedProtocols.join(","), retentionYears: String(item.retentionYears),
      effectiveFrom: item.effectiveFrom.slice(0, 16), effectiveUntil: item.effectiveUntil?.slice(0, 16) ?? "",
      enabled: item.enabled, legalReference: item.legalReference ?? "",
    });
    setDialog("policy");
  };

  const submitGrant = () => createGrant.mutate({
    regulatorName: grantForm.regulatorName.trim(), jurisdiction: grantForm.jurisdiction.trim().toUpperCase(),
    scopes: csv(grantForm.scopes), allowedIpAddresses: csv(grantForm.allowedIpAddresses), expiresAt: grantForm.expiresAt,
  }, { onSuccess: (result) => { setIssuedAccessKey(result.accessKey ?? ""); setGrantForm(grantInitial); success("Regulator access granted. The access key is displayed once below."); }, onError: (value) => setError(mutationError(value)) });

  const openVerify = (item: TravelRuleTransfer, party: string) => { setSelectedTransfer(item); setVerifyParty(party); setVerifyEvidence(""); setVerificationPassed(true); setDialog("verify"); };
  const submitVerification = () => selectedTransfer && verifyIdentity.mutate({
    id: selectedTransfer.id, party: verifyParty, verified: verificationPassed, evidenceReference: verifyEvidence.trim(),
  }, { onSuccess: () => success(`${verifyParty.toLowerCase()} identity decision recorded.`), onError: (value) => setError(mutationError(value)) });

  return <HokekaPageShell title="Wallet Intelligence" subtitle="VASP due diligence, wallet exposure, Travel Rule, and regulator access" noCard>
    <Box>
      {issuedAccessKey && <Alert severity="warning" sx={{ mb: 2 }} onClose={() => setIssuedAccessKey("")}>
        One-time regulator access key: <Typography component="span" fontFamily="monospace" sx={{ wordBreak: "break-all" }}>{issuedAccessKey}</Typography>
      </Alert>}
      <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 2, mb: 2, flexWrap: "wrap" }}>
        <Tabs value={tab} onChange={(_, value) => setTab(value)} variant="scrollable" scrollButtons="auto">
          <Tab icon={<WalletCards size={17} />} iconPosition="start" label={`Wallets (${wallets.data?.totalElements ?? 0})`} />
          <Tab icon={<ShieldCheck size={17} />} iconPosition="start" label={`Screening (${screenings.data?.totalElements ?? 0})`} />
          <Tab icon={<Building2 size={17} />} iconPosition="start" label={`VASP directory (${vasps.data?.totalElements ?? 0})`} />
          <Tab icon={<Send size={17} />} iconPosition="start" label={`Travel Rule (${transfers.data?.totalElements ?? 0})`} />
          <Tab icon={<FileKey2 size={17} />} iconPosition="start" label="Governance" />
        </Tabs>
        <Button variant="contained" startIcon={<Plus size={16} />} onClick={() => setDialog(tab === 0 ? "wallet" : tab === 2 ? "vasp" : tab === 3 ? "transfer" : tab === 4 ? "policy" : "wallet")}>
          {tab === 0 ? "Wallet" : tab === 2 ? "VASP" : tab === 3 ? "Transfer" : tab === 4 ? "Policy" : "Wallet"}
        </Button>
      </Box>

      {tab === 0 && <Paper variant="outlined"><TableContainer><Table size="small"><TableHead><TableRow>
        <TableCell>Wallet</TableCell><TableCell>Customer</TableCell><TableCell>VASP</TableCell><TableCell>Screening</TableCell><TableCell>Exposure</TableCell><TableCell>Next due</TableCell><TableCell align="right">Action</TableCell>
      </TableRow></TableHead><TableBody>
        {wallets.isLoading && <TableRow><TableCell colSpan={7} align="center">Loading wallets...</TableCell></TableRow>}
        {!wallets.isLoading && !wallets.data?.content.length && <TableRow><TableCell colSpan={7} align="center" sx={{ py: 5 }}>No crypto wallets registered.</TableCell></TableRow>}
        {wallets.data?.content.map((item) => <TableRow key={item.id} hover>
          <TableCell><Link to={`/records/CRYPTO_WALLET_PROFILE/${item.id}`}><Typography fontFamily="monospace" variant="body2" sx={{ maxWidth: 210, overflow: "hidden", textOverflow: "ellipsis" }}>{item.walletAddress}</Typography></Link><Typography variant="caption" color="text.secondary">{item.network} / {item.ownershipType}</Typography></TableCell>
          <TableCell><Link to={`/records/MULTI_ASSET_CUSTOMER/${item.customerId}`}>{item.customerName}</Link></TableCell>
          <TableCell>{item.vaspId ? <Link to={`/records/VASP_DIRECTORY_ENTRY/${item.vaspId}`}>{item.vaspName}</Link> : "Self-hosted / unknown"}</TableCell>
          <TableCell><Chip size="small" color={item.screeningAvailable ? "success" : "error"} label={item.screeningStatus} /><Typography variant="caption" display="block">{item.latestProvider || "No provider result"}</Typography></TableCell>
          <TableCell><Chip size="small" color={riskColor(item.latestRiskScore)} label={item.latestRiskScore ?? "Unavailable"} /> <Typography variant="caption">{item.latestCategories.join(", ") || "No categories"}</Typography></TableCell>
          <TableCell>{displayDate(item.nextScreeningAt)}</TableCell>
          <TableCell align="right"><Button size="small" onClick={() => { setSelectedWalletId(item.id); setScreeningPage(0); setTab(1); }}>History</Button><Button size="small" startIcon={<RefreshCw size={14} />} disabled={screenWallet.isPending} onClick={() => screenWallet.mutate({ id: item.id, trigger: "MANUAL" }, { onSuccess: () => setNotice("Wallet rescreened."), onError: (value) => setError(mutationError(value)) })}>Screen</Button></TableCell>
        </TableRow>)}
      </TableBody></Table></TableContainer><TablePagination component="div" count={wallets.data?.totalElements ?? 0} page={walletPage} rowsPerPage={25} rowsPerPageOptions={[25]} onPageChange={(_, page) => setWalletPage(page)} /></Paper>}

      {tab === 1 && <Paper variant="outlined"><Box sx={{ p: 2, display: "flex", gap: 2, alignItems: "center", flexWrap: "wrap" }}><TextField select size="small" label="Wallet evidence" value={selectedWalletId ?? ""} onChange={(e) => { setSelectedWalletId(e.target.value ? Number(e.target.value) : undefined); setScreeningPage(0); }} sx={{ minWidth: 300 }}><MenuItem value="">All wallets</MenuItem>{wallets.data?.content.map((item) => <MenuItem key={item.id} value={item.id}>{item.walletAddress} / {item.network}</MenuItem>)}</TextField></Box><TableContainer><Table size="small"><TableHead><TableRow>
        <TableCell>Screened</TableCell><TableCell>Address</TableCell><TableCell>Customer / transaction</TableCell><TableCell>Trigger</TableCell><TableCell>Provider evidence</TableCell><TableCell>Risk</TableCell><TableCell>Direct / indirect</TableCell><TableCell>Depth</TableCell><TableCell>Retention</TableCell>
      </TableRow></TableHead><TableBody>
        {screenings.isLoading && <TableRow><TableCell colSpan={9} align="center">Loading immutable evidence...</TableCell></TableRow>}
        {!screenings.isLoading && !screenings.data?.content.length && <TableRow><TableCell colSpan={9} align="center" sx={{ py: 5 }}>No wallet screening evidence.</TableCell></TableRow>}
        {screenings.data?.content.map((item) => <TableRow key={item.id} hover><TableCell><Link to={`/records/WALLET_SCREENING_RECORD/${item.id}`}>{displayDate(item.screenedAt)}</Link></TableCell><TableCell><Typography fontFamily="monospace" variant="body2" sx={{ maxWidth: 180, overflow: "hidden", textOverflow: "ellipsis" }}>{item.screenedAddress || "Address unavailable"}</Typography><Typography variant="caption">{item.network || "Network unavailable"}</Typography></TableCell><TableCell><Link to={`/records/MULTI_ASSET_CUSTOMER/${item.customerId}`}>{item.customerName}</Link>{item.transactionId && <Typography variant="caption" display="block"><Link to={`/records/MULTI_ASSET_TRANSACTION/${item.transactionId}`}>Transaction #{item.transactionId}</Link></Typography>}</TableCell><TableCell>{item.triggerType.replaceAll("_", " ")}</TableCell><TableCell>{item.provider}<Typography variant="caption" display="block" color={item.available ? "text.secondary" : "error"}>{item.providerReference || item.unavailableReason || "No reference"}</Typography></TableCell><TableCell><Chip size="small" color={riskColor(item.riskScore)} label={item.riskScore ?? "Unavailable"} /><Typography variant="caption" display="block">{item.categories.join(", ") || "No categories"}</Typography></TableCell><TableCell>{item.directExposurePercent ?? "-"}% / {item.indirectExposurePercent ?? "-"}%</TableCell><TableCell>{item.maximumExposureDepth ?? "-"}</TableCell><TableCell>{item.retainUntil}</TableCell></TableRow>)}
      </TableBody></Table></TableContainer><TablePagination component="div" count={screenings.data?.totalElements ?? 0} page={screeningPage} rowsPerPage={25} rowsPerPageOptions={[25]} onPageChange={(_, page) => setScreeningPage(page)} /></Paper>}

      {tab === 2 && <Box sx={{ display: "grid", gap: 2 }}><Paper variant="outlined"><TableContainer><Table size="small"><TableHead><TableRow>
        <TableCell>VASP</TableCell><TableCell>Licence</TableCell><TableCell>Sanctions</TableCell><TableCell>Protocols</TableCell><TableCell>Risk</TableCell><TableCell>Decision</TableCell><TableCell>Review due</TableCell><TableCell />
      </TableRow></TableHead><TableBody>
        {!vasps.isLoading && !vasps.data?.content.length && <TableRow><TableCell colSpan={8} align="center" sx={{ py: 5 }}>No VASPs in the directory.</TableCell></TableRow>}
        {vasps.data?.content.map((item) => <TableRow key={item.id} hover selected={selectedVaspId === item.id}><TableCell><Link to={`/records/VASP_DIRECTORY_ENTRY/${item.id}`}>{item.legalName}</Link><Typography variant="caption" display="block">{item.jurisdiction} / {item.regulatorName || "Regulator unrecorded"}</Typography></TableCell><TableCell><Chip size="small" label={item.licenceStatus} /><Typography variant="caption" display="block">{item.licenceNumber || "No licence number"}</Typography></TableCell><TableCell><Chip size="small" color={item.sanctionsStatus === "MATCH" ? "error" : item.sanctionsStatus === "CLEAR" ? "success" : "warning"} label={item.sanctionsStatus} /><Typography variant="caption" display="block">{item.sanctionsMatchCount} match(es) / {item.sanctionsProvider || "Not screened"}</Typography></TableCell><TableCell>{item.travelRuleProtocols.join(", ") || "None"}</TableCell><TableCell><Chip size="small" color={riskColor(item.riskScore)} label={`${item.riskLevel} ${item.riskScore}`} /></TableCell><TableCell><Chip size="small" color={item.transferDecision === "PROHIBITED" ? "error" : item.transferDecision === "REVIEW" ? "warning" : "success"} label={item.transferDecision} /></TableCell><TableCell>{displayDate(item.nextReviewAt)}</TableCell><TableCell><Button size="small" onClick={() => setSelectedVaspId(item.id)}>History</Button><Button size="small" startIcon={<RefreshCw size={14} />} disabled={screenVasp.isPending} onClick={() => screenVasp.mutate(item.id, { onSuccess: () => setNotice("VASP and named beneficial owners rescreened."), onError: (value) => setError(mutationError(value)) })}>Screen</Button><Button size="small" onClick={() => editVasp(item)}>Edit</Button></TableCell></TableRow>)}
      </TableBody></Table></TableContainer></Paper>
      {selectedVaspId && <Paper variant="outlined"><Box sx={{ p: 2 }}><Typography variant="h6" sx={{ fontSize: "1rem" }}>VASP sanctions screening evidence</Typography></Box><TableContainer><Table size="small"><TableHead><TableRow><TableCell>Screened</TableCell><TableCell>Subject</TableCell><TableCell>Provider</TableCell><TableCell>Status</TableCell><TableCell>Matches</TableCell><TableCell>Retention</TableCell></TableRow></TableHead><TableBody>{vaspScreenings.isLoading && <TableRow><TableCell colSpan={6} align="center">Loading screening history...</TableCell></TableRow>}{!vaspScreenings.isLoading && !vaspScreenings.data?.content.length && <TableRow><TableCell colSpan={6} align="center">No screening evidence.</TableCell></TableRow>}{vaspScreenings.data?.content.map((item) => <TableRow key={item.id}><TableCell><Link to={`/records/VASP_SCREENING_RECORD/${item.id}`}>{displayDate(item.screenedAt)}</Link></TableCell><TableCell>{item.subjectName}<Typography variant="caption" display="block">{item.subjectType}</Typography></TableCell><TableCell>{item.provider}</TableCell><TableCell><Chip size="small" color={item.status === "MATCH" ? "error" : item.status === "CLEAR" ? "success" : "warning"} label={item.status} /></TableCell><TableCell>{item.matchCount}</TableCell><TableCell>{item.retainUntil}</TableCell></TableRow>)}</TableBody></Table></TableContainer></Paper>}
      </Box>}

      {tab === 3 && <Paper variant="outlined"><TableContainer><Table size="small"><TableHead><TableRow>
        <TableCell>Created</TableCell><TableCell>Transaction</TableCell><TableCell>VASP route</TableCell><TableCell>Policy</TableCell><TableCell>Identity</TableCell><TableCell>Status</TableCell><TableCell>Delivery</TableCell>
      </TableRow></TableHead><TableBody>
        {!transfers.isLoading && !transfers.data?.content.length && <TableRow><TableCell colSpan={7} align="center" sx={{ py: 5 }}>No Travel Rule transfer records.</TableCell></TableRow>}
        {transfers.data?.content.map((item) => <TableRow key={item.id} hover><TableCell><Link to={`/records/TRAVEL_RULE_TRANSFER/${item.id}`}>{displayDate(item.createdAt)}</Link></TableCell><TableCell><Link to={`/records/MULTI_ASSET_TRANSACTION/${item.transactionId}`}>{item.externalTransactionId}</Link></TableCell><TableCell>{item.originatorVaspName || "Unspecified"} to {item.beneficiaryVaspName || "Unspecified"}<Typography variant="caption" display="block">{item.protocol || "Protocol unselected"}</Typography></TableCell><TableCell>{item.policyId ? <Link to={`/records/TRAVEL_RULE_JURISDICTION_POLICY/${item.policyId}`}>{item.policyCode}</Link> : "No applicable policy"}</TableCell><TableCell><Button size="small" color={item.originatorVerification === "VERIFIED" ? "success" : "primary"} disabled={item.originatorVerification === "VERIFIED"} onClick={() => openVerify(item, "ORIGINATOR")}>Originator: {item.originatorVerification}</Button><Typography variant="caption" display="block">{item.originatorVerificationReference || "No evidence reference"}</Typography><Button size="small" color={item.beneficiaryVerification === "VERIFIED" ? "success" : "primary"} disabled={item.beneficiaryVerification === "VERIFIED"} onClick={() => openVerify(item, "BENEFICIARY")}>Beneficiary: {item.beneficiaryVerification}</Button><Typography variant="caption" display="block">{item.beneficiaryVerificationReference || "No evidence reference"}</Typography></TableCell><TableCell><Chip size="small" color={item.status === "FAILED" || item.status === "REJECTED" || item.status === "PENDING_DATA" ? "error" : item.status === "ACKNOWLEDGED" || item.status === "TRANSMITTED" ? "success" : "warning"} label={item.status} /><Typography variant="caption" display="block" color="error">{item.failureReason}</Typography></TableCell><TableCell><Button size="small" startIcon={<Send size={14} />} disabled={!(["READY", "FAILED"].includes(item.status)) || transmit.isPending} onClick={() => transmit.mutate(item.id, { onSuccess: (result) => setNotice(`Transmission ${result.status.toLowerCase()}.`), onError: (value) => setError(mutationError(value)) })}>Transmit</Button><Typography variant="caption" display="block">{item.transmissionAttempts} attempt(s)</Typography></TableCell></TableRow>)}
      </TableBody></Table></TableContainer><TablePagination component="div" count={transfers.data?.totalElements ?? 0} page={transferPage} rowsPerPage={25} rowsPerPageOptions={[25]} onPageChange={(_, page) => setTransferPage(page)} /></Paper>}

      {tab === 4 && <Box sx={{ display: "grid", gap: 2 }}>
        <Paper variant="outlined"><Box sx={{ px: 2, py: 1.5, display: "flex", justifyContent: "space-between", alignItems: "center" }}><Typography variant="h6" sx={{ fontSize: "1rem" }}>Jurisdiction policies</Typography><Button size="small" startIcon={<Plus size={15} />} onClick={() => setDialog("policy")}>Policy</Button></Box><TableContainer><Table size="small"><TableHead><TableRow><TableCell>Jurisdiction</TableCell><TableCell>Policy</TableCell><TableCell>Threshold</TableCell><TableCell>Required data</TableCell><TableCell>Protocols</TableCell><TableCell>Effective</TableCell><TableCell /></TableRow></TableHead><TableBody>{policies.data?.map((item) => <TableRow key={item.id}><TableCell>{item.jurisdiction}</TableCell><TableCell><Link to={`/records/TRAVEL_RULE_JURISDICTION_POLICY/${item.id}`}>{item.policyCode}</Link><Chip size="small" sx={{ ml: 1 }} label={item.enabled ? "Enabled" : "Disabled"} /></TableCell><TableCell>{item.appliesToAllTransfers ? "All transfers" : `$${item.thresholdUsd.toLocaleString()}`}</TableCell><TableCell>{item.requiredFields.join(", ")}</TableCell><TableCell>{item.acceptedProtocols.join(", ")}</TableCell><TableCell>{displayDate(item.effectiveFrom)}</TableCell><TableCell><Button size="small" onClick={() => editPolicy(item)}>Edit</Button></TableCell></TableRow>)}</TableBody></Table></TableContainer></Paper>
        <Paper variant="outlined"><Box sx={{ px: 2, py: 1.5, display: "flex", justifyContent: "space-between", alignItems: "center" }}><Typography variant="h6" sx={{ fontSize: "1rem" }}>Read-only regulator access</Typography><Button size="small" startIcon={<KeyRound size={15} />} onClick={() => setDialog("grant")}>Grant</Button></Box><TableContainer><Table size="small"><TableHead><TableRow><TableCell>Regulator</TableCell><TableCell>Scopes</TableCell><TableCell>IP allowlist</TableCell><TableCell>Expires</TableCell><TableCell>Last access</TableCell><TableCell>Status</TableCell><TableCell /></TableRow></TableHead><TableBody>{!grants.data?.length && <TableRow><TableCell colSpan={7} align="center" sx={{ py: 4 }}>No regulator access grants.</TableCell></TableRow>}{grants.data?.map((item) => <TableRow key={item.id}><TableCell><Link to={`/records/VIRTUAL_ASSET_REGULATOR_ACCESS_GRANT/${item.id}`}>{item.regulatorName}</Link><Typography variant="caption" display="block">{item.jurisdiction}</Typography></TableCell><TableCell>{item.scopes.join(", ")}</TableCell><TableCell>{item.allowedIpAddresses.join(", ") || "Any source IP"}</TableCell><TableCell>{displayDate(item.expiresAt)}</TableCell><TableCell>{displayDate(item.lastAccessedAt)}</TableCell><TableCell><Chip size="small" color={item.revokedAt ? "default" : "success"} label={item.revokedAt ? "Revoked" : "Active"} /></TableCell><TableCell><Button size="small" color="error" disabled={Boolean(item.revokedAt) || revokeGrant.isPending} onClick={() => revokeGrant.mutate(item.id, { onSuccess: () => setNotice("Access grant revoked."), onError: (value) => setError(mutationError(value)) })}>Revoke</Button></TableCell></TableRow>)}</TableBody></Table></TableContainer></Paper>
      </Box>}

      <Dialog open={dialog === "wallet"} onClose={closeDialog} fullWidth maxWidth="sm"><DialogTitle>Register crypto wallet</DialogTitle><DialogContent><Box sx={{ display: "grid", gap: 2, pt: 1 }}>
        <TextField select label="Customer" required value={walletForm.customerId} onChange={(e) => setWalletForm({ ...walletForm, customerId: e.target.value, assetAccountId: "" })}>{customers.data?.content.map((item) => <MenuItem key={item.id} value={item.id}>{item.displayName} / {item.externalCustomerId}</MenuItem>)}</TextField>
        <TextField select label="Crypto asset account" required value={walletForm.assetAccountId} onChange={(e) => setWalletForm({ ...walletForm, assetAccountId: e.target.value })}>{eligibleAccounts.map((item) => <MenuItem key={item.id} value={item.id}>{item.externalAccountId} / {item.publicAddress || "No public address"}</MenuItem>)}</TextField>
        {selectedCustomerId && !customer360.isLoading && !eligibleAccounts.length && <Alert severity="warning">This customer has no crypto wallet or exchange account. Create the account in Customer 360 before registering it for screening.</Alert>}
        <TextField select label="Attributed VASP" value={walletForm.vaspId} onChange={(e) => setWalletForm({ ...walletForm, vaspId: e.target.value })}><MenuItem value="">Self-hosted / unknown</MenuItem>{vasps.data?.content.map((item) => <MenuItem key={item.id} value={item.id}>{item.legalName}</MenuItem>)}</TextField>
        <TextField label="Network" required value={walletForm.network} onChange={(e) => setWalletForm({ ...walletForm, network: e.target.value })} /><TextField label="Address label" value={walletForm.addressLabel} onChange={(e) => setWalletForm({ ...walletForm, addressLabel: e.target.value })} /><TextField label="Ownership type" value={walletForm.ownershipType} onChange={(e) => setWalletForm({ ...walletForm, ownershipType: e.target.value })} /><TextField label="Screening interval (hours)" type="number" inputProps={{ min: 1, max: 8760 }} value={walletForm.screeningIntervalHours} onChange={(e) => setWalletForm({ ...walletForm, screeningIntervalHours: e.target.value })} />
      </Box></DialogContent><DialogActions><Button onClick={closeDialog}>Cancel</Button><Button variant="contained" disabled={!walletForm.assetAccountId || !walletForm.network.trim() || registerWallet.isPending} onClick={submitWallet}>Register and screen</Button></DialogActions></Dialog>

      <Dialog open={dialog === "vasp"} onClose={closeDialog} fullWidth maxWidth="lg"><DialogTitle>{vaspForm.id ? "Update" : "Create"} VASP due diligence</DialogTitle><DialogContent><Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", md: "repeat(3, minmax(0, 1fr))" }, gap: 2, pt: 1 }}>
        <TextField label="Legal name" required value={vaspForm.legalName} onChange={(e) => setVaspForm({ ...vaspForm, legalName: e.target.value })} /><TextField label="Trading name" value={vaspForm.tradingName} onChange={(e) => setVaspForm({ ...vaspForm, tradingName: e.target.value })} /><TextField label="Jurisdiction" required value={vaspForm.jurisdiction} onChange={(e) => setVaspForm({ ...vaspForm, jurisdiction: e.target.value.toUpperCase() })} />
        <TextField label="Regulator" value={vaspForm.regulatorName} onChange={(e) => setVaspForm({ ...vaspForm, regulatorName: e.target.value })} /><TextField label="Licence number" value={vaspForm.licenceNumber} onChange={(e) => setVaspForm({ ...vaspForm, licenceNumber: e.target.value })} /><TextField select label="Licence status" value={vaspForm.licenceStatus} onChange={(e) => setVaspForm({ ...vaspForm, licenceStatus: e.target.value })}>{["LICENSED", "PENDING", "SUSPENDED", "REVOKED", "UNLICENSED", "UNKNOWN"].map((value) => <MenuItem key={value} value={value}>{value}</MenuItem>)}</TextField>
        <TextField label="Licence valid until" type="date" InputLabelProps={{ shrink: true }} value={vaspForm.licenceValidUntil} onChange={(e) => setVaspForm({ ...vaspForm, licenceValidUntil: e.target.value })} /><TextField label="Registration number" value={vaspForm.registrationNumber} onChange={(e) => setVaspForm({ ...vaspForm, registrationNumber: e.target.value })} /><TextField label="Website" value={vaspForm.website} onChange={(e) => setVaspForm({ ...vaspForm, website: e.target.value })} />
        <TextField label="Travel Rule protocols (comma-separated)" value={vaspForm.travelRuleProtocols} onChange={(e) => setVaspForm({ ...vaspForm, travelRuleProtocols: e.target.value })} /><TextField label="Sanctions screening" disabled value={vaspForm.id ? vaspForm.sanctionsStatus : "Runs on save"} /><TextField label="Risk score" type="number" inputProps={{ min: 0, max: 100 }} value={vaspForm.riskScore} onChange={(e) => setVaspForm({ ...vaspForm, riskScore: e.target.value })} />
        <TextField select label="Transfer decision" value={vaspForm.transferDecision} onChange={(e) => setVaspForm({ ...vaspForm, transferDecision: e.target.value })}>{["PERMITTED", "REVIEW", "PROHIBITED"].map((value) => <MenuItem key={value} value={value}>{value}</MenuItem>)}</TextField><TextField label="Next review" type="datetime-local" InputLabelProps={{ shrink: true }} value={vaspForm.nextReviewAt} onChange={(e) => setVaspForm({ ...vaspForm, nextReviewAt: e.target.value })} /><TextField label="Review notes" value={vaspForm.reviewNotes} onChange={(e) => setVaspForm({ ...vaspForm, reviewNotes: e.target.value })} />
        <TextField label="Beneficial ownership JSON" multiline minRows={4} value={vaspForm.beneficialOwnership} onChange={(e) => setVaspForm({ ...vaspForm, beneficialOwnership: e.target.value })} sx={{ gridColumn: { md: "span 2" } }} /><TextField label="Attributed wallet clusters JSON" multiline minRows={4} value={vaspForm.walletClusters} onChange={(e) => setVaspForm({ ...vaspForm, walletClusters: e.target.value })} />
      </Box></DialogContent><DialogActions><Button onClick={closeDialog}>Cancel</Button><Button variant="contained" disabled={!vaspForm.legalName.trim() || !vaspForm.jurisdiction.trim() || saveVasp.isPending} onClick={submitVasp}>Save</Button></DialogActions></Dialog>

      <Dialog open={dialog === "transfer"} onClose={closeDialog} fullWidth maxWidth="lg"><DialogTitle>Prepare encrypted Travel Rule transfer</DialogTitle><DialogContent><Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", md: "repeat(3, minmax(0, 1fr))" }, gap: 2, pt: 1 }}>
        <TextField select label="Crypto transaction" required value={transferForm.transactionId} onChange={(e) => setTransferForm({ ...transferForm, transactionId: e.target.value })}>{transactions.data?.content.map((item) => <MenuItem key={item.id} value={item.id}>{item.externalTransactionId} / {item.amount} {item.currency}</MenuItem>)}</TextField><TextField label="Jurisdiction" required value={transferForm.jurisdiction} onChange={(e) => setTransferForm({ ...transferForm, jurisdiction: e.target.value.toUpperCase() })} /><TextField select label="Protocol" value={transferForm.protocol} onChange={(e) => setTransferForm({ ...transferForm, protocol: e.target.value })}><MenuItem value="">Select at transmission</MenuItem>{["TRISA", "OPENVASP", "SYGNA", "NOTABENE", "CUSTOM_API"].map((value) => <MenuItem key={value} value={value}>{value}</MenuItem>)}</TextField>
        <TextField select label="Originator VASP" value={transferForm.originatorVaspId} onChange={(e) => setTransferForm({ ...transferForm, originatorVaspId: e.target.value })}><MenuItem value="">Unspecified</MenuItem>{vasps.data?.content.map((item) => <MenuItem key={item.id} value={item.id}>{item.legalName}</MenuItem>)}</TextField><TextField select label="Beneficiary VASP" value={transferForm.beneficiaryVaspId} onChange={(e) => { const item = vasps.data?.content.find((v) => v.id === Number(e.target.value)); setTransferForm({ ...transferForm, beneficiaryVaspId: e.target.value, beneficiaryVasp: item?.legalName ?? transferForm.beneficiaryVasp }); }}><MenuItem value="">Unspecified</MenuItem>{vasps.data?.content.map((item) => <MenuItem key={item.id} value={item.id}>{item.legalName}</MenuItem>)}</TextField><Box />
        <TextField label="Originator name" required value={transferForm.originatorName} onChange={(e) => setTransferForm({ ...transferForm, originatorName: e.target.value })} /><TextField label="Originator account" required value={transferForm.originatorAccount} onChange={(e) => setTransferForm({ ...transferForm, originatorAccount: e.target.value })} /><TextField label="Originator country" required value={transferForm.originatorCountry} onChange={(e) => setTransferForm({ ...transferForm, originatorCountry: e.target.value.toUpperCase() })} /><TextField label="Originator address" required value={transferForm.originatorAddress} onChange={(e) => setTransferForm({ ...transferForm, originatorAddress: e.target.value })} />
        <TextField label="Beneficiary name" required value={transferForm.beneficiaryName} onChange={(e) => setTransferForm({ ...transferForm, beneficiaryName: e.target.value })} /><TextField label="Beneficiary account" required value={transferForm.beneficiaryAccount} onChange={(e) => setTransferForm({ ...transferForm, beneficiaryAccount: e.target.value })} /><TextField label="Beneficiary VASP name" required value={transferForm.beneficiaryVasp} onChange={(e) => setTransferForm({ ...transferForm, beneficiaryVasp: e.target.value })} />
      </Box></DialogContent><DialogActions><Button onClick={closeDialog}>Cancel</Button><Button variant="contained" disabled={!transferForm.transactionId || !transferForm.originatorName.trim() || !transferForm.beneficiaryName.trim() || prepareTransfer.isPending} onClick={submitTransfer}>Prepare encrypted record</Button></DialogActions></Dialog>

      <Dialog open={dialog === "verify"} onClose={closeDialog} fullWidth maxWidth="sm"><DialogTitle>Review {verifyParty.toLowerCase()} identity</DialogTitle><DialogContent><Box sx={{ display: "grid", gap: 1, mt: 1 }}><TextField autoFocus fullWidth label="Verification evidence reference" required value={verifyEvidence} onChange={(e) => setVerifyEvidence(e.target.value)} helperText="Enter the KYC, document, or provider evidence reference used for this decision." /><FormControlLabel control={<Checkbox checked={verificationPassed} onChange={(e) => setVerificationPassed(e.target.checked)} />} label="Identity verification passed" /></Box></DialogContent><DialogActions><Button onClick={closeDialog}>Cancel</Button><Button variant="contained" color={verificationPassed ? "primary" : "error"} disabled={!verifyEvidence.trim() || verifyIdentity.isPending} onClick={submitVerification}>Record decision</Button></DialogActions></Dialog>

      <Dialog open={dialog === "policy"} onClose={closeDialog} fullWidth maxWidth="lg"><DialogTitle>{policyForm.id ? "Update" : "Create"} jurisdiction policy</DialogTitle><DialogContent><Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", md: "repeat(3, minmax(0, 1fr))" }, gap: 2, pt: 1 }}>
        <TextField label="Jurisdiction" required value={policyForm.jurisdiction} onChange={(e) => setPolicyForm({ ...policyForm, jurisdiction: e.target.value.toUpperCase() })} /><TextField label="Policy code" required value={policyForm.policyCode} onChange={(e) => setPolicyForm({ ...policyForm, policyCode: e.target.value })} /><TextField label="Threshold USD" type="number" value={policyForm.thresholdUsd} onChange={(e) => setPolicyForm({ ...policyForm, thresholdUsd: e.target.value })} />
        <TextField label="Required payload fields" value={policyForm.requiredFields} onChange={(e) => setPolicyForm({ ...policyForm, requiredFields: e.target.value })} sx={{ gridColumn: { md: "span 2" } }} /><TextField label="Accepted protocols" value={policyForm.acceptedProtocols} onChange={(e) => setPolicyForm({ ...policyForm, acceptedProtocols: e.target.value })} /><TextField label="Retention years" type="number" inputProps={{ min: 7 }} value={policyForm.retentionYears} onChange={(e) => setPolicyForm({ ...policyForm, retentionYears: e.target.value })} /><TextField label="Effective from" required type="datetime-local" InputLabelProps={{ shrink: true }} value={policyForm.effectiveFrom} onChange={(e) => setPolicyForm({ ...policyForm, effectiveFrom: e.target.value })} /><TextField label="Effective until" type="datetime-local" InputLabelProps={{ shrink: true }} value={policyForm.effectiveUntil} onChange={(e) => setPolicyForm({ ...policyForm, effectiveUntil: e.target.value })} /><TextField label="Legal reference" value={policyForm.legalReference} onChange={(e) => setPolicyForm({ ...policyForm, legalReference: e.target.value })} sx={{ gridColumn: { md: "span 2" } }} /><Box><FormControlLabel control={<Checkbox checked={policyForm.appliesToAllTransfers} onChange={(e) => setPolicyForm({ ...policyForm, appliesToAllTransfers: e.target.checked })} />} label="Applies to all transfers" /><FormControlLabel control={<Checkbox checked={policyForm.verifyOriginator} onChange={(e) => setPolicyForm({ ...policyForm, verifyOriginator: e.target.checked })} />} label="Verify originator" /><FormControlLabel control={<Checkbox checked={policyForm.verifyBeneficiary} onChange={(e) => setPolicyForm({ ...policyForm, verifyBeneficiary: e.target.checked })} />} label="Verify beneficiary" /><FormControlLabel control={<Checkbox checked={policyForm.enabled} onChange={(e) => setPolicyForm({ ...policyForm, enabled: e.target.checked })} />} label="Enabled" /></Box>
      </Box></DialogContent><DialogActions><Button onClick={closeDialog}>Cancel</Button><Button variant="contained" disabled={!policyForm.policyCode.trim() || !policyForm.effectiveFrom || savePolicy.isPending} onClick={submitPolicy}>Save</Button></DialogActions></Dialog>

      <Dialog open={dialog === "grant"} onClose={closeDialog} fullWidth maxWidth="sm"><DialogTitle>Create read-only regulator grant</DialogTitle><DialogContent><Box sx={{ display: "grid", gap: 2, pt: 1 }}><TextField label="Regulator name" required value={grantForm.regulatorName} onChange={(e) => setGrantForm({ ...grantForm, regulatorName: e.target.value })} /><TextField label="Jurisdiction" required value={grantForm.jurisdiction} onChange={(e) => setGrantForm({ ...grantForm, jurisdiction: e.target.value.toUpperCase() })} /><TextField label="Scopes (comma-separated)" required value={grantForm.scopes} onChange={(e) => setGrantForm({ ...grantForm, scopes: e.target.value })} /><TextField label="Allowed IP addresses (comma-separated)" value={grantForm.allowedIpAddresses} onChange={(e) => setGrantForm({ ...grantForm, allowedIpAddresses: e.target.value })} /><TextField label="Expires at" required type="datetime-local" InputLabelProps={{ shrink: true }} value={grantForm.expiresAt} onChange={(e) => setGrantForm({ ...grantForm, expiresAt: e.target.value })} /></Box></DialogContent><DialogActions><Button onClick={closeDialog}>Cancel</Button><Button variant="contained" disabled={!grantForm.regulatorName.trim() || !grantForm.scopes.trim() || !grantForm.expiresAt || createGrant.isPending} onClick={submitGrant}>Issue access key</Button></DialogActions></Dialog>

      <Snackbar open={Boolean(notice)} autoHideDuration={5000} onClose={() => setNotice("")} message={notice} />
      <Snackbar open={Boolean(error)} autoHideDuration={7000} onClose={() => setError("")}><Alert severity="error" onClose={() => setError("")}>{error}</Alert></Snackbar>
    </Box>
  </HokekaPageShell>;
}
