import PspListCrud from "../../../components/Common/PspListCrud";
interface Props { pspId: string; trustees: any[]; onRefresh: () => void; }
export default function TrusteesTab({ pspId, trustees, onRefresh }: Props) {
  return <PspListCrud nameField="trusteeNames" title="Trustees" items={trustees} pspId={pspId} apiPath="trustees" onRefresh={onRefresh} />;
}