import PspListCrud from "../../../components/Common/PspListCrud";
interface Props { pspId: string; products: any[]; onRefresh: () => void; }
export default function ProductsTab({ pspId, products, onRefresh }: Props) {
  return <PspListCrud nameField="productName" title="Products" items={products} pspId={pspId} apiPath="products" onRefresh={onRefresh} />;
}