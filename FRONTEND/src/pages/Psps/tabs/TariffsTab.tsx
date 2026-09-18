import PspListCrud from "../../../components/Common/PspListCrud";
interface Props { pspId: string; tariffs: any[]; onRefresh: () => void; }
export default function TariffsTab({ pspId, tariffs, onRefresh }: Props) {
  return <PspListCrud nameField="chargeDescription" title="Transaction Tariffs" items={tariffs} pspId={pspId} apiPath="tariffs" onRefresh={onRefresh}
    extraFields={[{ key: "fee", label: "Fee (USD)", placeholder: "0.00", type: "number" }]} />;
}