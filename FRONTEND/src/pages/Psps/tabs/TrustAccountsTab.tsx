import { Grid, TextField } from "@mui/material";
import CrudTab from "./CrudTab";
import { usePspTrustAccounts } from "../../../features/api/queries";
import { useCreatePspTrustAccount, useDeletePspTrustAccount } from "../../../features/api/mutations";
import { apiClient } from "../../../lib/apiClient";
import { useQueryClient } from "@tanstack/react-query";

const COLS = [
  { field: "bankAccountNumber", header: "Account Number" },
  { field: "bankId", header: "Bank ID" },
  { field: "sectorCode", header: "Sector" },
  { field: "closingBalance", header: "Closing Balance" },
  { field: "reportingDate", header: "Reporting Date" },
];

const DEFAULT = {
  reportingDate: "",
  bankId: "",
  bankAccountNumber: "",
  trustAccDrTypeCode: "",
  orgReceivingDonation: "",
  sectorCode: "",
  trustAccIntUtilizedDetails: "",
  trustAccOpeningBalance: "",
  principalAmount: "",
  trustAccInterestEarned: "",
  closingBalance: "",
  trustAccInterestUtilized: "",
  trustFields: "",
};

interface Props { pspId: string }

export default function TrustAccountsTab({ pspId }: Props) {
  const qc = useQueryClient();
  const { data = [], isLoading, isError } = usePspTrustAccounts(pspId);
  const create = useCreatePspTrustAccount(pspId);
  const remove = useDeletePspTrustAccount(pspId);

  const handleUpdate = async (id: unknown, data: Record<string, unknown>) => {
    await apiClient.put(`psps/${pspId}/cbk/trust-accounts/${id}`, data);
    qc.invalidateQueries({ queryKey: ["psp", pspId, "trust-accounts"] });
  };

  return (
    <CrudTab
      title="Trust Accounts"
      description="Daily trust account balances submitted to the CBK."
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
            <TextField fullWidth size="small" label="Bank ID" value={form.bankId}
              onChange={(e) => setForm((f) => ({ ...f, bankId: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Bank Account Number *" value={form.bankAccountNumber}
              onChange={(e) => setForm((f) => ({ ...f, bankAccountNumber: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="DR Type Code" value={form.trustAccDrTypeCode}
              onChange={(e) => setForm((f) => ({ ...f, trustAccDrTypeCode: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Sector Code" value={form.sectorCode}
              onChange={(e) => setForm((f) => ({ ...f, sectorCode: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Org Receiving Donation" value={form.orgReceivingDonation}
              onChange={(e) => setForm((f) => ({ ...f, orgReceivingDonation: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Reporting Date" type="date"
              InputLabelProps={{ shrink: true }} value={form.reportingDate}
              onChange={(e) => setForm((f) => ({ ...f, reportingDate: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={4}>
            <TextField fullWidth size="small" label="Opening Balance" type="number" value={form.trustAccOpeningBalance}
              onChange={(e) => setForm((f) => ({ ...f, trustAccOpeningBalance: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={4}>
            <TextField fullWidth size="small" label="Principal Amount" type="number" value={form.principalAmount}
              onChange={(e) => setForm((f) => ({ ...f, principalAmount: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={4}>
            <TextField fullWidth size="small" label="Closing Balance" type="number" value={form.closingBalance}
              onChange={(e) => setForm((f) => ({ ...f, closingBalance: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Interest Earned" type="number" value={form.trustAccInterestEarned}
              onChange={(e) => setForm((f) => ({ ...f, trustAccInterestEarned: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Interest Utilized" type="number" value={form.trustAccInterestUtilized}
              onChange={(e) => setForm((f) => ({ ...f, trustAccInterestUtilized: e.target.value }))} />
          </Grid>
          <Grid item xs={12}>
            <TextField fullWidth size="small" label="Interest Utilized Details" multiline rows={2}
              value={form.trustAccIntUtilizedDetails}
              onChange={(e) => setForm((f) => ({ ...f, trustAccIntUtilizedDetails: e.target.value }))} />
          </Grid>
          <Grid item xs={12}>
            <TextField fullWidth size="small" label="Trust Fields (JSON)" multiline rows={2} value={form.trustFields}
              onChange={(e) => setForm((f) => ({ ...f, trustFields: e.target.value }))} />
          </Grid>
        </Grid>
      )}
    />
  );
}
