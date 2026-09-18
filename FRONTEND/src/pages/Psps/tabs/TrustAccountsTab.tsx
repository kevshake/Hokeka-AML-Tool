import PspListCrud from "../../../components/Common/PspListCrud";
interface Props { pspId: string; trustAccounts: any[]; onRefresh: () => void; }
export default function TrustAccountsTab({ pspId, trustAccounts, onRefresh }: Props) {
  return <PspListCrud nameField="bankAccountNumber" title="Trust Accounts" items={trustAccounts} pspId={pspId} apiPath="trust-accounts" onRefresh={onRefresh}
    extraFields={[{ key: "balance", label: "Balance (USD)", placeholder: "0.00", type: "number" }]} />;
}