import { useEffect, useMemo, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import {
  AlertTriangle,
  Bitcoin,
  Building2,
  Coins,
  Landmark,
  Plus,
  Search,
  ShieldCheck,
  Smartphone,
  UserRound,
} from "lucide-react";
import {
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  MenuItem,
  TextField,
} from "@mui/material";
import { apiClient } from "../../lib/apiClient";
import { useCustomer360, useMultiAssetCustomers } from "../../features/api/queries";
import type {
  AssetAccountType,
  AssetClass,
  Customer360,
  CustomerRelationshipType,
  MultiAssetCustomer,
  MultiAssetTransactionType,
  ProductDomain,
} from "../../types/multiAsset";

const assetClasses: AssetClass[] = ["BANKING", "SECURITIES", "E_MONEY", "TOKENIZED_FIAT", "CRYPTO"];
const accountTypes: Record<AssetClass, AssetAccountType[]> = {
  BANKING: ["BANK_ACCOUNT"],
  SECURITIES: ["BROKERAGE"],
  E_MONEY: ["MOBILE_MONEY", "DIGITAL_WALLET", "PREPAID_VALUE"],
  TOKENIZED_FIAT: ["TOKENIZED_FIAT_ACCOUNT"],
  CRYPTO: ["CRYPTO_WALLET", "EXCHANGE_ACCOUNT"],
};
const productDomains: Record<AssetClass, ProductDomain[]> = {
  BANKING: ["BANKING"],
  SECURITIES: ["SECURITIES_AML", "SECURITIES_MARKET_SURVEILLANCE"],
  E_MONEY: ["E_MONEY_MOBILE_MONEY", "PREPAID_CLOSED_LOOP"],
  TOKENIZED_FIAT: ["TOKENIZED_FIAT_CBDC"],
  CRYPTO: ["VIRTUAL_ASSET"],
};
const productDomainLabels: Record<ProductDomain, string> = {
  BANKING: "Banking AML",
  SECURITIES_AML: "Securities AML",
  SECURITIES_MARKET_SURVEILLANCE: "Market surveillance",
  E_MONEY_MOBILE_MONEY: "Mobile money and e-money",
  PREPAID_CLOSED_LOOP: "Prepaid and closed loop",
  VIRTUAL_ASSET: "Virtual assets",
  TOKENIZED_FIAT_CBDC: "Tokenized fiat and CBDC",
};
const transactionTypes: MultiAssetTransactionType[] = [
  "DEPOSIT", "WITHDRAWAL", "TRANSFER", "TOP_UP", "PAYMENT", "BUY", "SELL", "REFUND", "BRIDGE", "SWAP",
];

const assetMeta = {
  BANKING: { label: "Banking", icon: Landmark, color: "var(--ink)" },
  SECURITIES: { label: "Securities", icon: Building2, color: "var(--gold)" },
  E_MONEY: { label: "E-money", icon: Smartphone, color: "var(--info)" },
  TOKENIZED_FIAT: { label: "Tokenized fiat", icon: Coins, color: "var(--success)" },
  CRYPTO: { label: "Crypto", icon: Bitcoin, color: "var(--danger)" },
} satisfies Record<AssetClass, { label: string; icon: typeof Landmark; color: string }>;

function formatAmount(value?: number, currency = "USD") {
  if (value === undefined || value === null) return "-";
  try {
    return new Intl.NumberFormat(undefined, { style: "currency", currency, maximumFractionDigits: 2 }).format(value);
  } catch {
    return `${value.toLocaleString()} ${currency}`;
  }
}

function formatDate(value?: string) {
  return value ? new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "-";
}

function EmptyState({ text }: { text: string }) {
  return <div className="py-10 text-center text-sm text-white/45">{text}</div>;
}

export default function Customer360Page() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState("");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [dialog, setDialog] = useState<"customer" | "account" | "relationship" | "transaction" | null>(null);
  const customers = useMultiAssetCustomers(search);
  const customer360 = useCustomer360(selectedId);

  useEffect(() => {
    const first = customers.data?.content[0];
    if (selectedId === null && first) setSelectedId(first.id);
  }, [customers.data, selectedId]);

  const refresh = async (customerId?: number) => {
    await queryClient.invalidateQueries({ queryKey: ["multi-asset"] });
    if (customerId) setSelectedId(customerId);
    setDialog(null);
  };

  return (
    <div className="flex min-h-0 flex-1 flex-col bg-[var(--ink)] text-white">
      <header className="flex flex-wrap items-center justify-between gap-4 border-b border-white/10 px-6 py-5">
        <div>
          <p className="text-xs font-semibold uppercase tracking-widest text-gold">Multi-asset intelligence</p>
          <h1 className="mt-1 text-2xl font-semibold">Customer 360</h1>
        </div>
        <div className="flex gap-2">
          <Button variant="outlined" startIcon={<Plus size={16} />} onClick={() => setDialog("customer")}>Customer</Button>
          <Button variant="outlined" startIcon={<Plus size={16} />} disabled={!selectedId} onClick={() => setDialog("account")}>Account</Button>
          <Button variant="outlined" startIcon={<Plus size={16} />} disabled={!selectedId} onClick={() => setDialog("relationship")}>Relationship</Button>
          <Button variant="contained" startIcon={<Plus size={16} />} disabled={!selectedId} onClick={() => setDialog("transaction")}>Transaction</Button>
        </div>
      </header>

      <div className="grid min-h-0 flex-1 grid-cols-1 xl:grid-cols-[300px_minmax(0,1fr)]">
        <aside className="border-r border-white/10 bg-black/10 p-4">
          <div className="relative mb-4">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-white/40" size={16} />
            <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search customers" className="h-10 w-full border border-white/10 bg-white/5 pl-9 pr-3 text-sm outline-none focus:border-gold/70" />
          </div>
          {customers.isLoading && <EmptyState text="Loading customers..." />}
          {customers.isError && <EmptyState text="Customer records could not be loaded." />}
          {!customers.isLoading && !customers.data?.content.length && <EmptyState text="No customers found." />}
          <div className="space-y-1">
            {customers.data?.content.map((customer) => (
              <button key={customer.id} onClick={() => setSelectedId(customer.id)} className={`w-full border-l-2 px-3 py-3 text-left transition ${selectedId === customer.id ? "border-gold bg-white/8" : "border-transparent hover:bg-white/5"}`}>
                <div className="flex items-center justify-between gap-3">
                  <span className="truncate text-sm font-medium">{customer.displayName}</span>
                  <span className={`text-[10px] font-semibold ${customer.riskTier === "CRITICAL" || customer.riskTier === "HIGH" ? "text-red-300" : "text-white/45"}`}>{customer.riskTier}</span>
                </div>
                <div className="mt-1 flex items-center justify-between text-xs text-white/40">
                  <span>{customer.externalCustomerId}</span><span>{customer.kycStatus}</span>
                </div>
              </button>
            ))}
          </div>
        </aside>

        <main className="min-w-0 overflow-y-auto p-4 md:p-6">
          {!selectedId && <EmptyState text="Select a customer or create the first customer record." />}
          {customer360.isLoading && <EmptyState text="Building the customer view..." />}
          {customer360.isError && <EmptyState text="The customer view could not be loaded." />}
          {customer360.data && <CustomerView data={customer360.data} />}
        </main>
      </div>

      <CustomerDialog open={dialog === "customer"} onClose={() => setDialog(null)} onSaved={refresh} />
      {selectedId && <AccountDialog customerId={selectedId} open={dialog === "account"} onClose={() => setDialog(null)} onSaved={() => refresh(selectedId)} />}
      {selectedId && <RelationshipDialog customerId={selectedId} customers={customers.data?.content ?? []} open={dialog === "relationship"} onClose={() => setDialog(null)} onSaved={() => refresh(selectedId)} />}
      {selectedId && <TransactionDialog data={customer360.data} customerId={selectedId} open={dialog === "transaction"} onClose={() => setDialog(null)} onSaved={() => refresh(selectedId)} />}
    </div>
  );
}

function CustomerView({ data }: { data: Customer360 }) {
  const customer = data.customer;
  return <div className="space-y-6">
    <section className="flex flex-wrap items-start justify-between gap-5 border-b border-white/10 pb-6">
      <div className="flex items-center gap-4">
        <div className="flex h-12 w-12 items-center justify-center bg-gold/15 text-gold"><UserRound size={24} /></div>
        <div><h2 className="text-xl font-semibold">{customer.displayName}</h2><p className="mt-1 text-sm text-white/45">{customer.customerType} · {customer.externalCustomerId} · {customer.countryCode || "Country not recorded"}</p></div>
      </div>
      <div className="flex gap-6 text-right">
        <div><p className="text-xs text-white/40">Composite risk</p><p className="mt-1 text-xl font-semibold">{data.compositeRiskScore}</p></div>
        <div><p className="text-xs text-white/40">KYC status</p><p className="mt-1 text-sm font-semibold text-gold">{customer.kycStatus}</p></div>
        <div><p className="text-xs text-white/40">Risk tier</p><p className="mt-1 text-sm font-semibold">{customer.riskTier}</p></div>
      </div>
    </section>

    <section>
      <h3 className="mb-3 text-sm font-semibold">Asset exposure</h3>
      <div className="grid grid-cols-2 gap-px border border-white/10 bg-white/10 lg:grid-cols-4">
        {assetClasses.map((asset) => { const meta = assetMeta[asset]; const Icon = meta.icon; return <div key={asset} className="bg-[var(--ink)] p-4"><div className="flex items-center gap-2 text-xs text-white/50"><Icon size={15} style={{ color: meta.color }} />{meta.label}</div><p className="mt-4 text-lg font-semibold">{formatAmount(data.volumeByAssetClass[asset])}</p><p className="mt-1 text-xs text-white/35">{data.transactionCountByAssetClass[asset] ?? 0} transactions</p></div>; })}
      </div>
    </section>

    <section>
      <h3 className="mb-3 text-sm font-semibold">Product-domain exposure</h3>
      <div className="grid gap-px border border-white/10 bg-white/10 md:grid-cols-2 xl:grid-cols-4">
        {Object.entries(data.volumeByProductDomain).map(([domain, volume]) => (
          <div key={domain} className="bg-[var(--ink)] p-4">
            <p className="text-xs text-white/50">{productDomainLabels[domain as ProductDomain]}</p>
            <p className="mt-3 text-lg font-semibold">{formatAmount(volume)}</p>
            <p className="mt-1 text-xs text-white/35">{data.transactionCountByProductDomain[domain as ProductDomain] ?? 0} transactions</p>
          </div>
        ))}
        {!Object.keys(data.volumeByProductDomain).length && <div className="col-span-full"><EmptyState text="No product-domain activity available." /></div>}
      </div>
    </section>

    <section className="grid gap-6 2xl:grid-cols-2">
      <div>
        <h3 className="mb-3 text-sm font-semibold">Ownership and control</h3>
        <div className="divide-y divide-white/8 border border-white/10">
          {data.relationships.map((relationship) => <Link to={`/records/MULTI_ASSET_CUSTOMER/${relationship.relatedCustomerId}`} key={relationship.id} className="flex items-center justify-between gap-4 p-4 transition hover:bg-white/5"><div><p className="text-sm font-medium text-white hover:text-gold">{relationship.relatedCustomerName}</p><p className="mt-1 text-xs text-white/40">{relationship.relatedCustomerExternalId}</p></div><div className="text-right"><p className="text-xs font-semibold text-gold">{relationship.relationshipType.replaceAll("_", " ")}</p>{relationship.ownershipPercentage !== undefined && <p className="mt-1 text-xs text-white/45">{relationship.ownershipPercentage}% ownership</p>}</div></Link>)}
          {!data.relationships.length && <EmptyState text="No ownership or control relationships linked." />}
        </div>
      </div>
      <div>
        <h3 className="mb-3 text-sm font-semibold">Top counterparties</h3>
        <div className="divide-y divide-white/8 border border-white/10">
          {data.topCounterparties.map((counterparty) => <div key={counterparty.counterpartyReference} className="flex items-center justify-between gap-4 p-4"><span className="truncate text-sm">{counterparty.counterpartyReference}</span><span className="text-sm font-medium">{formatAmount(counterparty.volume)}</span></div>)}
          {!data.topCounterparties.length && <EmptyState text="No counterparty exposure available." />}
        </div>
      </div>
    </section>

    <section className="grid gap-6 2xl:grid-cols-2">
      <div><h3 className="mb-3 text-sm font-semibold">Linked accounts</h3><div className="overflow-x-auto border border-white/10"><table className="w-full text-left text-sm"><thead className="bg-white/5 text-xs text-white/45"><tr><th className="px-4 py-3">Account</th><th className="px-4 py-3">Domain</th><th className="px-4 py-3">Provider</th><th className="px-4 py-3">Status</th></tr></thead><tbody className="divide-y divide-white/8">{data.accounts.map((account) => <tr key={account.id}><td className="px-4 py-3"><div>{account.externalAccountId}</div><div className="text-xs text-white/35">{account.accountType.replaceAll("_", " ")}</div></td><td className="px-4 py-3"><div>{productDomainLabels[account.productDomain]}</div><div className="text-xs text-white/35">{assetMeta[account.assetClass].label}</div></td><td className="px-4 py-3 text-white/55">{account.providerName || "-"}</td><td className="px-4 py-3 text-xs text-emerald-300">{account.status}</td></tr>)}</tbody></table>{!data.accounts.length && <EmptyState text="No linked accounts." />}</div></div>
      <div><h3 className="mb-3 text-sm font-semibold">Risk signals</h3><div className="divide-y divide-white/8 border border-white/10">{data.recentSignals.slice(0, 8).map((signal) => <div key={signal.id} className="flex gap-3 p-4"><AlertTriangle size={17} className="mt-0.5 shrink-0 text-amber-300"/><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><span className="text-sm font-medium">{signal.signalCode.replaceAll("_", " ")}</span>{/* W18-8 fix: signalType existed on the API response and the type but was never rendered here */}<span className="rounded border border-white/15 px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-wide text-white/50">{signal.signalType}</span><span className="text-[10px] font-semibold text-white/40">+{signal.scoreImpact} · {signal.severity}</span></div><p className="mt-1 text-xs leading-5 text-white/50">{signal.description}</p></div></div>)}{!data.recentSignals.length && <EmptyState text="No risk signals detected." />}</div></div>
    </section>

    <section><h3 className="mb-3 text-sm font-semibold">Recent cross-asset activity</h3><div className="overflow-x-auto border border-white/10"><table className="w-full min-w-[760px] text-left text-sm"><thead className="bg-white/5 text-xs text-white/45"><tr><th className="px-4 py-3">Time</th><th className="px-4 py-3">Activity</th><th className="px-4 py-3">Amount</th><th className="px-4 py-3">Counterparty</th><th className="px-4 py-3">Risk</th><th className="px-4 py-3">Decision</th></tr></thead><tbody className="divide-y divide-white/8">{data.recentTransactions.map((tx) => <tr key={tx.id}><td className="px-4 py-3 text-white/50">{formatDate(tx.executedAt)}</td><td className="px-4 py-3"><div>{assetMeta[tx.assetClass].label}</div><div className="text-xs text-white/35">{tx.transactionType}</div></td><td className="px-4 py-3">{formatAmount(tx.amount, tx.currency)}</td><td className="px-4 py-3 text-white/50">{tx.counterpartyReference || "-"}</td><td className="px-4 py-3">{tx.riskScore}</td><td className="px-4 py-3"><span className={tx.decision === "ALLOW" ? "text-emerald-300" : "text-amber-300"}>{tx.decision}</span>{tx.travelRuleRequired && <div className="mt-1 text-[10px] text-white/35">TRAVEL RULE: {tx.travelRuleStatus}</div>}</td></tr>)}</tbody></table>{!data.recentTransactions.length && <EmptyState text="No activity has been ingested." />}</div></section>
  </div>;
}

function CustomerDialog({ open, onClose, onSaved }: { open: boolean; onClose: () => void; onSaved: (id: number) => void }) {
  const [form, setForm] = useState({ externalCustomerId: "", customerType: "INDIVIDUAL", displayName: "", countryCode: "", riskTier: "MEDIUM", kycStatus: "PENDING" });
  const mutation = useMutation({ mutationFn: () => apiClient.post<MultiAssetCustomer>("multi-asset/customers", form), onSuccess: (value) => onSaved(value.id) });
  return <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm"><DialogTitle>Create customer</DialogTitle><DialogContent className="grid gap-4 pt-2"><TextField label="External customer ID" required value={form.externalCustomerId} onChange={(e) => setForm({ ...form, externalCustomerId: e.target.value })}/><TextField label="Display name" required value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })}/><TextField select label="Customer type" value={form.customerType} onChange={(e) => setForm({ ...form, customerType: e.target.value })}><MenuItem value="INDIVIDUAL">Individual</MenuItem><MenuItem value="ENTITY">Entity</MenuItem></TextField><TextField label="Country code" value={form.countryCode} onChange={(e) => setForm({ ...form, countryCode: e.target.value })}/>{mutation.isError && <p className="text-sm text-red-500">Customer could not be created.</p>}</DialogContent><DialogActions><Button onClick={onClose}>Cancel</Button><Button variant="contained" disabled={!form.externalCustomerId || !form.displayName || mutation.isPending} onClick={() => mutation.mutate()}>Create</Button></DialogActions></Dialog>;
}

function AccountDialog({ customerId, open, onClose, onSaved }: { customerId: number; open: boolean; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState({ externalAccountId: "", assetClass: "BANKING" as AssetClass, productDomain: "BANKING" as ProductDomain, accountType: "BANK_ACCOUNT" as AssetAccountType, providerName: "", currency: "", countryCode: "", publicAddress: "", programReference: "", issuerName: "", issuerVerified: false, ledgerReference: "" });
  const mutation = useMutation({ mutationFn: () => {
    const metadata = form.productDomain === "PREPAID_CLOSED_LOOP"
      ? { programReference: form.programReference }
      : form.productDomain === "TOKENIZED_FIAT_CBDC"
        ? { issuerName: form.issuerName, issuerVerified: form.issuerVerified, ledgerReference: form.ledgerReference }
        : {};
    return apiClient.post(`multi-asset/customers/${customerId}/accounts`, {
      externalAccountId: form.externalAccountId, assetClass: form.assetClass, productDomain: form.productDomain,
      accountType: form.accountType, providerName: form.providerName, currency: form.currency,
      countryCode: form.countryCode, publicAddress: form.publicAddress, metadata,
    });
  }, onSuccess: onSaved });
  const setAsset = (assetClass: AssetClass) => setForm({ ...form, assetClass, productDomain: productDomains[assetClass][0], accountType: accountTypes[assetClass][0] });
  return <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm"><DialogTitle>Link asset account</DialogTitle><DialogContent className="grid gap-4 pt-2"><TextField label="External account ID" required value={form.externalAccountId} onChange={(e) => setForm({ ...form, externalAccountId: e.target.value })}/><TextField select label="Asset class" value={form.assetClass} onChange={(e) => setAsset(e.target.value as AssetClass)}>{assetClasses.map((asset) => <MenuItem key={asset} value={asset}>{assetMeta[asset].label}</MenuItem>)}</TextField><TextField select label="Product domain" value={form.productDomain} onChange={(e) => setForm({ ...form, productDomain: e.target.value as ProductDomain })}>{productDomains[form.assetClass].map((domain) => <MenuItem key={domain} value={domain}>{productDomainLabels[domain]}</MenuItem>)}</TextField><TextField select label="Account type" value={form.accountType} onChange={(e) => setForm({ ...form, accountType: e.target.value as AssetAccountType })}>{accountTypes[form.assetClass].map((type) => <MenuItem key={type} value={type}>{type.replaceAll("_", " ")}</MenuItem>)}</TextField><TextField label="Provider" value={form.providerName} onChange={(e) => setForm({ ...form, providerName: e.target.value })}/><div className="grid grid-cols-2 gap-3"><TextField label="Currency" value={form.currency} onChange={(e) => setForm({ ...form, currency: e.target.value })}/><TextField label="Country code" value={form.countryCode} onChange={(e) => setForm({ ...form, countryCode: e.target.value })}/></div>{form.productDomain === "PREPAID_CLOSED_LOOP" && <TextField label="Programme reference" required value={form.programReference} onChange={(e) => setForm({ ...form, programReference: e.target.value })}/>} {form.productDomain === "TOKENIZED_FIAT_CBDC" && <><TextField label="Issuer or central bank" required value={form.issuerName} onChange={(e) => setForm({ ...form, issuerName: e.target.value })}/><TextField label="Ledger or settlement reference" required value={form.ledgerReference} onChange={(e) => setForm({ ...form, ledgerReference: e.target.value })}/><FormControlLabel control={<Checkbox checked={form.issuerVerified} onChange={(e) => setForm({ ...form, issuerVerified: e.target.checked })}/>} label="Issuer provenance verified" /></>} {form.assetClass === "CRYPTO" && <TextField label="Public wallet address" value={form.publicAddress} onChange={(e) => setForm({ ...form, publicAddress: e.target.value })}/>}</DialogContent><DialogActions><Button onClick={onClose}>Cancel</Button><Button variant="contained" disabled={!form.externalAccountId || mutation.isPending} onClick={() => mutation.mutate()}>Link account</Button></DialogActions></Dialog>;
}

function RelationshipDialog({ customerId, customers, open, onClose, onSaved }: { customerId: number; customers: MultiAssetCustomer[]; open: boolean; onClose: () => void; onSaved: () => void }) {
  const candidates = customers.filter((customer) => customer.id !== customerId);
  const [form, setForm] = useState({ relatedCustomerId: "", relationshipType: "UBO" as CustomerRelationshipType, ownershipPercentage: "" });
  const mutation = useMutation({ mutationFn: () => apiClient.post(`multi-asset/customers/${customerId}/relationships`, { relatedCustomerId: Number(form.relatedCustomerId), relationshipType: form.relationshipType, ownershipPercentage: form.ownershipPercentage ? Number(form.ownershipPercentage) : undefined }), onSuccess: onSaved });
  const relationshipTypes: CustomerRelationshipType[] = ["UBO", "DIRECTOR", "SHAREHOLDER", "SIGNATORY", "CONTROLLING_PERSON", "RELATED_CUSTOMER"];
  return <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm"><DialogTitle>Link ownership or control</DialogTitle><DialogContent className="grid gap-4 pt-2"><TextField select label="Related customer" required value={form.relatedCustomerId} onChange={(e) => setForm({ ...form, relatedCustomerId: e.target.value })}>{candidates.map((customer) => <MenuItem key={customer.id} value={customer.id}>{customer.displayName} ({customer.externalCustomerId})</MenuItem>)}</TextField><TextField select label="Relationship" value={form.relationshipType} onChange={(e) => setForm({ ...form, relationshipType: e.target.value as CustomerRelationshipType })}>{relationshipTypes.map((type) => <MenuItem key={type} value={type}>{type.replaceAll("_", " ")}</MenuItem>)}</TextField><TextField label="Ownership percentage" type="number" value={form.ownershipPercentage} onChange={(e) => setForm({ ...form, ownershipPercentage: e.target.value })}/>{!candidates.length && <p className="text-sm text-amber-700">Create the related individual or entity as a customer before linking it.</p>}</DialogContent><DialogActions><Button onClick={onClose}>Cancel</Button><Button variant="contained" disabled={!form.relatedCustomerId || mutation.isPending} onClick={() => mutation.mutate()}>Link relationship</Button></DialogActions></Dialog>;
}

function TransactionDialog({ customerId, data, open, onClose, onSaved }: { customerId: number; data?: Customer360; open: boolean; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState({ externalTransactionId: "", assetClass: "BANKING" as AssetClass, productDomain: "BANKING" as ProductDomain, transactionType: "TRANSFER" as MultiAssetTransactionType, amount: "", fiatEquivalentUsd: "", currency: "USD", assetSymbol: "", quantity: "", unitPrice: "", sourceAccountId: "", destinationAccountId: "", counterpartyReference: "", fundingPartyCustomerId: "", sourceNetwork: "", destinationNetwork: "", originatorName: "", originatorAccount: "", beneficiaryName: "", beneficiaryAccount: "", deviceFingerprint: "", countryCode: "", merchantCategory: "" });
  const eligibleAccounts = useMemo(() => data?.accounts.filter((account) => account.assetClass === form.assetClass) ?? [], [data, form.assetClass]);
  useEffect(() => {
    if (!productDomains[form.assetClass].includes(form.productDomain)) {
      setForm((current) => ({ ...current, productDomain: productDomains[current.assetClass][0] }));
    }
  }, [form.assetClass, form.productDomain]);
  const selectedAccount = data?.accounts.find((account) => account.id === Number(form.sourceAccountId || form.destinationAccountId));
  const transactionDomain = selectedAccount?.productDomain ?? form.productDomain;
  const mutation = useMutation({ mutationFn: () => apiClient.post("multi-asset/transactions", { ...form, productDomain: transactionDomain, customerId, amount: Number(form.amount), fiatEquivalentUsd: form.fiatEquivalentUsd ? Number(form.fiatEquivalentUsd) : undefined, quantity: form.quantity ? Number(form.quantity) : undefined, unitPrice: form.unitPrice ? Number(form.unitPrice) : undefined, sourceAccountId: form.sourceAccountId ? Number(form.sourceAccountId) : undefined, destinationAccountId: form.destinationAccountId ? Number(form.destinationAccountId) : undefined }), onSuccess: onSaved });
  return <Dialog open={open} onClose={onClose} fullWidth maxWidth="md"><DialogTitle>Ingest transaction</DialogTitle><DialogContent className="grid gap-4 pt-2"><div className="grid gap-3 md:grid-cols-2"><TextField label="External transaction ID" required value={form.externalTransactionId} onChange={(e) => setForm({ ...form, externalTransactionId: e.target.value })}/><TextField select label="Asset class" value={form.assetClass} onChange={(e) => setForm({ ...form, assetClass: e.target.value as AssetClass, sourceAccountId: "", destinationAccountId: "" })}>{assetClasses.map((asset) => <MenuItem key={asset} value={asset}>{assetMeta[asset].label}</MenuItem>)}</TextField><TextField select label="Transaction type" value={form.transactionType} onChange={(e) => setForm({ ...form, transactionType: e.target.value as MultiAssetTransactionType })}>{transactionTypes.map((type) => <MenuItem key={type} value={type}>{type}</MenuItem>)}</TextField><TextField label="Amount" type="number" required value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })}/><TextField label="Currency" required value={form.currency} onChange={(e) => setForm({ ...form, currency: e.target.value })}/><TextField label="USD equivalent" type="number" value={form.fiatEquivalentUsd} onChange={(e) => setForm({ ...form, fiatEquivalentUsd: e.target.value })}/><TextField select label="Source account" value={form.sourceAccountId} onChange={(e) => setForm({ ...form, sourceAccountId: e.target.value })}><MenuItem value="">None</MenuItem>{eligibleAccounts.map((account) => <MenuItem key={account.id} value={account.id}>{account.externalAccountId}</MenuItem>)}</TextField><TextField select label="Destination account" value={form.destinationAccountId} onChange={(e) => setForm({ ...form, destinationAccountId: e.target.value })}><MenuItem value="">None</MenuItem>{eligibleAccounts.map((account) => <MenuItem key={account.id} value={account.id}>{account.externalAccountId}</MenuItem>)}</TextField><TextField label="Counterparty reference" value={form.counterpartyReference} onChange={(e) => setForm({ ...form, counterpartyReference: e.target.value })}/><TextField label="Country code" value={form.countryCode} onChange={(e) => setForm({ ...form, countryCode: e.target.value })}/></div>{form.assetClass === "CRYPTO" && <div className="grid gap-3 border-t border-black/10 pt-4 md:grid-cols-2"><TextField label="Source network" value={form.sourceNetwork} onChange={(e) => setForm({ ...form, sourceNetwork: e.target.value })}/><TextField label="Destination network" value={form.destinationNetwork} onChange={(e) => setForm({ ...form, destinationNetwork: e.target.value })}/><TextField label="Originator name" value={form.originatorName} onChange={(e) => setForm({ ...form, originatorName: e.target.value })}/><TextField label="Originator account" value={form.originatorAccount} onChange={(e) => setForm({ ...form, originatorAccount: e.target.value })}/><TextField label="Beneficiary name" value={form.beneficiaryName} onChange={(e) => setForm({ ...form, beneficiaryName: e.target.value })}/><TextField label="Beneficiary account" value={form.beneficiaryAccount} onChange={(e) => setForm({ ...form, beneficiaryAccount: e.target.value })}/></div>}{form.assetClass === "E_MONEY" && <div className="grid gap-3 border-t border-black/10 pt-4 md:grid-cols-2"><TextField label="Device fingerprint" value={form.deviceFingerprint} onChange={(e) => setForm({ ...form, deviceFingerprint: e.target.value })}/><TextField label="Merchant category" value={form.merchantCategory} onChange={(e) => setForm({ ...form, merchantCategory: e.target.value })}/></div>}{mutation.isError && <p className="text-sm text-red-500">The transaction was rejected. Check the account and transaction fields.</p>}</DialogContent><DialogActions><Button onClick={onClose}>Cancel</Button><Button variant="contained" startIcon={<ShieldCheck size={16}/>} disabled={!form.externalTransactionId || !form.amount || !form.currency || mutation.isPending} onClick={() => mutation.mutate()}>Assess and ingest</Button></DialogActions></Dialog>;
}
