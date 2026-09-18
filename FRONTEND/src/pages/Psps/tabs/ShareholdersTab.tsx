import PspListCrud from "../../../components/Common/PspListCrud";
interface Props { pspId: string; shareholders: any[]; onRefresh: () => void; }
export default function ShareholdersTab({ pspId, shareholders, onRefresh }: Props) {
  return <PspListCrud nameField="shareholderName" title="Shareholders" items={shareholders} pspId={pspId} apiPath="shareholders" onRefresh={onRefresh} />;
}