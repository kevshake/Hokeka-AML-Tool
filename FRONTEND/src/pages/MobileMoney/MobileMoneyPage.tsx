import { useState } from "react";
import { Link } from "react-router-dom";
import { Network, Plus, Search, Smartphone } from "lucide-react";
import {
  Alert, Box, Button, Checkbox, Chip, Dialog, DialogActions, DialogContent, DialogTitle,
  FormControlLabel, MenuItem, Paper, Snackbar, Tab, Table, TableBody, TableCell,
  TableContainer, TableHead, TablePagination, TableRow, Tabs, TextField, Typography,
} from "@mui/material";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import {
  useMobileMoneyNetwork, useMobileMoneyRiskProfiles, useMobileMoneyTransactions, useMultiAssetCustomers,
} from "../../features/api/queries";
import { useIngestMobileMoneyTransaction } from "../../features/api/mutations";
import type { MobileMoneyEntityType, MobileMoneyEventType } from "../../types/mobileMoney";

const eventTypes: MobileMoneyEventType[] = [
  "CASH_IN", "CASH_OUT", "P2P_TRANSFER", "MERCHANT_PAYMENT", "BILL_PAYMENT", "REFUND", "REVERSAL",
];

const transactionType: Record<MobileMoneyEventType, string> = {
  CASH_IN: "TOP_UP",
  CASH_OUT: "WITHDRAWAL",
  P2P_TRANSFER: "TRANSFER",
  MERCHANT_PAYMENT: "PAYMENT",
  BILL_PAYMENT: "PAYMENT",
  REFUND: "REFUND",
  REVERSAL: "REFUND",
};

const initialForm = {
  externalTransactionId: "", customerId: "", eventType: "P2P_TRANSFER" as MobileMoneyEventType,
  amount: "", fiatEquivalentUsd: "", currency: "KES", executedAt: "", countryCode: "KE",
  walletReference: "", counterpartyWalletReference: "", counterpartyReference: "",
  fundingPartyCustomerId: "", deviceFingerprint: "", agentDeviceFingerprint: "",
  simIdentifierHash: "", agentReference: "", merchantReference: "", merchantCategory: "",
  latitude: "", longitude: "", agentLatitude: "", agentLongitude: "", cellId: "",
  simChangedAt: "", deviceRegisteredAt: "", walletPreviousActivityAt: "",
  agentFloatBefore: "", agentFloatAfter: "", crossBorder: false,
};

function displayDate(value?: string) {
  return value ? new Date(value).toLocaleString() : "-";
}

function riskColor(level: string): "default" | "warning" | "error" | "success" {
  if (level === "CRITICAL") return "error";
  if (level === "HIGH" || level === "MEDIUM") return "warning";
  if (level === "LOW") return "success";
  return "default";
}

function optionalNumber(value: string) {
  return value === "" ? undefined : Number(value);
}

function optionalText(value: string) {
  const trimmed = value.trim();
  return trimmed || undefined;
}

export default function MobileMoneyPage() {
  const [tab, setTab] = useState(0);
  const [transactionPage, setTransactionPage] = useState(0);
  const [profilePage, setProfilePage] = useState(0);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [form, setForm] = useState(initialForm);
  const [selected, setSelected] = useState<{ type: MobileMoneyEntityType; reference: string } | null>(null);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const transactions = useMobileMoneyTransactions(transactionPage, 25);
  const profiles = useMobileMoneyRiskProfiles(profilePage, 25);
  const customers = useMultiAssetCustomers("", 0, 100);
  const network = useMobileMoneyNetwork(selected?.type ?? null, selected?.reference ?? null);
  const ingest = useIngestMobileMoneyTransaction();

  const setField = (field: keyof typeof form, value: string | boolean) =>
    setForm((current) => ({ ...current, [field]: value }));

  const submit = () => ingest.mutate({
    externalTransactionId: form.externalTransactionId.trim(),
    customerId: Number(form.customerId),
    transactionType: transactionType[form.eventType],
    eventType: form.eventType,
    amount: Number(form.amount),
    fiatEquivalentUsd: optionalNumber(form.fiatEquivalentUsd),
    currency: form.currency.trim().toUpperCase(),
    executedAt: optionalText(form.executedAt),
    countryCode: optionalText(form.countryCode)?.toUpperCase(),
    walletReference: form.walletReference.trim(),
    counterpartyWalletReference: optionalText(form.counterpartyWalletReference),
    counterpartyReference: optionalText(form.counterpartyReference),
    fundingPartyCustomerId: optionalText(form.fundingPartyCustomerId),
    deviceFingerprint: optionalText(form.deviceFingerprint),
    agentDeviceFingerprint: optionalText(form.agentDeviceFingerprint),
    simIdentifierHash: optionalText(form.simIdentifierHash),
    agentReference: optionalText(form.agentReference),
    merchantReference: optionalText(form.merchantReference),
    merchantCategory: optionalText(form.merchantCategory),
    latitude: optionalNumber(form.latitude), longitude: optionalNumber(form.longitude),
    agentLatitude: optionalNumber(form.agentLatitude), agentLongitude: optionalNumber(form.agentLongitude),
    cellId: optionalText(form.cellId), simChangedAt: optionalText(form.simChangedAt),
    deviceRegisteredAt: optionalText(form.deviceRegisteredAt),
    walletPreviousActivityAt: optionalText(form.walletPreviousActivityAt),
    agentFloatBefore: optionalNumber(form.agentFloatBefore), agentFloatAfter: optionalNumber(form.agentFloatAfter),
    crossBorder: form.crossBorder, metadata: {},
  }, {
    onSuccess: (result) => {
      setNotice(`Transaction assessed with ${result.signals.length} signal(s) and ${result.networkEdges.length} network link(s).`);
      setForm(initialForm);
      setDialogOpen(false);
    },
    onError: (value) => setError((value as { message?: string }).message || "The transaction could not be assessed."),
  });

  return <HokekaPageShell title="Mobile Money Intelligence" subtitle="Wallet, device, SIM, agent, merchant, and network risk" noCard>
    <Box>
      <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 2, mb: 2, flexWrap: "wrap" }}>
        <Tabs value={tab} onChange={(_, value) => setTab(value)}>
          <Tab icon={<Smartphone size={17} />} iconPosition="start" label={`Activity (${transactions.data?.totalElements ?? 0})`} />
          <Tab icon={<Network size={17} />} iconPosition="start" label={`Entity risk (${profiles.data?.totalElements ?? 0})`} />
        </Tabs>
        <Button variant="contained" startIcon={<Plus size={17} />} onClick={() => setDialogOpen(true)}>Transaction</Button>
      </Box>

      {tab === 0 && <Paper variant="outlined">
        <TableContainer><Table size="small"><TableHead><TableRow>
          <TableCell>Executed</TableCell><TableCell>Transaction</TableCell><TableCell>Customer</TableCell>
          <TableCell>Event</TableCell><TableCell>Wallet trail</TableCell><TableCell>Agent / merchant</TableCell>
          <TableCell>Amount</TableCell><TableCell>Risk</TableCell><TableCell>Decision</TableCell>
        </TableRow></TableHead><TableBody>
          {transactions.isLoading && <TableRow><TableCell colSpan={9} align="center">Loading activity...</TableCell></TableRow>}
          {!transactions.isLoading && !transactions.data?.content.length && <TableRow><TableCell colSpan={9} align="center" sx={{ py: 5 }}>No mobile-money activity.</TableCell></TableRow>}
          {transactions.data?.content.map((item) => <TableRow key={item.id} hover>
            <TableCell>{displayDate(item.executedAt)}</TableCell>
            <TableCell><Link to={`/records/MOBILE_MONEY_TRANSACTION_CONTEXT/${item.id}`}><Typography variant="body2" color="primary" fontFamily="monospace">{item.externalTransactionId}</Typography></Link><Link to={`/records/MULTI_ASSET_TRANSACTION/${item.transactionId}`}><Typography variant="caption" color="text.secondary">Transaction #{item.transactionId}</Typography></Link></TableCell>
            <TableCell><Link to={`/records/MULTI_ASSET_CUSTOMER/${item.customerId}`}>{item.customerName}</Link></TableCell>
            <TableCell>{item.eventType.replaceAll("_", " ")}{item.crossBorder && <Chip size="small" label="Cross-border" sx={{ ml: 1 }} />}</TableCell>
            <TableCell><Button size="small" startIcon={<Search size={14} />} onClick={() => { setSelected({ type: "WALLET", reference: item.walletReference }); setTab(1); }}>{item.walletReference}</Button><Typography variant="caption" display="block" color="text.secondary">{item.counterpartyWalletReference || "No counterparty wallet"}</Typography></TableCell>
            <TableCell>{item.agentReference || item.merchantReference || "-"}</TableCell>
            <TableCell>{item.amount.toLocaleString()} {item.currency}</TableCell>
            <TableCell><Chip size="small" label={item.riskScore} color={item.riskScore >= 70 ? "error" : item.riskScore >= 40 ? "warning" : "default"} /></TableCell>
            <TableCell>{item.decision}</TableCell>
          </TableRow>)}
        </TableBody></Table></TableContainer>
        <TablePagination component="div" count={transactions.data?.totalElements ?? 0} page={transactionPage} rowsPerPage={25} rowsPerPageOptions={[25]} onPageChange={(_, page) => setTransactionPage(page)} />
      </Paper>}

      {tab === 1 && <Box sx={{ display: "grid", gap: 2 }}>
        <Paper variant="outlined"><TableContainer><Table size="small"><TableHead><TableRow>
          <TableCell>Entity</TableCell><TableCell>Reference</TableCell><TableCell>Risk</TableCell>
          <TableCell>Signals</TableCell><TableCell>Last seen</TableCell><TableCell>Latest transaction</TableCell><TableCell align="right">Trail</TableCell>
        </TableRow></TableHead><TableBody>
          {profiles.isLoading && <TableRow><TableCell colSpan={7} align="center">Loading entity risk...</TableCell></TableRow>}
          {!profiles.isLoading && !profiles.data?.content.length && <TableRow><TableCell colSpan={7} align="center" sx={{ py: 5 }}>No entity risk profiles.</TableCell></TableRow>}
          {profiles.data?.content.map((profile) => <TableRow key={profile.id} hover selected={selected?.type === profile.entityType && selected.reference === profile.entityReference}>
            <TableCell><Chip size="small" variant="outlined" label={profile.entityType.replaceAll("_", " ")} /></TableCell>
            <TableCell><Link to={`/records/MOBILE_MONEY_RISK_PROFILE/${profile.id}`}>{profile.entityReference}</Link></TableCell>
            <TableCell><Chip size="small" color={riskColor(profile.riskLevel)} label={`${profile.riskLevel} ${profile.riskScore}`} /></TableCell>
            <TableCell>{profile.signalCount}</TableCell><TableCell>{displayDate(profile.lastSeenAt)}</TableCell>
            <TableCell>{profile.latestTransactionId ? <Link to={`/records/MULTI_ASSET_TRANSACTION/${profile.latestTransactionId}`}>#{profile.latestTransactionId}</Link> : "-"}</TableCell>
            <TableCell align="right"><Button size="small" startIcon={<Network size={15} />} onClick={() => setSelected({ type: profile.entityType, reference: profile.entityReference })}>Network</Button></TableCell>
          </TableRow>)}
        </TableBody></Table></TableContainer>
        <TablePagination component="div" count={profiles.data?.totalElements ?? 0} page={profilePage} rowsPerPage={25} rowsPerPageOptions={[25]} onPageChange={(_, page) => setProfilePage(page)} />
        </Paper>

        {selected && <Paper variant="outlined" sx={{ p: 2 }}>
          <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 1, gap: 2 }}>
            <Typography variant="h6" sx={{ fontSize: "1rem" }}>{selected.type.replaceAll("_", " ")} / {selected.reference}</Typography>
            {network.data?.riskProfile && <Link to={`/records/MOBILE_MONEY_RISK_PROFILE/${network.data.riskProfile.id}`}>Risk profile</Link>}
          </Box>
          <TableContainer><Table size="small"><TableHead><TableRow><TableCell>Relationship</TableCell><TableCell>From</TableCell><TableCell>To</TableCell><TableCell>Occurrences</TableCell><TableCell>Volume</TableCell><TableCell>Last seen</TableCell><TableCell>Evidence</TableCell></TableRow></TableHead><TableBody>
            {network.isLoading && <TableRow><TableCell colSpan={7} align="center">Loading network...</TableCell></TableRow>}
            {!network.isLoading && !network.data?.edges.length && <TableRow><TableCell colSpan={7} align="center">No related entities.</TableCell></TableRow>}
            {network.data?.edges.map((edge) => <TableRow key={edge.id} hover>
              <TableCell>{edge.relationshipType.replaceAll("_", " ")}</TableCell>
              <TableCell><Button size="small" onClick={() => setSelected({ type: edge.fromEntityType, reference: edge.fromEntityReference })}>{edge.fromEntityType}: {edge.fromEntityReference}</Button></TableCell>
              <TableCell><Button size="small" onClick={() => setSelected({ type: edge.toEntityType, reference: edge.toEntityReference })}>{edge.toEntityType}: {edge.toEntityReference}</Button></TableCell>
              <TableCell>{edge.occurrenceCount}</TableCell><TableCell>{edge.cumulativeAmount.toLocaleString()} {edge.currency}</TableCell><TableCell>{displayDate(edge.lastSeenAt)}</TableCell>
              <TableCell><Link to={`/records/MOBILE_MONEY_NETWORK_EDGE/${edge.id}`}>Edge #{edge.id}</Link>{edge.latestTransactionId && <Typography variant="caption" display="block"><Link to={`/records/MULTI_ASSET_TRANSACTION/${edge.latestTransactionId}`}>Transaction #{edge.latestTransactionId}</Link></Typography>}</TableCell>
            </TableRow>)}
          </TableBody></Table></TableContainer>
        </Paper>}
      </Box>}

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} fullWidth maxWidth="lg">
        <DialogTitle>Assess mobile-money transaction</DialogTitle><DialogContent>
          <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", md: "repeat(3, minmax(0, 1fr))" }, gap: 2, pt: 1 }}>
            <TextField label="External transaction ID" required value={form.externalTransactionId} onChange={(e) => setField("externalTransactionId", e.target.value)} />
            <TextField select label="Customer" required value={form.customerId} onChange={(e) => setField("customerId", e.target.value)}>{customers.data?.content.map((customer) => <MenuItem key={customer.id} value={customer.id}>{customer.displayName} / {customer.externalCustomerId}</MenuItem>)}</TextField>
            <TextField select label="Event type" value={form.eventType} onChange={(e) => setField("eventType", e.target.value)}>{eventTypes.map((event) => <MenuItem key={event} value={event}>{event.replaceAll("_", " ")}</MenuItem>)}</TextField>
            <TextField label="Wallet reference" required value={form.walletReference} onChange={(e) => setField("walletReference", e.target.value)} />
            <TextField label="Counterparty wallet" value={form.counterpartyWalletReference} onChange={(e) => setField("counterpartyWalletReference", e.target.value)} />
            <TextField label="Counterparty reference" value={form.counterpartyReference} onChange={(e) => setField("counterpartyReference", e.target.value)} />
            <TextField label="Amount" type="number" required value={form.amount} onChange={(e) => setField("amount", e.target.value)} />
            <TextField label="Currency" required value={form.currency} onChange={(e) => setField("currency", e.target.value.toUpperCase())} />
            <TextField label="USD equivalent" type="number" value={form.fiatEquivalentUsd} onChange={(e) => setField("fiatEquivalentUsd", e.target.value)} />
            <TextField label="Executed at" type="datetime-local" InputLabelProps={{ shrink: true }} value={form.executedAt} onChange={(e) => setField("executedAt", e.target.value)} />
            <TextField label="Country code" value={form.countryCode} onChange={(e) => setField("countryCode", e.target.value.toUpperCase())} />
            <TextField label="Funding party customer ID" value={form.fundingPartyCustomerId} onChange={(e) => setField("fundingPartyCustomerId", e.target.value)} />
            <TextField label="Device fingerprint" value={form.deviceFingerprint} onChange={(e) => setField("deviceFingerprint", e.target.value)} />
            <TextField label="SIM identifier hash" value={form.simIdentifierHash} onChange={(e) => setField("simIdentifierHash", e.target.value)} />
            <TextField label="Cell ID" value={form.cellId} onChange={(e) => setField("cellId", e.target.value)} />
            <TextField label="Agent reference" value={form.agentReference} onChange={(e) => setField("agentReference", e.target.value)} />
            <TextField label="Agent device fingerprint" value={form.agentDeviceFingerprint} onChange={(e) => setField("agentDeviceFingerprint", e.target.value)} />
            <TextField label="Merchant reference" value={form.merchantReference} onChange={(e) => setField("merchantReference", e.target.value)} />
            <TextField label="Merchant category" value={form.merchantCategory} onChange={(e) => setField("merchantCategory", e.target.value)} />
            <TextField label="Latitude" type="number" value={form.latitude} onChange={(e) => setField("latitude", e.target.value)} />
            <TextField label="Longitude" type="number" value={form.longitude} onChange={(e) => setField("longitude", e.target.value)} />
            <TextField label="Agent latitude" type="number" value={form.agentLatitude} onChange={(e) => setField("agentLatitude", e.target.value)} />
            <TextField label="Agent longitude" type="number" value={form.agentLongitude} onChange={(e) => setField("agentLongitude", e.target.value)} />
            <TextField label="SIM changed at" type="datetime-local" InputLabelProps={{ shrink: true }} value={form.simChangedAt} onChange={(e) => setField("simChangedAt", e.target.value)} />
            <TextField label="Device registered at" type="datetime-local" InputLabelProps={{ shrink: true }} value={form.deviceRegisteredAt} onChange={(e) => setField("deviceRegisteredAt", e.target.value)} />
            <TextField label="Previous wallet activity" type="datetime-local" InputLabelProps={{ shrink: true }} value={form.walletPreviousActivityAt} onChange={(e) => setField("walletPreviousActivityAt", e.target.value)} />
            <TextField label="Agent float before" type="number" value={form.agentFloatBefore} onChange={(e) => setField("agentFloatBefore", e.target.value)} />
            <TextField label="Agent float after" type="number" value={form.agentFloatAfter} onChange={(e) => setField("agentFloatAfter", e.target.value)} />
            <FormControlLabel control={<Checkbox checked={form.crossBorder} onChange={(e) => setField("crossBorder", e.target.checked)} />} label="Cross-border" />
          </Box>
        </DialogContent><DialogActions><Button onClick={() => setDialogOpen(false)}>Cancel</Button><Button variant="contained" disabled={!form.externalTransactionId.trim() || !form.customerId || !form.walletReference.trim() || !form.amount || ingest.isPending} onClick={submit}>Assess</Button></DialogActions>
      </Dialog>

      <Snackbar open={!!notice} autoHideDuration={5000} onClose={() => setNotice("")}><Alert severity="success" onClose={() => setNotice("")}>{notice}</Alert></Snackbar>
      <Snackbar open={!!error} autoHideDuration={8000} onClose={() => setError("")}><Alert severity="error" onClose={() => setError("")}>{error}</Alert></Snackbar>
    </Box>
  </HokekaPageShell>;
}
