import { Grid, TextField } from "@mui/material";
import CrudTab from "./CrudTab";
import { usePspProducts } from "../../../features/api/queries";
import { useCreatePspProduct, useDeletePspProduct } from "../../../features/api/mutations";
import { apiClient } from "../../../lib/apiClient";
import { useQueryClient } from "@tanstack/react-query";

const COLS = [
  { field: "productName", header: "Product Name" },
  { field: "productTransactionCode", header: "Txn Code" },
  { field: "productOwnershipFlag", header: "Ownership" },
  { field: "statusCode", header: "Status" },
  { field: "noOfCustomers", header: "Customers" },
];

const DEFAULT = {
  reportingDate: "",
  productOwnershipFlag: "",
  productOwnershipCategory: "",
  productPartnerName: "",
  productTransactionCode: "",
  gender: "",
  statusCode: "",
  bandCode: "",
  noOfCustomers: "",
  noOfTransactions: "",
  valueOfTransactions: "",
  productName: "",
};

interface Props { pspId: string }

export default function ProductsTab({ pspId }: Props) {
  const qc = useQueryClient();
  const { data = [], isLoading, isError } = usePspProducts(pspId);
  const create = useCreatePspProduct(pspId);
  const remove = useDeletePspProduct(pspId);

  const handleUpdate = async (id: unknown, data: Record<string, unknown>) => {
    await apiClient.put(`psps/${pspId}/cbk/products/${id}`, data);
    qc.invalidateQueries({ queryKey: ["psp", pspId, "products"] });
  };

  return (
    <CrudTab
      title="Products"
      description="PSP product catalogue submitted monthly to the CBK."
      columns={COLS}
      rows={data}
      isLoading={isLoading}
      isError={isError}
      onCreate={(d) => create.mutateAsync(d)}
      onUpdate={handleUpdate}
      onDelete={(id) => remove.mutateAsync(id)}
      defaultFormData={DEFAULT}
      renderForm={(form, setForm) => (
        <Grid container spacing={2}>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Product Name *" value={form.productName}
              onChange={(e) => setForm((f) => ({ ...f, productName: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Transaction Code" value={form.productTransactionCode}
              onChange={(e) => setForm((f) => ({ ...f, productTransactionCode: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Ownership Flag" value={form.productOwnershipFlag}
              onChange={(e) => setForm((f) => ({ ...f, productOwnershipFlag: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Ownership Category" value={form.productOwnershipCategory}
              onChange={(e) => setForm((f) => ({ ...f, productOwnershipCategory: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Partner Name" value={form.productPartnerName}
              onChange={(e) => setForm((f) => ({ ...f, productPartnerName: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Status Code" value={form.statusCode}
              onChange={(e) => setForm((f) => ({ ...f, statusCode: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Band Code" value={form.bandCode}
              onChange={(e) => setForm((f) => ({ ...f, bandCode: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Gender" value={form.gender}
              onChange={(e) => setForm((f) => ({ ...f, gender: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={4}>
            <TextField fullWidth size="small" label="No. of Customers" type="number" value={form.noOfCustomers}
              onChange={(e) => setForm((f) => ({ ...f, noOfCustomers: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={4}>
            <TextField fullWidth size="small" label="No. of Transactions" type="number" value={form.noOfTransactions}
              onChange={(e) => setForm((f) => ({ ...f, noOfTransactions: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={4}>
            <TextField fullWidth size="small" label="Value of Transactions" type="number" value={form.valueOfTransactions}
              onChange={(e) => setForm((f) => ({ ...f, valueOfTransactions: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Reporting Date" type="date"
              InputLabelProps={{ shrink: true }} value={form.reportingDate}
              onChange={(e) => setForm((f) => ({ ...f, reportingDate: e.target.value }))} />
          </Grid>
        </Grid>
      )}
    />
  );
}
